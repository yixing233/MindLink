package wex.ui.core.feature;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/**
 * 首页日历和音乐按需展开；没有安全入口时保留卡片原承载。
 *
 * 来源：侧栏交互设计参照 WeKit 的
 * `features/items/beautify/home_screen_panel/HomeSidePanel*.kt`（GPL-3.0）；
 * 本文件基于 Wex 原有的日历/音乐卡片实现，改为 Java 的 Dialog 承载。
 */
public final class HomeSidebar {
    private static final String ENTRY_TAG = "wex_home_sidebar_entry";
    private static final String LIST_CLASS = "com.tencent.mm.ui.conversation.ConversationListView";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, WeakReference<Session>> SESSIONS =
            new WeakHashMap<Activity, WeakReference<Session>>();
    private static boolean configured;
    private static boolean calendar;
    private static boolean image;
    private static boolean music;
    private static String order;
    private static boolean lifecycleRegistered;

    private HomeSidebar() {
    }

    /** 保持原设置的进程启动快照，不把尚未重启的设置当作已生效。 */
    public static synchronized void configure(String modulePath) {
        if (configured) return;
        try {
            calendar = WexStore.INSTANCE.getBool("home_calendar_card", false);
            image = WexStore.INSTANCE.getBool("home_image_card", false);
            music = WexStore.INSTANCE.getBool("home_music_card", false);
            order = WexStore.INSTANCE.getString("home_card_order", "calendar,image,music");
            configured = true;
        } catch (Throwable error) {
            log("读取卡片配置失败 " + error.getClass().getSimpleName());
        }
    }

    public static void onHomeCards(Activity activity, ViewGroup root) {
        sync(activity, root);
    }

