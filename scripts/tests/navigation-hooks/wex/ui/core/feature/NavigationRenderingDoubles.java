package wex.ui.core.feature;

import android.graphics.*;
import android.view.View;

/** Rendering-only doubles. NavigationBarViews, Session, visibility and motion are production code. */
final class ViewBackdropSampler {
    boolean running, capturing;
    int contentChanges;
    View source, content;
    ViewBackdropSampler(View source, View overlay) { this.source = this.content = source; }
    void source(View source, View content) { this.source = source; this.content = content; }
    boolean isCapturing() { return capturing; }
    void changed() { }
    void sourceContentChanged() { if (running && !capturing) contentChanges++; }
    void start() { running = true; }
    void stop() { running = false; }
}
final class LiquidGlassLayer {
    boolean baseReady = true, indicatorReady = true;
    boolean rebuildFailed() { return false; }
    long retryDelay() { return -1; }
    long retryBudget() { return 0; }
    void release() { }
    boolean drawBase(Canvas c, ViewBackdropSampler s, RectF r, float radius, float blur, boolean liquid, boolean dark, float density, boolean backdrop) { return c.isHardwareAccelerated() && baseReady; }
    boolean drawIndicator(Canvas c, RenderNode n, RectF r, float radius, float progress, boolean dark, float density, boolean liquid, boolean sharp, int color) { return liquid && indicatorReady; }
    void drawSnapshot(Canvas canvas) { }
}
final class LiquidGlassDecoration {
    int touches, highlights;
    void release() { }
    void dropShadow(Canvas c, RectF r, float radius, float density, boolean dark) { }
    void highlight(Canvas c, RectF r, float radius, float density, float progress, float x, float y, float angle) { highlights++; }
    void touch(Canvas c, RectF r, float radius, float center, float progress) { touches++; }
    void innerShadow(Canvas c, RectF r, float radius, float density, float progress) { }
}
final class GlassGravity {
    float x,y;
    GlassGravity(View view) { }
    void stop() { }
    void enable(boolean value) { }
}
final class NavigationIcons { static Path create(int index, boolean filled) { return new Path(); } }
final class SystemBarsCoordinator {
    static int remainingBottom(View view) { return 0; }
    static Insets bars(View view) { return Insets.of(0,0,0,0); }
}
