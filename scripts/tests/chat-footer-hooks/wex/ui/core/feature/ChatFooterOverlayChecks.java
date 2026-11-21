package wex.ui.core.feature;

import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.tencent.mm.pluginsdk.ui.chat.AppPanel;
import com.tencent.mm.pluginsdk.ui.chat.ChatFooter;
import com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.h3;

/** Deterministic replay of the phone's stable overlay/More/scroll-space contract. */
public final class ChatFooterOverlayChecks {
    private static int assertions;
    public static void main(String[] args) throws Throwable {
        geometryAndRestoration();
        decorationLifecycle();
        listReplacementReleasesSpace();
        nativeVisibilityAndSmallWindow();
        movedFooterDoesNotRestoreOldLift();
        deepInputAndGestureBand();
        sourceEntry();
        nativePanelOrderStillOverlays();
        messagesFollowVisibleArea();
        resizedImeDoesNotReserveTwice();
        linearPanelExpansion();
        resizedWindowKeepsAnimationOrigin();
        System.out.println("GREEN phone footer integration: " + assertions + " assertions");
    }

    private static void linearPanelExpansion() {
        Tree tree = new Tree(); RecyclerView list = messageList(tree);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        settleMessages(tree, list, overlay, 0, 874, 356);
        android.animation.ValueAnimator.enabled = true;
        settleMessages(tree, list, overlay, 2, 518, 356);
        android.animation.ValueAnimator animation = android.animation.ValueAnimator.last;
        check(animation != null && animation.running, "opening a panel starts an animation");
        check(animation.duration == 220 && animation.interpolator instanceof android.view.animation.LinearInterpolator,
                "panel expansion uses a 220ms linear interpolator");
        check(tree.footer.getTranslationY() == 356, "first frame retains the input card's previous screen position");
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tree.footer.getLayoutParams();
        for (float fraction : new float[]{0.25f, 0.5f, 0.75f}) {
            animation.frame(fraction);
            list.layoutItems(); overlay.apply(2, 0, 356, 10, 12, true);
            check(tree.footer.getTranslationY() == 356 * (1 - fraction), "input card advances linearly at " + fraction);
            check(list.contentHeight - list.scrollOffset == Math.round(874 - 356 * fraction),
                    "last message follows intermediate animation frame " + fraction);
            check(tree.footer.getHeight() + lp.topMargin + lp.bottomMargin == 0,
                    "animation never consumes the full-height message viewport");
        }
        animation.frame(0.5f);
        // A new panel starts at the currently rendered position, even halfway through expansion.
        settleMessages(tree, list, overlay, 3, 654, 220);
        android.animation.ValueAnimator next = android.animation.ValueAnimator.last;
        check(next != animation && !animation.running && tree.footer.getTranslationY() == 42,
                "interruption retargets from the current screen position without a jump");
        next.frame(1);
        for (int i = 0; i < 6; i++) { list.layoutItems(); overlay.apply(3, 0, 356, 10, 12, true); }
        check(tree.footer.getTranslationY() == 0 && list.contentHeight - list.scrollOffset == 654,
                "completed animation restores exact target geometry and message visibility");
        settleMessages(tree, list, overlay, 2, 518, 356);
        android.animation.ValueAnimator cancelled = android.animation.ValueAnimator.last;
        overlay.restore();
        check(!cancelled.running && tree.footer.getTranslationY() == 0, "release cancels animation and removes its translation");
        cancelled.frame(1);
        check(tree.footer.getTranslationY() == 0, "released animation cannot mutate the footer later");
        android.animation.ValueAnimator.enabled = false;
    }

    private static RecyclerView messageList(Tree tree) {
        RecyclerView list = new RecyclerView(tree.footer.getContext());
        tree.root.addView(list, 0, new ViewGroup.LayoutParams(400, 1000));
        list.layout(0, 0, 400, 1000);
        list.contentHeight = 2000; list.scrollOffset = 1000;
        return list;
    }

