package com.gpo.cronpaw.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.gpo.yoin.symbols.YoinSymbols

/** Back button using the Yoin Symbols glyph (auto-mirrored). */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(YoinSymbols.Back, contentDescription = "返回")
    }
}

/** Standard pushed-page bar. Title is a label (no subtitles that narrate). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PawTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    /** Replaces the plain [title] text (e.g. to cross-fade only the title). */
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { titleContent?.invoke() ?: Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = { if (showBack && onBack != null) BackButton(onBack) },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/**
 * Expressive large flexible bar for list pages (收藏夹 / 历史 / 设置). It keeps the
 * page colour when content scrolls under it: the list's seam (ui/components/seam,
 * the tide line) is what parts them, so a tinted bar would draw a hard edge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PawLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = true,
    /**
     * The width of the page's centred content column (e.g. 560.dp + padding): the large
     * title then starts where the column starts instead of at the window edge.
     */
    contentMaxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = if (contentMaxWidth == androidx.compose.ui.unit.Dp.Unspecified) 0.dp else
        with(density) { ((window.width.toDp() - contentMaxWidth) / 2 - 16.dp).coerceAtLeast(0.dp) }
    LargeFlexibleTopAppBar(
        title = { Text(title, Modifier.padding(start = inset), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = subtitle?.let { { Text(it, maxLines = 1) } },
        modifier = modifier,
        navigationIcon = { if (showBack && onBack != null) BackButton(onBack) },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
