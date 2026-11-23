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
    var pending by mutableStateOf(read("pending") == "true")
        private set
    var actionRevision by mutableStateOf(0)
        private set

    /** Detail pages opened from the overview; empty means the overview is showing. */
    var stack by mutableStateOf(emptyList<String>())
        private set
    /** Direction of the last navigation, used only for the transition. */
    var forward by mutableStateOf(true)
        private set
    private var disposed = false

    val currentPage: String? get() = stack.lastOrNull()

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

    /** Opens a detail page. Re-opening the page already on top is ignored (double taps). */
    fun open(id: String) {
        if (disposed || currentPage == id || pageOf(id) == null) return
        forward = true
        stack = stack + id
    }

    /** Pops one detail page. Returns false at the overview so the host can close the panel. */
    fun back(): Boolean {
        if (disposed || stack.isEmpty()) return false
        forward = false
        stack = stack.dropLast(1)
        return true
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
    /**
     * [fallback] marks a setting whose blank value means "follow a default that depends on other
     * settings"; the row then shows that default and offers to restore it.
     */
    data class Range(
        override val key: String, val title: String, val min: Int, val max: Int,
        val format: (Int) -> String,
        val fallback: ((SettingsState) -> Int)? = null,
    ) : Setting
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
    /** Opens another detail page, e.g. the shared card material from each floating component. */
    data class Link(val target: String, val title: String) : Setting {
        override val key: String? get() = null
    }
}

/**
 * One card inside a detail page. A section [master] gates only this card's rows, for pages that
 * bundle independent features (top bar profile vs. quick entries, global background vs. wallpaper).
 */
internal data class Section(
    val title: String?,
    val summary: String? = null,
    val rows: List<Setting>,
    val master: Setting.Flag? = null,
)

/**
 * One overview entry. [master] is the feature switch shown both on the overview row and at the top
 * of the detail page; it gates every section. A page without [sections] is a plain overview switch.
 */
internal data class SettingsPage(
    val id: String,
    val title: String,
    val summary: String,
    val master: Setting.Flag? = null,
    val sections: List<Section> = emptyList(),
    val status: ((SettingsState) -> String)? = null,
) {
    val navigable: Boolean get() = sections.isNotEmpty()
}

internal data class PageGroup(val title: String, val pages: List<SettingsPage>)

private fun dp() = { v: Int -> "$v dp" }
private fun percent() = { v: Int -> "$v%" }
private fun onOff(state: SettingsState, key: String) = if (state.flag(key)) "开" else "关"

private val glassModes = listOf("0" to "普通表面", "1" to "毛玻璃", "2" to "液态玻璃")
private val materialLink = Setting.Link(MATERIAL_PAGE, "卡片材质")

internal const val MATERIAL_PAGE = "material"

internal fun settingsGroups(state: SettingsState): List<PageGroup> = listOf(
    PageGroup("首页", listOf(topBarPage(), bottomBarPage(), groupingPage(), sidePanelPage(), markReadPage())),
    PageGroup("聊天", listOf(chatHeaderPage(state), chatToolbarPage(), chatFooterPage(), bubblePage(state))),
    PageGroup("外观", listOf(materialPage(state), backgroundPage())),
    PageGroup("隐私与诊断", listOf(networkPage(), diagnosticsPage())),
)

internal fun SettingsState.pageOf(id: String): SettingsPage? =
    settingsGroups(this).firstNotNullOfOrNull { group -> group.pages.firstOrNull { it.id == id } }

// ---------------------------------------------------------------- 首页

