// Copyright 2026 compose-miuix-ui contributors. SPDX-License-Identifier: Apache-2.0
// Adapted from WeKit FloatingBottomBar / miuix BloomStroke / AndroidLiquidGlass InnerShadow.
package wex.ui.core.feature;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

final class LiquidGlassDecoration {
    private static final String TOUCH_HIGHLIGHT =
            "layout(color) uniform half4 color; uniform float radius; uniform float2 position;\n"
            + "half4 main(float2 coord) { float dist = distance(coord, position);\n"
            + "return color * smoothstep(radius, radius * 0.5, dist); }\n";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RenderNode shadow = new RenderNode("MindLink-glass-inner-shadow");
    private final RenderNode outer = new RenderNode("MindLink-glass-drop-shadow");
    private final RectF lastOuter = new RectF();
    private RuntimeShader bloom, touch;
    private boolean unavailable, darkShadow;
    private float shadowDensity;

    LiquidGlassDecoration() { clear.setBlendMode(BlendMode.CLEAR); }

    void dropShadow(Canvas canvas, RectF rect, float radius, float density, boolean dark) {
        if (!canvas.isHardwareAccelerated()) return;
        try {
        if (!lastOuter.equals(rect) || shadowDensity != density || darkShadow != dark || !outer.hasDisplayList()) {
            float padding = 30f * density;
            int left = (int) Math.floor(rect.left - padding), top = (int) Math.floor(rect.top - padding);
            int right = (int) Math.ceil(rect.right + padding), bottom = (int) Math.ceil(rect.bottom + padding);
            outer.setPosition(left, top, right, bottom);
            RecordingCanvas recording = outer.beginRecording(right - left, bottom - top);
            try {
                recording.translate(-left, -top);
                paint.setBlendMode(BlendMode.SRC_OVER); paint.setShader(null); paint.setColor(dark ? 0x33000000 : 0x1A000000);
                recording.drawRoundRect(rect, radius, radius, paint);
            } finally { outer.endRecording(); }
            outer.setRenderEffect(RenderEffect.createBlurEffect(10f * density, 10f * density, Shader.TileMode.DECAL));
            lastOuter.set(rect); shadowDensity = density; darkShadow = dark;
        }
        canvas.drawRenderNode(outer);
        } catch (Throwable error) {
            outer.discardDisplayList();
            if (!unavailable) NavigationBarFeature.log("玻璃阴影不可用 " + error.getClass().getSimpleName());
        }
    }

    void highlight(Canvas canvas, RectF rect, float radius, float density, float alpha,
                   float gravityX, float gravityY, float extraDegrees) {
        if (unavailable || alpha <= 0f || !canvas.isHardwareAccelerated()) return;
        try {
            if (bloom == null) bloom = new RuntimeShader(LiquidGlassShaders.BLOOM_DUAL);
            float halfW = rect.width() / 2f, halfH = rect.height() / 2f;
            bloom.setFloatUniform("halfView", halfW, halfH);
            bloom.setFloatUniform("halfViewFloor", (float) Math.floor(halfW), (float) Math.floor(halfH));
            bloom.setFloatUniform("cornerRadii", radius, radius, radius, radius);
            bloom.setFloatUniform("strokeWidth", Math.min(density, Math.min(halfW, halfH)));
            bloom.setFloatUniform("innerBlurRadius", density * 2f);
            bloom.setFloatUniform("innerBlurRadiusSq", density * density * 4f);
            bloom.setFloatUniform("highlightAlpha", alpha);
            bloom.setColorUniform("strokeColor", Color.WHITE);
            bloom.setFloatUniform("strokeAlphaMul", 0.12f);
            float square = gravityX * gravityX + gravityY * gravityY;
            float x = 0f, y = -1f;
            if (square > 0.01f) { float inverse = 1f / (float) Math.sqrt(square); x = gravityX * inverse; y = gravityY * inverse; }
            double radians = Math.toRadians(extraDegrees);
            float lx = (float) Math.cos(radians) * x - (float) Math.sin(radians) * y;
            float ly = (float) Math.sin(radians) * x + (float) Math.cos(radians) * y;
            light(bloom, "1", lx, ly, -0.05f, 1f);
            light(bloom, "2", 0f, 0.1f, -0.5f, 0.4f);
            paint.setColor(Color.WHITE); paint.setShader(bloom); paint.setBlendMode(BlendMode.PLUS);
            int save = canvas.save(); canvas.translate(rect.left, rect.top);
            canvas.drawRect(0f, 0f, rect.width(), rect.height(), paint); canvas.restoreToCount(save);
        } catch (Throwable error) {
            unavailable = true;
            NavigationBarFeature.log("双光源高光不可用 " + error.getClass().getSimpleName());
        } finally { paint.setShader(null); paint.setBlendMode(BlendMode.SRC_OVER); }
    }

