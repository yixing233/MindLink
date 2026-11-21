package dev.mindlink.sidepanel.vendor.wekit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Ancestor Initial-pass long press: clickable descendants may consume DOWN, but not the edit hold.
 * Pointer-up, a second pointer, cancellation or movement beyond touch slop leaves scroll/click alone.
 */
fun Modifier.sidePanelEditLongPress(id: String, onEdit: () -> Unit): Modifier = pointerInput(id, onEdit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var cancelled = false
        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val held = event.changes.firstOrNull { it.id == down.id }
                if (held == null || !held.pressed || event.changes.count { it.pressed } != 1 ||
                    (held.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    cancelled = true
                    break
                }
            }
            true
        }
        if (finished == null && !cancelled) {
            currentEvent.changes.forEach { it.consume() }
            onEdit()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
