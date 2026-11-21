package dev.mindlink.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Refresh
import com.composables.icons.materialsymbols.outlined.Chevron_right
import dev.mindlink.ui.MindLinkCard
import dev.mindlink.ui.MindLinkPage
import dev.mindlink.ui.MindLinkSectionHeader
import dev.mindlink.ui.MindLinkSwitchRow
import dev.mindlink.ui.MindLinkTheme
import dev.mindlink.ui.MindLinkIconButton
import dev.mindlink.ui.MindLinkTopBar

@Composable
internal fun SettingsScreen(state: SettingsState) {
    val dark = state.dark || isSystemInDarkTheme()
    MindLinkTheme(dark = dark) {
        val colors = MaterialTheme.colorScheme
        var editing by remember { mutableStateOf<Setting.Text?>(null) }
        var consent by remember { mutableStateOf<Setting.Flag?>(null) }
        var logOpen by remember { mutableStateOf(false) }
        var restarting by remember { mutableStateOf(false) }

        MindLinkPage {
                Header { restarting = true }
                Tabs(state)
                if (state.pending) {
                    PendingBar { restarting = true }
                }
                val sections = sectionsFor(state.tabIndex, state)
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sections.size) { index ->
                        val section = sections[index]
                        SectionBlock(section) { row ->
                            when (row) {
                                is Setting.Flag -> FlagRow(row, state.flag(row.key)) { checked ->
                                    if (row.confirm != null && checked) consent = row
                                    else state.toggle(row.key, checked)
                                }
                                is Setting.Range -> RangeRow(row, state.number(row.key, row.min), state)
                                is Setting.Pick -> PickRow(row, state.value(row.key)) { state.write(row.key, it) }
                                is Setting.Text -> TextRow(row, state.value(row.key)) { editing = row }
                                is Setting.Photo -> PhotoRow(row) { state.pick(row.key) }
                                is Setting.Button -> ButtonRow(row) {
                                    if (row.id == "log") logOpen = true else state.action(row.id)
                                }
                                is Setting.Note -> Text(
                                    row.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                                is Setting.Tools -> ToolsRow(row, state)
                            }
                        }
                    }
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
                title = { Text("日志与脱敏诊断") },
                text = {
                    Text(
                        state.read("logText").ifBlank { "暂无日志" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = { TextButton(onClick = { logOpen = false }) { Text("关闭") } },
                dismissButton = {
                    TextButton(onClick = { state.action("copyDiagnostic"); logOpen = false }) { Text("复制诊断") }
                },
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
private fun Header(onRestart: () -> Unit) {
    MindLinkTopBar("设置") {
        MindLinkIconButton(MaterialSymbols.Outlined.Refresh, "重启微信", onRestart)
    }
}

@Composable
private fun Tabs(state: SettingsState) {
    PrimaryTabRow(
        selectedTabIndex = state.tabIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        settingsTabs.forEachIndexed { index, label ->
            Tab(selected = state.tabIndex == index, onClick = { state.selectTab(index) }, text = { Text(label) })
        }
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

@Composable
private fun SectionBlock(section: Section, row: @Composable (Setting) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        MindLinkSectionHeader(section.title, section.summary)
        MindLinkCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                section.rows.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    row(entry)
                }
            }
        }
    }
}

@Composable
private fun FlagRow(row: Setting.Flag, checked: Boolean, onChange: (Boolean) -> Unit) {
    MindLinkSwitchRow(row.title, checked, row.summary, onChange)
}

@Composable
private fun RangeRow(row: Setting.Range, initial: Int, state: SettingsState) {
    var draft by remember(row.key, initial) { mutableStateOf(initial.coerceIn(row.min, row.max)) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
        Text(
            "${row.title}  ${row.format(draft)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = draft.toFloat(),
            onValueChange = { draft = it.toInt().coerceIn(row.min, row.max) },
            onValueChangeFinished = {
                state.write(row.key, draft.toString())
                draft = state.number(row.key, draft)
            },
            valueRange = row.min.toFloat()..row.max.toFloat(),
        )
    }
}

@Composable
private fun PickRow(row: Setting.Pick, current: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            row.options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == current,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, row.options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun TextRow(row: Setting.Text, current: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
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
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun PhotoRow(row: Setting.Photo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onClick) { Text("选择图片") }
    }
}

@Composable
private fun ButtonRow(row: Setting.Button, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (row.primary) {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(row.title) }
        } else {
            FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(row.title) }
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
private fun ToolsRow(row: Setting.Tools, state: SettingsState) {
    val names = remember(state.data) { state.read("tools").split(",").filter { it.isNotBlank() } }
    val csv = state.value(row.key)
    val enabled = if (csv.isBlank()) names.toSet() else csv.split(",").filter { it.isNotBlank() }.toSet()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "长按工具栏图标也可拖动换位",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        names.forEach { name ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                MindLinkIconButton(
                    MaterialSymbols.Outlined.Chevron_right, "上移",
                    { state.action("toolUp:$name") }, Modifier.rotate(-90f),
                )
                MindLinkIconButton(
                    MaterialSymbols.Outlined.Chevron_right, "下移",
                    { state.action("toolDown:$name") }, Modifier.rotate(90f),
                )
                Switch(
                    checked = name in enabled,
                    onCheckedChange = { checked ->
                        val next = if (checked) enabled + name else enabled - name
                        state.write(row.key, names.filter { it in next }.joinToString(","))
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
