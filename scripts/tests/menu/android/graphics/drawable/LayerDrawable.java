package android.graphics.drawable;

public class LayerDrawable extends Drawable {
    public final Drawable[] layers;

    public LayerDrawable(Drawable[] layers) { this.layers = layers; }

    @Override
    public boolean isStateful() {
        for (Drawable layer : layers) if (layer.isStateful()) return true;
        return false;
    }
}
