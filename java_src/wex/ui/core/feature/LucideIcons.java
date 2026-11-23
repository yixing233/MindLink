package wex.ui.core.feature;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * Lucide 线性矢量图标（全模块 View 侧唯一图标来源）。
 *
 * 数据：lucide-static · ISC · Copyright (c) Lucide Icons and Contributors，
 * 由 scripts/import_lucide_icons.py 从 icon-nodes.json 生成，见 THIRD-PARTY-NOTICES.md。
 * 统一 24x24 栅格、2px 描边、圆头圆角。
 *
 * circle/rect/line/弧线等在生成时已全部转成绝对坐标的 M / L / C / Z，所以这里只需解析这四种命令。
 * 不依赖 android.util.PathParser（本工程没有该类）。
 * 同一图标的多段子路径用 \n 分隔。
 */
public final class LucideIcons {

    private static final String TAG = "WEX: LucideIcons: ";

    /** Lucide 坐标栅格。 */
    private static final float GRID = 24f;
    /**
     * 设计安全区：Lucide 图标本体大致落在栅格的 2..22 之间，跨 20 个单位。
     *
     * 原手绘 PNG 是「裁掉透明边 → 撑满整个方框」，视觉上图形本体等于方框边长。
     * 若直接按 24 栅格铺满，所有图标会整体缩小约 17%（20/24）且描边同时变细。
     * 这里按安全区归一化：让 20 个单位映射到方框边长，既保持所有图标共用同一
     * 缩放（描边粗细一致），又与替换前的视觉尺寸对齐。
     */
    private static final float SAFE = 20f;
    /** Lucide 基准描边（栅格单位）。 */
    private static final float STROKE = 2f;
    /**
     * 实际描边宽度：STROKE × SAFE / GRID ≈ 1.667。
     *
     * 原手绘 PNG 的笔画约占图形本体的 8.3%，而 Lucide 默认 2/24 在本工程里
     * 会因为本体只有 20 单位而显粗到 10%。按安全区等比回退后，
     * 18dp 图标在 3x 屏上是 4.5px 描边，与替换前完全一致。
     */
    private static final float STROKE_SAFE = STROKE * SAFE / GRID;
    /** 取不到屏幕密度时的兜底。 */
    private static final float FALLBACK_DENSITY = 3f;

