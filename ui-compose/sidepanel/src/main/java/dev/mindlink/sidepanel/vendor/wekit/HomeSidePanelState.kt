// SPDX-License-Identifier: GPL-3.0-only
// Platform binding for the copied WeKit components. No service, account query or external request.
package dev.mindlink.sidepanel.vendor.wekit

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Render-only subset of the original WeKit state; persistence/editing/actions belong to Java. */
data class HomeSidePanelUiState(
    val profile: HomeSidePanelProfile,
    val renderedLayout: HomeSidePanelLayout,
    val runtimeStates: Map<String, HomeSidePanelCardRuntimeState>,
    val route: HomeSidePanelRoute,
    val editing: Boolean,
    val initialized: Boolean,
    val imageImportingCardIds: Set<String> = emptySet(),
)

sealed interface HomeSidePanelRoute {
    data object Home : HomeSidePanelRoute
    /** Editor root: card list with edit badges. */
    data object EditHome : HomeSidePanelRoute
    /** Add-card page: previews of all seven card types. */
    data object AddCard : HomeSidePanelRoute
    /** Per-card settings page (lunar/wallet/weather city/hitokoto). */
    data class CardSettings(val cardId: String) : HomeSidePanelRoute
}

data class HomeSidePanelProfile(
    val wxId: String,
    val nickname: String,
    val avatarBitmap: Bitmap?,
    val status: HomeSidePanelStatusUiState,
)

data class TextStatus(val iconId: String, val description: String, val iconBitmap: Bitmap?)

sealed interface HomeSidePanelStatusUiState {
    data object Loading : HomeSidePanelStatusUiState
    data class Ready(val status: TextStatus) : HomeSidePanelStatusUiState
    data object NoStatus : HomeSidePanelStatusUiState
    data object Error : HomeSidePanelStatusUiState
}

sealed interface HomeSidePanelCardRuntimeState {
    data class Weather(val state: WeatherUiState) : HomeSidePanelCardRuntimeState
    data class Wallet(val state: HomeSidePanelWalletUiState) : HomeSidePanelCardRuntimeState
    data class Hitokoto(val state: HitokotoUiState) : HomeSidePanelCardRuntimeState
}

class HomeSidePanelState(snapshot: Bundle, images: Map<String, Bitmap>, callbacks: Map<String, Runnable>) {
    var uiState by mutableStateOf(readSnapshot(snapshot, images))
        private set
    private var images = images.toMap()
    private var imageKeys = imageKeys(snapshot)
    private var callbacks = callbacks.toMap()
    private var active = false
    private var disposed = false
    var gestureInProgress = false
        private set

    fun update(snapshot: Bundle, images: Map<String, Bitmap>) {
        checkMain()
        if (disposed) return
        val next = readSnapshot(snapshot, images)
        this.images = images.toMap()
        imageKeys = imageKeys(snapshot)
        uiState = next
    }

    fun updateCallbacks(callbacks: Map<String, Runnable>) {
        checkMain()
        if (!disposed) this.callbacks = callbacks.toMap()
    }

    fun lifecycle(active: Boolean) {
        if (!active) setGestureOwnership(false)
        this.active = active && !disposed
    }
    fun dispose() {
        setGestureOwnership(false)
        disposed = true
        active = false
        callbacks = emptyMap()
        images = emptyMap()
        imageKeys = emptyMap()
        // Release all host Bitmaps and data when the owning view has been disposed.
        uiState = HomeSidePanelUiState(
            HomeSidePanelProfile("", "", null, HomeSidePanelStatusUiState.Loading),
            HomeSidePanelLayout(cards = emptyList()), emptyMap(), HomeSidePanelRoute.Home,
            editing = false, initialized = false,
        )
    }

    private fun call(key: String) {
        checkMain()
        if (active && !disposed) callbacks[key]?.run()
    }