    private static void light(RuntimeShader shader, String suffix, float x, float y, float z, float intensity) {
        float length = Math.max(0.000001f, (float) Math.sqrt(x * x + y * y + z * z));
        shader.setFloatUniform("lightDir" + suffix, x / length, y / length, z / length);
        shader.setColorUniform("lightColor" + suffix, Color.WHITE);
        shader.setFloatUniform("lightIntensity" + suffix, intensity);
    }

    void touch(Canvas canvas, RectF rect, float radius, float position, float progress) {
        if (unavailable || progress <= 0f || !canvas.isHardwareAccelerated()) return;
        int save = canvas.save();
        try {
            clip.reset(); clip.addRoundRect(rect, radius, radius, Path.Direction.CW); canvas.clipPath(clip);
            paint.setBlendMode(BlendMode.PLUS); paint.setColor(Color.argb(Math.round(255f * 0.06f * progress), 255, 255, 255));
            canvas.drawRect(rect, paint);
            if (touch == null) touch = new RuntimeShader(TOUCH_HIGHLIGHT);
            touch.setColorUniform("color", Color.argb(Math.round(255f * 0.12f * progress), 255, 255, 255));
            touch.setFloatUniform("radius", Math.min(rect.width(), rect.height()) * 1.2f);
            touch.setFloatUniform("position", Math.max(rect.left, Math.min(rect.right, position)), rect.centerY());
            paint.setShader(touch); paint.setColor(Color.WHITE); canvas.drawRect(rect, paint);
        } catch (Throwable error) {
            unavailable = true; NavigationBarFeature.log("交互高光不可用 " + error.getClass().getSimpleName());
        } finally { canvas.restoreToCount(save); paint.setShader(null); paint.setBlendMode(BlendMode.SRC_OVER); }
    }

    void innerShadow(Canvas canvas, RectF rect, float corner, float density, float progress) {
        if (progress <= 0.001f || !canvas.isHardwareAccelerated()) return;
        try {
        float blur = 8f * density * progress;
        int width = Math.max(1, (int) Math.ceil(rect.width())), height = Math.max(1, (int) Math.ceil(rect.height()));
        shadow.setPosition(0, 0, width, height);
        shadow.setUseCompositingLayer(true, null);
        RecordingCanvas recording = shadow.beginRecording(width, height);
        clip.reset(); clip.addRoundRect(0f, 0f, rect.width(), rect.height(), corner, corner, Path.Direction.CW);
        try {
            recording.clipPath(clip); paint.setColor(0x26000000); paint.setShader(null); paint.setBlendMode(BlendMode.SRC_OVER);
            recording.drawRoundRect(0f, 0f, rect.width(), rect.height(), corner, corner, paint);
            recording.translate(0f, blur);
            recording.drawRoundRect(0f, 0f, rect.width(), rect.height(), corner, corner, clear);
        } finally { shadow.endRecording(); }
        shadow.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL));
        shadow.setAlpha(progress);
        int save = canvas.save(); canvas.translate(rect.left, rect.top); canvas.clipPath(clip);
        canvas.drawRenderNode(shadow); canvas.restoreToCount(save);
        } catch (Throwable error) {
            shadow.discardDisplayList();
            if (!unavailable) NavigationBarFeature.log("玻璃内阴影不可用 " + error.getClass().getSimpleName());
        }
    }

    void release() { shadow.discardDisplayList(); outer.discardDisplayList(); }
}