    // BEGIN GENERATED (scripts/import_lucide_icons.py)
    /** Lucide 1.77.0 · ISC · Copyright (c) Lucide Icons and Contributors。键沿用原 assets 文件名，调用方无需改动。 */
    private static final String[][] ICONS = {
        { "album.png", "M 5 3 L 19 3 C 20.105 3 21 3.895 21 5 L 21 19 C 21 20.105 20.105 21 19 21 L 5 21 C 3.895 21 3 20.105 3 19 L 3 5 C 3 3.895 3.895 3 5 3 Z\nM 11 9 C 11 10.105 10.105 11 9 11 C 7.895 11 7 10.105 7 9 C 7 7.895 7.895 7 9 7 C 10.105 7 11 7.895 11 9 Z\nM 21 15 L 17.914 11.914 C 17.133 11.133 15.867 11.133 15.086 11.914 L 6 21" }, // image
        { "shoot.png", "M 13.997 4 C 14.732 4 15.408 4.403 15.757 5.05 L 16.243 5.95 C 16.592 6.597 17.268 7 18.003 7 L 20 7 C 21.105 7 22 7.895 22 9 L 22 18 C 22 19.105 21.105 20 20 20 L 4 20 C 2.895 20 2 19.105 2 18 L 2 9 C 2 7.895 2.895 7 4 7 L 5.997 7 C 6.731 7 7.406 6.598 7.756 5.952 L 8.245 5.048 C 8.595 4.402 9.27 4 10.004 4 Z\nM 15 13 C 15 14.657 13.657 16 12 16 C 10.343 16 9 14.657 9 13 C 9 11.343 10.343 10 12 10 C 13.657 10 15 11.343 15 13 Z" }, // camera
        { "video_call.png", "M 16 13 L 21.223 16.482 C 21.376 16.584 21.574 16.594 21.736 16.507 C 21.898 16.42 22 16.25 22 16.066 L 22 7.87 C 22 7.691 21.904 7.525 21.749 7.436 C 21.594 7.347 21.403 7.348 21.248 7.438 L 16 10.5\nM 4 6 L 14 6 C 15.105 6 16 6.895 16 8 L 16 16 C 16 17.105 15.105 18 14 18 L 4 18 C 2.895 18 2 17.105 2 16 L 2 8 C 2 6.895 2.895 6 4 6 Z" }, // video
        { "voice_call.png", "M 13.832 16.568 C 14.257 16.763 14.761 16.637 15.045 16.265 L 15.4 15.8 C 15.778 15.296 16.37 15 17 15 L 20 15 C 21.105 15 22 15.895 22 17 L 22 20 C 22 21.105 21.105 22 20 22 C 10.059 22 2 13.941 2 4 C 2 2.895 2.895 2 4 2 L 7 2 C 8.105 2 9 2.895 9 4 L 9 7 C 9 7.63 8.704 8.222 8.2 8.6 L 7.732 8.951 C 7.353 9.24 7.231 9.756 7.44 10.184 C 8.807 12.96 11.054 15.205 13.832 16.568" }, // phone
        { "location.png", "M 20 10 C 20 14.993 14.461 20.193 12.601 21.799 C 12.245 22.067 11.755 22.067 11.399 21.799 C 9.539 20.193 4 14.993 4 10 C 4 5.582 7.582 2 12 2 C 16.418 2 20 5.582 20 10\nM 15 10 C 15 11.657 13.657 13 12 13 C 10.343 13 9 11.657 9 10 C 9 8.343 10.343 7 12 7 C 13.657 7 15 8.343 15 10 Z" }, // map-pin
        { "redpacket.png", "M 12 7 L 12 21\nM 20 11 L 20 19 C 20 20.105 19.105 21 18 21 L 6 21 C 4.895 21 4 20.105 4 19 L 4 11\nM 7.5 7 C 6.119 7 5 5.881 5 4.5 C 5 3.119 6.119 2 7.5 2 C 9.474 1.966 11.26 3.949 12 7 C 12.74 3.949 14.526 1.966 16.5 2 C 17.881 2 19 3.119 19 4.5 C 19 5.881 17.881 7 16.5 7\nM 4 7 L 20 7 C 20.552 7 21 7.448 21 8 L 21 10 C 21 10.552 20.552 11 20 11 L 4 11 C 3.448 11 3 10.552 3 10 L 3 8 C 3 7.448 3.448 7 4 7 Z" }, // gift
        { "transfer.png", "M 8 3 L 4 7 L 8 11\nM 4 7 L 20 7\nM 16 21 L 20 17 L 16 13\nM 20 17 L 4 17" }, // arrow-left-right
        { "file.png", "M 6 22 C 4.895 22 4 21.105 4 20 L 4 4 C 4 2.895 4.895 2 6 2 L 14 2 C 14.639 1.999 15.253 2.253 15.704 2.706 L 19.292 6.294 C 19.746 6.745 20.001 7.36 20 8 L 20 20 C 20 21.105 19.105 22 18 22 Z\nM 14 2 L 14 7 C 14 7.552 14.448 8 15 8 L 20 8\nM 10 9 L 8 9\nM 16 13 L 8 13\nM 16 17 L 8 17" }, // file-text
        { "favorite.png", "M 11.525 2.295 C 11.614 2.114 11.798 2 12 2 C 12.202 2 12.386 2.114 12.475 2.295 L 14.785 6.974 C 15.094 7.599 15.69 8.033 16.38 8.134 L 21.546 8.89 C 21.746 8.919 21.912 9.059 21.974 9.251 C 22.036 9.442 21.984 9.653 21.84 9.794 L 18.104 13.432 C 17.604 13.919 17.375 14.622 17.493 15.31 L 18.375 20.45 C 18.41 20.65 18.329 20.852 18.165 20.971 C 18.001 21.09 17.783 21.105 17.604 21.01 L 12.986 18.582 C 12.368 18.258 11.631 18.258 11.013 18.582 L 6.396 21.01 C 6.217 21.105 6 21.089 5.836 20.97 C 5.673 20.851 5.591 20.649 5.626 20.45 L 6.507 15.311 C 6.625 14.622 6.397 13.92 5.896 13.432 L 2.16 9.795 C 2.014 9.654 1.962 9.443 2.024 9.25 C 2.087 9.058 2.253 8.917 2.454 8.889 L 7.619 8.134 C 8.31 8.034 8.907 7.6 9.216 6.974 Z" }, // star
        { "chain.png", "M 11 5 L 21 5\nM 11 12 L 21 12\nM 11 19 L 21 19\nM 4 4 L 5 4 L 5 9\nM 4 9 L 6 9\nM 6.5 20 L 3.4 20 C 3.4 19 6 18.075 6 16.5 C 6 15.883 5.622 15.329 5.048 15.103 C 4.473 14.878 3.82 15.027 3.4 15.48" }, // list-ordered
        { "card.png", "M 16 2 L 16 4\nM 7 21 L 7 19 C 7 17.895 7.895 17 9 17 L 15 17 C 16.105 17 17 17.895 17 19 L 17 21\nM 8 2 L 8 4\nM 15 10 C 15 11.657 13.657 13 12 13 C 10.343 13 9 11.657 9 10 C 9 8.343 10.343 7 12 7 C 13.657 7 15 8.343 15 10 Z\nM 5 3 L 19 3 C 20.105 3 21 3.895 21 5 L 21 19 C 21 20.105 20.105 21 19 21 L 5 21 C 3.895 21 3 20.105 3 19 L 3 5 C 3 3.895 3.895 3 5 3 Z" }, // contact
        { "music.png", "M 21 16 L 21 3 C 21 2.685 20.852 2.389 20.6 2.2 C 20.348 2.011 20.022 1.952 19.72 2.04 L 9.72 4.957 C 9.293 5.081 9 5.473 9 5.917 L 9 19\nM 21 16 C 21 17.657 19.657 19 18 19 C 16.343 19 15 17.657 15 16 C 15 14.343 16.343 13 18 13 C 19.657 13 21 14.343 21 16 Z\nM 9 19 C 9 20.657 7.657 22 6 22 C 4.343 22 3 20.657 3 19 C 3 17.343 4.343 16 6 16 C 7.657 16 9 17.343 9 19 Z" }, // eighth-notes
        { "more.png", "M 13 12 C 13 12.552 12.552 13 12 13 C 11.448 13 11 12.552 11 12 C 11 11.448 11.448 11 12 11 C 12.552 11 13 11.448 13 12 Z\nM 20 12 C 20 12.552 19.552 13 19 13 C 18.448 13 18 12.552 18 12 C 18 11.448 18.448 11 19 11 C 19.552 11 20 11.448 20 12 Z\nM 6 12 C 6 12.552 5.552 13 5 13 C 4.448 13 4 12.552 4 12 C 4 11.448 4.448 11 5 11 C 5.552 11 6 11.448 6 12 Z" }, // ellipsis
        { "mark_read.png", "M 18 6 L 7 17 L 2 12\nM 22 10 L 14.5 17.5 L 13 16" }, // check-check
        { "wex_setting_icon.png", "M 9.671 4.136 C 9.785 2.935 10.794 2.017 12 2.017 C 13.207 2.017 14.216 2.935 14.33 4.136 C 14.397 4.896 14.831 5.575 15.491 5.957 C 16.152 6.338 16.957 6.373 17.649 6.051 C 18.745 5.553 20.04 5.969 20.643 7.011 C 21.245 8.054 20.958 9.383 19.979 10.084 C 19.355 10.522 18.983 11.237 18.983 11.999 C 18.983 12.762 19.355 13.477 19.979 13.915 C 20.958 14.616 21.245 15.945 20.643 16.988 C 20.04 18.03 18.745 18.446 17.649 17.948 C 16.957 17.626 16.152 17.661 15.491 18.042 C 14.831 18.424 14.397 19.103 14.33 19.863 C 14.216 21.064 13.207 21.982 12.001 21.982 C 10.794 21.982 9.785 21.064 9.671 19.863 C 9.604 19.103 9.17 18.423 8.509 18.042 C 7.848 17.66 7.043 17.625 6.351 17.948 C 5.255 18.446 3.96 18.03 3.357 16.988 C 2.755 15.945 3.042 14.616 4.021 13.915 C 4.645 13.477 5.017 12.762 5.017 11.999 C 5.017 11.237 4.645 10.522 4.021 10.084 C 3.044 9.383 2.757 8.054 3.359 7.013 C 3.961 5.971 5.254 5.555 6.35 6.051 C 7.042 6.373 7.847 6.338 8.508 5.957 C 9.168 5.575 9.602 4.896 9.669 4.136\nM 15 12 C 15 13.657 13.657 15 12 15 C 10.343 15 9 13.657 9 12 C 9 10.343 10.343 9 12 9 C 13.657 9 15 10.343 15 12 Z" }, // settings
        { "topbar_search.png", "M 21 21 L 16.66 16.66\nM 19 11 C 19 15.418 15.418 19 11 19 C 6.582 19 3 15.418 3 11 C 3 6.582 6.582 3 11 3 C 15.418 3 19 6.582 19 11 Z" }, // search
        { "gift.png", "M 11 21.73 C 11.619 22.087 12.381 22.087 13 21.73 L 20 17.73 C 20.618 17.373 20.999 16.714 21 16 L 21 8 C 20.999 7.286 20.618 6.627 20 6.27 L 13 2.27 C 12.381 1.913 11.619 1.913 11 2.27 L 4 6.27 C 3.382 6.627 3.001 7.286 3 8 L 3 16 C 3.001 16.714 3.382 17.373 4 17.73 Z\nM 12 22 L 12 12\nM 3.29 7 L 12 12 L 20.71 7\nM 7.5 4.27 L 16.5 9.42" }, // package
        { "live.png", "M 16.247 7.761 C 18.585 10.103 18.585 13.897 16.247 16.239\nM 19.075 4.933 C 22.975 8.837 22.975 15.163 19.075 19.067\nM 4.925 19.067 C 1.025 15.163 1.025 8.837 4.925 4.933\nM 7.753 16.239 C 5.415 13.897 5.415 10.103 7.753 7.761\nM 14 12 C 14 13.105 13.105 14 12 14 C 10.895 14 10 13.105 10 12 C 10 10.895 10.895 10 12 10 C 13.105 10 14 10.895 14 12 Z" }, // radio
        { "group_tool.png", "M 16 21 L 16 19 C 16 16.791 14.209 15 12 15 L 6 15 C 3.791 15 2 16.791 2 19 L 2 21\nM 16 3.128 C 17.764 3.585 18.996 5.177 18.996 7 C 18.996 8.823 17.764 10.415 16 10.872\nM 22 21 L 22 19 C 21.999 17.177 20.765 15.586 19 15.13\nM 13 7 C 13 9.209 11.209 11 9 11 C 6.791 11 5 9.209 5 7 C 5 4.791 6.791 3 9 3 C 11.209 3 13 4.791 13 7 Z" }, // users
        { "voice_input.png", "M 12 19 L 12 22\nM 19 10 L 19 12 C 19 15.866 15.866 19 12 19 C 8.134 19 5 15.866 5 12 L 5 10\nM 12 2 L 12 2 C 13.657 2 15 3.343 15 5 L 15 12 C 15 13.657 13.657 15 12 15 L 12 15 C 10.343 15 9 13.657 9 12 L 9 5 C 9 3.343 10.343 2 12 2 Z" }, // mic
        { "reply.png", "M 22 17 C 22 18.105 21.105 19 20 19 L 6.828 19 C 6.298 19 5.789 19.211 5.414 19.586 L 3.212 21.788 C 3.009 21.991 2.704 22.052 2.438 21.942 C 2.173 21.832 2 21.573 2 21.286 L 2 5 C 2 3.895 2.895 3 4 3 L 20 3 C 21.105 3 22 3.895 22 5 Z\nM 7 11 L 17 11\nM 7 15 L 13 15\nM 7 7 L 15 7" }, // message-square-text
        { "上一曲.png", "M 17.971 4.285 C 18.589 3.914 19.358 3.905 19.985 4.26 C 20.612 4.615 21 5.279 21 6 L 21 18 C 21 18.721 20.612 19.385 19.985 19.74 C 19.358 20.095 18.589 20.086 17.971 19.715 L 7.974 13.717 C 7.37 13.356 7 12.705 7 12.002 C 6.999 11.299 7.368 10.647 7.971 10.285 Z\nM 3 20 L 3 4" }, // skip-back
        { "下一曲.png", "M 21 4 L 21 20\nM 6.029 4.285 C 5.411 3.914 4.642 3.905 4.015 4.26 C 3.388 4.615 3 5.279 3 6 L 3 18 C 3 18.721 3.388 19.385 4.015 19.74 C 4.642 20.095 5.411 20.086 6.029 19.715 L 16.026 13.717 C 16.63 13.356 17 12.705 17 12.002 C 17.001 11.299 16.632 10.647 16.029 10.285 Z" }, // skip-forward
        { "关闭.png", "M 18 6 L 6 18\nM 6 6 L 18 18" }, // x
        { "搜索.png", "M 21 21 L 16.66 16.66\nM 19 11 C 19 15.418 15.418 19 11 19 C 6.582 19 3 15.418 3 11 C 3 6.582 6.582 3 11 3 C 15.418 3 19 6.582 19 11 Z" }, // search
        { "播放列表.png", "M 16 5 L 3 5\nM 11 12 L 3 12\nM 11 19 L 3 19\nM 21 16 L 21 5\nM 21 16 C 21 17.657 19.657 19 18 19 C 16.343 19 15 17.657 15 16 C 15 14.343 16.343 13 18 13 C 19.657 13 21 14.343 21 16 Z" }, // list-music
        { "点击播放.png", "M 5 5 C 5 4.284 5.383 3.622 6.004 3.265 C 6.625 2.908 7.389 2.911 8.008 3.272 L 20.005 10.27 C 20.621 10.628 21.001 11.286 21.001 11.998 C 21.002 12.71 20.624 13.369 20.008 13.728 L 8.008 20.728 C 7.389 21.089 6.625 21.092 6.004 20.735 C 5.383 20.378 5 19.716 5 19 Z" }, // play
        { "点击暂停.png", "M 15 3 L 18 3 C 18.552 3 19 3.448 19 4 L 19 20 C 19 20.552 18.552 21 18 21 L 15 21 C 14.448 21 14 20.552 14 20 L 14 4 C 14 3.448 14.448 3 15 3 Z\nM 6 3 L 9 3 C 9.552 3 10 3.448 10 4 L 10 20 C 10 20.552 9.552 21 9 21 L 6 21 C 5.448 21 5 20.552 5 20 L 5 4 C 5 3.448 5.448 3 6 3 Z" }, // pause
        { "已收藏.png", "M 11.525 2.295 C 11.614 2.114 11.798 2 12 2 C 12.202 2 12.386 2.114 12.475 2.295 L 14.785 6.974 C 15.094 7.599 15.69 8.033 16.38 8.134 L 21.546 8.89 C 21.746 8.919 21.912 9.059 21.974 9.251 C 22.036 9.442 21.984 9.653 21.84 9.794 L 18.104 13.432 C 17.604 13.919 17.375 14.622 17.493 15.31 L 18.375 20.45 C 18.41 20.65 18.329 20.852 18.165 20.971 C 18.001 21.09 17.783 21.105 17.604 21.01 L 12.986 18.582 C 12.368 18.258 11.631 18.258 11.013 18.582 L 6.396 21.01 C 6.217 21.105 6 21.089 5.836 20.97 C 5.673 20.851 5.591 20.649 5.626 20.45 L 6.507 15.311 C 6.625 14.622 6.397 13.92 5.896 13.432 L 2.16 9.795 C 2.014 9.654 1.962 9.443 2.024 9.25 C 2.087 9.058 2.253 8.917 2.454 8.889 L 7.619 8.134 C 8.31 8.034 8.907 7.6 9.216 6.974 Z" }, // star
        { "未收藏.png", "M 11.525 2.295 C 11.614 2.114 11.798 2 12 2 C 12.202 2 12.386 2.114 12.475 2.295 L 14.785 6.974 C 15.094 7.599 15.69 8.033 16.38 8.134 L 21.546 8.89 C 21.746 8.919 21.912 9.059 21.974 9.251 C 22.036 9.442 21.984 9.653 21.84 9.794 L 18.104 13.432 C 17.604 13.919 17.375 14.622 17.493 15.31 L 18.375 20.45 C 18.41 20.65 18.329 20.852 18.165 20.971 C 18.001 21.09 17.783 21.105 17.604 21.01 L 12.986 18.582 C 12.368 18.258 11.631 18.258 11.013 18.582 L 6.396 21.01 C 6.217 21.105 6 21.089 5.836 20.97 C 5.673 20.851 5.591 20.649 5.626 20.45 L 6.507 15.311 C 6.625 14.622 6.397 13.92 5.896 13.432 L 2.16 9.795 C 2.014 9.654 1.962 9.443 2.024 9.25 C 2.087 9.058 2.253 8.917 2.454 8.889 L 7.619 8.134 C 8.31 8.034 8.907 7.6 9.216 6.974 Z" }, // star
        { "shell_chat", "M 2.992 16.342 C 3.139 16.713 3.172 17.119 3.086 17.509 L 2.021 20.799 C 1.951 21.138 2.062 21.489 2.314 21.727 C 2.565 21.965 2.922 22.056 3.257 21.967 L 6.67 20.969 C 7.038 20.896 7.419 20.928 7.769 21.061 C 12.178 23.12 17.428 21.707 20.208 17.713 C 22.987 13.72 22.489 8.306 19.028 4.886 C 15.567 1.467 10.147 1.035 6.187 3.863 C 2.228 6.692 0.88 11.959 2.992 16.342" }, // message-circle
        { "shell_home", "M 15 21 L 15 13 C 15 12.448 14.552 12 14 12 L 10 12 C 9.448 12 9 12.448 9 13 L 9 21\nM 3 10 C 3 9.411 3.259 8.852 3.709 8.472 L 10.709 2.472 C 11.454 1.842 12.546 1.842 13.291 2.472 L 20.291 8.472 C 20.741 8.852 21 9.411 21 10 L 21 19 C 21 20.105 20.105 21 19 21 L 5 21 C 3.895 21 3 20.105 3 19 Z" }, // house
        { "shell_top", "M 5 3 L 19 3 C 20.105 3 21 3.895 21 5 L 21 19 C 21 20.105 20.105 21 19 21 L 5 21 C 3.895 21 3 20.105 3 19 L 3 5 C 3 3.895 3.895 3 5 3 Z\nM 3 9 L 21 9" }, // panel-top
        { "shell_bg", "M 12 17 L 12 21\nM 8 21 L 16 21\nM 9 17 L 15.1 10.9 C 15.882 10.134 17.134 10.138 17.91 10.91 L 22 15\nM 10 9 C 10 10.105 9.105 11 8 11 C 6.895 11 6 10.105 6 9 C 6 7.895 6.895 7 8 7 C 9.105 7 10 7.895 10 9 Z\nM 4 3 L 20 3 C 21.105 3 22 3.895 22 5 L 22 15 C 22 16.105 21.105 17 20 17 L 4 17 C 2.895 17 2 16.105 2 15 L 2 5 C 2 3.895 2.895 3 4 3 Z" }, // wallpaper
        { "shell_more", "M 22 12 C 22 17.523 17.523 22 12 22 C 6.477 22 2 17.523 2 12 C 2 6.477 6.477 2 12 2 C 17.523 2 22 6.477 22 12 Z\nM 17 12 L 17.01 12\nM 12 12 L 12.01 12\nM 7 12 L 7.01 12" }, // circle-ellipsis
        { "shell_close", "M 18 6 L 6 18\nM 6 6 L 18 18" }, // x
        { "shell_back", "M 15 18 L 9 12 L 15 6" }, // chevron-left
        { "shell_next", "M 9 18 L 15 12 L 9 6" }, // chevron-right
        { "shell_restart", "M 3 12 C 3 7.029 7.029 3 12 3 C 14.516 3.009 16.931 3.991 18.74 5.74 L 21 8\nM 21 3 L 21 8 L 16 8\nM 21 12 C 21 16.971 16.971 21 12 21 C 9.484 20.991 7.069 20.009 5.26 18.26 L 3 16\nM 8 16 L 3 16 L 3 21" }, // refresh-cw
        { "qrcode", "M 4 3 L 7 3 C 7.552 3 8 3.448 8 4 L 8 7 C 8 7.552 7.552 8 7 8 L 4 8 C 3.448 8 3 7.552 3 7 L 3 4 C 3 3.448 3.448 3 4 3 Z\nM 17 3 L 20 3 C 20.552 3 21 3.448 21 4 L 21 7 C 21 7.552 20.552 8 20 8 L 17 8 C 16.448 8 16 7.552 16 7 L 16 4 C 16 3.448 16.448 3 17 3 Z\nM 4 16 L 7 16 C 7.552 16 8 16.448 8 17 L 8 20 C 8 20.552 7.552 21 7 21 L 4 21 C 3.448 21 3 20.552 3 20 L 3 17 C 3 16.448 3.448 16 4 16 Z\nM 21 16 L 18 16 C 16.895 16 16 16.895 16 18 L 16 21\nM 21 21 L 21 21.01\nM 12 7 L 12 10 C 12 11.105 11.105 12 10 12 L 7 12\nM 3 12 L 3.01 12\nM 12 3 L 12.01 3\nM 12 16 L 12 16.01\nM 16 12 L 17 12\nM 21 12 L 21 12.01\nM 12 21 L 12 20" }, // qr-code
        { "wallet", "M 19 7 L 19 4 C 19 3.448 18.552 3 18 3 L 5 3 C 3.895 3 3 3.895 3 5 C 3 6.105 3.895 7 5 7 L 20 7 C 20.552 7 21 7.448 21 8 L 21 12 L 18 12 C 16.895 12 16 12.895 16 14 C 16 15.105 16.895 16 18 16 L 21 16 C 21.552 16 22 15.552 22 15 L 22 13 C 22 12.448 21.552 12 21 12\nM 3 5 L 3 19 C 3 20.105 3.895 21 5 21 L 20 21 C 20.552 21 21 20.552 21 20 L 21 16" }, // wallet
        { "user-plus", "M 16 21 L 16 19 C 16 16.791 14.209 15 12 15 L 6 15 C 3.791 15 2 16.791 2 19 L 2 21\nM 13 7 C 13 9.209 11.209 11 9 11 C 6.791 11 5 9.209 5 7 C 5 4.791 6.791 3 9 3 C 11.209 3 13 4.791 13 7 Z\nM 19 8 L 19 14\nM 22 11 L 16 11" }, // user-plus
        { "id-badge", "M 13 19 C 13 16.791 11.209 15 9 15 C 6.791 15 5 16.791 5 19\nM 16 10 L 18 10\nM 16 14 L 18 14\nM 12 12 C 12 13.657 10.657 15 9 15 C 7.343 15 6 13.657 6 12 C 6 10.343 7.343 9 9 9 C 10.657 9 12 10.343 12 12 Z\nM 4 5 L 20 5 C 21.105 5 22 5.895 22 7 L 22 17 C 22 18.105 21.105 19 20 19 L 4 19 C 2.895 19 2 18.105 2 17 L 2 7 C 2 5.895 2.895 5 4 5 Z" }, // id-card
        { "users-group", "M 16 21 L 16 19 C 16 16.791 14.209 15 12 15 L 6 15 C 3.791 15 2 16.791 2 19 L 2 21\nM 16 3.128 C 17.764 3.585 18.996 5.177 18.996 7 C 18.996 8.823 17.764 10.415 16 10.872\nM 22 21 L 22 19 C 21.999 17.177 20.765 15.586 19 15.13\nM 13 7 C 13 9.209 11.209 11 9 11 C 6.791 11 5 9.209 5 7 C 5 4.791 6.791 3 9 3 C 11.209 3 13 4.791 13 7 Z" }, // users
    };

