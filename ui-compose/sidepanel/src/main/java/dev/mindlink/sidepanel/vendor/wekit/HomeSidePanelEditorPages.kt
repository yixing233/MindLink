// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/
//        HomeSidePanelAddPages.kt + HomeSidePanelCardSettings.kt
// MindLink adaptations (2026-11-16): Compose editor pages replacing the removed Java
// LinearLayout editor. Routes/callbacks come from Java snapshots; drag-in is phase two.
// Weather settings support foreground district location and explicit place search.
// 2026-11-22: shared editor scaffold (pinned header with back arrow, scrolling body, pinned
// bottom actions, single inset pass), live card preview, grouped sections, Hitokoto length
// fields + category select-all/clear + unsaved-changes confirmation, weather current city.
// 2026-11-22: image page (picker, scale mode, height slider) and action-card page (reorder,
// delete, add) backed by the Java image_* / *_action callbacks registered in the same change.
// 2026-11-22: all icons from Lucide (dev.mindlink.ui.LucideIcons); hand-drawn arrows removed.
package dev.mindlink.sidepanel.vendor.wekit

import dev.mindlink.ui.LucideIcons
import dev.mindlink.ui.MindLinkCard
import dev.mindlink.ui.LiquidTopBarButton
import dev.mindlink.ui.LiquidTopScaffold
import dev.mindlink.ui.MindLinkListRow
import dev.mindlink.ui.MindLinkSectionHeader
import dev.mindlink.ui.MindLinkSwitchRow
import dev.mindlink.ui.MindLinkTopBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mindlink.sidepanel.R
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val HOME_SIDE_PANEL_CARD_TYPES = listOf(
    HomeSidePanelCardType.DATE_TIME,
    HomeSidePanelCardType.WEATHER,
    HomeSidePanelCardType.WALLET,
    HomeSidePanelCardType.HITOKOTO,
    HomeSidePanelCardType.IMAGE,
    HomeSidePanelCardType.HORIZONTAL_ACTIONS,
    HomeSidePanelCardType.VERTICAL_ACTIONS,
)

/** Wire serial names of the Java codec (HomeSidePanelLayout.CardType.serialName). */
private fun HomeSidePanelCardType.serialName(): String = when (this) {
    HomeSidePanelCardType.DATE_TIME -> "date_time"
    HomeSidePanelCardType.WEATHER -> "weather"
    HomeSidePanelCardType.WALLET -> "wallet"
    HomeSidePanelCardType.HITOKOTO -> "hitokoto"
    HomeSidePanelCardType.IMAGE -> "image"
    HomeSidePanelCardType.HORIZONTAL_ACTIONS -> "horizontal_actions"
    HomeSidePanelCardType.VERTICAL_ACTIONS -> "vertical_actions"
}

// ------------------------------------------------------------------ shared editor frame

/** Content edge shared by header icon, sections and bottom bar (icon button 4dp + 12dp inset). */
private val EDITOR_EDGE = 16.dp
private val EDITOR_SECTION_GAP = 20.dp

/**
 * The host drawer takes horizontal drags to close itself. A horizontal control inside the drawer
 * (slider) claims the gesture on press so the drawer leaves it alone until the finger lifts.
 */
private fun Modifier.claimDrawerGesture(panelState: HomeSidePanelState): Modifier = pointerInput(panelState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        panelState.setGestureOwnership(true)
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            panelState.setGestureOwnership(false)
        }
    }
}

/**
 * Pinned header + scrolling body + optional pinned bottom bar. Top/bottom safe-drawing insets are
 * applied exactly once here; the body receives padding that already includes the bottom inset
 * when there is no bottom bar.
 */
