package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.tencent.mm.pluginsdk.ui.chat.ChatFooter;
import com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom;
import com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout;
import com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout;
import com.tencent.mm.ui.LauncherUI;
import com.tencent.mm.ui.chatting.ChattingUI;
import dev.mindlink.xposed.XposedBridge;
import wex.ui.core.WexStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Runs production init and its registered hooks; no copied observe/apply logic. */
public final class ChatFooterHookChecks {
    private static int assertions;
    private static boolean optionalPresent;
    private static String variant;

    public static void main(String[] args) throws Throwable {
        variant = args[0];
        optionalPresent = "present".equals(variant) || "pre-fix-panel".equals(variant)
                || "pre-fix-pill".equals(variant) || "pre-fix-glass".equals(variant);
        enable(true);
        HostTargetResolver.fail = !optionalPresent;
        // Construct an attached footer before feature init: no constructor/attach hook
        // saw this tree, exactly the late module initialization case.
        LauncherUI lateActivity = new LauncherUI();
        ViewGroup lateRoot = root(lateActivity);
        ChatFooter late = new ChatFooter(lateActivity);
        lateRoot.addView(late, margins(1, 2, 3, -180)); lateRoot.attach();
        FloatingChatFooterFeature.init(ChatFooterHookChecks.class.getClassLoader());
        if ("pre-fix-observe".equals(variant)) {
            try {
                XposedBridge.constructed(new ChatFooter(lateActivity));
                throw new AssertionError("pre-fix observe must reproduce failure");
            } catch (StackOverflowError expected) {
                check(late.attachListeners.isEmpty(), "pre-fix created no state");
                System.out.println("RED confirmed: original observe/state recursion -> StackOverflowError on first real constructor callback");
                return;
            }
        }
        if ("pre-fix-pill".equals(variant)) {
            Handler.drain();
            enable(true);
            FooterTree replay = new FooterTree();
            XposedBridge.constructed(replay.footer); replay.settle();
            XposedBridge.invoke(replay.footer, "switchHost", new Class<?>[]{int.class, boolean.class, int.class}, 0, true, -1);
            root(replay.activity).insets = band(0, 0, 0); replay.settle();
            int plain = cardBottom(replay);
            FloatingFooterLayout.rememberBand(replay.scroll, 48); replay.settle();
            int stripped = cardBottom(replay);
            check(plain == stripped, "pre-fix margins must ignore the remembered gesture band");
            XposedBridge.invoke(replay.footer, "switchHost", new Class<?>[]{int.class, boolean.class, int.class}, 3, true, -1);
            replay.settle();
            check(replay.params.bottomMargin < 8 + 48,
                    "pre-fix plus panel parks on the pill, margin=" + replay.params.bottomMargin);
            System.out.println("RED confirmed: without the band floor the closed card and the plus panel "
                    + "ignore the remembered gesture pill (plain=" + plain + " stripped=" + stripped
                    + " margin=" + replay.params.bottomMargin + ")");
            return;
        }
        if ("pre-fix-panel".equals(variant)) {
            Handler.drain(); FooterTree replay = new FooterTree(); replay.settle();
            XposedBridge.constructed(replay.footer); replay.settle();
            check(replay.panel.getParent() == replay.input, "historical overflow replay also preserves unknown-mode native initialization");
            XposedBridge.invoke(replay.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},0,true,-1); replay.settle();
            check(replay.panel.getParent() == replay.card && replay.panel.getVisibility() == View.VISIBLE,
                    "pre-fix production reparent leaves native panel VISIBLE");
            check(replay.footer.getHeight() == 360 && replay.params.bottomMargin == 8,
                    "pre-fix positive bottom margin exposes full naturally measured hidden panel");
            System.out.println("RED confirmed: production reparent without GONE exposes native collapsed panel -> composer 60 + panel300 = 360");
            return;
        }
        if (args.length > 1 && "repair-only".equals(args[1])) {
            Handler.drain();
            if (args.length > 2 && "lazy".equals(args[2])) lazyFirstNativeOpen();
            else if (args.length > 2 && "keyboard".equals(args[2])) keyboardCloseSequence();
            else if (args.length > 2 && "native-ime".equals(args[2])) nativeImeRequests();
            else ownedPanelSequence();
            return;
        }
        if (args.length > 1 && "geometry-only".equals(args[1])) {
            Handler.drain(); actualMarginGeometry(); freeformWindowGeometry(); return;
        }
        check(HostTargetResolver.calls == 6, "each optional resolver attempted even if first throws");
        int methods = XposedBridge.hooks.size();
        FloatingChatFooterFeature.init(ChatFooterHookChecks.class.getClassLoader());
        check(methods == XposedBridge.hooks.size(), "repeated init does not duplicate hooks");
        check(XposedBridge.constructors.get(ChatFooter.class).size() == 1, "one constructor callback");
        for (List<?> callbacks : XposedBridge.hooks.values()) check(callbacks.size() == 1, "inherited hooks deduplicated");
        if (!optionalPresent) {
            check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=getYFromBottom unavailable=NoSuchMethodException")), "missing getY isolated");
            check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=fitSystemWindows unavailable=NoSuchMethodException")), "missing fit isolated");
        }
        resume(lateActivity);
        check(late.attachListeners.isEmpty(), "resume only queues discovery after host lifecycle");
        Handler.drain();
        check(late.attachListeners.size() == 1, "resume discovers pre-existing exact footer");
        check(late.getBackground() instanceof GradientDrawable, "late discovery actually styles footer");
        check(((ViewGroup.MarginLayoutParams) late.getLayoutParams()).leftMargin == 13, "late discovered footer margins applied");
        stop(lateActivity);
        boundedDiagnostics();
        constructorAndRestore();
        glassMaterial();
        delayedDiscovery();
        discoveryBoundaries();
        disabledFit();
        delayedLayoutAndOwnership();
        fallbackPanelVisibility();
        panelSafety();
        gesturePillBand();
        navStrip();
        naturalPanelStates();
        directionalInsets();
        stripPaddingOwnership();
        edgeRebinding();
        initialNativeClip();
        if (optionalPresent) actualMarginGeometry();
        freeformWindowGeometry();
        if (optionalPresent) { ownedPanelSequence(); lazyFirstNativeOpen(); keyboardCloseSequence(); nativeImeRequests(); }
        if (optionalPresent) maskOscillation();
        check(Handler.pending() == 0, "no leaked retry tasks at suite end");
        check(XposedBridge.logs.stream().noneMatch(s -> s.contains("secret-chat-body")), "diagnostics never log view text");
        System.out.println("GREEN " + variant + ": " + assertions + " assertions (real init/hooks, observe, discovery, style, margins, restore, panel fallback)");
    }

    private static void boundedDiagnostics() throws Throwable {
        enable(true);
        if (optionalPresent) {
            for (String id : new String[]{"refresh", "switch", "config", "keyboard", "fullscreen", "scroll"})
                check(XposedBridge.logs.stream().anyMatch(s -> s.contains("stage=resolve id=floating_footer_" + id + " registered=true method=")), "resolver registered evidence " + id);
            check(XposedBridge.logs.stream().anyMatch(s -> s.contains("#switchHost(IZI)V")), "actual registered descriptor, not cached speculation");
        }
        FooterTree tree = new FooterTree(); tree.settle();
        // Content getters are traps, even when reachable through a known layout node.
        tree.input.addView(new View(tree.activity) {
            public CharSequence getText() { throw new AssertionError("body read"); }
            public CharSequence getHint() { throw new AssertionError("hint read"); }
            public CharSequence getContentDescription() { throw new AssertionError("description read"); }
            @Override public String toString() { throw new AssertionError("view rendered"); }
        }, new ViewGroup.LayoutParams(-1,0));
        int start = XposedBridge.logs.size(); XposedBridge.constructed(tree.footer); tree.settle();
        check(XposedBridge.logs.subList(start, XposedBridge.logs.size()).stream().anyMatch(s -> s.contains("kind=panelReady")
                && s.contains("reason=" + (optionalPresent ? "native-unconfirmed" : "hooks"))), "panelReady accepted/rejection reason");
        Object state = tree.footer.attachListeners.get(0);
        Class<?> type = state.getClass();
        Field frames = type.getDeclaredField("traceFrames"); frames.setAccessible(true);
        check(frames.getInt(state) == 0, "snapshot window ends after transition's three preDraw frames");
        long snapshots = XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().filter(s -> s.contains("kind=snapshot")).count();
        for (int i=0;i<20;i++) { tree.inputRow.natural=60+i; tree.settle(); }
        check(snapshots == XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().filter(s -> s.contains("kind=snapshot")).count(), "no continuous geometry-change snapshots after window");
        if (optionalPresent) {
            XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1); tree.settle();
            check(XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().anyMatch(s -> s.contains("phase=firstOpen") && s.contains("moved=true") && s.contains("outlineOwned=true")), "first open records panel and outline ownership");
            tree.footer.failSwitch=true;
            try { XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,true,-1); throw new AssertionError("native error swallowed"); }
            catch (IllegalStateException expected) { check("native switch failed".equals(expected.getMessage()), "native Throwable preserved by diagnostic wrapper"); }
            tree.footer.failSwitch=false;
            check(XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().anyMatch(s -> s.contains("kind=failedPanel") && s.contains("afterMode=-1")), "failedPanel transition distinct from absent callbacks");
            check(XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().anyMatch(s -> s.contains("kind=callback") && s.contains("hostError=IllegalStateException") && s.contains("afterMode=-1")), "callback execution and native error type recorded");
        }
        for (String line : XposedBridge.logs.subList(start,XposedBridge.logs.size())) if (line.contains("kind=snapshot")) {
            check(Integer.parseInt(line.split("nodeCount=")[1].split(" ")[0]) <= 8, "snapshot fixed-node bound");
            check(Integer.parseInt(line.split("ancestorCount=")[1].split(" ")[0]) <= 3, "snapshot ancestor bound");
            check(!line.contains("node8=") && !line.contains("secret-chat-body") && !line.contains("resourceName="), "snapshot contains no content or overflow node");
        }
        Method transition = type.getDeclaredMethod("traceTransition",String.class); transition.setAccessible(true);
        for (int i=0;i<100;i++) { transition.invoke(state,"test"); tree.footer.getViewTreeObserver().draw(); }
        long events=XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().filter(s -> s.contains("stage=transition ")).count();
        check(events == 24, "all event kinds share strict 24-event State lifetime budget");
        int end=XposedBridge.logs.size();
        enable(false);enable(true);tree.settle();
        check(XposedBridge.logs.subList(end,XposedBridge.logs.size()).stream().noneMatch(s -> s.contains("stage=transition ")), "restore/enable never renews trace budget");
        System.out.println("GREEN bounded footer diagnostics: registered descriptors, callback/native failure, 24 events, 3 frames, nodes8/ancestors3, no content");
        stop(tree.activity);
    }

    private static void constructorAndRestore() throws Throwable {
        enable(true);
        LauncherUI activity = new LauncherUI(); ViewGroup root = root(activity); root.attach();
        ChatFooter footer = new ChatFooter(activity);
        Drawable nativeBackground = new Drawable(); footer.setBackground(nativeBackground);
        ViewOutlineProvider nativeOutline = new ViewOutlineProvider() { public void getOutline(View view, Outline outline) { } };
        footer.setOutlineProvider(nativeOutline); footer.setElevation(2f);
        XposedBridge.constructed(footer);
        check(footer.attachListeners.size() == 1, "first constructor observe creates State (no recursion)");
        check(footer.getBackground() == nativeBackground, "detached constructor leaves UI intact");
        ViewGroup.MarginLayoutParams params = margins(3, 4, 5, -240);
        root.addView(footer, params);
        XposedBridge.invoke(footer, "onAttachedToWindow", new Class<?>[0]);
        check(footer.attachListeners.size() == 1, "constructor + attach observe is idempotent");
        check(footer.getViewTreeObserver().listeners.size() == 1, "one pre-draw listener");
        check(params.leftMargin == 15 && params.rightMargin == 17 && params.bottomMargin == -232 && params.topMargin == 4, "native-panel margins retained plus configured gap");
        check(footer.getClipToOutline() && footer.getElevation() == 4f, "production drawing style applied");
        GradientDrawable surface = (GradientDrawable) footer.getBackground();
        check(surface.radius == 24f && surface.color == 0xFFF7F7F7, "configured surface and radius");
        int layouts = footer.layouts, outlines = footer.outlines, changes = surface.changes;
        for (int i=0; i<12; i++) { footer.getViewTreeObserver().draw(); XposedBridge.constructed(footer); }
        check(footer.layouts == layouts, "unchanged pre-draw does not relayout");
        check(footer.outlines == outlines && surface.changes == changes, "style does not invalidate itself every frame");
        check(footer.attachListeners.size() == 1 && footer.getViewTreeObserver().listeners.size() == 1, "repeated observe never adds listeners");
        root.removeView(footer);
        check(footer.getBackground() == nativeBackground && footer.getOutlineProvider() == nativeOutline && !footer.getClipToOutline() && footer.getElevation() == 2f, "detach restores drawing ownership");
        check(params.leftMargin == 3 && params.rightMargin == 5 && params.bottomMargin == -240 && params.topMargin == 4, "detach restores exact original margins");
        check(footer.getViewTreeObserver().listeners.isEmpty(), "detach removes pre-draw");
        root.addView(footer, params); XposedBridge.invoke(footer, "onAttachedToWindow", new Class<?>[0]);
        check(footer.attachListeners.size() == 1 && footer.getViewTreeObserver().listeners.size() == 1, "reattach reuses original State/listener");
        check(params.leftMargin == 15, "reattach does not accumulate margins");
        Looper.main = false;
        try { XposedBridge.constructed(footer); } finally { Looper.main = true; }
        Handler.drain();
        check(footer.attachListeners.size() == 1, "off-main observe posts idempotently");
        Method observe = FloatingChatFooterFeature.class.getDeclaredMethod("observe", View.class); observe.setAccessible(true);
        observe.invoke(null, new Object[]{null});
        check(true, "collected weak footer observation is null safe");
        stop(activity);
    }

    /** The floating card swaps only its fill: the home-bar material, then back to a solid surface. */
    private static void glassMaterial() throws Throwable {
        ChatGlassSurface.forced = ChatGlassSurface.LIQUID;
        ChatGlassSurface.constructions = 0;
        enable(true);
        LauncherUI activity = new LauncherUI(); ViewGroup root = root(activity); root.attach();
        ChatFooter footer = new ChatFooter(activity);
        Drawable nativeBackground = new Drawable(); footer.setBackground(nativeBackground);
        XposedBridge.constructed(footer);
        root.addView(footer, margins(0, 0, 0, -300));
        XposedBridge.invoke(footer, "onAttachedToWindow", new Class<?>[0]);
        if ("pre-fix-glass".equals(variant)) {
            check(footer.getBackground() instanceof GradientDrawable && !(footer.getBackground() instanceof ChatGlassSurface),
                    "pre-fix card is a flat gradient fill");
            check(ChatGlassSurface.constructions == 0, "pre-fix card never builds the home-bar material");
            System.out.println("RED confirmed: pre-fix floating card stays an opaque gradient fill with no sampled material");
            ChatGlassSurface.forced = ChatGlassSurface.PLAIN;
            stop(activity);
            return;
        }
        check(footer.getBackground() instanceof ChatGlassSurface, "glass mode installs the card material instead of a solid fill");
        ChatGlassSurface glass = (ChatGlassSurface) footer.getBackground();
        check(glass.mode == ChatGlassSurface.LIQUID && glass.glassBlur == 8, "mode and blur come from the shared bottom-bar keys");
        check(glass.color == 0xFFF7F7F7 && glass.radius == 24f && glass.density == 1f && !glass.dark,
                "glass tint, corner and density match the solid card it replaces");
        int constructions = ChatGlassSurface.constructions;
        for (int i = 0; i < 12; i++) footer.getViewTreeObserver().draw();
        check(footer.getBackground() == glass && ChatGlassSurface.constructions == constructions,
                "repeated frames reuse one glass surface, never a per-frame rebuild");
        check(footer.getElevation() == 4f && footer.getClipToOutline(), "elevation and outline clip survive the material swap");

        int stops = ChatGlassSurface.stops;
        ChatGlassSurface.forced = ChatGlassSurface.PLAIN;
        FloatingChatFooterFeature.refresh(); Handler.drain();
        check(footer.getBackground() instanceof GradientDrawable, "switching the shared setting off returns a solid surface");
        GradientDrawable solid = (GradientDrawable) footer.getBackground();
        check(solid.radius == 24f && solid.color == 0xFFF7F7F7, "the solid card keeps its configured corner and tint");
        check(ChatGlassSurface.stops == stops + 1, "leaving glass mode releases the sampler exactly once");

        ChatGlassSurface.forced = ChatGlassSurface.LIQUID;
        FloatingChatFooterFeature.refresh(); Handler.drain();
        check(footer.getBackground() instanceof ChatGlassSurface && footer.getBackground() != glass,
                "re-entering glass mode builds one fresh surface");

        stops = ChatGlassSurface.stops;
        root.removeView(footer);
        check(footer.getBackground() == nativeBackground, "detach restores the native background, never a dead glass surface");
        check(ChatGlassSurface.stops == stops + 1, "detach stops the glass sampler");
        ChatGlassSurface.forced = ChatGlassSurface.PLAIN;
        stop(activity);
    }

    private static void delayedDiscovery() throws Throwable {
        enable(true); ChattingUI activity = new ChattingUI(); ViewGroup root = root(activity); root.attach();
        resume(activity); Handler.drain(); check(Handler.pending() == 1, "missing footer schedules one bounded retry");
        ChatFooter footer = new ChatFooter(activity); root.addView(footer, margins(0,0,0,0));
        Handler.tick(); check(footer.attachListeners.size() == 1, "later inflated footer discovered by retry");
        check(Handler.pending() == 0, "successful discovery cancels retry");
        resume(activity); FloatingChatFooterFeature.refresh(); FloatingChatFooterFeature.refresh(); Handler.drain();
        check(footer.attachListeners.size() == 1 && footer.getViewTreeObserver().listeners.size() == 1, "multiple resume/refresh remain idempotent");
        stop(activity);

        enable(true); LauncherUI detached = new LauncherUI(); ViewGroup detachedRoot = root(detached); detachedRoot.attach();
        resume(detached); Handler.drain(); check(Handler.pending() == 1, "pending discovery before root detach");
        detachedRoot.detach(); check(Handler.pending() == 0, "root detach cancels pending discovery"); stop(detached);

        enable(true); LauncherUI paused = new LauncherUI(); root(paused).attach(); resume(paused); Handler.drain();
        XposedBridge.invoke(paused,"onPause",new Class<?>[0]); check(Handler.pending() == 0, "pause cancels discovery"); stop(paused);

        enable(true); LauncherUI exhausted = new LauncherUI(); root(exhausted).attach(); resume(exhausted); Handler.finish();
        check(Handler.pending() == 0, "discovery stops after finite attempts");
        check(XposedBridge.logs.stream().anyMatch(s->s.contains("stage=discover exhausted")), "bounded exhaustion diagnosed"); stop(exhausted);

        // Notification entry: the chat tree arrives after this feature's bounded discovery gave up.
        enable(true); LauncherUI notified = new LauncherUI(); ViewGroup notifiedRoot = root(notified); notifiedRoot.attach();
        resume(notified); Handler.finish();
        ChatFooter missed = new ChatFooter(notified); notifiedRoot.addView(missed, margins(0,0,0,0)); Handler.drain();
        check(missed.attachListeners.isEmpty(), "exhausted discovery misses a chat tree delivered late");
        FloatingChatFooterFeature.adopt(missed); Handler.drain();
        check(missed.attachListeners.size() == 1 && missed.getViewTreeObserver().listeners.size() == 1,
                "header hand-off observes and watches the missed footer");
        FloatingChatFooterFeature.adopt(missed); FloatingChatFooterFeature.adopt(missed); Handler.drain();
        check(missed.attachListeners.size() == 1 && missed.getViewTreeObserver().listeners.size() == 1,
                "repeated hand-off every header frame stays idempotent");
        stop(notified);
    }

    private static void discoveryBoundaries() throws Throwable {
        enable(true); LauncherUI current = new LauncherUI(); LauncherUI other = new LauncherUI();
        ViewGroup root = root(current); root.attach();
        ChatFooter foreign = new ChatFooter(other); root.addView(foreign, margins(0,0,0,0));
        ViewGroup hidden = new ViewGroup(current); hidden.setVisibility(View.GONE); root.addView(hidden);
        ChatFooter inactive = new ChatFooter(current); hidden.addView(inactive, margins(0,0,0,0));
        ChatFooter subclass = new DerivedFooter(current); root.addView(subclass, margins(0,0,0,0));
        resume(current); Handler.finish();
        check(foreign.attachListeners.isEmpty() && inactive.attachListeners.isEmpty() && subclass.attachListeners.isEmpty(), "foreign Activity, hidden tree and subclass are not candidates");
        stop(current);
        enable(true); LauncherUI ambiguous = new LauncherUI(); ViewGroup both = root(ambiguous); both.attach();
        ChatFooter one = new ChatFooter(ambiguous), two = new ChatFooter(ambiguous);
        both.addView(one); both.addView(two); resume(ambiguous); Handler.finish();
        check(one.attachListeners.isEmpty() && two.attachListeners.isEmpty(), "ambiguous visible footers rejected"); stop(ambiguous);
        enable(true); LauncherUI huge = new LauncherUI(); ViewGroup hugeRoot = root(huge); hugeRoot.attach();
        hugeRoot.addView(new ViewGroup(huge) {
            @Override public int getChildCount() { return 10000; }
            @Override public View getChildAt(int index) { throw new AssertionError("scan exceeded admitted node budget"); }
        });
        resume(huge); Handler.finish(); check(Handler.pending() == 0, "scan budget bounds enqueuing as well as visits"); stop(huge);
    }

    private static void disabledFit() throws Throwable {
        enable(false); LauncherUI activity = new LauncherUI(); ViewGroup root = root(activity); root.attach();
        ChattingUILayout layout = new ChattingUILayout(activity); layout.setPadding(1,2,3,34); layout.paint.setAlpha(151); root.addView(layout);
        ChatFooter footer = new ChatFooter(activity); Drawable nativeBackground = new Drawable(); footer.setBackground(nativeBackground);
        ViewGroup.MarginLayoutParams params = margins(2,3,4,-100); layout.addView(footer,params); XposedBridge.constructed(footer);
        resume(activity); Handler.drain();
        if (optionalPresent) { Rect fit = new Rect();fit.bottom=34;XposedBridge.invoke(layout,"fitSystemWindows",new Class<?>[]{Rect.class},fit); }
        check(footer.getBackground() == nativeBackground && params.leftMargin == 2 && params.bottomMargin == -100, "disabled leaves original footer UI");
        check(layout.getPaddingBottom() == 34 && layout.paint.getAlpha() == 151, "disabled optional fit does not suppress native strip");
        check(footer.getViewTreeObserver().listeners.isEmpty() && Handler.pending() == 0, "disabled has no draw/retry work");
        stop(activity);
    }

    private static void delayedLayoutAndOwnership() throws Throwable {
        enable(true); LauncherUI activity = new LauncherUI(); ViewGroup root = root(activity); root.attach();
        ChattingUILayout layout = new ChattingUILayout(activity); layout.setPadding(1,2,3,25); root.addView(layout);
        ChatFooter footer = new ChatFooter(activity); Drawable original = new Drawable(); footer.setBackground(original);
        View child = new View(activity); Drawable inputBackground = new Drawable(); child.setBackground(inputBackground); footer.addView(child);
        layout.addView(footer, new ViewGroup.LayoutParams(400,60)); XposedBridge.constructed(footer);
        check(footer.getBackground() instanceof GradientDrawable, "drawing style does not require MarginLayoutParams");
        check(child.getBackground() == inputBackground, "never strips native input/control background");
        ViewGroup.MarginLayoutParams later = margins(7,8,9,-110); footer.setLayoutParams(later); footer.getViewTreeObserver().draw();
        check(later.leftMargin == 19 && later.rightMargin == 21 && later.bottomMargin == -102, "layout params arriving after style are captured correctly");
        ViewGroup.MarginLayoutParams replaced = margins(10,11,12,-120); footer.setLayoutParams(replaced); footer.getViewTreeObserver().draw();
        check(replaced.leftMargin == 22 && replaced.bottomMargin == -112, "host replacement params rebase correctly");
        replaced.bottomMargin = -230; footer.getViewTreeObserver().draw();
        check(replaced.bottomMargin == -222, "host keyboard margin changes retained plus gap");
        Drawable themeChanged = new Drawable(); footer.setBackground(themeChanged); footer.getViewTreeObserver().draw();
        check(footer.getBackground() instanceof GradientDrawable, "host surface replaced while feature enabled");
        enable(false); Handler.drain();
        check(footer.getBackground() == themeChanged, "disable restores latest host-owned background");
        check(replaced.leftMargin == 10 && replaced.rightMargin == 12 && replaced.bottomMargin == -230, "disable restores rebased host margins");
        check(layout.getPaddingBottom() == 25 && layout.paint.getAlpha() == 255, "disable restores owned bottom strip");
        check(ChatWindowInsetsCoordinator.acquired == ChatWindowInsetsCoordinator.released, "bottom inset owner released");
        stop(activity);

        enable(true); LauncherUI edge = new LauncherUI(); ViewGroup edgeRoot = root(edge); edgeRoot.attach();
        ChattingUILayout failedLayout = new ChattingUILayout(edge); edgeRoot.addView(failedLayout); ChatFooter stillStyled = new ChatFooter(edge);
        failedLayout.addView(stillStyled,margins(0,0,0,0)); ChatWindowInsetsCoordinator.fail = true;
        XposedBridge.constructed(stillStyled); ChatWindowInsetsCoordinator.fail = false;
        check(stillStyled.getBackground() instanceof GradientDrawable && ((ViewGroup.MarginLayoutParams) stillStyled.getLayoutParams()).leftMargin == 12, "optional insets failure cannot roll back basic style"); stop(edge);
    }

    private static void fallbackPanelVisibility() throws Throwable {
        WexStore.INSTANCE.flags.put(FloatingChatFooterFeature.KEY_PANEL_ABOVE,false);
        enable(true); FooterTree tree = new FooterTree();
        tree.params.topMargin=-60;
        tree.settle(); XposedBridge.constructed(tree.footer); tree.settle();
        check(tree.panel.getParent()==tree.input && tree.footer.getHeight()==360 && outlineBottom(tree.footer)==60,
                "native fallback outline uses actual screen intersection, not guessed panel subtraction");
        int staleHeight=tree.panel.getHeight();
        tree.panel.setVisibility(View.GONE);tree.params.bottomMargin=0;tree.settle();
        check(staleHeight==300 && tree.panel.getHeight()==300 && tree.panelParams.height==300,
                "GONE retains real stale bounds and positive native LayoutParams");
        check(tree.footer.getHeight()==60 && outlineBottom(tree.footer)==60,
                "native GONE with host closed margin never subtracts stale panel bounds");
        tree.panel.setVisibility(View.VISIBLE);tree.params.bottomMargin=0;tree.params.topMargin=-360;tree.settle();
        check(tree.footer.getHeight()==360 && outlineBottom(tree.footer)==360,"native visible panel inside host has full outline");
        tree.params.bottomMargin=-100;tree.params.topMargin=-260;tree.settle();
        check(outlineBottom(tree.footer)==260,"native negative margins use actual clipped pixels");
        tree.params.bottomMargin=-300;tree.params.topMargin=-60;tree.footer.setTranslationY(-300f);tree.settle();
        check(outlineBottom(tree.footer)==360,"native child keyboard translation reveals the complete card");
        tree.footer.setTranslationY(0f);tree.panel.setVisibility(View.INVISIBLE);tree.settle();
        check(outlineBottom(tree.footer)==60,"INVISIBLE panel still measured but outline follows actual footer clip");
        tree.panel.setVisibility(View.VISIBLE);tree.input.setVisibility(View.GONE);tree.settle();
        check(tree.footer.getHeight()==0 && outlineBottom(tree.footer)==0,"zero measured card has empty outline despite stale panel");
        tree.input.setVisibility(View.VISIBLE);tree.panelParams.height=0;tree.params.bottomMargin=0;tree.settle();
        check(outlineBottom(tree.footer)==60,"zero-height native panel consumes no input pixels");
        tree.panelParams.height=300;tree.footer.setLayoutParams(new ViewGroup.LayoutParams(-1,-2));tree.settle();
        check(outlineBottom(tree.footer)==0,"no-margin footer after MATCH_PARENT content is genuinely outside host; empty outline does not pretend full visibility");
        stop(tree.activity);WexStore.INSTANCE.flags.remove(FloatingChatFooterFeature.KEY_PANEL_ABOVE);
    }

    private static int outlineBottom(View footer) {
        Outline outline = new Outline();
        ViewOutlineProvider provider = footer.getOutlineProvider();
        if (provider == null) return 0;
        provider.getOutline(footer,outline);
        return outline.bottom;
    }

    private static void panelSafety() throws Throwable {
        enable(true); LauncherUI activity = new LauncherUI(); ViewGroup root = root(activity); root.attach();
        ChattingScrollLayout scroll = new ChattingScrollLayout(activity); root.addView(scroll);
        ChatFooter footer = new ChatFooter(activity); ViewGroup.MarginLayoutParams params = margins(0,0,0,-200); scroll.addView(footer,params);
        RelativeLayout card = new RelativeLayout(activity); footer.addView(card);
        LinearLayout input = new LinearLayout(activity); card.addView(input,new RelativeLayout.LayoutParams(-1,-2));
        View inputRow = new View(activity); input.addView(inputRow);
        ChatFooterBottom panel = new ChatFooterBottom(activity); ViewGroup.LayoutParams panelParams = new ViewGroup.LayoutParams(-1,200); input.addView(panel,panelParams);
        if (optionalPresent) {
            root.layout(0,0,400,1000);scroll.layout(0,0,400,1000);
            footer.layout(0,0,400,260);card.layout(0,0,400,260);input.layout(0,0,400,260);
            inputRow.layout(0,0,400,60);panel.layout(0,60,400,260);
        } else {
            footer.layout(0,0,400,260);panel.layout(0,60,400,260);
        }
        footer.setTranslationY(-25f);
        XposedBridge.constructed(footer); Handler.drain(); footer.getViewTreeObserver().draw();
        if (optionalPresent) {
            check(panel.getParent() == input && panel.getVisibility() == View.VISIBLE, "unknown native VISIBLE panel remains native until successful transition");
            XposedBridge.invoke(footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},0,true,-1);
            check(panel.getParent() == card && card.getChildCount() == 2, "resolved successful state machine permits exact supported reparent");
            check(panel.getVisibility() == View.GONE, "native VISIBLE off-screen panel starts collapsed after reparent");
            check(params.bottomMargin == 8 && params.topMargin < 0, "supported linear host gets floating margins");
            int additions = card.additions;
            for (int i=0;i<10;i++) footer.getViewTreeObserver().draw();
            check(card.additions == additions, "panel reparent occurs once");
            enable(false); Handler.drain();
            check(panel.getParent() == input && panel.getLayoutParams() == panelParams && params.bottomMargin == -200, "disable restores panel location and layout params");
        } else {
            check(panel.getParent() == input && panel.getVisibility() == View.VISIBLE, "missing optional state machine keeps native keyboard panel");
            check(params.bottomMargin == -192 && params.topMargin == 0 && footer.getTranslationY() == -25f, "missing state machine does not emulate panel movement");
            Outline outline = new Outline();
            ViewOutlineProvider provider = footer.getOutlineProvider();
            if (provider == null) throw new AssertionError("native fallback keeps an outline provider");
            provider.getOutline(footer,outline);
            Rect actual = FloatingFooterLayout.visibleFooterBounds(footer);
            check(outline.bottom == actual.bottom && outline.top == actual.top && outline.bottom < footer.getHeight(),
                    "native fallback intersects actual short ancestor and translated card; no guessed panel height");
        }
        stop(activity);

        enable(true); LauncherUI unknown = new LauncherUI(); ViewGroup unknownRoot = root(unknown); unknownRoot.attach();
        ChatFooter unsupported = new ChatFooter(unknown); unknownRoot.addView(unsupported,margins(0,1,0,-100));
        LinearLayout column = new LinearLayout(unknown); unsupported.addView(column); ChatFooterBottom nativePanel = new ChatFooterBottom(unknown); column.addView(nativePanel,new ViewGroup.LayoutParams(-1,100));
        XposedBridge.constructed(unsupported); Handler.drain();
        check(nativePanel.getParent() == column && nativePanel.getVisibility() == View.VISIBLE, "unsupported footer parent never reparents/hides panel");
        check(((ViewGroup.MarginLayoutParams)unsupported.getLayoutParams()).topMargin == 1, "unsupported parent never gets negative overlay top"); stop(unknown);
    }

    /** The gesture pill is owned by the card, so the card must clear the band on its
     *  own: the closed keyboard and the "+" panel used to land on it, while the smiley
     *  panel was carried by its native re-fit and is the accepted baseline. */
    private static void gesturePillBand() throws Throwable {
        LauncherUI activity = new LauncherUI();
        ViewGroup root = root(activity);
        root.layout(0, 0, 400, 1000);
        View host = new View(activity);
        root.addView(host, new ViewGroup.LayoutParams(-1, -1));
        root.attach();
        root.insets = band(48, 0, 0);
        host.layout(0, 940, 400, 1000);
        check(FloatingFooterLayout.pillClearance(host) == 48, "host reaching the window bottom owes the whole gesture band");
        check(FloatingFooterLayout.remainingBottom(host) == 48, "the same host already reports the band as remaining navigation");
        host.layout(0, 892, 400, 952);
        check(FloatingFooterLayout.pillClearance(host) == 0, "host that already ends above the band owes nothing");
        host.layout(0, 940, 400, 1000);
        root.insets = band(48, 300, 0);
        check(FloatingFooterLayout.pillClearance(host) == 0, "keyboard brackets suppress the gesture band");
        root.insets = band(48, 0, 48);
        check(FloatingFooterLayout.pillClearance(host) == 0, "three-button bar is laid out, never overlapped");
        root.insets = band(0, 0, 0);
        check(FloatingFooterLayout.pillClearance(host) == 0, "no gesture bar means no clearance");
        // Stripped world: the chat window's consumers read nav = 0, so the remembered
        // band is the only evidence left and the card must still clear it.
        FloatingFooterLayout.rememberBand(host, 48);
        check(FloatingFooterLayout.pillClearance(host) == 48, "remembered band survives a stripped window inset");
        check(FloatingFooterLayout.remainingBottom(host) == 0, "stripped window reports no remaining navigation");
        root.insets = band(48, 200, 0);
        FloatingFooterLayout.pillClearance(host);
        check(FloatingFooterLayout.rememberedBand(host) == 0, "band memory is dropped when the keyboard takes the bottom");
        FloatingFooterLayout.rememberBand(host, 48);
        root.insets = band(0, 0, 0);
        check(FloatingFooterLayout.pillClearance(host) == 48, "band memory is re-learned on the next gesture frame");
        host.layout(0, 1000, 400, 1000);
        check(FloatingFooterLayout.pillClearance(host) == 0, "unmeasured host defers to the host measurement");
        FloatingFooterLayout.forgetBand();
        stop(activity);
        if (!optionalPresent) return;
        enable(true);
        FooterTree tree = new FooterTree();
        XposedBridge.constructed(tree.footer); tree.settle();
        XposedBridge.invoke(tree.footer, "switchHost", new Class<?>[]{int.class, boolean.class, int.class}, 0, true, -1);
        root(tree.activity).insets = band(0, 0, 0); tree.settle();
        int plain = cardBottom(tree), plainMargin = tree.params.bottomMargin;
        root(tree.activity).insets = band(48, 0, 0); tree.settle();
        int gesture = cardBottom(tree);
        check(plain - gesture == 48, "gesture band lifts the closed card by exactly the band: plain=" + plain + " gesture=" + gesture);
        check(tree.params.bottomMargin >= 8, "closed card keeps its shadow gap over the band: " + tree.params.bottomMargin);
        // Same tree with the window's insets stripped, as every chat consumer sees them.
        root(tree.activity).insets = band(0, 0, 0);
        FloatingFooterLayout.rememberBand(tree.scroll, 48); tree.settle();
        int stripped = cardBottom(tree);
        check(plain - stripped == 48, "stripped window still lifts the closed card: plain=" + plain + " stripped=" + stripped);
        check(stripped < plain - 8, "stripped window card is off the pill, not merely gap-sized: " + stripped);
        // The smiley panel is the accepted baseline: the floor never touches it.
        XposedBridge.invoke(tree.footer, "switchHost", new Class<?>[]{int.class, boolean.class, int.class}, 2, true, -1);
        tree.settle();
        int smiley = tree.params.bottomMargin;
        check(smiley == plainMargin, "smiley panel keeps its accepted bottom: " + smiley + " vs closed " + plainMargin
                + " card=" + cardBottom(tree));
        // The "+" panel is the reported broken state and does take the floor.
        XposedBridge.invoke(tree.footer, "switchHost", new Class<?>[]{int.class, boolean.class, int.class}, 3, true, -1);
        tree.settle();
        check(tree.params.bottomMargin >= plainMargin + 48, "plus panel clears the gesture band: "
                + tree.params.bottomMargin + " vs closed " + plainMargin + " card=" + cardBottom(tree));
        FloatingFooterLayout.forgetBand();
        stop(tree.activity);
        System.out.println("GREEN gesture pill band: readable, remembered, smiley baseline untouched");
    }

    private static int cardBottom(FooterTree tree) {
        int[] location = new int[2];
        tree.footer.getLocationOnScreen(location);
        return location[1] + tree.footer.getHeight();
    }

    private static WindowInsets band(int nav, int ime, int tap) {
        WindowInsets insets = new WindowInsets();
        insets.nav = nav; insets.ime = ime; insets.tap = tap;
        return insets;
    }

    private static void navStrip() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();
        XposedBridge.constructed(tree.footer); tree.settle();
        check(ChatWindowInsetsCoordinator.owned, "footer apply acquired the chat window bottom acquired="
                + ChatWindowInsetsCoordinator.acquired + " released=" + ChatWindowInsetsCoordinator.released);
        WindowInsets given = new WindowInsets(); given.nav = 60;
        WindowInsets seen = (WindowInsets) XposedBridge.invoke(tree.scroll, "onApplyWindowInsets", new Class<?>[]{WindowInsets.class}, given);
        check(seen.nav == 0, "owned gesture bottom strips navigation inset for host consumers");
        check(seen != given && given.nav == 60, "strip builds a separate insets object instead of mutating caller state");
        seen = (WindowInsets) XposedBridge.invoke(tree.scroll, "onApplyWindowInsets", new Class<?>[]{WindowInsets.class}, given);
        check(seen.nav == 0, "repeated dispatches strip idempotently");
        given.ime = 300;
        seen = (WindowInsets) XposedBridge.invoke(tree.scroll, "onApplyWindowInsets", new Class<?>[]{WindowInsets.class}, given);
        check(seen.nav == 60 && seen.ime == 300, "IME open keeps the native keyboard inset path");
        given.ime = 0; given.tap = 24;
        seen = (WindowInsets) XposedBridge.invoke(tree.scroll, "onApplyWindowInsets", new Class<?>[]{WindowInsets.class}, given);
        check(seen.nav == 60, "three-button navigation keeps its solid bar");
        given.tap = 0;
        enable(false); tree.settle();
        seen = (WindowInsets) XposedBridge.invoke(tree.scroll, "onApplyWindowInsets", new Class<?>[]{WindowInsets.class}, given);
        check(seen.nav == 60 && !ChatWindowInsetsCoordinator.owned, "release restores the native inset path");
        System.out.println("GREEN nav strip: owned gesture bottom strips nav for consumers, idempotent, IME/three-button/release preserved");
    }

    private static void naturalPanelStates() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();
        tree.settle();
        check(tree.panel.getVisibility() == View.VISIBLE && tree.footer.getHeight() == 60 + 300,
                "native collapsed panel is VISIBLE and naturally measures behind negative margin");
        int heightParam = tree.params.height;
        XposedBridge.constructed(tree.footer); tree.settle();
        if (!optionalPresent) {
            check(tree.panel.getParent() == tree.input && tree.panel.getVisibility() == View.VISIBLE,
                    "natural measurement with missing hooks keeps native off-screen panel");
            check(tree.footer.getHeight() == 360 && tree.params.bottomMargin == -292,
                    "missing hooks never flatten native composer/panel measurement");
            stop(tree.activity); return;
        }
        check(tree.panel.getParent() == tree.input && tree.panel.getVisibility() == View.VISIBLE && tree.footer.getHeight() == 360,
                "unknown mode preserves native collapsed panel and first-open measurement");
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},0,true,-1); tree.settle();
        check(tree.panel.getVisibility() == View.GONE && tree.footer.getHeight() == 60,
                "successful collapsed transition migrates panel without fixed composer height");
        check(tree.params.height == heightParam && tree.params.height == ViewGroup.LayoutParams.WRAP_CONTENT,
                "composer height stays WRAP_CONTENT");
        for (int mode : new int[]{2, 3, 0, 1, 4, 2, 4}) {
            XposedBridge.invoke(tree.footer, "switchHost", new Class<?>[]{int.class,boolean.class,int.class}, mode,true,-1);
            boolean opened = mode == 2 || mode == 3;
            check(tree.panel.getVisibility() == (opened ? View.VISIBLE : View.GONE), "mode " + mode + " has exact panel visibility");
            if (!opened) check(tree.footer.beforeSwitchVisibility == View.GONE, "close " + mode + " is hidden before native scroll");
            tree.settle();
            check(tree.footer.getHeight() == 60 + (opened ? tree.panel.getLayoutParams().height : 0),
                    "mode " + mode + " naturally measures input plus only a visible panel");
            check(tree.params.bottomMargin == 8 && tree.scroll.lastY == 0,
                    "mode " + mode + " removes native negative margin and panel-only scroll");
        }
        tree.inputRow.natural = 144; tree.settle();
        check(tree.footer.getHeight() == 144 && tree.params.topMargin == -152, "multiline composer grows naturally and overlay follows it");
        tree.inputRow.natural = 48; tree.voice.natural = 72; tree.voice.setVisibility(View.VISIBLE); tree.settle();
        check(tree.footer.getHeight() == 120 && tree.params.height == heightParam, "voice controls/tool row contribute natural height");
        enable(false); tree.settle();
        check(tree.panel.getParent() == tree.input && tree.input.indexOfChild(tree.panel) == 1
                        && tree.panel.getLayoutParams() == tree.panelParams && tree.panel.getVisibility() == View.VISIBLE,
                "disable restores exact native panel parent/index/params/visibility");
        check(((RelativeLayout.LayoutParams)tree.input.getLayoutParams()).getRule(RelativeLayout.BELOW) == 0,
                "disable removes only our BELOW rule");
        check(tree.params.bottomMargin == -300 && tree.params.topMargin == 0, "disable restores native footer margins");
        // Record a known open state while disabled, then re-enable: VISIBLE by
        // itself is never evidence, but a successful 2/3 transition is.
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,true,-1);
        enable(true); tree.settle();
        check(tree.panel.getVisibility() == View.VISIBLE && tree.footer.getHeight() == 420, "known opened 3 survives late-enable migration");
        for (int i=0;i<5;i++) {
            enable(false); enable(true); tree.settle();
            check(tree.params.leftMargin == 12 && tree.params.bottomMargin == 8 && tree.panel.getLayoutParams().height == 300,
                    "repeated restore/enable does not accumulate margins or panel compression");
        }
        stop(tree.activity);

        enable(true); FooterTree first = new FooterTree(); first.settle();
        first.footer.switchProbe = () -> check(first.panel.getParent() == first.input && first.panel.getVisibility() == View.VISIBLE,
                "first-ever native open retains panel subtree/visibility until native opening succeeds");
        XposedBridge.invoke(first.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1);
        first.settle(); check(first.panel.getVisibility() == View.VISIBLE && first.footer.getHeight() == 360,
                "first-ever switch opens only after completed mode 2");
        first.footer.switchProbe = null; first.footer.failSwitch = true;
        try { XposedBridge.invoke(first.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,true,-1);throw new AssertionError("host switch should fail"); }
        catch (IllegalStateException expected) { check(first.panel.getVisibility() == View.GONE, "failed native switch never commits a phantom open mode"); }
        first.footer.failSwitch = false; stop(first.activity);
    }

    private static void nativeImeRequests() throws Throwable {
        for (boolean above : new boolean[]{true, false}) {
            enable(true);
            WexStore.INSTANCE.flags.put("floating_chat_footer_panel_above", above);
            FloatingChatFooterFeature.refresh(); Handler.drain();
            FooterTree tree = new FooterTree(); tree.settle();
            android.view.WindowInsets insets = new android.view.WindowInsets();
            root(tree.activity).insets = insets;
            XposedBridge.constructed(tree.footer);
            for (int mode : new int[]{2, 3}) {
                XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},mode,true,-1);
                tree.settle();
                int calls = tree.footer.keyboardCalls, requests = tree.footer.imeRequests;
                XposedBridge.invoke(tree.footer,"keyboardHost",new Class<?>[0]);
                tree.settle();
                check(tree.footer.keyboardCalls == calls + 1 && tree.footer.requestedMode == 1,
                        "native keyboard-state request reaches mode1, above=" + above + " panel=" + mode);
                check(tree.footer.imeRequests == requests + 1,
                        "panel-to-input does not swallow the native IME request, above=" + above);
                if (above) check(tree.panel.getVisibility() == View.GONE, "native mode1 still collapses the owned upper panel");
            }
            insets.ime = 300;
            int requests = tree.footer.imeRequests;
            XposedBridge.invoke(tree.footer,"keyboardHost",new Class<?>[0]);
            check(tree.footer.imeRequests == requests, "already visible IME does not gain an extra native request");
            tree.footer.failConfig = true;
            try {
                XposedBridge.invoke(tree.footer,"keyboardHost",new Class<?>[0]);
                throw new AssertionError("native keyboard failure swallowed");
            } catch (IllegalStateException expected) {
                check("native config failed".equals(expected.getMessage()), "native keyboard failure is preserved");
            }
            tree.footer.failConfig = false; stop(tree.activity);
        }
        WexStore.INSTANCE.flags.put("floating_chat_footer_panel_above", true);
        FloatingChatFooterFeature.refresh(); Handler.drain();
        System.out.println("GREEN native IME requests: both panel directions, mode1, no duplicate IME and native errors");
    }

    private static void maskOscillation() throws Throwable {
        enable(true); FooterTree tree = new FooterTree(); tree.settle();
        XposedBridge.constructed(tree.footer);
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},0,true,-1);
        android.view.WindowInsets insets = new android.view.WindowInsets(); insets.nav = 80;
        root(tree.activity).insets = insets;
        tree.settle();
        check(Boolean.TRUE.equals(stateField(tree.footer.attachListeners.get(0),"panelReady")),
                "oscillation case runs with owned panel-above margins");
        check(tree.params.bottomMargin == 88, "owned gesture margin holds the measured nav remainder");
        int layouts = tree.footer.layouts;
        int margin = tree.params.bottomMargin;
        // A restored native bottom mask refits the host: the scroll host's bottom
        // alternates between the masked reserve and the owned edge-to-edge layout,
        // the restored bottom=168 -> reset -> restore round trip from the field.
        for (int i = 0; i < 12; i++) {
            tree.hostHeight = (i % 2 == 0) ? 920 : 1000;
            tree.frame();
        }
        check(tree.params.bottomMargin == margin, "alternating mask refits never flip the applied margin");
        check(tree.footer.layouts == layouts, "oscillating mask geometry causes no per-frame footer relayout");
        tree.hostHeight = 920; tree.settle();
        check(tree.params.bottomMargin == 8, "a settled masked layout still adopts its zero remainder");
        stop(tree.activity);
        System.out.println("GREEN mask oscillation: alternating host refits hold applied margins, settled refit adopted");
    }

    private static void directionalInsets() throws Throwable {
        check(FloatingFooterLayout.remainingNavigationBottom(1000,1000,20,80) == 60, "navigation remainder deducts parent padding");
        check(FloatingFooterLayout.remainingNavigationBottom(1000,920,0,80) == 0, "host navigation avoidance is not repeated");
        check(FloatingFooterLayout.remainingImeBottom(1000,1000,20,80,300) == 220, "IME scrolling excludes navigation already reserved by margin");
        check(FloatingFooterLayout.remainingImeBottom(1000,700,0,80,300) == 0, "adjustResize already consumes the IME");
        if (!optionalPresent) return;
        clippedImeGeometry();
        enable(true); FooterTree tree = new FooterTree();
        android.view.WindowInsets insets = new android.view.WindowInsets(); insets.nav=80; insets.ime=300; root(tree.activity).insets=insets;
        tree.scroll.setPadding(0,0,0,20); XposedBridge.constructed(tree.footer);
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},0,true,-1); tree.settle();
        check(tree.params.bottomMargin == 68, "margin owns only nav80 minus parent padding20 plus gap8, not IME300");
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},300,true,0,0);
        check(tree.scroll.lastY == 220, "collapsed keyboard scroll avoids remaining IME once");
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},300,true,0,0);
        check(tree.scroll.lastY == 220, "current scroll translation cannot masquerade as resize");
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},100,true,17,29);
        check(tree.scroll.lastY == 100, "small native keyboard lift remains native instead of forcing the theoretical inset");
        XposedBridge.invoke(tree.footer,"configHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1); tree.settle();
        check(tree.panel.getVisibility() == View.VISIBLE && tree.footer.configCalls == 0 && tree.scroll.lastY == 100,
                "opening panel with IME preserves keyboard avoidance without extra shift");
        insets.ime=400;
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},400,true,0,0);
        check(tree.scroll.lastY == 320, "visible panel follows increased actual IME, not old220 keyboardScroll");
        insets.ime=300;
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY==220,"IME returning to an old height does not revive stale native100 lift");
        insets.ime=400;
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY == 320, "panel-only request cannot replace current IME avoidance");
        insets.ime=250;
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},250,true,0,0);
        check(tree.scroll.lastY == 170, "visible panel follows decreased actual IME");
        tree.scroll.setPadding(0,0,0,200);
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY == 50, "parent padding consumes200 of IME250 before scrolling");
        tree.scroll.setPadding(0,0,0,20);
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY == 170, "less host padding restores remaining IME with unchanged raw keyboard height");
        tree.hostHeight=900; tree.settle();
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY == 130, "host resize100 plus padding20 consumes120, not another full IME");
        tree.hostHeight=1000; tree.settle();
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY == 170, "host growth restores the missing lift, rather than reusing old130");
        insets.ime=300;
        tree.hostHeight=700; tree.settle();
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},360,true,0,0);
        check(tree.scroll.lastY == 0 && tree.params.bottomMargin == 8, "resized host adds neither keyboard scroll nor navigation twice");
        check(tree.panel.getLayoutParams().height == 300, "resize does not double-subtract IME and pin natural panel at minimum");
        tree.inputRow.natural=144; tree.settle();
        check(tree.panel.getLayoutParams().height == 300 && tree.footer.getHeight() == 444,
                "multiline height remains natural while resized host has sufficient room");
        tree.hostHeight=500; tree.settle();
        check(tree.panel.getLayoutParams().height == 208, "small resized host limits panel against actual input reserve and gap");
        tree.hostHeight=1000; insets.ime=0; insets.nav=0; tree.settle();
        check(tree.panel.getLayoutParams().height == 300, "host growth restores panel natural height instead of accumulating compression");
        XposedBridge.invoke(tree.footer,"keyboardHost",new Class<?>[0]); tree.settle();
        check(tree.panel.getVisibility() == View.GONE && tree.footer.keyboardCalls == 1 && tree.footer.requestedMode == 1,
                "return to input retains the native keyboard-state request");
        stop(tree.activity);
    }

    private static void clippedImeGeometry() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();
        XposedBridge.constructed(tree.footer);
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1);
        android.view.WindowInsets insets = new android.view.WindowInsets();
        insets.nav=80; insets.ime=300; root(tree.activity).insets=insets;
        tree.scroll.setPadding(0,0,0,20); tree.layoutHeight=700;
        tree.settle();
        check(FloatingFooterLayout.remainingBottom(tree.scroll)==0,
                "clipped ancestor already owns navigation avoidance");
        check(FloatingFooterLayout.remainingImeHeight(tree.scroll,300)==0,
                "clipped ancestor already owns IME avoidance");
        check(tree.panel.getLayoutParams().height==300,"ancestor IME avoidance does not double-compress native panel");
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},300,true,17,29);
        tree.settle(); checkCardBounds(tree,692,"clipped ancestor plus IME");
        int layouts=tree.footer.layouts;
        for(int i=0;i<20;i++) { tree.frame();checkCardBounds(tree,692,"stable clipped IME frame "+i); }
        check(tree.footer.layouts==layouts,"stable clipped IME produces no margin feedback writes");
        stop(tree.activity);
    }

    private static void stripPaddingOwnership() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();
        android.view.WindowInsets insets = new android.view.WindowInsets(); insets.nav=80; root(tree.activity).insets=insets;
        tree.layout.setPadding(1,2,3,17); tree.layout.paint.setAlpha(151); XposedBridge.constructed(tree.footer);
        check(tree.layout.getPaddingBottom() == 17, "unknown bottom padding is never blindly zeroed");
        if (optionalPresent) {
            tree.layout.extraPadding=17; Rect nav = new Rect(); nav.bottom=80;
            for (int i=0;i<8;i++) {
                XposedBridge.invoke(tree.layout,"fitSystemWindows",new Class<?>[]{Rect.class},nav);
                tree.footer.getViewTreeObserver().draw();
                check(tree.layout.getPaddingBottom() == 17, "fit removes known navigation only, retaining content padding");
            }
            enable(false); check(tree.layout.getPaddingBottom() == 97 && tree.layout.paint.getAlpha() == 151,
                    "disable restores exact native fit padding and paint alpha");
            enable(true); tree.layout.setPadding(1,2,3,117); tree.footer.getViewTreeObserver().draw();
            check(tree.layout.getPaddingBottom() == 117, "unknown host rewrite with nav visible remains owned by host");
            tree.layout.noOpFit=true; XposedBridge.invoke(tree.layout,"fitSystemWindows",new Class<?>[]{Rect.class},nav);
            check(tree.layout.getPaddingBottom() == 37, "confirmed navigation fit can remove only its80 contribution");
            for (int i=0;i<8;i++) XposedBridge.invoke(tree.layout,"fitSystemWindows",new Class<?>[]{Rect.class},nav);
            check(tree.layout.getPaddingBottom() == 37, "no-op fit cannot cumulatively consume unknown remainder");
            tree.layout.failFit=true;
            try { XposedBridge.invoke(tree.layout,"fitSystemWindows",new Class<?>[]{Rect.class},nav);throw new AssertionError("host fit should fail"); }
            catch (IllegalStateException expected) { check(tree.layout.getPaddingBottom() == 117, "failed fit returns native padding rather than applying guessed inset"); }
            tree.layout.failFit=false; tree.layout.noOpFit=false; tree.layout.extraPadding=17; insets.ime=300; Rect ime=new Rect();ime.bottom=300;
            XposedBridge.invoke(tree.layout,"fitSystemWindows",new Class<?>[]{Rect.class},ime);
            check(tree.layout.getPaddingBottom() == 317, "IME plus content padding is not stripped as navigation");
        }
        stop(tree.activity);
    }

    private static void actualMarginGeometry() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();
        tree.settle(); XposedBridge.constructed(tree.footer); tree.settle();
        Object beforeState=tree.footer.attachListeners.get(0);
        System.out.println("before switch debug completed="+stateField(beforeState,"panelTransitionCompleted")
                +" depth="+stateField(beforeState,"panelSwitchDepth")+" card="+tree.card.additions
                +" panelCard="+(tree.panel.getParent()==tree.card));
        System.out.println("registered hooks " + XposedBridge.hooks.keySet());
        System.out.println("initial logs " + XposedBridge.logs);
        int debugLogs = XposedBridge.logs.size();
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1); tree.settle();
        Object state=tree.footer.attachListeners.get(0);
        System.out.println("after switch debug completed="+stateField(state,"panelTransitionCompleted")
                +" depth="+stateField(state,"panelSwitchDepth")+" card="+tree.card.additions
                +" panelCard="+(tree.panel.getParent()==tree.card));
        System.out.println("switch logs " + XposedBridge.logs.subList(debugLogs, XposedBridge.logs.size()));
        System.out.println("actual debug panelCard="+(tree.panel.getParent()==tree.card)
                +" panelReady="+stateField(state,"panelReady")+" panelState="+(stateField(state,"panelState")!=null)
                +" depth="+stateField(state,"panelSwitchDepth")+" completed="+stateField(state,"panelTransitionCompleted")
                +" failed="+stateField(state,"panelFailed")+" host="+tree.scroll.getHeight()
                +" footer="+tree.footer.getHeight()+" top="+tree.params.topMargin+" bottom="+tree.params.bottomMargin);
        int[] footer = new int[2], host = new int[2];
        tree.footer.getLocationOnScreen(footer); tree.scroll.getLocationOnScreen(host);
        System.out.println("margin geometry: footerBottom=" + (footer[1]+tree.footer.getHeight())
                + " hostBottom=" + (host[1]+tree.scroll.getHeight()) + " topMargin=" + tree.params.topMargin);
        check(footer[1]+tree.footer.getHeight() == host[1]+tree.scroll.getHeight()-8,
                "real LinearLayout margin placement keeps full opened card inside host, not outline-only");
        check(FloatingFooterGeometry.panelHeightLimit(240,0,144,120,160) == 0,
                "recommended minimum never overrides hard available height");
        check(FloatingFooterGeometry.panelHeightLimit(0,0,144,120,160) == -1,
                "unmeasured host defers native height instead of infinite room");
        check(tree.content.getHeight()==1000 && tree.recycler.getHeight()==1000,
                "preceding content/recycler actual viewport still extends to host bottom");
        for(int mode:new int[]{3,2,0,3,1,4}) {
            XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},mode,true,-1);tree.settle();
            checkCardBounds(tree,992,"margin transition "+mode);
            check(tree.content.getHeight()==1000,"panel modes do not shrink wallpaper/list carrier candidate");
        }
        tree.inputRow.natural=144;tree.settle();checkCardBounds(tree,992,"multiline collapsed");
        check(tree.footer.getHeight()==144 && tree.params.height==-2,"multiline input stays natural WRAP_CONTENT");
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,true,-1);tree.settle();
        check(tree.footer.getHeight()==444,"multiline plus panel natural height");checkCardBounds(tree,992,"multiline open");
        check(tree.recycler.getPaddingBottom()==0,"message surface keeps full viewport so content passes under the floating card");
        tree.layoutHeight=700;tree.settle();checkCardBounds(tree,692,"short clipping ancestor");
        check(tree.content.getHeight()==1000 && tree.layout.getClipChildren() && tree.scroll.getClipChildren(),
                "clip intersection moves actual card but never changes ancestor clip or shrinks content");
        tree.layoutHeight=1000;tree.hostHeight=300;tree.settle();
        check(tree.panel.getLayoutParams().height==28,"small-window cap includes actual input reserve and configured gap below minimum");
        checkCardBounds(tree,292,"small-window cap");
        tree.hostHeight=240;tree.settle();check(tree.panel.getLayoutParams().height==0,"real zero available cap never forces recommended minimum");
        checkCardBounds(tree,232,"zero available cap");
        tree.hostHeight=1000;tree.settle();check(tree.panel.getLayoutParams().height==300,"natural panel returns after window grows");
        android.view.WindowInsets insets=new android.view.WindowInsets();insets.nav=80;insets.ime=300;root(tree.activity).insets=insets;
        tree.scroll.setPadding(0,0,0,20);tree.settle();
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},300,true,0,0);tree.settle();
        check(tree.scroll.getTranslationY()==0 && tree.footer.getTranslationY()==-220,"host IME path translates child not host, remaining avoidance once");
        checkCardBounds(tree,692,"IME plus navigation margin");
        tree.hostHeight=700;tree.settle();
        XposedBridge.invoke(tree.scroll,"scrollHost",new Class<?>[]{int.class,boolean.class,int.class,int.class},300,true,0,0);tree.settle();
        check(tree.scroll.lastY==0 && tree.params.bottomMargin==8,"resize consumes IME/nav before scrolling and margins");
        checkCardBounds(tree,672,"resized IME");
        insets.ime=0;insets.nav=0;tree.hostHeight=1000;tree.scroll.setPadding(0,0,0,0);
        XposedBridge.invoke(tree.footer,"keyboardHost",new Class<?>[0]);tree.settle();
        check(tree.panel.getVisibility()==View.GONE && tree.panel.getHeight()>0 && tree.footer.getHeight()==144,
                "GONE stale panel height never contributes actual card bounds");checkCardBounds(tree,992,"IME closed");
        System.out.println("GREEN actual-margin geometry"); stop(tree.activity);
        appPanelAndRestoreGeometry();
    }

    /** getGlobalVisibleRect is root-relative. A freeform window at (100,200)
     *  must produce the same viewport as the same view at (0,0). */
    private static void freeformWindowGeometry() throws Throwable {
        LauncherUI activity = new LauncherUI();
        ViewGroup root = root(activity);
        root.layout(100,200,280,300);
        activity.getWindowManager().bounds.left = 100;
        activity.getWindowManager().bounds.top = 200;
        activity.getWindowManager().bounds.right = 280;
        activity.getWindowManager().bounds.bottom = 300;
        ViewGroup host = new ViewGroup(activity);
        root.addView(host,new ViewGroup.LayoutParams(-1,-1));
        root.attach();
        host.layout(0,0,180,100);
        View card = new View(activity);
        host.addView(card,new ViewGroup.LayoutParams(-1,-1));
        card.layout(12,40,168,92);

        Rect bounds = new Rect();
        check(FloatingFooterLayout.contentBounds(host,bounds),
                "freeform host remains visible away from the screen origin");
        check(bounds.left==100 && bounds.top==200 && bounds.right==280 && bounds.bottom==300,
                "content bounds convert root coordinates to screen coordinates");
        Rect visible = FloatingFooterLayout.visibleFooterBounds(card);
        check(visible.left==0 && visible.top==0 && visible.right==156 && visible.bottom==52,
                "footer-local outline survives a nonzero freeform window origin");
        check(FloatingFooterLayout.bottomCorrection(host)==0,
                "fully visible freeform host has no phantom bottom correction");
        stop(activity);

        enable(true);
        FooterTree tree = new FooterTree();
        tree.rootX = 100; tree.rootY = 200; tree.width = 180;
        tree.layoutHeight = 100; tree.windowHeight = 100;
        tree.settle();
        int additions = tree.card.additions;
        XposedBridge.constructed(tree.footer);
        tree.settle();
        check(tree.panel.getParent()==tree.input && tree.card.additions==additions,
                "narrow freeform window keeps the native panel tree");
        check(tree.params.leftMargin==0 && tree.params.rightMargin==0
                        && tree.params.topMargin==0 && tree.params.bottomMargin==-300,
                "narrow freeform window never writes floating margins");
        ViewOutlineProvider freeformProvider = tree.footer.getOutlineProvider();
        // A null provider is the platform default (full bounds), which never clips.
        // Only an installed empty outline combined with clipToOutline erases content.
        if (freeformProvider == null) {
            check(!tree.footer.getClipToOutline(), "freeform fallback restores native clipping");
        } else {
            Outline outline = new Outline();
            freeformProvider.getOutline(tree.footer,outline);
            check(outline.bottom>outline.top && outline.right>outline.left,
                    "valid freeform viewport never produces an empty clip outline");
        }
        stop(tree.activity);
        System.out.println("GREEN freeform coordinates: nonzero window origin, narrow native fallback, non-empty outline");
    }

    private static void checkCardBounds(FooterTree tree,int bottom,String label) {
        int[] location=new int[2];tree.footer.getLocationOnScreen(location);
        Rect visible=new Rect();check(tree.footer.getGlobalVisibleRect(visible),label+" full visible rect");
        int actualBottom=location[1]+tree.footer.getHeight();
        check(actualBottom==bottom,label+" actual bottom="+actualBottom+" expected="+bottom
                +" top="+location[1]+" height="+tree.footer.getHeight()
                +" marginTop="+tree.params.topMargin+" marginBottom="+tree.params.bottomMargin);
        check(visible.top==location[1] && visible.bottom==bottom,label+" full card in ancestor clip, not outline-only");
        check(outlineBottom(tree.footer)==tree.footer.getHeight(),label+" outline agrees with actual whole card");
        check(tree.params.bottomMargin>=8 && tree.footer.getElevation()==4f,label+" shadow gap and elevation remain");
    }

    private static void appPanelAndRestoreGeometry() throws Throwable {
        enable(true);FooterTree tree=new FooterTree();
        com.tencent.mm.pluginsdk.ui.chat.AppPanel app=new com.tencent.mm.pluginsdk.ui.chat.AppPanel(tree.activity);
        View equalHeight=new View(tree.activity);ViewGroup.LayoutParams unrelated=new ViewGroup.LayoutParams(-1,300);
        app.addView(equalHeight,unrelated);tree.panel.addView(app,new ViewGroup.LayoutParams(-1,-1));
        tree.settle();XposedBridge.constructed(tree.footer);
        XposedBridge.invoke(app,"setPortHeighPx",new Class<?>[]{int.class},300);
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,true,-1);
        tree.hostHeight=280;tree.settle();
        check(app.lastHeight==92 && tree.panel.getLayoutParams().height==92,"confirmed AppPanel setter synchronizes hard cap");
        check(unrelated.height==300,"equal-height descendant is not an owned panel body");
        tree.hostHeight=1000;tree.settle();check(app.lastHeight==300,"confirmed natural setter value survives compression");
        tree.hostHeight=0;tree.settle();
        check(tree.panel.getLayoutParams().height==300 && app.lastHeight==300,"unmeasured real host defers production resize");
        tree.hostHeight=1000;tree.settle();
        ViewGroup.MarginLayoutParams replacement=margins(7,9,11,-180);tree.footer.setLayoutParams(replacement);tree.settle();
        enable(false);tree.settle();
        check(tree.footer.getLayoutParams()==replacement && replacement.leftMargin==7 && replacement.rightMargin==11
                && replacement.topMargin==9 && replacement.bottomMargin==-180,"new footer LayoutParams restore exact host snapshot");
        check(tree.panel.getParent()==tree.input && tree.panel.getLayoutParams()==tree.panelParams,
                "reparent restores original parent and exact native LayoutParams");
        check(tree.content.getHeight()==1000 && tree.recycler.getPaddingBottom()==0 && tree.recycler.getClipToPadding(),
                "list padding/clip restore and viewport preserved");
        stop(tree.activity);
        enable(true); FooterTree direct=new FooterTree();direct.card.removeView(direct.input);direct.footer.addView(direct.input,new ViewGroup.LayoutParams(-1,-2));
        // Already-direct native structures put the panel first; the helper must
        // preserve that supported ordering rather than emulate a new container.
        direct.input.removeView(direct.panel);direct.input.addView(direct.panel,0,direct.panelParams);
        direct.settle();XposedBridge.constructed(direct.footer);
        XposedBridge.invoke(direct.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1);
        direct.hostHeight=280;direct.settle();check(direct.panelParams.height==92,"already-direct panel is capped without reparent");
        enable(false);direct.settle();check(direct.panel.getParent()==direct.input && direct.panelParams.height==300,
                "already-direct panel restores native height snapshot without swapping parent");stop(direct.activity);
    }

    private static void lazyFirstNativeOpen() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();
        com.tencent.mm.pluginsdk.ui.chat.AppPanel app = new com.tencent.mm.pluginsdk.ui.chat.AppPanel(tree.activity);
        app.lazy=true; app.setId(700);
        app.layout(0,0,0,0); // Real first-open diagnostic saw app0x0; no cached height claim.
        tree.panel.addView(app,new ViewGroup.LayoutParams(-1,-1));
        XposedBridge.constructed(tree.footer);
        check(tree.panel.getParent() == tree.input && tree.panel.getVisibility() == View.VISIBLE,
                "unknown mode preserves native panel tree/visibility for lazy first initialization");
        check(!Boolean.TRUE.equals(stateField(tree.footer.attachListeners.get(0),"panelReady")),
                "unknown mode keeps native margins, not unconfirmed floating ownership");
        tree.footer.switchProbe = () -> {
            check(tree.panel.getParent() == tree.input && tree.footer.findViewById(700) == app,
                    "first native switch still discovers AppPanel in real footer subtree");
            check(app.attachedInitialized && app.getWidth() == 0 && app.getHeight() == 0,
                    "first lazy initialization happens after attach but before any manual settle");
            try { XposedBridge.invoke(app,"setPortHeighPx",new Class<?>[]{int.class},300); }
            catch (Throwable error) { throw new AssertionError(error); }
        };
        int start = XposedBridge.logs.size();
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,true,-1);
        Object lazyState = tree.footer.attachListeners.get(0);
        check(tree.panel.getParent() == tree.card && tree.panel.getVisibility() == View.VISIBLE,
                "successful native after callback alone commits first migration/open"
                        + " fit=" + stateField(lazyState,"fitState") + " panelReady=" + stateField(lazyState,"panelReady")
                        + " depth=" + stateField(lazyState,"panelSwitchDepth") + " completed=" + stateField(lazyState,"panelTransitionCompleted")
                        + " failed=" + stateField(lazyState,"panelFailed") + " state=" + (stateField(lazyState,"panelState") != null)
                        + " footerParent=" + tree.footer.getParent().getClass().getSimpleName()
                        + " hostHeight=" + tree.scroll.getHeight() + " hostMeasured=" + tree.scroll.getMeasuredHeight()
                        + " panelParent=" + tree.panel.getParent().getClass().getSimpleName());
        int calls=app.calls;
        tree.footer.getViewTreeObserver().draw();tree.footer.getViewTreeObserver().draw();
        check(app.calls == calls,"unchanged two preDraw passes do not invoke native AppPanel setter again");
        check(XposedBridge.logs.subList(start,XposedBridge.logs.size()).stream().anyMatch(s -> s.contains("kind=callback")
                && s.contains("id=floating_footer_switch") && s.contains("phase=after") && s.contains("afterMode=3")),
                "actual first native switch after callback logged, not simulated completed state");
        tree.footer.switchProbe=null;stop(tree.activity);
        System.out.println("GREEN lazy first native open: native subtree, attach-before-init, app0x0, successful after, setter deduplicated");
        enable(true); FooterTree cramped = new FooterTree();
        cramped.hostHeight = 100; cramped.inputRow.natural = 144; cramped.settle();
        int crampedAdditions = cramped.card.additions;
        XposedBridge.constructed(cramped.footer);
        XposedBridge.invoke(cramped.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1);
        Object crampedState = cramped.footer.attachListeners.get(0);
        check(cramped.panel.getParent() == cramped.input && cramped.card.additions == crampedAdditions
                        && cramped.params.topMargin == 0,
                "input card larger than the window stays native without negative overlay"
                        + " fit=" + stateField(crampedState,"fitState") + " parent=" + cramped.panel.getParent().getClass().getSimpleName()
                        + " additions=" + cramped.card.additions + "/" + crampedAdditions + " top=" + cramped.params.topMargin
                        + " inputRow=" + cramped.inputRow.getHeight() + "/" + cramped.inputRow.getMeasuredHeight()
                        + " host=" + cramped.scroll.getHeight() + "/" + cramped.scroll.getMeasuredHeight());
        cramped.frame(); cramped.frame();
        check(cramped.panel.getParent() == cramped.input && cramped.card.additions == crampedAdditions
                        && cramped.params.topMargin == 0,
                "persistent undersized window never adopts floating margins");
        stop(cramped.activity);
    }

    private static void keyboardCloseSequence() throws Throwable {
        enable(true); FooterTree tree = new FooterTree();tree.frame();
        XposedBridge.constructed(tree.footer);
        tree.footer.scrollFlagA=17;tree.footer.scrollFlagB=29;
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},3,false,-1);
        check(tree.scroll.scrollCalls==2 && tree.scroll.lastY==0 && !tree.scroll.lastAnimate
                && tree.scroll.lastFlagA==17 && tree.scroll.lastFlagB==29,
                "first migration replays only last witnessed native scroll, preserving all native flags");
        int scrollCalls=tree.scroll.scrollCalls;
        // Exactly two real passes, separately asserted: first does not fake a settled draw.
        for(int i=0;i<2;i++) { tree.frame();check(tree.panel.getParent()==tree.card && tree.panel.getVisibility()==View.VISIBLE,"open preDraw "+i+" owns same visible panel"); }
        check(tree.scroll.scrollCalls==scrollCalls,"preDraw does not replay native scroll again");
        checkCardBounds(tree,992,"first completed native open");
        android.view.WindowInsets insets=new android.view.WindowInsets();insets.nav=80;insets.ime=300;root(tree.activity).insets=insets;
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},1,true,-1);
        tree.frame();tree.frame();checkCardBounds(tree,692,"keyboard visible");
        insets.nav=0;insets.ime=0;
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},0,true,-1);
        tree.frame();tree.frame();checkCardBounds(tree,992,"keyboard closed");
        Rect visible=new Rect();int[] loc=new int[2];tree.footer.getLocationOnScreen(loc);tree.footer.getGlobalVisibleRect(visible);
        check(visible.left==loc[0] && visible.right==loc[0]+tree.footer.getWidth(),"keyboard close both horizontal card corners inside clip");
        check(tree.panel.getVisibility()==View.GONE && tree.footer.getHeight()==60 && tree.params.bottomMargin==8,
                "successful keyboard close restores natural input, GONE and shadow gap");
        stop(tree.activity);System.out.println("GREEN keyboard close: two real preDraw passes, all card edges/corners, natural input, margins/IME/shadow");
    }

    private static void ownedPanelSequence() throws Throwable {
        if (!optionalPresent) return;
        enable(true); FooterTree tree = new FooterTree(); tree.settle();
        int cardAddsBefore = tree.card.additions;
        XposedBridge.constructed(tree.footer);
        XposedBridge.invoke(tree.footer,"switchHost",new Class<?>[]{int.class,boolean.class,int.class},2,true,-1);
        Object state = tree.footer.attachListeners.get(0);
        View owned = tree.panel;
        System.out.println("owned after switch: completed=" + stateField(state, "panelTransitionCompleted")
                + " depth=" + stateField(state, "panelSwitchDepth")
                + " ready=" + stateField(state, "panelReady")
                + " panelState=" + (stateField(state, "panelState") != null)
                + " panelCard=" + (tree.panel.getParent() == tree.card)
                + " additions=" + tree.card.additions
                + " hostMeasured=" + tree.scroll.getMeasuredHeight()
                + " host=" + tree.scroll.getHeight()
                + " logs=" + XposedBridge.logs.subList(Math.max(0, XposedBridge.logs.size() - 4), XposedBridge.logs.size()));
        check(tree.panel.getParent() == tree.card && tree.card.additions == cardAddsBefore + 1,
                "successful native after commits panel ownership before first preDraw");
        int additions = tree.card.additions;
        int removals = tree.card.removals;
        for (int i=0;i<2;i++) {
            tree.frame();
            check(stateField(state,"panel") == owned && Boolean.TRUE.equals(stateField(state,"panelReady")),
                    "two actual preDraw passes keep same owned panel ready");
            check(tree.card.additions == additions && tree.panel.getParent() == tree.card,
                    "preDraw does not restore/reparent owned identity");
        }
        tree.hostHeight = 60; tree.inputRow.natural = 144; tree.frame();
        check(tree.panel.getParent() == tree.card && tree.card.additions == additions
                        && tree.card.removals == removals,
                "one transient undersized frame does not move a committed panel");
        tree.hostHeight = 1000; tree.inputRow.natural = 60;
        tree.frame(); tree.frame();
        check(tree.panel.getParent() == tree.card && tree.card.additions == additions
                        && tree.card.removals == removals,
                "recovered host size keeps the same committed panel identity");
        Method lookup = FloatingChatFooterFeature.class.getDeclaredMethod("findUnique",View.class,String.class,int.class); lookup.setAccessible(true);
        check(lookup.invoke(null,tree.footer,ChatFooterBottom.class.getName(),256) == owned,
                "strict footer subtree lookup sees supported in-footer moved panel");
        check(lookup.invoke(null,tree.input,ChatFooterBottom.class.getName(),256) == null,
                "tree lookup never globally finds panel removed from original input");
        tree.card.removeView(tree.panel);
        ChatFooterBottom replacement = new ChatFooterBottom(tree.activity);
        ViewGroup.LayoutParams replacementParams = new ViewGroup.LayoutParams(-1,240);
        tree.input.addView(replacement,1,replacementParams);
        tree.footer.getViewTreeObserver().draw();
        check(tree.panel.getParent() == null && !tree.panel.isAttachedToWindow(),
                "host replacement must not resurrect removed owned panel into original input");
        check(stateField(state,"panel") == replacement && Boolean.TRUE.equals(stateField(state,"panelReady")),
                "replacement becomes sole owned panel without ambiguous duplicate");
        tree.footer.getViewTreeObserver().draw();
        check(stateField(state,"panel") == replacement && replacement.getParent() == tree.card,
                "second preDraw keeps replacement, not stale orphan");
        enable(false);
        check(replacement.getParent() == tree.input && replacement.getLayoutParams() == replacementParams,
                "disable restores only replacement and exact replacement params");
        check(tree.panel.getParent() == null,"disable never resurrects old orphan");
        stop(tree.activity);
        System.out.println("GREEN owned-panel sequence: strict subtree find, two preDraw passes, host replacement/orphan, exact restore");
    }

    private static Object stateField(Object state,String name) throws Exception {
        Field field = state.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(state);
    }

    private static void edgeRebinding() throws Throwable {
        enable(true); FooterTree tree = new FooterTree(); tree.settle();
        XposedBridge.constructed(tree.footer);
        check(tree.layout.paint.getAlpha() == 0, "initial layout strip acquired");
        ChattingUILayout nextLayout = new ChattingUILayout(tree.activity);
        ChattingScrollLayout nextScroll = new ChattingScrollLayout(tree.activity);
        root(tree.activity).addView(nextLayout,new ViewGroup.LayoutParams(400,700));
        nextLayout.addView(nextScroll,new ViewGroup.LayoutParams(400,700));
        // Refit an attached island without delivering a footer detach notification.
        tree.scroll.children.remove(tree.footer);nextScroll.children.add(tree.footer);
        Field parent=View.class.getDeclaredField("parent");parent.setAccessible(true);parent.set(tree.footer,nextScroll);
        tree.footer.getViewTreeObserver().draw();
        check(tree.layout.paint.getAlpha() == 255 && nextLayout.paint.getAlpha() == 0,
                "layout identity change releases the old mask and acquires the new mask");
        Field states=FloatingChatFooterFeature.class.getDeclaredField("SCROLL_STATES");states.setAccessible(true);
        Map<?,?> scrolls=(Map<?,?>)states.get(null);
        check(!scrolls.containsKey(tree.scroll) && scrolls.containsKey(nextScroll),
                "scroll identity change removes stale callback ownership");
        android.view.Window previousWindow=tree.activity.getWindow();
        Field window=Activity.class.getDeclaredField("window");window.setAccessible(true);
        android.view.Window nextWindow=new android.view.Window(tree.activity);window.set(tree.activity,nextWindow);
        tree.footer.getViewTreeObserver().draw();
        check(stateField(tree.footer.attachListeners.get(0),"edgeWindow")==nextWindow,
                "window identity change transfers bottom ownership to the current window");
        window.set(tree.activity,previousWindow);
        tree.footer.getViewTreeObserver().draw();
        stop(tree.activity);
        check(nextLayout.paint.getAlpha() == 255, "disable restores the current layout mask");
    }

    private static void initialNativeClip() throws Throwable {
        enable(true);FooterTree tree=new FooterTree();tree.layoutHeight=700;
        nativeWeightedFrames(tree);
        XposedBridge.constructed(tree.footer);nativeWeightedFrames(tree);
        int[] origin=new int[2];tree.inputRow.getLocationOnScreen(origin);
        check(origin[1]+tree.inputRow.getHeight()==692,
                "first native input row stays above a short clipping ancestor without a panel transition: bottom="
                        +(origin[1]+tree.inputRow.getHeight()));
        check(tree.panel.getParent()==tree.input && tree.panel.getVisibility()==View.VISIBLE
                && tree.panelParams.height==300,"unknown panel keeps native tree, visibility and natural height");
        stop(tree.activity);
    }

    private static void nativeWeightedFrames(FooterTree tree) {
        // Native content receives the remaining LinearLayout weight budget; the
        // generic test container otherwise always measures MATCH_PARENT at 1000.
        for(int i=0;i<4;i++){
            tree.frame();
            tree.content.getLayoutParams().height=Math.max(0,tree.hostHeight
                    -tree.footer.getMeasuredHeight()-tree.params.topMargin-tree.params.bottomMargin);
        }
        tree.frame();
    }

    private static final class NaturalRow extends View {
        int natural;
        NaturalRow(Context context,int height) { super(context);natural=height; }
        @Override protected void onMeasure(int width,int height) { setMeasuredDimension(resolveSize(400,width),resolveSize(natural,height)); }
    }
    private static final class FooterTree {
        final LauncherUI activity=new LauncherUI();
        final ChattingUILayout layout=new ChattingUILayout(activity);
        final ChattingScrollLayout scroll=new ChattingScrollLayout(activity);
        final com.tencent.mm.ui.chatting.view.MMChattingListView content=new com.tencent.mm.ui.chatting.view.MMChattingListView(activity);
        final com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView recycler=new com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView(activity);
        final ChatFooter footer=new ChatFooter(activity);
        final RelativeLayout card=new RelativeLayout(activity);
        final LinearLayout input=new LinearLayout(activity);
        final NaturalRow inputRow=new NaturalRow(activity,60),voice=new NaturalRow(activity,0);
        final ChatFooterBottom panel=new ChatFooterBottom(activity);
        final ViewGroup.MarginLayoutParams params=margins(0,0,0,-300);
        final ViewGroup.LayoutParams panelParams=new ViewGroup.LayoutParams(-1,300);
        int hostHeight=1000;
        int layoutHeight=1000;
        int windowHeight=1000;
        int width=400;
        int rootX,rootY;
        FooterTree() {
            root(activity).layout(rootX,rootY,rootX+width,rootY+1000); root(activity).addView(layout,new ViewGroup.LayoutParams(-1,-1));
            layout.addView(scroll,new ViewGroup.LayoutParams(-1,-1)); scroll.addView(footer,params);
            footer.addView(card,new ViewGroup.LayoutParams(-1,-2));card.addView(input,new RelativeLayout.LayoutParams(-1,-2));
            input.addView(inputRow,new ViewGroup.LayoutParams(-1,-2));input.addView(panel,panelParams);
            voice.setVisibility(View.GONE);input.addView(voice,new ViewGroup.LayoutParams(-1,-2));root(activity).attach();
        }
        void settle() { for (int i=0;i<3;i++) frame(); }
        void frame() {
            // Exactly one actual measure/layout/preDraw pass; no manual bottom alignment.
            int widthSpec=View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY);
            int height=View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST);
            root(activity).layout(rootX,rootY,rootX+width,rootY+windowHeight);
            activity.getWindowManager().bounds.left=rootX;
            activity.getWindowManager().bounds.top=rootY;
            activity.getWindowManager().bounds.right=rootX+width;
            activity.getWindowManager().bounds.bottom=rootY+windowHeight;
            layout.layout(0,0,width,layoutHeight);scroll.layout(0,0,width,hostHeight);
            footer.measure(widthSpec,height);
            if (content.getParent() == null) {
                content.addView(recycler,new ViewGroup.LayoutParams(-1,-1));
                scroll.addView(content,0,new ViewGroup.LayoutParams(-1,-1));
            }
            scroll.measure(widthSpec,View.MeasureSpec.makeMeasureSpec(hostHeight,View.MeasureSpec.EXACTLY));
            scroll.layout(0,0,width,hostHeight);
            footer.getViewTreeObserver().draw();
        }
    }

    private static class DerivedFooter extends ChatFooter { DerivedFooter(Context context) { super(context); } }
    private static ViewGroup root(Activity a) { return (ViewGroup)a.getWindow().getDecorView(); }
    private static ViewGroup.MarginLayoutParams margins(int l,int t,int r,int b) { ViewGroup.MarginLayoutParams p=new ViewGroup.MarginLayoutParams(-1,-2);p.leftMargin=l;p.topMargin=t;p.rightMargin=r;p.bottomMargin=b;return p; }
    private static void resume(Activity activity) throws Throwable { XposedBridge.invoke(activity,"onResume",new Class<?>[0]); }
    private static void enable(boolean value) { WexStore.INSTANCE.flags.put(FloatingChatFooterFeature.KEY_ENABLED,value);FloatingChatFooterFeature.refresh();Handler.drain(); }
    private static void stop(Activity activity) throws Throwable { enable(false);XposedBridge.invoke(activity,"onPause",new Class<?>[0]);root(activity).detach();Handler.finish(); }
    private static void check(boolean valid,String message) { assertions++;if(!valid)throw new AssertionError(message); }
}
