package wex.ui.core.feature;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

/**
 * HyperOS-style progressive blur across the top of a first-level page: the content scrolling up
 * under the status bar and the floating cards is blurred at the top and fades to sharp below.
 *
 * <p>Recipe ported from HyperChanger's {@code ProgressiveBlurLayer} (ColdP/HyperChanger,
 * Apache-2.0): one fixed blur over the backdrop, an AGSL alpha mask that keeps the upper half
 * fully blurred and fades to clear across the lower half, and a surface tint gradient over it.
 * The band reaches {@link #HEIGHT_FACTOR} times the status bar + action bar height, so the
 * fade happens below the bar instead of ending on a hard edge at its bottom.</p>
 *
 * <p>The backdrop comes from the same {@link ViewBackdropSampler} the card glass uses (the
 * page beside the action bar, never the bar itself), so the page is recorded once per change.
 * Without a usable backdrop only the tint gradient is drawn: never a hard opaque block.</p>
 */
final class ProgressiveTopBlur {
    /**
     * Band height over the status bar + action bar height. HyperChanger uses 1.4926; on the
     * WeChat home pages that reached far into the first rows, so the band is kept shorter.
     */
    static final float HEIGHT_FACTOR = 1.18f;
    static final float BLUR_DP = 10f;
    /** Tint stops (alpha over the page surface colour) at 0, {@link #TINT_MID_AT} and 1. */
    static final float TINT_TOP = 0.92f, TINT_MID = 0.18f, TINT_MID_AT = 0.72f;
    /** HyperChanger PROGRESSIVE_BLUR_ALPHA_MASK_SHADER. */
    static final String MASK = "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "half4 main(float2 coord) {\n"
            + "    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);\n"
            + "    return content.eval(coord) * blurAlpha;\n"
            + "}\n";

    private final RenderNode node = new RenderNode("MindLink-progressive-top");
    private final Rect region = new Rect();
    private final Paint tint = new Paint();
    private RuntimeShader mask;
    private LinearGradient gradient;
    private int effectWidth = -1, effectHeight = -1, gradientTop, gradientBottom, gradientColor;
    private float effectBlur = -1f;
    private boolean failed, logged;

    /**
     * Draws the band in the caller's coordinates from {@code top} (the window top) to
     * {@code bottom}. {@code alpha} fades the whole band (the host moving the bar on a pull).
     */
    void draw(Canvas canvas, ViewBackdropSampler sampler, int width, int top, int bottom,
              float density, boolean dark, float alpha) {
        draw(canvas, sampler, 0, width, top, bottom, density, dark, alpha);
    }

    void draw(Canvas canvas, ViewBackdropSampler sampler, int left, int width, int top, int bottom,
              float density, boolean dark, float alpha) {
        if (width <= 0 || bottom <= top || alpha <= 0f) return;
        region.set(left, top, left + width, bottom);
        int w = region.width(), h = region.height();
        boolean sampled = sampler != null && !failed && canvas.isHardwareAccelerated()
                && sampler.prepare(canvas, region);
        if (sampled) {
            try {
                node.setPosition(region.left, region.top, region.right, region.bottom);
                RecordingCanvas recording = node.beginRecording(w, h);
                try {
                    recording.translate(-region.left, -region.top);
                    sampler.draw(recording);
                } finally { node.endRecording(); }
                float blur = BLUR_DP * density;
                if (effectWidth != w || effectHeight != h || effectBlur != blur) {
                    if (mask == null) mask = new RuntimeShader(MASK);
                    mask.setFloatUniform("size", w, h);
                    // Chain: blur first (inner), then the alpha mask over the blurred image.
                    node.setRenderEffect(RenderEffect.createChainEffect(
                            RenderEffect.createRuntimeShaderEffect(mask, "content"),
                            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)));
                    effectWidth = w; effectHeight = h; effectBlur = blur;
                }
                node.setAlpha(alpha);
                canvas.drawRenderNode(node);
                if (!logged) { logged = true; log("已启用 band=" + h + " blur=" + Math.round(blur)); }
            } catch (Throwable error) {
                // A device without AGSL effects keeps the tint only; never retried every frame.
                failed = true;
                node.discardDisplayList();
                log("渐进模糊不可用，仅保留渐变着色 " + error.getClass().getSimpleName());
            }
        }
        int color = (dark ? 0x191919 : 0xF7F7F7);
        if (gradient == null || gradientTop != top || gradientBottom != bottom || gradientColor != color) {
            gradient = new LinearGradient(0f, top, 0f, bottom,
                    new int[]{color | alpha(TINT_TOP) << 24, color | alpha(TINT_MID) << 24, color},
                    new float[]{0f, TINT_MID_AT, 1f}, Shader.TileMode.CLAMP);
            gradientTop = top; gradientBottom = bottom; gradientColor = color;
        }
        tint.setShader(gradient);
        tint.setAlpha(Math.round(255f * Math.min(1f, alpha)));
        canvas.drawRect(region, tint);
    }

    private static int alpha(float value) { return Math.round(255f * value); }

    void release() {
        node.discardDisplayList();
        effectWidth = effectHeight = -1; effectBlur = -1f;
    }

    private static void log(String message) {
        try { dev.mindlink.xposed.XposedBridge.log("WEX: 首页顶栏卡片: 渐进模糊 " + message); }
        catch (Throwable ignored) { }
    }
}
