package wex.ui.core.feature;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/** Keeps the conversation page's non-capturable GL backdrop from flashing through during a pull. */
final class ConversationPullWallpaperGuard {
    private static final String PAGE = "com.tencent.mm.ui.conversation.MainUIView";
    private static final String CONTAINER = "com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer";
    private static final String SURFACE = "com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView";

    private WeakReference<View> surfaceRef;
    private int originalVisibility;

    boolean hideForConversationList(View list) {
        View page = conversationPage(list);
        View surface = page == null ? null : wallpaperSurface(page);
        if (surface == null) return restore();

        View previous = surfaceRef == null ? null : surfaceRef.get();
        if (previous == surface) {
            if (surface.getVisibility() == View.INVISIBLE) return false;
            surface.setVisibility(View.INVISIBLE);
            return true;
        }
        boolean restored = restore();
        if (surface.getVisibility() != View.VISIBLE) return restored;
        originalVisibility = surface.getVisibility();
        surfaceRef = new WeakReference<>(surface);
        surface.setVisibility(View.INVISIBLE);
        return true;
    }

    boolean restore() {
        View surface = surfaceRef == null ? null : surfaceRef.get();
        boolean changed = surface != null && surface.getVisibility() == View.INVISIBLE;
        if (changed)
            surface.setVisibility(originalVisibility);
        surfaceRef = null;
        return changed;
    }

    private static View conversationPage(View list) {
        for (View current = list; current != null;
             current = current.getParent() instanceof View ? (View) current.getParent() : null)
            if (PAGE.equals(current.getClass().getName())) return current;
        return null;
    }

    private static View wallpaperSurface(View page) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(page);
        for (int visited = 0; !queue.isEmpty() && visited < 256; visited++) {
            View current = queue.removeFirst();
            if (SURFACE.equals(current.getClass().getName()) && current.getParent() instanceof View) {
                View container = (View) current.getParent();
                if (CONTAINER.equals(container.getClass().getName()) && container.getParent() == page)
                    return current;
            }
            if (current instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) current;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }
}