    public static void sync(Activity activity, ViewGroup root) {
        if (!valid(activity) || root == null || !(calendar || image || music)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        installLifecycle(activity.getApplication());
        Session session = session(activity);
        if (session == null || session.disposed || session.root.get() != root) {
            if (session != null) session.dispose();
            session = new Session(activity, root);
            SESSIONS.put(activity, new WeakReference<Session>(session));
        }
        session.request(0L);
    }

    private static boolean valid(Activity activity) {
        return activity != null && "com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName())
                && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static Session session(Activity activity) {
        WeakReference<Session> ref = SESSIONS.get(activity);
        return ref == null ? null : ref.get();
    }

    private static void installLifecycle(Application application) {
        if (lifecycleRegistered) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity, Bundle state) { }
            public void onActivityStarted(Activity activity) { }
            public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            public void onActivityResumed(Activity activity) {
                Session session = session(activity);
                if (session != null) {
                    session.paused = false;
                    session.request(0L);
                }
            }
            public void onActivityPaused(Activity activity) {
                Session session = session(activity);
                if (session != null) session.leave();
            }
            public void onActivityStopped(Activity activity) {
                Session session = session(activity);
                if (session != null) session.leave();
            }
            public void onActivityDestroyed(Activity activity) {
                Session session = session(activity);
                if (session != null) session.dispose();
                SESSIONS.remove(activity);
            }
        });
        lifecycleRegistered = true;
    }

    private static final class Session implements Runnable, ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        final WeakReference<Activity> owner;
        final WeakReference<ViewGroup> root;
        final HomeSidebarState state = new HomeSidebarState();
        final HomeSidebarCards cards;
        WeakReference<ListView> list = new WeakReference<ListView>(null);
        WeakReference<ViewGroup> toolbar = new WeakReference<ViewGroup>(null);
        ViewTreeObserver observer;
        ImageView entry;
        LinearLayout header;
        LinearLayout sideBody;
        View musicView;
        Dialog dialog;
        View loadHost;
        Runnable loadCards;
        List<String> headerKinds = new ArrayList<String>();
        int headerWidth;
        int entryAttempts;
        int ownerWidth;
        long lastCheck;
        boolean queued;
        boolean disposed;
        boolean paused;
        boolean warned;
        boolean changing;

        Session(Activity activity, ViewGroup decor) {
            owner = new WeakReference<Activity>(activity);
            root = new WeakReference<ViewGroup>(decor);
            cards = new HomeSidebarCards(activity);
            decor.addOnAttachStateChangeListener(this);
            watch(decor);
        }

        void watch(ViewGroup decor) {
            ViewTreeObserver current = decor.getViewTreeObserver();
            if (observer == current) return;
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = current;
            observer.addOnPreDrawListener(this);
        }

        void request(long delay) {
            if (queued || disposed || paused) return;
            queued = true;
            MAIN.postDelayed(this, delay);
        }

        @Override
        public void run() {
            queued = false;
            Activity activity = owner.get();
            ViewGroup decor = root.get();
            if (disposed || !valid(activity) || decor == null) {
                dispose();
                return;
            }
            if (paused || !decor.isAttachedToWindow()) return;
            if (ownerWidth != 0 && ownerWidth != decor.getWidth()) close();
            ownerWidth = decor.getWidth();
            lastCheck = SystemClock.uptimeMillis();
            changing = true;
            try {
                ListView current = findConversationList(decor);
                if (current != list.get()) {
                    close();
                    LinearLayout oldHeader = header;
                    removeHeader();
                    if (oldHeader != null) oldHeader.removeAllViews();
                    list = new WeakReference<ListView>(current);
                    headerKinds.clear();
                    headerWidth = 0;
                }
                int pageState = current == null ? 0 : conversationState(decor, current);
                if (pageState != 1) {
                    state.available(false);
                    close();
                    if (entry != null) entry.setVisibility(View.GONE);
                    if (pageState < 0 && visible(current)) {
                        rebuildHeader(current, HomeSidebarState.order(order, calendar, image, music));
                        updateMusicVisibility();
                    } else {
                        cards.setMusicVisible(false);
                    }
                    return;
                }
                boolean available = (calendar || music) && ensureEntry(activity, decor);
                state.available(available);
                if (!available) close();
                if (!available && (calendar || music) && ++entryAttempts < 10) {
                    rebuildHeader(current, HomeSidebarState.order(order, false, image, false));
                    request(150L);
                } else {
                    if (available) entryAttempts = 0;
                    rebuildHeader(current, HomeSidebarState.order(order,
                            !available && calendar, image, !available && music));
                    if (!available && (calendar || music) && !warned) {
                        warned = true;
                        log("顶栏没有安全的侧栏入口，日历与音乐保留在首页卡片区");
                    }
                }
                updateMusicVisibility();
            } catch (Throwable error) {
                close();
                log("同步侧栏失败 " + error.getClass().getSimpleName());
            } finally {
                changing = false;
            }
        }

        boolean ensureEntry(final Activity activity, ViewGroup decor) {
            ViewGroup bar = toolbar.get();
            if (bar == null || !bar.isAttachedToWindow()) {
                bar = findToolbar(decor);
                toolbar = new WeakReference<ViewGroup>(bar);
            }
            if (bar == null || !bar.isAttachedToWindow() || !visible(bar)) {
                if (entry != null) entry.setVisibility(View.GONE);
                return false;
            }
            ViewGroup slot = findSlotContainer(bar);
            if (slot == null) {
                if (entry != null) entry.setVisibility(View.GONE);
                return false;
            }
            int size = dp(activity, 48);
            int padding = dp(activity, 4);
            int slotWidth = slot.getWidth() - slot.getPaddingLeft() - slot.getPaddingRight();
            int slotHeight = slot.getHeight() - slot.getPaddingTop() - slot.getPaddingBottom();
            int[] base = new int[2];
            slot.getLocationInWindow(base);
            base[0] += slot.getPaddingLeft();
            base[1] += slot.getPaddingTop();
            List<int[]> occupied = new ArrayList<int[]>();
            int[] visited = new int[]{0};
            collectOccupied(bar, bar, slot, entry, base, occupied, visited);
            int left = visited[0] > 128 ? -1
                    : HomeSidebarSlot.find(slotWidth, slotHeight, size, padding, occupied);
            if (left < 0) {
                if (entry != null) entry.setVisibility(View.GONE);
                return false;
            }
            if (entry == null) {
                entry = icon(activity, "shell_home", "日历和音乐");
                entry.setTag(ENTRY_TAG);
                entry.setVisibility(View.INVISIBLE);
                entry.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) { open(); }
                });
            }
            if (entry.getParent() != slot) {
                removeEntry();
                ViewGroup.LayoutParams params = slot instanceof RelativeLayout
                        ? new RelativeLayout.LayoutParams(size, size) : new FrameLayout.LayoutParams(size, size);
                slot.addView(entry, params);
            }
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) entry.getLayoutParams();
            int top = (slotHeight - size) / 2;
            if (params.leftMargin != left || params.topMargin != top) {
                params.leftMargin = left;
                params.topMargin = top;
                if (params instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) params).gravity = Gravity.TOP | Gravity.LEFT;
                }
                entry.setLayoutParams(params);
            }
            if (entry.getVisibility() == View.GONE) entry.setVisibility(View.INVISIBLE);
            int[] actual = new int[2];
            entry.getLocationInWindow(actual);
            boolean fits = entry.isAttachedToWindow() && entry.getWidth() == size && entry.getHeight() == size
                    && HomeSidebarSlot.fits(slotWidth, slotHeight, size, actual[0] - base[0],
                    actual[1] - base[1], padding, occupied);
            int visibility = fits ? View.VISIBLE : View.INVISIBLE;
            if (entry.getVisibility() != visibility) entry.setVisibility(visibility);
            return fits && visible(entry);
        }

        void rebuildHeader(ListView target, List<String> kinds) {
            int width = target.getWidth() - dp(target.getContext(), 16);
            if (width <= 0) return;
            if (headerWidth == width && headerKinds.equals(kinds)) return;
            if (dialog != null) close();
            List<View> nextCards = new ArrayList<View>();
            for (String kind : kinds) {
                View card = cards.get(kind, width);
                if (card == null) continue;
                if (card.getParent() != null && card.getParent() != header && card.getParent() != sideBody) continue;
                nextCards.add(card);
                if ("music".equals(kind)) musicView = card;
            }
            cards.setMusicVisible(false);
            LinearLayout container = header;
            if (nextCards.isEmpty()) {
                removeHeader();
                if (container != null) container.removeAllViews();
            } else {
                if (container == null) {
                    container = new LinearLayout(target.getContext());
                    container.setOrientation(LinearLayout.VERTICAL);
                    int padding = dp(target.getContext(), 8);
                    container.setPadding(padding, padding, padding, padding);
                    container.setTag("wex_home_sidebar_fallback_cards");
                }
                // 保留已注册的 header，只更新自己的子卡片，不改变图片与搜索 header 的先后位置。
                container.removeAllViews();
                for (View card : nextCards) {
                    if (card.getParent() == sideBody && sideBody != null) sideBody.removeView(card);
                    addCard(container, card);
                }
                if (header == null) {
                    target.addHeaderView(container, null, false);
                    header = container;
                }
            }
            headerKinds = new ArrayList<String>(kinds);
            headerWidth = width;
        }

        void removeHeader() {
            ListView target = list.get();
            if (target != null && header != null) target.removeHeaderView(header);
            header = null;
        }

        void removeEntry() {
            if (entry != null && entry.getParent() instanceof ViewGroup) {
                ((ViewGroup) entry.getParent()).removeView(entry);
            }
        }

        void open() {
            Activity activity = owner.get();
            ViewGroup decor = root.get();
            ListView target = list.get();
            if (paused || !valid(activity) || decor == null || target == null || entry == null
                    || !visible(entry) || conversationState(decor, target) != 1
                    || !ensureEntry(activity, decor)) return;
            final long token = state.open();
            if (token < 0L) return;
            try {
                final Dialog window = new Dialog(activity);
                window.requestWindowFeature(Window.FEATURE_NO_TITLE);
                boolean dark = (activity.getResources().getConfiguration().uiMode & 0x30) == 0x20;
                final FrameLayout shade = new FrameLayout(activity);
                shade.setBackgroundColor(0x66000000);
                shade.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) { close(); }
                });
                final LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setClickable(true);
                panel.setBackgroundColor(SettingsShell.windowBackground(dark));
                int screenWidth = decor.getWidth();
                final int width = Math.min(dp(activity, 400), Math.max(1, screenWidth - dp(activity, 48)));
                shade.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
                final int padding = dp(activity, 16);
                panel.setPadding(padding, 0, padding, 0);
                panel.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                        android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                        panel.setPadding(padding + bars.left, bars.top, padding, bars.bottom + padding);
                        return insets;
                    }
                });
                LinearLayout heading = new LinearLayout(activity);
                heading.setGravity(Gravity.CENTER_VERTICAL);
                TextView title = new TextView(activity);
                title.setText("Mdl");
                title.setTextSize(20f);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextColor(dark ? 0xFFF2F4F2 : 0xFF14171A);
                heading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                ImageView close = icon(activity, "shell_close", "关闭侧栏");
                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) { close(); }
                });
                heading.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 56)));
                panel.addView(heading);
                ScrollView scroll = new ScrollView(activity);
                final LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);
                // 顶部资料区：头像 + 昵称 + 状态（参照 WeKit 的侧栏头部版式）。
                View profile = ProfileView.buildPanel(activity);
                if (profile != null) {
                    LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    profileParams.bottomMargin = dp(activity, 14);
                    content.addView(profile, profileParams);
                }
                View actions = SidebarActions.build(activity);
                if (actions != null) {
                    LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    actionParams.bottomMargin = dp(activity, 14);
                    content.addView(actions, actionParams);
                }
                sideBody = content;
                scroll.addView(content);
                panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                window.setContentView(shade);
                window.setCancelable(true);
                window.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(android.content.DialogInterface ignored) {
                        panel.animate().cancel();
                        content.removeAllViews();
                        if (sideBody == content) {
                            cancelLoad();
                            state.close();
                            sideBody = null;
                            if (dialog == window) dialog = null;
                            cards.setMusicVisible(false);
                        }
                    }
                });
                dialog = window;
                Window androidWindow = window.getWindow();
                if (androidWindow != null) {
                    androidWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    androidWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    androidWindow.setDecorFitsSystemWindows(false);
                }
                window.show();
                if (androidWindow != null) {
                    androidWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    SettingsShell.applyBarAppearance(androidWindow, dark);
                }
                panel.requestApplyInsets();
                panel.setTranslationX(-width);
                panel.animate().translationX(0f).setDuration(180L).start();
                loadHost = panel;
                loadCards = new Runnable() {
                    int attempts;

                    @Override
                    public void run() {
                        if (!state.accepts(token) || dialog != window || sideBody != content) return;
                        int contentWidth = panel.getWidth() - panel.getPaddingLeft() - panel.getPaddingRight();
                        if (contentWidth <= 0) {
                            if (++attempts < 8) {
                                panel.postDelayed(this, 120L);
                            } else {
                                close();
                                log("侧栏尺寸未就绪，已取消卡片加载");
                            }
                            return;
                        }
                        loadHost = null;
                        loadCards = null;
                        try {
                            for (String kind : HomeSidebarState.order(order, calendar, false, music)) {
                                View card = cards.get(kind, contentWidth);
                                if (card == null || card.getParent() != null) continue;
                                addCard(sideBody, card);
                                if ("music".equals(kind)) musicView = card;
                            }
                            if (musicView != null) {
                                musicView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (state.accepts(token) && dialog == window) updateMusicVisibility();
                                    }
                                });
                            }
                        } catch (Throwable error) {
                            log("加载侧栏卡片失败 " + error.getClass().getSimpleName());
                        }
                    }
                };
                panel.post(loadCards);
            } catch (Throwable error) {
                close();
                log("打开侧栏失败 " + error.getClass().getSimpleName());
            }
        }

        void leave() {
            paused = true;
            MAIN.removeCallbacks(this);
            queued = false;
            state.available(false);
            close();
            cards.setMusicVisible(false);
            if (entry != null) entry.setVisibility(View.GONE);
        }

        void cancelLoad() {
            if (loadHost != null && loadCards != null) loadHost.removeCallbacks(loadCards);
            loadHost = null;
            loadCards = null;
        }

        void close() {
            boolean wasOpen = state.isOpen() || dialog != null;
            cancelLoad();
            state.close();
            Dialog current = dialog;
            dialog = null;
            if (sideBody != null) {
                sideBody.removeAllViews();
                sideBody = null;
            }
            if (current != null) {
                try { current.dismiss(); } catch (Throwable ignore) { }
            }
            if (wasOpen) cards.setMusicVisible(false);
        }

        void updateMusicVisibility() {
            boolean visible = musicView != null && musicView.isAttachedToWindow() && musicView.isShown();
            cards.setMusicVisible(visible);
        }

        @Override
        public boolean onPreDraw() {
            if (!changing) request(Math.max(0L, 250L - (SystemClock.uptimeMillis() - lastCheck)));
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            ViewGroup decor = root.get();
            if (!disposed && decor != null) {
                watch(decor);
                request(0L);
            }
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            MAIN.removeCallbacks(this);
            queued = false;
            close();
            cards.setMusicVisible(false);
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            MAIN.removeCallbacks(this);
            queued = false;
            close();
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            ViewGroup decor = root.get();
            if (decor != null) decor.removeOnAttachStateChangeListener(this);
            removeEntry();
            removeHeader();
            cards.release();
            musicView = null;
            entry = null;
        }
    }

    private static void addCard(LinearLayout host, View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (host.getChildCount() > 0) params.topMargin = dp(host.getContext(), 10);
        host.addView(card, params);
    }

    private static ImageView icon(Activity activity, String name, String description) {
        ImageView view = new ImageView(activity);
        view.setImageBitmap(TablerIcons.bitmap(name, dp(activity, 22),
                (activity.getResources().getConfiguration().uiMode & 0x30) == 0x20 ? 0xFFE6E6E6 : 0xFF1A1A1A));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888), null, new ColorDrawable(Color.WHITE)));
        return view;
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean visible(View view) {
        return view != null && view.isAttachedToWindow() && view.isShown() && view.getAlpha() > 0f
                && view.getWidth() > 0 && view.getHeight() > 0 && view.getGlobalVisibleRect(new Rect());
    }

    /** 1=会话页，0=已离开，-1=宿主结构未知（仅保留原卡片）。 */
    private static int conversationState(ViewGroup root, ListView list) {
        ArrayDeque<View> queue = new ArrayDeque<View>();
        queue.add(root);
        for (int count = 0; !queue.isEmpty() && count < 512; count++) {
            View view = queue.removeFirst();
            String name = view.getClass().getName();
            if ((name.contains("ChattingUILayout") || name.contains("MMChattingListView")
                    || name.startsWith("com.tencent.mm.ui.chatting.ChattingUI")) && visible(view)) return 0;
            if (view instanceof ViewGroup && !(view instanceof ListView)) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 512; i++) queue.addLast(group.getChildAt(i));
            }
        }
        View page = list;
        boolean pagerFound = false;
        for (int depth = 0; page.getParent() instanceof View && depth < 24; depth++) {
            View parent = (View) page.getParent();
            String name = parent.getClass().getName();
            if ("com.tencent.mm.ui.CustomViewPager".equals(name)
                    || "com.tencent.mm.ui.base.CustomViewPager".equals(name)) {
                try {
                    Object position = parent.getClass().getMethod("getCurrentItem").invoke(parent);
                    if (!(position instanceof Integer)) return -1;
                    if (((Integer) position).intValue() != 0) return 0;
                    pagerFound = true;
                } catch (Throwable ignore) {
                    return -1;
                }
                break;
            }
            page = parent;
        }
        if (!pagerFound) return -1;
        Rect area = new Rect();
        return visible(page) && page.getGlobalVisibleRect(area)
                && area.width() >= page.getWidth() * 0.9f ? 1 : 0;
    }

    private static ListView findConversationList(View root) {
        View result = find(root, false);
        return result instanceof ListView ? (ListView) result : null;
    }

    private static ViewGroup findToolbar(View root) {
        View result = find(root, true);
        return result instanceof ViewGroup ? (ViewGroup) result : null;
    }

    private static View find(View root, boolean toolbar) {
        ArrayDeque<View> queue = new ArrayDeque<View>();
        queue.add(root);
        for (int count = 0; !queue.isEmpty() && count < 512; count++) {
            View view = queue.removeFirst();
            String name = view.getClass().getName();
            if (toolbar ? name.endsWith(".Toolbar") && visible(view) : LIST_CLASS.equals(name)) return view;
            if (view instanceof ViewGroup && !(view instanceof ListView)) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 512; i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }

    private static ViewGroup findSlotContainer(ViewGroup toolbar) {
        if (toolbar instanceof RelativeLayout || toolbar instanceof FrameLayout) return toolbar;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if ((view instanceof RelativeLayout || view instanceof FrameLayout) && visible(view)
                    && view.getWidth() >= toolbar.getWidth() * 0.8f) return (ViewGroup) view;
        }
        return null;
    }

    private static void collectOccupied(View view, View root, View slot, View entry, int[] base,
                                        List<int[]> occupied, int[] count) {
        if (++count[0] > 128 || view == entry || !visible(view)) return;
        if (view != root && view != slot && (!(view instanceof ViewGroup) || view.isClickable())) {
            int[] at = new int[2];
            view.getLocationInWindow(at);
            occupied.add(new int[]{at[0] - base[0], at[1] - base[1],
                    at[0] - base[0] + view.getWidth(), at[1] - base[1] + view.getHeight()});
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectOccupied(group.getChildAt(i), root, slot, entry, base, occupied, count);
        }
    }

    private static void log(String message) {
        try { XposedBridge.log("WEX: 首页侧栏: " + message); } catch (Throwable ignore) { }
    }
}