    private static void resizedWindowKeepsAnimationOrigin() {
        Tree tree = new Tree(); RecyclerView list = messageList(tree);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        tree.root.layout(0, 0, 400, 700);
        settleMessages(tree, list, overlay, 1, 574, 356);
        android.animation.ValueAnimator.enabled = true;
        overlay.transition(2);
        // Closing the IME grows the host between the switch callback and layout.
        tree.root.layout(0, 0, 400, 1000);
        tree.panel.setVisibility(View.VISIBLE);
        tree.footer.layout(0, 518, 400, 990);
        tree.row.layout(0, 0, 400, 106);
        tree.row.getChildAt(0).layout(0, 40, 400, 100);
        tree.panel.layout(0, 116, 400, 472);
        tree.app.layout(0, 0, 400, 356);
        for (int i = 0; i < 8; i++) {
            list.layoutItems(); overlay.apply(2, 0, 356, 10, 12, true);
        }
        android.animation.ValueAnimator animation = android.animation.ValueAnimator.last;
        int[] location = new int[2]; tree.footer.getLocationOnScreen(location);
        check(location[1] == 574, "IME resize preserves the input card's screen position on the first frame");
        animation.frame(0.5f); tree.footer.getLocationOnScreen(location);
        check(location[1] == 546, "IME-to-panel movement interpolates between screen positions");
        tree.root.setTranslationY(-40);
        list.layoutItems(); overlay.apply(2, 0, 356, 10, 12, true);
        tree.footer.getLocationOnScreen(location);
        check(location[1] == 526, "ancestor movement updates the screen-space target before drawing");
        overlay.transition(1);
        check(!animation.running && tree.footer.getTranslationY() == 0,
                "returning to IME cancels the module animation");
        overlay.restore(); android.animation.ValueAnimator.enabled = false;
    }

    private static void settleMessages(Tree tree, RecyclerView list, ChatFooterOverlay overlay,
            int mode, int footerTop, int panelHeight) {
        overlay.transition(mode);
        tree.panel.setVisibility(mode == 2 || mode == 3 ? View.VISIBLE : View.GONE);
        tree.footer.layout(0, footerTop, 400, footerTop + 116 + panelHeight);
        tree.row.layout(0, 0, 400, 106);
        tree.row.getChildAt(0).layout(0, 40, 400, 100);
        tree.panel.layout(0, 116, 400, 116 + panelHeight);
        tree.app.layout(0, 0, 400, panelHeight);
        for (int i = 0; i < 12; i++) {
            list.layoutItems();
            overlay.apply(mode, 0, 356, 10, 12, true);
        }
    }

    private static void messagesFollowVisibleArea() {
        Tree tree = new Tree(); RecyclerView list = messageList(tree);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        settleMessages(tree, list, overlay, 0, 874, 356);
        check(list.contentHeight - list.scrollOffset == 874,
                "default state keeps the last message above the floating input card");
        tree.footer.setTranslationY(-300);
        settleMessages(tree, list, overlay, 1, 874, 356);
        check(list.contentHeight - list.scrollOffset == 574,
                "IME lift keeps the last message in the visible message area");
        settleMessages(tree, list, overlay, 2, 518, 356);
        check(list.contentHeight - list.scrollOffset == 518,
                "emoji panel follows the end without a second keyboard lift");
        settleMessages(tree, list, overlay, 3, 654, 220);
        check(list.contentHeight - list.scrollOffset == 654,
                "More panel releases the unused emoji height");
        settleMessages(tree, list, overlay, 0, 874, 356);
        check(list.contentHeight - list.scrollOffset == 874,
                "closing panels restores the default message position");
        list.scrollBy(0, -500);
        overlay.apply(0, 0, 356, 10, 12, true);
        int history = list.scrollOffset;
        settleMessages(tree, list, overlay, 2, 518, 356);
        check(list.scrollOffset == history, "opening a panel preserves the history reading position");
        check(list.getHeight() == 1000 && list.getPaddingBottom() == 0,
                "message viewport still extends behind the floating footer");
        overlay.restore();
    }

    private static void resizedImeDoesNotReserveTwice() {
        Tree tree = new Tree(); RecyclerView list = messageList(tree);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        settleMessages(tree, list, overlay, 0, 874, 356);
        // The host shortens the list and lifts its sibling footer by the same IME height.
        list.layout(0, 0, 400, 700);
        tree.footer.setTranslationY(-300);
        settleMessages(tree, list, overlay, 1, 874, 356);
        check(list.tailHeight == 126, "a resized list reserves only its actual overlap with the card");
        check(list.contentHeight - list.scrollOffset == 574,
                "a viewport resize retains the previously anchored last message");
        overlay.restore();
    }

    private static void movedFooterDoesNotRestoreOldLift() {
        Tree tree = new Tree();
        tree.panel.setVisibility(View.VISIBLE);
        tree.footer.setTranslationY(-300);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        for (int i = 0; i < 3; i++) overlay.apply(2, 0, 356, 10, 12, true);
        tree.root.removeView(tree.footer);
        ViewGroup parent = new ViewGroup(tree.footer.getContext());
        parent.addView(tree.footer); parent.attach();
        overlay.restore();
        check(tree.footer.getTranslationY() == 0,
                "a reparented footer never receives the previous window's native lift");
    }

