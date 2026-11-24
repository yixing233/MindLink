package wex.ui.core.feature;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Converts existing Java data/config into the original WeKit UI's platform-only schema v1. */
final class HomeSidePanelSnapshot {
    private static final String AVATAR_KEY = "profile:avatar";
    private static final String STATUS_ICON_KEY = "profile:status_icon";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IMAGE_IO = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16), task -> {
                Thread thread = new Thread(task, "MindLink-sidebar-images");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private static final ExecutorService ICON_IO = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(8), task -> {
                Thread thread = new Thread(task, "MindLink-sidebar-status-icon");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final NativeStatusIcon STATUS_ICON = new NativeStatusIcon();

    private HomeSidePanelSnapshot() { }

    static SidePanelComposeBridge.Snapshot toolbar() {
        return build(true, false, java.util.Collections.<HomeSidePanelLayout.Card>emptyList(),
                java.util.Collections.<String, Boolean>emptyMap(),
                java.util.Collections.<String, Bitmap>emptyMap(), false);
    }

    /** The owner supplies an application-safe Context and a guarded, weakly-held completion callback. */
    static SidePanelComposeBridge.Snapshot toolbar(Context context, Runnable changed) {
        TextStatusBridge.Snapshot status = TextStatusBridge.snapshot();
        STATUS_ICON.request(context, status, changed);
        return toolbar();
    }

    static void releaseToolbar(Runnable changed) { STATUS_ICON.release(changed); }

    static SidePanelComposeBridge.Snapshot build(boolean initialized, boolean editing,
            List<HomeSidePanelLayout.Card> cards, Map<String, Boolean> masks,
            Map<String, Bitmap> loadedImages, boolean requestIfExpired) {
        return build(initialized, editing, editing ? null : "", cards, masks, loadedImages, requestIfExpired);
    }

    /** Editing variant: an editorRoute routes the Compose editor pages; null keeps the previous route out. */
    static SidePanelComposeBridge.Snapshot build(boolean initialized, boolean editing, String editorRoute,
            List<HomeSidePanelLayout.Card> cards, Map<String, Boolean> masks,
            Map<String, Bitmap> loadedImages, boolean requestIfExpired) {
        Bundle values = new Bundle();
        Map<String, Bitmap> images = new LinkedHashMap<>();
        values.putInt("schemaVersion", 1);
        values.putBoolean("initialized", initialized);
        values.putBoolean("editing", editing);
        if (editorRoute != null) values.putString("editorRoute", editorRoute);
        values.putBundle("profile", profile(images));
        ArrayList<Bundle> encoded = new ArrayList<>();
        for (HomeSidePanelLayout.Card card : cards) {
            Bundle value = new Bundle();
            value.putString("id", card.id);
            value.putString("cardType", card.type.serialName);
            switch (card.type) {
                case DATE_TIME:
                    value.putBoolean("showLunarCalendar", card.showLunarCalendar);
                    break;
                case WEATHER:
                    value.putBoolean("currentLocation", WeatherLocation.CURRENT.equals(card.weatherCity));
                    try { value.putBundle("city", HomeSidePanelData.toBundle(HomeSidePanelData.city(HomeSidePanelData.resolvedCity(card.weatherCity)))); }
                    catch (Exception ignored) { }
                    value.putBundle("state", HomeSidePanelData.weatherState(card, requestIfExpired));
                    break;
                case WALLET:
                    value.putBoolean("hideBalanceByDefault", card.hideWalletBalance);
                    Bundle wallet = new Bundle();
                    Long balance = HomeSidePanelWallet.read();
                    if (balance != null) wallet.putLong("balanceFen", balance);
                    wallet.putBoolean("defaultMaskEnabled", card.hideWalletBalance);
                    Boolean masked = masks.get(card.id);
                    wallet.putBoolean("isMasked", card.hideWalletBalance && (masked == null || masked));
                    value.putBundle("state", wallet);
                    break;
                case HITOKOTO:
                    Bundle settings = new Bundle();
                    settings.putString("categories", safe(card.hitokotoCategories));
                    if (card.hitokotoMinLength != null) settings.putInt("minLength", card.hitokotoMinLength);
                    if (card.hitokotoMaxLength != null) settings.putInt("maxLength", card.hitokotoMaxLength);
                    settings.putBoolean("showSource", card.showHitokotoSource);
                    settings.putBoolean("showAuthor", card.showHitokotoAuthor);
                    value.putBundle("settings", settings);
                    value.putBundle("state", HomeSidePanelData.hitokotoState(card, requestIfExpired));
                    break;
                case IMAGE:
                    if (card.imageAssetId != null) value.putString("imageAssetId", card.imageAssetId);
                    value.putInt("heightDp", imageHeight(card.imageHeightDp));
                    value.putString("scaleMode", HomeSidePanelLayout.isImageScaleMode(card.imageScaleMode)
                            ? card.imageScaleMode : "crop");
                    Bitmap bitmap = loadedImages.get(card.imageAssetId);
                    value.putBoolean("importing", loadedImages instanceof ImageResult
                            && ((ImageResult) loadedImages).pending.contains(card.imageAssetId));
                    if (bitmap != null && !bitmap.isRecycled()) {
                        String key = "image:" + card.imageAssetId;
                        images.put(key, bitmap);
                        value.putString("imageKey", key);
                        value.putInt("imageWidthPx", bitmap.getWidth());
                        value.putInt("imageHeightPx", bitmap.getHeight());
                    }
                    break;
                case HORIZONTAL_ACTIONS:
                case VERTICAL_ACTIONS:
                    ArrayList<Bundle> actions = new ArrayList<>();
                    for (HomeSidePanelLayout.Action action : card.actions) {
                        Bundle encodedAction = new Bundle();
                        encodedAction.putString("id", action.id);
                        encodedAction.putString("kind", action.kind.name());
                        actions.add(encodedAction);
                    }
                    value.putParcelableArrayList("actions", actions);
                    break;
            }
            encoded.add(value);
        }
        values.putParcelableArrayList("cards", encoded);
        return new SidePanelComposeBridge.Snapshot(values, images);
    }

    private static Bundle profile(Map<String, Bitmap> images) {
        Bundle profile = new Bundle();
        profile.putString("wxId", safe(SelfProfile.wxid()));
        profile.putString("nickname", safe(SelfProfile.nickname()));
        Bitmap avatar = SelfProfile.avatar();
        if (avatar != null && !avatar.isRecycled()) {
            images.put(AVATAR_KEY, avatar);
            profile.putString("avatarKey", AVATAR_KEY);
        }
        TextStatusBridge.Snapshot source = TextStatusBridge.snapshot();
        Bitmap statusBitmap = STATUS_ICON.bitmap(source);
        Bundle status = new Bundle();
        switch (source.kind) {
            case READY:
                status.putString("kind", "ready");
                status.putString("iconId", safe(source.iconId));
                status.putString("description", safe(source.description));
                if (statusBitmap != null && !statusBitmap.isRecycled()) {
                    images.put(STATUS_ICON_KEY, statusBitmap);
                    status.putString("iconKey", STATUS_ICON_KEY);
                }
                break;
            case NONE:
                status.putString("kind", "no_status");
                break;
            case ERROR:
                status.putString("kind", "error");
                status.putString("description", source.label());
                break;
            default:
                status.putString("kind", "loading");
                break;
        }
        profile.putBundle("status", status);
        return profile;
    }

    private static int imageHeight(Integer height) {
        int value = height == null ? 240 : height;
        return Math.max(HomeSidePanelLayout.IMAGE_MIN_HEIGHT_DP,
                Math.min(HomeSidePanelLayout.IMAGE_MAX_HEIGHT_DP, value)) / HomeSidePanelLayout.IMAGE_HEIGHT_STEP_DP
                * HomeSidePanelLayout.IMAGE_HEIGHT_STEP_DP;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static boolean postMain(Runnable task, long delay) {
        Runnable guarded = () -> { try { task.run(); } catch (Throwable ignored) { } };
        try { return delay > 0 ? MAIN.postDelayed(guarded, delay) : MAIN.post(guarded); }
        catch (Throwable ignored) { return false; }
    }

    private static void notifySafely(Runnable listener) {
        if (listener != null) try { listener.run(); } catch (Throwable ignored) { }
    }

    /** Private Java-only envelope. Only its Bitmap values and an importing boolean enter the bridge. */
    private static final class ImageResult extends LinkedHashMap<String, Bitmap> {
        final Set<String> pending;
        ImageResult(Map<String, Bitmap> loaded, Set<String> pending) {
            super(loaded);
            this.pending = java.util.Collections.unmodifiableSet(new HashSet<>(pending));
        }
    }

    /** One native icon at a time; account/status generation and owner disposal invalidate late results. */
    private static final class NativeStatusIcon {
        private final Map<Runnable, Long> listeners = new java.util.WeakHashMap<>();
        private String currentKey = "";
        private Bitmap loaded;
        private long generation;
        private boolean pending, attempted;

        synchronized Bitmap bitmap(TextStatusBridge.Snapshot source) {
            String key = key(source);
            if (!currentKey.equals(key)) reset(key);
            return key.isEmpty() || loaded == null || loaded.isRecycled() ? null : loaded;
        }

        void request(Context context, TextStatusBridge.Snapshot source, Runnable changed) {
            String key = key(source);
            if (context == null || changed == null || key.isEmpty()) return;
            // Never retain the Activity supplied by a toolbar/drawer. No host discovery/reflection on MAIN.
            Context application;
            try { application = context.getApplicationContext(); }
            catch (Throwable ignored) { return; }
            if (application == null || application instanceof android.app.Activity) return;
            final long token;
            synchronized (this) {
                if (!currentKey.equals(key)) reset(key);
                if (loaded != null && !loaded.isRecycled()) return;
                if (pending) listeners.put(changed, generation);
                if (pending || attempted) return;
                listeners.put(changed, generation);
                token = generation;
                pending = true;
            }
            WeakReference<Context> owner = new WeakReference<>(application);
            try {
                ICON_IO.execute(() -> {
                    try {
                        Context target = owner.get();
                        if (target == null || !valid(token, key)
                                || !key.equals(key(TextStatusBridge.snapshot()))) {
                            complete(token, key, null, false); return;
                        }
                        // Use the existing, fail-closed status renderer. No invented glyphs or service bypass.
                        IconTarget image = new IconTarget(target, token, key);
                        TextStatusBridge.renderIcon(image, source);
                        image.capture();
                        // The native renderer can assign its drawable later. Keep that real completion alive,
                        // but bound it without sleeping or keeping any Activity/owner strongly referenced.
                        if (!image.complete) {
                            if (!postMain(() -> complete(token, key, null, false), 6000L)) abandon(token, key);
                        }
                    } catch (Throwable ignored) {
                        complete(token, key, null, false);
                    }
                });
            } catch (Throwable ignored) { complete(token, key, null, false); }
        }

        synchronized void release(Runnable changed) {
            if (changed != null) listeners.remove(changed);
            listeners.size(); // expunge dead weak owners
            if (listeners.isEmpty() && pending) reset(currentKey);
        }

        private synchronized boolean valid(long token, String key) {
            return generation == token && currentKey.equals(key) && pending;
        }

        private void complete(long token, String key, Bitmap bitmap, boolean delivered) {
            if (!postMain(() -> {
                try {
                    List<Runnable> notify = new ArrayList<>();
                    synchronized (NativeStatusIcon.this) {
                        if (!valid(token, key)) return;
                        if (!key.equals(key(TextStatusBridge.snapshot()))) { reset(""); return; }
                        loaded = bitmap != null && !bitmap.isRecycled() ? bitmap : null;
                        pending = false;
                        attempted = true;
                        if (delivered && loaded != null) for (Map.Entry<Runnable, Long> entry : listeners.entrySet()) {
                            Runnable listener = entry.getKey();
                            if (listener != null && entry.getValue() == token) notify.add(listener);
                        }
                        listeners.clear();
                    }
                    for (Runnable listener : notify) notifySafely(listener);
                } catch (Throwable ignored) { abandon(token, key); }
            }, 0)) abandon(token, key);
        }

        private synchronized void abandon(long token, String key) {
            if (generation != token || !currentKey.equals(key)) return;
            loaded = null; pending = false; attempted = true;
            listeners.clear();
        }

        private void reset(String key) {
            generation++;
            currentKey = key;
            loaded = null; pending = false; attempted = false;
            listeners.clear();
            // Published bitmaps may still belong to the isolated UI. Drop references, never recycle.
        }

        private String key(TextStatusBridge.Snapshot value) {
            if (value == null || value.kind != TextStatusBridge.Kind.READY || safe(value.iconId).isEmpty()
                    || !safe(value.account).equals(safe(SelfProfile.wxid()))) return "";
            return safe(value.account) + '\u0000' + safe(value.statusId) + '\u0000' + value.iconId;
        }

        /** Captures the host's actual drawable, including a later async ImageView drawable assignment. */
        private final class IconTarget extends ImageView {
            private final long token;
            private final String key;
            private boolean capturing;
            volatile boolean complete;

            IconTarget(Context context, long token, String key) {
                super(context);
                this.token = token; this.key = key;
            }

            @Override public void setImageDrawable(Drawable drawable) {
                try {
                    super.setImageDrawable(drawable);
                    if (drawable != null) capture();
                } catch (Throwable ignored) { failed(); }
            }

            @Override public void setImageBitmap(Bitmap bitmap) {
                try {
                    super.setImageBitmap(bitmap);
                    if (bitmap != null) capture();
                } catch (Throwable ignored) { failed(); }
            }

            private void failed() {
                if (!complete) {
                    complete = true;
                    NativeStatusIcon.this.complete(token, key, null, false);
                }
            }

            void capture() {
                if (capturing || complete) return;
                capturing = true;
                try {
                    Drawable drawable = getDrawable();
                    if (drawable == null || !valid(token, key)) return;
                    Bitmap bitmap = null;
                    if (drawable instanceof BitmapDrawable) bitmap = ((BitmapDrawable) drawable).getBitmap();
                    if (bitmap == null || bitmap.isRecycled()) {
                        // Rasterize only the real host drawable, at a bounded icon size, not a substitute.
                        int size = Math.max(24, Math.min(192,
                                Math.round(getResources().getDisplayMetrics().density * 24f)));
                        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        Rect original = new Rect(drawable.getBounds());
                        try {
                            drawable.setBounds(0, 0, size, size);
                            drawable.draw(new Canvas(bitmap));
                        } finally { drawable.setBounds(original); }
                    }
                    complete = true;
                    NativeStatusIcon.this.complete(token, key, bitmap, true);
                } catch (Throwable ignored) { failed(); }
                finally { capturing = false; }
            }
        }
    }

    /** Per-owner local bitmap cache. A decode never blocks Compose snapshot construction or deletes files. */
    static final class Images {
        private final Context context;
        private final WeakReference<Runnable> changed;
        private final Map<String, Bitmap> loaded = new LinkedHashMap<>();
        private final Set<String> attempted = new HashSet<>();
        private final Set<String> pending = new HashSet<>();
        private Set<String> wanted = new HashSet<>();
        private volatile long generation;
        private volatile boolean closed;
        private final java.util.concurrent.ConcurrentHashMap<String, Long> failedPosts = new java.util.concurrent.ConcurrentHashMap<>();

        Images(Context context, Runnable changed) {
            Context application = context.getApplicationContext();
            this.context = application == null ? context : application;
            this.changed = new WeakReference<>(changed);
        }

        Map<String, Bitmap> snapshot(List<HomeSidePanelLayout.Card> cards) {
            if (closed) return java.util.Collections.emptyMap();
            drainFailedPosts();
            Runnable listener = changed.get();
            if (listener != null) STATUS_ICON.request(context, TextStatusBridge.snapshot(), listener);
            Set<String> next = new HashSet<>();
            for (HomeSidePanelLayout.Card card : cards) {
                if (card.type == HomeSidePanelLayout.CardType.IMAGE && card.imageAssetId != null
                        && !card.imageAssetId.isEmpty()) next.add(card.imageAssetId);
            }
            if (!wanted.equals(next)) {
                generation++;
                wanted = next;
                loaded.keySet().retainAll(next);
                attempted.retainAll(next);
                pending.clear();
            }
            for (String id : wanted) {
                if (loaded.containsKey(id) || attempted.contains(id) || !pending.add(id)) continue;
                final long token = generation;
                try {
                    IMAGE_IO.execute(() -> {
                        if (closed) return;
                        Bitmap result;
                        try { result = HomeSidePanelImages.load(context, id); }
                        catch (Throwable ignored) { result = null; }
                        final Bitmap decoded = result;
                        if (!postMain(() -> {
                            if (closed || generation != token || !wanted.contains(id)) return;
                            pending.remove(id);
                            attempted.add(id);
                            if (decoded != null && !decoded.isRecycled()) loaded.put(id, decoded);
                            notifySafely(changed.get());
                        }, 0) && !closed && generation == token) failedPosts.put(id, token);
                    });
                } catch (Throwable ignored) { pending.remove(id); }
            }
            return new ImageResult(loaded, pending());
        }

        /** All assets not yet successfully or unsuccessfully decoded are in-flight, even if IO is queued. */
        Set<String> pending() {
            drainFailedPosts();
            Set<String> unresolved = new HashSet<>(wanted);
            unresolved.removeAll(loaded.keySet());
            unresolved.removeAll(attempted);
            return unresolved;
        }

        private void drainFailedPosts() {
            for (Map.Entry<String, Long> failure : failedPosts.entrySet()) {
                if (!failedPosts.remove(failure.getKey(), failure.getValue())) continue;
                if (!closed && failure.getValue() == generation) {
                    pending.remove(failure.getKey());
                    attempted.add(failure.getKey());
                }
            }
        }

        void close() {
            closed = true;
            Runnable listener = changed.get();
            STATUS_ICON.release(listener);
            generation++;
            loaded.clear(); attempted.clear(); pending.clear(); wanted.clear(); failedPosts.clear();
            changed.clear();
            // Bitmap references may still belong to a saved Compose frame: never recycle or delete here.
        }
    }
}
