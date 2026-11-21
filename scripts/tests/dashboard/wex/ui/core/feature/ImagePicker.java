package wex.ui.core.feature;

public final class ImagePicker {
    public static final ImagePicker INSTANCE = new ImagePicker();
    public static int calls;
    public void toast(android.content.Context context, String value) {
        calls++;
        throw new AssertionError("host image picker used on desktop");
    }
}
