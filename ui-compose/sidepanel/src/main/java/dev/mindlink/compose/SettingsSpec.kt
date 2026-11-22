package dev.mindlink.compose

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.function.Function

/** Live view of the module's settings, expressed only through the bridge maps. */
internal class SettingsState(
    values: Bundle,
    private val writers: Map<String, Function<String, String>>,
    private val actions: Map<String, Runnable>,
    private val readers: Map<String, Function<String, String>>,
    val dark: Boolean,
) {
    var data by mutableStateOf(readBundle(values))
        private set
    var tabIndex by mutableStateOf(values.getString("panel_tab")?.toIntOrNull()?.coerceIn(0, 2) ?: 0)
    var pending by mutableStateOf(read("pending") == "true")
        private set
    var actionRevision by mutableStateOf(0)
        private set
    private var disposed = false

    fun value(key: String): String = data[key].orEmpty()

    fun flag(key: String): Boolean = value(key) == "true"

    fun number(key: String, fallback: Int): Int = value(key).toIntOrNull() ?: fallback

    fun write(key: String, requested: String) {
        if (disposed) return
        val effective = try {
            writers[key]?.apply(requested) ?: requested
        } catch (error: Throwable) {
            value(key)
        }
        data = data + (key to effective)
        pending = read("pending") == "true"
    }

    fun toggle(key: String, checked: Boolean) = write(key, if (checked) "true" else "false")

    fun selectTab(index: Int) {
        tabIndex = index.coerceIn(0, 2)
        write("panel_tab", tabIndex.toString())
    }

    fun action(id: String) {
        if (!disposed) {
            actions[id]?.run()
            actionRevision++
            pending = read("pending") == "true"
        }
    }

    fun pick(key: String) {
        if (!disposed) actions["pick:$key"]?.run()
    }

    fun read(id: String): String = try {
        readers[id]?.apply("") ?: ""
    } catch (error: Throwable) {
        ""
    }

    fun dispose() {
        disposed = true
    }

    private fun readBundle(bundle: Bundle): Map<String, String> = buildMap {
        for (key in bundle.keySet()) {
            runCatching { bundle.getString(key) }.getOrNull()?.let { put(key, it) }
        }
    }
}

internal sealed interface Setting {
    val key: String?

    data class Flag(override val key: String, val title: String, val summary: String? = null, val confirm: String? = null) : Setting
    data class Range(override val key: String, val title: String, val min: Int, val max: Int, val format: (Int) -> String) : Setting
    data class Pick(override val key: String, val title: String, val options: List<Pair<String, String>>) : Setting
    data class Text(override val key: String, val title: String, val placeholder: String) : Setting
    data class Photo(override val key: String, val title: String) : Setting
    data class Button(val id: String, val title: String, val summary: String? = null, val primary: Boolean = false) : Setting {
        override val key: String? get() = null
    }
    data class Note(val text: String) : Setting {
        override val key: String? get() = null
    }
    data class Tools(override val key: String) : Setting
}

internal data class Section(
    val title: String,
    val summary: String? = null,
    val rows: List<Setting>,
    val master: Setting.Flag? = null,
) {
    val id: String get() = master?.key ?: title
}

/** Explicit master keys keep subordinate switches in their feature's configuration. */
private fun Section.feature(key: String): Section = copy(
    master = rows.filterIsInstance<Setting.Flag>().single { it.key == key },
    rows = rows.filterNot { it.key == key },
)

internal val settingsTabs = listOf("首页", "聊天", "更多")

private fun dp(value: Int) = { v: Int -> "$v dp" }
private fun percent(value: Int) = { v: Int -> "$v%" }

internal fun sectionsFor(tab: Int, state: SettingsState): List<Section> = when (tab) {
    0 -> homeSections()
    1 -> chatSections(state)
    else -> moreSections()
}

