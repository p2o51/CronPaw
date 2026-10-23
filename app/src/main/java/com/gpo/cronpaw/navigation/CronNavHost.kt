package com.gpo.cronpaw.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.gpo.cronpaw.feature.home.HomeScreen
import com.gpo.cronpaw.feature.search.SearchScreen
import com.gpo.cronpaw.feature.settings.DatabaseSetupScreen
import com.gpo.cronpaw.feature.settings.SettingsScreen
import com.gpo.cronpaw.ui.theme.YoinMotion

/** Four pages, so a plain stack of strings: "home", "search", "settings", "db:<id>" ("db:" = pick a new one). */
class Nav(private val stack: MutableList<String>) {
    val top: String get() = stack.last()
    val depth: Int get() = stack.size
    fun push(route: String) { stack += route }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

@Composable
fun CronNavHost() {
    val stack = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf("home")
    }
    val nav = remember(stack) { Nav(stack) }
    BackHandler(enabled = stack.size > 1) { nav.pop() }
    // Home stays composed underneath, so the timeline keeps its place and scroll.
    Box(Modifier.fillMaxSize()) {
        HomeScreen(nav)
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
                (fadeIn(YoinMotion.defaultEffectsSpec()) + scaleIn(YoinMotion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
                    fadeOut(YoinMotion.fastEffectsSpec())
            },
            label = "nav",
        ) { route ->
            when {
                route == "search" -> SearchScreen(nav)
                route == "settings" -> SettingsScreen(nav)
                route.startsWith("db:") -> DatabaseSetupScreen(nav, route.removePrefix("db:").ifEmpty { null })
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}
