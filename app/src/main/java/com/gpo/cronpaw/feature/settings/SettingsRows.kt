package com.gpo.cronpaw.feature.settings

import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamText
import com.gpo.cronpaw.ui.components.seam.seamViewport
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.BuildConfig
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.NotionOAuth
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.core.focus.FocusSettings
import com.gpo.cronpaw.core.focus.FocusZen
import com.gpo.cronpaw.R
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols

// The settings pages' shared rows: group titles, cards, list rows, inline choices.

/** Groups are 24dp apart under a small title; rows inside a group touch. */
@Composable
internal fun GroupTitle(text: String, top: androidx.compose.ui.unit.Dp = 32.dp) {
    Text(
        text,
        Modifier.padding(start = 20.dp, top = top, bottom = 8.dp).seamText(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().seamGraphic().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 4.dp),
    ) { content() }
}

@Composable
internal fun Tile(modifier: Modifier, label: String, onClick: () -> Unit, face: @Composable () -> Unit) {
    Column(modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))) { face() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icon, title (and a quiet line under it), then whatever the row holds on the right. */
@Composable
internal fun ListRow(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).seamText()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Plain words in a line, the chosen one coloured — like a player's speed row. */
@Composable
internal fun <T> InlineChoices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == selected
            val ink by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, YoinMotion.defaultEffectsSpec(), label = "choice")
            Text(
                label,
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(v) }
                    .padding(start = 7.dp, end = if (i == options.lastIndex) 0.dp else 7.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = ink,
            )
        }
    }
}

@Composable
internal fun Chevron(open: Boolean) {
    val turn by animateFloatAsState(if (open) 90f else 0f, YoinMotion.defaultSpatialSpec(), label = "chevron")
    Icon(YoinSymbols.ChevronRight, null, Modifier.size(20.dp).graphicsLayer { rotationZ = turn }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Expand(open: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        open,
        enter = expandVertically(YoinMotion.defaultSpatialSpec()) + fadeIn(YoinMotion.defaultEffectsSpec()),
        exit = shrinkVertically(YoinMotion.fastSpatialSpec()) + fadeOut(YoinMotion.fastEffectsSpec()),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
