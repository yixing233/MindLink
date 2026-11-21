package wex.ui.core.feature;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.WeakHashMap;

/** A host-loaded ItemDecoration adds scroll range, never viewport padding. */
final class ChatTailSpacer implements InvocationHandler {
    private static final WeakHashMap<View, WeakReference<ChatTailSpacer>> INSTANCES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> FAILED = new WeakHashMap<>();
    private final WeakReference<View> recycler;
    private int height, pending = -1, stable;
    private int viewport = -1, pendingViewport = -1, followScroll;
    private boolean atEnd, changing, followEnd;

    // Device-verified bridge for WeChat 8.0.76's optimized p2/h3 ABI. Compile-only
    // AndroidX classes must never enter the module class loader.
    private static final String DEX = "ZGV4CjAzNQBy3aHsMwyZziZm1f5iAQzs0bb8V/vEK270BAAAcAAAAHhWNBIAAAAAAAAAAEgEAAAbAAAAcAAAAA8AAADcAAAABQAAABgBAAABAAAAVAEAAAUAAABcAQAAAQAAAIQBAABQAwAApAEAAKQBAACsAQAArwEAALUBAAC7AQAA1AEAAOgBAAD9AQAAKgIAAE0CAABwAgAAhAIAAJgCAACvAgAA1gIAAPICAAAPAwAAGQMAAC4DAAAxAwAANQMAADwDAABRAwAAWwMAAGsDAABzAwAAiwMAAAEAAAAEAAAABQAAAAYAAAAHAAAACAAAAAkAAAAKAAAACwAAAAwAAAANAAAADgAAAA8AAAASAAAAFQAAAAIAAAAAAAAAqAMAAAMAAAAHAAAAkAMAABIAAAANAAAAAAAAABQAAAANAAAAnAMAABMAAAANAAAAtAMAAAwACgAWAAAAAgAAABoAAAAHAAIAAAAAAAoAAQAYAAAADAAEAAAAAAAMAAMAFwAAAAwAAAARAAAABgAAAAAAAAARAAAAAAAAADcEAAAAAAAABjxpbml0PgABSQAESUxMTAAETExMTAAXTGFuZHJvaWQvZ3JhcGhpY3MvUmVjdDsAEkxhbmRyb2lkL3V0aWwvTG9nOwATTGFuZHJvaWQvdmlldy9WaWV3OwArTGFuZHJvaWR4L3JlY3ljbGVydmlldy93aWRnZXQvUmVjeWNsZXJWaWV3OwAhTGFuZHJvaWR4L3JlY3ljbGVydmlldy93aWRnZXQvaDM7ACFMYW5kcm9pZHgvcmVjeWNsZXJ2aWV3L3dpZGdldC9wMjsAEkxqYXZhL2xhbmcvT2JqZWN0OwASTGphdmEvbGFuZy9TdHJpbmc7ABVMamF2YS9sYW5nL1Rocm93YWJsZTsAJUxqYXZhL2xhbmcvcmVmbGVjdC9JbnZvY2F0aW9uSGFuZGxlcjsAGkxqYXZhL2xhbmcvcmVmbGVjdC9NZXRob2Q7ABtMbWRsL3NwYWNlci9UYWlsRGVjb3JhdGlvbjsACE1kbEdsYXNzABNUYWlsRGVjb3JhdGlvbi5qYXZhAAFWAAJWTAAFVkxMTEwAE1tMamF2YS9sYW5nL09iamVjdDsACGNhbGxiYWNrAA5nZXRJdGVtT2Zmc2V0cwAGaW52b2tlABZ0YWlsIGRlY29yYXRpb24gZmFpbGVkAAF3AAAAAwAAAAcACwAOAAAABAAAAAEAAwAEAAUAAwAAAAgACAAJAAAAAQAAAAoAAAAAAAAAAAAAAA4BAA4AEwQAAAAADqcbHnkAAAAAAgACAAEAAADEAwAABgAAAHAQAQAAAFsBAAAOAAYABQAEAAEAyQMAABQAAABUEAAAJEAOADJUDAISA3JAAgAQIygJDQIaAxAAGgQZAHEwAABDAg4AAAAAAAoAAQABAAsAAQEBABIDgYAE2AcEAfQHAA4AAAAAAAAAAQAAAAAAAAABAAAAGwAAAHAAAAACAAAADwAAANwAAAADAAAABQAAABgBAAAEAAAAAQAAAFQBAAAFAAAABQAAAFwBAAAGAAAAAQAAAIQBAAACIAAAGwAAAKQBAAABEAAABAAAAJADAAADEAAAAgAAALwDAAADIAAAAgAAAMQDAAABIAAAAgAAANgDAAAAIAAAAQAAADcEAAAAEAAAAQAAAEgEAAA=";

    private ChatTailSpacer(View view) { recycler = new WeakReference<>(view); }
    View view() { return recycler.get(); }

