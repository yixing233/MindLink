package android.view;

public final class WindowInsets {
    private final android.graphics.Insets safe;
    public WindowInsets(android.graphics.Insets safe) { this.safe = safe; }
    public android.graphics.Insets getInsets(int types) { return safe; }
    public static final class Type {
        public static int systemBars() { return 1; }
        public static int displayCutout() { return 2; }
    }
}
