package android.widget;

public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static String lastText;
    public static Toast makeText(android.content.Context context, CharSequence value, int duration) {
        lastText = value.toString();
        return new Toast();
    }
    public void show() {}
}
