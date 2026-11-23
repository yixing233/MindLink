// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelActions.kt
// MindLink adaptations (2026-11-14): original enum-label-MaterialSymbols mapping only; all native action/payment/service executor code remains in Java callbacks
// 2026-11-22: icons switched to Lucide (dev.mindlink.ui.LucideIcons, ISC); see THIRD-PARTY-NOTICES.md.
// Original component geometry/typography are retained (icons are now Lucide); host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import dev.mindlink.ui.LucideIcons
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mindlink.sidepanel.R
data class HomeSidePanelActionSpec(
    val kind: HomeSidePanelActionKind,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

fun homeSidePanelActionSpec(kind: HomeSidePanelActionKind): HomeSidePanelActionSpec = when (kind) {
    HomeSidePanelActionKind.ADD_FRIEND -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_home_side_panel_action_add_friend,
        LucideIcons.UserPlus,
    )

    HomeSidePanelActionKind.SCAN -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_scan,
        LucideIcons.ScanQrCode,
    )

    HomeSidePanelActionKind.MOMENTS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_moments,
        LucideIcons.Aperture,
    )

    HomeSidePanelActionKind.WALLET -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_wallet,
        LucideIcons.Wallet,
    )

    HomeSidePanelActionKind.CHANNELS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_channels,
        LucideIcons.Clapperboard,
    )

    HomeSidePanelActionKind.WECHAT_SETTINGS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_settings,
        LucideIcons.Settings,
    )

    HomeSidePanelActionKind.FAVORITES -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_favorites,
        LucideIcons.Star,
    )

    HomeSidePanelActionKind.WEKIT_SETTINGS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_module_settings,
        LucideIcons.Puzzle,
    )

    HomeSidePanelActionKind.RESTART_WECHAT -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_restart_wechat,
        LucideIcons.RotateCw,
    )

    HomeSidePanelActionKind.FORCE_STOP_WECHAT -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_force_stop,
        LucideIcons.CircleX,
    )

    HomeSidePanelActionKind.MARK_ALL_READ -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_mark_all_read,
        LucideIcons.MessageCircleCheck,
    )
}
