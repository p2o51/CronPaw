package com.gpo.cronpaw

import android.content.Intent
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.rememberNavBackStack
import com.gpo.cronpaw.core.account.Account
import com.gpo.cronpaw.core.weibo.Emoticons
import com.gpo.cronpaw.feature.compose.ComposerHost
import com.gpo.cronpaw.feature.media.ImageViewerHost
import com.gpo.cronpaw.feature.system.ShellRequests
import com.gpo.cronpaw.feature.system.SystemIntents
import com.gpo.cronpaw.navigation.AppNavHost
import com.gpo.cronpaw.navigation.LinkRouter
import com.gpo.cronpaw.navigation.Navigator
import com.gpo.cronpaw.navigation.Route
import com.gpo.cronpaw.ui.components.LocalSnackbarHost
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.theme.PawTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinks = Channel<Route>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        lifecycleScope.launch {
            runCatching { Account.refresh() }
            Emoticons.refreshIfStale()
            if (BuildConfig.DEBUG && intent?.getBooleanExtra("probe", false) == true) com.gpo.cronpaw.core.debug.ApiProbe.run()
        }
        handleIntent(intent, fresh = savedInstanceState == null)
        setContent {
            PawTheme {
                val snackbar = remember { SnackbarHostState() }
                val rootScope = rememberCoroutineScope()
                val backStack = rememberNavBackStack(Route.Main)
                val navigator = remember(backStack) { Navigator(backStack, this@MainActivity) }
                LaunchedEffect(Unit) {
                    for (route in deepLinks) navigator.push(route)
                }
                // A shortcut or the widget asked for a shell section (handleIntent): back to the shell first.
                LaunchedEffect(Unit) {
                    ShellRequests.toShell.collect { navigator.popToMain() }
                }
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackbar,
                    LocalToastScope provides rootScope,
                ) {
                    ImageViewerHost {
                        ComposerHost {
                            Box(Modifier.fillMaxSize()) {
                                AppNavHost(navigator)
                                SnackbarHost(
                                    snackbar,
                                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
                                ) { data ->
                                    // A tonal pill in the app's own colours, not M3's dark inverse bar.
                                    androidx.compose.material3.Snackbar(
                                        data,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                                        actionColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Shortcuts and the 热搜 widget (`cronpaw://`), shares from other apps (→ 发微博) and
     * weibo links (LinkRouter). [fresh] is false when the activity is being restored: its
     * intent was handled before, and the restored stack already shows it. An intent replayed
     * from Recents (the task's first intent again) is not routed a second time either.
     */
    private fun handleIntent(intent: Intent?, fresh: Boolean = true) {
        SystemIntents.onAppOpen(this)
        if (intent == null || !fresh || intent.action == Intent.ACTION_MAIN) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (SystemIntents.isShare(intent)) {
            lifecycleScope.launch { deepLinks.trySend(SystemIntents.shareRoute(this@MainActivity, intent)) }
            return
        }
        when (val target = SystemIntents.target(this, intent)) {
            is SystemIntents.Target.Page -> deepLinks.trySend(target.route)
            is SystemIntents.Target.Shell -> ShellRequests.request(target.tab, target.search)
            null -> intent.dataString?.let { LinkRouter.route(it) }?.let { deepLinks.trySend(it) }
        }
    }
}