    fun runtimeState(id: String) = uiState.runtimeStates[id]
    fun imageBitmap(id: String) = imageKeys[id]?.let { images[it] }?.takeUnless { it.isRecycled }
    fun enterEditing() = call("enter_edit")
    fun moveCard(id: String, destination: Int) = call("move_card:$id:$destination")
    fun setGestureOwnership(owned: Boolean) {
        gestureInProgress = owned && active && !disposed
        call(if (gestureInProgress) "gesture_start" else "gesture_end")
    }
    fun editCard(id: String) = call(if (callbacks.containsKey("card_settings:$id")) "card_settings:$id" else "enter_edit")
    fun removeCard(id: String) = call("delete:$id")
    fun openAddCard() = call("add_card")
    fun addCard(type: String) = call("add_card:$type")
    fun updateLunar(id: String) = call("update_lunar:$id")
    fun updateWalletMask(id: String) = call("update_wallet_mask:$id")
    fun updateWeatherCity(id: String, cityCode: String) {
        dev.mindlink.compose.WeatherCityPayload.CODE = cityCode
        call("update_weather_city:$id")
    }
    fun updateHitokoto(id: String, settings: HitokotoSettings) {
        val payload = dev.mindlink.compose.PendingHitokotoSettings.get()
        payload.categories = settings.categories.joinToString(",") { it }
        payload.minLength = settings.minLength
        payload.maxLength = settings.maxLength
        payload.showSource = settings.showSource
        payload.showAuthor = settings.showAuthor
        call("update_hitokoto_settings:$id")
    }
    fun refreshWeather(id: String) = call("refresh_weather:$id")
    fun toggleWallet(id: String) = call("toggle_wallet:$id")
    fun refreshHitokoto(id: String) = call("refresh_hitokoto:$id")
    fun selectImage(id: String) = call("select_image:$id")
    fun updateImageScaleMode(id: String, mode: HomeSidePanelImageScaleMode) =
        call("image_scale:$id:${mode.name.lowercase(Locale.ROOT)}")
    fun updateImageHeight(id: String, height: Int) = call("image_height:$id:$height")
    fun removeAction(cardId: String, actionId: String) = call("delete_action:$cardId:$actionId")
    fun openAddAction(id: String) = call("add_action:$id")
    fun runAction(cardId: String, actionId: String, kind: HomeSidePanelActionKind) =
        call("run:$cardId:$actionId")
    fun runAction(kind: HomeSidePanelActionKind) = call(if (kind == HomeSidePanelActionKind.SCAN) "scan" else "action:${kind.name}")
    fun openPaymentCode() = call("payment_code")
    fun openPersonalProfile() = call("open_profile")
    fun openStatusEditor() = call("open_status")
    fun refreshStatus() = call("refresh_status")
    fun openPanelSettings() = call("open_settings")
    fun discardEditing() = call("discard_edit")
    fun saveEditing() = call("save_edit")
    fun openPanel() = call("open_panel")

    private fun imageKeys(snapshot: Bundle): Map<String, String> = snapshot.bundles("cards")
        .filter { it.string("cardType") == "image" }
        .mapNotNull { card -> card.optionalString("imageKey")?.let { card.string("id") to it } }
        .toMap()
}