    static ChatTailSpacer obtain(View view) {
        WeakReference<ChatTailSpacer> previous = INSTANCES.get(view);
        ChatTailSpacer spacer = previous == null ? null : previous.get();
        if (spacer != null) return spacer;
        if (FAILED.containsKey(view)) return null;
        try {
            ClassLoader parent = view.getClass().getClassLoader();
            Class<?> decoration = Class.forName("androidx.recyclerview.widget.p2", false, parent);
            byte[] dex = java.util.Base64.getDecoder().decode(DEX);
            ClassLoader loader = (ClassLoader) Class.forName("dalvik.system.InMemoryDexClassLoader")
                    .getConstructor(ByteBuffer.class, ClassLoader.class).newInstance(ByteBuffer.wrap(dex), parent);
            spacer = new ChatTailSpacer(view);
            Object bridge = Class.forName("mdl.spacer.TailDecoration", true, loader)
                    .getConstructor(InvocationHandler.class).newInstance(spacer);
            ChatFooterOverlay.method(view.getClass(), "N", decoration).invoke(view, bridge);
            INSTANCES.put(view, new WeakReference<>(spacer));
            return spacer;
        } catch (ReflectiveOperationException | LinkageError error) {
            FAILED.put(view, Boolean.TRUE);
            de.robv.android.xposed.XposedBridge.log("WEX: tail-spacer unavailable " + error.getClass().getSimpleName());
            return null;
        }
    }

    boolean update(int value) {
        return update(value, false);
    }

    boolean update(int value, boolean animating) {
        View view = recycler.get();
        if (view == null || !view.isShown() || view.getHeight() <= 0) return true;
        if (view.isLayoutRequested()) return false;
        if (followScroll > 0) {
            if (followEnd) view.scrollBy(0, followScroll);
            followScroll = 0;
            changing = false;
            atEnd = !view.canScrollVertically(1);
        }
        int nextViewport = view.getHeight();
        if (pending != value || pendingViewport != nextViewport) {
            captureEnd(view);
            pending = value; pendingViewport = nextViewport; stable = 0;
            if (!animating) return false;
        }
        if (++stable < 2 && !animating) return false;
        if (height != value || viewport != nextViewport) {
            // Keep the old end anchor until the decoration has been laid out. The
            // host may already have moved it; scrollBy clamps to the remaining range.
            int distance = Math.max(0, value - height + (viewport < 0 ? 0 : viewport - nextViewport));
            boolean offsetsChanged = height != value;
            height = value; viewport = nextViewport;
            if (offsetsChanged) invalidate();
            if (followEnd && distance > 0) { followScroll = distance; return false; }
        }
        changing = false;
        atEnd = !view.canScrollVertically(1);
        return true;
    }

    void transition() {
        View view = recycler.get();
        if (view != null) captureEnd(view);
    }

    private void captureEnd(View view) {
        if (changing) return;
        // A resized viewport can make canScrollVertically true before this pass.
        // In that case the last stable frame still describes the user's anchor.
        followEnd = !view.canScrollVertically(1) || viewport >= 0 && viewport != view.getHeight() && atEnd;
        changing = true;
    }

    void release() {
        // Retain the zero-height decoration for reuse by this list: no footer or
        // Activity is held by its callback and reattachment never duplicates it.
        pending = pendingViewport = viewport = -1; stable = followScroll = 0;
        changing = followEnd = atEnd = false;
        if (height != 0) { height = 0; invalidate(); }
    }

    private void invalidate() {
        View view = recycler.get();
        if (view == null) return;
        try { ChatFooterOverlay.method(view.getClass(), "D0").invoke(view); }
        catch (ReflectiveOperationException error) {
            de.robv.android.xposed.XposedBridge.log("WEX: tail-spacer invalidate " + error.getClass().getSimpleName());
        }
    }

    @Override public Object invoke(Object proxy, Method unused, Object[] args) throws Exception {
        Rect offsets = (Rect) args[0]; offsets.setEmpty();
        View parent = (View) args[2];
        int position = (Integer) ChatFooterOverlay.method(parent.getClass(), "u0", View.class).invoke(parent, args[1]);
        int count = (Integer) ChatFooterOverlay.method(args[3].getClass(), "b").invoke(args[3]);
        if (position < 0 || count <= 0) return null;
        Object manager = ChatFooterOverlay.method(parent.getClass(), "getLayoutManager").invoke(parent);
        boolean reverse = manager != null && ChatFooterOverlay.field(manager.getClass(), "h").getBoolean(manager);
        if (position == (reverse ? 0 : count - 1)) offsets.bottom = height;
        return null;
    }

    static View recycler(View root) { return recycler(root, new int[]{0}, 0); }
    private static View recycler(View view, int[] budget, int depth) {
        if (view == null || !view.isShown() || depth > 12 || ++budget[0] > 512) return null;
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
            if ("androidx.recyclerview.widget.RecyclerView".equals(type.getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = recycler(group.getChildAt(i), budget, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }
}
