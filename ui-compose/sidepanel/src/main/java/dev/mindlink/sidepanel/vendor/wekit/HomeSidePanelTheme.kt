// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/ui/utils/theme/InjectedUiTheme.kt
// MindLink adaptations: delegate to the shared Material3 theme; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import androidx.compose.runtime.Composable
import dev.mindlink.ui.MindLinkTheme

@Composable
fun HomeSidePanelTheme(content: @Composable () -> Unit) {
    MindLinkTheme(content = content)
}
