package com.gpo.cronpaw

import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.navigation.CronNavHost
import com.gpo.cronpaw.ui.components.LocalSnackbarHost
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.theme.PawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            PawTheme {
                val snackbar = remember { SnackbarHostState() }
                val rootScope = rememberCoroutineScope()
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackbar,
                    LocalToastScope provides rootScope,
                ) {
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

    override fun onResume() {
        super.onResume()
        // Notion has no push: a return to the app is the moment to look again.
        Agenda.refresh()
    }
}
