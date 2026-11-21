package wex.ui.core.feature;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;

/**
 * Tabler Outline 矢量图标。
 *
 * 数据：@tabler/icons 3.31.0 · MIT · Copyright (c) 2020-2024 Paweł Kuna。
 * 统一 24x24 栅格、2px 描边、圆头圆角，与原手绘 PNG 归一化后的笔画比例一致。
 *
 * 弧线（A 命令）在入库前已全部转成三次贝塞尔，所以这里只需解析 M / L / C / Z。
 * 不依赖 android.util.PathParser（本工程没有该类）。
 * 同一图标的多段子路径用 \n 分隔。
 */
public final class TablerIcons {

    private static final String TAG = "WEX: TablerIcons: ";

    /** Tabler 坐标栅格。 */
    private static final float GRID = 24f;
    /**
     * 设计安全区：Tabler 图标本体大致落在栅格的 2..22 之间，跨 20 个单位。
     *
     * 原手绘 PNG 是「裁掉透明边 → 撑满整个方框」，视觉上图形本体等于方框边长。
     * 若直接按 24 栅格铺满，所有图标会整体缩小约 17%（20/24）且描边同时变细。
     * 这里按安全区归一化：让 20 个单位映射到方框边长，既保持所有图标共用同一
     * 缩放（描边粗细一致），又与替换前的视觉尺寸对齐。
     */
    private static final float SAFE = 20f;
    /** Tabler 基准描边（栅格单位）。 */
    private static final float STROKE = 2f;
    /**
     * 实际描边宽度：STROKE × SAFE / GRID ≈ 1.667。
     *
     * 原手绘 PNG 的笔画约占图形本体的 8.3%，而 Tabler 默认 2/24 在本工程里
     * 会因为本体只有 20 单位而显粗到 10%。按安全区等比回退后，
     * 18dp 图标在 3x 屏上是 4.5px 描边，与替换前完全一致。
     */
    private static final float STROKE_SAFE = STROKE * SAFE / GRID;
    /** 取不到屏幕密度时的兜底。 */
    private static final float FALLBACK_DENSITY = 3f;

