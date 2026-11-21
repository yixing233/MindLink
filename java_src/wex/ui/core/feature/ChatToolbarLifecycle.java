package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import java.util.IdentityHashMap;

/** 工具栏挂载只在视图已附着且开关开启时重试，不保留 Activity 强引用。 */
final class ChatToolbarLifecycle implements Runnable {

    /** Retry budget keeps growing past this point but never gives up while the footer
     *  is attached: WeChat inflates the input column lazily on a cold chat entry and on
     *  notification launches, so a bounded window silently lost the first mount. */
    static final int SLOW_RETRY_ATTEMPTS = 12;
    private static final long RETRY_MS = 150L;
    private static final long RETRY_MAX_MS = 2000L;

    interface Target {
        boolean isEnabled();
        boolean isAttached();
        boolean mount();
        void unmount();
        boolean post(Runnable task, long delay);
        void cancel(Runnable task);
        void onExhausted();
    }

    private final Target target;
    private int attempts;
    private boolean pending;

    ChatToolbarLifecycle(Target target) {
        this.target = target;
    }

    void refresh() {
        target.cancel(this);
        pending = false;
        attempts = 0;
        if (!target.isEnabled() || !target.isAttached()) {
            target.unmount();
            return;
        }
        pending = target.post(this, 0L);
    }

    void detach() {
        target.cancel(this);
        pending = false;
        attempts = 0;
        target.unmount();
    }

    /** Stop queued work while an Activity is paused without discarding a mounted shell. */
    void suspend() {
        target.cancel(this);
        pending = false;
        attempts = 0;
    }

    boolean isPending() { return pending; }

    @Override
    public void run() {
        if (!pending) {
            return;
        }
        pending = false;
        if (!target.isEnabled() || !target.isAttached()) {
            target.unmount();
            return;
        }
        if (target.mount()) {
            return;
        }
        attempts++;
        if (attempts == SLOW_RETRY_ATTEMPTS) target.onExhausted();
        long delay = Math.min(RETRY_MAX_MS, RETRY_MS << Math.min(attempts / 4, 4));
        pending = target.post(this, delay);
    }

    static Activity findActivity(Context context) {
        IdentityHashMap<Context, Boolean> seen = new IdentityHashMap<Context, Boolean>();
        while (context != null && seen.put(context, Boolean.TRUE) == null) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            if (!(context instanceof ContextWrapper)) {
                break;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
