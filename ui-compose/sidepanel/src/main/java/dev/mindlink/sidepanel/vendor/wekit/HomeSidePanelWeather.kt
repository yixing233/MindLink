// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelWeather.kt
// MindLink adaptations (2026-11-14): full display models and original icon-code mapping only; network/location/database/services/default city omitted; errors are platform strings
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

data class WeatherCity(
    val countryCode: String,
    val province: String,
    val city: String,
    val district: String?,
    val cityNum: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
data class WeatherSnapshot(
    val city: WeatherCity,
    val weatherCode: String,
    val temperature: String,
    val feelsLike: String,
    val high: String,
    val low: String,
    val humidity: String,
    val windSpeed: String,
    val publishedAt: String,
    val fetchedAt: Long,
)

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(
        val snapshot: WeatherSnapshot,
        val refreshing: Boolean = false,
    ) : WeatherUiState

    data class Error(
        val message: String,
        val cached: WeatherSnapshot?,
    ) : WeatherUiState
}

enum class WeatherIconKind {
    SUNNY,
    PARTLY_CLOUDY,
    OVERCAST,
    SHOWER,
    THUNDERSTORM,
    HAIL,
    SLEET,
    LIGHT_RAIN,
    RAIN,
    HEAVY_RAIN,
    RAINSTORM,
    SNOW_SHOWER,
    LIGHT_SNOW,
    SNOW,
    HEAVY_SNOW,
    BLIZZARD,
    FOG,
    FREEZING_RAIN,
    DUST_STORM,
    DUST,
    SAND,
    SQUALL,
    TORNADO,
    HAZE,
    UNKNOWN,
}

fun weatherIconKind(code: String): WeatherIconKind = when (code.toIntOrNull()) {
    0 -> WeatherIconKind.SUNNY
    1 -> WeatherIconKind.PARTLY_CLOUDY
    2 -> WeatherIconKind.OVERCAST
    3 -> WeatherIconKind.SHOWER
    4 -> WeatherIconKind.THUNDERSTORM
    5 -> WeatherIconKind.HAIL
    6 -> WeatherIconKind.SLEET
    7 -> WeatherIconKind.LIGHT_RAIN
    8 -> WeatherIconKind.RAIN
    9 -> WeatherIconKind.HEAVY_RAIN
    10, 11, 12 -> WeatherIconKind.RAINSTORM
    13 -> WeatherIconKind.SNOW_SHOWER
    14 -> WeatherIconKind.LIGHT_SNOW
    15 -> WeatherIconKind.SNOW
    16 -> WeatherIconKind.HEAVY_SNOW
    17 -> WeatherIconKind.BLIZZARD
    18 -> WeatherIconKind.FOG
    19 -> WeatherIconKind.FREEZING_RAIN
    20 -> WeatherIconKind.DUST_STORM
    21, 22 -> WeatherIconKind.RAIN
    23 -> WeatherIconKind.HEAVY_RAIN
    24, 25 -> WeatherIconKind.RAINSTORM
    26 -> WeatherIconKind.SNOW
    27 -> WeatherIconKind.HEAVY_SNOW
    28 -> WeatherIconKind.BLIZZARD
    29 -> WeatherIconKind.DUST
    30 -> WeatherIconKind.SAND
    31 -> WeatherIconKind.DUST_STORM
    32 -> WeatherIconKind.SQUALL
    33 -> WeatherIconKind.TORNADO
    34 -> WeatherIconKind.BLIZZARD
    35 -> WeatherIconKind.FOG
    53 -> WeatherIconKind.HAZE
    else -> WeatherIconKind.UNKNOWN
}