@Composable
private fun HomeSidePanelEditorScaffold(
    title: String,
    onNavigate: () -> Unit,
    navigationIcon: ImageVector = LucideIcons.ArrowLeft,
    navigationLabel: String = stringResource(R.string.wekit_sp_home_side_panel_back),
    bottomBar: (@Composable () -> Unit)? = null,
    body: @Composable (PaddingValues) -> Unit,
) {
    val insets = WindowInsets.safeDrawing
    val bottom = insets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    Column(modifier = Modifier.fillMaxSize()) {
        LiquidTopScaffold(Modifier.weight(1f), header = {
            MindLinkTopBar(title, navigation = { LiquidTopBarButton(navigationIcon, navigationLabel, onNavigate) })
        }) { top ->
            body(
                PaddingValues(
                    start = EDITOR_EDGE,
                    top = top + 4.dp,
                    end = EDITOR_EDGE,
                    bottom = if (bottomBar == null) EDITOR_EDGE + bottom else EDITOR_EDGE,
                ),
            )
        }
        if (bottomBar != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = EDITOR_EDGE, top = 12.dp, end = EDITOR_EDGE, bottom = 12.dp + bottom),
            ) { bottomBar() }
        }
    }
}

@Composable
private fun EditorScrollColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(EDITOR_SECTION_GAP),
        content = content,
    )
}

/** Live preview of the card being edited, rendered with the current (draft) configuration. */
@Composable
private fun EditorPreview(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MindLinkSectionHeader(
            stringResource(R.string.wekit_sp_home_side_panel_editor_preview),
            stringResource(R.string.wekit_sp_home_side_panel_editor_draft_hint),
        )
        content()
    }
}

/** One titled group: rows of the same topic share a single card, separated by dividers. */
@Composable
private fun EditorSection(
    title: String,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MindLinkSectionHeader(title, summary)
        MindLinkCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun EditorDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = EDITOR_EDGE),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ------------------------------------------------------------------ add card