private fun homeSections(): List<Section> = listOf(
    Section(
        "快捷工具",
        "自选首页顶部的扫码、收付款等快捷入口",
        listOf(
            Setting.Flag("top_scan_enabled", "扫一扫入口"),
            Setting.Flag("tool_scan", "扫一扫"),
            Setting.Flag("tool_group", "发起群聊"),
            Setting.Flag("tool_qrcode", "二维码名片"),
            Setting.Flag("tool_pay", "收付款"),
            Setting.Flag("tool_friend", "添加朋友"),
        ),
    ).feature("top_scan_enabled"),
    Section(
        "首页面板",
        null,
        listOf(
            Setting.Flag("conversation_filter_enabled", "用户分组", "启用可编辑的会话分组"),
            Setting.Flag("home_side_panel_enabled", "侧滑面板", "右滑打开、左滑收起；长按侧栏进入编辑"),
        ),
    ),
    Section(
        "首页顶栏",
        "首页顶栏显示微信本人的头像、昵称与状态",
        listOf(
            Setting.Flag("top_profile_enabled", "启用顶栏资料"),
            Setting.Text("top_title", "标题文字", "留空则保持微信原样"),
            Setting.Text("top_nickname", "昵称", "留空则不显示"),
            Setting.Text("top_status", "状态文字", "如：在线；留空则不显示"),
            Setting.Pick("top_dot_color", "状态圆点颜色", listOf("green" to "绿色", "red" to "红色")),
            Setting.Photo("top_avatar_path", "顶栏头像"),
            Setting.Flag("top_search_bar", "显示搜索框"),
            Setting.Text("top_search_hint", "搜索框提示", "留空则显示“搜索”"),
        ),
    ).feature("top_profile_enabled"),
    Section(
        "一键已读",
        "开启后在首页「+」面板显示入口",
        listOf(
            Setting.Flag("mark_all_read_enabled", "启用一键已读"),
            Setting.Button("markAllRead", "立即全部标为已读", primary = true),
        ),
    ).feature("mark_all_read_enabled"),
    Section(
        "首页底栏",
        "设置首页导航的尺寸、图标与滚动行为",
        listOf(
            Setting.Flag("bottom_bar_enabled", "启用首页底栏"),
            Setting.Flag("nav_bar_use_floating", "悬浮胶囊", "胶囊浮在列表上方，不再占据布局空间"),
            Setting.Range("nav_bar_scale", "底栏缩放", 50, 150, percent(0)),
            Setting.Flag("nav_bar_use_wechat_icons", "使用微信原生图标"),
            Setting.Flag("nav_bar_hide_labels", "隐藏标签文字"),
            Setting.Flag("nav_bar_show_finder_badge", "显示发现角标"),
            Setting.Flag("nav_bar_animate_page_change", "点击切页动画"),
            Setting.Flag("nav_bar_auto_hide_on_scroll", "会话滚动时自动收起"),
            Setting.Flag("nav_bar_dynamic_gravity_highlight", "随设备倾斜移动高光"),
        ),
    ).feature("bottom_bar_enabled"),
)

private fun chatSections(state: SettingsState): List<Section> {
    val bubbleMode = state.number("bubble_mode", 0)
    return listOf(
        Section(
            "悬浮聊天顶栏",
            "只作用于聊天页标题卡；消息列表与状态栏会同步避让",
            listOf(
                Setting.Flag("floating_chat_header_enabled", "启用悬浮顶栏"),
                Setting.Pick(
                    "floating_chat_header_layout_style",
                    "样式",
                    listOf("INTEGRATED" to "整体式", "SEPARATED" to "分体式"),
                ),
                Setting.Range("floating_chat_header_side_margin", "左右间距", 0, 32, dp(0)),
                Setting.Range("floating_chat_header_top_gap", "顶部间距", 0, 24, dp(0)),
                Setting.Range("floating_chat_header_extra_gap", "卡片间距", 0, 24, dp(0)),
                Setting.Range("floating_chat_header_corner_radius", "圆角", 0, 32, dp(0)),
                Setting.Range("floating_chat_header_elevation", "阴影高度", 0, 16, dp(0)),
                Setting.Range("floating_chat_header_component_gap", "分体组件间距", 0, 24, dp(0)),
            ),
        ).feature("floating_chat_header_enabled"),
        Section(
            "聊天工具栏",
            "输入框上方的快捷工具条，可逐项启用并调整顺序",
            listOf(
                Setting.Flag("chat_toolbar_enabled", "启用聊天工具栏"),
                Setting.Tools("chat_toolbar_enabled_items"),
            ),
        ).feature("chat_toolbar_enabled"),
        Section(
            "悬浮聊天输入栏",
            "底部间距在导航栏或键盘安全区域之上计算",
            listOf(
                Setting.Flag("floating_chat_footer_enabled", "启用悬浮输入栏"),
                Setting.Range("floating_chat_footer_bottom_gap", "底部间距", 0, 24, dp(0)),
                Setting.Range("floating_chat_footer_side_margin", "左右间距", 0, 32, dp(0)),
                Setting.Range("floating_chat_footer_corner_radius", "圆角", 0, 32, dp(0)),
                Setting.Range("floating_chat_footer_elevation", "阴影高度", 0, 16, dp(0)),
                Setting.Flag("floating_chat_footer_panel_above", "面板显示在输入栏上方"),
                Setting.Range("floating_chat_footer_more_height_dp", "更多面板高度", 220, 360, dp(0)),
            ),
        ).feature("floating_chat_footer_enabled"),
        Section(
            "自定义气泡",
            "毛玻璃 / 自定义图片（.9 图自动拉伸）/ 透明",
            buildList {
                add(Setting.Flag("bubble_enabled", "启用自定义气泡"))
                add(Setting.Pick("bubble_mode", "模式", listOf("0" to "毛玻璃", "1" to "自定义图片", "2" to "透明气泡")))
                if (bubbleMode == 0) {
                    add(Setting.Range("bubble_glass_blur_dp", "模糊强度", 0, 32, dp(0)))
                    add(Setting.Range("bubble_glass_tint", "染色浓度", 0, 200) { v -> "$v / 200" })
                    add(Setting.Note("纯色背景模糊不明显；找不到聊天背景时使用渐变降级。"))
                }
                if (bubbleMode == 1) {
                    add(Setting.Photo("bubble_bg_path_peer", "更换对方气泡图片"))
                    add(Setting.Photo("bubble_bg_path_self", "更换我方气泡图片"))
                    add(Setting.Range("bubble_bg_alpha", "图片不透明度", 0, 255) { v -> "${Math.round(v * 100f / 255f)}%" })
                }
                add(Setting.Range("bubble_corner_radius_dp", "圆角半径", 0, 40, dp(0)))
                add(Setting.Note(".9 图的圆角由原图控制。"))
            },
        ).feature("bubble_enabled"),
    )
}

