package wex.ui.core.feature;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Managed storage for side-panel image cards under the module's Mdl directory. */
final class HomeSidePanelImages {

    private static final String SUBDIR = "sidebar_images";

    private HomeSidePanelImages() {
    }

    static File directory(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        File dir = new File(new File(base.getParentFile(), "Mdl"), SUBDIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static File file(Context context, String assetId) {
        if (assetId == null || assetId.length() == 0) {
            return null;
        }
        String safe = assetId.replaceAll("[^A-Za-z0-9._-]", "");
        if (safe.length() == 0) {
            return null;
        }
        return new File(directory(context), safe + ".png");
    }

    static Bitmap load(Context context, String assetId) {
        File file = file(context, assetId);
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int sample = 1;
            while (options.outWidth / sample > 1440 || options.outHeight / sample > 1440) {
                sample *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean save(Context context, String assetId, byte[] bytes) {
        File file = file(context, assetId);
        if (file == null || bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            InputStream input = new java.io.ByteArrayInputStream(bytes);
            OutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.close();
            input.close();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void remove(Context context, String assetId) {
        File file = file(context, assetId);
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
