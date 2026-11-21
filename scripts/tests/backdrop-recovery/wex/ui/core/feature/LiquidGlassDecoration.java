package wex.ui.core.feature;
import android.graphics.Canvas;
import android.graphics.RectF;
/**
 * Recording double for the bloom/shadow decoration: the real class is GPU-only path code that
 * this suite never rasterizes, but the card material must ask for exactly one highlight per
 * sampled liquid frame and must release it when it stops.
 */
final class LiquidGlassDecoration {
    int highlights, shadows, releases;
    float lastAlpha;
    @SuppressWarnings("unused")
    void dropShadow(Canvas canvas, RectF rect, float radius, float density, boolean dark) { shadows++; }
    void highlight(Canvas canvas, RectF rect, float radius, float density, float alpha,
                   float gravityX, float gravityY, float extraDegrees) {
        highlights++;
        lastAlpha = alpha;
    }
    void touch(Canvas canvas, RectF rect, float radius, float position, float progress) { }
    void innerShadow(Canvas canvas, RectF rect, float corner, float density, float progress) { }
    void release() { releases++; }
}