private fun topBarPage() = SettingsPage(
    id = "top_bar",
    title = "首页顶栏",
    summary = "悬浮卡片、资料卡、搜索框与快捷入口",
    status = {
        "卡片 ${onOff(it, "top_floating_cards")} · 资料卡 ${onOff(it, "top_profile_enabled")} · 快捷入口 ${onOff(it, "top_scan_enabled")}"
    },
    sections = listOf(
        Section(
            "悬浮卡片",
            "左边是资料卡和标题（关闭资料卡时只有标题），右边只放搜索和「+」",
            listOf(
                Setting.Flag(
                    "top_cards_hide_title", "隐藏页面标题",
                    "隐藏微信（含未读数）、通讯录、发现页顶栏的标题",
                ),
                Setting.Flag(
                    "top_cards_overlay", "内容延伸到顶栏下方",
                    "顶栏变透明，列表从卡片下方滚过；关闭则保留原来的不透明顶栏",
                ),
                Setting.Flag(
                    "top_cards_progressive_blur", "顶部渐进模糊",
                    "状态栏和卡片后方的内容越靠上越模糊，向下渐变清晰（需开启「内容延伸到顶栏下方」，Android 13+）",
                ),
                materialLink,
            ),
            master = Setting.Flag("top_floating_cards", "启用悬浮卡片"),
        ),
        Section(
            "资料卡",
            "在顶栏显示微信本人的头像与昵称",
            listOf(
                Setting.Text("top_title", "标题文字", "留空则保持微信原样"),
                Setting.Photo("top_avatar_path", "顶栏头像"),
                Setting.Flag("top_search_bar", "显示搜索框"),
                Setting.Text("top_search_hint", "搜索框提示", "留空则显示“搜索”"),
            ),
            master = Setting.Flag("top_profile_enabled", "启用资料卡"),
        ),
        Section(
            "快捷入口",
            "顶栏右侧的扫码、收付款等入口，可逐项选择",
            listOf(
                Setting.Flag("tool_scan", "扫一扫"),
                Setting.Flag("tool_group", "发起群聊"),
                Setting.Flag("tool_qrcode", "二维码名片"),
                Setting.Flag("tool_pay", "收付款"),
                Setting.Flag("tool_friend", "添加朋友"),
            ),
            master = Setting.Flag("top_scan_enabled", "启用快捷入口"),
        ),
    ),
)

private fun bottomBarPage() = SettingsPage(
    id = "bottom_bar",
    title = "首页底栏",
    summary = "导航的尺寸、图标与滚动行为",
    master = Setting.Flag("bottom_bar_enabled", "启用首页底栏"),
    sections = listOf(
        Section(
            "外观",
            null,
            listOf(
                Setting.Flag("nav_bar_use_floating", "悬浮胶囊", "胶囊浮在列表上方，不再占据布局空间"),
                Setting.Range("nav_bar_scale", "底栏缩放", 50, 150, percent()),
                Setting.Flag("nav_bar_use_wechat_icons", "使用微信原生图标"),
                Setting.Flag("nav_bar_hide_labels", "隐藏标签文字"),
                Setting.Flag("nav_bar_show_finder_badge", "显示发现角标"),
                materialLink,
            ),
        ),
        Section(
            "行为",
            null,
            listOf(
                Setting.Flag("nav_bar_animate_page_change", "点击切页动画"),
                Setting.Flag("nav_bar_auto_hide_on_scroll", "会话滚动时自动收起"),
                Setting.Flag("nav_bar_dynamic_gravity_highlight", "随设备倾斜移动高光"),
            ),
        ),
    ),
)

private fun groupingPage() = SettingsPage(
    id = "conversation_filter",
    title = "会话分组",
    summary = "在会话列表上方显示可编辑的分组",
    master = Setting.Flag("conversation_filter_enabled", "会话分组"),
)

private fun sidePanelPage() = SettingsPage(
    id = "home_side_panel",
    title = "侧滑面板",
    summary = "右滑打开、左滑收起；长按侧栏进入编辑",
    master = Setting.Flag("home_side_panel_enabled", "侧滑面板"),
)

private fun markReadPage() = SettingsPage(
    id = "mark_all_read",
    title = "一键已读",
    summary = "在首页「+」菜单显示一键已读入口",
    master = Setting.Flag("mark_all_read_enabled", "启用一键已读"),
    sections = listOf(
        Section(null, null, listOf(Setting.Button("markAllRead", "立即全部标为已读", primary = true))),
    ),
)

// ---------------------------------------------------------------- 聊天

private fun chatHeaderPage(state: SettingsState): SettingsPage {
    val separated = state.value("floating_chat_header_layout_style") == "SEPARATED"
    return SettingsPage(
        id = "chat_header",
        title = "悬浮聊天顶栏",
        summary = "只作用于聊天页标题卡；消息列表与状态栏会同步避让",
        master = Setting.Flag("floating_chat_header_enabled", "启用悬浮顶栏"),
        sections = listOf(
            Section(
                "样式",
                null,
                listOf(
                    Setting.Pick(
                        "floating_chat_header_layout_style",
                        "布局",
                        listOf("INTEGRATED" to "整体式", "SEPARATED" to "分体式"),
                    ),
                    materialLink,
                ),
            ),
            Section(
                "间距",
                null,
                buildList {
                    add(Setting.Range("floating_chat_header_side_margin", "左右间距", 0, 32, dp()))
                    add(Setting.Range("floating_chat_header_top_gap", "顶部间距", 0, 24, dp()))
                    add(Setting.Range("floating_chat_header_extra_gap", "卡片间距", 0, 24, dp()))
                    if (separated) add(Setting.Range("floating_chat_header_component_gap", "分体组件间距", 0, 24, dp()))
                },
            ),
            Section(
                "外形",
                null,
                listOf(
                    Setting.Range("floating_chat_header_corner_radius", "圆角", 0, 32, dp()),
                    Setting.Range("floating_chat_header_elevation", "阴影高度", 0, 16, dp()),
                ),
            ),
        ),
    )
}