    /** 键沿用原 assets 文件名，调用方无需改动字符串常量。 */
    private static final String[][] ICONS = {
        { "album.png", "M15 8L15.01 8\nM3 6C3 4.343 4.343 3 6 3L18 3C19.657 3 21 4.343 21 6L21 18C21 19.657 19.657 21 18 21L6 21C4.343 21 3 19.657 3 18L3 6Z\nM3 16L8 11C8.928 10.107 10.072 10.107 11 11L16 16\nM14 14L15 13C15.928 12.107 17.072 12.107 18 13L21 16" },
        { "shoot.png", "M5 7L6 7C7.105 7 8 6.105 8 5C8 4.448 8.448 4 9 4L15 4C15.552 4 16 4.448 16 5C16 6.105 16.895 7 18 7L19 7C20.105 7 21 7.895 21 9L21 18C21 19.105 20.105 20 19 20L5 20C3.895 20 3 19.105 3 18L3 9C3 7.895 3.895 7 5 7\nM9 13C9 14.657 10.343 16 12 16C13.657 16 15 14.657 15 13C15 11.343 13.657 10 12 10C10.343 10 9 11.343 9 13" },
        { "video_call.png", "M15 10L19.553 7.724C19.863 7.569 20.231 7.586 20.526 7.768C20.82 7.95 21 8.272 21 8.618L21 15.382C21 15.728 20.82 16.05 20.526 16.232C20.231 16.414 19.863 16.431 19.553 16.276L15 14L15 10Z\nM3 6M3 8C3 6.895 3.895 6 5 6L13 6C14.105 6 15 6.895 15 8L15 16C15 17.105 14.105 18 13 18L5 18C3.895 18 3 17.105 3 16Z" },
        { "voice_call.png", "M5 4L9 4L11 9L8.5 10.5C9.571 12.672 11.328 14.429 13.5 15.5L15 13L20 15L20 19C20 20.105 19.105 21 18 21C9.928 20.509 3.491 14.072 3 6C3 4.895 3.895 4 5 4" },
        { "location.png", "M9 11C9 12.657 10.343 14 12 14C13.657 14 15 12.657 15 11C15 9.343 13.657 8 12 8C10.343 8 9 9.343 9 11\nM17.657 16.657L13.414 20.9C12.633 21.68 11.368 21.68 10.587 20.9L6.343 16.657C3.219 13.533 3.219 8.468 6.343 5.343C9.467 2.219 14.533 2.219 17.657 5.343C20.781 8.468 20.781 13.533 17.657 16.657Z" },
        { "redpacket.png", "M3 8M3 9C3 8.448 3.448 8 4 8L20 8C20.552 8 21 8.448 21 9L21 11C21 11.552 20.552 12 20 12L4 12C3.448 12 3 11.552 3 11Z\nM12 8L12 21\nM19 12L19 19C19 20.105 18.105 21 17 21L7 21C5.895 21 5 20.105 5 19L5 12\nM7.5 8C6.119 8 5 6.881 5 5.5C5 4.119 6.119 3 7.5 3C9.474 2.966 11.26 4.949 12 8C12.74 4.949 14.526 2.966 16.5 3C17.881 3 19 4.119 19 5.5C19 6.881 17.881 8 16.5 8" },
        { "transfer.png", "M7 10L21 10L17 6\nM17 14L3 14L7 18" },
        { "file.png", "M14 3L14 7C14 7.552 14.448 8 15 8L19 8\nM17 21L7 21C5.895 21 5 20.105 5 19L5 5C5 3.895 5.895 3 7 3L14 3L19 8L19 19C19 20.105 18.105 21 17 21Z\nM9 9L10 9\nM9 13L15 13\nM9 17L15 17" },
        { "favorite.png", "M12 17.75L5.828 20.995L7.007 14.122L2.007 9.255L8.907 8.255L11.993 2.002L15.079 8.255L21.979 9.255L16.979 14.122L18.158 20.995Z" },
        { "chain.png", "M11 6L20 6\nM11 12L20 12\nM12 18L20 18\nM4 16C4 14.895 4.895 14 6 14C7.105 14 8 14.895 8 16C8 16.591 7.5 17 7 17.5L4 20L8 20\nM6 10L6 4L4 6" },
        { "card.png", "M3 4M3 7C3 5.343 4.343 4 6 4L18 4C19.657 4 21 5.343 21 7L21 17C21 18.657 19.657 20 18 20L6 20C4.343 20 3 18.657 3 17Z\nM9 10M7 10C7 11.105 7.895 12 9 12C10.105 12 11 11.105 11 10C11 8.895 10.105 8 9 8C7.895 8 7 8.895 7 10\nM15 8L17 8\nM15 12L17 12\nM7 16L17 16" },
        { "music.png", "M3 17C3 18.657 4.343 20 6 20C7.657 20 9 18.657 9 17C9 15.343 7.657 14 6 14C4.343 14 3 15.343 3 17\nM13 17C13 18.657 14.343 20 16 20C17.657 20 19 18.657 19 17C19 15.343 17.657 14 16 14C14.343 14 13 15.343 13 17\nM9 17L9 4L19 4L19 17\nM9 8L19 8" },
        { "more.png", "M5 12M4 12C4 12.552 4.448 13 5 13C5.552 13 6 12.552 6 12C6 11.448 5.552 11 5 11C4.448 11 4 11.448 4 12\nM12 12M11 12C11 12.552 11.448 13 12 13C12.552 13 13 12.552 13 12C13 11.448 12.552 11 12 11C11.448 11 11 11.448 11 12\nM19 12M18 12C18 12.552 18.448 13 19 13C19.552 13 20 12.552 20 12C20 11.448 19.552 11 19 11C18.448 11 18 11.448 18 12" },
        { "mark_read.png", "M7 12L12 17L22 7\nM2 12L7 17M12 12L17 7" },
        { "wex_setting_icon.png", "M10.325 4.317C10.751 2.561 13.249 2.561 13.675 4.317C13.805 4.852 14.182 5.293 14.691 5.504C15.199 5.714 15.778 5.67 16.248 5.383C17.791 4.443 19.558 6.209 18.618 7.753C18.332 8.223 18.287 8.801 18.498 9.309C18.708 9.818 19.148 10.195 19.683 10.325C21.439 10.751 21.439 13.249 19.683 13.675C19.148 13.805 18.707 14.182 18.496 14.691C18.286 15.199 18.33 15.778 18.617 16.248C19.557 17.791 17.791 19.558 16.247 18.618C15.777 18.332 15.199 18.287 14.691 18.498C14.182 18.708 13.805 19.148 13.675 19.683C13.249 21.439 10.751 21.439 10.325 19.683C10.195 19.148 9.818 18.707 9.309 18.496C8.801 18.286 8.222 18.33 7.752 18.617C6.209 19.557 4.442 17.791 5.382 16.247C5.668 15.777 5.713 15.199 5.502 14.691C5.292 14.182 4.852 13.805 4.317 13.675C2.561 13.249 2.561 10.751 4.317 10.325C4.852 10.195 5.293 9.818 5.504 9.309C5.714 8.801 5.67 8.222 5.383 7.752C4.443 6.209 6.209 4.442 7.753 5.382C8.753 5.99 10.049 5.452 10.325 4.317Z\nM9 12C9 13.657 10.343 15 12 15C13.657 15 15 13.657 15 12C15 10.343 13.657 9 12 9C10.343 9 9 10.343 9 12" },
        { "topbar_search.png", "M10 10M3 10C3 13.866 6.134 17 10 17C13.866 17 17 13.866 17 10C17 6.134 13.866 3 10 3C6.134 3 3 6.134 3 10\nM21 21L15 15" },
        { "gift.png", "M3 5M3 8C3 6.343 4.343 5 6 5L18 5C19.657 5 21 6.343 21 8L21 16C21 17.657 19.657 19 18 19L6 19C4.343 19 3 17.657 3 16Z\nM7 16L10 13L13 16\nM8 13C7.211 13 6 12.328 6 11.5C6 10.672 6.711 10 7.5 10C8.628 9.98 9.577 11.17 10 13C10.423 11.17 11.372 9.98 12.5 10C13.289 10 14 10.672 14 11.5C14 12.328 12.789 13 12 13L8 13Z" },
        { "live.png", "M18.364 19.364C21.879 15.849 21.879 10.151 18.364 6.636C14.849 3.121 9.151 3.121 5.636 6.636C2.121 10.151 2.121 15.849 5.636 19.364\nM15.536 16.536C17.488 14.583 17.488 11.418 15.535 9.465C13.583 7.513 10.417 7.513 8.465 9.465C6.512 11.418 6.512 14.583 8.464 16.536\nM12 13M11 13C11 13.552 11.448 14 12 14C12.552 14 13 13.552 13 13C13 12.448 12.552 12 12 12C11.448 12 11 12.448 11 13" },
        { "group_tool.png", "M10 13C10 14.105 10.895 15 12 15C13.105 15 14 14.105 14 13C14 11.895 13.105 11 12 11C10.895 11 10 11.895 10 13\nM8 21L8 20C8 18.895 8.895 18 10 18L14 18C15.105 18 16 18.895 16 20L16 21\nM15 5C15 6.105 15.895 7 17 7C18.105 7 19 6.105 19 5C19 3.895 18.105 3 17 3C15.895 3 15 3.895 15 5\nM17 10L19 10C20.105 10 21 10.895 21 12L21 13\nM5 5C5 6.105 5.895 7 7 7C8.105 7 9 6.105 9 5C9 3.895 8.105 3 7 3C5.895 3 5 3.895 5 5\nM3 13L3 12C3 10.895 3.895 10 5 10L7 10" },
        { "voice_input.png", "M9 2M9 5C9 3.343 10.343 2 12 2L12 2C13.657 2 15 3.343 15 5L15 10C15 11.657 13.657 13 12 13L12 13C10.343 13 9 11.657 9 10Z\nM5 10C5 13.866 8.134 17 12 17C15.866 17 19 13.866 19 10\nM8 21L16 21\nM12 17L12 21" },
        { "reply.png", "M8 9L16 9\nM8 13L14 13\nM18 4C19.657 4 21 5.343 21 7L21 15C21 16.657 19.657 18 18 18L13 18L8 21L8 18L6 18C4.343 18 3 16.657 3 15L3 7C3 5.343 4.343 4 6 4L18 4Z" },
        { "上一曲.png", "M20 5L20 19L8 12Z\nM4 5L4 19" },
        { "下一曲.png", "M4 5L4 19L16 12Z\nM20 5L20 19" },
        { "关闭.png", "M18 6L6 18\nM6 6L18 18" },
        { "搜索.png", "M10 10M3 10C3 13.866 6.134 17 10 17C13.866 17 17 13.866 17 10C17 6.134 13.866 3 10 3C6.134 3 3 6.134 3 10\nM21 21L15 15" },
        { "播放列表.png", "M14 17M11 17C11 18.657 12.343 20 14 20C15.657 20 17 18.657 17 17C17 15.343 15.657 14 14 14C12.343 14 11 15.343 11 17\nM17 17L17 4L21 4\nM13 5L3 5\nM3 9L13 9\nM9 13L3 13" },
        { "点击播放.png", "M6 4L6 20C6 20.362 6.196 20.696 6.512 20.873C6.828 21.05 7.215 21.042 7.524 20.852L20.524 12.852C20.82 12.67 21 12.347 21 12C21 11.653 20.82 11.33 20.524 11.148L7.524 3.148C7.215 2.958 6.828 2.95 6.512 3.127C6.196 3.304 6 3.638 6 4Z" },
        { "点击暂停.png", "M9 4L7 4C5.895 4 5 4.895 5 6L5 18C5 19.105 5.895 20 7 20L9 20C10.105 20 11 19.105 11 18L11 6C11 4.895 10.105 4 9 4Z\nM17 4L15 4C13.895 4 13 4.895 13 6L13 18C13 19.105 13.895 20 15 20L17 20C18.105 20 19 19.105 19 18L19 6C19 4.895 18.105 4 17 4Z" },
        { "已收藏.png", "M8.243 7.34L1.863 8.265L1.75 8.288C1.402 8.38 1.13 8.653 1.039 9.002C0.948 9.35 1.051 9.721 1.31 9.972L5.932 14.471L4.842 20.826L4.829 20.936C4.808 21.296 4.982 21.64 5.285 21.836C5.589 22.031 5.974 22.048 6.293 21.88L11.999 18.88L17.692 21.88L17.792 21.926C18.128 22.058 18.509 21.999 18.79 21.771C19.07 21.543 19.205 21.182 19.144 20.826L18.053 14.471L22.677 9.971L22.755 9.886C22.982 9.607 23.041 9.227 22.91 8.892C22.779 8.557 22.478 8.318 22.122 8.266L15.742 7.34L12.89 1.56C12.722 1.218 12.374 1.002 11.993 1.002C11.612 1.002 11.264 1.218 11.096 1.56L8.243 7.34Z" },
        { "未收藏.png", "M12 17.75L5.828 20.995L7.007 14.122L2.007 9.255L8.907 8.255L11.993 2.002L15.079 8.255L21.979 9.255L16.979 14.122L18.158 20.995Z" },
        { "shell_chat", "M3 20L4.3 16.1C1.976 12.663 2.874 8.228 6.4 5.726C9.926 3.225 14.99 3.43 18.245 6.206C21.5 8.983 21.94 13.472 19.274 16.707C16.608 19.942 11.659 20.922 7.7 19L3 20" },
        { "shell_home", "M4 4M4 6C4 4.895 4.895 4 6 4L8 4C9.105 4 10 4.895 10 6L10 18C10 19.105 9.105 20 8 20L6 20C4.895 20 4 19.105 4 18Z\nM14 4M14 6C14 4.895 14.895 4 16 4L18 4C19.105 4 20 4.895 20 6L20 12C20 13.105 19.105 14 18 14L16 14C14.895 14 14 13.105 14 12Z" },
        { "shell_top", "M4 4M4 6C4 4.895 4.895 4 6 4L18 4C19.105 4 20 4.895 20 6L20 18C20 19.105 19.105 20 18 20L6 20C4.895 20 4 19.105 4 18Z\nM4 9L20 9" },
        { "shell_bg", "M15 8L15.01 8\nM3 6C3 4.343 4.343 3 6 3L18 3C19.657 3 21 4.343 21 6L21 18C21 19.657 19.657 21 18 21L6 21C4.343 21 3 19.657 3 18L3 6Z\nM3 16L8 11C8.928 10.107 10.072 10.107 11 11L16 16\nM14 14L15 13C15.928 12.107 17.072 12.107 18 13L21 16" },
        { "shell_more", "M12 12M3 12C3 16.971 7.029 21 12 21C16.971 21 21 16.971 21 12C21 7.029 16.971 3 12 3C7.029 3 3 7.029 3 12\nM8 12L8 12.01\nM12 12L12 12.01\nM16 12L16 12.01" },
        { "shell_close", "M18 6l-12 12\nM6 6l12 12" },
        { "shell_back", "M15 6l-6 6l6 6" },
        { "shell_next", "M9 6l6 6l-6 6" },
        { "shell_restart", "M20 11C19.497 7.383 16.636 4.549 13.014 4.082C9.391 3.614 5.905 5.629 4.5 9M4 5L4 9L8 9\nM4 13C4.503 16.617 7.364 19.451 10.986 19.918C14.609 20.386 18.095 18.371 19.5 15M20 19L20 15L16 15" },
        // 侧栏快捷动作卡（Tabler outline 3.31.0，弧线已转三次贝塞尔）。
        { "qrcode", "M4.00 4.00\nM4.00 5.00\nC4.00 4.45 4.45 4.00 5.00 4.00\nL9.00 4.00\nC9.55 4.00 10.00 4.45 10.00 5.00\nL10.00 9.00\nC10.00 9.55 9.55 10.00 9.00 10.00\nL5.00 10.00\nC4.45 10.00 4.00 9.55 4.00 9.00\nZ\nM7.00 17.00\nL7.00 17.01\nM14.00 4.00\nM14.00 5.00\nC14.00 4.45 14.45 4.00 15.00 4.00\nL19.00 4.00\nC19.55 4.00 20.00 4.45 20.00 5.00\nL20.00 9.00\nC20.00 9.55 19.55 10.00 19.00 10.00\nL15.00 10.00\nC14.45 10.00 14.00 9.55 14.00 9.00\nZ\nM7.00 7.00\nL7.00 7.01\nM4.00 14.00\nM4.00 15.00\nC4.00 14.45 4.45 14.00 5.00 14.00\nL9.00 14.00\nC9.55 14.00 10.00 14.45 10.00 15.00\nL10.00 19.00\nC10.00 19.55 9.55 20.00 9.00 20.00\nL5.00 20.00\nC4.45 20.00 4.00 19.55 4.00 19.00\nZ\nM17.00 7.00\nL17.00 7.01\nM14.00 14.00\nL17.00 14.00\nM20.00 14.00\nL20.00 14.01\nM14.00 14.00\nL14.00 17.00\nM14.00 20.00\nL17.00 20.00\nM17.00 17.00\nL20.00 17.00\nM20.00 17.00\nL20.00 20.00" },
        { "wallet", "M17.00 8.00\nL17.00 5.00\nC17.00 4.45 16.55 4.00 16.00 4.00\nL6.00 4.00\nC4.90 4.00 4.00 4.90 4.00 6.00\nC4.00 7.10 4.90 8.00 6.00 8.00\nL18.00 8.00\nC18.55 8.00 19.00 8.45 19.00 9.00\nL19.00 12.00\nM19.00 16.00\nL19.00 19.00\nC19.00 19.55 18.55 20.00 18.00 20.00\nL6.00 20.00\nC4.90 20.00 4.00 19.10 4.00 18.00\nL4.00 6.00\nM20.00 12.00\nL20.00 16.00\nL16.00 16.00\nC14.90 16.00 14.00 15.10 14.00 14.00\nC14.00 12.90 14.90 12.00 16.00 12.00\nL20.00 12.00" },
        { "user-plus", "M8.00 7.00\nC8.00 9.21 9.79 11.00 12.00 11.00\nC14.21 11.00 16.00 9.21 16.00 7.00\nC16.00 4.79 14.21 3.00 12.00 3.00\nC9.79 3.00 8.00 4.79 8.00 7.00\nM16.00 19.00\nL22.00 19.00\nM19.00 16.00\nL19.00 22.00\nM6.00 21.00\nL6.00 19.00\nC6.00 16.79 7.79 15.00 10.00 15.00\nL14.00 15.00" },
        { "id-badge", "M5.00 3.00\nM5.00 6.00\nC5.00 4.34 6.34 3.00 8.00 3.00\nL16.00 3.00\nC17.66 3.00 19.00 4.34 19.00 6.00\nL19.00 18.00\nC19.00 19.66 17.66 21.00 16.00 21.00\nL8.00 21.00\nC6.34 21.00 5.00 19.66 5.00 18.00\nZ\nM12.00 13.00\nM10.00 13.00\nC10.00 14.10 10.90 15.00 12.00 15.00\nC13.10 15.00 14.00 14.10 14.00 13.00\nC14.00 11.90 13.10 11.00 12.00 11.00\nC10.90 11.00 10.00 11.90 10.00 13.00\nM10.00 6.00\nL14.00 6.00\nM9.00 18.00\nL15.00 18.00" },
        { "users-group", "M10.00 13.00\nC10.00 14.10 10.90 15.00 12.00 15.00\nC13.10 15.00 14.00 14.10 14.00 13.00\nC14.00 11.90 13.10 11.00 12.00 11.00\nC10.90 11.00 10.00 11.90 10.00 13.00\nM8.00 21.00\nL8.00 20.00\nC8.00 18.90 8.90 18.00 10.00 18.00\nL14.00 18.00\nC15.10 18.00 16.00 18.90 16.00 20.00\nL16.00 21.00\nM15.00 5.00\nC15.00 6.10 15.90 7.00 17.00 7.00\nC18.10 7.00 19.00 6.10 19.00 5.00\nC19.00 3.90 18.10 3.00 17.00 3.00\nC15.90 3.00 15.00 3.90 15.00 5.00\nM17.00 10.00\nL19.00 10.00\nC20.10 10.00 21.00 10.90 21.00 12.00\nL21.00 13.00\nM5.00 5.00\nC5.00 6.10 5.90 7.00 7.00 7.00\nC8.10 7.00 9.00 6.10 9.00 5.00\nC9.00 3.90 8.10 3.00 7.00 3.00\nC5.90 3.00 5.00 3.90 5.00 5.00\nM3.00 13.00\nL3.00 12.00\nC3.00 10.90 3.90 10.00 5.00 10.00\nL7.00 10.00" },
    };

