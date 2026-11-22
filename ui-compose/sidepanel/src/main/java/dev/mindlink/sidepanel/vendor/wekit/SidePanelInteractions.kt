package dev.mindlink.sidepanel.vendor.wekit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/** One list-owned gesture survives card moves and the transition into editing. */
fun Modifier.sidePanelLongPressDrag(
    onStart: (Offset) -> Boolean,
    onDrag: (Float) -> Unit,
    onFinish: (Boolean) -> Unit,
): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val finish by rememberUpdatedState(onFinish)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val held = event.changes.firstOrNull { it.id == down.id }
                    if (held == null || !held.pressed || event.changes.count { it.pressed } != 1 ||
                        (held.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    val final = awaitPointerEvent(PointerEventPass.Final)
                    if (final.changes.any { it.isConsumed && it.position != it.previousPosition }) break
                }
                true
            }
            if (released == null && start(down.position)) {
                var completed = false
                try {
                    currentEvent.changes.forEach { it.consume() }
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val held = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) break
                        // Android CANCEL is delivered as an already-consumed pointer-up.
                        if (held.isConsumed) break
                        if (!held.pressed) {
                            completed = held.previousPressed
                            if (completed) drag(held.positionChange().y)
                            event.changes.forEach { it.consume() }
                            break
                        }
                        drag(held.positionChange().y)
                        event.changes.forEach { it.consume() }
                    }
                } finally {
                    finish(completed)
                }
            }
        }
    }
}
