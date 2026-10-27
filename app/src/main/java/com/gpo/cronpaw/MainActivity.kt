package com.gpo.cronpaw

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.focus.FocusController
import com.gpo.cronpaw.core.focus.FocusNotifier
import com.gpo.cronpaw.feature.home.HomeRequests
import com.gpo.cronpaw.navigation.CronNavHost
import com.gpo.cronpaw.ui.components.LocalSnackbarHost
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.launch.LaunchHandoff
import com.gpo.cronpaw.ui.launch.LaunchHandoffOverlay
import com.gpo.cronpaw.ui.launch.LocalLaunchHandoff
import com.gpo.cronpaw.ui.theme.PawTheme
import com.gpo.cronpaw.widget.WidgetOpen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** Compose has laid out its first frame: the splash may go. */
    private var firstLayout = false
    private val handoff = LaunchHandoff()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        installHandoff(splash, savedInstanceState)
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            PawTheme {
                val snackbar = remember { SnackbarHostState() }
                val rootScope = rememberCoroutineScope()
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackbar,
                    LocalToastScope provides rootScope,
                    LocalLaunchHandoff provides handoff,
                ) {
                    // The Surface supplies onSurface as the content colour for every uncoloured Text and Icon.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { firstLayout = true }) {
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
                            LaunchHandoffOverlay(handoff)
                        }
                    }
                }
            }
        }
    }

    /**
     * The splash stays until Compose has laid out its first frame (at most [MaxHoldMs]), then hands
     * over to [LaunchHandoff]: from the launcher the date flies into the header's 今天 button; off a
     * widget, notification or link it just fades; with animations off, or on a recreated activity,
     * it is simply gone.
     */
    private fun installHandoff(splash: SplashScreen, savedInstanceState: Bundle?) {
        val created = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !firstLayout && SystemClock.uptimeMillis() - created < MaxHoldMs }
        val launched = intent
        val fromLauncher = savedInstanceState == null &&
            launched?.action == Intent.ACTION_MAIN &&
            launched.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            launched.data == null
        // Android 12+ calls the exit listener only for a splash it actually showed.
        window.decorView.postDelayed(handoff::abandonIfWaiting, GiveUpMs)
        splash.setOnExitAnimationListener { provider ->
            if (savedInstanceState != null || !ValueAnimator.areAnimatorsEnabled()) {
                handoff.skip(provider::remove)
                return@setOnExitAnimationListener
            }
            val decor = window.decorView
            val icon = provider.iconView
            val hasIcon = icon.isAttachedToWindow && icon.width > 0
            val center = if (hasIcon) {
                val at = IntArray(2).also(icon::getLocationInWindow)
                Offset(at[0] + icon.width / 2f, at[1] + icon.height / 2f)
            } else {
                Offset(decor.width / 2f, decor.height / 2f)
            }
            handoff.begin(
                fly = fromLauncher,
                iconCenter = center,
                // Android 12+ sizes the icon view to the 192dp circle (72 of the 108 units); the
                // compat splash's view spans all 108. ic_splash_paw draws the paw at 1.5x of that.
                unit = if (!hasIcon) 0f else 1.5f * icon.width / (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 72f else 108f),
                remove = provider::remove,
            )
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

    private companion object {
        const val MaxHoldMs = 1_000L
        const val GiveUpMs = 3_000L
    }
}
