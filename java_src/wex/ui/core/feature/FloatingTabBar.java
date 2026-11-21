package wex.ui.core.feature;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/**
 * 悬浮底栏增强：真悬浮、滚动自动隐藏、圆角描边与轻量毛玻璃底。
 *
 * 来源：交互与视觉参照 WeKit 的
 * `features/items/beautify/ReplaceNavigationBar.kt`（GPL-3.0）：
 *  - 悬浮模式下底栏脱离正常布局流、浮在内容之上（对应其 `attachBar(floating)`）；
 *  - 滚动时自动收起、停止滚动后恢复（对应 `nav_bar_auto_hide_on_scroll`）；
 *  - 圆角胶囊 + 半透明表面 + 细描边。
 *
 * 与 WeKit 的差异：WeKit 整条底栏由 Compose 重绘并接管标签页索引重映射；
 * 本项目保留既有的胶囊栏（已支持换图标/颜色/文字），只在其外层做悬浮与显隐，
 * 因此不触碰宿主 tab 索引，也不会影响页面切换逻辑。
 */
public final class FloatingTabBar {

    private static final String TAG = "WEX: 悬浮底栏: ";
    private static final String KEY_ENABLED = "bottom_bar_enabled";
    private static final String KEY_FLOATING = "bottom_bar_floating";
    private static final String KEY_AUTO_HIDE = "bottom_bar_auto_hide";
    private static final String KEY_BLUR = "bottom_bar_blur";

    private static final int TAG_OVERLAY = 0x7E000201;
    private static final int SCROLL_THRESHOLD_PX = 12;
    private static final long HIDE_DELAY_MS = 220L;

    private FloatingTabBar() {
    }

    public static boolean enabled() {
        return flag(KEY_ENABLED, true);
    }

    public static boolean floating() {
        return flag(KEY_FLOATING, true);
    }

    public static boolean autoHide() {
        return flag(KEY_AUTO_HIDE, true);
    }

    public static boolean blur() {
        return flag(KEY_BLUR, true);
    }

    private static boolean flag(String key, boolean def) {
        try {
            return WexStore.INSTANCE.getBool(key, def);
        } catch (Throwable ignored) {
            return def;
        }
    }

    /**
     * 把已构建好的胶囊栏转为悬浮层。
     *
     * 调用点：`BottomBarComposeFeature.apply` 在把胶囊栏 addView 到页面容器之前。
     * 这里不改变胶囊栏自身的测量与点击逻辑，只调整它的布局参数与外观。
     *
     * @param host 胶囊栏所在的页面容器
     * @param bar  胶囊栏
     */
    public static void attach(ViewGroup host, View bar) {
        if (host == null || bar == null || !enabled()) {
            return;
        }
        try {
            if (floating()) {
                makeFloating(host, bar);
            }
            if (blur()) {
                applySurface(host.getContext(), bar);
            }
            if (autoHide()) {
                installAutoHide(host, bar);
            }
            log("已应用 悬浮=" + floating() + " 自动隐藏=" + autoHide() + " 表面=" + blur());
        } catch (Throwable error) {
            log("应用失败 " + error.getClass().getSimpleName());
        }
    }

    /** 悬浮：底部对齐、抬高 z 序、外层不裁剪，让胶囊浮在内容之上。 */
    private static void makeFloating(ViewGroup host, View bar) {
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
            frame.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            bar.setLayoutParams(frame);
        }
        bar.setElevation(dp(host.getContext(), 8));
        bar.setTranslationZ(dp(host.getContext(), 8));
        // 逐级解除裁剪，否则圆角外的阴影与探出的胶囊会被父容器切掉。
        ViewGroup current = host;
        for (int depth = 0; current != null && depth < 6; depth++) {
            current.setClipChildren(false);
            current.setClipToPadding(false);
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof ViewGroup ? (ViewGroup) parent : null;
        }
    }

    /** 圆角胶囊表面：半透明底 + 细描边，深浅色分别取色。 */
    private static void applySurface(Context context, View bar) {
        boolean dark = (context.getResources().getConfiguration().uiMode & 0x30) == 0x20;
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable surface = new GradientDrawable();
        surface.setShape(GradientDrawable.RECTANGLE);
        surface.setCornerRadius(26f * density);
        surface.setColor(dark ? 0xE61C1F1D : 0xE6FFFFFF);
        surface.setStroke(Math.max(1, Math.round(density)), dark ? 0x33FFFFFF : 0x14000000);
        Drawable original = bar.getBackground();
        if (original instanceof GradientDrawable) {
            // 保留原实现设置的圆角与配色语义，只在外面叠加描边。
            ((GradientDrawable) original).setStroke(Math.max(1, Math.round(density)),
                    dark ? 0x33FFFFFF : 0x14000000);
        } else if (original == null) {
            bar.setBackground(surface);
        }
        bar.setClipToOutline(true);
        bar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = 26f * view.getResources().getDisplayMetrics().density;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    /** 滚动自动隐藏：会话列表下滑时收起胶囊，停止后恢复。 */
    private static void installAutoHide(final ViewGroup host, final View bar) {
        if (bar.getTag(TAG_OVERLAY) != null) {
            return;
        }
        bar.setTag(TAG_OVERLAY, Boolean.TRUE);
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private View list;
            private final int[] last = new int[2];

            @Override
            public void onViewAttachedToWindow(View view) {
                list = findScrollable(host, 0);
                if (list == null) {
                    return;
                }
                list.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    @Override
                    public void onScrollChange(View target, int x, int y, int oldX, int oldY) {
                        int delta = y - last[1];
                        last[1] = y;
                        if (Math.abs(delta) < SCROLL_THRESHOLD_PX) {
                            return;
                        }
                        if (delta > 0) {
                            hide(bar);
                        } else {
                            show(bar);
                        }
                    }
                });
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if (list != null) {
                    list.setOnScrollChangeListener((View.OnScrollChangeListener) null);
                    list = null;
                }
            }
        });
    }

    private static void hide(View bar) {
        bar.animate().cancel();
        bar.animate().translationY(bar.getHeight() + dp(bar.getContext(), 24))
                .setDuration(HIDE_DELAY_MS).start();
    }

    private static void show(View bar) {
        bar.animate().cancel();
        bar.animate().translationY(0f).setDuration(HIDE_DELAY_MS).start();
    }

    private static View findScrollable(View view, int depth) {
        if (view == null || depth > 8) {
            return null;
        }
        if (view instanceof android.widget.AbsListView
                || view.getClass().getName().contains("RecyclerView")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findScrollable(group.getChildAt(i), depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
