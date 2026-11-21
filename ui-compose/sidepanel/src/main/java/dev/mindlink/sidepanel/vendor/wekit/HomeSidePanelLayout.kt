// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelLayout.kt
// MindLink adaptations (2026-11-14): retain original card/action/image models and constants; serialization/codec/host migration omitted; config-only weather city nullable during loading
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import java.util.UUID

const val HOME_SIDE_PANEL_LAYOUT_VERSION = 1
const val HOME_SIDE_PANEL_IMAGE_MIN_HEIGHT_DP = 80
const val HOME_SIDE_PANEL_IMAGE_MAX_HEIGHT_DP = 800
const val HOME_SIDE_PANEL_IMAGE_HEIGHT_STEP_DP = 8
const val HOME_SIDE_PANEL_IMAGE_MAX_ASPECT_RATIO = 100

fun interface HomeSidePanelIdGenerator {
    fun nextId(): String
}

object UuidHomeSidePanelIdGenerator : HomeSidePanelIdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
data class HomeSidePanelLayout(
    val version: Int = HOME_SIDE_PANEL_LAYOUT_VERSION,
    val cards: List<HomeSidePanelCardConfig>,
)
enum class HomeSidePanelActionKind {
    ADD_FRIEND,
    SCAN,
    MOMENTS,
    WALLET,
    CHANNELS,
    WECHAT_SETTINGS,
    FAVORITES,
    WEKIT_SETTINGS,
    RESTART_WECHAT,
    FORCE_STOP_WECHAT,
    MARK_ALL_READ,
}
enum class HomeSidePanelCardType {
    DATE_TIME,
    WEATHER,
    WALLET,
    HITOKOTO,
    IMAGE,
    HORIZONTAL_ACTIONS,
    VERTICAL_ACTIONS,
}
data class HomeSidePanelActionConfig(
    val id: String,
    val kind: HomeSidePanelActionKind,
)
sealed class HomeSidePanelCardConfig {
    abstract val id: String
    abstract val type: HomeSidePanelCardType
}
data class DateTimeCardConfig(
    override val id: String,
    val showLunarCalendar: Boolean = false,
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.DATE_TIME
}
data class WeatherCardConfig(
    override val id: String,
    val city: WeatherCity? = null,
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.WEATHER
}
data class WalletCardConfig(
    override val id: String,
    val hideBalanceByDefault: Boolean = false,
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.WALLET
}
data class HitokotoCardConfig(
    override val id: String,
    val settings: HitokotoSettings = HitokotoSettings(),
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.HITOKOTO
}
enum class HomeSidePanelImageScaleMode {
    CROP,
    FIT,
    FILL_BOUNDS,
    AUTO_RATIO,
}
data class ImageCardConfig(
    override val id: String,
    val imageAssetId: String? = null,
    val imageWidthPx: Int? = null,
    val imageHeightPx: Int? = null,
    val heightDp: Int = 240,
    val scaleMode: HomeSidePanelImageScaleMode = HomeSidePanelImageScaleMode.CROP,
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.IMAGE
}
data class HorizontalActionsCardConfig(
    override val id: String,
    val actions: List<HomeSidePanelActionConfig>,
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.HORIZONTAL_ACTIONS
}
data class VerticalActionsCardConfig(
    override val id: String,
    val actions: List<HomeSidePanelActionConfig>,
) : HomeSidePanelCardConfig() {
    override val type: HomeSidePanelCardType = HomeSidePanelCardType.VERTICAL_ACTIONS
}

fun isHomeSidePanelImageAspectRatioSupported(width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0) return false
    val longer = maxOf(width, height).toLong()
    val shorter = minOf(width, height).toLong()
    return longer <= shorter * HOME_SIDE_PANEL_IMAGE_MAX_ASPECT_RATIO
}
