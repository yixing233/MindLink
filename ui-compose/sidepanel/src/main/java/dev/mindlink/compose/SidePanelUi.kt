package dev.mindlink.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelContent
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelState
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelTheme
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelToolbarContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** Separate host entry and lifecycle; NEVER receives legacy Compose/AndroidX/Kotlin objects. */
object SidePanelUi {
    @JvmStatic
    fun create(
        activity: Activity,
        moduleResources: Context,
        saved: Bundle?,
        snapshot: Bundle,
        images: Map<String, Bitmap>,
        callbacks: Map<String, Runnable>,
        toolbar: Boolean,
    ): View {
        mainThread()
        check(moduleResources.packageName == "dev.mindlink") { "Side-panel resources must belong to MindLink" }
        val owner = SidePanelOwner(saved, snapshot, images, callbacks, toolbar)
        val context = object : ContextWrapper(activity) {
            override fun getClassLoader(): ClassLoader = SidePanelUi::class.java.classLoader!!
            override fun getResources(): Resources = moduleResources.resources
            override fun getAssets(): AssetManager = moduleResources.assets
            // alpha13 caches its motion-scale flow by application Context. Never cache this Activity wrapper.
            override fun getApplicationContext(): Context = activity.applicationContext
        }
        val panel = SidePanelView(context, owner)
        try {
            // All owners and the explicit parent belong to this view, never the WeChat DecorView.
            panel.setViewTreeLifecycleOwner(owner)
            panel.setViewTreeViewModelStoreOwner(owner)
            panel.setViewTreeSavedStateRegistryOwner(owner)
            panel.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            panel.bindParent()
            // Installation/rendering must still be verified on Android; this is not desktop navigation.
            Log.i("MindLinkSidePanel", "WeKit-view-created entry=${if (toolbar) "toolbar" else "drawer"} runtime=isolated drag=20261121-v2 hostGesture=${callbacks.containsKey("gesture_start")}")
            return panel
        } catch (error: Throwable) {
            panel.dispose()
            throw error
        }
    }

    @JvmStatic
    fun update(view: View, snapshot: Bundle, images: Map<String, Bitmap>) {
        mainThread()
        (view as SidePanelView).owner.state.update(snapshot, images)
    }

    @JvmStatic
    fun update(view: View, snapshot: Bundle, images: Map<String, Bitmap>, callbacks: Map<String, Runnable>) {
        mainThread()
        val state = (view as SidePanelView).owner.state
        state.update(snapshot, images)
        state.updateCallbacks(callbacks)
    }

    @JvmStatic
    fun save(view: View, output: Bundle) {
        mainThread()
        (view as SidePanelView).owner.save(output)
    }

    @JvmStatic
    fun lifecycle(view: View, resumed: Boolean) {
        mainThread()
        (view as SidePanelView).owner.lifecycle(resumed)
    }

    @JvmStatic
    fun dispose(view: View) {
        mainThread()
        (view as SidePanelView).dispose()
    }

    private class SidePanelView(context: Context, val owner: SidePanelOwner) : AbstractComposeView(context) {
        private var parentRecomposer: Recomposer? = null
        private var parentJob: Job? = null
        private var disposed = false

        override val shouldCreateCompositionOnAttachedToWindow: Boolean get() = !disposed

        fun bindParent() {
            if (disposed || parentRecomposer != null) return
            val job = SupervisorJob()
            try {
                // Explicit lifecycle bypasses windowRecomposer's walk to the foreign DecorView.
                // A separate job also bounds the official helper's runRecompose coroutine.
                val recomposer = createLifecycleAwareWindowRecomposer(
                    coroutineContext = job,
                    lifecycle = owner.lifecycle,
                )
                parentJob = job
                parentRecomposer = recomposer
                setParentCompositionContext(recomposer)
            } catch (error: Throwable) {
                parentRecomposer?.cancel()
                parentRecomposer = null
                parentJob = null
                job.cancel()
                throw error
            }
        }

        override fun onAttachedToWindow() {
            if (!disposed) {
                bindParent()
                owner.attached(true)
            }
            try {
                // AbstractComposeView may create the composition here; the isolated parent is ready first.
                super.onAttachedToWindow()
            } catch (error: Throwable) {
                dispose()
                throw error
            }
        }

        override fun onDetachedFromWindow() {
            try {
                super.onDetachedFromWindow()
            } finally {
                owner.attached(false)
                releaseParent()
            }
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            try {
                owner.dispose()
            } finally {
                releaseParent()
            }
        }

        private fun releaseParent() {
            val recomposer = parentRecomposer
            val job = parentJob
            parentRecomposer = null
            parentJob = null
            try {
                disposeComposition()
            } finally {
                recomposer?.cancel()
                job?.cancel()
            }
            // Keep AbstractComposeView's cancelled explicit parent until a reattach replaces it.
            // Clearing it would let a queued measure fall back to the foreign windowRecomposer.
        }

        @Composable
        override fun Content() {
            if (disposed) return
            HomeSidePanelTheme {
                val state = owner.state.uiState
                if (owner.toolbar) {
                    HomeSidePanelToolbarContent(
                        profile = state.profile,
                        onAvatarClick = owner.state::openPanel,
                        onStatusClick = owner.state::openStatusEditor,
                    )
                } else {
                    HomeSidePanelContent(state, owner.state)
                }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    Log.i("MindLinkSidePanel", "WeKit-first-composition entry=${if (owner.toolbar) "toolbar" else "drawer"}")
                }
            }
        }
    }

    private class SidePanelOwner(
        saved: Bundle?,
        snapshot: Bundle,
        images: Map<String, Bitmap>,
        callbacks: Map<String, Runnable>,
        val toolbar: Boolean,
    ) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        val state = HomeSidePanelState(snapshot, images, callbacks)
        private var destroyed = false
        private var attached = false
        private var resumeRequested = false
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        private val savedKey = if (toolbar) "mdl_sidepanel_toolbar_saved_state" else "mdl_sidepanel_saved_state"

        init {
            controller.performAttach()
            controller.performRestore(saved?.getBundle(savedKey))
            registry.currentState = Lifecycle.State.CREATED
        }

        fun lifecycle(resumed: Boolean) {
            if (destroyed) return
            resumeRequested = resumed
            syncLifecycle()
        }

        fun attached(value: Boolean) {
            if (destroyed) return
            attached = value
            syncLifecycle()
        }

        private fun syncLifecycle() {
            val resumed = attached && resumeRequested
            state.lifecycle(resumed)
            registry.currentState = if (resumed) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        }

        fun save(output: Bundle) {
            if (destroyed) return
            val saved = Bundle()
            controller.performSave(saved)
            output.putBundle(savedKey, saved)
        }

        fun dispose() {
            if (destroyed) return
            destroyed = true
            attached = false
            resumeRequested = false
            try {
                state.dispose()
            } finally {
                registry.currentState = Lifecycle.State.DESTROYED
                viewModelStore.clear()
            }
        }
    }

    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) { "Side-panel UI requires the main thread" } }
}
