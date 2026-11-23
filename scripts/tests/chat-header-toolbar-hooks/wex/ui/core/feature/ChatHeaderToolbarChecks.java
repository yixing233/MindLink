package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.widget.ActionBarContainer;
import com.tencent.mm.pluginsdk.ui.chat.ChatFooter;
import com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout;
import com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout;
import com.tencent.mm.ui.LauncherUI;
import com.tencent.mm.ui.chatting.ChattingUI;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import wex.ui.core.WexStore;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual feature init/callbacks and real ChatToolbarLifecycle/ChatToolbarViews. */
public final class ChatHeaderToolbarChecks {
    private static int checks;
    private static final String TOOLBAR = "wex_chat_toolbar";

    public static void main(String[] args) throws Throwable {
        String variant = args[0];
        if ("separated-geometry".equals(variant)) { separatedGeometry(); return; }
        toolSettingsRoundtrip();
        flags(true, true);
        if ("pre-fix-header".equals(variant)) {
            try {
                FloatingChatHeaderFeature.init(ChatHeaderToolbarChecks.class.getClassLoader());
                throw new AssertionError("original missing optional fit must throw");
            } catch (NoSuchMethodException expected) {
                check(XposedBridge.constructors.containsKey(ChattingUILayout.class), "original constructors registered before failure");
                check(XposedBridge.hooks.keySet().stream().noneMatch(m -> "onResume".equals(m.getName())), "original optional failure prevented lifecycle registration");
                System.out.println("RED confirmed: original Header init missing fitSystemWindows aborts after constructors/onLayout, no resume hook");
                return;
            }
        }
        XposedBridge.failGrid = !"present".equals(variant);
        if ("pre-fix-toolbar".equals(variant)) {
            init(); Handler.drain();
            existingTagRecovery(true);
            System.out.println("RED confirmed: original Toolbar existing lifecycle tag returns from real attach callback; late input remains unmounted");
            return;
        }
        if ("pre-fix-entry".equals(variant)) {
            init(); Handler.drain();
            notificationEntry(false);
            System.out.println("RED confirmed: without onNewIntent/onStart hooks a notification-launched chat page stays undecorated");
            return;
        }
        if ("pre-fix-scan".equals(variant)) {
            init(); Handler.drain();
            try {
                toolbarNoisyPanels();
                throw new IllegalStateException("old full-footer scan unexpectedly passed");
            } catch (AssertionError expected) {
                check(expected.getMessage().equals("visible input survives a huge hidden native panel"),
                        "RED replay failed at the intended finder assertion");
                System.out.println("RED confirmed: candidate-009 full-footer scan rejects a valid input beside a hidden native panel");
                return;
            }
        }
        if ("pre-fix-padding".equals(variant)) {
            init(); Handler.drain();
            try {
                pinnedCardGeometry();
                throw new IllegalStateException("old padding ownership unexpectedly passed");
            } catch (AssertionError expected) {
                check(expected.getMessage().equals("native padding overwrite gets our full inset reapplied"),
                        "RED replay failed at the intended native overwrite assertion");
                System.out.println("RED confirmed: candidate-009 blindly subtracts our inset after a native padding overwrite");
                return;
            }
        }

        // Trees already attached before feature init: neither constructor nor attach
        // callback observed them. Embedded Launcher has a separate home actionbar.
        LauncherUI lateActivity = new LauncherUI();
        Tree late = tree(lateActivity, true, false);
        ActionBarContainer home = new ActionBarContainer(lateActivity); home.setElevation(1f);
        root(lateActivity).addView(home, margin(1,2,3,4)); root(lateActivity).attach();
        init();
        if (!"present".equals(variant)) {
            check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=fitSystemWindows unavailable=NoSuchMethodException")), "missing optional fit is isolated");
        }
        int callbacks = callbackCount();
        init();
        check(callbackCount() == callbacks, "repeated init deduplicates inherited hooks");
        check(XposedBridge.constructors.get(ChattingUILayout.class).size() == 1, "header constructor callback once");
        resume(lateActivity);
        check(late.layout.attachListeners.isEmpty() && late.footer.attachListeners.isEmpty(), "resume is after plus queued, no early tree mutation");
        Handler.drain();
        check(late.layout.attachListeners.size() == 1, "late existing layout discovered");
        check(late.footer.attachListeners.size() == 1, "late existing footer discovered");
        check(late.layout.getViewTreeObserver().listeners.size() == 1, "one header pre-draw watcher");
        check(late.footer.getViewTreeObserver().globals.size() == 1, "one footer limited layout watcher");
        check(late.title.getElevation() == 4f && ((ViewGroup.MarginLayoutParams)late.title.getLayoutParams()).leftMargin == 12, "embedded unique sibling title receives real feature margins");
        check(home.getElevation() == 1f && ((ViewGroup.MarginLayoutParams)home.getLayoutParams()).leftMargin == 1, "home actionbar outside chat island untouched");
        check(countToolbar(late.footer) == 1 && late.input.getChildCount() == 2, "real toolbar shell inserted above native input row");
        check(chipCount(late.footer) == 15, "real toolbar builds existing 15 tool chips without redesign");
        if (!"present".equals(variant)) {
            check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=init-grid unavailable=LinkageError")), "posted optional grid hook Throwable is caught");
            check(countToolbar(late.footer) == 1, "grid hook failure cannot block baseline toolbar");
        }
        for (int i=0;i<5;i++) { resume(lateActivity); FloatingChatHeaderFeature.refresh(); ChatToolbarFeature.refresh(); }
        Handler.drain();
        check(countToolbar(late.footer) == 1 && late.footer.attachListeners.size() == 1 && late.layout.attachListeners.size() == 1, "repeat resume/refresh no duplicate observers or insertion");
        stop(lateActivity);
        check(late.title.getElevation() == 0f && ((ViewGroup.MarginLayoutParams)late.title.getLayoutParams()).leftMargin == 2, "disable restores header margins and drawing ownership");
        check(countToolbar(late.footer) == 0 && late.footer.getViewTreeObserver().globals.isEmpty(), "disable removes toolbar and global watcher");

        existingTagRecovery(false);
        lateInputAfterExhaustion();
        delayedTreesAndCancellation();
        preciseHeaderFallback();
        headerReparentAndDetach();
        discoveryBoundaries();
        notificationEntry(true);
        toolbarNoisyPanels();
        pinnedCardGeometry();
        if ("present".equals(variant)) panelSnapshotEntry();
        check(Handler.pending() == 0, "no leaked discovery/mount/load tasks");
        check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=mount-child")),
                "failed input search emits bounded element geometry");
        check(XposedBridge.logs.stream().noneMatch(s -> s.contains("secret-chat-body")), "diagnostics never expose chat body");
        System.out.println("GREEN " + variant + ": " + checks + " assertions (real Header/Toolbar init, inherited lifecycle, bounded discovery, margins, toolbar mount, late input, cleanup)");
    }

    /** Split-header geometry: real FloatingHeaderStyle draws three pills around the
     *  synthetic title text; the title pill must cover the full title view bounds
     *  (long Thai titles are reserved only by *visible* menu actions).
     *  White-bar regression: the host card visuals must stay neutralized on every
     *  re-apply (frame N+1) so no full-width white card paints behind the pills,
     *  and the decoration must reserve outdented shadow padding like WeKit. */
    private static void separatedGeometry() throws Throwable {
        java.lang.reflect.Method apply = FloatingHeaderStyle.class.getDeclaredMethod("applySeparated", View.class, FloatingHeaderStyle.SeparatedState.class, int.class, int.class, int.class);
        apply.setAccessible(true);
        java.lang.reflect.Method restore = FloatingHeaderStyle.class.getDeclaredMethod("restoreSeparated", FloatingHeaderStyle.SeparatedState.class);
        restore.setAccessible(true);
        int corner = 24, gap = 8, elevation = 4;
        int[] cases = {0, 1, 2}; // invisible slot, no actions, visible content-empty native slot
        for (int mode : cases) {
            LauncherUI activity = new LauncherUI();
            ViewGroup decor = root(activity); decor.attach();
            RelativeLayout island = new RelativeLayout(activity); decor.addView(island);
            // Header (ActionBarContainer) -> Toolbar -> parts-root RelativeLayout, width 600, height 60.
            ActionBarContainer header = new ActionBarContainer(activity);
            androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(activity);
            RelativeLayout parts = new RelativeLayout(activity);
            toolbar.addView(parts, new ViewGroup.LayoutParams(-1, 60));
            header.addView(toolbar, new ViewGroup.LayoutParams(-1, 60));
            island.addView(header, margin(12, 3, 12, 0));
            // Left: back host 100px wide whose icon content is 44px (expressed as paddingRight),
            // matching WeChat where the visible icon center sits left of the host center.
            ViewGroup left = new ViewGroup(activity);
            ImageView back = new ImageView(activity);
            back.setPadding(0, 0, 56, 0); // icon content spans 0..44
            left.addView(back, new ViewGroup.LayoutParams(100, 60));
            // Title: centered synthetic long Thai placeholder (no user chat content).
            TextView title = new TextView(activity); title.setText("ทดสอบเรื่องยาวมาก");
            RelativeLayout.LayoutParams titleRule = new RelativeLayout.LayoutParams(-2, 60); titleRule.addRule(RelativeLayout.CENTER_IN_PARENT);
            // Right: menu host [0,200] empty slot + [200,244] visible action — as kept by the
            // host across style switches: the empty slot must not widen the title reserve.
            ViewGroup right = new ViewGroup(activity);
            // Menu host mirrors WeChat's WXActionMenuView (LinearLayout): children laid out in order.
            LinearLayout menuHost = new LinearLayout(activity);
            if (mode != 1) {
                ViewGroup hidden = mode == 2 ? new com.tencent.mm.ui.widget.WXActionMenuView(activity) : new ViewGroup(activity);
                if (mode == 0) hidden.setVisibility(View.INVISIBLE);
                if (mode == 2) {
                    hidden.addView(new ImageView(activity), new ViewGroup.LayoutParams(200, 60));
                    TextView empty = new TextView(activity); empty.setText("");
                    hidden.addView(empty, new ViewGroup.LayoutParams(200, 60));
                }
                menuHost.addView(hidden, new ViewGroup.LayoutParams(200, 60));
                ViewGroup action = new ViewGroup(activity);
                menuHost.addView(action, new ViewGroup.LayoutParams(44, 60));
            }
            right.addView(menuHost, new ViewGroup.LayoutParams(244, 60));
            RelativeLayout.LayoutParams leftRule = new RelativeLayout.LayoutParams(100, 60); leftRule.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            parts.addView(left, 0, leftRule);
            parts.addView(title, 1, titleRule);
            RelativeLayout.LayoutParams rightRule = new RelativeLayout.LayoutParams(244, 60); rightRule.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            parts.addView(right, 2, rightRule);
            // Measure top-down first so the doubles' container onLayout (which re-layouts
            // children from measured sizes) reproduces the intended geometry:
            // left 0..100, title 100..300, right 356..600, action window 556..600.
            header.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));
            header.layout(0, 0, 600, 60);
            // Rule-blind double containers misplace anchored children; pin the leaves last.
            right.layout(356, 0, 600, 60);
            title.layout(100, 0, 300, 60);
            Object state = apply.invoke(null, header, null, corner, gap, elevation);
            check(state != null, "separated header state built for mode " + mode);
            int titleWidth=titleRule.width;
            check(titleWidth>200&&title.getMaxWidth()==titleWidth,"native title max width follows available pill width");
            title.measure(View.MeasureSpec.makeMeasureSpec(titleWidth,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(60,View.MeasureSpec.EXACTLY));
            title.layout((600-titleWidth)/2,0,(600+titleWidth)/2,60);
            // SeparatedState fields are package-private in the real style and absent in
            // the compiled double; resolve the decorator reflectively in both builds.
            View decoration = decorationOf(state);
            check(decoration.getLayerType() == View.LAYER_TYPE_HARDWARE, "decoration uses the hardware glass layer mode " + mode);
            // Draw once: shadow pass then fill pass record the round-rect pills.
            ViewGroup layer = (ViewGroup) decoration.getParent();
            ViewGroup.MarginLayoutParams layerParams = (ViewGroup.MarginLayoutParams) layer.getLayoutParams();
            check(layerParams.leftMargin <= -10 && layerParams.leftMargin + layerParams.width >= (mode != 1 ? 618 : 610),
                    "software canvas includes the actual pill union plus shadow, not only root bounds");
            layer.layout(layerParams.leftMargin, layerParams.topMargin,
                    layerParams.leftMargin + layerParams.width, layerParams.topMargin + layerParams.height);
            decoration.layout(0, 0, layerParams.width, layerParams.height);
            android.graphics.Canvas canvas = new android.graphics.Canvas();
            Method draw = decoration.getClass().getDeclaredMethod("onDraw", android.graphics.Canvas.class);
            draw.setAccessible(true);
            draw.invoke(decoration, canvas);
            java.util.List<android.graphics.RectF> pills = new ArrayList<>();
            for (int i = 0; i < canvas.roundRects.size(); i++) {
                if (!canvas.roundPaints.get(i)[0]) {
                    android.graphics.RectF rect = new android.graphics.RectF();
                    rect.set(canvas.roundRects.get(i));
                    int[] origin = new int[2], rootOrigin = new int[2];
                    decoration.getLocationInWindow(origin); parts.getLocationInWindow(rootOrigin);
                    rect.offset(origin[0] - rootOrigin[0], origin[1] - rootOrigin[1]);
                    pills.add(rect);
                }
            }
            int expected = mode != 1 ? 3 : 2;
            check(pills.size() == expected, "expected " + expected + " pills mode " + mode + " got " + pills.size());
            android.graphics.RectF leftPill = pills.get(0), titlePill = pills.get(1);
            android.graphics.RectF rightPill = pills.size() > 2 ? pills.get(2) : new android.graphics.RectF();
            check(Math.abs(leftPill.left) < 0.6f && Math.abs(leftPill.right - 60f) < 0.6f
                    && left.getTranslationX() == 8f, "left pill aligns with the card margin and its native hit host moves with it");
            check(leftPill.height() == 60f, "left pill spans full header height mode " + mode);
            // Title pill spans between the reserves; hidden empty slot must not shrink it.
            check(titlePill.left <= (600-titleWidth)/2f && titlePill.right >= (600+titleWidth)/2f,
                    "title pill covers the actual centered long title bounds mode " + mode);
            check(titlePill.height() == 60f, "title pill spans full header height mode " + mode);
            check(titlePill.top == 0f && titlePill.bottom == 60f, "outdented drawing aligns with actual title window bounds");
            check(!parts.getClipChildren() && !parts.getClipToPadding()
                    && !toolbar.getClipChildren() && !header.getClipChildren(), "shadow escapes the header ancestor clips");
            check(Math.abs(titlePill.left + titlePill.right - 600f) < 0.6f, "title pill symmetric about screen center mode " + mode + " got " + titlePill.left + ".." + titlePill.right);
            check(Math.abs((titlePill.left - leftPill.right) - gap) < 0.6f, "left side gap honors component gap mode " + mode + " got " + (titlePill.left - leftPill.right));
            if (mode != 1) {
                check(Math.abs(rightPill.left - 540f) < 0.6f && Math.abs(rightPill.right - 600f) < 0.6f,
                        "right pill aligns with the opposite card margin mode " + mode);
                check(right.getTranslationX() == -8f, "menu icon and hit host follow the aligned right pill");
                check(Math.abs(rightPill.left - titlePill.right - gap) < 0.6f, "both title gaps equal the component setting");
            } else {
                check(rightPill.isEmpty(), "no right circle without visible actions mode " + mode + " got " + rightPill.left + ".." + rightPill.right);
                check(titlePill.right >= 512f, "empty menu host reserves no right space (title pill expands) mode " + mode + " got right=" + titlePill.right);
            }
            restore.invoke(null, state);
            check(left.getTranslationX() == 0f, "disable restores native return-button position");
            check(right.getTranslationX() == 0f, "disable restores native menu position");
            check(titleRule.width==-2&&title.getMaxWidth()==Integer.MAX_VALUE,"split restore returns native title width constraints");
            check(parts.getClipChildren() && parts.getClipToPadding()
                    && toolbar.getClipChildren() && header.getClipChildren(), "split disable restores ancestor clips");
            check(header.getBackground() == null && header.getElevation() == 0f, "restore returns original header background and clears decoration elevation mode " + mode + " got bg=" + header.getBackground() + " elev=" + header.getElevation());
            decor.removeView(island); decor.detach();
        }

        // Two menu actions reserve more room on the right. A long title uses the space
        // between the aligned edge cards; a short title stays centred on the page.
        {
            LauncherUI activity = new LauncherUI();
            ViewGroup decor = root(activity); decor.attach();
            RelativeLayout island = new RelativeLayout(activity); decor.addView(island);
            ActionBarContainer header = new ActionBarContainer(activity);
            androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(activity);
            RelativeLayout parts = new RelativeLayout(activity);
            toolbar.addView(parts, new ViewGroup.LayoutParams(-1, 60));
            header.addView(toolbar, new ViewGroup.LayoutParams(-1, 60));
            island.addView(header, margin(12, 3, 12, 0));
            ViewGroup left = new ViewGroup(activity);
            ImageView back = new ImageView(activity);
            back.setPadding(0, 0, 56, 0);
            left.addView(back, new ViewGroup.LayoutParams(100, 60));
            TextView title = new TextView(activity);
            title.setText("12345678901234567890123456789012345678901234567890"); // 50 chars = 500px
            RelativeLayout.LayoutParams titleRule = new RelativeLayout.LayoutParams(-2, 60); titleRule.addRule(RelativeLayout.CENTER_IN_PARENT);
            ViewGroup right = new ViewGroup(activity);
            LinearLayout menuHost = new LinearLayout(activity);
            ViewGroup action = new ViewGroup(activity);
            menuHost.addView(action, new ViewGroup.LayoutParams(44, 60));
            ViewGroup secondAction = new ViewGroup(activity);
            menuHost.addView(secondAction, new ViewGroup.LayoutParams(44, 60));
            right.addView(menuHost, new ViewGroup.LayoutParams(88, 60));
            RelativeLayout.LayoutParams leftRule = new RelativeLayout.LayoutParams(100, 60); leftRule.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            parts.addView(left, 0, leftRule);
            parts.addView(title, 1, titleRule);
            RelativeLayout.LayoutParams rightRule = new RelativeLayout.LayoutParams(88, 60); rightRule.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            parts.addView(right, 2, rightRule);
            header.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));
            header.layout(0, 0, 600, 60);
            right.layout(512, 0, 600, 60); menuHost.layout(0, 0, 88, 60);
            action.layout(0, 0, 44, 60); secondAction.layout(44, 0, 88, 60);
            title.layout(100, 0, 500, 60);
            Object state = apply.invoke(null, header, null, corner, gap, elevation);
            check(state != null, "long-title separated state built");
            // Frame N+1 with the title laid out at the width just applied: the decision must hold.
            title.layout(300 - titleRule.width / 2, 0, 300 + titleRule.width / 2, 60);
            state = apply.invoke(null, header, state, corner, gap, elevation);
            View decoration = decorationOf(state);
            java.lang.reflect.Field pillField = decoration.getClass().getDeclaredField("title");
            pillField.setAccessible(true);
            android.graphics.RectF pill = (android.graphics.RectF) pillField.get(decoration);
            check(Math.abs(pill.left - 68f) < 0.6f && Math.abs(pill.right - 488f) < 0.6f,
                    "long title pill spans from the left circle to the right one, got " + pill.left + ".." + pill.right);
            check(titleRule.width == 404 && title.getMaxWidth() == 404, "long title host gets the whole pill minus the inner gaps, got " + titleRule.width);
            check(Math.abs(title.getTranslationX() + 22f) < 0.6f, "long title host is centred in the wider pill, got dx=" + title.getTranslationX());
            // A short title goes back to the symmetric pill and the native position.
            title.setText("1234567890");
            title.layout(300 - titleRule.width / 2, 0, 300 + titleRule.width / 2, 60);
            state = apply.invoke(null, header, state, corner, gap, elevation);
            pill = (android.graphics.RectF) pillField.get(decorationOf(state));
            check(Math.abs(pill.left - 112f) < 0.6f && Math.abs(pill.right - 488f) < 0.6f,
                    "short title keeps the centred pill, got " + pill.left + ".." + pill.right);
            check(titleRule.width == 360 && title.getTranslationX() == 0f, "short title host is centred again, got w=" + titleRule.width + " dx=" + title.getTranslationX());
            restore.invoke(null, state);
            check(titleRule.width == -2 && title.getTranslationX() == 0f && title.getMaxWidth() == Integer.MAX_VALUE,
                    "restore returns native title width, position and limit");
            decor.removeView(island); decor.detach();
        }

        // A fixed width WeChat gave a view inside the title host (computed for the native
        // header) must not cap the name below the pill: it wraps its content while split.
        {
            LauncherUI activity = new LauncherUI();
            ViewGroup decor = root(activity); decor.attach();
            RelativeLayout island = new RelativeLayout(activity); decor.addView(island);
            ActionBarContainer header = new ActionBarContainer(activity);
            androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(activity);
            RelativeLayout parts = new RelativeLayout(activity);
            toolbar.addView(parts, new ViewGroup.LayoutParams(-1, 60));
            header.addView(toolbar, new ViewGroup.LayoutParams(-1, 60));
            island.addView(header, margin(12, 3, 12, 0));
            ViewGroup left = new ViewGroup(activity); left.addView(new ImageView(activity), new ViewGroup.LayoutParams(44, 60));
            LinearLayout titleHost = new LinearLayout(activity);
            // WeChat pads the title host by the native side-button width on both sides.
            titleHost.setPadding(84, 0, 84, 0);
            TextView name = new TextView(activity); name.setText("12345678901234567890");
            ViewGroup.LayoutParams nameParams = new ViewGroup.LayoutParams(150, 60);
            titleHost.addView(name, nameParams);
            ImageView mute = new ImageView(activity);
            ViewGroup.LayoutParams muteParams = new ViewGroup.LayoutParams(20, 20);
            titleHost.addView(mute, muteParams);
            RelativeLayout.LayoutParams titleRule = new RelativeLayout.LayoutParams(-2, 60); titleRule.addRule(RelativeLayout.CENTER_IN_PARENT);
            ViewGroup right = new ViewGroup(activity);
            RelativeLayout.LayoutParams rightRule = new RelativeLayout.LayoutParams(44, 60); rightRule.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            parts.addView(left, 0, new RelativeLayout.LayoutParams(44, 60));
            parts.addView(titleHost, 1, titleRule);
            parts.addView(right, 2, rightRule);
            header.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));
            header.layout(0, 0, 600, 60);
            titleHost.layout(150, 0, 450, 60); name.layout(0, 0, 150, 60);
            Object state = apply.invoke(null, header, null, corner, gap, elevation);
            check(state != null, "fixed-width title state built");
            check(nameParams.width == ViewGroup.LayoutParams.WRAP_CONTENT, "fixed native name width no longer caps the title, got " + nameParams.width);
            check(muteParams.width == 20, "icons keep their own fixed size");
            // The host keeps its own side padding (WeChat re-applies it on layout); the host grows
            // by it, so the content area between the paddings is the whole pill.
            java.lang.reflect.Field pillOf = decorationOf(state).getClass().getDeclaredField("title");
            pillOf.setAccessible(true);
            int pillInner = Math.round(((android.graphics.RectF) pillOf.get(decorationOf(state))).width() - 2 * gap);
            check(titleHost.getPaddingLeft() == 84 && titleHost.getPaddingRight() == 84, "native side padding is left to the host");
            check(titleRule.width == pillInner + 168, "host widened by its padding so content spans the pill, got " + titleRule.width + " pill " + pillInner);
            restore.invoke(null, state);
            check(nameParams.width == 150, "restore returns the native fixed name width, got " + nameParams.width);
            check(titleRule.width == -2 && titleHost.getPaddingLeft() == 84, "restore returns the native width and keeps the native padding");
            decor.removeView(island); decor.detach();
        }

        // A tall ancestor can still clip an outdented shadow near its top edge.
        // Restore separated visuals before releasing the integrated owner snapshot.
        {
            LauncherUI activity=new LauncherUI();
            ViewGroup decor=root(activity);decor.attach();
            ActionBarContainer header=new ActionBarContainer(activity);
            androidx.appcompat.widget.Toolbar toolbar=new androidx.appcompat.widget.Toolbar(activity);
            RelativeLayout parts=new RelativeLayout(activity);
            toolbar.addView(parts,new ViewGroup.LayoutParams(600,60));header.addView(toolbar,new ViewGroup.LayoutParams(600,60));
            ViewGroup tall=new ViewGroup(activity);tall.setClipToOutline(true);decor.addView(tall,new ViewGroup.LayoutParams(600,121));tall.addView(header,new ViewGroup.LayoutParams(600,60));
            View left=new View(activity),right=new View(activity);TextView title=new TextView(activity);
            RelativeLayout.LayoutParams titleParams=new RelativeLayout.LayoutParams(-2,60);titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            RelativeLayout.LayoutParams rightParams=new RelativeLayout.LayoutParams(44,60);rightParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            parts.addView(left,new RelativeLayout.LayoutParams(44,60));parts.addView(title,titleParams);parts.addView(right,rightParams);
            tall.layout(0,0,600,121);header.layout(0,3,600,63);toolbar.layout(0,0,600,60);parts.layout(0,0,600,60);
            left.layout(0,0,44,60);right.layout(556,0,600,60);title.layout(50,0,550,60);
            FloatingHeaderStyle.CardState integrated=FloatingHeaderStyle.applyCard(header,null,24,4);
            FloatingHeaderStyle.SeparatedState state=FloatingHeaderStyle.applySeparated(header,null,24,8,4,null);
            check(state!=null&&!tall.getClipChildren()&&!tall.getClipToOutline(),"height121 ancestor does not clip shadow at top3 or to outline");
            FloatingHeaderStyle.restoreSeparated(state);FloatingHeaderStyle.restore(integrated);
            check(header.getBackground()==null&&header.getOutlineProvider()==null&&tall.getClipChildren()&&tall.getClipToOutline(),
                    "integrated to separated to disable restores original visuals and tall-ancestor clip");
            decor.removeView(tall);decor.detach();
        }

        // ── Home-bar material: the integrated card swaps only its fill; the elevation
        // shadow, the outline clip and the host background ownership stay untouched.
        {
            LauncherUI activity = new LauncherUI();
            ViewGroup decor = root(activity); decor.attach();
            ActionBarContainer header = new ActionBarContainer(activity);
            android.graphics.drawable.Drawable nativeBackground = new android.graphics.drawable.ColorDrawable(0xFF101010);
            header.setBackground(nativeBackground);
            decor.addView(header, new ViewGroup.LayoutParams(600, 60));
            header.layout(0, 0, 600, 60);
            ChatGlassSurface.forced = ChatGlassSurface.LIQUID;
            FloatingHeaderStyle.CardState card = FloatingHeaderStyle.applyCard(header, null, 24, 4,
                    ChatGlassSurface.LIQUID, 8);
            check(header.getBackground() instanceof ChatGlassSurface,
                    "glass header installs the home-bar material instead of a solid fill");
            ChatGlassSurface glass = (ChatGlassSurface) header.getBackground();
            check(glass.mode == ChatGlassSurface.LIQUID && glass.glassBlur == 8 && glass.radius == 24f && !glass.dark,
                    "header glass uses the shared mode/blur and the card corner");
            int constructions = ChatGlassSurface.constructions;
            for (int i = 0; i < 8; i++) FloatingHeaderStyle.applyCard(header, card, 24, 4, ChatGlassSurface.LIQUID, 8);
            check(header.getBackground() == glass && ChatGlassSurface.constructions == constructions,
                    "per-frame header card reuses one glass surface");
            check(header.getClipToOutline() && header.getElevation() == 4f,
                    "header outline clip and elevation survive the material swap");
            int stops = ChatGlassSurface.stops;
            FloatingHeaderStyle.applyCard(header, card, 24, 4, ChatGlassSurface.PLAIN, 0);
            check(header.getBackground() instanceof android.graphics.drawable.GradientDrawable
                            && ChatGlassSurface.stops == stops + 1,
                    "plain applyCard drops back to a solid fill and releases the sampler");
            FloatingHeaderStyle.restore(card);
            check(header.getBackground() == nativeBackground,
                    "restore returns the host background, never a glass surface");
            ChatGlassSurface.forced = ChatGlassSurface.PLAIN;
            decor.removeView(header); decor.detach();
        }

        // ── White-bar regression: re-applying a cached separated state must keep the
        // host card visuals neutralized (WeKit neutralizeSeparatedHeader runs per frame).
        // The old code only neutralized on first creation, so a later applyCard() re-fit
        // a full-width white GradientDrawable + elevation onto the ActionBarContainer and
        // re-apply never cleared it — that is the screenshot white bar.
        {
            LauncherUI activity = new LauncherUI();
            ViewGroup decor = root(activity); decor.attach();
            RelativeLayout island = new RelativeLayout(activity); decor.addView(island);
            ActionBarContainer header = new ActionBarContainer(activity);
            androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(activity);
            RelativeLayout parts = new RelativeLayout(activity);
            toolbar.addView(parts, new ViewGroup.LayoutParams(-1, 60));
            header.addView(toolbar, new ViewGroup.LayoutParams(-1, 60));
            island.addView(header, margin(12, 3, 12, 0));
            ViewGroup left = new ViewGroup(activity);
            left.addView(new ImageView(activity), new ViewGroup.LayoutParams(100, 60));
            TextView title = new TextView(activity); title.setText("标题");
            RelativeLayout.LayoutParams titleRule = new RelativeLayout.LayoutParams(-2, 60); titleRule.addRule(RelativeLayout.CENTER_IN_PARENT);
            ViewGroup right = new ViewGroup(activity);
            LinearLayout menuHost = new LinearLayout(activity);
            ViewGroup action = new ViewGroup(activity);
            menuHost.addView(action, new ViewGroup.LayoutParams(44, 60));
            right.addView(menuHost, new ViewGroup.LayoutParams(44, 60));
            parts.addView(left, 0, new RelativeLayout.LayoutParams(100, 60));
            parts.addView(title, 1, titleRule);
            RelativeLayout.LayoutParams rightRule = new RelativeLayout.LayoutParams(44, 60); rightRule.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            parts.addView(right, 2, rightRule);
            header.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));
            header.layout(0, 0, 600, 60);
            // Rule-blind double containers misplace anchored children; pin the leaves.
            left.layout(0, 0, 100, 60);
            title.layout(100, 0, 300, 60);
            right.layout(556, 0, 600, 60);
            Object state = apply.invoke(null, header, null, corner, gap, elevation);
            check(state != null, "white-bar: separated state built");
            check(header.getBackground() instanceof android.graphics.drawable.ColorDrawable
                    && ((android.graphics.drawable.ColorDrawable) header.getBackground()).getColor() == android.graphics.Color.TRANSPARENT,
                "white-bar: creation neutralizes header background");
            check(header.getStateListAnimator() == null, "white-bar: creation clears state list animator");
            // Frame 2: a late applyCard() re-fits the integrated card visuals; the next
            // cached re-apply must re-neutralize instead of leaving the white bar.
            header.setBackground(new android.graphics.drawable.GradientDrawable());
            header.setElevation(16f);
            header.setStateListAnimator(new android.animation.StateListAnimator());
            toolbar.setElevation(8f);
            state = apply.invoke(null, header, state, corner, gap, elevation);
            check(state != null && state == apply.invoke(null, header, state, corner, gap, elevation),
                "white-bar: cached re-apply reuses state");
            check(header.getBackground() instanceof android.graphics.drawable.ColorDrawable
                    && ((android.graphics.drawable.ColorDrawable) header.getBackground()).getColor() == android.graphics.Color.TRANSPARENT,
                "white-bar: re-apply re-neutralizes header background (no full-width white card) got " + header.getBackground());
            check(header.getElevation() == 0f && toolbar.getElevation() == 0f,
                "white-bar: re-apply re-clears host elevation got header=" + header.getElevation() + " toolbar=" + toolbar.getElevation());
            check(header.getStateListAnimator() == null, "white-bar: re-apply re-clears state list animator");

            // Shadow outdent: the decoration LAYER (parent of the drawing view) must be
            // larger than the whole blur footprint, otherwise the software bitmap cuts
            // the shadow into a rectangle at its own edge.
            View decoration = decorationOf(state);
            ViewGroup layer = (ViewGroup) decoration.getParent();
            ViewGroup.MarginLayoutParams decorationParams = (ViewGroup.MarginLayoutParams) layer.getLayoutParams();
            int expectedPadding = Math.max(2, Math.round(30f));
            check(decorationParams.leftMargin == -expectedPadding && decorationParams.topMargin == -expectedPadding,
                "white-bar: decoration outdented by negative shadow margins got " + decorationParams.leftMargin + "/" + decorationParams.topMargin + " expected -" + expectedPadding);
            check(layer.getLayoutParams().width == 600 + expectedPadding * 2 && layer.getLayoutParams().height == 60 + expectedPadding * 2,
                "white-bar: decoration enlarged by shadow padding got " + layer.getLayoutParams().width + "x" + layer.getLayoutParams().height);

            // Dark-mode stroke: WeKit strokes each pill 1dp 0x24FFFFFF in dark mode. The
            // stroke width is latched by refresh() under dark theme; a re-apply in dark
            // mode yields a stroke pass after the fill pass (2× non-shadow records).
            wex.ui.core.feature.SettingsTheme.dark = true;
            try {
                apply.invoke(null, header, state, corner, gap, elevation);
                android.graphics.Canvas strokeCanvas = new android.graphics.Canvas();
                Method strokeDraw = decoration.getClass().getDeclaredMethod("onDraw", android.graphics.Canvas.class);
                strokeDraw.setAccessible(true);
                strokeDraw.invoke(decoration, strokeCanvas);
                int fillRecords = 0;
                for (int i = 0; i < strokeCanvas.roundPaints.size(); i++) {
                    if (!strokeCanvas.roundPaints.get(i)[0]) fillRecords++; // non-shadow draws: fill + stroke passes
                }
                check(fillRecords == 6, // white-bar tree: 3 pills (left/title/right) × (fill + stroke)
                    "white-bar: dark mode strokes every pill (fill+stroke passes) got " + fillRecords + " expected 6");
            } finally {
                wex.ui.core.feature.SettingsTheme.dark = false;
            }
            restore.invoke(null, state);
            decor.removeView(island); decor.detach();
        }
        notificationHeaderGeometry();
        System.out.println("GREEN separated-geometry: " + checks + " assertions (real FloatingHeaderStyle pill geometry: icon-centered circles, visible-action reserves, no title clipping, per-frame host neutralization, shadow outdent, notification parent measurement)");
    }

    private static void notificationHeaderGeometry() throws Exception {
        ChattingUI activity = new ChattingUI();
        ViewGroup decor = root(activity); decor.attach();
        ActionBarContainer header = new ActionBarContainer(activity);
        // Notification ChattingMainUI uses ActionBarOverlayLayout: it measures its
        // known native children, but lays out every child using its measured size.
        ViewGroup overlay = new ViewGroup(activity) {
            @Override protected void onMeasure(int width, int height) {
                header.measure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(width) - 24, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));
                setMeasuredDimension(View.MeasureSpec.getSize(width), View.MeasureSpec.getSize(height));
            }
        };
        decor.addView(overlay); overlay.setPadding(7, 11, 0, 0);
        overlay.addView(header, margin(12, 45, 12, 0));
        androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(activity);
        RelativeLayout parts = new RelativeLayout(activity);
        header.addView(toolbar, new ViewGroup.LayoutParams(-1, 60));
        toolbar.addView(parts, new ViewGroup.LayoutParams(-1, 60));
        View back = new View(activity), menu = new View(activity);
        TextView title = new TextView(activity); title.setText("Notification chat");
        RelativeLayout.LayoutParams titleRule = new RelativeLayout.LayoutParams(-2, 60);
        titleRule.addRule(RelativeLayout.CENTER_IN_PARENT);
        RelativeLayout.LayoutParams menuRule = new RelativeLayout.LayoutParams(60, 60);
        menuRule.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        parts.addView(back, new RelativeLayout.LayoutParams(60, 60));
        parts.addView(title, titleRule); parts.addView(menu, menuRule);
        Method apply = FloatingHeaderStyle.class.getDeclaredMethod("applySeparated", View.class, FloatingHeaderStyle.SeparatedState.class, int.class, int.class, int.class);
        apply.setAccessible(true);
        Method restore = FloatingHeaderStyle.class.getDeclaredMethod("restoreSeparated", FloatingHeaderStyle.SeparatedState.class);
        restore.setAccessible(true);
        Object state = null;
        for (int width : new int[]{600, 600, 720}) {
            overlay.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
            overlay.layout(20, 30, 20 + width, 930);
            state = apply.invoke(null, header, state, 24, 8, 4);
            check(state != null, "notification header has a separated decoration");
            View decoration = decorationOf(state);
            View layer = (View) decoration.getParent();
            check(layer.getMeasuredWidth() == layer.getLayoutParams().width
                            && layer.getMeasuredHeight() == layer.getLayoutParams().height
                            && decoration.getWidth() == layer.getLayoutParams().width
                            && decoration.getHeight() == layer.getLayoutParams().height,
                    "notification parent must not leave the glass layer at zero or stale size");
            int[] layerAt = new int[2], rootAt = new int[2];
            layer.getLocationInWindow(layerAt); parts.getLocationInWindow(rootAt);
            check(layerAt[0] == rootAt[0] - 30 && layerAt[1] == rootAt[1] - 30,
                    "notification glass follows the title through parent padding and resize");
            android.graphics.Canvas canvas = new android.graphics.Canvas();
            Method draw = decoration.getClass().getDeclaredMethod("onDraw", android.graphics.Canvas.class);
            draw.setAccessible(true); draw.invoke(decoration, canvas);
            check(!canvas.roundPaints.isEmpty(), "notification glass paints in the current frame");
        }
        restore.invoke(null, state);
        check(overlay.getChildCount() == 1 && overlay.getChildAt(0) == header,
                "notification decoration cleanup leaves the native title in place");
        decor.removeView(overlay); decor.detach();
    }

    private static void init() throws Exception {        FloatingChatHeaderFeature.init(ChatHeaderToolbarChecks.class.getClassLoader());
        XC_LoadPackage.LoadPackageParam param = new XC_LoadPackage.LoadPackageParam(); param.classLoader = ChatHeaderToolbarChecks.class.getClassLoader();
        ChatToolbarFeature.INSTANCE.init(param);
    }

    private static void existingTagRecovery(boolean preFix) throws Throwable {
        flags(false,true);
        LauncherUI activity = new LauncherUI(); root(activity).attach();
        ChatFooter footer = new ChatFooter(activity); root(activity).addView(footer, margin(0,0,0,0));
        XposedBridge.constructed(footer);
        for (int i=0;i<12;i++) Handler.tick();
        check(footer.attachListeners.size() == 1 && countToolbar(footer) == 0,
                "initial observation keeps waiting slowly when the input column is missing");
        LinearLayout input = input(activity); footer.addView(input);
        // No global-layout event is dispatched: specifically test the existing tag
        // after-attach path that used to return without refreshing its lifecycle.
        XposedBridge.invoke(footer,"onAttachedToWindow",new Class<?>[0]); Handler.drain();
        check(countToolbar(footer) == (preFix ? 0 : 1), "tagged after-attach refresh recovers existing lifecycle");
        check(footer.attachListeners.size() == 1, "tagged callback keeps original attach listener");
        stop(activity);
    }

    private static void toolbarNoisyPanels() throws Throwable {
        flags(false, true);
        LauncherUI activity = new LauncherUI(); root(activity).attach();
        ChatFooter footer = new ChatFooter(activity); root(activity).addView(footer);
        ViewGroup body = new ViewGroup(activity); footer.addView(body); body.addView(new View(activity));
        LinearLayout column = input(activity); body.addView(column);
        com.tencent.mm.pluginsdk.ui.chat.AppPanel huge = new com.tencent.mm.pluginsdk.ui.chat.AppPanel(activity) {
            @Override public int getChildCount() { return 10000; }
            @Override public View getChildAt(int index) { throw new AssertionError("native panel must not be traversed"); }
        };
        huge.setVisibility(View.GONE); footer.addView(huge);
        Method finder = ChatToolbarFeature.class.getDeclaredMethod("findInputContainer", ViewGroup.class);
        finder.setAccessible(true);
        check(finder.invoke(null, footer) == column, "visible input survives a huge hidden native panel");
        Object nativeTag = new Object(); footer.setTag(0x7E000102, nativeTag);
        XposedBridge.invoke(footer, "onLayout", new Class<?>[]{boolean.class,int.class,int.class,int.class,int.class},
                true, 0, 0, 400, 60);
        Handler.drain();
        check(column.findViewWithTag(TOOLBAR) != null, "layout-only entry mounts even when constructor/attach were missed");
        check(footer.getTag(0x7E000102) == nativeTag, "a foreign keyed tag is preserved and does not gate our lifecycle");
        huge.setVisibility(View.VISIBLE);
        check(finder.invoke(null, footer) == column, "known input column avoids a visible native panel too");
        footer.removeView(body);
        LinearLayout real = input(activity), decoy = input(activity); decoy.setVisibility(View.GONE);
        footer.addView(real); footer.addView(decoy);
        check(finder.invoke(null, footer) == real, "fallback rejects a hidden later editor column");
        stop(activity);
    }

    private static void pinnedCardGeometry() throws Throwable {
        flags(true, false);
        LauncherUI activity = new LauncherUI(); root(activity).attach();
        Tree chat = tree(activity, false, false);
        View scroll = chat.layout.getChildAt(0); chat.layout.removeView(scroll);
        android.widget.FrameLayout content = new android.widget.FrameLayout(activity);
        chat.layout.addView(content); content.addView(scroll); content.layout(0,40,400,800);
        content.setPadding(0,13,0,0);
        com.tencent.mm.ui.chatting.view.MMChattingListView messages =
                new com.tencent.mm.ui.chatting.view.MMChattingListView(activity);
        content.addView(messages);
        com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView list =
                new com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView(activity);
        messages.addView(list);
        for (int i=0;i<300;i++) list.addView(new View(activity));
        list.setPadding(2,20,3,4);
        com.tencent.mm.ui.tipsbar.ChatTipsBarGroup tips = new com.tencent.mm.ui.tipsbar.ChatTipsBarGroup(activity);
        tips.target = list;
        content.addView(tips, margin(1,2,3,4));
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(activity); tips.addView(wrapper);
        RelativeLayout panel = new RelativeLayout(activity); panel.setId(0x7f093d1f);
        ColorDrawable original = new ColorDrawable(0xFFF7F7F7); panel.setBackground(original); wrapper.addView(panel);
        // Expand scrim: a plain full-size View that stretched the group (and its glass) to the screen bottom.
        View scrim = new View(activity); ColorDrawable scrimColor = new ColorDrawable(0x80000000);
        scrim.setBackground(scrimColor);
        wrapper.addView(scrim, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewGroup items = new ViewGroup(activity); items.setId(0x7f095a10); panel.addView(items);
        // One pinned entry: a container with WeChat's own white card around a leaf that draws itself.
        ViewGroup entry = new ViewGroup(activity); ColorDrawable entryCard = new ColorDrawable(0xFFFFFFFF);
        entry.setBackground(entryCard); items.addView(entry);
        View item = new View(activity); ColorDrawable itemColor = new ColorDrawable(0xFF123456);
        item.setBackground(itemColor); entry.addView(item);
        XposedBridge.constructed(chat.layout); Handler.drain();
        check(FloatingChatFooterFeature.adopted.contains(chat.footer),
                "header pre-draw hands the page's ChatFooter to the footer feature (old GlassPatch.onFrame backstop)");
        check(((ViewGroup.MarginLayoutParams)tips.getLayoutParams()).topMargin == 19,
                "pinned card compensates both parent screen y40 and padding13");
        int y = content.getPaddingTop() + ((ViewGroup.MarginLayoutParams)tips.getLayoutParams()).topMargin;
        tips.layout(12,y,388,y+64);
        int[] position = new int[2]; tips.getLocationOnScreen(position);
        check(position[1] == 72, "pinned card screen top clears header64 and gap8");
        check(tips.getElevation() == 4f, "pinned card is found beyond a populated message subtree");
        check(panel.getBackground() == null && item.getBackground() == itemColor,
                "nested tip chrome clears without touching host item backgrounds");
        check(entry.getBackground() == null,
                "pinned entry card loses its white fill so the glass shows through (old GlassPatch.onCardApplied)");
        check(scrim.getVisibility() == View.GONE && scrim.getAlpha() == 0f && scrim.getBackground() == null,
                "expand scrim no longer stretches the pinned card to the screen bottom");
        check(panel.getVisibility() == View.VISIBLE && item.getVisibility() == View.VISIBLE,
                "only the structural scrim is hidden, never the card body or its rows");
        check(list.getPaddingTop() == 92, "pinned card adds header64 and gap8 to native list padding20");
        for(int i=0;i<4;i++) chat.layout.getViewTreeObserver().draw();
        check(list.getPaddingTop() == 92, "stable frames do not accumulate our list inset");
        wrapper.layout(0,0,376,64); panel.layout(0,6,376,46);
        chat.layout.getViewTreeObserver().draw();
        // Only the style double records the clamp; the separated-geometry variant compiles the real style.
        Integer clamped = clampedForTest();
        if (clamped != null) check(clamped == 46,
                "pinned card height follows the card body bottom (wrapper 0 + panel 6 + 40), not the group");
        XposedBridge.invoke(tips,"setListViewPaddingTop",new Class<?>[]{int.class},72);
        check(tips.observedTop == 20 && list.getTag(0).equals(20),
                "native baseline capture sees its own padding without our inset");
        check(list.getPaddingTop() == 164,
                "native write equal to our previous number still receives the full inset");
        chat.layout.getViewTreeObserver().draw();
        check(list.getPaddingTop() == 164, "native writer reapply is stable on the next frame");
        list.setPadding(2,40,3,4); chat.layout.getViewTreeObserver().draw();
        check(list.getPaddingTop() == 112, "native padding overwrite gets our full inset reapplied");
        XposedBridge.invoke(tips,"setListViewPaddingTop",new Class<?>[]{int.class},60);
        chat.layout.getViewTreeObserver().draw();
        check(list.getPaddingTop() == 152, "native fold expansion keeps its own new list padding");
        tips.setVisibility(View.GONE); chat.layout.getViewTreeObserver().draw();
        check(list.getPaddingTop() == 144, "hidden tip removes only its extra gap");
        chat.layout.setVisibility(View.GONE);
        XposedBridge.invoke(tips,"setListViewPaddingTop",new Class<?>[]{int.class},60);
        check(list.getPaddingTop() == 80, "native writer on a hidden chat does not reapply header inset");
        chat.layout.setVisibility(View.VISIBLE); chat.layout.getViewTreeObserver().draw();
        check(list.getPaddingTop() == 144, "visible chat recovers its inset after hidden native writes");
        stop(activity);
        check(list.getPaddingTop() == 80 && panel.getBackground() == original,
                "disable preserves latest native padding and restores nested chrome");
        check(entry.getBackground() == entryCard && item.getBackground() == itemColor,
                "disable restores the pinned entry card");
        check(scrim.getBackground() == scrimColor && scrim.getAlpha() == 1f && scrim.getVisibility() == View.GONE,
                "disable restores the scrim fill but leaves showing it to the host's next expand");
        check(((ViewGroup.MarginLayoutParams)tips.getLayoutParams()).topMargin == 2,
                "disable restores original pinned-card margin");
    }

    private static Integer clampedForTest() {
        try { return FloatingHeaderStyle.class.getField("clamped").getInt(null); }
        catch (ReflectiveOperationException error) { return null; }
    }

    private static void panelSnapshotEntry() throws Throwable {
        flags(false,true);
        LauncherUI activity = new LauncherUI(); root(activity).attach();
        ChatFooter footer = new ChatFooter(activity); root(activity).addView(footer);
        footer.addView(input(activity));
        com.tencent.mm.pluginsdk.ui.chat.AppPanel panel = new com.tencent.mm.pluginsdk.ui.chat.AppPanel(activity);
        footer.addView(panel);
        android.widget.AbsListView grid = new android.widget.AbsListView(activity) {
            @Override public android.widget.ListAdapter getAdapter() {
                return new android.widget.ListAdapter() {
                    public int getCount(){return 13;}
                    public View getView(int pos,View old,ViewGroup parent){
                        TextView label = new TextView(activity); label.setText("tool-" + pos); return label;
                    }
                };
            }
        };
        java.lang.reflect.Field listener = android.widget.AdapterView.class.getDeclaredField("mOnItemClickListener");
        listener.setAccessible(true);
        listener.set(grid, (android.widget.AdapterView.OnItemClickListener)(parent,view,pos,id) -> {});
        panel.addView(grid);
        check(footer.attachListeners.isEmpty(), "native panel test has missed constructor and attach callbacks");
        XposedBridge.invoke(panel,"initGrid",new Class<?>[]{com.tencent.mm.pluginsdk.ui.chat.AppPanel.class},panel);
        Handler.drain();
        check(countToolbar(footer)==1, "native 13-tool snapshot callback independently observes and mounts its footer");
        check(XposedBridge.logs.stream().anyMatch(s -> s.contains("after 快照，工具数=13")),
                "native adapter snapshot preserves all 13 actions");
        check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=panel-owner found=true")),
                "native snapshot records its footer owner without message content");
        stop(activity);
    }

    private static void lateInputAfterExhaustion() throws Throwable {
        flags(false,true);
        ChattingUI activity = new ChattingUI(); root(activity).attach();
        ChatFooter footer = new ChatFooter(activity); root(activity).addView(footer); XposedBridge.constructed(footer);
        for (int i=0;i<14;i++) Handler.tick();
        check(countToolbar(footer) == 0 && Handler.pending() == 1,
                "slow retry keeps exactly one pending attempt while the input column is missing");
        LinearLayout first = input(activity); ColorDrawable original = new ColorDrawable(0xFFF0F0F0); first.setBackground(original); footer.addView(first);
        Handler.tick();
        check(countToolbar(footer) == 1 && first.findViewWithTag(TOOLBAR) != null,
                "slow retry mounts when the late input column finally appears");
        int additions = first.additions;
        for (int i=0;i<20;i++) footer.getViewTreeObserver().global(); Handler.drain();
        check(first.additions == additions && countToolbar(footer) == 1, "stable layout events do not restart or add duplicate toolbar");
        check(Handler.pending() == 2, "stable layouts retain one load task and one settle probe");
        View lostShell = first.findViewWithTag(TOOLBAR);
        first.removeView(lostShell);
        footer.getViewTreeObserver().global(); Handler.tick(); Handler.drain();
        check(countToolbar(footer) == 1 && first.findViewWithTag(TOOLBAR) != lostShell,
                "same input column recovers a toolbar removed by native child reinflation");
        first.findViewWithTag(TOOLBAR).setVisibility(View.GONE);
        footer.getViewTreeObserver().global(); Handler.tick(); Handler.drain();
        check(first.findViewWithTag(TOOLBAR).getVisibility() == View.VISIBLE,
                "same input column restores a shell hidden by native layout");
        footer.detach();
        check(Handler.pending() == 0 && footer.getViewTreeObserver().globals.isEmpty() && countToolbar(footer) == 0, "detach removes watcher, mount and load tasks");
        check(first.getBackground() == original, "detach restores toolbar-owned host background");
        footer.attach(); Handler.drain();
        check(countToolbar(footer) == 1 && footer.attachListeners.size() == 1 && footer.getViewTreeObserver().globals.size() == 1, "reattach reuses lifecycle and inserts once");
        footer.removeView(first);
        LinearLayout second = input(activity); footer.addView(second); footer.getViewTreeObserver().global(); Handler.tick(); Handler.drain();
        check(countToolbar(footer) == 1 && second.findViewWithTag(TOOLBAR) != null, "newly inflated replacement input host mounts once");
        stop(activity);
        check(Handler.pending() == 0 && second.findViewWithTag(TOOLBAR) == null, "disable cancels replacement load and removes shell");
    }

    private static void delayedTreesAndCancellation() throws Throwable {
        flags(true,true);
        LauncherUI activity = new LauncherUI(); root(activity).attach(); resume(activity); Handler.drain();
        check(Handler.pending() == 2, "no chat tree schedules one bounded discovery per feature");
        Tree late = tree(activity,true,false); Handler.tick();
        check(late.layout.attachListeners.size() == 1 && countToolbar(late.footer) == 1, "delayed chat subtree discovered in current Activity");
        stop(activity);
        flags(true,true);
        LauncherUI detached = new LauncherUI(); root(detached).attach(); resume(detached); Handler.drain();
        root(detached).detach(); check(Handler.pending() == 0, "decor detach cancels both feature discoveries"); stop(detached);
        flags(true,true);
        LauncherUI paused = new LauncherUI(); root(paused).attach(); resume(paused); Handler.drain();
        XposedBridge.invoke(paused,"onPause",new Class<?>[0]); check(Handler.pending() == 0, "inherited pause cancels both discovery tasks"); stop(paused);
        flags(true,true);
        ChattingUI exhausted = new ChattingUI(); root(exhausted).attach(); resume(exhausted); discoveryRounds();
        check(Handler.pending() > 0, "missing chat tree keeps one slow discovery attempt pending");
        stop(exhausted);
        check(Handler.pending() == 0, "leaving an absent chat tree cancels slow discovery");
    }

    private static void preciseHeaderFallback() throws Throwable {
        flags(true,false);
        LauncherUI activity = new LauncherUI(); Tree embedded = tree(activity,false,false);
        ActionBarContainer home = new ActionBarContainer(activity); root(activity).addView(home); root(activity).attach();
        check(findHeader(embedded.layout,activity) == embedded.title, "embedded fallback only unique chat-island sibling");
        ActionBarContainer duplicate = new ActionBarContainer(activity); embedded.island.addView(duplicate);
        check(findHeader(embedded.layout,activity) == null, "ambiguous embedded sibling titles rejected");
        embedded.island.removeView(duplicate);
        embedded.island.setId(android.R.id.content);
        check(findHeader(embedded.layout,activity) == null, "never use activity content/home root as embedded fallback");
        embedded.island.setId(View.NO_ID);
        ActionBarContainer localOne = new ActionBarContainer(activity), localTwo = new ActionBarContainer(activity);
        embedded.layout.addView(localOne); embedded.layout.addView(localTwo);
        check(findHeader(embedded.layout,activity) == null, "ambiguous local titles cannot fall through to sibling");
        embedded.layout.removeView(localOne); embedded.layout.removeView(localTwo);
        stop(activity);

        flags(true,false);
        ChattingUI standalone = new ChattingUI(); ViewGroup content = new ViewGroup(standalone); content.setId(android.R.id.content); root(standalone).addView(content);
        ChattingUILayout layout = new ChattingUILayout(standalone); content.addView(layout);
        ActionBarContainer title = new ActionBarContainer(standalone); root(standalone).addView(title); root(standalone).attach();
        check(findHeader(layout,standalone) == title, "standalone exact ChattingUI unique decor title retained");
        content.addView(new ChattingUILayout(standalone));
        check(findHeader(layout,standalone) == null, "standalone fallback rejects multiple chat layouts"); stop(standalone);

        flags(true,false);
        LauncherUI noMargins = new LauncherUI(); Tree tree = tree(noMargins,false,true); root(noMargins).attach();
        XposedBridge.constructed(tree.layout); Handler.drain();
        check(tree.title.getElevation() == 4f, "non-margin title params no longer abort basic header apply"); stop(noMargins);
    }

    private static void headerReparentAndDetach() throws Throwable {
        flags(true,false);
        LauncherUI activity = new LauncherUI(); ViewGroup decor = root(activity); decor.attach();
        RelativeLayout island = new RelativeLayout(activity); decor.addView(island);
        ChattingUILayout layout = new ChattingUILayout(activity); island.addView(layout);
        ChattingScrollLayout content = new ChattingScrollLayout(activity); layout.addView(content);
        XposedBridge.constructed(layout);
        check(layout.getViewTreeObserver().listeners.size() == 1, "header waits on existing layout for late title");
        ActionBarContainer title = new ActionBarContainer(activity); layout.addView(title,margin(2,3,4,5));
        layout.getViewTreeObserver().draw();
        check(Handler.pending() == 1, "late local title schedules one guarded reparent");
        layout.detach();
        check(Handler.pending() == 0 && title.getParent() == layout && layout.getViewTreeObserver().listeners.isEmpty(), "detach cancels queued reparent and restores without moving title");
        layout.attach(); Handler.drain();
        check(title.getParent() == island && title.getElevation() == 4f, "reattach can reparent local chat title after host inflation");
        int additions = island.additions;
        for(int i=0;i<10;i++) layout.getViewTreeObserver().draw(); Handler.drain();
        check(island.additions == additions && layout.attachListeners.size() == 1, "moved header reused, no repeated reparent or listener");
        stop(activity);
        check(title.getParent() == layout && ((ViewGroup.MarginLayoutParams)title.getLayoutParams()).leftMargin == 2, "disable restores original title parent and params");
    }

    private static void discoveryBoundaries() throws Throwable {
        flags(true,true);
        LauncherUI current = new LauncherUI(), foreign = new LauncherUI();
        Tree wrong = tree(foreign,true,false); ViewGroup foreignIsland = wrong.island;
        root(foreign).removeView(foreignIsland); root(current).addView(foreignIsland); root(current).attach();
        resume(current); discoveryRounds();
        check(wrong.layout.attachListeners.isEmpty() && wrong.footer.attachListeners.isEmpty(), "foreign-context trees not rediscovered"); stop(current);
        flags(true,true);
        LauncherUI ambiguous = new LauncherUI(); Tree one = tree(ambiguous,true,false), two = tree(ambiguous,true,false); root(ambiguous).attach();
        resume(ambiguous); discoveryRounds();
        check(one.layout.attachListeners.isEmpty() && two.layout.attachListeners.isEmpty() && countToolbar(one.footer)==0 && countToolbar(two.footer)==0, "multiple active exact chat trees rejected"); stop(ambiguous);
        flags(true,true);
        LauncherUI hidden = new LauncherUI(); Tree invisible = tree(hidden,true,false); invisible.island.setVisibility(View.GONE); root(hidden).attach(); resume(hidden); discoveryRounds();
        check(invisible.layout.attachListeners.isEmpty() && invisible.footer.attachListeners.isEmpty(), "hidden chat tree excluded"); stop(hidden);
        flags(true,true);
        LauncherUI huge = new LauncherUI(); root(huge).attach(); root(huge).addView(new ViewGroup(huge) {
            @Override public int getChildCount(){return 10000;}
            @Override public View getChildAt(int i){throw new AssertionError("admitted-node scan cap exceeded");}
        });
        resume(huge); discoveryRounds(); check(Handler.pending()>0, "discovery bounds enqueue bounded work as well as visits"); stop(huge);
        flags(false,true);
        LauncherUI noisy = new LauncherUI(); Tree valid = tree(noisy,true,false);
        ViewGroup noise = new ViewGroup(noisy) {
            @Override public int getChildCount(){return 10000;}
            @Override public View getChildAt(int i){throw new AssertionError("hidden sibling must not be traversed");}
        };
        noise.setVisibility(View.GONE); root(noisy).addView(noise); root(noisy).attach();
        resume(noisy); Handler.drain();
        check(countToolbar(valid.footer)==1, "hidden oversized siblings do not veto an otherwise unique footer");
        stop(noisy);
    }

    /** A notification tap reuses the running task: the chat page can arrive through
     *  onNewIntent or onStart with no further onResume for that Activity. Nothing else
     *  re-runs discovery, so the entry hook is the only recovery path. */
    private static void notificationEntry(boolean entryHooked) throws Throwable {
        resetEntryLogs();
        flags(true, true);
        Handler.finish(); // settle the bounded start-up enable probe before counting work
        LauncherUI background = new LauncherUI(); root(background).attach(); resume(background);
        XposedBridge.invoke(background, "onPause", new Class<?>[0]); Handler.drain();
        check(Handler.pending() == 0, "a backgrounded task keeps no discovery running");
        // The chat page is already inflated inside the reused task when the notification
        // is delivered; only the entry callback can restart decoration.
        LauncherUI notified = new LauncherUI();
        Tree chat = tree(notified, true, false); root(notified).attach();
        XposedBridge.invoke(notified, "onNewIntent",
                new Class<?>[]{android.content.Intent.class}, new android.content.Intent());
        Handler.drain();
        if (!entryHooked) {
            check(chat.layout.attachListeners.isEmpty() && chat.footer.attachListeners.isEmpty(),
                    "without the entry hook the notification chat page stays undecorated");
            check(countToolbar(chat.footer) == 0, "without the entry hook no input toolbar is mounted");
            check(chat.title.getElevation() == 0f, "without the entry hook the floating header is not applied");
            return;
        }
        check(hookCount(android.app.Activity.class, "onNewIntent", android.content.Intent.class) == 2,
                "both features hook Activity.onNewIntent for notification entry");
        check(hookCount(android.app.Activity.class, "onStart") == 2,
                "both features hook Activity.onStart for notification entry");
        check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=entry trigger=onNewIntent")),
                "entry diagnostics report the notification trigger");
        check(chat.layout.attachListeners.size() == 1, "notification entry discovers the floating header tree");
        check(chat.footer.attachListeners.size() == 1 && countToolbar(chat.footer) == 1,
                "notification entry mounts the input toolbar");
        check(chat.title.getElevation() == 4f, "notification entry applies the floating header");
        stop(notified);
        check(Handler.pending() == 0, "notification entry leaves no pending discovery");

        // Same task brought forward through onStart only: still no onResume of its own.
        resetEntryLogs();
        flags(true, true);
        LauncherUI started = new LauncherUI();
        Tree again = tree(started, true, false); root(started).attach();
        XposedBridge.invoke(started, "onStart", new Class<?>[0]); Handler.drain();
        check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=entry trigger=onStart")),
                "entry diagnostics report the start trigger");
        check(again.footer.attachListeners.size() == 1 && countToolbar(again.footer) == 1,
                "onStart entry alone mounts the input toolbar");
        check(again.title.getElevation() == 4f, "onStart entry alone applies the floating header");
        stop(started);
        check(Handler.pending() == 0, "onStart entry leaves no pending discovery");
    }

    /** Log budget is shared across the whole JVM run; reset it so this scenario can
     *  still observe its own entry diagnostics. */
    private static void resetEntryLogs() throws Exception {
        for (Class<?> type : new Class<?>[]{FloatingChatHeaderFeature.class, ChatToolbarFeature.class}) {
            java.lang.reflect.Field field = type.getDeclaredField("entryLogs");
            field.setAccessible(true);
            field.setInt(null, 0);
        }
    }

    private static void toolSettingsRoundtrip() {
        String[] initial = ChatToolbarFeature.toolNames();
        ChatToolbarFeature.moveTool(initial[1], -1);
        check(ChatToolbarFeature.toolNames()[0].equals(initial[1]), "settings reader follows saved tool order");
        ChatToolbarFeature.moveTool(initial[1], 1);
        check(ChatToolbarFeature.toolNames()[0].equals(initial[0]), "consecutive reorder is reflected immediately");
        for (String name : initial) ChatToolbarFeature.setToolEnabled(name, false);
        for (String name : initial) check(!ChatToolbarFeature.isToolEnabled(name), "last disabled tool stays disabled");
        ChatToolbarFeature.setToolEnabled(initial[0], true);
        check(ChatToolbarFeature.isToolEnabled(initial[0]) && !ChatToolbarFeature.isToolEnabled(initial[1]), "explicit empty selection can re-enable one tool");
        WexStore.INSTANCE.putString("chat_toolbar_order", "");
        WexStore.INSTANCE.putString("chat_toolbar_enabled_items", "");
        Handler.drain();
    }

    private static int hookCount(Class<?> owner, String name, Class<?>... types) {
        java.lang.reflect.Method method = null;
        for (Class<?> type = owner; type != null && method == null; type = type.getSuperclass()) {
            try { method = type.getDeclaredMethod(name, types); } catch (NoSuchMethodException ignored) { }
        }
        List<?> callbacks = method == null ? null : XposedBridge.hooks.get(method);
        return callbacks == null ? 0 : callbacks.size();
    }

    private static final class Tree {
        RelativeLayout island; ChattingUILayout layout; ChatFooter footer; LinearLayout input; ActionBarContainer title;
    }
    private static Tree tree(Activity activity,boolean withInput,boolean plainTitleParams) {
        Tree t=new Tree(); t.island=new RelativeLayout(activity);root(activity).addView(t.island);
        t.layout=new ChattingUILayout(activity);t.island.addView(t.layout);
        ChattingScrollLayout scroll=new ChattingScrollLayout(activity);t.layout.addView(scroll);
        t.footer=new ChatFooter(activity);scroll.addView(t.footer,margin(0,0,0,0));
        if(withInput){t.input=input(activity);t.footer.addView(t.input);}
        t.title=new ActionBarContainer(activity);t.island.addView(t.title,plainTitleParams?new ViewGroup.LayoutParams(-1,60):margin(2,3,4,5));
        return t;
    }
    private static LinearLayout input(Context activity) {LinearLayout host=new LinearLayout(activity);host.setOrientation(LinearLayout.VERTICAL);EditText editable=new EditText(activity);editable.setText("secret-chat-body");host.addView(editable);return host;}
    private static View findHeader(View layout,Activity activity)throws Exception {Method m=FloatingChatHeaderFeature.class.getDeclaredMethod("findHeader",View.class,Activity.class);m.setAccessible(true);return (View)m.invoke(null,layout,activity);}
    /** The separated decorator is the software-layer child of SeparatedState.layer. */
    private static View decorationOf(Object state) throws Exception {
        java.lang.reflect.Field layerField = state.getClass().getDeclaredField("layer");
        layerField.setAccessible(true);
        Object layerValue = layerField.get(state);
        if (!(layerValue instanceof ViewGroup)) throw new AssertionError("state.layer missing");
        ViewGroup layer = (ViewGroup) layerValue;
        if (layer.getChildCount() != 1)
            throw new AssertionError("separated layer child count=" + layer.getChildCount());
        View decoration = layer.getChildAt(0);
        if (decoration.getLayerType() != View.LAYER_TYPE_HARDWARE)
            throw new AssertionError("separated decoration is not a hardware glass layer");
        return decoration;
    }
    private static int countToolbar(View root){int count=TOOLBAR.equals(root.getTag())?1:0;if(root instanceof ViewGroup)for(View v:((ViewGroup)root).children)count+=countToolbar(v);return count;}
    private static int chipCount(View footer){ViewGroup shell=(ViewGroup)footer.findViewWithTag(TOOLBAR);ViewGroup scroll=(ViewGroup)shell.getChildAt(0);return ((ViewGroup)scroll.getChildAt(0)).getChildCount();}
    private static int callbackCount(){int count=0;for(List<?> list:XposedBridge.hooks.values())count+=list.size();return count;}
    private static ViewGroup root(Activity a){return (ViewGroup)a.getWindow().getDecorView();}
    private static ViewGroup.MarginLayoutParams margin(int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=new ViewGroup.MarginLayoutParams(-1,-2);p.setMargins(l,t,r,b);return p;}
    private static void flags(boolean header,boolean toolbar){WexStore.INSTANCE.flags.put("floating_chat_header_enabled",header);WexStore.INSTANCE.flags.put("chat_toolbar_enabled",toolbar);FloatingChatHeaderFeature.refresh();ChatToolbarFeature.refresh();Handler.drain();}
    private static void discoveryRounds(){Handler.drain();for(int i=0;i<10;i++)Handler.tick();}
    private static void resume(Activity a)throws Throwable{XposedBridge.invoke(a,"onResume",new Class<?>[0]);}
    private static void stop(Activity a)throws Throwable{flags(false,false);XposedBridge.invoke(a,"onPause",new Class<?>[0]);root(a).detach();Handler.finish();}
    private static void check(boolean valid,String message){checks++;if(!valid)throw new AssertionError(message);}
}

