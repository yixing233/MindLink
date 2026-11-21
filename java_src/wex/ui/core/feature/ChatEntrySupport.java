package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;

/**
 * Chat setup that must not depend on which entry opened the activity.
 *
 * Notification entry, launcher entry and in-app entry all reach the same ChattingUILayout, but the
 * floating header only ever looked inside its own island. This resolves the header from the
 * actually visible footer's chat layout, and keeps the gesture-navigation strip transparent for
 * that chat window.
 */
public final class ChatEntrySupport {
    private static final WeakHashMap<View, Long> OBSERVED = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<View>> FOOTER_LAYOUTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> DETACH_WATCHED = new WeakHashMap<>();

    private ChatEntrySupport() { }

    private static void log(String message) {
        try { XposedBridge.log("WEX: 聊天入口: " + message); } catch (Throwable ignored) { }
    }

    static Activity activity(View view) {
        Context context = view.getContext();
        for (int i = 0; i < 12 && context instanceof ContextWrapper; i++) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static View chatAncestor(View view) {
        for (int i = 0; view != null && i < 24; i++) {
            if ("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout".equals(view.getClass().getName())) return view;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    /** Called for every observed chat footer; idempotent and cheap when nothing changed. */
    public static void fromFooter(final View footer) {
        try {
            if (footer == null || !footer.isAttachedToWindow() || !footer.isShown()) return;
            final View layout = chatAncestor(footer);
            final Activity activity = activity(footer);
            if (layout == null || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            WeakReference<View> old = FOOTER_LAYOUTS.get(footer);
            if (old != null && old.get() != layout) releaseFooter(footer);
            FOOTER_LAYOUTS.put(footer, new WeakReference<>(layout));
            if (!DETACH_WATCHED.containsKey(footer)) {
                DETACH_WATCHED.put(footer, Boolean.TRUE);
                footer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) { }
                    @Override public void onViewDetachedFromWindow(View view) {
                        DETACH_WATCHED.remove(view);
                        releaseFooter(view);
                    }
                });
            }
            GestureNavigationApply.applyChat(layout, activity);
            long now = android.os.SystemClock.uptimeMillis();
            Long previous = OBSERVED.get(layout);
            if (previous != null && now - previous < 750) return;
            OBSERVED.put(layout, now);
            FloatingChatHeaderFeature.observe(layout);
        } catch (Exception error) {
            log("setup failed " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    public static void releaseFooter(View footer) {
        WeakReference<View> ref = FOOTER_LAYOUTS.remove(footer);
        View layout = ref == null ? null : ref.get();
        if (layout == null) return;
        OBSERVED.remove(layout);
        GestureNavigationApply.releaseChat(layout);
    }

    /**
     * Notification-entry header fallback: accept an ActionBarContainer only when it is the single
     * compact, screen-wide title bar of this chat window.
     */
    public static View headerFallback(View layout, Activity activity, View original) {
        if (original != null) return original;
        try {
            if (activity == null || layout == null || !layout.isShown()
                    || chatAncestor(layout) != layout || activity(layout) != activity) return null;
            View decor = activity.getWindow().getDecorView();
            if (layout.getRootView() != decor) return null;
            ArrayDeque<View> queue = new ArrayDeque<>();
            queue.add(decor);
            View candidate = null;
            int chats = 0, budget = 1024;
            while (!queue.isEmpty() && budget-- > 0) {
                View view = queue.removeFirst();
                if (!visible(view)) continue;
                String name = view.getClass().getName();
                if ("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout".equals(name)) {
                    if (view != layout) return null;
                    chats++;
                }
                if (isActionBar(view)) {
                    if (candidate != null && candidate != view) return null;
                    candidate = view;
                    continue;
                }
                if (name.contains("ChatFooter") || name.contains("RecyclerView")
                        || name.contains("MMChattingListView")) continue;
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            if (!queue.isEmpty() || chats != 1 || candidate == null || activity(candidate) != activity) return null;
            int[] xy = new int[2];
            candidate.getLocationInWindow(xy);
            if (candidate.getWidth() < decor.getWidth() * 3 / 4 || candidate.getHeight() <= 0
                    || candidate.getHeight() > Math.round(120 * decor.getResources().getDisplayMetrics().density)
                    || xy[1] < 0 || xy[1] > decor.getHeight() / 3) return null;
            return candidate;
        } catch (Exception error) {
            log("header fallback failed " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return null;
        }
    }

    private static boolean isActionBar(View view) {
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            if ("androidx.appcompat.widget.ActionBarContainer".equals(type.getName())) return true;
        }
        return false;
    }

    private static boolean visible(View view) {
        return view.getVisibility() == View.VISIBLE && view.getAlpha() > 0 && view.isShown()
                && view.getGlobalVisibleRect(new Rect());
    }
}