    private static void listReplacementReleasesSpace() {
        Tree tree = new Tree();
        RecyclerView list = new RecyclerView(tree.footer.getContext());
        tree.root.addView(list, 0, new ViewGroup.LayoutParams(400, 1000));
        list.layout(0, 0, 400, 1000);
        tree.footer.layout(0, 874, 400, 1346);
        tree.row.layout(0, 0, 400, 106); tree.row.getChildAt(0).layout(0, 40, 400, 100);
        tree.panel.layout(0, 116, 400, 472); tree.app.layout(0, 0, 400, 356);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        for (int i = 0; i < 8; i++) overlay.apply(0, 0, 356, 10, 12, true);
        h3 state = new h3(); state.count = 1;
        Rect offsets = new Rect();
        check(list.decorations.size() == 1, "overlay resolves and decorates the actual chat list");
        list.decorations.get(0).getItemOffsets(offsets, new View(tree.footer.getContext()), list, state);
        check(offsets.bottom == 126 && list.getPaddingBottom() == 0, "actual obstruction becomes tail space, not padding");
        tree.root.removeView(list);
        overlay.apply(0, 0, 356, 10, 12, true);
        list.decorations.get(0).getItemOffsets(offsets, new View(tree.footer.getContext()), list, state);
        check(offsets.bottom == 0, "losing the list releases its old decoration immediately");
        overlay.restore();
    }

    private static void geometryAndRestoration() {
        Tree tree = new Tree();
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        check(!overlay.apply(0, 0, 356, 10, 12, true), "first geometry is only a candidate");
        check(!overlay.apply(0, 0, 356, 10, 12, true), "second matching pass is still settling");
        check(overlay.apply(0, 0, 356, 10, 12, true), "third pass commits geometry");
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tree.footer.getLayoutParams();
        check(lp.topMargin == -126 && lp.bottomMargin == -346, "closed margins use the input band");
        check(472 + lp.topMargin + lp.bottomMargin == 0, "closed footer occupies no list layout slot");
        check(overlay.applied.visible == 116, "toolbar and input row are included");
        check(tree.row.getBackground() instanceof ColorDrawable
                && ((ColorDrawable) tree.row.getBackground()).getColor() == 0, "owned input background becomes clear");
        tree.footer.setTranslationY(-300);
        overlay.apply(0, 0, 356, 10, 12, true);
        check(tree.footer.getTranslationY() == -300, "closed keyboard lift stays native");
        overlay.transition(3);
        tree.panel.setVisibility(View.VISIBLE);
        overlay.apply(3, 1, 356, 10, 12, true);
        check(tree.footer.getTranslationY() == -300, "unfinished native transition defers geometry");
        for (int i = 0; i < 3; i++) overlay.apply(3, 0, 356, 10, 12, true);
        check(tree.footer.getTranslationY() == 0, "open panel has no second vertical lift");
        check(tree.panel.getLayoutParams().height == 220, "More is compacted to 220dp");
        check(tree.app.p0 == 200 && tree.app.layoutCalls > 0, "More inner height excludes padding and is applied");
        tree.footer.layout(0, 0, 400, 336);
        tree.row.layout(0, 0, 400, 106);
        tree.row.getChildAt(0).layout(0, 40, 400, 100);
        tree.panel.layout(0, 116, 400, 336);
        tree.app.layout(0, 0, 400, 220);
        for (int i = 0; i < 3; i++) overlay.apply(3, 0, 356, 10, 12, true);
        check(overlay.applied.visible == 336, "glass spans the whole visible open panel");
        check(336 + lp.topMargin + lp.bottomMargin == 0, "open footer also occupies zero space");
        overlay.transition(2);
        check(tree.panel.getLayoutParams().height == 356 && tree.app.p0 == 356,
                "leaving More restores native outer and port heights");
        tree.panel.layoutRequested = true;
        int top = lp.topMargin;
        check(!overlay.apply(2, 0, 356, 10, 12, true) && lp.topMargin == top,
                "pending panel layout never commits stale geometry");
        overlay.restore();
        check(tree.row.getBackground() == tree.original, "disabling restores the original background reference");
        check(tree.root.getClipChildren() && tree.root.getClipToPadding(), "parent clipping restores");
    }

