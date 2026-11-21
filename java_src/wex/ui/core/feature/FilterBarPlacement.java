package wex.ui.core.feature;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ListView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;

/**
 * 筛选栏固定在会话页内，为原滚动分支预留高度，不覆盖列表。
 *
 * 来源：固定占位与「列表 header 分离」的做法参照 WeKit 的
 * `features/items/chat/ConversationGroupTabsHost.kt`（GPL-3.0）。
 */
public final class FilterBarPlacement {
    private static final String PAGE_CLASS = "com.tencent.mm.ui.FrostedContentView";
    private static final String LIST_CLASS = "com.tencent.mm.ui.conversation.ConversationListView";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ListView, WeakReference<Mount>> MOUNTS =
            new WeakHashMap<ListView, WeakReference<Mount>>();
    private static final WeakHashMap<Activity, Resume> RESUMES = new WeakHashMap<Activity, Resume>();
    private static WeakReference<Mount> active = new WeakReference<Mount>(null);
    private static Method readyMethod;
    private static Method releaseMethod;

    private FilterBarPlacement() {
    }

    public static boolean isHome(Activity activity) {
        return activity != null && "com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName())
                && !activity.isFinishing() && !activity.isDestroyed() && activity.getWindow() != null;
    }

    public static void onHomeResume(Activity activity) {
        if (!isHome(activity)) {
            return;
        }
        Resume previous = RESUMES.remove(activity);
        if (previous != null) {
            MAIN.removeCallbacks(previous);
        }
        Resume task = new Resume(activity);
        RESUMES.put(activity, task);
        MAIN.postDelayed(task, 150L);
    }

    private static final class Resume implements Runnable {
        final WeakReference<Activity> activity;
        int attempts;

        Resume(Activity activity) {
            this.activity = new WeakReference<Activity>(activity);
        }

        @Override
        public void run() {
            Activity target = activity.get();
            if (!isHome(target) || RESUMES.get(target) != this) {
                return;
            }
            ListView list = findList(target.getWindow().getDecorView());
            if (!target.getWindow().getDecorView().isAttachedToWindow()
                    || list == null || !list.isAttachedToWindow()) {
                if (++attempts < ChatToolbarLifecycle.SLOW_RETRY_ATTEMPTS) {
                    MAIN.postDelayed(this, 150L);
                } else {
                    RESUMES.remove(target);
                    log("等待会话页列表超时");
                }
                return;
            }
            RESUMES.remove(target);
            try {
                if (readyMethod == null) {
                    readyMethod = feature().getMethod("onHomeReady", Activity.class);
                }
                readyMethod.invoke(null, target);
            } catch (Throwable error) {
                log("筛选回调失败 " + error.getClass().getSimpleName());
            }
        }
    }

    private static Class<?> feature() throws ClassNotFoundException {
        return Class.forName("wex.ui.core.feature.ConversationFilterFeature", false,
                FilterBarPlacement.class.getClassLoader());
    }

