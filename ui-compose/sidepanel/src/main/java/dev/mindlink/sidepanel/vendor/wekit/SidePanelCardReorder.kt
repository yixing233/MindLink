package dev.mindlink.sidepanel.vendor.wekit

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preview only. Java receives one draft mutation on a completed drop. */
internal class SidePanelCardReorder(
    private val layoutInfo: () -> LazyListLayoutInfo,
    private val preserveScrollPosition: () -> Unit,
) {
    constructor(list: LazyListState) : this(
        { list.layoutInfo },
        { list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) },
    )
    var draggedId by mutableStateOf<String?>(null)
        private set
    var order by mutableStateOf<List<String>?>(null)
        private set
    private var top by mutableFloatStateOf(0f)
    var height = 0
        private set
    private var fingerY = 0f
    private var firstCardIndex = 0
    private val pendingLayouts = mutableListOf<List<String>>()

    fun start(y: Float, ids: List<String>): Boolean {
        val info = layoutInfo()
        val layoutY = y + info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull {
            it.key in ids && layoutY >= it.offset && layoutY < it.offset + it.size
        } ?: return false
        order = ids.toList()
        draggedId = item.key as String
        top = item.offset.toFloat()
        height = item.size
        fingerY = layoutY
        firstCardIndex = item.index - ids.indexOf(item.key)
        pendingLayouts.clear()
        pendingLayouts += ids.toList()
        return true
    }

    fun drag(delta: Float) {
        if (draggedId == null) return
        top += delta
        fingerY += delta
        reorder()
    }

    fun overlayTop(): Float = top - layoutInfo().viewportStartOffset

    fun scrollStep(edge: Float): Float {
        if (draggedId == null) return 0f
        val info = layoutInfo()
        val start = info.viewportStartOffset + edge
        val end = info.viewportEndOffset - edge
        return when {
            fingerY < start -> -((start - fingerY) / edge).coerceIn(0f, 1f) * edge / 5f
            fingerY > end -> ((fingerY - end) / edge).coerceIn(0f, 1f) * edge / 5f
            else -> 0f
        }
    }

    fun reorder() {
        val id = draggedId ?: return
        val ids = order ?: return
        val visible = layoutInfo().visibleItemsInfo
        // Pointer events can outrun lazy-list measurement. Resolve against the order
        // that produced these coordinates, including on the final pointer-up.
        val measured = visible.filter { it.key in ids }
        val layoutIndex = pendingLayouts.indexOfLast { layout ->
            measured.all { layout.getOrNull(it.index - firstCardIndex) == it.key }
        }
        if (layoutIndex < 0) return
        val layout = pendingLayouts[layoutIndex]
        repeat(layoutIndex) { pendingLayouts.removeAt(0) }
        val destination = sidePanelDropIndex(layout, id, top + height / 2f,
            measured.map { SidePanelCardBounds(it.key as String, it.offset, it.size) })
        val next = moveSidePanelCard(layout, id, destination)
        if (next == ids) return
        preserveScrollPosition()
        order = next
        pendingLayouts += next
    }

    fun finish(commit: Boolean, onMove: (String, Int) -> Unit) {
        if (commit) reorder()
        val id = draggedId
        val destination = order?.indexOf(id) ?: -1
        if (commit && id != null && destination >= 0) onMove(id, destination)
        draggedId = null
        order = null
        pendingLayouts.clear()
    }
}

internal data class SidePanelCardBounds(val id: String, val top: Int, val height: Int)

/** Cross every midpoint reached by this event, including differently sized cards. */
internal fun sidePanelDropIndex(
    ids: List<String>, draggedId: String, center: Float, visible: List<SidePanelCardBounds>,
): Int {
    val source = ids.indexOf(draggedId)
    if (source < 0) return -1
    val below = visible.filter { ids.indexOf(it.id) > source && center > it.top + it.height / 2f }
    if (below.isNotEmpty()) return below.maxOf { ids.indexOf(it.id) }
    return visible.filter { ids.indexOf(it.id) in 0 until source && center < it.top + it.height / 2f }
        .minOfOrNull { ids.indexOf(it.id) } ?: source
}

internal fun moveSidePanelCard(ids: List<String>, id: String, destination: Int): List<String> {
    val source = ids.indexOf(id)
    if (source < 0 || destination !in ids.indices || source == destination) return ids
    return ids.toMutableList().apply { add(destination, removeAt(source)) }
}
