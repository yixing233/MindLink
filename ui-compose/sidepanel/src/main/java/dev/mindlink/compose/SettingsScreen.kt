package dev.mindlink.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mindlink.ui.LiquidTopBarButton
import dev.mindlink.ui.LucideIcons
import dev.mindlink.ui.MindLinkCard
import dev.mindlink.ui.MindLinkIconButton
import dev.mindlink.ui.MindLinkListRow
import dev.mindlink.ui.MindLinkPage
import dev.mindlink.ui.MindLinkSectionHeader
import dev.mindlink.ui.MindLinkSwitchRow
import dev.mindlink.ui.MindLinkTheme
import dev.mindlink.ui.MindLinkTopBar

/** Callbacks every row needs; dialogs stay owned by [SettingsScreen]. */
private class RowActions(
    val flag: (Setting.Flag, Boolean) -> Unit,
    val edit: (Setting.Text) -> Unit,
    val button: (String) -> Unit,
)

/**
 * Two levels: an overview grouped by where each feature lives (首页 / 聊天 / 外观 / 隐私与诊断),
 * and one detail page per feature. A feature switch sits on its overview row and again at the top
 * of its detail page; switching it off greys the configuration out instead of hiding it.
 */
@Composable
internal fun SettingsScreen(state: SettingsState) {
    val dark = state.dark || isSystemInDarkTheme()
    MindLinkTheme(dark = dark) {
        var editing by remember { mutableStateOf<Setting.Text?>(null) }
        var consent by remember { mutableStateOf<Setting.Flag?>(null) }
        var logOpen by remember { mutableStateOf(false) }
        var restarting by remember { mutableStateOf(false) }
        val overviewList = rememberLazyListState()
        val actions = remember(state) {
            RowActions(
                flag = { row, checked ->
                    if (row.confirm != null && checked) consent = row else state.toggle(row.key, checked)
                },
                edit = { editing = it },
                button = { id -> if (id == "log") logOpen = true else state.action(id) },
            )
        }

        val page = state.currentPage?.let { state.pageOf(it) }
        MindLinkPage(header = {
            Header(page?.title ?: "设置", onBack = if (page != null) ({ state.back(); Unit }) else null) { restarting = true }
            if (state.pending) {
                PendingBar { restarting = true }
            }
        }) { top ->
            AnimatedContent(
                targetState = page?.id,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (state.forward) 1 else -1
                    (slideInHorizontally(tween(240)) { width -> direction * width / 5 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(200)) { width -> -direction * width / 5 } + fadeOut(tween(160)))
                },
                label = "settings-page",
            ) { id ->
                val target = id?.let { state.pageOf(it) }
                if (target == null) Overview(state, overviewList, actions, top) else DetailPage(target, state, actions, top)
            }
        }

        editing?.let { row ->
            TextDialog(
                row = row,
                current = state.value(row.key),
                onConfirm = { value ->
                    state.write(row.key, value)
                    editing = null
                },
                onCancel = { editing = null },
            )
        }
        consent?.let { row ->
            AlertDialog(
                onDismissRequest = { consent = null },
                title = { Text("启用联网数据？") },
                text = { Text(row.confirm.orEmpty(), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = {
                        state.toggle(row.key, true)
                        consent = null
                    }) { Text("同意并开启") }
                },
                dismissButton = { TextButton(onClick = { consent = null }) { Text("取消") } },
            )
        }
        if (logOpen) {
            AlertDialog(
                onDismissRequest = { logOpen = false },
                title = { Text("日志") },
                text = {
                    Text(
                        state.read("logText").ifBlank { "暂无日志" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = { TextButton(onClick = { logOpen = false }) { Text("关闭") } },
            )
        }
        if (restarting) {
            AlertDialog(
                onDismissRequest = { restarting = false },
                title = { Text("重启微信") },
                text = { Text("微信会立即退出，请手动重新打开。\n\n设置中的部分功能需要微信重启后才能加载。") },
                confirmButton = {
                    TextButton(onClick = {
                        restarting = false
                        state.action("restart")
                    }) { Text("立即重启") }
                },
                dismissButton = { TextButton(onClick = { restarting = false }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?, onRestart: () -> Unit) {
    MindLinkTopBar(
        title,
        navigation = { if (onBack != null) LiquidTopBarButton(LucideIcons.ArrowLeft, "返回", onBack) },
    ) {
        LiquidTopBarButton(LucideIcons.RefreshCw, "重启微信", onRestart)
    }
}

@Composable
private fun PendingBar(onRestart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "存在未生效改动，需要重启微信",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestart) { Text("重启") }
    }
}

// ---------------------------------------------------------------- 总览

@Composable
private fun Overview(state: SettingsState, list: LazyListState, actions: RowActions, top: Dp) {
    val groups = settingsGroups(state)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "status") { StatusCard(state) }
        itemsIndexed(groups, key = { _, group -> "group:" + group.title }) { _, group ->
            Column(Modifier.fillMaxWidth()) {
                MindLinkSectionHeader(group.title)
                MindLinkCard(modifier = Modifier.fillMaxWidth()) {
                    group.pages.forEachIndexed { index, page ->
                        if (index > 0) RowDivider()
                        OverviewRow(page, state, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: SettingsState) {
    val version = state.read("version")
    val activation = state.read("activation")
    MindLinkCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                if (version.isBlank()) "WeLiquid" else "WeLiquid $version",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (activation.isNotBlank()) {
                Text(
                    activation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Overview icon per feature page (Lucide, ISC). Unknown ids simply render without an icon. */
private fun pageIcon(id: String): ImageVector? = when (id) {
    "top_bar" -> LucideIcons.PanelTop
    "bottom_bar" -> LucideIcons.PanelBottom
    "conversation_filter" -> LucideIcons.Folders
    "home_side_panel" -> LucideIcons.PanelLeft
    "mark_all_read" -> LucideIcons.CheckCheck
    "chat_header" -> LucideIcons.AppWindow
    "chat_toolbar" -> LucideIcons.Toolbox
    "chat_footer" -> LucideIcons.Keyboard
    "bubble" -> LucideIcons.MessageCircle
    MATERIAL_PAGE -> LucideIcons.Layers
    "background" -> LucideIcons.Wallpaper
    "network" -> LucideIcons.Globe
    "diagnostics" -> LucideIcons.Bug
    else -> null
}

/** Tonal rounded tile, the usual settings-list leading icon. */
@Composable
private fun PageIcon(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Plain switch pages toggle on tap. Pages with configuration open on tap; when they also have a
 * feature switch it sits behind a divider so tapping the text never flips the feature by accident.
 */
@Composable
private fun OverviewRow(page: SettingsPage, state: SettingsState, actions: RowActions) {
    val master = page.master
    val leading: (@Composable () -> Unit)? = pageIcon(page.id)?.let { icon -> { PageIcon(icon) } }
    if (!page.navigable && master != null) {
        MindLinkSwitchRow(page.title, state.flag(master.key), page.summary, leading = leading) { actions.flag(master, it) }
        return
    }
    val summary = page.status?.invoke(state) ?: page.summary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).clickable { state.open(page.id) }) {
            MindLinkListRow(
                title = page.title,
                summary = summary,
                trailing = if (master == null) chevronTrailing else null,
                leading = leading,
            )
        }
        if (master != null) {
            VerticalDivider(
                Modifier.height(32.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Switch(
                checked = state.flag(master.key),
                onCheckedChange = { actions.flag(master, it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 详情页

@Composable
private fun DetailPage(page: SettingsPage, state: SettingsState, actions: RowActions, top: Dp) {
    val pageOn = page.master?.let { state.flag(it.key) } ?: true
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "intro") {
            Text(
                page.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        page.master?.let { master ->
            item(key = "master") { MasterCard(master, state, actions) }
        }
        itemsIndexed(page.sections, key = { index, section -> page.id + ":" + (section.title ?: index.toString()) }) { _, section ->
            SectionCard(section, pageOn, state, actions)
        }
    }
}

@Composable
private fun MasterCard(master: Setting.Flag, state: SettingsState, actions: RowActions) {
    val checked = state.flag(master.key)
    MindLinkCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        MindLinkSwitchRow(
            master.title,
            checked,
            master.summary ?: if (checked) null else "开启后才能调整下面的设置，已保存的参数会保留",
        ) { actions.flag(master, it) }
    }
}

@Composable
private fun SectionCard(section: Section, parentOn: Boolean, state: SettingsState, actions: RowActions) {
    val sectionOn = section.master?.let { state.flag(it.key) } ?: true
    val enabled = parentOn && sectionOn
    Column(Modifier.fillMaxWidth()) {
        if (section.title != null) MindLinkSectionHeader(section.title, section.summary)
        MindLinkCard(modifier = Modifier.fillMaxWidth()) {
            if (section.title == null && section.summary != null) {
                Text(
                    section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            section.master?.let { master ->
                MindLinkSwitchRow(master.title, sectionOn, master.summary, enabled = parentOn) { actions.flag(master, it) }
                if (section.rows.isNotEmpty()) RowDivider()
            }
            Column(Modifier.alpha(if (enabled) 1f else 0.6f)) {
                section.rows.forEachIndexed { index, row ->
                    if (index > 0) RowDivider()
                    SettingRow(row, enabled, state, actions)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(row: Setting, enabled: Boolean, state: SettingsState, actions: RowActions) {
    when (row) {
        is Setting.Flag -> MindLinkSwitchRow(row.title, state.flag(row.key), row.summary, enabled) { actions.flag(row, it) }
        is Setting.Range -> RangeRow(row, state.number(row.key, row.fallback?.invoke(state) ?: row.min), state, enabled)
        is Setting.Pick -> PickRow(row, state.value(row.key), enabled) { state.write(row.key, it) }
        is Setting.Text -> TextRow(row, state.value(row.key), enabled) { actions.edit(row) }
        is Setting.Photo -> PhotoRow(row, enabled) { state.pick(row.key) }
        is Setting.Button -> ButtonRow(row, enabled) { actions.button(row.id) }
        is Setting.Note -> Text(
            row.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        is Setting.Tools -> ToolsRow(row, state, enabled)
        is Setting.Link -> LinkRow(row, state)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(LucideIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val chevronTrailing: @Composable () -> Unit = { Chevron() }

/** Shared settings stay reachable even when the linking feature is off. */
@Composable
private fun LinkRow(row: Setting.Link, state: SettingsState) {
    val target = state.pageOf(row.target) ?: return
    MindLinkListRow(
        title = row.title,
        summary = target.status?.invoke(state) ?: target.summary,
        modifier = Modifier.clickable { state.open(row.target) },
        trailing = chevronTrailing,
    )
}

@Composable
private fun RangeRow(row: Setting.Range, initial: Int, state: SettingsState, enabled: Boolean) {
    var draft by remember(row.key, initial) { mutableStateOf(initial.coerceIn(row.min, row.max)) }
    // Blank value with a fallback = following the default; dragging stores an explicit value.
    val followsDefault = row.fallback != null && state.value(row.key).isBlank()
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (row.fallback != null && !followsDefault) {
                TextButton(onClick = { state.write(row.key, "") }, enabled = enabled) { Text("恢复默认") }
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(if (followsDefault) row.format(draft) + " · 默认" else row.format(draft),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Slider(
            value = draft.toFloat(),
            onValueChange = { draft = it.toInt().coerceIn(row.min, row.max) },
            onValueChangeFinished = {
                state.write(row.key, draft.toString())
                draft = state.number(row.key, draft)
            },
            valueRange = row.min.toFloat()..row.max.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun PickRow(row: Setting.Pick, current: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            row.options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == current,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, row.options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun TextRow(row: Setting.Text, current: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            current.ifBlank { row.placeholder },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PhotoRow(row: Setting.Photo, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text("选择图片") }
    }
}

@Composable
private fun ButtonRow(row: Setting.Button, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (row.primary) {
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(row.title) }
        } else {
            FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(row.title) }
        }
        if (row.summary != null) {
            Text(
                row.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ToolsRow(row: Setting.Tools, state: SettingsState, enabled: Boolean) {
    val names = remember(state.data, state.actionRevision) { state.read("tools").split(",").filter { it.isNotBlank() } }
    val csv = state.value(row.key)
    val selected = if (csv.isBlank()) names.toSet() else csv.split(",").filter { it.isNotBlank() }.toSet()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        names.forEachIndexed { index, name ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                MindLinkIconButton(
                    LucideIcons.ChevronRight, "上移",
                    { if (enabled && index > 0) state.action("toolUp:$name") }, Modifier.rotate(-90f),
                )
                MindLinkIconButton(
                    LucideIcons.ChevronRight, "下移",
                    { if (enabled && index < names.lastIndex) state.action("toolDown:$name") }, Modifier.rotate(90f),
                )
                Switch(
                    checked = name in selected,
                    enabled = enabled,
                    onCheckedChange = { checked ->
                        val next = if (checked) selected + name else selected - name
                        state.write(row.key, names.filter { it in next }.joinToString(",").ifEmpty { "__none__" })
                    },
                )
            }
        }
    }
}

@Composable
private fun TextDialog(row: Setting.Text, current: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember(row.key, current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(row.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(row.placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
