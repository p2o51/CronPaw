package com.gpo.cronpaw

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.focus.FocusController
import com.gpo.cronpaw.core.focus.FocusNotifier
import com.gpo.cronpaw.feature.home.HomeRequests
import com.gpo.cronpaw.navigation.CronNavHost
import com.gpo.cronpaw.ui.components.LocalSnackbarHost
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.theme.PawTheme
import com.gpo.cronpaw.widget.WidgetOpen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            PawTheme {
                val snackbar = remember { SnackbarHostState() }
                val rootScope = rememberCoroutineScope()
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackbar,
                    LocalToastScope provides rootScope,
                ) {
                    // The Surface supplies onSurface as the content colour for every uncoloured Text and Icon.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Box(Modifier.fillMaxSize()) {
                            CronNavHost()
                            SnackbarHost(
                                snackbar,
                                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
                            ) { data ->
                                // A tonal pill in the app's own colours, not M3's dark inverse bar.
                                Snackbar(
                                    data,
                                    shape = CircleShape,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    actionColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** A widget's tap: go to that day, and open the item's sheet when it named one. */
    private fun openFrom(intent: Intent?) {
        if (intent?.getBooleanExtra(FocusNotifier.OpenFocus, false) == true) {
            FocusController.openPage.value = true
            return
        }
        if (intent == null || !intent.hasExtra(WidgetOpen.Date)) return
        val date = LocalDate.ofEpochDay(intent.getLongExtra(WidgetOpen.Date, LocalDate.now().toEpochDay()))
        Agenda.init(this)
        lifecycleScope.launch {
            Agenda.awaitReady()
            val item = intent.getStringExtra(WidgetOpen.Item)?.let { key -> Agenda.items.value.firstOrNull { it.key == key } }
            HomeRequests.jump.value = HomeRequests.Jump(date, open = item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Notion has no push: a return to the app is the moment to look again.
        Agenda.refresh()
        // Catch the session up, or ask how long it ran if it was cut off.
        FocusController.reconcile(this)
    }
}
