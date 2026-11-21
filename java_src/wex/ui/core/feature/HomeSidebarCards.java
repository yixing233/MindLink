package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

/** Main-thread, lazy card ownership for the home drawer and its header fallback. */
public final class HomeSidebarCards {
    private static final int IMAGE = 0;
    private static final int CALENDAR = 1;
    private static final int MUSIC = 2;
    private static final String IMAGE_CLASS = "wex.ui.core.feature.ImageCardFeature";
    private static final String CALENDAR_CLASS = "wex.ui.core.feature.CalendarCardFeature";
    private static final String MUSIC_CLASS = "x4.u0";
    private static final String[] CALENDAR_STYLES = {
            "wex.ui.core.feature.CalendarStyleHuangli",
            "wex.ui.core.feature.CalendarStyleMinimal",
            "wex.ui.core.feature.CalendarStyleProgress",
            "wex.ui.core.feature.CalendarStyleThreeDay"
    };
    private static final String[] IMAGE_VIEWS = {"cachedCard", "cachedPolaroidCard"};
    private static final ThreadLocal<Integer> BUILD_WIDTH = new ThreadLocal<Integer>();

    // Neither these shared stamps nor the construction override keep an Activity alive.
    private static final SharedCard[] SHARED = new SharedCard[3];
    private final WeakReference<Activity> owner;
    private final Card[] cards = new Card[3];
    private boolean released;
    private boolean musicVisible;
    private View pendingMusicAttach;
    private final View.OnAttachStateChangeListener musicAttach =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    if (pendingMusicAttach == view) {
                        cancelMusicAttach();
                        syncMusicVisibility();
                    }
                }

                @Override public void onViewDetachedFromWindow(View view) { }
            };

    public HomeSidebarCards(Activity activity) {
        owner = new WeakReference<Activity>(activity);
    }

    /** Used by smali only while a calendar/music card is being constructed. */
    public static int width(Context context) {
        Integer override = isMainThread() ? BUILD_WIDTH.get() : null;
        return override != null && override.intValue() > 0
                ? override.intValue() : context.getResources().getDisplayMetrics().widthPixels;
    }

    /** Fixed-dp calendar layouts must remain unchanged outside the sidebar bridge. */
    public static boolean hasWidthOverride() {
        Integer override = isMainThread() ? BUILD_WIDTH.get() : null;
        return override != null && override.intValue() > 0;
    }

    public View get(String kind, int widthPx) {
        if (released || !isMainThread()) return null;
        Activity activity = owner.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            release();
            return null;
        }
        int type = "image".equals(kind) ? IMAGE : "calendar".equals(kind) ? CALENDAR
                : "music".equals(kind) ? MUSIC : -1;
        if (type < 0) return null;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int requestedWidth = Math.max(1, type == IMAGE || widthPx <= 0 ? screenWidth : widthPx);
        Card local = cards[type];
        if (local != null && local.width == requestedWidth
                && (local.view == null || owns(local.view, activity))
                && (type != MUSIC || matches(SHARED[MUSIC], activity, local))) {
            if (type == MUSIC) syncMusicVisibility();
            return local.view;
        }
        if (local != null) {
            if (type == MUSIC) cancelMusicAttach();
            detach(local.view, activity);
            cards[type] = null;
        }
        SharedCard shared = SHARED[type];
        if (shared != null && shared.owner.get() == activity && shared.width == requestedWidth) {
            View view = shared.view.get();
            if (shared.empty || owns(view, activity)) {
                cards[type] = new Card(requestedWidth, view);
                if (type == MUSIC) syncMusicVisibility();
                return view;
            }
        }

        // Cache a null result too: repeated host checks must not retry construction/network work.
        Card card = new Card(requestedWidth, null);
        cards[type] = card;
        try {
            if (type == CALENDAR) {
                calendarCaches(activity, requestedWidth, true);
            } else if (type == IMAGE) {
                imageCaches(activity, requestedWidth, true);
            } else {
                Activity previous = shared == null ? null : shared.owner.get();
                if (previous != null && previous != activity) releaseMusic(previous);
                releaseMusic(activity);
            }
            View view = build(type, activity, requestedWidth);
            if (owns(view, activity)) {
                card.view = view;
                if (type == CALENDAR) allowCalendarHeight(view);
            }
        } catch (Exception error) {
            warn(kind, error);
        } catch (LinkageError error) {
            warn(kind, error);
        }
        SHARED[type] = new SharedCard(activity, card);
        if (type == MUSIC) syncMusicVisibility();
        return card.view;
    }

    public void setMusicVisible(boolean visible) {
        if (released || !isMainThread()) return;
        musicVisible = visible;
        // Re-evaluate attachment/showing, but forward only an effective visibility transition.
        syncMusicVisibility();
    }

    public void release() {
        if (released || !isMainThread()) return;
        released = true;
        musicVisible = false;
        cancelMusicAttach();
        Activity activity = owner.get();
        if (activity != null) {
            if (cards[MUSIC] != null) {
                try {
                    releaseMusic(activity);
                } catch (Exception error) {
                    warn("release music", error);
                } catch (LinkageError error) {
                    warn("release music", error);
                }
            }
            if (cards[CALENDAR] != null) releaseCaches(activity, CALENDAR);
            if (cards[IMAGE] != null) releaseCaches(activity, IMAGE);
            for (int type = 0; type < cards.length; type++) {
                Card card = cards[type];
                if (card != null) detach(card.view, activity);
                SharedCard shared = SHARED[type];
                if (card != null && shared != null && shared.owner.get() == activity) {
                    SHARED[type] = null;
                }
            }
        }
        for (int type = 0; type < cards.length; type++) cards[type] = null;
        owner.clear();
    }

    private static View build(int type, Activity activity, int widthPx) throws Exception {
        Integer previous = BUILD_WIDTH.get();
        // Images deliberately keep their original full-screen resource metrics, even if nested.
        if (type == IMAGE) BUILD_WIDTH.remove();
        else BUILD_WIDTH.set(Integer.valueOf(widthPx));
        try {
            Class<?> feature = moduleClass(type == IMAGE ? IMAGE_CLASS
                    : type == CALENDAR ? CALENDAR_CLASS : MUSIC_CLASS);
            Object receiver = type == MUSIC ? null : feature.getField("INSTANCE").get(null);
            Object result = feature.getMethod(type == MUSIC ? "f" : "getCard", Activity.class)
                    .invoke(receiver, activity);
            return result instanceof View ? (View) result : null;
        } finally {
            if (previous == null) BUILD_WIDTH.remove();
            else BUILD_WIDTH.set(previous);
        }
    }

    private void syncMusicVisibility() {
        Activity activity = owner.get();
        Card card = cards[MUSIC];
        SharedCard shared = SHARED[MUSIC];
        if (released || activity == null || card == null || card.view == null
                || !matches(shared, activity, card)) {
            cancelMusicAttach();
            return;
        }
        View view = card.view;
        if (musicVisible && !view.isAttachedToWindow()) {
            if (pendingMusicAttach != view) {
                cancelMusicAttach();
                pendingMusicAttach = view;
                view.addOnAttachStateChangeListener(musicAttach);
            }
        } else {
            cancelMusicAttach();
        }
        boolean visible = musicVisible && view.isAttachedToWindow() && view.isShown()
                && view.getWindowVisibility() == View.VISIBLE;
        if (shared.visibilityKnown && shared.visible == visible) return;
        // Mark even an unavailable bridge once; a redraw must not repeatedly invoke reflection.
        shared.visibilityKnown = true;
        shared.visible = visible;
        try {
            moduleClass(MUSIC_CLASS).getMethod("setUiVisible", Activity.class, Boolean.TYPE)
                    .invoke(null, activity, Boolean.valueOf(visible));
        } catch (Exception error) {
            warn("music visibility", error);
        } catch (LinkageError error) {
            warn("music visibility", error);
        }
    }

    private void cancelMusicAttach() {
        View view = pendingMusicAttach;
        pendingMusicAttach = null;
        if (view != null) view.removeOnAttachStateChangeListener(musicAttach);
    }

    private static void releaseMusic(Activity activity) throws Exception {
        moduleClass(MUSIC_CLASS).getMethod("releaseUi", Activity.class).invoke(null, activity);
    }

    private static void calendarCaches(Activity activity, int widthPx, boolean preparing)
            throws Exception {
        for (String name : CALENDAR_STYLES) {
            Class<?> style = moduleClass(name);
            Object value = cachedField(style, "cachedWrapper").get(null);
            if (!(value instanceof View)) continue;
            View view = (View) value;
            boolean sameOwner = owns(view, activity);
            boolean sameSize = matches(SHARED[CALENDAR], activity, new Card(widthPx, view));
            if (preparing ? !sameOwner || !sameSize : sameOwner) {
                // Only this style's confirmed cached View; no CalendarData or bitmap invalidation.
                style.getMethod("clearCache").invoke(style.getField("INSTANCE").get(null));
            }
        }
    }

    private static void imageCaches(Activity activity, int widthPx, boolean preparing)
            throws Exception {
        Class<?> feature = moduleClass(IMAGE_CLASS);
        for (String name : IMAGE_VIEWS) {
            Field field = cachedField(feature, name);
            Object value = field.get(null);
            if (!(value instanceof View)) continue;
            View view = (View) value;
            boolean sameOwner = owns(view, activity);
            boolean sameSize = matches(SHARED[IMAGE], activity, new Card(widthPx, view));
            if (preparing ? !sameOwner || !sameSize : sameOwner) {
                // Do not call ImageCardFeature.clearCache: cachedBitmap and image business stay intact.
                field.set(null, null);
            }
        }
    }

    private static void releaseCaches(Activity activity, int type) {
        try {
            if (type == CALENDAR) calendarCaches(activity, 0, false);
            else imageCaches(activity, 0, false);
        } catch (Exception error) {
            warn("release cached view", error);
        } catch (LinkageError error) {
            warn("release cached view", error);
        }
    }

    private static Field cachedField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Class<?> moduleClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, HomeSidebarCards.class.getClassLoader());
    }

    private static boolean matches(SharedCard shared, Activity activity, Card card) {
        return shared != null && shared.owner.get() == activity && shared.width == card.width
                && shared.view.get() == card.view && (card.view != null || shared.empty);
    }

    private static boolean owns(View view, Activity activity) {
        if (view == null || activity == null) return false;
        Context context = view.getContext();
        IdentityHashMap<Context, Boolean> seen = new IdentityHashMap<Context, Boolean>();
        while (context != null && seen.put(context, Boolean.TRUE) == null) {
            if (context == activity) return true;
            if (context instanceof Activity || !(context instanceof ContextWrapper)) return false;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return false;
    }

    private static void detach(View view, Activity activity) {
        if (!owns(view, activity)) return;
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
    }

    private static void allowCalendarHeight(View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup wrapper = (ViewGroup) view;
        if (wrapper.getChildCount() != 1) return;
        View card = wrapper.getChildAt(0);
        if (!(card instanceof FrameLayout)) return;
        ViewGroup.LayoutParams params = card.getLayoutParams();
        if (params != null && params.height > 0) {
            // Narrow columns/large fonts may grow naturally; never scale TextViews or bitmaps as a unit.
            card.setMinimumHeight(Math.max(card.getMinimumHeight(), params.height));
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            card.setLayoutParams(params);
        }
    }

    private static boolean isMainThread() {
        Looper main = Looper.getMainLooper();
        return main != null && Looper.myLooper() == main;
    }

    private static void warn(String operation, Throwable error) {
        Log.w("WEX.HomeSidebarCards", operation + " failed (" + error.getClass().getSimpleName() + ")");
    }

    private static final class Card {
        final int width;
        View view;

        Card(int width, View view) {
            this.width = width;
            this.view = view;
        }
    }

    private static final class SharedCard {
        final WeakReference<Activity> owner;
        final WeakReference<View> view;
        final int width;
        final boolean empty;
        boolean visibilityKnown;
        boolean visible;

        SharedCard(Activity activity, Card card) {
            owner = new WeakReference<Activity>(activity);
            view = new WeakReference<View>(card.view);
            width = card.width;
            empty = card.view == null;
        }
    }
}
