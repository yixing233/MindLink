package wex.ui.core.feature;

import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-frame extras on the floating chat header's native parts (back / title / menu), for both
 * the integrated and the separated style:
 * <ul>
 *   <li>press ripple on the back button and on every visible menu action
 *       ({@link HeaderButtonRipple}), centred where the separated style draws its circles;</li>
 *   <li>in the integrated style, the title's {@code NoMeasuredTextView} limit is lifted to the
 *       width it really has ({@link FloatingHeaderStyle.TitleState#lift}); the separated style
 *       does this in its own title fit.</li>
 * </ul>
 * Everything is restorable: foregrounds and title limits return on {@link #restore}.
 */
final class FloatingHeaderParts {
    private FloatingHeaderParts() { }

    static final class State {
        final View header; final RelativeLayout root; final View back, title, menu;
        FloatingHeaderStyle.TitleState titleFit;
        final Map<View, Attached> ripples = new WeakHashMap<>();
        /** Ancestors whose clipping we released so a ripple circle can spill past a narrow button, with their original flags. */
        final Map<ViewGroup, boolean[]> clips = new WeakHashMap<>();
        State(View header, RelativeLayout root, View[] parts) {
            this.header = header; this.root = root; back = parts[0]; title = parts[1]; menu = parts[2];
        }
        boolean valid(View current) {
            return header == current && root.isAttachedToWindow()
                    && back.getParent() == root && title.getParent() == root && menu.getParent() == root;
        }
    }

    private static final class Attached {
        final Drawable original; final HeaderButtonRipple ripple;
        Attached(Drawable original, HeaderButtonRipple ripple) { this.original = original; this.ripple = ripple; }
    }

    static State apply(View header, State state, boolean separated) {
        if (header == null) { restore(state); return null; }
        if (state == null || !state.valid(header)) {
            restore(state);
            RelativeLayout root = FloatingHeaderStyle.headerRoot(header);
            View[] parts = root == null ? null : FloatingHeaderStyle.parts(root);
            if (parts == null) return null;
            state = new State(header, root, parts);
        }
        if (separated) {
            // The separated style owns the title host width and its texts; never fight it.
            if (state.titleFit != null) { state.titleFit.restore(); state.titleFit = null; }
        } else {
            if (state.titleFit == null) state.titleFit = new FloatingHeaderStyle.TitleState(state.title);
            state.titleFit.lift(0, false);
        }
        ripples(state, separated);
        return state;
    }

    static void restore(State state) {
        if (state == null) return;
        if (state.titleFit != null) { state.titleFit.restore(); state.titleFit = null; }
        for (Map.Entry<View, Attached> entry : state.ripples.entrySet()) detach(entry.getKey(), entry.getValue());
        state.ripples.clear();
        for (Map.Entry<ViewGroup, boolean[]> entry : state.clips.entrySet()) {
            ViewGroup group = entry.getKey(); boolean[] was = entry.getValue();
            if (was[0] && !group.getClipChildren()) group.setClipChildren(true);
            if (was[1] && !group.getClipToPadding()) group.setClipToPadding(true);
        }
        state.clips.clear();
    }

    private static void ripples(State state, boolean separated) {
        RelativeLayout root = state.root;
        int height = root.getHeight();
        if (height <= 0 || !root.isShown()) return;
        float density = root.getResources().getDisplayMetrics().density;
        // Separated: the drawn pill (diameter = header height). Integrated: an M3 icon-button state layer.
        float radius = separated ? height / 2f : Math.min(height / 2f, 20f * density);
        float centerY = height / 2f;
        boolean dark = SettingsTheme.isDark(root.getContext());
        Set<View> live = new HashSet<>();
        View back = clickable(state.back);
        if (back != null) {
            RectF icon = backIcon(root, state.back);
            float centerX = icon != null ? icon.centerX() : bounds(root, back).centerX();
            attach(state, back, centerX, centerY, radius, dark);
            live.add(back);
        }
        ViewGroup menu = menuContainer(state.menu);
        if (menu != null) {
            for (int i = 0; i < menu.getChildCount(); i++) {
                View action = menu.getChildAt(i);
                if (!visibleAction(action)) continue;
                View target = clickable(action);
                if (target == null) continue;
                attach(state, target, bounds(root, action).centerX(), centerY, radius, dark);
                live.add(target);
            }
        }
        for (Iterator<Map.Entry<View, Attached>> it = state.ripples.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<View, Attached> entry = it.next();
            if (!live.contains(entry.getKey())) { detach(entry.getKey(), entry.getValue()); it.remove(); }
        }
    }

    private static void attach(State state, View target, float rootX, float rootY, float radius, boolean dark) {
        Attached attached = state.ripples.get(target);
        if (attached == null || target.getForeground() != attached.ripple) {
            Drawable current = target.getForeground();
            // A ripple left by an earlier state (header re-applied) is not the host's original.
            Drawable original = current instanceof HeaderButtonRipple ? null : current;
            attached = new Attached(original, new HeaderButtonRipple());
            target.setForeground(attached.ripple);
            state.ripples.put(target, attached);
        }
        unclip(state, target);
        RectF own = bounds(state.root, target);
        attached.ripple.circle(rootX - own.left, rootY - own.top, radius, dark);
    }

    /**
     * The ripple is the clickable view's foreground, so every clipping parent cuts it to that
     * view's rectangle. The back button's clickable view is narrower than the circle (the menu
     * actions are not, which is why only the left side was cut). Release the clip on the chain
     * from the target's parent up to the header root; the root's own parent still bounds it to
     * the header, which the circle (diameter ≤ header height) fits in.
     */
    private static void unclip(State state, View target) {
        int depth = 0;
        for (android.view.ViewParent parent = target.getParent(); parent instanceof ViewGroup && depth++ < 8; parent = parent.getParent()) {
            ViewGroup group = (ViewGroup) parent;
            if (!state.clips.containsKey(group))
                state.clips.put(group, new boolean[]{group.getClipChildren(), group.getClipToPadding()});
            if (group.getClipChildren()) group.setClipChildren(false);
            if (group.getClipToPadding()) group.setClipToPadding(false);
            if (group == state.root) break;
        }
    }

    private static void detach(View target, Attached attached) {
        attached.ripple.stop();
        if (target.getForeground() == attached.ripple) target.setForeground(attached.original);
    }

    /** The view that actually receives the press: the host itself or its first clickable descendant. */
    private static View clickable(View host) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(host);
        for (int count = 0; !queue.isEmpty() && count < 32; count++) {
            View view = queue.removeFirst();
            if (!view.isShown()) continue;
            if (view.isClickable()) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return null;
    }

    /** Same rule as the separated decoration: the visible back icon, not the wider host. */
    private static RectF backIcon(View root, View host) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(host);
        for (int count = 0; !queue.isEmpty() && count < 64; count++) {
            View child = queue.removeFirst();
            if (!child.isShown()) continue;
            if (child instanceof ImageView) {
                RectF b = bounds(root, child);
                if (b.width() > child.getPaddingLeft() + child.getPaddingRight()) {
                    return new RectF(b.left + child.getPaddingLeft(), b.top, b.right - child.getPaddingRight(), b.bottom);
                }
            }
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return null;
    }

    /** The right part wraps one menu container (WeChat's WXActionMenuView); actions live inside it. */
    private static ViewGroup menuContainer(View host) {
        if (!(host instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) host;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof ViewGroup) return (ViewGroup) group.getChildAt(i);
        }
        return group;
    }

    /** Empty action slots the host keeps across style switches get no ripple. */
    private static boolean visibleAction(View action) {
        if (!action.isShown() || action.getWidth() <= 0 || action.getHeight() <= 0) return false;
        if (!"com.tencent.mm.ui.widget.WXActionMenuView".equals(action.getClass().getName())) return true;
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(action);
        for (int visited = 0; !queue.isEmpty() && visited < 64; visited++) {
            View content = queue.removeFirst();
            if (!content.isShown()) continue;
            if (content instanceof ImageView && ((ImageView) content).getDrawable() != null) return true;
            if (content instanceof TextView) {
                CharSequence text = ((TextView) content).getText();
                if (text != null && text.length() > 0) return true;
            }
            if (content instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) content;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return false;
    }

    private static RectF bounds(View root, View view) {
        int[] r = new int[2], v = new int[2];
        root.getLocationInWindow(r); view.getLocationInWindow(v);
        return new RectF(v[0] - r[0], v[1] - r[1], v[0] - r[0] + view.getWidth(), v[1] - r[1] + view.getHeight());
    }
}
