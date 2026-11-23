package wex.ui.core.feature;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView;
import com.tencent.mm.dynamicbackground.view.GradientColorBackgroundView;
import com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer;
import com.tencent.mm.ui.conversation.MainUIView;

/**
 * Host-JVM contract for the chat card material: it must reuse the bottom-bar keys, sample the
 * sibling content through the real sampler, and degrade to the previous solid card on every
 * environment it cannot handle.
 */
public final class ChatGlassSurfaceChecks {
    private static int assertions;

    public static void main(String[] args) {
        RenderNode.reset();
        modeKeys();
        liquidDrawsTheRealBackdrop();
        conversationWallpaperCannotReplaceList();
        plainAndSoftwareFallbacks();
        missingContentFallsBack();
        translucentHostFallsBack();
        releaseIsIdempotent();
        nestedConsumerExcludesItsBranch();
        independentPillLayers();
        explicitConsumerSourceIsExcluded();
        notificationBackdropCoversCard();
        System.out.println("GREEN chat glass surface: " + assertions
                + " assertions (shared keys, backdrop wiring, plain/software/structure fallbacks, idempotent release)");
    }

    private static void explicitConsumerSourceIsExcluded() {
        Tree tree = new Tree();
        tree.glass.backdrop(tree.host, tree.host);
        tree.glass.draw(new Canvas());
        check(tree.host.drawCalls == 0 && tree.consumer.drawCalls == 0 && tree.content.drawCalls == 1,
                "explicit backdrop containing the consumer resolves to its excluded sibling");
        tree.glass.stop();
    }

    private static void notificationBackdropCoversCard() {
        Tree tree = new Tree();
        tree.consumer.y = 146;
        tree.consumer.height = 144;
        tree.content.y = 278;
        tree.content.height = 522;
        tree.glass.setBounds(0, 0, 400, 144);
        tree.glass.backdrop(tree.content, tree.content);
        Canvas opening = new Canvas();
        tree.glass.draw(opening);
        check(opening.renderedNodes.isEmpty() && tree.content.drawCalls == 0,
                "notification opening must not sample a backdrop covering only the bottom of the pill");
        check(opening.rectColors.get(0) == 0xFFF7F7F7,
                "notification opening paints the complete neutral card while the chat is inset");
        tree.content.y = 144;
        tree.content.height = 656;
        Canvas ready = new Canvas();
        tree.glass.draw(ready);
        check(!ready.renderedNodes.isEmpty() && tree.content.drawCalls == 1,
                "notification glass resumes as soon as the chat covers the pill, without a delay");
        tree.content.y = 278;
        Canvas moved = new Canvas();
        tree.glass.draw(moved);
        check(moved.renderedNodes.isEmpty(), "a partial backdrop never replays the previous glass snapshot");
        tree.content.y = 144;
        Canvas recovered = new Canvas();
        tree.glass.draw(recovered);
        check(!recovered.renderedNodes.isEmpty(), "glass recovers after the backdrop returns");
        tree.glass.stop();
    }

    private static void nestedConsumerExcludesItsBranch() {
        Tree tree = new Tree();
        ViewGroup branch = new ViewGroup();
        branch.windowToken = tree.host.windowToken;
        tree.host.children.remove(tree.consumer);
        tree.host.add(branch);
        branch.add(tree.consumer);
        Canvas canvas = new Canvas();
        tree.glass.draw(canvas);
        check(!canvas.renderedNodes.isEmpty(), "nested card samples from the ancestor's sibling");
        check(tree.host.drawCalls == 0 && branch.drawCalls == 0 && tree.consumer.drawCalls == 0,
                "ancestor sampling excludes the complete consumer branch");
        check(tree.content.drawCalls == 1, "nested card records the actual list");
        tree.glass.stop();
    }

