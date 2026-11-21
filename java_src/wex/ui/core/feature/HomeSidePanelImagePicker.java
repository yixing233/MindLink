package wex.ui.core.feature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexCallback;

/** Imports a system-document result directly into side-panel managed storage. */
final class HomeSidePanelImagePicker {
    private static final int REQUEST = 0x7e17;
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static WexCallback pending;
    private static boolean initialized;

    private HomeSidePanelImagePicker() { }

    static synchronized void init() {
        if (initialized) return;
        XposedBridge.hookAllMethods(Activity.class, "onActivityResult", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity) || param.args.length < 3
                        || !(param.args[0] instanceof Integer) || (Integer) param.args[0] != REQUEST) return;
                Activity activity = (Activity) param.thisObject;
                WexCallback callback;
                synchronized (HomeSidePanelImagePicker.class) {
                    if (owner.get() != activity) return;
                    callback = pending; pending = null; owner.clear();
                }
                String asset = null;
                if ((Integer) param.args[1] == Activity.RESULT_OK && param.args[2] instanceof Intent) {
                    asset = importUri(activity, ((Intent) param.args[2]).getData());
                }
                if (callback != null) callback.invoke(asset);
            }
        });
        initialized = true;
    }

    static synchronized void pick(Activity activity, WexCallback callback) {
        if (activity == null || callback == null || pending != null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("image/*");
            owner = new WeakReference<>(activity); pending = callback;
            activity.startActivityForResult(intent, REQUEST);
        } catch (Throwable error) { pending = null; owner.clear(); callback.invoke(null); }
    }

    private static String importUri(Activity activity, Uri uri) {
        if (uri == null) return null;
        String asset = UUID.randomUUID().toString();
        File target = HomeSidePanelImages.file(activity, asset), partial = new File(target.getParentFile(), "." + asset + ".part");
        long total = 0;
        try (InputStream input = activity.getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(partial)) {
            if (input == null) return null;
            byte[] buffer = new byte[16384]; int read;
            while ((read = input.read(buffer)) != -1) { total += read; if (total > 50L * 1024L * 1024L) return null; output.write(buffer, 0, read); }
        } catch (Throwable error) { partial.delete(); return null; }
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(partial.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 50_000_000L
                || (double) Math.max(bounds.outWidth, bounds.outHeight) / Math.min(bounds.outWidth, bounds.outHeight) > 12d) {
            partial.delete(); return null;
        }
        if (!partial.renameTo(target)) { partial.delete(); return null; }
        return asset;
    }
}