/** Ready is strict: an incomplete data model is never converted into fabricated display fields. */
private fun readSnapshot(values: Bundle, images: Map<String, Bitmap>): HomeSidePanelUiState {
    require(values.integer("schemaVersion") == 1) { "Unsupported side-panel schema" }
    val initialized = values.boolean("initialized")
    val editing = values.boolean("editing")
    val profileValue = values.bundle("profile")
    val profile = HomeSidePanelProfile(
        wxId = profileValue.string("wxId"),
        nickname = profileValue.string("nickname"),
        avatarBitmap = profileValue.optionalString("avatarKey")?.let { images[it] }?.takeUnless { it.isRecycled },
        status = readStatus(profileValue.bundle("status"), images),
    )
    val runtime = linkedMapOf<String, HomeSidePanelCardRuntimeState>()
    val importing = linkedSetOf<String>()
    val cards = values.bundles("cards").map { card ->
        val id = card.string("id").also { require(it.isNotBlank()) { "Card IDs must not be blank" } }
        when (card.string("cardType")) {
            "date_time" -> DateTimeCardConfig(id, card.boolean("showLunarCalendar", false))
            "weather" -> {
                val state = readWeatherState(card.bundle("state"))
                runtime[id] = HomeSidePanelCardRuntimeState.Weather(state)
                @Suppress("DEPRECATION") val configuredCity = card.get("city")
                val city = weatherCardSnapshot(state)?.city ?: (configuredCity as? Bundle)?.let(::readCity)
                WeatherCardConfig(id, city)
            }
            "wallet" -> {
                val state = card.bundle("state")
                val defaultMask = state.boolean("defaultMaskEnabled")
                runtime[id] = HomeSidePanelCardRuntimeState.Wallet(
                    HomeSidePanelWalletUiState(
                        balanceFen = state.optionalLong("balanceFen"),
                        displayState = HomeSidePanelWalletDisplayState(defaultMask, state.boolean("isMasked")),
                    ),
                )
                WalletCardConfig(id, card.boolean("hideBalanceByDefault", defaultMask))
            }
            "hitokoto" -> {
                val settings = readHitokotoSettings(card.bundle("settings"))
                val state = readHitokotoState(card.bundle("state"))
                val invalid = hitokotoSettingsError(settings)
                val rendered = if (invalid == null) state else HitokotoUiState.Error(
                    "一言设置无效：$invalid；请编辑此卡片（原配置未改变）",
                    when (state) {
                        is HitokotoUiState.Ready -> state.snapshot
                        is HitokotoUiState.Error -> state.cached
                        HitokotoUiState.Loading -> null
                    },
                )
                runtime[id] = HomeSidePanelCardRuntimeState.Hitokoto(rendered)
                // Retain the user's model, even when invalid; this binding never persists or requests data.
                HitokotoCardConfig(id, settings)
            }
            "image" -> {
                if (card.boolean("importing", false)) importing += id
                val height = card.integer("heightDp", 240)
                require(height in HOME_SIDE_PANEL_IMAGE_MIN_HEIGHT_DP..HOME_SIDE_PANEL_IMAGE_MAX_HEIGHT_DP &&
                    height % HOME_SIDE_PANEL_IMAGE_HEIGHT_STEP_DP == 0) { "Invalid image height" }
                val widthPx = card.optionalInt("imageWidthPx")
                val heightPx = card.optionalInt("imageHeightPx")
                require((widthPx == null) == (heightPx == null)) { "Image dimensions must both be present or absent" }
                if (widthPx != null && heightPx != null) {
                    require(isHomeSidePanelImageAspectRatioSupported(widthPx, heightPx) &&
                        widthPx.toLong() * heightPx <= 50_000_000L) { "Invalid image dimensions" }
                }
                ImageCardConfig(id, card.optionalString("imageAssetId"), widthPx, heightPx, height,
                    HomeSidePanelImageScaleMode.valueOf(card.string("scaleMode", "crop").uppercase(Locale.ROOT)))
            }
            "horizontal_actions" -> HorizontalActionsCardConfig(id, readActions(card))
            "vertical_actions" -> VerticalActionsCardConfig(id, readActions(card))
            else -> error("Unknown side-panel card type '${card.string("cardType")}'")
        }
    }
    require(cards.map { it.id }.toSet().size == cards.size) { "Duplicate card IDs" }
    val route = when {
        !editing -> HomeSidePanelRoute.Home
        else -> readEditorRoute(values.optionalString("editorRoute"), cards)
    }
    return HomeSidePanelUiState(profile, HomeSidePanelLayout(cards = cards), runtime, route, editing, initialized, importing)
}

/** Editor routes arrive as strings; unknown values degrade to the editor root, never crash. */
private fun readEditorRoute(raw: String?, cards: List<HomeSidePanelCardConfig>): HomeSidePanelRoute = when {
    raw == null -> HomeSidePanelRoute.EditHome
    raw == "add_card" -> HomeSidePanelRoute.AddCard
    raw.startsWith("card_settings:") -> {
        val cardId = raw.removePrefix("card_settings:")
        if (cards.any { it.id == cardId }) HomeSidePanelRoute.CardSettings(cardId) else HomeSidePanelRoute.EditHome
    }
    else -> HomeSidePanelRoute.EditHome
}

private fun readActions(card: Bundle): List<HomeSidePanelActionConfig> = card.bundles("actions").map {
    HomeSidePanelActionConfig(it.string("id").also { id -> require(id.isNotBlank()) },
        HomeSidePanelActionKind.valueOf(it.string("kind")))
}.also { actions -> require(actions.map { it.id }.toSet().size == actions.size) { "Duplicate action IDs" } }

private fun readStatus(value: Bundle, images: Map<String, Bitmap>): HomeSidePanelStatusUiState = when (value.string("kind")) {
    "loading" -> HomeSidePanelStatusUiState.Loading
    "no_status" -> HomeSidePanelStatusUiState.NoStatus
    "error" -> HomeSidePanelStatusUiState.Error
    "ready" -> HomeSidePanelStatusUiState.Ready(TextStatus(value.string("iconId"), value.string("description"),
        value.optionalString("iconKey")?.let { images[it] }?.takeUnless { it.isRecycled }))
    else -> error("Unknown status state")
}

private fun readCity(value: Bundle) = WeatherCity(
    value.string("countryCode"), value.string("province"), value.string("city"),
    value.optionalString("district"), value.string("cityNum"), value.optionalDouble("latitude"), value.optionalDouble("longitude"),
)

private fun readWeatherSnapshot(value: Bundle) = WeatherSnapshot(
    city = readCity(value.bundle("city")),
    weatherCode = value.nonBlank("weatherCode"), temperature = value.nonBlank("temperature"),
    feelsLike = value.nonBlank("feelsLike"), high = value.nonBlank("high"), low = value.nonBlank("low"),
    humidity = value.nonBlank("humidity"), windSpeed = value.nonBlank("windSpeed"),
    publishedAt = value.nonBlank("publishedAt"), fetchedAt = value.long("fetchedAt"),
)

