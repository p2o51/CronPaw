package com.gpo.cronpaw.feature.settings

import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamViewport
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.DatabaseSchema
import com.gpo.cronpaw.core.notion.DatabaseSummary
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.notion.NotionDb
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.LoadingState
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.yoin.symbols.YoinSymbols

private val Palette = listOf(
    0xFFE0703A, 0xFFD94F70, 0xFF8E6CD8, 0xFF4C7BD9, 0xFF2BA6A0, 0xFF4FA35B, 0xFFC9A227, 0xFF7A7A85,
).map { it.toInt() }

/**
 * 添加数据库 / 数据库设置: which date property puts pages on the timeline, which property
 * means done (and its done / to-do values), a colour, and which properties the item sheet
 * shows besides 状态 and 时间.
 */
@Composable
fun DatabaseSetupScreen(nav: Nav, databaseId: String?) {
    var id by remember { mutableStateOf(databaseId) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(cs.surface)) {
        PawTopBar(if (databaseId == null) "添加数据库" else "数据库", onBack = { nav.pop() })
        val current = id
        if (current == null) Picker(nav) { id = it } else Setup(nav, current, isNew = databaseId == null)
    }
}

@Composable
private fun Picker(nav: Nav, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var list by remember { mutableStateOf<List<DatabaseSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!NotionClient.hasToken) { error = "先在设置里连接 Notion"; return@LaunchedEffect }
        runCatching { NotionClient.searchDatabases() }
            .onSuccess { all -> list = all.filter { s -> NotionRepo.db(s.id) == null } }
            .onFailure { error = it.message ?: "连不上 Notion" }
    }
    val l = list
    when {
        error != null -> Column(Modifier.padding(24.dp)) {
            Text(error!!, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { nav.pop(); nav.push("settings") }) { Text("去设置") }
        }
        l == null -> LoadingState()
        l.isEmpty() -> Text("没有可添加的数据库。到设置里重新连接 Notion，在授权页勾上要用的数据库。", Modifier.padding(24.dp), color = cs.onSurfaceVariant)
        else -> { val scroll = rememberScrollState(); Column(Modifier.seamViewport(scroll).verticalScroll(scroll).padding(12.dp)) {
            l.forEach { db ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .seamGraphic()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.surfaceContainer)
                        .clickable { onPick(db.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(db.icon ?: "", Modifier.padding(end = if (db.icon != null) 10.dp else 0.dp))
                    Text(db.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(YoinSymbols.ChevronRight, null, tint = cs.onSurfaceVariant)
                }
            }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(nav: Nav, id: String, isNew: Boolean) {
    val cs = MaterialTheme.colorScheme
    val toast = rememberToast()
    var schema by remember { mutableStateOf<DatabaseSchema?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val existing = remember { NotionRepo.db(id) }
    var dateProp by remember { mutableStateOf(existing?.dateProp) }
    var statusProp by remember { mutableStateOf(existing?.statusProp) }
    var doneValue by remember { mutableStateOf(existing?.doneValue) }
    var todoValue by remember { mutableStateOf(existing?.todoValue) }
    var color by remember { mutableStateOf(existing?.color ?: Palette[NotionRepo.databases.value.size % Palette.size]) }
    var visible by remember { mutableStateOf(existing?.visibleProps.orEmpty()) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        runCatching { NotionRepo.schema(id) }
            .onSuccess { s ->
                schema = s
                if (dateProp == null) dateProp = s.dateProps.firstOrNull()?.name
                if (statusProp == null && existing == null) {
                    // A status property, or a checkbox, is the natural 完成.
                    val st = s.statusProps.firstOrNull { it.type == "status" } ?: s.statusProps.firstOrNull { it.type == "checkbox" }
                    statusProp = st?.name
                    if (st?.type == "status") {
                        doneValue = st.options.lastOrNull()?.name
                        todoValue = st.options.firstOrNull()?.name
                    }
                }
            }
            .onFailure { error = it.message ?: "读不到这个数据库" }
    }

    val s = schema
    if (error != null) { Text(error!!, Modifier.padding(24.dp)); return }
    if (s == null) { LoadingState(); return }

    val scroll = rememberScrollState()
    Column(Modifier.seamViewport(scroll).verticalScroll(scroll).navigationBarsPadding()) {
        Text(s.title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section("日期属性") {
            if (s.dateProps.isEmpty()) Hint("这个数据库没有日期属性，放不上日历。")
            else Choices(s.dateProps.map { it.name to it.name }, dateProp) { dateProp = it }
        }

        Section("完成状态") {
            Choices(listOf<Pair<String?, String>>(null to "没有") + s.statusProps.map { p -> p.name to p.name }, statusProp) { name ->
                statusProp = name
                val p = s.statusProps.firstOrNull { it.name == name }
                doneValue = p?.options?.lastOrNull()?.name
                todoValue = p?.options?.firstOrNull()?.name
            }
            val p = s.statusProps.firstOrNull { it.name == statusProp }
            if (p != null && p.type != "checkbox") {
                Hint("算作完成")
                Choices(p.options.map { it.name to it.name }, doneValue) { doneValue = it }
                Hint("新建时")
                Choices(p.options.map { it.name to it.name }, todoValue) { todoValue = it }
            }
        }

        Section("颜色") {
            FlowRow(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEach { c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c)).clickable { color = c },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c == color) Icon(YoinSymbols.Check, null, tint = Color.White)
                    }
                }
            }
        }

        Section("事项里显示") {
            Hint("状态和时间总会显示。其他属性也可以在事项的 ⋯ 里改。")
            s.props.filter { it.type != "title" && it.name != dateProp && it.name != statusProp }.forEach { p ->
                val on = p.name in visible
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { visible = if (on) visible - p.name else visible + p.name }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (on) cs.onSurface else cs.onSurfaceVariant)
                    Icon(if (on) YoinSymbols.Visibility else YoinSymbols.VisibilityOff, null, tint = if (on) cs.primary else cs.onSurfaceVariant)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                TextButton(onClick = {
                    if (!confirmRemove) { confirmRemove = true; return@TextButton }
                    NotionRepo.remove(id)
                    Agenda.refresh()
                    nav.pop()
                }) { Text(if (confirmRemove) "再点一次移除" else "移除", color = cs.error) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = dateProp != null && s.titleProp != null,
                onClick = {
                    val titleProp = s.titleProp ?: return@Button
                    val date = dateProp ?: return@Button
                    val st = s.statusProps.firstOrNull { it.name == statusProp }
                    NotionRepo.upsert(
                        NotionDb(
                            id = id,
                            title = s.title,
                            color = color,
                            titleProp = titleProp,
                            dateProp = date,
                            statusProp = st?.name,
                            statusType = st?.type,
                            doneValue = if (st?.type == "checkbox") null else doneValue,
                            todoValue = if (st?.type == "checkbox") null else todoValue,
                            visibleProps = visible,
                            enabled = existing?.enabled ?: true,
                        ),
                    )
                    Agenda.refresh()
                    toast(if (isNew) "已添加" else "已保存")
                    nav.pop()
                },
            ) { Text(if (isNew) "添加" else "保存") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

