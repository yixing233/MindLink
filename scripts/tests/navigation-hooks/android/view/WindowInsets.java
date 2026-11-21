package android.view;

/** Nav-private insets: navigation and IME can be varied independently. */
public final class WindowInsets {
    private final android.graphics.Insets safe, navigation, ime;
    public WindowInsets(android.graphics.Insets safe) { this(safe, safe, android.graphics.Insets.of(0, 0, 0, 0)); }
    public WindowInsets(android.graphics.Insets safe, android.graphics.Insets navigation, android.graphics.Insets ime) {
        this.safe = safe; this.navigation = navigation; this.ime = ime;
    }
    public android.graphics.Insets getInsets(int types) {
        if (types == Type.navigationBars()) return navigation;
        if (types == Type.ime()) return ime;
        return safe;
    }
    public static final class Type {
        public static int systemBars() { return 1; }
        public static int displayCutout() { return 2; }
        public static int navigationBars() { return 4; }
        public static int ime() { return 8; }
    }
}
