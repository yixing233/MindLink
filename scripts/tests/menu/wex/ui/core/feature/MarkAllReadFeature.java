package wex.ui.core.feature;

import android.app.Activity;

/** 测试替身，绝不加载真正的已读业务。 */
public class MarkAllReadFeature {
    public static int calls;
    public static Activity last;
    public static void markAllRead(Activity activity) { calls++; last = activity; }
}
