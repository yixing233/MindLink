package wex.ui.core.feature;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.SystemClock;

/** The background is blurred before foreground recording; the indicator only refracts that combined image. */
final class LiquidGlassLayer {
    private final RenderNode node = new RenderNode("MindLink-liquid-background");
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Rect region = new Rect();
    private final RectF lastRect = new RectF(), effectRect = new RectF();
    private RuntimeShader lens, dispersion, colors, unpremultiply;
    private long revision = Long.MIN_VALUE;
    private int offsetX, offsetY, surfaceColor;
    private float lastRadius = -1f, effectBlur = -1f, effectRadius = -1f, effectDensity = -1f;
    private boolean effectLiquid, effectValid, drawn, warned, rebuildFailed;
    private final RectF retryRect = new RectF();
    private ViewBackdropSampler retrySampler;
    private long retryRevision, retryAt, retryBudget;
    private int retryFailures, retryOffsetX, retryOffsetY;
    private float retryRadius, retryBlur, retryDensity;
    private boolean retryLiquid;

    /** Distinguishes layer rebuild failure/backoff from a sampler-not-ready fallback. */
    boolean rebuildFailed() { return rebuildFailed; }
    long retryBudget() { return retryBudget; }
    long retryDelay() {
        return retryFailures == 0 || retryFailures >= 3 ? -1L
                : Math.max(0L, retryAt - SystemClock.uptimeMillis());
    }

    boolean drawBase(Canvas canvas, ViewBackdropSampler sampler, RectF rect, float radius,
                     float blur, boolean liquid, boolean dark, float density, boolean useBackdrop) {
        float lensAmount = liquid && blur > 0f ? 24f * density : 0f;
        float padding = Math.max(1f, Math.max(blur * 3f, lensAmount));
        bounds(rect, padding);
        boolean sampled = useBackdrop && canvas.isHardwareAccelerated() && sampler.prepare(canvas, region);
        rebuildFailed = false;
        if (sampled) {
            boolean newInput = retrySampler != sampler || retryRevision != sampler.revision()
                    || retryOffsetX != sampler.offsetX() || retryOffsetY != sampler.offsetY()
                    || !retryRect.equals(rect) || retryRadius != radius || retryBlur != blur
                    || retryLiquid != liquid || retryDensity != density;
            if (newInput) {
                // A successful source/parameter revision can replenish exhausted rebuilds,
                // but repeated input changes cannot bypass an outstanding backoff.
                if (retryFailures >= 3) { retryFailures = 0; retryBudget++; }
                retrySampler = sampler; retryRevision = sampler.revision();
                retryOffsetX = sampler.offsetX(); retryOffsetY = sampler.offsetY();
                retryRect.set(rect); retryRadius = radius; retryBlur = blur;
                retryLiquid = liquid; retryDensity = density;
            }
            boolean changed = !effectValid || !effectRect.equals(rect) || effectBlur != blur
                    || effectRadius != radius || effectLiquid != liquid || effectDensity != density;
            if (changed || !drawn || revision != sampler.revision() || offsetX != sampler.offsetX()
                    || offsetY != sampler.offsetY() || !node.hasDisplayList()) {
                if (retryFailures >= 3 || retryAt > SystemClock.uptimeMillis()) {
                    sampled = false; rebuildFailed = true;
                } else try {
                    record(sampler, null);
                    if (!node.hasDisplayList()) throw new IllegalStateException("EmptyDisplayList");
                    if (changed || !drawn) {
                        node.setRenderEffect(effect(rect, radius, blur, lensAmount, lensAmount, false, 0f, liquid));
                        effectRect.set(rect); effectBlur = blur; effectRadius = radius;
                        effectLiquid = liquid; effectDensity = density; effectValid = true;
                    }
                    revision = sampler.revision(); offsetX = sampler.offsetX(); offsetY = sampler.offsetY();
                    if (retryFailures != 0 || retryAt != 0L) retryBudget++;
                    retryFailures = 0; retryAt = 0L;
                } catch (Throwable error) {
                    sampled = false; rebuildFailed = true;
                    retryFailures++; retryBudget++;
                    retryAt = SystemClock.uptimeMillis() + (retryFailures == 1 ? 80L : 240L);
                    failed(error);
                }
            }
        }
        drawn = sampled;
        lastRect.set(rect); lastRadius = radius;
        int alpha = sampled ? (liquid ? (blur > 0f ? 102 : 0) : 166) : 255;
        surfaceColor = ((dark ? 0x191919 : 0xF7F7F7) & 0xFFFFFF) | alpha << 24;
        drawSnapshot(canvas);
        return sampled;
    }