private fun chatToolbarPage() = SettingsPage(
    id = "chat_toolbar",
    title = "聊天工具栏",
    summary = "输入框上方的快捷工具条，可逐项启用并调整顺序",
    master = Setting.Flag("chat_toolbar_enabled", "启用聊天工具栏"),
    sections = listOf(Section("工具", "长按工具栏图标也可拖动换位", listOf(Setting.Tools("chat_toolbar_enabled_items")))),
)

private fun chatFooterPage() = SettingsPage(
    id = "chat_footer",
    title = "悬浮聊天输入栏",
    summary = "底部间距在导航栏或键盘安全区域之上计算",
    master = Setting.Flag("floating_chat_footer_enabled", "启用悬浮输入栏"),
    sections = listOf(
        Section(
            "间距",
            null,
            listOf(
                Setting.Range("floating_chat_footer_bottom_gap", "底部间距", 0, 24, dp()),
                Setting.Range("floating_chat_footer_side_margin", "左右间距", 0, 32, dp()),
            ),
        ),
        Section(
            "外形",
            null,
            listOf(
                Setting.Range("floating_chat_footer_corner_radius", "圆角", 0, 32, dp()),
                Setting.Range("floating_chat_footer_elevation", "阴影高度", 0, 16, dp()),
                materialLink,
            ),
        ),
        Section(
            "面板",
            "表情、更多等面板的位置与高度",
            listOf(
                Setting.Flag("floating_chat_footer_panel_above", "面板显示在输入栏上方"),
                Setting.Range("floating_chat_footer_more_height_dp", "更多面板高度", 220, 360, dp()),
            ),
        ),
    ),
)

private fun bubblePage(state: SettingsState): SettingsPage {
    val mode = state.number("bubble_mode", 0)
    return SettingsPage(
        id = "bubble",
        title = "自定义气泡",
        summary = "毛玻璃 / 自定义图片（.9 图自动拉伸）/ 透明",
        master = Setting.Flag("bubble_enabled", "启用自定义气泡"),
        sections = buildList {
            add(Section("模式", null, listOf(Setting.Pick("bubble_mode", "气泡模式", listOf("0" to "毛玻璃", "1" to "自定义图片", "2" to "透明气泡")))))
            if (mode == 0) {
                add(
                    Section(
                        "毛玻璃",
                        "纯色背景模糊不明显；找不到聊天背景时使用渐变降级",
                        listOf(
                            Setting.Range("bubble_glass_blur_dp", "模糊强度", 0, 32, dp()),
                            Setting.Range("bubble_glass_tint", "染色浓度", 0, 200, format = { v -> "$v / 200" }),
                        ),
                    ),
                )
            }
            if (mode == 1) {
                add(
                    Section(
                        "图片",
                        ".9 图的圆角由原图控制",
                        listOf(
                            Setting.Photo("bubble_bg_path_peer", "对方气泡图片"),
                            Setting.Photo("bubble_bg_path_self", "我方气泡图片"),
                            Setting.Range("bubble_bg_alpha", "图片不透明度", 0, 255, format = { v -> "${Math.round(v * 100f / 255f)}%" }),
                        ),
                    ),
                )
            }
            add(Section("外形", null, listOf(Setting.Range("bubble_corner_radius_dp", "圆角半径", 0, 40, dp()))))
        },
    )
}

// ---------------------------------------------------------------- 外观

/** Matches LiquidGlassLayer.tintAlpha's defaults: liquid 40% (0% without blur), frosted 65%. */
internal fun defaultSurfaceOpacity(state: SettingsState): Int = when (state.value("nav_bar_glass_mode")) {
    "1" -> 65
    "2" -> if (state.number("nav_bar_blur_radius", 8) > 0) 40 else 0
    else -> 100
}

private fun surfaceOpacity(state: SettingsState): Int =
    state.value("nav_bar_surface_opacity").toIntOrNull() ?: defaultSurfaceOpacity(state)

