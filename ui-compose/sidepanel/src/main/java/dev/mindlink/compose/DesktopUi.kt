package dev.mindlink.compose

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Chevron_right
import dev.mindlink.ui.MindLinkCard
import dev.mindlink.ui.MindLinkColors
import dev.mindlink.ui.MindLinkListRow
import dev.mindlink.ui.MindLinkPage
import dev.mindlink.ui.MindLinkSectionHeader
import dev.mindlink.ui.MindLinkTheme
import dev.mindlink.ui.MindLinkIconButton
import dev.mindlink.ui.MindLinkTopBar
import java.util.WeakHashMap

/** Launcher entry in the same payload as the drawer/settings; no host settings or objects cross it. */
object DesktopUi {
    private class Navigation(saved: Bundle?) {
        var document by mutableStateOf(saved?.getString(DOCUMENT_KEY)?.takeIf { it in documents.keys })
    }

    private val navigation = WeakHashMap<View, Navigation>()
    private const val DOCUMENT_KEY = "mdl_desktop_document"
    private val documents = linkedMapOf(
        "README.md" to "项目说明",
        "THIRD-PARTY-NOTICES.md" to "致谢与第三方声明",
        "LICENSE.txt" to "GPL-3.0",
    )

    @JvmStatic
    fun create(activity: Activity, saved: Bundle?): View {
        mainThread()
        check(activity.packageName == "dev.mindlink" && Application.getProcessName() == "dev.mindlink") {
            "Desktop UI is module-process only"
        }
        val state = Navigation(saved)
        val dark = activity.resources.configuration.uiMode and 0x30 == 0x20
        val glyphs = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        // MainActivity builds this view before setContentView, so the window has no decor yet.
        val applyBars = {
            runCatching {
                activity.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else glyphs, glyphs)
            }
        }
        applyBars()
        val view = SettingsUi.createContent(
            activity, activity, saved, Bundle(), emptyMap(), emptyMap(), emptyMap(), dark,
        ) {
            MindLinkTheme {
                DesktopScreen(activity, state)
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                applyBars()
                v.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        navigation[view] = state
        return view
    }

    @JvmStatic
    fun save(view: View, output: Bundle) {
        mainThread()
        SettingsUi.save(view, output)
        output.putString(DOCUMENT_KEY, navigation[view]?.document)
    }

    @JvmStatic
    fun lifecycle(view: View, resumed: Boolean) = SettingsUi.lifecycle(view, resumed)

    @JvmStatic
    fun back(view: View): Boolean {
        mainThread()
        val state = navigation[view] ?: return false
        if (state.document == null) return false
        state.document = null
        return true
    }

    @JvmStatic
    fun dispose(view: View) {
        mainThread()
        navigation.remove(view)
        SettingsUi.dispose(view)
    }

    @Composable
    private fun DesktopScreen(activity: Activity, state: Navigation) {
        val document = state.document
        MindLinkPage {
            MindLinkTopBar(document?.let { documents[it] }.orEmpty().ifBlank { "MindLink" }) {
                if (document != null) {
                    MindLinkIconButton(MaterialSymbols.Outlined.Close, "返回", { state.document = null })
                }
            }
            if (document != null) {
                val text = remember(document) {
                    runCatching { activity.assets.open("mindlink/$document").bufferedReader().use { it.readText() } }
                        .getOrElse { "文档不可用" }
                }
                val paragraphs = remember(text) { text.split("\n\n") }
                LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(paragraphs.size) { index ->
                        Text(
                            paragraphs[index], style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                    }
                }
            } else {
                Home(activity) { state.document = it }
            }
        }
    }

    @Composable
    private fun Home(activity: Activity, openDocument: (String) -> Unit) {
        val pm = activity.packageManager
        val version = remember { packageVersion(pm, "dev.mindlink").orEmpty() }
        val wechat = remember { packageVersion(pm, "com.tencent.mm") }
        val icon = remember {
            runCatching {
                val drawable = pm.getApplicationIcon("dev.mindlink")
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 96, 96)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()
        }
        var error by remember { mutableStateOf<String?>(null) }
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(MindLinkColors.CONTENT_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(MindLinkColors.SECTION_GAP_DP.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(64.dp)) }
                    Column {
                        Text("MindLink", style = MaterialTheme.typography.titleLarge)
                        Text(version, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                MindLinkSectionHeader("应用")
                MindLinkCard(modifier = Modifier.fillMaxWidth()) {
                    MindLinkListRow("微信", summary = wechat ?: "未安装")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MindLinkListRow("模块设置", summary = "微信内设置")
                }
            }
            item {
                Button(
                    enabled = wechat != null,
                    onClick = {
                        error = runCatching {
                            val intent = checkNotNull(pm.getLaunchIntentForPackage("com.tencent.mm"))
                            activity.startActivity(intent)
                            null
                        }.getOrElse { "无法打开微信" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("打开微信") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                MindLinkSectionHeader("关于")
                MindLinkCard(modifier = Modifier.fillMaxWidth()) {
                    documents.entries.forEachIndexed { index, (file, title) ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MindLinkListRow(
                            title, modifier = Modifier.clickable { openDocument(file) },
                            trailing = { Icon(MaterialSymbols.Outlined.Chevron_right, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }

    private fun packageVersion(pm: PackageManager, name: String): String? = try {
        pm.getPackageInfo(name, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun mainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Desktop UI requires the main thread" }
    }
}
