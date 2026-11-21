package wex.ui.core.feature;

public final class FeatureDiagnostics {
    public static int calls;
    public static String summary() { calls++; throw new AssertionError("host diagnostics used on desktop"); }
}
