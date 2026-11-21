package dev.mindlink.compose.tests

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
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
        state.editCard("date-original")
        verify(state.uiState.editing && events == listOf("settings"), "Active long press opens the editor")
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
        realRecomposerLifecycle()
        println("PASS: dedicated side-panel boundary checks ($assertions assertions, real pinned runtime; Android UI/rendering NOT tested)")
    }
}
