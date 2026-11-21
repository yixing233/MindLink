// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelDragState.kt
// MindLink adaptations (2026-11-14): original pointer up/cancel decision only, required by image resize; not the drag host
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

enum class HomeSidePanelPointerLifecycleDecision {
    Continue,
    Finish,
    Cancel,
}

fun homeSidePanelPointerLifecycleDecision(
    previousPressed: Boolean,
    pressed: Boolean,
    consumedAtInitialPass: Boolean,
): HomeSidePanelPointerLifecycleDecision = when {
    !previousPressed || pressed -> HomeSidePanelPointerLifecycleDecision.Continue
    consumedAtInitialPass -> HomeSidePanelPointerLifecycleDecision.Cancel
    else -> HomeSidePanelPointerLifecycleDecision.Finish
}
