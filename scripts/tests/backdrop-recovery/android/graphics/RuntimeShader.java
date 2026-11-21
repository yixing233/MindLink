package android.graphics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Captures uniforms, but does not compile or execute AGSL. */
public final class RuntimeShader extends Shader {
    public final String source;
    public final Map<String, float[]> uniforms = new LinkedHashMap<>();
    public static int creations;
    public RuntimeShader(String source) { this.source = source; creations++; }
    public void setFloatUniform(String name, float... values) { uniforms.put(name, values.clone()); }
}
