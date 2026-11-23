package dev.mindlink.compose.tests

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.mindlink.sidepanel.vendor.wekit.DateTimeCardConfig
import dev.mindlink.sidepanel.vendor.wekit.HITOKOTO_CATEGORY_CODES
import dev.mindlink.sidepanel.vendor.wekit.HitokotoCardConfig
import dev.mindlink.sidepanel.vendor.wekit.HitokotoUiState
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelCardRuntimeState
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelState
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelRoute
import dev.mindlink.sidepanel.vendor.wekit.WeatherIconKind
import dev.mindlink.sidepanel.vendor.wekit.weatherIconKind
import dev.mindlink.sidepanel.vendor.wekit.moveSidePanelCard
import dev.mindlink.sidepanel.vendor.wekit.sidePanelDropIndex
import dev.mindlink.sidepanel.vendor.wekit.SidePanelCardBounds
import dev.mindlink.sidepanel.vendor.wekit.SidePanelCardReorder
import dev.mindlink.sidepanel.vendor.wekit.homeSidePanelPointerLifecycleDecision
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelPointerLifecycleDecision
import dev.mindlink.compose.SettingsState
import dev.mindlink.compose.Setting
import dev.mindlink.compose.MATERIAL_PAGE
import dev.mindlink.compose.pageOf
import dev.mindlink.compose.settingsGroups
import java.util.function.Function
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/** Runs real pinned alpha13/lifecycle code on the JVM; only Android platform boundaries are fixtures.
 * Does not construct AndroidComposeView, attach to Android, or claim pixel/render validation.
 */
object SidePanelBoundaryChecks {
    private var assertions = 0
    private fun verify(value: Boolean, message: String) {
        check(value) { message }
        assertions++
    }

