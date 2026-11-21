package android.graphics;

/** Records an effect graph; no shader/GPU behavior is claimed. */
public final class RenderEffect {
    public final String kind;
    public final float blurX, blurY;
    public final RenderEffect first, second;
    public final RuntimeShader shader;
    public final String input;
    public static int blurCreations, failBlurCreations;
    private RenderEffect(String kind, float x, float y, RenderEffect first, RenderEffect second,
                         RuntimeShader shader, String input) {
        this.kind = kind; blurX = x; blurY = y; this.first = first; this.second = second;
        this.shader = shader; this.input = input;
    }
    public static RenderEffect createBlurEffect(float x, float y, Shader.TileMode mode) { return blur(x, y, null); }
    public static RenderEffect createBlurEffect(float x, float y, RenderEffect child, Shader.TileMode mode) { return blur(x, y, child); }
    private static RenderEffect blur(float x, float y, RenderEffect child) {
        blurCreations++;
        if (failBlurCreations > 0) { failBlurCreations--; throw new IllegalStateException("injected blur creation failure"); }
        return new RenderEffect("blur", x, y, child, null, null, null);
    }
    public static RenderEffect createRuntimeShaderEffect(RuntimeShader shader, String input) {
        return new RenderEffect("runtime", 0f, 0f, null, null, shader, input);
    }
    public static RenderEffect createChainEffect(RenderEffect outer, RenderEffect inner) {
        return new RenderEffect("chain", 0f, 0f, outer, inner, null, null);
    }
    public static void reset() { blurCreations = failBlurCreations = 0; RuntimeShader.creations = 0; }
}
