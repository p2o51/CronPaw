package com.gpo.cronpaw.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.ui.components.LoadingState
import com.gpo.yoin.symbols.YoinSymbols

/**
 * 未排期: a database's pages with no date, as bars to hold and drag onto the timeline
 * (the same carry as moving an item). [removed] hides the ones just scheduled.
 */
@Composable
fun UnscheduledTray(
    databaseId: String,
    removed: Set<String>,
    mover: MoveDrag,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val db = remember(databaseId) { NotionRepo.db(databaseId) }
    var list by remember(databaseId) { mutableStateOf<List<AgendaItem>?>(null) }
    LaunchedEffect(databaseId) { list = db?.let { NotionRepo.unscheduledItems(it) } ?: emptyList() }
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(db?.title ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("未排期", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            IconButton(onClick = onClose) { Icon(YoinSymbols.Close, "关闭") }
        }
        val l = list?.filter { it.key !in removed }
        when {
            l == null -> LoadingState(Modifier.heightIn(min = 80.dp))
            l.isEmpty() -> Text("都排上了", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            else -> {
                Text("按住拖到时间轴上", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 260.dp).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(l, key = { it.key }) { item -> Bar(item, mover) }
                }
            }
        }
    }
}

@Composable
private fun Bar(item: AgendaItem, mover: MoveDrag) {
    val (bg, ink) = itemColors(item)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val m by rememberUpdatedState(mover)
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords = it }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .pointerInput(item.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos -> coords?.let { m.start(item, it.localToRoot(pos), 0f) } },
                    onDrag = { change, _ ->
                        change.consume()
                        coords?.let { m.move(it.localToRoot(change.position)) }
                    },
                    onDragEnd = { m.drop() },
                    onDragCancel = { m.cancel() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp, 20.dp).clip(RoundedCornerShape(4.dp)).background(Color(item.color)))
        Spacer(Modifier.width(10.dp))
        Text(item.title, color = ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