    /** 实心变体（Tabler filled）：仅用于必须靠填充区分状态的图标。 */
    private static final String[][] FILLED_ICONS = {
        { "star-filled", "M8.243 7.34L1.863 8.265L1.75 8.288C1.402 8.38 1.13 8.653 1.039 9.002C0.948 9.35 1.051 9.721 1.31 9.972L5.932 14.471L4.842 20.826L4.829 20.936C4.808 21.296 4.982 21.64 5.285 21.836C5.589 22.031 5.974 22.048 6.293 21.88L11.999 18.88L17.692 21.88L17.792 21.926C18.128 22.058 18.509 21.999 18.79 21.771C19.07 21.543 19.205 21.182 19.144 20.826L18.053 14.471L22.677 9.971L22.755 9.886C22.982 9.607 23.041 9.227 22.91 8.892C22.779 8.557 22.478 8.318 22.122 8.266L15.742 7.34L12.89 1.56C12.722 1.218 12.374 1.002 11.993 1.002C11.612 1.002 11.264 1.218 11.096 1.56L8.243 7.34Z" },
        { "play-filled", "M6 4L6 20C6 20.362 6.196 20.696 6.512 20.873C6.828 21.05 7.215 21.042 7.524 20.852L20.524 12.852C20.82 12.67 21 12.347 21 12C21 11.653 20.82 11.33 20.524 11.148L7.524 3.148C7.215 2.958 6.828 2.95 6.512 3.127C6.196 3.304 6 3.638 6 4Z" },
        { "pause-filled", "M9 4L7 4C5.895 4 5 4.895 5 6L5 18C5 19.105 5.895 20 7 20L9 20C10.105 20 11 19.105 11 18L11 6C11 4.895 10.105 4 9 4Z\nM17 4L15 4C13.895 4 13 4.895 13 6L13 18C13 19.105 13.895 20 15 20L17 20C18.105 20 19 19.105 19 18L19 6C19 4.895 18.105 4 17 4Z" },
        { "prev-filled", "M19.496 4.136L7.496 11.136C7.189 11.315 6.999 11.644 6.999 12C6.999 12.356 7.189 12.685 7.496 12.864L19.496 19.864C19.805 20.045 20.188 20.046 20.498 19.867C20.809 19.689 21 19.358 21 19L21 5C21 4.642 20.809 4.311 20.498 4.133C20.188 3.954 19.805 3.955 19.496 4.136Z\nM4 4C4.507 4 4.934 4.38 4.993 4.883L5 5L5 19C4.999 19.529 4.587 19.966 4.059 19.997C3.53 20.028 3.069 19.642 3.007 19.117L3 19L3 5C3 4.448 3.448 4 4 4Z" },
        { "next-filled", "M3 5L3 19C3 19.358 3.191 19.689 3.502 19.867C3.812 20.046 4.195 20.045 4.504 19.864L16.504 12.864C16.811 12.685 17.001 12.356 17.001 12C17.001 11.644 16.811 11.315 16.504 11.136L4.504 4.136C4.195 3.955 3.812 3.954 3.502 4.133C3.191 4.311 3 4.642 3 5Z\nM20 4C20.507 4 20.934 4.38 20.993 4.883L21 5L21 19C20.999 19.529 20.587 19.966 20.059 19.997C19.53 20.028 19.069 19.642 19.007 19.117L19 19L19 5C19 4.448 19.448 4 20 4Z" },
    };