private fun moreSections(): List<Section> = listOf(
    Section(
        "悬浮卡片材质", "首页底栏、聊天顶栏与输入栏共用",
        listOf(
            Setting.Pick("nav_bar_glass_mode", "表面效果", listOf("0" to "普通表面", "1" to "毛玻璃", "2" to "液态玻璃")),
            Setting.Range("nav_bar_blur_radius", "背景模糊", 0, 40, dp(0)),
        ),
    ),
    Section(
        "全局背景",
        "在所有页面（黑名单除外）叠加一张全屏背景图",
        listOf(
            Setting.Flag("global_bg_enabled", "启用全局背景"),
            Setting.Photo("global_bg_path", "更换背景图"),
            Setting.Range("global_bg_opacity", "不透明度", 1, 80, percent(0)),
            Setting.Flag("global_bg_transparent_status_bar", "透明状态栏", "背景图延伸到状态栏区域"),
        ),
    ).feature("global_bg_enabled"),
    Section(
        "全屏壁纸",
        "首页各页叠加一张全屏壁纸；扫一扫、拍摄与聊天页不显示",
        listOf(
            Setting.Flag("tab_bg_enabled", "启用全屏壁纸"),
            Setting.Photo("tab_bg_global", "更换壁纸"),
            Setting.Range("tab_bg_alpha", "蒙层不透明度", 0, 100, percent(0)),
            Setting.Note("四页共用同一张图与同一个不透明度；调到 0% 即不显示。"),
        ),
    ).feature("tab_bg_enabled"),
    Section(
        "联网天气、定位与一言",
        "默认关闭；按需开启天气、位置查询和一言",
        listOf(
            Setting.Flag(
                "location_network_enabled",
                "启用联网数据",
                "不上传聊天、联系人或资料正文",
                confirm = "手动更新定位时，经纬度会发送给 Nominatim 查询城市，并保存在本地；" +
                    "小米天气会接收保存的经纬度以获取日历天气；侧栏天气会向小米发送所选城市代码。\n" +
                    "一言会向 v1.hitokoto.cn 发送分类与长度条件。\n不会上传聊天、联系人、昵称、头像或状态正文。",
            ),
            Setting.Button("updateLocation", "更新定位"),
        ),
    ).feature("location_network_enabled"),
    Section(
        "日志与诊断",
        null,
        listOf(
            Setting.Flag("log_enabled", "调试日志", "功能异常时开启，便于排查"),
            Setting.Button("log", "查看日志与脱敏诊断"),
            Setting.Button("copyDiagnostic", "复制脱敏诊断"),
        ),
    ),
)
