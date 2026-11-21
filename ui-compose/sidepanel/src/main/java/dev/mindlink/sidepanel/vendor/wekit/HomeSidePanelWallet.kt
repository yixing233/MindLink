// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelWallet.kt
// MindLink adaptations (2026-11-14): original balance display state/toggle/reset/decimal formatter only; host cache service/hook not copied
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class HomeSidePanelWalletDisplayState(
    val defaultMaskEnabled: Boolean,
    val isMasked: Boolean = defaultMaskEnabled,
) {
    fun toggleFromCard(): HomeSidePanelWalletDisplayState = if (defaultMaskEnabled) {
        copy(isMasked = !isMasked)
    } else {
        this
    }

    fun reset() = copy(isMasked = defaultMaskEnabled)
}

data class HomeSidePanelWalletUiState(
    val balanceFen: Long? = null,
    val displayState: HomeSidePanelWalletDisplayState = HomeSidePanelWalletDisplayState(true),
) {
    fun withBalance(balanceFen: Long?): HomeSidePanelWalletUiState = copy(balanceFen = balanceFen)

    val displayBalance: String
        get() = if (displayState.isMasked) "******" else formatHomeSidePanelWalletBalance(balanceFen)
}

fun formatHomeSidePanelWalletBalance(balanceFen: Long?): String {
    if (balanceFen == null) return "¥ --"
    val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    return "¥ ${formatter.format(BigDecimal.valueOf(balanceFen, 2))}"
}
