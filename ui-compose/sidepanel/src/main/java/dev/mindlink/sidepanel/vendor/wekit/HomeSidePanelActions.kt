// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelActions.kt
// MindLink adaptations (2026-11-14): original enum-label-MaterialSymbols mapping only; all native action/payment/service executor code remains in Java callbacks
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlinedfilled.Camera
import com.composables.icons.materialsymbols.outlinedfilled.Cancel
import com.composables.icons.materialsymbols.outlinedfilled.Extension
import com.composables.icons.materialsymbols.outlinedfilled.Favorite
import com.composables.icons.materialsymbols.outlinedfilled.Mark_chat_read
import com.composables.icons.materialsymbols.outlinedfilled.Movie
import com.composables.icons.materialsymbols.outlinedfilled.Person_add
import com.composables.icons.materialsymbols.outlinedfilled.Qr_code_scanner
import com.composables.icons.materialsymbols.outlinedfilled.Settings
import com.composables.icons.materialsymbols.outlinedfilled.Update
import com.composables.icons.materialsymbols.outlinedfilled.Wallet
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
        MaterialSymbols.OutlinedFilled.Person_add,
    )

    HomeSidePanelActionKind.SCAN -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_scan,
        MaterialSymbols.OutlinedFilled.Qr_code_scanner,
    )

    HomeSidePanelActionKind.MOMENTS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_moments,
        MaterialSymbols.OutlinedFilled.Camera,
    )

    HomeSidePanelActionKind.WALLET -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_wallet,
        MaterialSymbols.OutlinedFilled.Wallet,
    )

    HomeSidePanelActionKind.CHANNELS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_channels,
        MaterialSymbols.OutlinedFilled.Movie,
    )

    HomeSidePanelActionKind.WECHAT_SETTINGS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_settings,
        MaterialSymbols.OutlinedFilled.Settings,
    )

    HomeSidePanelActionKind.FAVORITES -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_favorites,
        MaterialSymbols.OutlinedFilled.Favorite,
    )

    HomeSidePanelActionKind.WEKIT_SETTINGS -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_module_settings,
        MaterialSymbols.OutlinedFilled.Extension,
    )

    HomeSidePanelActionKind.RESTART_WECHAT -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_restart_wechat,
        MaterialSymbols.OutlinedFilled.Update,
    )

    HomeSidePanelActionKind.FORCE_STOP_WECHAT -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_force_stop,
        MaterialSymbols.OutlinedFilled.Cancel,
    )

    HomeSidePanelActionKind.MARK_ALL_READ -> HomeSidePanelActionSpec(
        kind,
        R.string.wekit_sp_fab_default_mark_all_read,
        MaterialSymbols.OutlinedFilled.Mark_chat_read,
    )
}
