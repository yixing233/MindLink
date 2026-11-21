package wex.ui.core.feature;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/**
 * 所有气泡模式共用调度、重入保护和恢复逻辑；模糊仅作用于背景。
 *
 * 来源：挂载点（`MMNeat7extView` / `AnimImageView`）、九宫格 `npTc` 序列化与
 * HSV 压暗 0.8 生成按压态的做法参照 WeKit 的
 * `features/items/beautify/CustomMessageBubbles.kt`（GPL-3.0）；
 * 本文件改用 Java 实现，并新增毛玻璃取样与异步模糊调度。
 */
public final class BubbleApply {

    private static final String BUBBLE_CLASS = "com.tencent.mm.ui.widget.MMNeat7extView";
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<View, State>();
    private static final ThreadLocal<Boolean> APPLYING = new ThreadLocal<Boolean>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /**
     * 模糊工作线程池。
     *
     * 单线程在滚动时会被「取消-重排」反复打断，有效吞吐很低；
     * 改用 2 个线程：一个跑当前帧，另一个可接替即将到来的下一帧。
     * 核心线程可超时回收，静止后不常驻。
     */
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(2, 2, 10L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(16), new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "MindLink-bubble-background");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            }, new java.util.concurrent.RejectedExecutionHandler() {
                @Override public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
                    Runnable dropped=executor.getQueue().poll();
                    if(dropped instanceof BubbleWork)((BubbleWork)dropped).dropped();
                    if(!executor.isShutdown())executor.execute(task);
                    else if(task instanceof BubbleWork)((BubbleWork)task).dropped();
                }
            });
    private static final class BubbleWork implements Runnable {
        final WeakReference<State> state; final Runnable delegate;
        BubbleWork(State state,Runnable delegate){this.state=new WeakReference<>(state);this.delegate=delegate;}
        @Override public void run(){delegate.run();}
        void dropped(){State current=state.get();if(current!=null)MAIN.post(current::retryDropped);}
    }

    private static boolean fallbackLogged;

    /** 位置停止变化多久后认为「已静止」，此后才做背景取样校验。 */
    private static final long IDLE_AFTER_MS = 300L;

    static {
        WORK.allowCoreThreadTimeOut(true);
    }

    private BubbleApply() {
    }

    /** View.setBackground 的 after 回调，只登记原有消息气泡。 */
    public static void onBackground(Object object) {
        if (APPLYING.get() != null || !(object instanceof View)
                || !BUBBLE_CLASS.equals(object.getClass().getName())) {
            return;
        }
        final View view = (View) object;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final WeakReference<View> ref = new WeakReference<View>(view);
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    View target = ref.get();
                    if (target != null) {
                        onBackground(target);
                    }
                }
            });
            return;
        }
        try {
            State state = STATES.get(view);
            Drawable background = view.getBackground();
            if (state == null) {
                if (!(background instanceof StateListDrawable)) {
                    return;
                }
                state = new State(view);
                STATES.put(view, state);
                view.addOnAttachStateChangeListener(state);
            }
            if (background != state.applied) {
                state.original = background;
            }
            state.changed();
        } catch (Throwable t) {
            log("气泡登记失败 " + t.getClass().getSimpleName());
        }
    }

    /** 兼容原 Smali Runnable；三种模式都在 Java 内处理。 */
    public static boolean apply(View view) {
        onBackground(view);
        return true;
    }

    public static void refresh() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (State state : new ArrayList<State>(STATES.values())) {
                    state.changed();
                }
                fallbackLogged = false;
                if (!BubbleSettings.enabled()) {
                    try {
                        WORK.execute(new Runnable() {
                            @Override
                            public void run() {
                                BubbleGlass.clearCache();
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException ignore) {
                    }
                }
            }
        });
    }

    private static final class State implements Runnable, View.OnAttachStateChangeListener,
            ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> ref;
        final BubbleGeneration generation = new BubbleGeneration();
        Drawable original;
        Drawable applied;
        BubbleSourceStamp sourceStamp;
        ViewTreeObserver observer;
        Future<?> work;
        long token;
        long lastSourceCheck;
        /** 最近一次位置/尺寸变化的时间，用于判断滚动是否已停止。 */
        long lastActivity;
        /** 异步结果回来时若位置已过期，置脏由下一帧 onPreDraw 立刻接手。 */
        boolean dirty;
        int mode;
        int width;
        int height;
        int x;
        int y;
        int attempts;
        boolean queued;
        boolean sourceCheckQueued;
        final int[] location = new int[2];
        int paddingLeft, paddingTop, paddingRight, paddingBottom;
        int appliedPaddingLeft, appliedPaddingTop, appliedPaddingRight, appliedPaddingBottom;
        boolean ownsPadding;
        final Runnable inspectSource = new Runnable() {
            @Override
            public void run() {
                sourceCheckQueued = false;
                View view = ref.get();
                if (view == null || !BubbleSettings.enabled() || !view.isAttachedToWindow()) {
                    stop(true);
                    return;
                }
                if (queued || mode != 0 || BubbleSettings.mode() != 0) {
                    return;
                }
                lastSourceCheck = SystemClock.uptimeMillis();
                if (!BubbleGlass.isCurrent(view, sourceStamp)) {
                    attempts = 0;
                    queue(0L);
                }
            }
        };

        State(View view) {
            ref = new WeakReference<View>(view);
            original = view.getBackground();
        }

        void changed() {
            View view = ref.get();
            if (view == null) {
                stop(false);
                return;
            }
            if (!BubbleSettings.enabled() || !view.isAttachedToWindow()) {
                stop(true);
                return;
            }
            if (observer == null || !observer.isAlive()) {
                observer = view.getViewTreeObserver();
                observer.addOnPreDrawListener(this);
            }
            attempts = 0;
            queue(0L);
        }

        void retryDropped(){View view=ref.get();work=null;if(view!=null&&view.isAttachedToWindow()&&BubbleSettings.enabled()){dirty=true;if(!queued)queue(16L);}}

        void queue(long delay) {
            View view = ref.get();
            if (view == null) {
                return;
            }
            if (work != null) {
                // 只取消、不 purge：purge 会清掉其它气泡已排队的任务，滚动时造成饥饿。
                work.cancel(true);
                work = null;
            }
            MAIN.removeCallbacks(this);
            MAIN.removeCallbacks(inspectSource);
            sourceCheckQueued = false;
            mode = BubbleSettings.mode();
            token = generation.next(BubbleSettings.enabled(), mode);
            queued = true;
            dirty = false;
            MAIN.postDelayed(this, delay);
        }

        void stop(boolean restore) {
            generation.cancel();
            MAIN.removeCallbacks(this);
            MAIN.removeCallbacks(inspectSource);
            sourceCheckQueued = false;
            sourceStamp = null;
            queued = false;
            if (work != null) {
                work.cancel(true);
                work = null;
            }
            if (observer != null && observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
            observer = null;
            View view = ref.get();
            if (restore && view != null) {
                if (applied != null && view.getBackground() == applied) {
                    try {
                        setBackground(view, original);
                        restorePadding(view);
                    } catch (Throwable t) {
                        log("恢复气泡背景失败 " + t.getClass().getSimpleName());
                    }
                }
            }
            if (view != null && (applied == null || view.getBackground() != applied)) {
                // Host replacement owns its current padding; never restore stale values over it.
                ownsPadding = false;
            }
            applied = null;
        }

        void restorePadding(View view) {
            checkPaddingOwnership(view);
            if (ownsPadding) {
                view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
                ownsPadding = false;
            }
        }

        void checkPaddingOwnership(View view) {
            if (ownsPadding && (view.getPaddingLeft() != appliedPaddingLeft || view.getPaddingTop() != appliedPaddingTop
                    || view.getPaddingRight() != appliedPaddingRight || view.getPaddingBottom() != appliedPaddingBottom)) {
                ownsPadding = false;
            }
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            changed();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            stop(true);
        }

        @Override
        public boolean onPreDraw() {
            View view = ref.get();
            if (view == null || !BubbleSettings.enabled() || !view.isAttachedToWindow()) {
                stop(true);
                return true;
            }
            view.getLocationInWindow(location);
            boolean moved = x != location[0] || y != location[1];
            boolean resized = width != view.getWidth() || height != view.getHeight();
            long now = SystemClock.uptimeMillis();
            if (resized || (mode == 0 && moved) || dirty) {
                // 位置/尺寸变化，或异步结果因过期被置脏：立刻重排。
                // 同一帧内多次 onPreDraw 由 queued 合并，不会重复提交。
                if (!queued) {
                    attempts = 0;
                    queue(0L);
                }
                lastActivity = now;
            } else if (now - lastActivity < IDLE_AFTER_MS) {
                // 刚发生过滚动：保持活跃，不安排背景校验，避免和重算抢主线程。
            } else if (!queued && mode == 0 && !sourceCheckQueued) {
                // 静止后才校验背景是否变化（滚动中背景不会变，无需检查）。
                sourceCheckQueued = true;
                MAIN.postDelayed(inspectSource, 0L);
            }
            return true;
        }

        @Override
        public void run() {
            queued = false;
            final View view = ref.get();
            if (view == null || !BubbleSettings.enabled() || !view.isAttachedToWindow()) {
                stop(true);
                return;
            }
            width = view.getWidth();
            height = view.getHeight();
            view.getLocationInWindow(location);
            x = location[0];
            y = location[1];
            if (width <= 0 || height <= 0) {
                if (++attempts <= 8) {
                    // 尺寸还没算出来：下一帧再来，不用固定 120ms。
                    queued = true;
                    MAIN.postDelayed(this, 16L);
                }
                return;
            }
            final long expected = token;
            final int expectedMode = mode;
            final float density = view.getResources().getDisplayMetrics().density;
            final float corner = BubbleSettings.cornerDp() * density;
            final boolean dark = (view.getResources().getConfiguration().uiMode & 0x30) == 0x20;
            final int tint = BubbleSettings.tintAlpha();
            sourceStamp = mode == 0 ? BubbleGlass.sourceStamp(view) : null;
            lastSourceCheck = SystemClock.uptimeMillis();
            try {
                if (mode == 2) {
                    GradientDrawable transparent = new GradientDrawable();
                    transparent.setColor(Color.TRANSPARENT);
                    transparent.setCornerRadius(corner);
                    transparent.setStroke(Math.max(1, Math.round(1.2f * density)), 0x66FFFFFF);
                    deliver(expected, expectedMode, transparent);
                    return;
                }
                if (mode == 1) {
                    String key = isSelfBubble(view) ? "bubble_bg_path_self" : "bubble_bg_path_peer";
                    String path = WexStore.INSTANCE.getString(key, "");
                    if (path.length() == 0) {
                        path = WexStore.INSTANCE.getString("bubble_bg_path", "");
                    }
                    if (path.length() > 0) {
                        final String file = path;
                        final int targetWidth = width;
                        final int targetHeight = height;
                        final int alpha = BubbleSettings.imageAlpha();
                        final android.content.res.Resources resources = view.getResources();
                        work = WORK.submit(new BubbleWork(this, new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final Drawable image = imageDrawable(resources, file, targetWidth,
                                            targetHeight, corner, alpha);
                                    MAIN.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            deliver(expected, expectedMode, image);
                                        }
                                    });
                                } catch (Throwable t) {
                                    failure(expected, expectedMode, corner, tint, dark, density);
                                }
                            }
                        }));
                        return;
                    }
                    deliver(expected, expectedMode, BubbleGlass.drawable(null, null, corner, tint, dark, density));
                    return;
                }
                final BubbleGlass.Sample sample = BubbleGlass.capture(view, BubbleSettings.blurDp());
                if (sample == null) {
                    if (!fallbackLogged) {
                        fallbackLogged = true;
                        log("未识别到可采样聊天背景，使用渐变降级（不含真实模糊）");
                    }
                    deliver(expected, expectedMode, BubbleGlass.drawable(null, null, corner, tint, dark, density));
                    return;
                }
                sourceStamp = sample.source;
                work = WORK.submit(new BubbleWork(this, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Bitmap bitmap = BubbleGlass.blur(sample);
                            final Drawable glass = BubbleGlass.drawable(bitmap, sample, corner, tint, dark, density);
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    deliver(expected, expectedMode, glass);
                                }
                            });
                        } catch (java.util.concurrent.CancellationException ignore) {
                        } catch (Throwable t) {
                            failure(expected, expectedMode, corner, tint, dark, density);
                        }
                    }
                }));
            } catch (Throwable t) {
                log("气泡渲染降级 " + t.getClass().getSimpleName());
                deliver(expected, expectedMode, BubbleGlass.drawable(null, null, corner, tint, dark, density));
            }
        }

        void failure(final long expected, final int expectedMode, final float corner, final int tint,
                     final boolean dark, final float density) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    if (!isCurrent(expected, expectedMode)) {
                        return;
                    }
                    log("气泡背景处理失败，使用渐变降级");
                    deliver(expected, expectedMode, BubbleGlass.drawable(null, null, corner, tint, dark, density));
                }
            });
        }

        void deliver(long expected, int expectedMode, Drawable background) {
            View view = ref.get();
            if (background == null || view == null || !BubbleSettings.enabled()
                    || BubbleSettings.mode() != expectedMode
                    || !generation.accepts(expected, expectedMode, view.isAttachedToWindow())) {
                return;
            }
            view.getLocationInWindow(location);
            if (view.getWidth() != width || view.getHeight() != height
                    || (expectedMode == 0 && (location[0] != x || location[1] != y
                    || !BubbleGlass.isCurrent(view, sourceStamp)))) {
                // 结果算完时气泡已移动：置脏。下一帧 onPreDraw 会立刻重新排队，
                // 不再额外等 120ms，避免滚动期间固定多出约一个 120ms 的滞后。
                dirty = true;
                return;
            }
            try {
                if (!isCurrent(expected, expectedMode)) {
                    return;
                }
                checkPaddingOwnership(view);
                if (view.getBackground() != applied) {
                    ownsPadding = false;
                }
                if (expectedMode == 1 && background instanceof android.graphics.drawable.NinePatchDrawable) {
                    if (!ownsPadding) {
                        paddingLeft = view.getPaddingLeft();
                        paddingTop = view.getPaddingTop();
                        paddingRight = view.getPaddingRight();
                        paddingBottom = view.getPaddingBottom();
                    }
                    android.graphics.Rect padding = new android.graphics.Rect();
                    background.getPadding(padding);
                    setBackground(view, background);
                    // Zero insets are meaningful too; getPadding's boolean is not a validity flag.
                    view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                    appliedPaddingLeft = padding.left;
                    appliedPaddingTop = padding.top;
                    appliedPaddingRight = padding.right;
                    appliedPaddingBottom = padding.bottom;
                    ownsPadding = true;
                } else {
                    setBackground(view, background);
                    restorePadding(view);
                }
                applied = background;
                work = null;
            } catch (Throwable t) {
                log("写回气泡背景失败 " + t.getClass().getSimpleName());
            }
        }

        boolean isCurrent(long expected, int expectedMode) {
            View view = ref.get();
            return view != null && BubbleSettings.enabled() && BubbleSettings.mode() == expectedMode
                    && generation.accepts(expected, expectedMode, view.isAttachedToWindow());
        }
    }

    private static void setBackground(View view, Drawable drawable) {
        int l = view.getPaddingLeft();
        int t = view.getPaddingTop();
        int r = view.getPaddingRight();
        int b = view.getPaddingBottom();
        try {
            APPLYING.set(Boolean.TRUE);
            view.setBackground(drawable);
            view.setPadding(l, t, r, b);
        } finally {
            APPLYING.remove();
        }
    }

    private static Drawable imageDrawable(android.content.res.Resources resources, String path,
                                           int width, int height, float radius, int alpha) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("image dimensions unavailable");
        }
        final boolean compiled;
        final BubbleNinePatch.Raw raw;
        try {
            compiled = BubbleNinePatch.hasCompiledChunk(path);
            raw = compiled ? null : BubbleNinePatch.rawFrame(path, bounds.outWidth, bounds.outHeight);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("image metadata unavailable", failure);
        }
        if (compiled || raw != null) {
            BubbleNinePatch.requireBudget(bounds.outWidth, bounds.outHeight);
        }
        int sample = 1;
        if (!compiled && raw == null) {
            while (bounds.outWidth / sample > Math.min(width * 2, 2048)
                    || bounds.outHeight / sample > Math.min(height * 2, 2048)) {
                sample *= 2;
            }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) {
            throw new IllegalArgumentException("image decode failed");
        }
        byte[] chunk = bitmap.getNinePatchChunk();
        if (chunk != null || compiled) {
            try {
                return BubbleNinePatch.drawable(resources, bitmap, chunk, alpha);
            } catch (RuntimeException failure) {
                bitmap.recycle();
                throw failure;
            }
        }
        if (raw != null) {
            Bitmap interior = null;
            try {
                if (bitmap.getWidth() != raw.width + 2 || bitmap.getHeight() != raw.height + 2) {
                    throw new IllegalArgumentException("raw nine-patch dimensions changed");
                }
                interior = Bitmap.createBitmap(bitmap, 1, 1, raw.width, raw.height);
                return BubbleNinePatch.drawable(resources, interior, raw.chunk(), alpha);
            } catch (RuntimeException failure) {
                if (interior != null) interior.recycle();
                throw failure;
            } finally {
                bitmap.recycle();
            }
        }
        float scale = Math.max(1f, Math.max(width / 768f, height / 512f));
        int w = Math.max(1, Math.round(width / scale));
        int h = Math.max(1, Math.round(height / scale));
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, h), radius / scale, radius / scale, Path.Direction.CW);
        canvas.clipPath(clip);
        float cover = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
        float scaledW = bitmap.getWidth() * cover;
        float scaledH = bitmap.getHeight() * cover;
        RectF dst = new RectF((w - scaledW) / 2f, (h - scaledH) / 2f,
                (w + scaledW) / 2f, (h + scaledH) / 2f);
        canvas.drawBitmap(bitmap, null, dst, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        bitmap.recycle();
        BitmapDrawable result = new BitmapDrawable(resources, output);
        result.setAlpha(alpha);
        result.setFilterBitmap(true);
        return result;
    }

    private static boolean isSelfBubble(View bubble) {
        View root = bubble.getRootView();
        int[] a = new int[2];
        int[] b = new int[2];
        root.getLocationInWindow(a);
        bubble.getLocationInWindow(b);
        return b[0] - a[0] + bubble.getWidth() / 2f > root.getWidth() / 2f;
    }

    private static void log(String message) {
        try {
            XposedBridge.log("WEX: 气泡: " + message);
        } catch (Throwable ignore) {
        }
    }
}
