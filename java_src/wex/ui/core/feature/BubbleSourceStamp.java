package wex.ui.core.feature;

import java.lang.ref.WeakReference;
import java.util.Arrays;

/** 背景来源与绘制参数的快照，不延长宿主视图的生命周期。 */
final class BubbleSourceStamp {
    private final WeakReference<Object> owner;
    private final WeakReference<Object> drawable;
    private final int[] values;

    BubbleSourceStamp(Object owner, Object drawable, int[] values) {
        this.owner = new WeakReference<Object>(owner);
        this.drawable = new WeakReference<Object>(drawable);
        this.values = values.clone();
    }

    Object owner() {
        return owner.get();
    }

    Object drawable() {
        return drawable.get();
    }

    static boolean same(BubbleSourceStamp a, BubbleSourceStamp b) {
        if (a == null || b == null) {
            return a == b;
        }
        Object owner = a.owner();
        Object drawable = a.drawable();
        return owner != null && drawable != null && owner == b.owner() && drawable == b.drawable()
                && Arrays.equals(a.values, b.values);
    }
}
