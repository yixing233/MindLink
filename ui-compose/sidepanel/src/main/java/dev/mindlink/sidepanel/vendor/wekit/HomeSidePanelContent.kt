// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelContent.kt
// MindLink adaptations (2026-11-14): retain exact fill-size Material3 surface with topEnd/bottomEnd 28dp and original loading; route/full drag host remains in Java host editor
// 2026-11-16: editor routes (edit_home / add_card / card_settings) render inside the SAME
// Compose root; the Java LinearLayout editor was removed (WeKit HomeSidePanelEditor semantics).
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class HomeSidePanelTransitionKind { ENTER_EDITOR, EXIT_EDITOR, NONE }
fun homeSidePanelTransitionKind(from: HomeSidePanelRoute, to: HomeSidePanelRoute): HomeSidePanelTransitionKind = when {
    from == to -> HomeSidePanelTransitionKind.NONE
    to is HomeSidePanelRoute.CardSettings || to == HomeSidePanelRoute.AddCard -> HomeSidePanelTransitionKind.ENTER_EDITOR
    to == HomeSidePanelRoute.EditHome -> if (from == HomeSidePanelRoute.Home) HomeSidePanelTransitionKind.ENTER_EDITOR else HomeSidePanelTransitionKind.NONE
    from != HomeSidePanelRoute.Home && to == HomeSidePanelRoute.Home -> HomeSidePanelTransitionKind.EXIT_EDITOR
    else -> HomeSidePanelTransitionKind.NONE
}

@Composable
fun HomeSidePanelContent(state: HomeSidePanelUiState, panelState: HomeSidePanelState) {
    val listState = rememberLazyListState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        if (!state.initialized) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (state.route) {
                HomeSidePanelRoute.Home -> HomeSidePanelHome(state = state, panelState = panelState, listState = listState)
                HomeSidePanelRoute.EditHome -> HomeSidePanelHome(state = state, panelState = panelState, listState = listState)
                HomeSidePanelRoute.AddCard -> HomeSidePanelAddCardPage(panelState = panelState)
                is HomeSidePanelRoute.CardSettings -> HomeSidePanelCardSettingsPage(
                    state = state,
                    cardId = state.route.cardId,
                    panelState = panelState,
                )
            }
        }
    }
}
