package dev.mindlink.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
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
import dev.mindlink.sidepanel.vendor.wekit.HomeSidePanelTheme
import java.util.function.Function
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Material3 settings panel. Lives in the same isolated payload as the side panel, but with its own
 * host entry and lifecycle so the dialog never borrows WeChat's DecorView owners.
 */
object SettingsUi {

    @JvmStatic
    fun create(
        activity: Activity,
        moduleResources: Context,
        saved: Bundle?,
        values: Bundle,
        writers: Map<String, Function<String, String>>,
        actions: Map<String, Runnable>,
        readers: Map<String, Function<String, String>>,
        dark: Boolean,
    ): View {
        mainThread()
        check(moduleResources.packageName == "dev.mindlink") { "Settings resources must belong to MindLink" }
        val owner = SettingsOwner(saved, values, writers, actions, readers, dark)
        val context = object : ContextWrapper(activity) {
            override fun getClassLoader(): ClassLoader = SettingsUi::class.java.classLoader!!
            override fun getResources(): Resources = moduleResources.resources
            override fun getAssets(): AssetManager = moduleResources.assets
            override fun getApplicationContext(): Context = activity.applicationContext
        }
        val panel = SettingsView(context, owner)
        try {
            panel.setViewTreeLifecycleOwner(owner)
            panel.setViewTreeViewModelStoreOwner(owner)
            panel.setViewTreeSavedStateRegistryOwner(owner)
            panel.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            panel.bindParent()
            Log.i("MindLinkSettings", "panel-created runtime=isolated")
            return panel
        } catch (error: Throwable) {
            panel.dispose()
            throw error
        }
    }

    @JvmStatic
    fun save(view: View, output: Bundle) {
        mainThread()
        (view as SettingsView).owner.save(output)
    }

    @JvmStatic
    fun dispose(view: View) {
        mainThread()
        (view as SettingsView).dispose()
    }

    private class SettingsView(context: Context, val owner: SettingsOwner) : AbstractComposeView(context) {
        private var parentRecomposer: Recomposer? = null
        private var parentJob: Job? = null
        private var disposed = false

        override val shouldCreateCompositionOnAttachedToWindow: Boolean get() = !disposed

        fun bindParent() {
            if (disposed || parentRecomposer != null) return
            val job = SupervisorJob()
            try {
                // Explicit lifecycle bypasses windowRecomposer's walk to the foreign DecorView.
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
        }

        @Composable
        override fun Content() {
            if (disposed) return
            HomeSidePanelTheme {
                SettingsScreen(owner.state)
            }
        }
    }

    private class SettingsOwner(
        saved: Bundle?,
        values: Bundle,
        writers: Map<String, Function<String, String>>,
        actions: Map<String, Runnable>,
        readers: Map<String, Function<String, String>>,
        dark: Boolean,
    ) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private var destroyed = false
        private var attached = false
        val state = SettingsState(values, writers, actions, readers, dark)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            controller.performAttach()
            controller.performRestore(saved?.getBundle(SAVED_KEY))
            registry.currentState = Lifecycle.State.CREATED
        }

        fun attached(value: Boolean) {
            if (destroyed) return
            attached = value
            registry.currentState = if (attached) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        }

        fun save(output: Bundle) {
            if (destroyed) return
            val bundle = Bundle()
            controller.performSave(bundle)
            output.putBundle(SAVED_KEY, bundle)
        }

        fun dispose() {
            if (destroyed) return
            destroyed = true
            attached = false
            try {
                state.dispose()
            } finally {
                registry.currentState = Lifecycle.State.DESTROYED
                viewModelStore.clear()
            }
        }
    }

    private const val SAVED_KEY = "mdl_settings_panel_saved_state"

    private fun mainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Settings UI requires the main thread" }
    }
}
