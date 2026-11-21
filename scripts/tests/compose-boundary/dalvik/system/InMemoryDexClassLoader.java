package dalvik.system;

import java.nio.ByteBuffer;

/** JVM contract double cannot execute DEX; reaching it is explicitly not runtime acceptance. */
public class InMemoryDexClassLoader extends ClassLoader {
    public static int attempts;
    public InMemoryDexClassLoader(ByteBuffer[] buffers,String libraryPath,ClassLoader parent) {
        super(parent);attempts++;
        throw new UnsupportedOperationException("DEX loading is device-only");
    }
}
