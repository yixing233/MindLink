package wex.ui.core.feature;

/** Compilation-only default-deny profile seam; no ConfigStorage/DexKit implementation. */
final class HostProfileBridge {
    private HostProfileBridge() { }
    static void observe(Object candidate) { }
    static void publishObserved(Object candidate) { }
    static boolean readInProgress() { return false; }
    static void invalidate() { }
    static boolean observed() { return false; }
    static boolean accepts(Object candidate) { return false; }
    static Class<?> storageType() { return null; }
}