    private static final HashMap<String, String> DATA = new HashMap<String, String>();
    private static final HashMap<String, String> FILLED_DATA = new HashMap<String, String>();

    /** 主表中按实心入库的图标（原 assets 就是实心图）。 */
    private static final java.util.HashSet<String> SOLID = new java.util.HashSet<String>();
    /** 固定颜色图标（不随主题变）。已收藏沿用原图的品牌红。 */
    private static final HashMap<String, Integer> FIXED_COLOR = new HashMap<String, Integer>();

    static {
        SOLID.add("点击播放.png");
        SOLID.add("点击暂停.png");
        SOLID.add("已收藏.png");
        FIXED_COLOR.put("已收藏.png", 0xFFFF262E);
    }
    private static final HashMap<String, Path> CACHE = new HashMap<String, Path>();

    static {
        for (String[] row : ICONS) {
            DATA.put(row[0], row[1]);
        }
        for (String[] row : FILLED_ICONS) {
            FILLED_DATA.put(row[0], row[1]);
        }
    }

    private TablerIcons() {
    }

    /** 是否收录。 */
    public static boolean has(String name) {
        return name != null && (DATA.containsKey(name) || FILLED_DATA.containsKey(name));
    }

    /** 是否收录为实心变体（含主表里按实心入库的播放/暂停/收藏）。 */
    public static boolean isFilled(String name) {
        if (name == null) {
            return false;
        }
        return FILLED_DATA.containsKey(name) || SOLID.contains(name);
    }