    private static ListView findList(View root) {
        ArrayDeque<View> queue = new ArrayDeque<View>();
        queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 512; visited++) {
            View view = queue.removeFirst();
            if (view instanceof ListView && LIST_CLASS.equals(view.getClass().getName())) {
                return (ListView) view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 512; i++) {
                    queue.addLast(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    private static Mount current(ListView list) {
        WeakReference<Mount> ref = MOUNTS.get(list);
        return ref == null ? null : ref.get();
    }

    public static boolean isMounted(ListView list) {
        Mount state = current(list);
        return state != null && !state.disposed && state.isMounted();
    }

    public static boolean mount(ListView list, View bar) {
        if (Looper.myLooper() != Looper.getMainLooper() || list == null || bar == null
                || !LIST_CLASS.equals(list.getClass().getName())) {
            return false;
        }
        Mount state = current(list);
        if (state == null || state.disposed) {
            Mount previous = active.get();
            if (previous != null && previous.list.get() != list) {
                previous.dispose();
            }
            state = new Mount(list, bar);
            MOUNTS.put(list, new WeakReference<Mount>(state));
            active = new WeakReference<Mount>(state);
            list.addOnAttachStateChangeListener(state);
            list.addOnLayoutChangeListener(state);
        }
        if (state.isMounted()) {
            return true;
        }
        state.lifecycle.refresh();
        return false;
    }

    /** 保留旧公开入口，但不再通过首页卡片或 header 调整顺序。 */
    public static void moveAboveCards(ListView list, View bar) {
        mount(list, bar);
    }

    public static void unmount(ListView list) {
        Mount state = current(list);
        if (state != null) {
            state.dispose();
        }
    }

    private static final class Mount implements ChatToolbarLifecycle.Target, FilterBarMount.Target,
            View.OnAttachStateChangeListener, View.OnLayoutChangeListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<ListView> list;
        final View bar;
        final ChatToolbarLifecycle lifecycle;
        final FilterBarMount transaction;
        WeakReference<FrameLayout> page = new WeakReference<FrameLayout>(null);
        WeakReference<View> content = new WeakReference<View>(null);
        FrameLayout.LayoutParams original;
        FrameLayout.LayoutParams reserved;
        ViewTreeObserver observer;
        int index;
        int barHeight;
        boolean changing;
        boolean disposed;
        boolean deferring;
        boolean warned;
        String reason = "未识别到安全的会话页固定槽位";
        final Runnable restoreLater = new Runnable() {
            @Override
            public void run() {
                ListView target = list.get();
                if (target == null || !target.isAttachedToWindow()) {
                    transaction.unmount();
                    release(target);
                }
            }
        };

        Mount(ListView list, View bar) {
            this.list = new WeakReference<ListView>(list);
            this.bar = bar;
            lifecycle = new ChatToolbarLifecycle(this);
            transaction = new FilterBarMount(this);
        }

        @Override
        public boolean isEnabled() {
            // 此入口仅由本进程已启用的筛选 Hook 调用，不让未重启的设置立即改变挂载。
            return !disposed;
        }

        @Override
        public boolean isAttached() {
            ListView target = list.get();
            return target != null && target.isAttachedToWindow()
                    && isHome(ChatToolbarLifecycle.findActivity(target.getContext()));
        }

        @Override
        public boolean mount() {
            if (original != null && !isMounted()) {
                transaction.unmount();
            }
            boolean mounted = transaction.mount();
            if (mounted) {
                watchPage();
            }
            return mounted;
        }

        private void watchPage() {
            FrameLayout host = page.get();
            if (host == null) {
                return;
            }
            ViewTreeObserver current = host.getViewTreeObserver();
            if (observer != current) {
                if (observer != null && observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }
                observer = current;
                observer.addOnPreDrawListener(this);
            }
        }

        @Override
        public void unmount() {
            if (deferring) {
                MAIN.removeCallbacks(restoreLater);
                MAIN.post(restoreLater);
            } else {
                transaction.unmount();
            }
        }

        @Override
        public boolean post(Runnable task, long delay) {
            return MAIN.postDelayed(task, delay);
        }

        @Override
        public void cancel(Runnable task) {
            MAIN.removeCallbacks(task);
        }

        @Override
        public void onExhausted() {
            if (!warned) {
                warned = true;
                log(reason + "，保持原界面");
            }
        }

        @Override
        public boolean isMounted() {
            FrameLayout host = page.get();
            View branch = content.get();
            return host != null && branch != null && bar.getParent() == host && branch.getParent() == host
                    && branch.getLayoutParams() == reserved && reserved != null;
        }

        @Override
        public boolean prepare() {
            ListView target = list.get();
            if (!isAttached() || target == null) {
                return false;
            }
            View child = target;
            for (int depth = 0; child.getParent() instanceof View && depth < 24; depth++) {
                View parent = (View) child.getParent();
                if (PAGE_CLASS.equals(parent.getClass().getName())) {
                    if (!(parent instanceof FrameLayout) || !(parent.getParent() instanceof ViewGroup)) {
                        reason = "会话页不是已支持的固定容器";
                        return false;
                    }
                    FrameLayout host = (FrameLayout) parent;
                    String pager = host.getParent().getClass().getName();
                    if (!("com.tencent.mm.ui.base.CustomViewPager".equals(pager)
                            || "com.tencent.mm.ui.CustomViewPager".equals(pager))
                            || host.getChildCount() != 1 || host.getChildAt(0) != child
                            || !(child instanceof ViewGroup) || child instanceof ListView
                            || !(child.getLayoutParams() instanceof FrameLayout.LayoutParams)
                            || host.getScrollY() != 0 || host.getTranslationY() != 0f) {
                        reason = "会话页固定边界未确认";
                        return false;
                    }
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
                    if (params.width != ViewGroup.LayoutParams.MATCH_PARENT
                            || params.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                        reason = "会话内容不是完整占位分支";
                        return false;
                    }
                    int width = host.getWidth() - host.getPaddingLeft() - host.getPaddingRight()
                            - params.leftMargin - params.rightMargin;
                    int height = host.getHeight() - host.getPaddingTop() - host.getPaddingBottom()
                            - params.topMargin - params.bottomMargin;
                    if (width <= 0 || height <= 0) {
                        return false;
                    }
                    if (bar.getParent() != null) {
                        // 仅通过 ListView 的公开 header API 解除注册，不直接移除其子视图。
                        target.removeHeaderView(bar);
                        if (bar.getParent() != null) {
                            return false;
                        }
                    }
                    FilterBarStyle.matchBackground(bar, host);
                    bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
                    barHeight = bar.getMeasuredHeight();
                    if (barHeight <= 0 || barHeight >= height) {
                        return false;
                    }
                    page = new WeakReference<FrameLayout>(host);
                    content = new WeakReference<View>(child);
                    original = params;
                    index = host.indexOfChild(child);
                    return true;
                }
                child = parent;
            }
            return false;
        }

        @Override
        public void install() {
            FrameLayout host = page.get();
            View branch = content.get();
            if (host == null || branch == null || branch.getParent() != host
                    || host.indexOfChild(branch) != index) {
                throw new IllegalStateException("Conversation slot changed");
            }
            changing = true;
            try {
                FrameLayout.LayoutParams row = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
                row.leftMargin = original.leftMargin;
                row.rightMargin = original.rightMargin;
                row.topMargin = original.topMargin;
                row.setMarginStart(original.getMarginStart());
                row.setMarginEnd(original.getMarginEnd());
                bar.setVisibility(branch.getVisibility());
                host.addView(bar, index + 1, row);
                reserve(branch, barHeight);
                bar.addOnLayoutChangeListener(this);
                watchPage();
                warned = false;
                log("筛选栏已固定到会话页，不占列表 header");
            } finally {
                changing = false;
            }
        }

        private void reserve(View branch, int height) {
            reserved = new FrameLayout.LayoutParams(original);
            reserved.topMargin = original.topMargin + height;
            branch.setLayoutParams(reserved);
            barHeight = height;
        }

        @Override
        public void restore() {
            changing = true;
            try {
                if (observer != null && observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }
                observer = null;
                bar.removeOnLayoutChangeListener(this);
                FrameLayout host = page.get();
                View branch = content.get();
                if (host != null && bar.getParent() == host) {
                    host.removeView(bar);
                }
                if (branch != null && branch.getParent() == host && original != null
                        && branch.getLayoutParams() == reserved) {
                    branch.setLayoutParams(original);
                }
            } finally {
                original = null;
                reserved = null;
                page.clear();
                content.clear();
                changing = false;
            }
        }

        @Override
        public void failed(Throwable error) {
            reason = "固定筛选栏失败 " + error.getClass().getSimpleName();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            if (!changing && !disposed) {
                MAIN.removeCallbacks(restoreLater);
                lifecycle.refresh();
                onHomeResume(ChatToolbarLifecycle.findActivity(view.getContext()));
            }
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (!changing) {
                deferring = true;
                lifecycle.detach();
                deferring = false;
            }
        }

        @Override
        public void onLayoutChange(View view, int l, int t, int r, int b,
                                   int oldL, int oldT, int oldR, int oldB) {
            if (changing || disposed) {
                return;
            }
            if (view == bar && isMounted() && b - t > 0 && b - t != barHeight) {
                View branch = content.get();
                if (branch != null) {
                    reserve(branch, b - t);
                }
            } else if (view != bar && !isMounted() && (r - l != oldR - oldL || b - t != oldB - oldT)) {
                lifecycle.refresh();
            }
        }

        @Override
        public boolean onPreDraw() {
            if (changing || disposed) {
                return true;
            }
            View branch = content.get();
            if (branch != null && isMounted()) {
                // 页面切换的可见性跟随会话内容分支，不跟随可能为空而隐藏的 ListView。
                if (bar.getVisibility() != branch.getVisibility()) {
                    bar.setVisibility(branch.getVisibility());
                }
            } else {
                unmount();
                lifecycle.refresh();
            }
            return true;
        }

        void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            MAIN.removeCallbacks(restoreLater);
            ListView target = list.get();
            if (target != null) {
                target.removeOnAttachStateChangeListener(this);
                target.removeOnLayoutChangeListener(this);
                MOUNTS.remove(target);
            }
            lifecycle.detach();
            release(target);
            if (active.get() == this) {
                active.clear();
            }
        }
    }

    private static void release(ListView list) {
        if (list == null) {
            return;
        }
        try {
            if (releaseMethod == null) {
                releaseMethod = feature().getMethod("releaseList", ListView.class);
            }
            releaseMethod.invoke(null, list);
        } catch (Throwable error) {
            log("清理筛选引用失败 " + error.getClass().getSimpleName());
        }
    }

    /** 兼容旧调用；只有主动点击分类的路径才使用归顶操作。 */
    public static void scrollToTop(Object adapter) {
        try {
            if (adapter == null) {
                return;
            }
            for (java.lang.reflect.Field field : adapter.getClass().getDeclaredFields()) {
                if (ListView.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object view = field.get(adapter);
                    if (view instanceof ListView) {
                        ((ListView) view).setSelection(0);
                        return;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log("WEX: 会话筛选: " + message);
        } catch (Throwable ignore) {
        }
    }
}
