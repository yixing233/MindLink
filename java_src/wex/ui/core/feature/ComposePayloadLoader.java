package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dalvik.system.InMemoryDexClassLoader;

/** Reads only the installed module's hash-indexed payloads; the parent is the Android boot runtime. */
final class ComposePayloadLoader {
    private static final Map<String, ClassLoader> LOADERS = new HashMap<>();

    private ComposePayloadLoader() { }

    static synchronized Class<?> entry(Context packaged, String prefix, String entryName)
            throws ReflectiveOperationException, IOException {
        if (!"dev.mindlink".equals(packaged.getPackageName()))
            throw new IllegalStateException("Compose payload must belong to the installed module");
        if (!("compose-payload".equals(prefix) && "dev.mindlink.compose.DesktopUi".equals(entryName))
                && !("sidepanel-compose-payload".equals(prefix) && "dev.mindlink.compose.DesktopUi".equals(entryName))
                && !("sidepanel-compose-payload".equals(prefix) && "dev.mindlink.compose.SidePanelUi".equals(entryName))
                && !("sidepanel-compose-payload".equals(prefix) && "dev.mindlink.compose.SettingsUi".equals(entryName)))
            throw new IllegalStateException("Unknown packaged Compose entry");
        ClassLoader loader = LOADERS.get(prefix);
        if (loader == null) {
            byte[] manifest = read(packaged, prefix + "/index.txt", 16 * 1024);
            List<ByteBuffer> dex = new ArrayList<>();
            int total = 0;
            for (String line : new String(manifest, StandardCharsets.UTF_8).split("\\n")) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split(" ");
                if (parts.length != 2 || !parts[0].matches("classes[0-9]*\\.bin")
                        || !parts[1].matches("[a-f0-9]{64}"))
                    throw new IllegalStateException("Invalid packaged payload manifest");
                byte[] bytes = read(packaged, prefix + "/" + parts[0], 64 * 1024 * 1024);
                total += bytes.length;
                if (total > 128 * 1024 * 1024 || dex.size() >= 16)
                    throw new IllegalStateException("Packaged payload exceeds limit");
                if (bytes.length < 112 || bytes[0] != 'd' || bytes[1] != 'e' || bytes[2] != 'x'
                        || !parts[1].equals(hash(bytes)))
                    throw new IllegalStateException("Packaged Compose payload integrity failed");
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.put(bytes);
                ((java.nio.Buffer) buffer).flip();
                dex.add(buffer);
            }
            if (dex.isEmpty()) throw new IllegalStateException("Compose payload missing");
            loader = new InMemoryDexClassLoader(dex.toArray(new ByteBuffer[0]),
                    packaged.getApplicationInfo().nativeLibraryDir, Activity.class.getClassLoader());
            Class<?> next = loader.loadClass(entryName);
            if (next.getClassLoader() != loader
                    || loader.loadClass("androidx.compose.runtime.Composer").getClassLoader() != loader
                    || loader.loadClass("kotlin.Unit").getClassLoader() != loader)
                throw new IllegalStateException("Compose runtime isolation failed");
            LOADERS.put(prefix, loader);
            return next;
        }
        Class<?> next = loader.loadClass(entryName);
        if (next.getClassLoader() != loader) throw new IllegalStateException("Compose entry isolation failed");
        return next;
    }

    private static byte[] read(Context packaged, String path, int maximum) throws IOException {
        try (InputStream input = packaged.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maximum) throw new IOException("Packaged asset exceeds limit");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest) out.append(String.format(Locale.ROOT, "%02x", value & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