    private static void nativeVisibilityAndSmallWindow() {
        Tree tree = new Tree();
        tree.panel.setVisibility(View.VISIBLE);
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        for (int i = 0; i < 3; i++) overlay.apply(-1, 0, 356, 10, 12, true);
        check(overlay.applied != null && !overlay.applied.open && overlay.applied.visible == 116,
                "VISIBLE alone never turns an unknown closed panel into an open one");
        tree.root.layout(0, 0, 400, 250);
        tree.footer.layout(0, 0, 400, 472);
        tree.row.layout(0, 0, 400, 106); tree.row.getChildAt(0).layout(0, 40, 400, 100);
        tree.panel.layout(0, 116, 400, 472); tree.app.layout(0, 0, 400, 356);
        overlay.transition(2);
        for (int i = 0; i < 3; i++) overlay.apply(2, 0, 356, 10, 12, true);
        check(tree.panel.getLayoutParams().height == 124, "emoji height is limited by the small window");
        overlay.restore();

        tree = new Tree(); overlay = new ChatFooterOverlay(tree.footer);
        tree.panel.setVisibility(View.VISIBLE);
        for (int i = 0; i < 3; i++) overlay.apply(3, 0, 356, 10, 12, true);
        overlay.nativePortChanged(tree.app, 400);
        tree.app.p0 = 400;
        BubbleSettings.values.put("floating_chat_footer_more_height_dp", 300);
        for (int i = 0; i < 3; i++) overlay.apply(3, 0, 400, 10, 12, true);
        check(tree.panel.getLayoutParams().height == 300, "More can grow after the preference changes");
        overlay.transition(2);
        check(tree.panel.getLayoutParams().height == 400 && tree.app.p0 == 400,
                "native port update during compact mode replaces the restoration snapshot");
        BubbleSettings.values.clear(); overlay.restore();
    }

    private static void decorationLifecycle() throws Exception {
        android.content.Context context = new android.content.Context();
        RecyclerView recycler = new RecyclerView(context); recycler.attach();
        ChatTailSpacer spacer = ChatTailSpacer.obtain(recycler);
        check(spacer != null && recycler.decorations.size() == 1, "host-loader bridge registers once");
        check(!spacer.update(126) && !spacer.update(126) && !spacer.update(126) && spacer.update(126),
                "tail height settles before applying its end anchor on the next layout");
        h3 state = new h3(); state.count = 4;
        Rect offsets = new Rect(); View child = new View(context);
        recycler.position = 3;
        recycler.decorations.get(0).getItemOffsets(offsets, child, recycler, state);
        check(offsets.bottom == 126, "only the last item gets scroll space");
        recycler.position = 2;
        recycler.decorations.get(0).getItemOffsets(offsets, child, recycler, state);
        check(offsets.bottom == 0, "ordinary items remain unchanged");
        recycler.manager.h = true; recycler.position = 0;
        recycler.decorations.get(0).getItemOffsets(offsets, child, recycler, state);
        check(offsets.bottom == 126, "reverse layout uses adapter position zero");
        spacer.release();
        recycler.decorations.get(0).getItemOffsets(offsets, child, recycler, state);
        check(offsets.bottom == 0, "release removes the obstruction without altering viewport padding");
        check(ChatTailSpacer.obtain(recycler) == spacer && recycler.decorations.size() == 1,
                "reattachment reuses a zero-height decoration");
    }

    private static void sourceEntry() throws Throwable {
        de.robv.android.xposed.XposedBridge.hooks.clear();
        HostTargetResolver.fail = false;
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_enabled", true);
        FloatingChatFooterFeature.init(ChatFooterOverlayChecks.class.getClassLoader());
        android.os.Handler.drain();
        Tree tree = new Tree();
        de.robv.android.xposed.XposedBridge.constructed(tree.footer);
        for (int i = 0; i < 5; i++) tree.footer.getViewTreeObserver().draw();
        java.lang.reflect.Method existing = FloatingChatFooterFeature.class.getDeclaredMethod("existing", View.class);
        existing.setAccessible(true); Object state = existing.invoke(null, tree.footer);
        java.lang.reflect.Field field = state.getClass().getDeclaredField("overlay"); field.setAccessible(true);
        check(field.get(state) instanceof ChatFooterOverlay, "real State selects the integrated native overlay");
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tree.footer.getLayoutParams();
        check(tree.footer.getHeight() + lp.topMargin + lp.bottomMargin == 0,
                "real preDraw owns the zero-slot margins");
        ((ChatFooterOverlay) field.get(state)).changed = true;
        tree.footer.setVisibility(View.GONE);
        java.lang.reflect.Method preDraw = state.getClass().getDeclaredMethod("onPreDraw");
        preDraw.setAccessible(true);
        check(preDraw.invoke(state).equals(true), "a hidden footer never cancels the whole window's drawing");
        tree.footer.setVisibility(View.VISIBLE);
        de.robv.android.xposed.XposedBridge.invoke(tree.footer, "switchHost",
                new Class<?>[]{int.class, boolean.class, int.class}, 3, true, 7);
        check(tree.root.lastY == 0, "real completed native switch cancels the open-panel lift");
        check(!tree.root.lastAnimate, "native scroller settles before the module animates the panel");
        for (int i = 0; i < 4; i++) tree.footer.getViewTreeObserver().draw();
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_enabled", false);
        FloatingChatFooterFeature.refresh(); android.os.Handler.drain();
        check(field.get(state) == null, "real disable releases the overlay");
        check(tree.root.lastY == 360 && tree.root.lastAnimate,
                "disable replays the witnessed native panel position and animation flags");
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_enabled", true);
        FloatingChatFooterFeature.refresh(); android.os.Handler.drain();
        de.robv.android.xposed.XposedBridge.invoke(tree.footer, "switchHost",
                new Class<?>[]{int.class, boolean.class, int.class}, 0, false, 0);
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_enabled", false);
        FloatingChatFooterFeature.refresh(); android.os.Handler.drain();
        check(tree.root.lastY == 0, "disabling after close does not replay an obsolete open-panel request");
    }