/** Only types missing from this draft may be added. */
@Composable
fun HomeSidePanelAddCardPage(panelState: HomeSidePanelState) {
    val existingTypes = panelState.uiState.renderedLayout.cards.mapTo(mutableSetOf()) { it.type }
    val availableTypes = HOME_SIDE_PANEL_CARD_TYPES.filterNot(existingTypes::contains)
    HomeSidePanelEditorScaffold(
        title = stringResource(R.string.wekit_sp_home_side_panel_add_card),
        onNavigate = { panelState.editCard("") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (availableTypes.isEmpty()) item {
                Text("所有类型的卡片均已添加", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(availableTypes, key = HomeSidePanelCardType::name) { type ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = homeSidePanelCardTypeName(type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { panelState.addCard(type.serialName()) },
                    ) {
                        HomeSidePanelCardPreview(type)
                    }
                }
            }
        }
    }
}

/** Real preview cards per type, mirroring WeKit's AddPages candidates (drag-in is phase two). */
@Composable
private fun HomeSidePanelCardPreview(type: HomeSidePanelCardType) {
    when (type) {
        HomeSidePanelCardType.DATE_TIME -> HomeSidePanelDateTimeCard(
            card = DateTimeCardConfig("preview-date-time"),
            content = DateTimeCardContent.Preview(LocalDateTime.of(2026, 8, 20, 12, 34)),
            editMode = false,
        )
        HomeSidePanelCardType.WEATHER -> HomeSidePanelWeatherCard(
            card = WeatherCardConfig(
                "preview-weather",
                WeatherCity("", "", stringResource(R.string.wekit_sp_home_side_panel_card_weather), null, "preview", null, null),
            ),
            content = WeatherCardContent.Preview(
                WeatherSnapshot(
                    city = WeatherCity("", "", "", null, "preview", null, null),
                    weatherCode = "0",
                    temperature = "26",
                    feelsLike = "27",
                    high = "30",
                    low = "21",
                    humidity = "48",
                    windSpeed = "8",
                    publishedAt = "12:00",
                    fetchedAt = 0L,
                ),
            ),
            editMode = false,
            interactionEnabled = false,
        )
        HomeSidePanelCardType.WALLET -> HomeSidePanelWalletCard(
            card = WalletCardConfig("preview-wallet"),
            content = WalletCardContent.Preview("¥ 1,234.56"),
            editMode = false,
            interactionEnabled = false,
        )
        HomeSidePanelCardType.HITOKOTO -> HomeSidePanelHitokotoCard(
            card = HitokotoCardConfig("preview-hitokoto"),
            content = HitokotoCardContent.Preview(
                HitokotoSnapshot("preview-hitokoto", "The best way out is always through.", "k", "A Servant to Servants", "Robert Frost", null, null, 0L),
            ),
            editMode = false,
            interactionEnabled = false,
        )
        HomeSidePanelCardType.IMAGE -> HomeSidePanelImageCard(
            card = ImageCardConfig("preview-image"),
            imageBitmap = null,
            editMode = false,
            importing = false,
            preview = true,
        )
        HomeSidePanelCardType.HORIZONTAL_ACTIONS -> HomeSidePanelHorizontalActionsCard(
            card = HorizontalActionsCardConfig(
                "preview-horizontal-actions",
                listOf(
                    HomeSidePanelActionConfig("preview-scan", HomeSidePanelActionKind.SCAN),
                    HomeSidePanelActionConfig("preview-wallet", HomeSidePanelActionKind.WALLET),
                    HomeSidePanelActionConfig("preview-favorites", HomeSidePanelActionKind.FAVORITES),
                ),
            ),
            content = HomeSidePanelActionCardContent.Preview,
            editMode = false,
            interactionEnabled = false,
        )
        HomeSidePanelCardType.VERTICAL_ACTIONS -> HomeSidePanelVerticalActionsCard(
            card = VerticalActionsCardConfig(
                "preview-vertical-actions",
                listOf(
                    HomeSidePanelActionConfig("preview-moments", HomeSidePanelActionKind.MOMENTS),
                    HomeSidePanelActionConfig("preview-channels", HomeSidePanelActionKind.CHANNELS),
                    HomeSidePanelActionConfig("preview-mark-read", HomeSidePanelActionKind.MARK_ALL_READ),
                ),
            ),
            content = HomeSidePanelActionCardContent.Preview,
            editMode = false,
            interactionEnabled = false,
        )
    }
}

@Composable
private fun homeSidePanelCardTypeName(type: HomeSidePanelCardType): String = when (type) {
    HomeSidePanelCardType.DATE_TIME -> stringResource(R.string.wekit_sp_home_side_panel_card_date_time)
    HomeSidePanelCardType.WEATHER -> stringResource(R.string.wekit_sp_home_side_panel_card_weather)
    HomeSidePanelCardType.WALLET -> stringResource(R.string.wekit_sp_home_side_panel_card_wallet)
    HomeSidePanelCardType.HITOKOTO -> stringResource(R.string.wekit_sp_home_side_panel_card_hitokoto)
    HomeSidePanelCardType.IMAGE -> stringResource(R.string.wekit_sp_home_side_panel_card_image)
    HomeSidePanelCardType.HORIZONTAL_ACTIONS -> stringResource(R.string.wekit_sp_home_side_panel_card_horizontal_actions)
    HomeSidePanelCardType.VERTICAL_ACTIONS -> stringResource(R.string.wekit_sp_home_side_panel_card_vertical_actions)
}

// ------------------------------------------------------------------ card settings

/** Route dispatch: per-type settings page for the selected draft card. */
@Composable
fun HomeSidePanelCardSettingsPage(state: HomeSidePanelUiState, cardId: String, panelState: HomeSidePanelState) {
    val card = state.renderedLayout.cards.firstOrNull { it.id == cardId }
    if (card == null) {
        HomeSidePanelHome(state = state, panelState = panelState, listState = androidx.compose.foundation.lazy.rememberLazyListState())
        return
    }
    when (card) {
        is DateTimeCardConfig -> HomeSidePanelDateTimeSettings(card, panelState)
        is WalletCardConfig -> HomeSidePanelWalletSettings(card, panelState)
        is WeatherCardConfig -> HomeSidePanelWeatherSettings(card, panelState)
        is HitokotoCardConfig -> HomeSidePanelHitokotoSettings(card, panelState)
        is ImageCardConfig -> HomeSidePanelImageSettings(card, card.id in state.imageImportingCardIds, panelState)
        is HorizontalActionsCardConfig -> HomeSidePanelActionsSettings(
            cardId = card.id,
            title = stringResource(R.string.wekit_sp_home_side_panel_card_horizontal_actions),
            actions = card.actions,
            panelState = panelState,
        ) {
            HomeSidePanelHorizontalActionsCard(
                card = card,
                content = HomeSidePanelActionCardContent.Runtime,
                editMode = false,
                interactionEnabled = false,
            )
        }
        is VerticalActionsCardConfig -> HomeSidePanelActionsSettings(
            cardId = card.id,
            title = stringResource(R.string.wekit_sp_home_side_panel_card_vertical_actions),
            actions = card.actions,
            panelState = panelState,
        ) {
            HomeSidePanelVerticalActionsCard(
                card = card,
                content = HomeSidePanelActionCardContent.Runtime,
                editMode = false,
                interactionEnabled = false,
            )
        }
        else -> HomeSidePanelHome(state = state, panelState = panelState, listState = androidx.compose.foundation.lazy.rememberLazyListState())
    }
}

@Composable
private fun HomeSidePanelDateTimeSettings(card: DateTimeCardConfig, panelState: HomeSidePanelState) {
    HomeSidePanelEditorScaffold(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_date_time),
        onNavigate = { panelState.editCard("") },
    ) { padding ->
        EditorScrollColumn(padding) {
            EditorPreview {
                HomeSidePanelDateTimeCard(card = card, content = DateTimeCardContent.Runtime, editMode = false)
            }
            EditorSection(stringResource(R.string.wekit_sp_home_side_panel_editor_display)) {
                MindLinkSwitchRow(
                    title = stringResource(R.string.wekit_sp_home_side_panel_show_lunar_calendar),
                    checked = card.showLunarCalendar,
                    onChange = { panelState.updateLunar(card.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeSidePanelWalletSettings(card: WalletCardConfig, panelState: HomeSidePanelState) {
    val runtime = (panelState.runtimeState(card.id) as? HomeSidePanelCardRuntimeState.Wallet)?.state
    HomeSidePanelEditorScaffold(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_wallet),
        onNavigate = { panelState.editCard("") },
    ) { padding ->
        EditorScrollColumn(padding) {
            if (runtime != null) {
                EditorPreview {
                    // Preview the state the card opens in, so the switch below is visible immediately.
                    HomeSidePanelWalletCard(
                        card = card,
                        content = WalletCardContent.Runtime(
                            runtime.copy(displayState = HomeSidePanelWalletDisplayState(card.hideBalanceByDefault)),
                        ),
                        editMode = false,
                        interactionEnabled = false,
                    )
                }
            }
            EditorSection(stringResource(R.string.wekit_sp_home_side_panel_editor_display)) {
                MindLinkSwitchRow(
                    title = stringResource(R.string.wekit_sp_home_side_panel_hide_balance_default),
                    checked = card.hideBalanceByDefault,
                    summary = stringResource(R.string.wekit_sp_home_side_panel_hide_balance_summary),
                    onChange = { panelState.updateWalletMask(card.id) },
                )
            }
        }
    }
}

/** Explicit foreground fix or place search; selections remain in the sidebar draft until Save. */
@Composable
private fun HomeSidePanelWeatherSettings(card: WeatherCardConfig, panelState: HomeSidePanelState) {
    var query by remember(card.id) { mutableStateOf("") }
    val runtime = panelState.runtimeState(card.id) as? HomeSidePanelCardRuntimeState.Weather
    val editor = runtime?.editor ?: WeatherPlaceEditor()
    HomeSidePanelEditorScaffold(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_weather),
        onNavigate = { panelState.editCard("") },
    ) { padding ->
        EditorScrollColumn(padding) {
            if (runtime != null) EditorPreview {
                HomeSidePanelWeatherCard(card, WeatherCardContent.Runtime(runtime.state), false, interactionEnabled = false)
            }
            EditorSection("天气位置", "选择后返回编辑页保存") {
                MindLinkListRow(
                    title = card.city?.let(::weatherPlaceLabel) ?: "尚未选择地点",
                    summary = if (editor.current) "跟随当前位置" + (card.city?.accuracy?.let { " · 定位精度约 ${it.roundToInt()} 米" } ?: "")
                        else "固定地点",
                )
                OutlinedButton(
                    onClick = { panelState.locateWeather(card.id) },
                    enabled = !editor.busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = EDITOR_EDGE, vertical = 8.dp),
                ) { Text(if (editor.busy) "正在查询…" else "使用当前位置") }
            }
            EditorSection("手动选择", "输入城市或区县名称，例如“西安市雁塔区”，点击搜索后选择地点") {
                Row(Modifier.fillMaxWidth().padding(EDITOR_EDGE), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(80) }, modifier = Modifier.weight(1f),
                        singleLine = true, label = { Text("城市或区县") },
                    )
                    Button(onClick = { panelState.searchWeatherCity(card.id, query.trim()) },
                        enabled = !editor.busy && query.trim().length >= 2, modifier = Modifier.height(56.dp)) { Text("搜索") }
                }
                if (editor.message.isNotBlank()) Text(editor.message,
                    modifier = Modifier.padding(horizontal = EDITOR_EDGE, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                editor.choices.forEachIndexed { index, place ->
                    TextButton(onClick = { panelState.selectWeatherCity(card.id, editor.token, index) },
                        enabled = !editor.busy, modifier = Modifier.fillMaxWidth()) { Text(weatherPlaceLabel(place)) }
                }
                Text("地点数据 © OpenStreetMap contributors", modifier = Modifier.padding(EDITOR_EDGE),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@StringRes
private fun hitokotoDraftError(settings: HitokotoSettings): Int? {
    val min = settings.minLength
    val max = settings.maxLength
    return when {
        settings.categories.isEmpty() -> R.string.wekit_sp_home_side_panel_error_no_category
        min != null && max != null && max < min -> R.string.wekit_sp_home_side_panel_error_length_range
        else -> null
    }
}

@Composable
private fun HomeSidePanelHitokotoSettings(card: HitokotoCardConfig, panelState: HomeSidePanelState) {
    var draft by remember(card.id, card.settings) { mutableStateOf(card.settings) }
    var confirmLeave by remember(card.id) { mutableStateOf(false) }
    // Any further edit (or an applied snapshot) dismisses a pending leave confirmation.
    LaunchedEffect(draft, card.settings) { confirmLeave = false }
    val lengthUpperBound = remember(card.settings) {
        maxOf(500, card.settings.minLength ?: 0, card.settings.maxLength ?: 0)
    }
    val dirty = draft != card.settings
    val errorRes = hitokotoDraftError(draft)
    val runtime = (panelState.runtimeState(card.id) as? HomeSidePanelCardRuntimeState.Hitokoto)?.state
    val leave = fun() {
        if (dirty) {
            confirmLeave = true
        } else {
            panelState.editCard("")
        }
    }
    HomeSidePanelEditorScaffold(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_hitokoto),
        onNavigate = leave,
        bottomBar = {
            if (confirmLeave) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.wekit_sp_home_side_panel_unsaved_changes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                confirmLeave = false
                                panelState.editCard("")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.wekit_sp_home_side_panel_discard))
                        }
                        Button(
                            onClick = {
                                panelState.updateHitokoto(card.id, draft)
                                panelState.editCard("")
                            },
                            enabled = errorRes == null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.wekit_sp_home_side_panel_apply_and_back))
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { draft = HitokotoSettings() },
                        enabled = draft != HitokotoSettings(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.wekit_sp_home_side_panel_restore_defaults))
                    }
                    Button(
                        onClick = { panelState.updateHitokoto(card.id, draft) },
                        enabled = dirty && errorRes == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.wekit_sp_home_side_panel_apply))
                    }
                }
            }
        },
    ) { padding ->
        EditorScrollColumn(padding) {
            if (runtime != null) {
                EditorPreview {
                    // Source/author visibility follows the draft; categories and length apply on next fetch.
                    HomeSidePanelHitokotoCard(
                        card = card.copy(settings = draft),
                        content = HitokotoCardContent.Runtime(runtime),
                        editMode = false,
                        interactionEnabled = false,
                    )
                }
            }
            EditorSection(
                stringResource(R.string.wekit_sp_home_side_panel_categories),
                stringResource(R.string.wekit_sp_home_side_panel_categories_selected)
                    .replace("%1\$d", draft.categories.size.toString())
                    .replace("%2\$d", HITOKOTO_CATEGORY_CODES.size.toString()),
            ) {
                Column(modifier = Modifier.padding(start = EDITOR_EDGE, top = 12.dp, end = EDITOR_EDGE, bottom = 4.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HITOKOTO_CATEGORY_CODES.sorted().forEach { code ->
                            FilterChip(
                                selected = code in draft.categories,
                                onClick = {
                                    draft = draft.copy(
                                        categories = if (code in draft.categories) draft.categories - code else draft.categories + code,
                                    )
                                },
                                label = { Text(homeSidePanelHitokotoCategoryName(code)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { draft = draft.copy(categories = emptySet()) },
                            enabled = draft.categories.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.wekit_sp_home_side_panel_clear_all))
                        }
                        TextButton(
                            onClick = { draft = draft.copy(categories = HITOKOTO_CATEGORY_CODES) },
                            enabled = draft.categories != HITOKOTO_CATEGORY_CODES,
                        ) {
                            Text(stringResource(R.string.wekit_sp_home_side_panel_select_all))
                        }
                    }
                }
            }
            EditorSection(stringResource(R.string.wekit_sp_home_side_panel_editor_display)) {
                MindLinkSwitchRow(
                    title = stringResource(R.string.wekit_sp_home_side_panel_show_source),
                    checked = draft.showSource,
                    onChange = { draft = draft.copy(showSource = it) },
                )
                EditorDivider()
                MindLinkSwitchRow(
                    title = stringResource(R.string.wekit_sp_home_side_panel_show_author),
                    checked = draft.showAuthor,
                    onChange = { draft = draft.copy(showAuthor = it) },
                )
            }
            EditorSection(
                stringResource(R.string.wekit_sp_home_side_panel_length),
                stringResource(R.string.wekit_sp_home_side_panel_length_summary),
            ) {
                HitokotoLengthRow(
                    title = stringResource(R.string.wekit_sp_home_side_panel_min_length),
                    value = draft.minLength,
                    upperBound = lengthUpperBound,
                    onChange = { draft = draft.copy(minLength = it) },
                )
                EditorDivider()
                HitokotoLengthRow(
                    title = stringResource(R.string.wekit_sp_home_side_panel_max_length),
                    value = draft.maxLength,
                    upperBound = lengthUpperBound,
                    onChange = { draft = draft.copy(maxLength = it) },
                )
            }
            if (errorRes != null) {
                Text(
                    stringResource(errorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Optional length: empty field means no limit (null), matching the persisted model. */
@Composable
private fun HitokotoLengthRow(title: String, value: Int?, upperBound: Int, onChange: (Int?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EDITOR_EDGE, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value?.toString() ?: "",
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(4)
                onChange(digits.toIntOrNull()?.coerceAtMost(upperBound))
            },
            modifier = Modifier.width(112.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.wekit_sp_home_side_panel_no_limit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// ------------------------------------------------------------------ image

private fun snapImageHeight(value: Float): Int {
    val step = HOME_SIDE_PANEL_IMAGE_HEIGHT_STEP_DP
    return ((value / step).roundToInt() * step)
        .coerceIn(HOME_SIDE_PANEL_IMAGE_MIN_HEIGHT_DP, HOME_SIDE_PANEL_IMAGE_MAX_HEIGHT_DP)
}

private data class ImageScaleOption(
    val mode: HomeSidePanelImageScaleMode,
    @StringRes val title: Int,
    @StringRes val summary: Int,
)

private val IMAGE_SCALE_OPTIONS = listOf(
    ImageScaleOption(
        HomeSidePanelImageScaleMode.CROP,
        R.string.wekit_sp_home_side_panel_image_scale_crop,
        R.string.wekit_sp_home_side_panel_image_scale_crop_summary,
    ),
    ImageScaleOption(
        HomeSidePanelImageScaleMode.FIT,
        R.string.wekit_sp_home_side_panel_image_scale_fit,
        R.string.wekit_sp_home_side_panel_image_scale_fit_summary,
    ),
    ImageScaleOption(
        HomeSidePanelImageScaleMode.FILL_BOUNDS,
        R.string.wekit_sp_home_side_panel_image_scale_stretch,
        R.string.wekit_sp_home_side_panel_image_scale_stretch_summary,
    ),
    ImageScaleOption(
        HomeSidePanelImageScaleMode.AUTO_RATIO,
        R.string.wekit_sp_home_side_panel_image_scale_auto_ratio,
        R.string.wekit_sp_home_side_panel_image_scale_auto_ratio_summary,
    ),
)

@Composable
private fun HomeSidePanelImageSettings(card: ImageCardConfig, importing: Boolean, panelState: HomeSidePanelState) {
    var height by remember(card.id) { mutableFloatStateOf(card.heightDp.toFloat()) }
    var sliding by remember(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.heightDp) { if (!sliding) height = card.heightDp.toFloat() }
    val shownHeight = snapImageHeight(height)
    HomeSidePanelEditorScaffold(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_image),
        onNavigate = { panelState.editCard("") },
    ) { padding ->
        EditorScrollColumn(padding) {
            EditorPreview {
                HomeSidePanelImageCard(
                    card = card.copy(heightDp = shownHeight),
                    imageBitmap = panelState.imageBitmap(card.id),
                    editMode = false,
                    importing = importing,
                    // Without an image the preview says "tap to select", not "enter edit mode".
                    preview = card.imageAssetId == null,
                )
            }
            EditorSection(stringResource(R.string.wekit_sp_home_side_panel_editor_image)) {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !importing) { panelState.selectImage(card.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            stringResource(
                                if (card.imageAssetId == null) {
                                    R.string.wekit_sp_home_side_panel_image_select
                                } else {
                                    R.string.wekit_sp_home_side_panel_image_replace
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = if (importing) {
                        { Text(stringResource(R.string.wekit_sp_home_side_panel_image_importing)) }
                    } else {
                        null
                    },
                    trailingContent = {
                        Icon(LucideIcons.ChevronRight, contentDescription = null)
                    },
                )
            }
            EditorSection(stringResource(R.string.wekit_sp_home_side_panel_image_scale_mode)) {
                IMAGE_SCALE_OPTIONS.forEachIndexed { index, option ->
                    if (index > 0) EditorDivider()
                    val selected = card.scaleMode == option.mode
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                enabled = !importing,
                                role = Role.RadioButton,
                                onClick = { panelState.updateImageScaleMode(card.id, option.mode) },
                            ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(option.title), style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text(stringResource(option.summary), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                    )
                }
            }
            EditorSection(stringResource(R.string.wekit_sp_home_side_panel_image_height)) {
                if (card.scaleMode == HomeSidePanelImageScaleMode.AUTO_RATIO) {
                    MindLinkListRow(title = stringResource(R.string.wekit_sp_home_side_panel_image_height_auto))
                } else {
                    Column(modifier = Modifier.padding(horizontal = EDITOR_EDGE, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.wekit_sp_home_side_panel_image_height),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.wekit_sp_home_side_panel_image_height_value)
                                    .replace("%1\$d", shownHeight.toString()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // Continuous slider snapped to 8dp: 90 discrete steps would draw a wall of ticks.
                        Slider(
                            value = height,
                            onValueChange = {
                                sliding = true
                                height = it
                            },
                            onValueChangeFinished = {
                                sliding = false
                                val committed = snapImageHeight(height)
                                height = committed.toFloat()
                                if (committed != card.heightDp) panelState.updateImageHeight(card.id, committed)
                            },
                            valueRange = HOME_SIDE_PANEL_IMAGE_MIN_HEIGHT_DP.toFloat()..HOME_SIDE_PANEL_IMAGE_MAX_HEIGHT_DP.toFloat(),
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth().claimDrawerGesture(panelState),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ action cards

@Composable
private fun HomeSidePanelActionsSettings(
    cardId: String,
    title: String,
    actions: List<HomeSidePanelActionConfig>,
    panelState: HomeSidePanelState,
    preview: @Composable () -> Unit,
) {
    HomeSidePanelEditorScaffold(
        title = title,
        onNavigate = { panelState.editCard("") },
    ) { padding ->
        EditorScrollColumn(padding) {
            if (actions.isNotEmpty()) EditorPreview(preview)
            EditorSection(
                stringResource(R.string.wekit_sp_home_side_panel_actions_added),
                if (actions.isEmpty()) {
                    null
                } else {
                    stringResource(R.string.wekit_sp_home_side_panel_actions_added_summary)
                        .replace("%1\$d", actions.size.toString())
                },
            ) {
                if (actions.isEmpty()) {
                    MindLinkListRow(title = stringResource(R.string.wekit_sp_home_side_panel_actions_empty))
                }
                actions.forEachIndexed { index, action ->
                    if (index > 0) EditorDivider()
                    val spec = homeSidePanelActionSpec(action.kind)
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(spec.icon, contentDescription = null) },
                        headlineContent = { Text(stringResource(spec.labelRes), style = MaterialTheme.typography.bodyLarge) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { panelState.moveAction(cardId, action.id, index - 1) },
                                    enabled = index > 0,
                                ) {
                                    Icon(LucideIcons.ChevronUp, stringResource(R.string.wekit_sp_home_side_panel_move_up))
                                }
                                IconButton(
                                    onClick = { panelState.moveAction(cardId, action.id, index + 1) },
                                    enabled = index < actions.lastIndex,
                                ) {
                                    Icon(LucideIcons.ChevronDown, stringResource(R.string.wekit_sp_home_side_panel_move_down))
                                }
                                IconButton(onClick = { panelState.removeAction(cardId, action.id) }) {
                                    Icon(
                                        LucideIcons.X,
                                        stringResource(R.string.wekit_sp_home_side_panel_delete_action),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }
            }
            EditorSection(
                stringResource(R.string.wekit_sp_home_side_panel_actions_available),
                stringResource(R.string.wekit_sp_home_side_panel_actions_available_summary),
            ) {
                val present = actions.mapTo(mutableSetOf(), HomeSidePanelActionConfig::kind)
                HomeSidePanelActionKind.values().forEachIndexed { index, kind ->
                    if (index > 0) EditorDivider()
                    val spec = homeSidePanelActionSpec(kind)
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { panelState.addAction(cardId, kind) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(spec.icon, contentDescription = null) },
                        headlineContent = { Text(stringResource(spec.labelRes), style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = if (kind in present) {
                            { Text(stringResource(R.string.wekit_sp_home_side_panel_action_already_added)) }
                        } else {
                            null
                        },
                        trailingContent = {
                            Icon(LucideIcons.Plus, stringResource(R.string.wekit_sp_action_add))
                        },
                    )
                }
            }
        }
    }
}

private val HITOKOTO_CATEGORY_LABELS = linkedMapOf(
    "a" to R.string.wekit_sp_home_side_panel_hitokoto_animation,
    "b" to R.string.wekit_sp_home_side_panel_hitokoto_comics,
    "c" to R.string.wekit_sp_home_side_panel_hitokoto_games,
    "d" to R.string.wekit_sp_home_side_panel_hitokoto_literature,
    "e" to R.string.wekit_sp_home_side_panel_hitokoto_original,
    "f" to R.string.wekit_sp_home_side_panel_hitokoto_web,
    "g" to R.string.wekit_sp_home_side_panel_hitokoto_other,
    "h" to R.string.wekit_sp_home_side_panel_hitokoto_movies,
    "i" to R.string.wekit_sp_home_side_panel_hitokoto_poetry,
    "j" to R.string.wekit_sp_home_side_panel_hitokoto_netease_music,
    "k" to R.string.wekit_sp_home_side_panel_hitokoto_philosophy,
    "l" to R.string.wekit_sp_home_side_panel_hitokoto_witty,
)

@Composable
private fun homeSidePanelHitokotoCategoryName(code: String): String {
    val res = HITOKOTO_CATEGORY_LABELS[code]
    return if (res != null) stringResource(res) else code
}
