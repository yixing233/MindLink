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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F0CE),
    onPrimaryContainer = Color(0xFF08251A),
    secondary = Color(0xFF4B6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E9DD),
    onSecondaryContainer = Color(0xFF17281E),
    tertiary = Color(0xFF2F7F7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB7EDE9),
    onTertiaryContainer = Color(0xFF042722),
    background = Color(0xFFF2F1ED),
    onBackground = Color(0xFF14171A),
    surface = Color(0xFFF2F1ED),
    onSurface = Color(0xFF14171A),
    surfaceVariant = Color(0xFFDDE8E0),
    onSurfaceVariant = Color(0xFF54615A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAF7),
    surfaceContainer = Color(0xFFEFF3EF),
    surfaceContainerHigh = Color(0xFFEAEFEA),
    surfaceContainerHighest = Color(0xFFE3E9E4),
    outline = Color(0xFF6F7F74),
    outlineVariant = Color(0xFFC6D4C9),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD4A0),
    onPrimary = Color(0xFF102016),
    primaryContainer = Color(0xFF1B5C41),
    onPrimaryContainer = Color(0xFFA6F0C4),
    secondary = Color(0xFFB4CCBD),
    onSecondary = Color(0xFF1E2B22),
    secondaryContainer = Color(0xFF33443A),
    onSecondaryContainer = Color(0xFFD0E4D8),
    tertiary = Color(0xFF89D4CF),
    onTertiary = Color(0xFF062321),
    tertiaryContainer = Color(0xFF1D4844),
    onTertiaryContainer = Color(0xFFA8ECE7),
    background = Color(0xFF0E100F),
    onBackground = Color(0xFFF2F4F2),
    surface = Color(0xFF0E100F),
    onSurface = Color(0xFFF2F4F2),
    surfaceVariant = Color(0xFF202B23),
    onSurfaceVariant = Color(0xFF98A29C),
    surfaceContainerLowest = Color(0xFF080A09),
    surfaceContainerLow = Color(0xFF151A17),
    surfaceContainer = Color(0xFF191F1B),
    surfaceContainerHigh = Color(0xFF222925),
    surfaceContainerHighest = Color(0xFF2B332E),
    outline = Color(0xFF86968B),
    outlineVariant = Color(0xFF3B4A40),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
internal fun SettingsScreen(state: SettingsState) {
    val dark = state.dark || isSystemInDarkTheme()
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        var editing by remember { mutableStateOf<Setting.Text?>(null) }
        var consent by remember { mutableStateOf<Setting.Flag?>(null) }
        var logOpen by remember { mutableStateOf(false) }
        var restarting by remember { mutableStateOf(false) }

        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Header(state) { restarting = true }
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
private fun Header(state: SettingsState, onRestart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "设置",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestart) { Text("重启") }
    }
}

@Composable
private fun Tabs(state: SettingsState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        settingsTabs.forEachIndexed { index, label ->
            val selected = state.tabIndex == index
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).clickable { state.selectTab(index) },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 9.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
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
        Text(
            section.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        if (section.summary != null) {
            Text(
                section.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
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
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            if (row.summary != null) {
                Text(
                    row.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.options.forEach { (value, label) ->
                val selected = value == current
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).clickable { onSelect(value) },
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
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
            modifier = Modifier.width(180.dp),
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
                TextButton(onClick = { state.action("toolUp:$name") }) { Text("▲") }
                TextButton(onClick = { state.action("toolDown:$name") }) { Text("▼") }
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