private fun readWeatherState(value: Bundle): WeatherUiState = when (value.string("kind")) {
    "loading" -> WeatherUiState.Loading
    "ready" -> WeatherUiState.Ready(readWeatherSnapshot(value.bundle("snapshot")), value.boolean("refreshing", false))
    "error" -> WeatherUiState.Error(value.string("message"), value.optionalBundle("cached")?.let(::readWeatherSnapshot))
    else -> error("Unknown weather state")
}

private fun readHitokotoSettings(value: Bundle): HitokotoSettings {
    val raw = value.string("categories", "").trim()
    val categories = if (raw.isEmpty()) HITOKOTO_CATEGORY_CODES else raw.split(Regex("[,，\\s]+")).filter { it.isNotEmpty() }.toSet()
    val min = value.optionalInt("minLength")
    val max = value.optionalInt("maxLength")
    return HitokotoSettings(categories, min, max, value.boolean("showSource", true), value.boolean("showAuthor", true))
}

/** The original WeKit validation order, local to this render-only binding. Never normalize saved values. */
private fun hitokotoSettingsError(settings: HitokotoSettings): String? = when {
    settings.minLength != null && settings.minLength < 0 || settings.maxLength != null && settings.maxLength < 0 ->
        "长度不能为负数"
    settings.categories.isEmpty() -> "至少选择一个分类"
    settings.categories.any { it !in HITOKOTO_CATEGORY_CODES } -> "包含不支持的一言分类"
    settings.minLength != null && settings.maxLength != null && settings.maxLength < settings.minLength ->
        "最大长度不能小于最小长度"
    else -> null
}

private fun readHitokotoSnapshot(value: Bundle) = HitokotoSnapshot(
    uuid = value.nonBlank("uuid"), text = value.nonBlank("text"), type = value.optionalString("type"),
    source = value.optionalString("source"), author = value.optionalString("author"), creator = value.optionalString("creator"),
    createdAt = value.optionalString("createdAt"), fetchedAt = value.long("fetchedAt"),
)

private fun readHitokotoState(value: Bundle): HitokotoUiState = when (value.string("kind")) {
    "loading" -> HitokotoUiState.Loading
    "ready" -> HitokotoUiState.Ready(readHitokotoSnapshot(value.bundle("snapshot")), value.boolean("refreshing", false))
    "error" -> HitokotoUiState.Error(value.string("message"), value.optionalBundle("cached")?.let(::readHitokotoSnapshot))
    else -> error("Unknown hitokoto state")
}

@Suppress("DEPRECATION")
private inline fun <reified T> Bundle.typed(key: String): T {
    val value = get(key)
    require(value is T) { "Side-panel key '$key' must be ${T::class.java.simpleName}" }
    return value
}
private fun Bundle.string(key: String) = typed<String>(key)
private fun Bundle.string(key: String, fallback: String) = if (containsKey(key)) string(key) else fallback
private fun Bundle.nonBlank(key: String) = string(key).also { require(it.isNotBlank()) { "Missing side-panel field '$key'" } }
private fun Bundle.integer(key: String) = typed<Int>(key)
private fun Bundle.integer(key: String, fallback: Int) = if (containsKey(key)) integer(key) else fallback
private fun Bundle.boolean(key: String) = typed<Boolean>(key)
private fun Bundle.boolean(key: String, fallback: Boolean) = if (containsKey(key)) boolean(key) else fallback
private fun Bundle.long(key: String) = typed<Long>(key)
private fun Bundle.bundle(key: String) = typed<Bundle>(key)
@Suppress("DEPRECATION")
private fun Bundle.optionalBundle(key: String): Bundle? = if (get(key) == null) null else bundle(key)
@Suppress("DEPRECATION")
private fun Bundle.optionalString(key: String): String? = if (get(key) == null) null else string(key)
@Suppress("DEPRECATION")
private fun Bundle.optionalInt(key: String): Int? = if (get(key) == null) null else integer(key)
@Suppress("DEPRECATION")
private fun Bundle.optionalLong(key: String): Long? = if (get(key) == null) null else long(key)
@Suppress("DEPRECATION")
private fun Bundle.optionalDouble(key: String): Double? = if (get(key) == null) null else typed<Double>(key)
@Suppress("DEPRECATION")
private fun Bundle.bundles(key: String): List<Bundle> {
    val value = get(key)
    require(value is ArrayList<*> && value.all { it is Bundle }) { "Side-panel key '$key' must contain platform Bundles" }
    return value.map { it as Bundle }
}
private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) { "Side-panel state requires the main thread" } }
