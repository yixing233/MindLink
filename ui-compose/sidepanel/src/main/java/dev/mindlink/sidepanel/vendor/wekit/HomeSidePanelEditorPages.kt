// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/
//        HomeSidePanelAddPages.kt + HomeSidePanelCardSettings.kt
// MindLink adaptations (2026-11-16): Compose editor pages replacing the removed Java
// LinearLayout editor. Routes/callbacks come from Java snapshots; drag-in is phase two.
// Weather settings keep the existing city-code input (no city database ported yet).
package dev.mindlink.sidepanel.vendor.wekit

import dev.mindlink.ui.MindLinkCard
import dev.mindlink.ui.MindLinkSwitchRow
import dev.mindlink.ui.MindLinkTopBar
import dev.mindlink.ui.MindLinkIconButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Close
import dev.mindlink.sidepanel.R
import java.time.LocalDateTime

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

@Composable
private fun HomeSidePanelEditorHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MindLinkTopBar(
        title, modifier,
        navigation = {
            MindLinkIconButton(
                MaterialSymbols.Outlined.Close,
                stringResource(R.string.wekit_sp_home_side_panel_discard_editing),
                onBack,
            )
        },
    )
}

/** Add-card page: every card type shows a real preview card; tapping appends it to the draft. */
@Composable
fun HomeSidePanelAddCardPage(panelState: HomeSidePanelState) {
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Column(modifier = Modifier.fillMaxSize()) {
        HomeSidePanelEditorHeader(
            title = stringResource(R.string.wekit_sp_home_side_panel_add_card),
            onBack = panelState::discardEditing,
            modifier = Modifier
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 4.dp,
                end = 18.dp,
                bottom = 18.dp + bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(HOME_SIDE_PANEL_CARD_TYPES, key = HomeSidePanelCardType::name) { type ->
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
        else -> HomeSidePanelHome(state = state, panelState = panelState, listState = androidx.compose.foundation.lazy.rememberLazyListState())
    }
}

@Composable
private fun HomeSidePanelDateTimeSettings(card: DateTimeCardConfig, panelState: HomeSidePanelState) {
    SettingsColumn(title = stringResource(R.string.wekit_sp_home_side_panel_card_date_time), panelState = panelState) {
        SettingsRow(
            title = stringResource(R.string.wekit_sp_home_side_panel_show_lunar_calendar),
            checked = card.showLunarCalendar,
            onChange = { panelState.updateLunar(card.id) },
        )
    }
}

@Composable
private fun HomeSidePanelWalletSettings(card: WalletCardConfig, panelState: HomeSidePanelState) {
    SettingsColumn(title = stringResource(R.string.wekit_sp_home_side_panel_card_wallet), panelState = panelState) {
        SettingsRow(
            title = stringResource(R.string.wekit_sp_home_side_panel_hide_balance_default),
            checked = card.hideBalanceByDefault,
            onChange = { panelState.updateWalletMask(card.id) },
        )
    }
}

/** Weather city settings: the persisted Java model is a city code string, so this page
 *  keeps a code input (WeKit's city-database search is not ported in phase one). */
@Composable
private fun HomeSidePanelWeatherSettings(card: WeatherCardConfig, panelState: HomeSidePanelState) {
    var code by remember(card.id, card.city?.cityNum) { mutableStateOf(card.city?.cityNum ?: "") }
    LaunchedEffect(card.city?.cityNum) { if (card.city?.cityNum != null) code = card.city.cityNum }
    SettingsColumn(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_weather),
        panelState = panelState,
        scroll = true,
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.wekit_sp_home_side_panel_city_code_label)) },
        )
        Button(
            onClick = { panelState.updateWeatherCity(card.id, code.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = code.isNotBlank(),
        ) {
            Text(stringResource(R.string.wekit_sp_home_side_panel_city_code_apply))
        }
    }
}

@Composable
private fun HomeSidePanelHitokotoSettings(card: HitokotoCardConfig, panelState: HomeSidePanelState) {
    var draft by remember(card.id, card.settings) { mutableStateOf(card.settings) }
    val lengthUpperBound = remember(card.settings) {
        maxOf(500, card.settings.minLength ?: 0, card.settings.maxLength ?: 0)
    }
    SettingsColumn(
        title = stringResource(R.string.wekit_sp_home_side_panel_card_hitokoto),
        panelState = panelState,
        scroll = true,
    ) {
        Text(stringResource(R.string.wekit_sp_home_side_panel_categories), style = MaterialTheme.typography.titleMedium)
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
        SettingsRow(
            title = stringResource(R.string.wekit_sp_home_side_panel_show_source),
            checked = draft.showSource,
            onChange = { draft = draft.copy(showSource = !draft.showSource) },
        )
        SettingsRow(
            title = stringResource(R.string.wekit_sp_home_side_panel_show_author),
            checked = draft.showAuthor,
            onChange = { draft = draft.copy(showAuthor = !draft.showAuthor) },
        )
        Text(
            stringResource(R.string.wekit_sp_home_side_panel_hitokoto_length_summary)
                .replace("%1\$d", (draft.minLength ?: 0).toString())
                .replace("%2\$d", (draft.maxLength ?: lengthUpperBound).toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { draft = HitokotoSettings() },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.wekit_sp_home_side_panel_restore_defaults))
            }
            Button(
                onClick = { panelState.updateHitokoto(card.id, draft) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.wekit_sp_home_side_panel_save_editing))
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

@Composable
private fun SettingsColumn(
    title: String,
    panelState: HomeSidePanelState,
    scroll: Boolean = false,
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        HomeSidePanelEditorHeader(
            title = title,
            onBack = { panelState.editCard("") },
            modifier = Modifier
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
        content()
    }
    if (scroll) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { body() }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom).asPaddingValues())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { body() }
    }
}

@Composable
private fun SettingsRow(title: String, checked: Boolean, onChange: () -> Unit) {
    MindLinkCard(modifier = Modifier.fillMaxWidth()) {
        MindLinkSwitchRow(title, checked, onChange = { onChange() })
    }
}