private fun materialPage(state: SettingsState): SettingsPage {
    val glass = state.value("nav_bar_glass_mode") != "0"
    return SettingsPage(
        id = MATERIAL_PAGE,
        title = "卡片材质",
        summary = "首页底栏、会话分组、聊天顶栏与输入栏共用",
        status = { s ->
            val mode = glassModes.firstOrNull { it.first == s.value("nav_bar_glass_mode") }?.second ?: "普通表面"
            if (s.value("nav_bar_glass_mode") == "0") mode
            else "$mode · 模糊 ${s.number("nav_bar_blur_radius", 0)} dp · 不透明度 ${surfaceOpacity(s)}%"
        },
        sections = buildList {
            add(Section(null, null, listOf(Setting.Pick("nav_bar_glass_mode", "表面效果", glassModes))))
            if (glass) {
                add(
                    Section(
                        "玻璃",
                        "不透明度是盖在模糊背景上的底色浓度；调低更通透，调高更接近实色。背景无法采样时仍显示实色。",
                        listOf(
                            Setting.Range("nav_bar_blur_radius", "背景模糊", 0, 40, dp()),
                            Setting.Range(
                                "nav_bar_surface_opacity", "表面不透明度", 0, 100, percent(),
                                fallback = ::defaultSurfaceOpacity,
                            ),
                        ),
                    ),
                )
            } else {
                add(Section(null, null, listOf(Setting.Note("普通表面为实色卡片，不使用模糊与不透明度。"))))
            }
        },
    )
}

private fun backgroundPage() = SettingsPage(
    id = "background",
    title = "背景与壁纸",
    summary = "全局背景作用于所有页面，首页壁纸只作用于首页四页",
    status = { "全局背景 ${onOff(it, "global_bg_enabled")} · 首页壁纸 ${onOff(it, "tab_bg_enabled")}" },
    sections = listOf(
        Section(
            "全局背景",
            "在所有页面（黑名单除外）叠加一张全屏背景图",
            listOf(
                Setting.Photo("global_bg_path", "背景图"),
                Setting.Range("global_bg_opacity", "不透明度", 1, 80, percent()),
                Setting.Flag("global_bg_transparent_status_bar", "透明状态栏", "背景图延伸到状态栏区域"),
            ),
            master = Setting.Flag("global_bg_enabled", "启用全局背景"),
        ),
        Section(
            "首页壁纸",
            "首页四页共用同一张图与不透明度；扫一扫、拍摄与聊天页不显示",
            listOf(
                Setting.Photo("tab_bg_global", "壁纸"),
                Setting.Range("tab_bg_alpha", "蒙层不透明度", 0, 100, percent()),
                Setting.Note("调到 0% 即不显示。"),
            ),
            master = Setting.Flag("tab_bg_enabled", "启用首页壁纸"),
        ),
    ),
)

// ---------------------------------------------------------------- 隐私与诊断

private fun networkPage() = SettingsPage(
    id = "network",
    title = "联网数据",
    summary = "天气、位置查询与一言；默认关闭",
    master = Setting.Flag(
        "location_network_enabled",
        "启用联网数据",
        "不上传聊天、联系人或资料正文",
        confirm = "手动更新定位时，经纬度会发送给 Nominatim 查询城市，并保存在本地；" +
            "小米天气会接收保存的经纬度以获取日历天气；侧栏天气会向小米发送所选城市代码。\n" +
            "一言会向 v1.hitokoto.cn 发送分类与长度条件。\n不会上传聊天、联系人、昵称、头像或状态正文。",
    ),
    sections = listOf(
        Section(
            "定位",
            "需要先在系统设置中授予微信定位权限",
            listOf(Setting.Button("updateLocation", "更新定位")),
        ),
        Section(
            "会发送的数据",
            null,
            listOf(
                Setting.Note("定位：经纬度发送给 Nominatim 查询城市，结果只保存在本地。"),
                Setting.Note("天气：小米天气接收保存的经纬度或所选城市代码。"),
                Setting.Note("一言：向 v1.hitokoto.cn 发送分类与长度条件。"),
            ),
        ),
    ),
)

private fun diagnosticsPage() = SettingsPage(
    id = "diagnostics",
    title = "日志与诊断",
    summary = "功能异常时开启调试日志，便于排查",
    status = { "调试日志 ${onOff(it, "log_enabled")}" },
    sections = listOf(
        Section(
            null,
            null,
            listOf(
                Setting.Flag("log_enabled", "调试日志", "功能异常时开启，便于排查"),
                Setting.Button("log", "查看日志"),
                Setting.Button("copyDiagnostic", "复制脱敏诊断"),
            ),
        ),
    ),
)