    /** 收录数量。 */
    public static int count() {
        return DATA.size() + FILLED_DATA.size();
    }

    // ------------------------------------------------------------ 主题色

    /** 系统是否处于深色模式（不需要 Context，进程内全局可读）。 */
    public static boolean isDark() {
        try {
            Resources r = Resources.getSystem();
            int uiMode = r.getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** 主题描边色：浅色 #1A1A1A，深色 #E6E6E6。 */
    public static int themeColor() {
        return isDark() ? 0xFFE6E6E6 : 0xFF1A1A1A;
    }

    /** 图标实际用色：有固定色用固定色，否则跟随主题。 */
    public static int colorOf(String name) {
        Integer fixed = name == null ? null : FIXED_COLOR.get(name);
        return fixed == null ? themeColor() : fixed;
    }

    /** 屏幕密度。 */
    public static float density() {
        try {
            return Resources.getSystem().getDisplayMetrics().density;
        } catch (Throwable ignore) {
            return FALLBACK_DENSITY;
        }
    }

    // ------------------------------------------------------------ 渲染

    /** 按像素渲染，自动取该图标的颜色（固定色优先，否则主题色）。 */
    public static Bitmap bitmapPx(String name, int sizePx) {
        return bitmap(name, sizePx, colorOf(name));
    }

    /** 按 dp 渲染，自动取该图标的颜色。 */
    public static Bitmap bitmapDp(String name, float sizeDp) {
        return bitmap(name, toPx(sizeDp), colorOf(name));
    }

    /**
     * 渲染为方图位图。
     *
     * @param name   原 assets 文件名，如 "album.png"
     * @param sizePx 目标边长（像素）
     * @param color  描边颜色（ARGB）
     */
    public static Bitmap bitmap(String name, int sizePx, int color) {
        int size = sizePx <= 0 ? 1 : sizePx;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Path p = path(name);
            if (p == null) {
                return bmp;
            }
            float scale = size / SAFE;
            Canvas c = new Canvas(bmp);
            // 把 24 栅格居中放进方框，安全区正好铺满。
            float offset = (size - GRID * scale) / 2f;
            c.translate(offset, offset);
            c.scale(scale, scale);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            if (isFilled(name)) {
                paint.setStyle(Paint.Style.FILL);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                // 小尺寸下描边不足 1 像素会断线，按像素下限兜底。
                paint.setStrokeWidth(Math.max(STROKE_SAFE, 1f / scale));
            }
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(p, paint);
        } catch (Throwable t) {
            log("渲染失败 " + name + " " + t.getMessage());
        }
        return bmp;
    }

    /** Drawable，指定颜色。 */
    public static Drawable drawable(String name, int sizePx, int color) {
        return new BitmapDrawable(null, bitmap(name, sizePx, color));
    }

    /** Drawable，按 dp + 该图标的颜色。 */
    public static Drawable drawableDp(String name, float sizeDp) {
        return drawable(name, toPx(sizeDp), colorOf(name));
    }

    /** Drawable，按 dp + 指定颜色。 */
    public static Drawable drawableDp(String name, float sizeDp, int color) {
        return drawable(name, toPx(sizeDp), color);
    }

    /** dp → px。 */
    public static int toPx(float dp) {
        return Math.max(1, (int) (dp * density() + 0.5f));
    }

    /** 取（并缓存）24 栅格坐标系下的 Path。 */
    public static Path path(String name) {
        Path cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        String data = DATA.get(name);
        if (data == null) {
            data = FILLED_DATA.get(name);
        }
        if (data == null) {
            return null;
        }
        Path p = new Path();
        try {
            for (String d : data.split("\n")) {
                appendTo(p, d);
            }
        } catch (Throwable t) {
            log("解析失败 " + name + " " + t.getMessage());
            return null;
        }
        CACHE.put(name, p);
        return p;
    }

    /** 解析单段 M/L/C/Z（绝对坐标）路径。 */
    private static void appendTo(Path path, String d) {
        int n = d.length();
        int i = 0;
        char cmd = 0;
        float cx = 0f;
        float cy = 0f;
        float sx = 0f;
        float sy = 0f;
        while (i < n) {
            char ch = d.charAt(i);
            if (ch == ' ') {
                i++;
                continue;
            }
            if (Character.isLetter(ch)) {
                if (ch == 'Z' || ch == 'z') {
                    path.close();
                    cx = sx;
                    cy = sy;
                }
                cmd = ch;
                i++;
                continue;
            }
            if (cmd == 'M') {
                cx = read(d, i);
                i = end(d, i);
                cy = read(d, i);
                i = end(d, i);
                sx = cx;
                sy = cy;
                path.moveTo(cx, cy);
            } else if (cmd == 'L') {
                cx = read(d, i);
                i = end(d, i);
                cy = read(d, i);
                i = end(d, i);
                path.lineTo(cx, cy);
            } else if (cmd == 'C') {
                float x1 = read(d, i);
                i = end(d, i);
                float y1 = read(d, i);
                i = end(d, i);
                float x2 = read(d, i);
                i = end(d, i);
                float y2 = read(d, i);
                i = end(d, i);
                cx = read(d, i);
                i = end(d, i);
                cy = read(d, i);
                i = end(d, i);
                path.cubicTo(x1, y1, x2, y2, cx, cy);
            } else {
                // 未知命令：跳过记号，避免死循环。
                i = end(d, i);
            }
        }
    }

    private static float read(String s, int from) {
        int at = start(s, from);
        int stop = end(s, at);
        if (stop <= at) {
            return 0f;
        }
        try {
            return Float.parseFloat(s.substring(at, stop));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static int start(String s, int from) {
        int i = from;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static int end(String s, int from) {
        int i = start(s, from);
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
            i++;
        }
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            i++;
        }
        return i > from ? i : from + 1;
    }

    private static void log(String msg) {
        try {
            de.robv.android.xposed.XposedBridge.log(TAG + msg);
        } catch (Throwable ignore) {
        }
    }
}