    /** 实心态：同一 Lucide 轮廓，填充闭合区域并以同色描边。 */
    private static final String[][] FILLED_ICONS = {
        { "star-filled", "M 11.525 2.295 C 11.614 2.114 11.798 2 12 2 C 12.202 2 12.386 2.114 12.475 2.295 L 14.785 6.974 C 15.094 7.599 15.69 8.033 16.38 8.134 L 21.546 8.89 C 21.746 8.919 21.912 9.059 21.974 9.251 C 22.036 9.442 21.984 9.653 21.84 9.794 L 18.104 13.432 C 17.604 13.919 17.375 14.622 17.493 15.31 L 18.375 20.45 C 18.41 20.65 18.329 20.852 18.165 20.971 C 18.001 21.09 17.783 21.105 17.604 21.01 L 12.986 18.582 C 12.368 18.258 11.631 18.258 11.013 18.582 L 6.396 21.01 C 6.217 21.105 6 21.089 5.836 20.97 C 5.673 20.851 5.591 20.649 5.626 20.45 L 6.507 15.311 C 6.625 14.622 6.397 13.92 5.896 13.432 L 2.16 9.795 C 2.014 9.654 1.962 9.443 2.024 9.25 C 2.087 9.058 2.253 8.917 2.454 8.889 L 7.619 8.134 C 8.31 8.034 8.907 7.6 9.216 6.974 Z" }, // star
        { "play-filled", "M 5 5 C 5 4.284 5.383 3.622 6.004 3.265 C 6.625 2.908 7.389 2.911 8.008 3.272 L 20.005 10.27 C 20.621 10.628 21.001 11.286 21.001 11.998 C 21.002 12.71 20.624 13.369 20.008 13.728 L 8.008 20.728 C 7.389 21.089 6.625 21.092 6.004 20.735 C 5.383 20.378 5 19.716 5 19 Z" }, // play
        { "pause-filled", "M 15 3 L 18 3 C 18.552 3 19 3.448 19 4 L 19 20 C 19 20.552 18.552 21 18 21 L 15 21 C 14.448 21 14 20.552 14 20 L 14 4 C 14 3.448 14.448 3 15 3 Z\nM 6 3 L 9 3 C 9.552 3 10 3.448 10 4 L 10 20 C 10 20.552 9.552 21 9 21 L 6 21 C 5.448 21 5 20.552 5 20 L 5 4 C 5 3.448 5.448 3 6 3 Z" }, // pause
        { "prev-filled", "M 17.971 4.285 C 18.589 3.914 19.358 3.905 19.985 4.26 C 20.612 4.615 21 5.279 21 6 L 21 18 C 21 18.721 20.612 19.385 19.985 19.74 C 19.358 20.095 18.589 20.086 17.971 19.715 L 7.974 13.717 C 7.37 13.356 7 12.705 7 12.002 C 6.999 11.299 7.368 10.647 7.971 10.285 Z\nM 3 20 L 3 4" }, // skip-back
        { "next-filled", "M 21 4 L 21 20\nM 6.029 4.285 C 5.411 3.914 4.642 3.905 4.015 4.26 C 3.388 4.615 3 5.279 3 6 L 3 18 C 3 18.721 3.388 19.385 4.015 19.74 C 4.642 20.095 5.411 20.086 6.029 19.715 L 16.026 13.717 C 16.63 13.356 17 12.705 17 12.002 C 17.001 11.299 16.632 10.647 16.029 10.285 Z" }, // skip-forward
    };
    // END GENERATED