    private static void deepInputAndGestureBand() {
        Tree tree = new Tree();
        View edit = tree.row.getChildAt(0);
        tree.row.removeView(edit);
        ViewGroup parent = tree.row;
        for (int i = 0; i < 10; i++) {
            ViewGroup wrapper = new ViewGroup(edit.getContext());
            parent.addView(wrapper, new ViewGroup.LayoutParams(400, 106));
            wrapper.layout(0, 0, 400, 106);
            parent = wrapper;
        }
        parent.addView(edit, new ViewGroup.LayoutParams(400, 60)); edit.layout(0, 40, 400, 100);
        check(ChatFooterOverlay.input(tree.footer) == edit, "captured deep input path remains discoverable");
        tree.root.insets = new android.view.WindowInsets(); tree.root.insets.nav = 24;
        ChatFooterOverlay overlay = new ChatFooterOverlay(tree.footer);
        for (int i = 0; i < 4; i++) overlay.apply(0, 0, 356, 10, 12, true);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tree.footer.getLayoutParams();
        check(overlay.applied != null && overlay.applied.visible == 116 && overlay.applied.gap == 34,
                "only the input card clears the gesture band and configured gap");
        check(lp.topMargin == -150 && tree.footer.getHeight() + lp.topMargin + lp.bottomMargin == 0,
                "gesture clearance does not consume message viewport height");
        overlay.restore();
    }

    private static void nativePanelOrderStillOverlays() throws Throwable {
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_panel_above", false);
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_enabled", true);
        FloatingChatFooterFeature.refresh(); android.os.Handler.drain();
        Tree tree = new Tree();
        de.robv.android.xposed.XposedBridge.constructed(tree.footer);
        for (int i = 0; i < 5; i++) tree.footer.getViewTreeObserver().draw();
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tree.footer.getLayoutParams();
        check(tree.footer.getHeight() + lp.topMargin + lp.bottomMargin == 0,
                "native panel order still leaves the message viewport full height");
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_enabled", false);
        FloatingChatFooterFeature.refresh(); android.os.Handler.drain();
        wex.ui.core.WexStore.INSTANCE.flags.put("floating_chat_footer_panel_above", true);
    }

    private static final class Tree {
        final com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout root;
        final ChatFooter footer;
        final ViewGroup row;
        final ChatFooterBottom panel;
        final AppPanel app;
        final ColorDrawable original = new ColorDrawable(0xFFFFFFFF);
        Tree() {
            android.content.Context context = new android.content.Context();
            root = new com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout(context);
            footer = new ChatFooter(context); row = new ViewGroup(context);
            panel = new ChatFooterBottom(context); app = new AppPanel(context);
            root.addView(footer, new ViewGroup.MarginLayoutParams(400, 472));
            footer.addView(row, new ViewGroup.LayoutParams(400, 106));
            row.addView(new EditText(context), new ViewGroup.LayoutParams(400, 60));
            footer.addView(panel, new ViewGroup.LayoutParams(400, 356));
            panel.addView(app, new ViewGroup.LayoutParams(400, 356));
            root.layout(0, 0, 400, 1000); footer.layout(0, 0, 400, 472);
            row.layout(0, 0, 400, 106); row.getChildAt(0).layout(0, 40, 400, 100);
            panel.layout(0, 116, 400, 472); app.layout(0, 0, 400, 356);
            app.setPadding(0, 10, 0, 10); app.p0 = 356;
            row.setBackground(original); panel.setVisibility(View.GONE);
            root.attach();
        }
    }

    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
