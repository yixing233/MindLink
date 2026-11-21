package wex.ui.core.feature;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

public class TablerIcons {
    public static Drawable drawable(String name, int size, int color) {
        if (!name.equals("wex_setting_icon.png") && !name.equals("mark_read.png")) throw new AssertionError(name);
        if (size <= 0) throw new AssertionError("icon size");
        return new ColorDrawable(color);
    }
}
