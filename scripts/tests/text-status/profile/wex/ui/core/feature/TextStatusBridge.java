package wex.ui.core.feature;

/** Status work is doubled in this source set; SelfProfile itself is production. */
final class TextStatusBridge {
    enum Kind { LOADING, READY, NONE, ERROR }
    static final class Snapshot { Kind kind = Kind.LOADING; String description = ""; }
    static int refreshes, accountChanges;
    static String changedTo = "";
    static Runnable beforeRefresh;
    static Snapshot value = new Snapshot();
    static Snapshot snapshot() { return value; }
    static void refresh(boolean force) { refreshes++; if (beforeRefresh != null) beforeRefresh.run(); }
    static void accountChanged(String account) { accountChanges++; changedTo = account; }
}
