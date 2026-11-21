package wex.ui.core.feature;

public final class FilterBarMountChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fake implements FilterBarMount.Target, ChatToolbarLifecycle.Target {
        final Object parent = new Object();
        final Object originalParams = new Object();
        Object contentParent = parent;
        Object params = originalParams;
        final int originalIndex = 2;
        int index = originalIndex;
        Object savedParams;
        boolean ready;
        boolean attached = true;
        boolean enabled = true;
        boolean bar;
        boolean failInstall;
        boolean verifyInstall = true;
        int installs;
        int restores;
        int failures;
        int exhausted;
        Runnable pending;
        final FilterBarMount transaction = new FilterBarMount(this);

        public boolean isMounted() { return bar && params != originalParams && verifyInstall; }
        public boolean prepare() {
            if (!ready || !attached) return false;
            savedParams = params;
            return true;
        }
        public void install() {
            installs++;
            bar = true;
            params = new Object();
            if (failInstall) throw new IllegalStateException("host rejected reserved slot");
        }
        public void restore() {
            if (bar || savedParams != null) restores++;
            bar = false;
            if (savedParams != null) params = savedParams;
            savedParams = null;
        }
        public void failed(Throwable error) { failures++; }
        public boolean isEnabled() { return enabled; }
        public boolean isAttached() { return attached; }
        public boolean mount() { return transaction.mount(); }
        public void unmount() { transaction.unmount(); }
        public boolean post(Runnable task, long delay) {
            check(pending == null, "one bounded retry at a time");
            pending = task;
            return true;
        }
        public void cancel(Runnable task) { if (pending == task) pending = null; }
        public void onExhausted() { exhausted++; }
        void tick() {
            Runnable task = pending;
            pending = null;
            if (task != null) task.run();
        }
        void original() {
            check(!bar && params == originalParams && contentParent == parent && index == originalIndex,
                    "original parent, index and layout parameters preserved");
        }
    }

    public static void main(String[] args) {
        Fake unknown = new Fake();
        check(!unknown.mount() && unknown.installs == 0 && unknown.restores == 0,
                "unknown container is never mutated");
        unknown.original();

        Fake normal = new Fake();
        normal.ready = true;
        check(normal.mount(), "known slot mounts");
        check(normal.mount() && normal.installs == 1, "repeated mount is idempotent");
        check(normal.contentParent == normal.parent && normal.index == normal.originalIndex,
                "fixed sibling does not reparent scrolling content");
        normal.unmount();
        normal.original();
        normal.unmount();
        normal.original();

        Fake rejected = new Fake();
        rejected.ready = true;
        rejected.failInstall = true;
        check(!rejected.mount() && rejected.failures == 1 && rejected.restores == 1,
                "partial insertion failure rolls back");
        rejected.original();
        rejected.failInstall = false;
        rejected.verifyInstall = false;
        check(!rejected.mount(), "unconfirmed install is not reported as mounted");
        rejected.original();
        rejected.verifyInstall = true;
        check(rejected.mount(), "previous failure can retry successfully");
        rejected.unmount();

        Fake delayed = new Fake();
        ChatToolbarLifecycle lifecycle = new ChatToolbarLifecycle(delayed);
        lifecycle.refresh();
        delayed.tick();
        check(delayed.pending != null && delayed.installs == 0, "wait for layout without mutation");
        delayed.ready = true;
        delayed.tick();
        check(delayed.isMounted(), "late known slot mounts");
        lifecycle.refresh();
        lifecycle.refresh();
        delayed.tick();
        check(delayed.installs == 1, "refresh does not duplicate fixed bar");
        lifecycle.detach();
        delayed.original();
        check(delayed.pending == null, "detach cancels pending work");
        delayed.attached = true;
        lifecycle.refresh();
        delayed.tick();
        check(delayed.installs == 2, "reattach can reinstall");
        delayed.enabled = false;
        lifecycle.refresh();
        delayed.original();

        Fake replacement = new Fake();
        replacement.ready = true;
        check(replacement.mount(), "new list receives its own slot");
        delayed.original();
        replacement.unmount();
        replacement.original();

        Fake waiting = new Fake();
        ChatToolbarLifecycle bounded = new ChatToolbarLifecycle(waiting);
        bounded.refresh();
        for (int i = 0; i < 50; i++) waiting.tick();
        check(waiting.exhausted == 1 && waiting.pending != null && waiting.installs == 0,
                "unsupported container exhausts once and keeps retrying");
        waiting.ready = true;
        waiting.tick();
        check(waiting.installs == 1 && waiting.pending == null,
                "late supported container mounts after retry exhaustion");
        waiting.unmount();
        waiting.original();
        System.out.println("PASS: fixed filter mount deduplication, rollback, delayed slot, detach, replacement and retry limit");
    }
}
