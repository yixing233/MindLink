package wex.ui.core.feature;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** 查找可在输入行上方插入工具条的纵向容器，包含传入的根节点。 */
final class ChatToolbarViews {
    private static final int NODE_LIMIT = 512;
    private static final int CHILD_LIMIT = 2048;

    private ChatToolbarViews() {
    }

    static ViewGroup findInputContainer(ViewGroup root) {
        if (root == null || root.getVisibility() != View.VISIBLE || panel(root)) {
            return null;
        }
        ViewGroup known = knownInputColumn(root);
        if (known != null) return known;
        return findFallback(root);
    }

    private static ViewGroup knownInputColumn(ViewGroup footer) {
        if (footer.getChildCount() == 0 || !(footer.getChildAt(0) instanceof ViewGroup)) return null;
        ViewGroup root = (ViewGroup) footer.getChildAt(0);
        if (root.getVisibility() != View.VISIBLE || panel(root)) return null;
        if (root.getChildCount() <= 1 || !(root.getChildAt(1) instanceof ViewGroup)) return null;
        ViewGroup column = (ViewGroup) root.getChildAt(1);
        return column.getVisibility() == View.VISIBLE && column instanceof LinearLayout
                && ((LinearLayout) column).getOrientation() == LinearLayout.VERTICAL
                && containsEditText(column) ? column : null;
    }

    /** Prefer the deepest vertical column containing the editor;
     *  the old top-down match could return the whole footer and lose the shell when
     *  the host rebuilt that container on first or notification entry. */
    private static ViewGroup findFallback(ViewGroup root) {
        ViewGroup best = null;
        int bestDepth = -1;
        ArrayDeque<View> queue = new ArrayDeque<View>();
        IdentityHashMap<View, Integer> depths = new IdentityHashMap<>();
        queue.add(root);
        depths.put(root, 0);
        int admitted = 1;
        int scanned = 0;
        for (int visited = 0; !queue.isEmpty() && visited < NODE_LIMIT; visited++) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || panel(view)) continue;
            if (!(view instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) view;
            int depth = depths.get(view);
            boolean vertical = group instanceof LinearLayout
                    && ((LinearLayout) group).getOrientation() == LinearLayout.VERTICAL;
            if (vertical && depth > bestDepth && containsEditText(group)) {
                best = group; bestDepth = depth;
            }
            // Only a vertical column may host the toolbar; a horizontal input row
            // would place it beside the editor, and the card/footer shell would
            // stretch it across the whole page.
            if (group.getChildCount() > CHILD_LIMIT - scanned) continue;
            for (int i = 0; i < group.getChildCount() && admitted < NODE_LIMIT; i++) {
                scanned++;
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || panel(child)) continue;
                queue.addLast(child); depths.put(child, depth + 1); admitted++;
            }
        }
        return best;
    }

    private static boolean containsEditText(ViewGroup group) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(group); int admitted = 1;
        int scanned = 0;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || panel(view)) continue;
            if (view instanceof EditText) return true;
            if (view instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) view;
                if (parent.getChildCount() > CHILD_LIMIT - scanned) continue;
                for (int i = 0; i < parent.getChildCount() && admitted < NODE_LIMIT; i++) {
                    scanned++;
                    View child = parent.getChildAt(i);
                    if (child.getVisibility() != View.VISIBLE || panel(child)) continue;
                    queue.addLast(child); admitted++;
                }
            }
        }
        return false;
    }

    private static boolean panel(View view) {
        if (view instanceof android.widget.AbsListView) return true;
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if ("com.tencent.mm.pluginsdk.ui.chat.AppPanel".equals(name)
                    || "com.tencent.mm.view.SmileyPanel".equals(name)) return true;
        }
        return false;
    }
}