    private static void independentPillLayers() {
        Tree tree = new Tree();
        tree.glass.setBounds(0, 0, 60, 60);
        tree.glass.draw(new Canvas());
        tree.glass.setBounds(80, 0, 240, 60);
        tree.glass.draw(new Canvas());
        tree.glass.setBounds(300, 0, 360, 60);
        tree.glass.draw(new Canvas());
        try {
            java.lang.reflect.Field field = ChatGlassSurface.class.getDeclaredField("regions");
            field.setAccessible(true);
            java.util.Map<?, ?> layers = (java.util.Map<?, ?>) field.get(tree.glass);
            check(layers.size() == 3 && new java.util.HashSet<>(layers.values()).size() == 3,
                    "three pills keep independent effect state");
            tree.glass.stop();
            check(layers.isEmpty(), "release clears all pill layers");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void modeKeys() {
        BubbleSettings.values.clear();
        check(ChatGlassSurface.mode() == ChatGlassSurface.LIQUID, "absent setting defaults to liquid glass like the home bar");
        check(ChatGlassSurface.blurRadiusDp(ChatGlassSurface.LIQUID) == 8, "liquid glass default blur is 8dp");
        check(ChatGlassSurface.blurRadiusDp(ChatGlassSurface.FROSTED) == 25, "frosted default blur is 25dp");
        BubbleSettings.values.put(NavigationBarFeature.KEY_EFFECT, ChatGlassSurface.FROSTED);
        BubbleSettings.values.put(NavigationBarFeature.KEY_BLUR, 30);
        check(ChatGlassSurface.mode() == ChatGlassSurface.FROSTED, "card follows nav_bar_glass_mode");
        check(ChatGlassSurface.blurRadiusDp(ChatGlassSurface.FROSTED) == 30, "card follows nav_bar_blur_radius");
        BubbleSettings.values.put(NavigationBarFeature.KEY_EFFECT, 9);
        check(ChatGlassSurface.mode() == ChatGlassSurface.LIQUID, "out-of-range mode clamps instead of asking for a fifth material");
        BubbleSettings.values.clear();
        check(ChatGlassSurface.opacity() == LiquidGlassLayer.AUTO_OPACITY, "absent opacity keeps the material default");
        check(LiquidGlassLayer.tintAlpha(LiquidGlassLayer.AUTO_OPACITY, true, 8f) == 102
                && LiquidGlassLayer.tintAlpha(LiquidGlassLayer.AUTO_OPACITY, true, 0f) == 0
                && LiquidGlassLayer.tintAlpha(LiquidGlassLayer.AUTO_OPACITY, false, 25f) == 166,
                "default tint alphas are unchanged");
        BubbleSettings.values.put(NavigationBarFeature.KEY_OPACITY, 80);
        check(ChatGlassSurface.opacity() == 80, "card follows nav_bar_surface_opacity");
        check(LiquidGlassLayer.tintAlpha(80, true, 0f) == 204 && LiquidGlassLayer.tintAlpha(0, false, 25f) == 0
                && LiquidGlassLayer.tintAlpha(100, true, 8f) == 255, "explicit opacity overrides both materials");
        BubbleSettings.values.put(NavigationBarFeature.KEY_OPACITY, 300);
        check(ChatGlassSurface.opacity() == 100, "out-of-range opacity clamps");
        BubbleSettings.values.clear();
    }

    private static void liquidDrawsTheRealBackdrop() {
        Tree tree = new Tree();
        Canvas canvas = new Canvas();
        tree.glass.draw(canvas);
        check(!canvas.renderedNodes.isEmpty(), "liquid draw publishes a sampled backdrop node");
        check(canvas.operations.contains("clip"), "the material is clipped to its round rect, never a hard square");
        check(canvas.rectColors.get(canvas.rectColors.size() - 1) == (0xF7F7F7 | (102 << 24)),
                "sampled liquid tint is 40% neutral, never the opaque card");
        check(tree.content.drawCalls == 1, "the sibling list is recorded exactly once for the first frame");
        check(tree.host.drawCalls == 0, "the card's parent is never recorded wholesale, so the card cannot capture itself");
        Outline outline = new Outline();
        tree.glass.getOutline(outline);
        check(outline.kind == Outline.ROUND_RECT && outline.radius == 24f && outline.rect.width() == 400,
                "the material reports the card's rounded outline, never a square shadow");
        Canvas next = new Canvas();
        tree.glass.draw(next);
        check(tree.content.drawCalls == 1, "an unchanged frame reuses the recorded backdrop instead of re-recording");
        check(!next.renderedNodes.isEmpty(), "the reused frame still draws the material");
        tree.glass.stop();
    }

    private static void conversationWallpaperCannotReplaceList() {
        MainUIView host = new MainUIView();
        host.height = 800;
        host.background = new Drawable();
        DynamicBgContainer wallpaper = new DynamicBgContainer();
        wallpaper.height = 800;
        wallpaper.add(new DynamicBackgroundGLSurfaceView());
        host.add(wallpaper);
        GradientColorBackgroundView gradient = new GradientColorBackgroundView();
        gradient.height = 800;
        host.add(gradient);
        View list = new View();
        list.height = 600;
        host.add(list);
        View card = new View();
        card.height = 60;
        host.add(card);
        ChatGlassSurface glass = new ChatGlassSurface(card);
        glass.setBounds(0, 0, 280, 60);
        glass.configure(0xFFF7F7F7, 24f, 1f, false, ChatGlassSurface.LIQUID, 8);
        Canvas canvas = new Canvas();
        glass.draw(canvas);
        check(!canvas.renderedNodes.isEmpty() && list.drawCalls == 1 && wallpaper.drawCalls == 0
                        && gradient.drawCalls == 0,
                "group card samples the list even when GL and gradient pull backgrounds precede it");
        glass.stop();
    }

    private static void plainAndSoftwareFallbacks() {
        Tree plain = new Tree();
        plain.glass.configure(0xFF252825, 24f, 1f, true, ChatGlassSurface.PLAIN, 0);
        Canvas plainCanvas = new Canvas();
        plain.glass.draw(plainCanvas);
        check(plainCanvas.renderedNodes.isEmpty(), "plain mode never samples a backdrop");
        check(plainCanvas.rectColors.get(0) == 0xFF252825 && !plainCanvas.operations.contains("clip"),
                "plain mode paints exactly the previous solid card");
        plain.glass.stop();

        Tree software = new Tree();
        Canvas softwareCanvas = new Canvas();
        softwareCanvas.hardwareAccelerated = false;
        software.glass.draw(softwareCanvas);
        check(softwareCanvas.renderedNodes.isEmpty() && software.content.drawCalls == 0,
                "a software canvas degrades to the solid card without sampling");
        software.glass.stop();
    }

    private static void missingContentFallsBack() {
        ViewGroup host = new ViewGroup();
        host.windowToken = new Object();
        host.width = 400;
        host.height = 800;
        View only = new View();
        only.width = 400;
        only.height = 60;
        only.y = 700;
        host.add(only);
        ChatGlassSurface glass = new ChatGlassSurface(only);
        glass.setBounds(0, 0, 400, 60);
        glass.configure(0xFFF7F7F7, 24f, 1f, false, ChatGlassSurface.LIQUID, 8);
        Canvas canvas = new Canvas();
        glass.draw(canvas);
        check(canvas.renderedNodes.isEmpty() && canvas.rectColors.size() == 1,
                "a card with no sibling content still paints the solid card, never an empty shell");
        glass.stop();
    }

    private static void translucentHostFallsBack() {
        Tree tree = new Tree();
        tree.host.alpha = 0.5f;
        Canvas canvas = new Canvas();
        tree.glass.draw(canvas);
        check(canvas.renderedNodes.isEmpty() && tree.content.drawCalls == 0,
                "a host the sampler refuses degrades to the solid card instead of a half transparent card"
                        + " (nodes=" + canvas.renderedNodes.size() + " contentDraws=" + tree.content.drawCalls
                        + " rects=" + canvas.rectColors.size() + ")");
        check(canvas.rectColors.get(0) == 0xFFF7F7F7, "the degraded card keeps the configured tint");
        tree.glass.stop();
    }

    private static void releaseIsIdempotent() {
        Tree tree = new Tree();
        tree.glass.draw(new Canvas());
        tree.glass.stop();
        tree.glass.stop();
        Canvas canvas = new Canvas();
        tree.glass.draw(canvas);
        check(!canvas.renderedNodes.isEmpty(), "a stopped surface rebinds when the card draws again");
        tree.glass.stop();
        check(true, "double release is a no-op");
    }

    /** Host = the card's parent, content = the largest sibling, exactly the shipping shape. */
    private static final class Tree {
        final ViewGroup host = new ViewGroup();
        final View content = new View();
        final View consumer = new View();
        final ChatGlassSurface glass;

        Tree() {
            host.windowToken = new Object();
            host.width = 400;
            host.height = 800;
            host.background = new Drawable();
            content.width = 400;
            content.height = 800;
            host.add(content);
            consumer.width = 400;
            consumer.height = 60;
            consumer.y = 700;
            host.add(consumer);
            glass = new ChatGlassSurface(consumer);
            glass.setBounds(0, 0, 400, 60);
            glass.configure(0xFFF7F7F7, 24f, 1f, false, ChatGlassSurface.LIQUID, 8);
        }
    }

    private static void check(boolean valid, String message) {
        assertions++;
        if (!valid) throw new AssertionError(message);
    }
}