    private static final HashMap<String, String> DATA = new HashMap<String, String>();
    private static final HashMap<String, String> FILLED_DATA = new HashMap<String, String>();

    /** 主表中按实心渲染的图标（原 assets 就是实心图）。 */
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

    private LucideIcons() {
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
        return bitmap(name, sizePx, color, 0f);
    }

    /**
     * {@code padding} in grid units on each side, shrinking the glyph inside the same box. The
     * stroke keeps its pixel width. Lucide geometry spans 2..22 and its stroke reaches 1..23, so
     * the default 20-unit safe area cuts round edges by up to one unit; padding 1 shows them whole.
     */
    public static Bitmap bitmap(String name, int sizePx, int color, float padding) {
        int size = sizePx <= 0 ? 1 : sizePx;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Path p = path(name);
            if (p == null) {
                return bmp;
            }
            float safe = SAFE + 2f * Math.max(0f, padding);
            float scale = size / safe;
            Canvas c = new Canvas(bmp);
            // 把 24 栅格居中放进方框，安全区正好铺满。
            float offset = (size - GRID * scale) / 2f;
            c.translate(offset, offset);
            c.scale(scale, scale);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (isFilled(name)) {
                // Lucide 没有实心套：先填充闭合轮廓，再用同色描边补出线段和圆角外缘。
                paint.setStyle(Paint.Style.FILL);
                c.drawPath(p, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            // 小尺寸下描边不足 1 像素会断线，按像素下限兜底。
            // 按 safe/SAFE 放大栅格线宽，加内边距后像素线宽不变。
            paint.setStrokeWidth(Math.max(STROKE_SAFE * safe / SAFE, 1f / scale));
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

    // ------------------------------------------------------------ 与原生图标对齐

    /** 图标墨迹在本库渲染结果中的位置（left, top, right, bottom，占边长比例），按名缓存。 */
    private static final HashMap<String, float[]> INK = new HashMap<String, float[]>();
    /** 原生图标墨迹测量结果，按 drawable 状态缓存；值为 {key, RectF}。 */
    private static final WeakHashMap<Object, Object[]> NATIVE_INK = new WeakHashMap<Object, Object[]>();
    /** 判定为墨迹的最低透明度。 */
    private static final int INK_ALPHA = 40;
    private static final int INK_REFERENCE = 120;
    private static final int INK_MAX_SAMPLE = 192;

    /**
     * 视觉对齐：让 Lucide 图标的实际墨迹与旁边原生图标的墨迹一样大、中心重合。
     *
     * 原生菜单图标通常在 ImageView 里留有边距，墨迹只占框的一部分；直接把 Lucide 铺满同尺寸的框会显得偏大偏重。
     * 这里分别量出原生 drawable 实际绘制出来的不透明范围、以及本库该图标的墨迹范围，按长边等比缩放。
     *
     * @return {sizePx, padLeft, padTop, padRight, padBottom}：在 boxWidth × boxHeight 的 ImageView（FIT_CENTER）里
     *         用这组内边距放 sizePx 的位图即可；量不到时返回 null，调用方保持原有铺满方式。
     */
    public static int[] matchNative(ImageView nativeIcon, String name, int boxWidth, int boxHeight) {
        try {
            if (nativeIcon == null || boxWidth <= 0 || boxHeight <= 0 || !has(name)) return null;
            RectF target = nativeInk(nativeIcon);
            float[] own = ink(name);
            if (target == null || own == null) return null;
            float ownMajor = Math.max(own[2] - own[0], own[3] - own[1]);
            float targetMajor = Math.max(target.width(), target.height());
            if (ownMajor <= 0f || targetMajor < 2f) return null;
            int size = Math.round(targetMajor / ownMajor);
            if (size <= 0 || size > boxWidth || size > boxHeight) return null;
            float left = target.centerX() - (own[0] + own[2]) / 2f * size;
            float top = target.centerY() - (own[1] + own[3]) / 2f * size;
            int padLeft = Math.max(0, Math.min(boxWidth - size, Math.round(left)));
            int padTop = Math.max(0, Math.min(boxHeight - size, Math.round(top)));
            return new int[]{size, padLeft, padTop, boxWidth - padLeft - size, boxHeight - padTop - size};
        } catch (Throwable t) {
            log("原生图标对齐失败 " + t.getClass().getSimpleName());
            return null;
        }
    }

    /** 本库图标的墨迹范围（比例），以参考尺寸渲染一次后缓存。 */
    private static float[] ink(String name) {
        float[] cached = INK.get(name);
        if (cached != null) return cached;
        Bitmap bitmap = bitmap(name, INK_REFERENCE, 0xFF000000);
        Rect bounds = alphaBounds(bitmap);
        bitmap.recycle();
        if (bounds == null) return null;
        float size = INK_REFERENCE;
        float[] result = {bounds.left / size, bounds.top / size, bounds.right / size, bounds.bottom / size};
        INK.put(name, result);
        return result;
    }

    /** 原生图标实际画出的不透明范围，换算到该 ImageView 自身坐标。 */
    private static RectF nativeInk(ImageView icon) {
        Drawable drawable = icon.getDrawable();
        if (drawable == null || icon.getWidth() <= 0 || icon.getHeight() <= 0) return null;
        Rect bounds = drawable.getBounds();
        int width = bounds.width();
        int height = bounds.height();
        if (width <= 0 || height <= 0) return null;
        Matrix matrix = icon.getImageMatrix();
        String key = width + "x" + height + "@" + icon.getPaddingLeft() + "," + icon.getPaddingTop()
                + (matrix == null ? "" : matrix.toShortString());
        Object state = drawable.getConstantState();
        Object owner = state != null ? state : drawable;
        Object[] cached = NATIVE_INK.get(owner);
        if (cached != null && key.equals(cached[0])) return new RectF((RectF) cached[1]);

        // 按 drawable 当前 bounds 画一遍（不改其状态），大图降采样以控制开销。
        float sample = Math.min(1f, INK_MAX_SAMPLE / (float) Math.max(width, height));
        int sampleWidth = Math.max(1, (int) Math.ceil(width * sample));
        int sampleHeight = Math.max(1, (int) Math.ceil(height * sample));
        Bitmap bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
        Rect ink;
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(sample, sample);
            canvas.translate(-bounds.left, -bounds.top);
            drawable.draw(canvas);
            ink = alphaBounds(bitmap);
        } finally {
            bitmap.recycle();
        }
        if (ink == null) return null;
        RectF result = new RectF(bounds.left + ink.left / sample, bounds.top + ink.top / sample,
                bounds.left + ink.right / sample, bounds.top + ink.bottom / sample);
        if (matrix != null) matrix.mapRect(result);
        result.offset(icon.getPaddingLeft(), icon.getPaddingTop());
        NATIVE_INK.put(owner, new Object[]{key, new RectF(result)});
        return result;
    }

    /** 透明度超过阈值的像素外接矩形（right/bottom 为开区间）；全透明返回 null。 */
    private static Rect alphaBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if ((pixels[row + x] >>> 24) <= INK_ALPHA) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        return right < 0 ? null : new Rect(left, top, right + 1, bottom + 1);
    }

    /** Drawable，按 dp + 该图标的颜色。 */
    public static Drawable drawableDp(String name, float sizeDp) {
        return drawable(name, toPx(sizeDp), colorOf(name));
    }

    /** Drawable，按 dp + 指定颜色。 */
    public static Drawable drawableDp(String name, float sizeDp, int color) {
        return drawable(name, toPx(sizeDp), color);
    }

    /** Drawable，按 dp + 指定颜色 + 四周内边距（栅格单位，见 {@link #bitmap(String, int, int, float)}）。 */
    public static Drawable drawableDp(String name, float sizeDp, int color, float padding) {
        return new BitmapDrawable(null, bitmap(name, toPx(sizeDp), color, padding));
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
            dev.mindlink.xposed.XposedBridge.log(TAG + msg);
        } catch (Throwable ignore) {
        }
    }
}
