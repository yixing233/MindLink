package wex.ui.core.feature;
import android.app.Activity;
public final class ImagePicker {
    public static final ImagePicker INSTANCE = new ImagePicker();
    public int toasts;
    public void toast(Activity activity, String message) { toasts++; }
}