    private fun bundle(vararg values: Pair<String, Any>): Bundle = Bundle().apply {
        values.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Bundle -> putBundle(key, value)
                else -> error("Unexpected fixture")
            }
        }
    }

    private fun snapshot(settings: Bundle, kind: String = "loading", cached: Boolean = false): Bundle {
        val data = bundle("uuid" to "real-cache-id", "text" to "真实旧缓存", "fetchedAt" to 123L)
        val runtime = bundle("kind" to kind)
        if (kind == "ready") runtime.putBundle("snapshot", data)
        if (kind == "error") {
            runtime.putString("message", "未授权联网；保留缓存")
            if (cached) runtime.putBundle("cached", data)
        }
        val cards = arrayListOf(
            bundle("id" to "date-original", "cardType" to "date_time", "showLunarCalendar" to true),
            bundle("id" to "hitokoto-original", "cardType" to "hitokoto", "settings" to settings, "state" to runtime),
        )
        return bundle(
            "schemaVersion" to 1, "initialized" to true, "editing" to false,
            "profile" to bundle("wxId" to "account-real", "nickname" to "真实资料", "status" to bundle("kind" to "loading")),
        ).apply { putParcelableArrayList("cards", cards) }
    }

    private fun invalidSettings() {
        val cases = listOf(
            bundle("categories" to "z", "showSource" to false, "showAuthor" to true) to "包含不支持",
            bundle("categories" to "a,b", "minLength" to -1, "showSource" to true, "showAuthor" to false) to "不能为负数",
            bundle("categories" to "a", "minLength" to 8, "maxLength" to 2) to "最大长度",
            bundle("categories" to ", ， ") to "至少选择",
        )
        cases.forEach { (settings, reason) ->
            val input = snapshot(settings, "ready")
            val original = Bundle(settings)
            val state = HomeSidePanelState(input, emptyMap<String, Bitmap>(), emptyMap())
            val ui = state.uiState
            verify(ui.renderedLayout.cards.map { it.id } == listOf("date-original", "hitokoto-original"), "Card IDs/order preserved")
            verify(ui.renderedLayout.cards.first() is DateTimeCardConfig, "Invalid one card does not remove date")
            verify(ui.profile.wxId == "account-real" && ui.profile.nickname == "真实资料", "Profile is not fabricated")
            val card = ui.renderedLayout.cards.last() as HitokotoCardConfig
            verify(card.settings.minLength == settings.get("minLength"), "Original min retained")
            verify(card.settings.maxLength == settings.get("maxLength"), "Original max retained")
            val runtime = (state.runtimeState(card.id) as HomeSidePanelCardRuntimeState.Hitokoto).state
            verify(runtime is HitokotoUiState.Error && runtime.message.contains(reason), "Invalid settings become local Error")
            verify((runtime as HitokotoUiState.Error).cached?.uuid == "real-cache-id" && runtime.cached?.text == "真实旧缓存", "Only genuine cache retained")
            verify(settings.keySet() == original.keySet() && settings.keySet().all { settings.get(it) == original.get(it) }, "Input Bundle unchanged")
            state.dispose()
        }
        val noCache = HomeSidePanelState(snapshot(bundle("categories" to "z")), emptyMap(), emptyMap())
        verify(((noCache.runtimeState("hitokoto-original") as HomeSidePanelCardRuntimeState.Hitokoto).state as HitokotoUiState.Error).cached == null, "No invented cache")
        val retainedCache = HomeSidePanelState(snapshot(bundle("categories" to "z"), "error", true), emptyMap(), emptyMap())
        verify(((retainedCache.runtimeState("hitokoto-original") as HomeSidePanelCardRuntimeState.Hitokoto).state as HitokotoUiState.Error).cached?.fetchedAt == 123L, "Error cache timestamp retained")
        val ready = HomeSidePanelState(snapshot(bundle("categories" to "a,b", "minLength" to 0, "maxLength" to 8), "ready"), emptyMap(), emptyMap())
        verify((ready.runtimeState("hitokoto-original") as HomeSidePanelCardRuntimeState.Hitokoto).state is HitokotoUiState.Ready, "Valid Ready stays Ready")
        ready.update(snapshot(bundle("categories" to "z")), emptyMap())
        verify((ready.runtimeState("hitokoto-original") as HomeSidePanelCardRuntimeState.Hitokoto).state is HitokotoUiState.Error, "Invalid update affects only one card")
        ready.update(snapshot(bundle("categories" to ""), "ready"), emptyMap())
        verify((ready.uiState.renderedLayout.cards.last() as HitokotoCardConfig).settings.categories == HITOKOTO_CATEGORY_CODES, "Legacy blank default unchanged")
        verify((ready.runtimeState("hitokoto-original") as HomeSidePanelCardRuntimeState.Hitokoto).state is HitokotoUiState.Ready, "Corrected update recovers")
        val calls = AtomicInteger()
        ready.updateCallbacks(mapOf("refresh_hitokoto:hitokoto-original" to Runnable { calls.incrementAndGet() }))
        ready.refreshHitokoto("hitokoto-original")
        verify(calls.get() == 0, "Callbacks inactive before lifecycle")
        ready.lifecycle(true)
        ready.refreshHitokoto("hitokoto-original")
        verify(calls.get() == 1, "Active callback invokes existing action")
        ready.dispose()
        ready.refreshHitokoto("hitokoto-original")
        verify(calls.get() == 1 && ready.uiState.renderedLayout.cards.isEmpty(), "Dispose drops data/actions")
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        init { registry.currentState = Lifecycle.State.CREATED }
    }

    private fun editorCallbacksStayActive() {
        val display = snapshot(bundle("categories" to "a"), "ready")
        val events = mutableListOf<String>()
        lateinit var state: HomeSidePanelState
        fun editing(route: String) = Bundle(display).apply {
            putBoolean("editing", true)
            putString("editorRoute", route)
        }
        val callbacks = mapOf(
            "enter_edit" to Runnable { events += "edit"; state.update(editing("edit_home"), emptyMap()) },
            "card_settings:date-original" to Runnable {
                events += "settings"
                state.update(editing("card_settings:date-original"), emptyMap())
            },
            "update_lunar:date-original" to Runnable { events += "lunar" },
            "add_card" to Runnable {
                events += "add"
                state.update(editing("add_card"), emptyMap())
            },
            "save_edit" to Runnable {
                events += "save"
                state.update(display, emptyMap())
            },
            "discard_edit" to Runnable {
                events += "discard"
                state.update(display, emptyMap())
            },
        )
        state = HomeSidePanelState(display, emptyMap(), callbacks)
        state.lifecycle(true)
        state.enterEditing()
        verify(state.uiState.route == HomeSidePanelRoute.EditHome && events == listOf("edit"), "Long press enters global edit mode")
        events.clear()
        state.editCard("date-original")
        verify(state.uiState.editing && events == listOf("settings"), "Settings badge opens card configuration")
        state.updateCallbacks(callbacks)
        state.updateLunar("date-original")
        state.openAddCard()
        verify(events == listOf("settings", "lunar", "add"), "Editor settings/add callbacks remain active after updates")
        verify(state.uiState.editing, "Add-card page retains editing state")
        state.saveEditing()
        verify(!state.uiState.editing && events.last() == "save", "Active editor save returns to display")
        state.editCard("date-original")
        val pausedEvents = events.toList()
        state.lifecycle(false)
        state.saveEditing()
        state.discardEditing()
        state.openAddCard()
        verify(events == pausedEvents && state.uiState.editing, "A real pause suppresses all editor callbacks without losing the draft")
        state.lifecycle(true)
        state.discardEditing()
        verify(!state.uiState.editing && events.last() == "discard", "Resume restores editor cancel interaction")
        state.editCard("date-original")
        state.dispose()
        val disposedEvents = events.toList()
        state.saveEditing()
        state.openAddCard()
        verify(events == disposedEvents, "Disposed editor cannot invoke stale host callbacks")
    }

    private fun settingsAndReorderContracts() {
        val ids = listOf("a", "b", "c", "d")
        verify(moveSidePanelCard(ids, "a", 3) == listOf("b", "c", "d", "a"), "Move first card to last")
        verify(moveSidePanelCard(ids, "d", 0) == listOf("d", "a", "b", "c"), "Move last card to first")
        verify(moveSidePanelCard(ids, "missing", 0) == ids && moveSidePanelCard(ids, "a", -1) == ids, "Invalid moves leave order intact")
        val equal = ids.mapIndexed { i, id -> SidePanelCardBounds(id, i * 114, 100) }
        verify(sidePanelDropIndex(ids, "a", 400f, equal) == 3, "One fast event crosses all three cards")
        verify(sidePanelDropIndex(ids, "d", 20f, equal) == 0, "One upward event crosses all three cards")
        val mixed = listOf(SidePanelCardBounds("a", 0, 110), SidePanelCardBounds("b", 124, 800),
            SidePanelCardBounds("c", 938, 68), SidePanelCardBounds("d", 1020, 240))
        verify(sidePanelDropIndex(ids, "a", 523f, mixed) == 0, "Tall-card midpoint has not been crossed")
        verify(sidePanelDropIndex(ids, "a", 525f, mixed) == 1, "Tall-card midpoint is crossed")
        verify(sidePanelDropIndex(ids, "a", 524f, mixed) == 0, "Exact midpoint does not move the card")
        verify(sidePanelDropIndex(ids, "missing", 900f, mixed) == -1, "Missing dragged ID has no destination")
        verify(sidePanelDropIndex(ids, "a", 1200f, emptyList()) == 0, "No visible target retains the source")
        verify(sidePanelDropIndex(ids, "a", 1200f, mixed) == 3, "Rapid mixed-height drop reaches last card")
        verify(sidePanelDropIndex(ids, "d", 54f, mixed) == 0, "Mixed-height upward drop reaches first card")
        verify(sidePanelDropIndex(ids, "a", 970f, mixed) == 1 && sidePanelDropIndex(ids, "a", 973f, mixed) == 2,
            "Final pointer-up displacement can cross another midpoint")
        verify(homeSidePanelPointerLifecycleDecision(true, false, true) == HomeSidePanelPointerLifecycleDecision.Cancel, "Consumed pointer-up cancels")
        verify(weatherIconKind("32") == WeatherIconKind.FOG && weatherIconKind("999") == WeatherIconKind.UNKNOWN, "Native Xiaomi weather codes")
        val state = SettingsState(bundle("top_profile_enabled" to "false"),
            mapOf("top_profile_enabled" to Function<String, String> { "false" }), emptyMap(), emptyMap(), false)
        val topBar = state.pageOf("top_bar")!!
        verify(topBar.master == null, "Top bar bundles independent features, so it has no page-wide switch")
        val profile = topBar.sections.single { it.master?.key == "top_profile_enabled" }
        verify(profile.rows.any { it.key == "top_search_bar" }, "Search stays under its actual top-bar dependency")
        verify(topBar.sections.single { it.master?.key == "top_scan_enabled" }.rows.any { it.key == "tool_pay" },
            "Quick entries keep their own switch, independent of the profile card")
        val bottom = state.pageOf("bottom_bar")!!
        verify(bottom.master?.key == "bottom_bar_enabled" && bottom.sections.any { s -> s.rows.any { it.key == "nav_bar_scale" } },
            "Bottom bar switch and configuration share a page")
        val material = state.pageOf(MATERIAL_PAGE)!!
        verify(material.master == null && material.sections.all { it.master == null }, "Shared material remains available independently of bottom-bar switch")
        fun opacityRow() = state.pageOf(MATERIAL_PAGE)!!.sections.flatMap { it.rows }
            .filterIsInstance<Setting.Range>().firstOrNull { it.key == "nav_bar_surface_opacity" }
        state.write("nav_bar_glass_mode", "0")
        verify(opacityRow() == null, "Plain surface offers no opacity")
        state.write("nav_bar_glass_mode", "1")
        verify(opacityRow()?.fallback?.invoke(state) == 65, "Frosted default opacity is 65%")
        state.write("nav_bar_glass_mode", "2")
        state.write("nav_bar_blur_radius", "8")
        verify(opacityRow()?.fallback?.invoke(state) == 40, "Liquid default opacity is 40%")
        state.write("nav_bar_blur_radius", "0")
        verify(opacityRow()?.fallback?.invoke(state) == 0, "Liquid without blur defaults to clear")
        state.write("nav_bar_surface_opacity", "70")
        verify(state.number("nav_bar_surface_opacity", -1) == 70, "Explicit opacity is stored as a plain percent")
        state.write("nav_bar_surface_opacity", "")
        for (id in listOf("bottom_bar", "chat_header", "chat_footer")) {
            verify(state.pageOf(id)!!.sections.any { s -> s.rows.any { it is Setting.Link && it.target == MATERIAL_PAGE } }, "$id links to shared material")
        }
        for (removed in listOf("top_nickname", "top_status", "top_dot_color", "panel_tab")) {
            val keys = settingsGroups(state).flatMap { g -> g.pages.flatMap { p -> p.sections.flatMap { s -> s.rows + listOfNotNull(s.master) } + listOfNotNull(p.master) } }
            verify(keys.none { it.key == removed }, "$removed is no longer offered")
        }
        for (mode in listOf("0", "1", "2")) {
            state.write("bubble_mode", mode)
            val pages = settingsGroups(state).flatMap { it.pages }
            val keys = pages.flatMap { page ->
                listOfNotNull(page.master) + page.sections.flatMap { section -> section.rows + listOfNotNull(section.master) }
            }.mapNotNull { it.key }
            verify(keys.distinct().size == keys.size, "Every setting appears once in mode $mode")
            verify(pages.map { it.id }.distinct().size == pages.size, "Stable unique page ids")
            verify(pages.all { it.navigable || it.master != null }, "Every overview row either opens a page or is a switch")
        }
        verify(!state.back() && state.currentPage == null, "Back at the overview is left to the host")
        state.open("bottom_bar")
        state.open("bottom_bar")
        state.open(MATERIAL_PAGE)
        state.open("missing")
        verify(state.stack == listOf("bottom_bar", MATERIAL_PAGE), "Links push pages; duplicates and unknown ids are ignored")
        verify(state.back() && state.currentPage == "bottom_bar" && !state.forward, "Back returns to the linking page")
        verify(state.back() && state.currentPage == null, "Second back returns to the overview")
        state.toggle("top_profile_enabled", true)
        verify(!state.flag("top_profile_enabled"), "Failed writes retain effective stored state")
        val revision = state.actionRevision
        state.action("toolUp:scan")
        verify(state.actionRevision == revision + 1, "Tool action invalidates reader data")
        state.dispose()
    }

    private fun dragStateContracts() {
        val ids = listOf("a", "b", "c", "d")
        val heights = mapOf("a" to 110, "b" to 800, "c" to 68, "d" to 240)
        var items = emptyList<LazyListItemInfo>()
        var viewportStart = -24
        val info = object : LazyListLayoutInfo {
            override val visibleItemsInfo get() = items
            override val viewportStartOffset get() = viewportStart
            override val viewportEndOffset = 1300
            override val totalItemsCount = 7
        }
        fun measure(order: List<String>, scroll: Int = 0) {
            var top = -scroll
            items = order.mapIndexed { position, id ->
                val offset = top
                val height = heights.getValue(id)
                top += height + 14
                object : LazyListItemInfo {
                    override val index = position + 2
                    override val key = id
                    override val offset = offset
                    override val size = height
                }
            }
        }
        var anchors = 0
        val moves = mutableListOf<Pair<String, Int>>()
        val drag = SidePanelCardReorder({ info }, { anchors++ })
        measure(ids)
        verify(!drag.start(1f, ids), "Padding/header is not a card")
        verify(drag.start(74f, ids) && drag.draggedId == "a", "Start accounts for content padding")
        verify(drag.height == 110 && drag.overlayTop() == 24f, "Preview preserves measured height and top")
        drag.drag(918f)
        verify(drag.order == listOf("b", "c", "a", "d"), "Fast event crosses two mixed-height midpoints")
        drag.drag(227f)
        verify(drag.order == listOf("b", "c", "d", "a"), "Later event resolves against still-stale measurement")
        drag.drag(-625f)
        verify(drag.order == listOf("b", "a", "c", "d"), "Reverse motion before remeasurement resolves from original order")
        measure(drag.order!!)
        drag.reorder()
        verify(drag.order == listOf("b", "a", "c", "d"), "Remeasured preview is stable without motion")
        drag.drag(500f)
        verify(drag.order == listOf("b", "c", "a", "d"), "Motion after remeasurement reaches the next slot")
        drag.drag(200f)
        drag.finish(true) { id, index -> moves += id to index }
        verify(moves == listOf("a" to 3) && anchors > 0, "Completed drop submits one final destination and preserves scroll")
        drag.finish(true) { id, index -> moves += id to index }
        verify(moves.size == 1 && drag.draggedId == null && drag.order == null, "Repeated finish never submits again")
        measure(ids)
        verify(drag.start(224f, ids) && drag.height == 800, "Tall image-sized card retains its full height")
        drag.drag(-500f)
        verify(drag.order == listOf("b", "a", "c", "d") && drag.scrollStep(56f) < 0f, "Upward move reaches first slot and upper scroll edge")
        drag.finish(false) { id, index -> moves += id to index }
        verify(moves.size == 1 && drag.order == null, "Cancelled drag discards all preview moves")
        measure(ids, scroll = 200)
        verify(drag.start(924f, ids), "Can start on a scrolled card")
        verify(drag.overlayTop() == 844f, "Scrolled preview uses list coordinates")
        drag.drag(500f)
        verify(drag.scrollStep(56f) > 0f, "Lower edge scroll is active")
        drag.finish(false) { id, index -> moves += id to index }
        verify(drag.scrollStep(56f) == 0f, "Finishing stops edge scrolling")
        verify(homeSidePanelPointerLifecycleDecision(true, true, false) == HomeSidePanelPointerLifecycleDecision.Continue,
            "Pressed pointer continues")
        verify(homeSidePanelPointerLifecycleDecision(true, false, false) == HomeSidePanelPointerLifecycleDecision.Finish,
            "Unconsumed pointer-up finishes")
        verify(homeSidePanelPointerLifecycleDecision(false, false, true) == HomeSidePanelPointerLifecycleDecision.Continue,
            "Inactive pointer has no completed gesture")
    }

    private suspend fun realRecomposerLifecycle() {
        val owner = Owner()
        val view = View(Context())
        val parentJob = SupervisorJob()
        val clock = BroadcastFrameClock()
        val motion = object : MotionDurationScale { override val scaleFactor = 1f }
        val context = Dispatchers.Unconfined + clock + motion + parentJob
        val recomposer = view.createLifecycleAwareWindowRecomposer(coroutineContext = context, lifecycle = owner.lifecycle)
        verify(recomposer::class.java.name == "androidx.compose.runtime.Recomposer", "Real alpha13 Recomposer, not fixture")
        verify(owner.registry.observerCount == 1 && parentJob.children.any(), "Official lifecycle observer and runner active")
        owner.registry.currentState = Lifecycle.State.RESUMED
        verify(recomposer.currentState.value != Recomposer.State.ShutDown, "Resume keeps runtime alive")
        owner.registry.currentState = Lifecycle.State.CREATED
        recomposer.cancel()
        parentJob.cancel()
        withTimeout(5000) { recomposer.join() }
        verify(recomposer.currentState.value == Recomposer.State.ShutDown, "Explicit cancellation terminates real runtime")
        verify(owner.registry.observerCount == 0 && parentJob.children.none(), "Cancellation removes official observer/job")
        val nextJob = SupervisorJob()
        val next = View(Context()).createLifecycleAwareWindowRecomposer(coroutineContext = Dispatchers.Unconfined + BroadcastFrameClock() + motion + nextJob, lifecycle = owner.lifecycle)
        verify(next !== recomposer, "Recreate uses a different parent runtime")
        owner.registry.currentState = Lifecycle.State.DESTROYED
        withTimeout(5000) { next.join() }
        nextJob.cancel()
        verify(next.currentState.value == Recomposer.State.ShutDown && owner.registry.observerCount == 0, "Destroy cancels and removes lifecycle observer")
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        // mutableStateOf below is provided by the actual alpha13 runtime, not an Android/Compose fixture.
        verify(mutableStateOf(1).value == 1, "Pinned runtime loaded")
        invalidSettings()
        editorCallbacksStayActive()
        settingsAndReorderContracts()
        dragStateContracts()
        realRecomposerLifecycle()
        println("PASS: dedicated side-panel boundary checks ($assertions assertions, real pinned runtime; Android UI/rendering NOT tested)")
    }
}
