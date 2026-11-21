// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelPreview.kt
// MindLink adaptations (2026-11-14): package only; original content variants retained
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import java.time.LocalDateTime

sealed interface DateTimeCardContent {
    data object Runtime : DateTimeCardContent
    data class Preview(val now: LocalDateTime) : DateTimeCardContent
}

sealed interface WeatherCardContent {
    data class Runtime(val state: WeatherUiState) : WeatherCardContent
    data class Preview(val snapshot: WeatherSnapshot) : WeatherCardContent
}

sealed interface WalletCardContent {
    data class Runtime(val state: HomeSidePanelWalletUiState) : WalletCardContent
    data class Preview(val displayBalance: String) : WalletCardContent
}

sealed interface HitokotoCardContent {
    data class Runtime(val state: HitokotoUiState) : HitokotoCardContent
    data class Preview(val snapshot: HitokotoSnapshot) : HitokotoCardContent
}

sealed interface HomeSidePanelActionCardContent {
    data object Runtime : HomeSidePanelActionCardContent
    data object Preview : HomeSidePanelActionCardContent
}
