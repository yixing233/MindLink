package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

public final class ToolbarLifecycleChecks {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Fake implements ChatToolbarLifecycle.Target {
        boolean enabled;
        boolean attached;
        boolean ready;
        boolean mounted;
        int attempts;
        int insertions;
        int exhausted;
        Runnable pending;

        public boolean isEnabled() { return enabled; }
        public boolean isAttached() { return attached; }
        public boolean mount() {
            attempts++;
            if (!ready) return false;
            if (!mounted) insertions++;
            mounted = true;
            return true;
        }
        public void unmount() { mounted = false; }
        public boolean post(Runnable task, long delay) {
            check(pending == null, "only one retry pending");
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
    }

    public static void main(String[] args) {
        Fake f = new Fake();
        ChatToolbarLifecycle lifecycle = new ChatToolbarLifecycle(f);
        lifecycle.refresh();
        check(f.pending == null && f.attempts == 0, "disabled startup does not mount");
        f.attached = true;
        lifecycle.refresh();
        check(f.pending == null, "attached but disabled");
        f.enabled = true;
        lifecycle.refresh();
        f.tick();
        check(f.pending != null && f.insertions == 0, "wait for input container");
        f.ready = true;
        f.tick();
        check(f.mounted && f.insertions == 1 && f.pending == null, "late container mounted");
        lifecycle.refresh();
        lifecycle.refresh();
        f.tick();
        check(f.insertions == 1, "no duplicate insertion");
        f.enabled = false;
        lifecycle.refresh();
        check(!f.mounted && f.pending == null, "disable removes toolbar and retry");
        f.enabled = true;
        lifecycle.refresh();
        f.attached = false;
        f.tick();
        check(!f.mounted && f.pending == null, "detach before queued mount");
        f.attached = true;
        lifecycle.refresh();
        f.tick();
        check(f.mounted && f.insertions == 2, "reattach mounts again");
        lifecycle.refresh();
        lifecycle.detach();
        check(!f.mounted && f.pending == null, "detach callback cleans up before platform flag changes");
        f.ready = false;
        f.mounted = false;
        int previousAttempts = f.attempts;
        lifecycle.refresh();
        for (int i = 0; i < 30; i++) f.tick();
        check(f.attempts - previousAttempts >= 12, "retries continue beyond the first slow window");
        check(f.exhausted == 1 && f.pending != null, "retry exhaustion reported once while retrying");
        f.ready = true;
        f.tick();
        check(f.mounted && f.pending == null, "late mount wins after exhaustion");
        Activity activity = new Activity();
        check(ChatToolbarLifecycle.findActivity(activity) == activity, "direct activity");
        check(ChatToolbarLifecycle.findActivity(new ContextWrapper(new ContextWrapper(activity))) == activity,
                "wrapped activity");
        ContextWrapper cycle = new ContextWrapper(null) {
            public Context getBaseContext() { return this; }
        };
        check(ChatToolbarLifecycle.findActivity(cycle) == null, "wrapper cycle is bounded");
        check(ChatToolbarLifecycle.findActivity(new Context()) == null, "non-activity context");
        check(ChatToolbarLifecycle.findActivity(null) == null, "null context");
        System.out.println("PASS: toolbar disabled startup, delayed mount, deduplication, detach, retry bound and wrapped Context");
    }
}