    void drawSnapshot(Canvas canvas) {
        clip.reset(); clip.addRoundRect(lastRect, lastRadius, lastRadius, Path.Direction.CW);
        int save = canvas.save(); canvas.clipPath(clip);
        if (drawn && node.hasDisplayList()) canvas.drawRenderNode(node);
        tint.setColor(surfaceColor); canvas.drawRect(lastRect, tint);
        canvas.restoreToCount(save);
    }

    boolean drawIndicator(Canvas canvas, RenderNode combined, RectF rect, float radius, float progress,
                          boolean dark, float density, boolean liquid, boolean transparent, int accent) {
        if (!liquid || combined == null || !combined.hasDisplayList() || !canvas.isHardwareAccelerated()) {
            tint.setColor((accent & 0xFFFFFF) | 0x26000000);
            canvas.drawRoundRect(rect, radius, radius, tint);
            return false;
        }
        float height = transparent ? 0f : 10f * density * progress;
        float amount = transparent ? 0f : 14f * density * progress;
        bounds(rect, Math.max(1f, amount));
        try {
            record(null, combined);
            node.setRenderEffect(effect(rect, radius, 0f, height, amount, true, 0.5f, false));
            clip.reset(); clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            int save = canvas.save(); canvas.clipPath(clip);
            canvas.drawRenderNode(node);
            if (!transparent) {
                tint.setColor((dark ? 0xFFFFFF : 0) | Math.round(255f * 0.1f * (1f - progress)) << 24);
                canvas.drawRect(rect, tint);
                tint.setColor(Math.round(255f * 0.03f * progress) << 24); canvas.drawRect(rect, tint);
            }
            canvas.restoreToCount(save);
            return true;
        } catch (Throwable error) { failed(error); return false; }
    }

    private void bounds(RectF rect, float padding) {
        region.set((int) Math.floor(rect.left - padding), (int) Math.floor(rect.top - padding),
                (int) Math.ceil(rect.right + padding), (int) Math.ceil(rect.bottom + padding));
    }

    private void record(ViewBackdropSampler sampler, RenderNode combined) {
        node.setPosition(region.left, region.top, region.right, region.bottom);
        RecordingCanvas recording = node.beginRecording(region.width(), region.height());
        try {
            recording.translate(-region.left, -region.top);
            if (sampler != null) sampler.draw(recording); else recording.drawRenderNode(combined);
        } finally { node.endRecording(); }
    }

    private RenderEffect effect(RectF rect, float radius, float blur, float height, float amount,
                                boolean depth, float aberration, boolean vibrancy) {
        RenderEffect effect = null;
        if (vibrancy && blur > 0f) {
            if (colors == null) colors = new RuntimeShader(LiquidGlassShaders.COLOR_CONTROLS);
            colors.setFloatUniform("in_brightness", 0f); colors.setFloatUniform("in_contrast", 1f);
            colors.setFloatUniform("in_saturation", 1.5f);
            effect = RenderEffect.createRuntimeShaderEffect(colors, "child");
        }
        if (blur > 0f) {
            effect = effect == null ? RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
                    : RenderEffect.createBlurEffect(blur, blur, effect, Shader.TileMode.CLAMP);
            if (unpremultiply == null) unpremultiply = new RuntimeShader(LiquidGlassShaders.UNPREMULTIPLY);
            effect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(unpremultiply, "child"), effect);
        }
        if (height > 0f && amount > 0f) {
            RuntimeShader shader;
            if (aberration > 0f) {
                if (dispersion == null) dispersion = new RuntimeShader(LiquidGlassShaders.LENS_DISPERSION);
                shader = dispersion; shader.setFloatUniform("chromaticAberration", aberration);
            } else {
                if (lens == null) lens = new RuntimeShader(LiquidGlassShaders.LENS);
                shader = lens;
            }
            shader.setFloatUniform("size", rect.width(), rect.height());
            shader.setFloatUniform("offset", region.left - rect.left, region.top - rect.top);
            shader.setFloatUniform("cornerRadii", radius, radius, radius, radius);
            shader.setFloatUniform("refractionHeight", height); shader.setFloatUniform("refractionAmount", -amount);
            shader.setFloatUniform("depthEffect", depth ? 1f : 0f);
            RenderEffect refraction = RenderEffect.createRuntimeShaderEffect(shader, "content");
            effect = effect == null ? refraction : RenderEffect.createChainEffect(refraction, effect);
        }
        return effect;
    }

    private void failed(Throwable error) {
        node.discardDisplayList(); drawn = false; effectValid = false; revision = Long.MIN_VALUE;
        if (!warned) { warned = true; NavigationBarFeature.log("玻璃渲染失败，未使用伪玻璃 " + error.getClass().getSimpleName()); }
    }

    void release() {
        node.discardDisplayList(); revision = Long.MIN_VALUE; drawn = false; effectValid = false;
        retryFailures = 0; retryAt = 0L; retrySampler = null; rebuildFailed = false; retryBudget++;
    }
}
