package wex.ui.core.feature;

/** Compilation-only default-deny profile seam; real reader behavior has its own suite. */
final class HostProfileBridge {
    private HostProfileBridge() { }
    static void observe(Object candidate) { }
    static void publishObserved(Object candidate) { }
    static boolean readInProgress() { return false; }
    static void invalidate() { }
    static boolean observed() { return false; }
    static Class<?> storageType() { return null; }
    static boolean accepts(Object candidate) { return false; }
}
