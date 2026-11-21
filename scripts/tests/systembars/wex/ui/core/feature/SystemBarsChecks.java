package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout;
import com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout;
import de.robv.android.xposed.XposedBridge;

/** Host-JVM contract checks; both coordinators below are production, not doubles. */
public final class SystemBarsChecks {
    static final int HOST_NAV=0xFFEFEFEF, HOST_DIVIDER=0xFFDDDDDD, HOST_STATUS=0xFFEEEEEE;
    static final int PAGE=0xFFF7F7F7;
    static class Fixture {
        final Activity activity=new Activity();
        final Window window=activity.getWindow();
        final EdgeToEdgeWrapperLayout wrapper=new EdgeToEdgeWrapperLayout(activity);
        final ChattingUILayout chat=new ChattingUILayout(activity);
        Fixture(){window.decor.addView(wrapper);wrapper.addView(chat);chat.id=android.R.id.content;chat.background=new ColorDrawable(PAGE);}
        void close(){ChatWindowInsetsCoordinator.release(window,3);SystemBarsCoordinator.restore(window);}
        void resume(){XposedBridge.call("onResume",activity);window.decor.drain();}
        void draw(){window.decor.observer.draw();window.decor.drain();}
    }
    public static void main(String[] args) {
        SystemBarsCoordinator.init();
        String scenario=args[0];
        if(scenario.startsWith("host-")) { hostChecks(scenario); return; }
        if(scenario.startsWith("home-")) {
            Home f=new Home(scenario.equals("home-dark"));
            f.window.controller.setSystemBarsAppearance(f.dark?8:0,8);
            if(scenario.equals("home-focus")) f.focus(true); else f.resume();
            eq(f.dark?0:8,f.window.controller.getSystemBarsAppearance()&8,"home background determines status glyphs after host lifecycle");
            return;
        }
        if(scenario.startsWith("return-")) {
            Home f=new Home(false);ChattingUILayout layout=new ChattingUILayout(f.activity);f.content.addView(layout);f.resume();
            f.window.controller.setSystemBarsAppearance(0,8);
            if(scenario.equals("return-remove")) f.content.removeView(layout);
            else {f.content.visibility=View.GONE;layout.getViewTreeObserver().layout();}
            f.window.decor.drain();
            eq(8,f.window.controller.getSystemBarsAppearance()&8,"same Activity chat exit without resume/focus restores home glyph contrast");return;
        }
        if(scenario.startsWith("review-")) { reviewChecks(scenario); return; }
        if(scenario.equals("status-green")) { statusChecks(); return; }
        if(scenario.equals("acquire-wrapper")) {
            Fixture f=new Fixture();ChatWindowInsetsCoordinator.acquire(f.activity,2);
            eq(Color.TRANSPARENT,f.wrapper.color,"BOTTOM acquire must clear named wrapper white strip");
            return;
        }
        if(scenario.equals("resume-ownership")) {
            Fixture f=new Fixture();ChatWindowInsetsCoordinator.acquire(f.activity,2);f.resume();
            eq(Color.TRANSPARENT,f.window.getNavigationBarColor(),"resume post must respect active BOTTOM rather than repaint page surface");
            return;
        }
        if(scenario.equals("missing-getter")) {
            Fixture f=new Fixture();ChatWindowInsetsCoordinator.acquire(f.activity,2);f.resume();
            eq(0xFFFFFFFF,f.wrapper.color,"missing wrapper getter must not invoke setter");eq(0,f.wrapper.writes,"missing getter setter count");f.close();
            System.out.println("GREEN: named wrapper without getter remains untouched; actual coordinator pair");return;
        }
        int checks=0;
        Fixture f=new Fixture();ChatWindowInsetsCoordinator.acquire(f.activity,2);f.resume();
        eq(0,f.window.getNavigationBarColor(),"gesture Window transparent");checks++;
        eq(0,f.wrapper.color,"gesture wrapper transparent");checks++;
        check(!f.window.isNavigationBarContrastEnforced(),"gesture contrast disabled");checks++;
        int writes=f.window.writes+f.wrapper.writes;f.draw();f.draw();
        eq(writes,f.window.writes+f.wrapper.writes,"stable preDraw makes no repeated writes");checks++;
        f.window.decor.insets.tap=24;f.draw();
        eq(PAGE,f.wrapper.color,"three-button wrapper opaque");checks++;
        check(f.window.isNavigationBarContrastEnforced(),"three-button contrast preserved");checks++;
        f.window.decor.insets.tap=0;f.window.decor.insets.ime=300;f.draw();
        eq(0,f.window.getNavigationBarColor(),"gesture background stays transparent during IME");checks++;
        f.window.decor.insets.ime=0;f.draw();eq(0,f.wrapper.color,"gesture returns after IME");checks++;
        f.chat.height=776;f.draw();eq(0,f.wrapper.color,"uncovered strip exposes window background by user request, not page surface");checks++;
        f.chat.height=800;f.wrapper.paddingBottom=24;f.draw();eq(0,f.wrapper.color,"ancestor padding clip no longer blocks owned transparency");checks++;
        f.wrapper.paddingBottom=0;f.draw();eq(0,f.wrapper.color,"full coverage stays transparent");checks++;
        f.wrapper.throwGetter=true;int wrapperWrites=f.wrapper.writes;f.draw();eq(wrapperWrites,f.wrapper.writes,"throwing getter not written");checks++;
        f.wrapper.throwGetter=false;f.window.setNavigationBarColor(0xFF123456);f.wrapper.color=0xFF234567;
        f.draw();eq(0,f.wrapper.color,"host paint reasserted while owned");checks++;
        f.close();eq(0xFF123456,f.window.getNavigationBarColor(),"latest host navigation restored");checks++;
        eq(0xFF234567,f.wrapper.color,"latest host wrapper restored");checks++;
        check(f.window.decor.observer.listeners.isEmpty(),"owner observer removed");checks++;
        Fixture top=new Fixture();ChatWindowInsetsCoordinator.acquire(top.activity,1);top.resume();
        eq(0,top.window.getNavigationBarColor(),"gesture transparency does not require a chat BOTTOM owner");checks++;
        eq(0,top.window.getStatusBarColor(),"existing TOP transparency preserved");checks++;
        ChatWindowInsetsCoordinator.acquire(top.activity,2);top.resume();eq(0,top.wrapper.color,"BOTTOM independent from TOP");checks++;
        ChatWindowInsetsCoordinator.release(top.window,2);eq(0,top.window.getStatusBarColor(),"BOTTOM release does not restore TOP");checks++;
        ChatWindowInsetsCoordinator.release(top.window,1);eq(HOST_STATUS,top.window.getStatusBarColor(),"TOP original restored");checks++;
        Fixture reassert=new Fixture();ChatWindowInsetsCoordinator.acquire(reassert.activity,2);check(!reassert.window.fits,"acquire leaves edge-to-edge");checks++;
        reassert.window.fits=true;ChatWindowInsetsCoordinator.reassertBottom(reassert.activity);check(!reassert.window.fits,"reassert keeps owned edge-to-edge");checks++;
        check(!ChatWindowInsetsCoordinator.needsBottomReassert(reassert.activity),"a fresh reassert leaves no pending demand");checks++;
        reassert.window.setDecorFitsSystemWindows(true);check(ChatWindowInsetsCoordinator.needsBottomReassert(reassert.activity),"an actual host re-fit demands exactly one bottom reassert");checks++;
        ChatWindowInsetsCoordinator.reassertBottom(reassert.activity);check(!ChatWindowInsetsCoordinator.needsBottomReassert(reassert.activity),"reassert clears the demand until the next host re-fit");checks++;
        reassert.close();check(reassert.window.fits,"release restores the original fit");checks++;
        Fixture late=new Fixture();late.wrapper.removeView(late.chat);ChatWindowInsetsCoordinator.acquire(late.activity,2);
        eq(0,late.wrapper.color,"ownership alone exposes the strip; window background is the fallback");checks++;
        late.chat.attached=true;late.wrapper.addView(late.chat);late.draw();eq(0,late.wrapper.color,"late chat attach recovers on draw");checks++;
        late.close();
        Fixture detached=new Fixture();ChatWindowInsetsCoordinator.acquire(detached.activity,2);detached.chat.detach();detached.draw();
        eq(0,detached.wrapper.color,"detached chat under owned bottom exposes window background");checks++;
        detached.close();
        Fixture overwrite=new Fixture();ChatWindowInsetsCoordinator.acquire(overwrite.activity,2);
        overwrite.window.setNavigationBarColor(0xFF456789);overwrite.wrapper.color=0xFF567890;overwrite.close();
        eq(0xFF456789,overwrite.window.getNavigationBarColor(),"external write preserved on close");checks++;
        eq(0xFF567890,overwrite.wrapper.color,"external wrapper write preserved on close");checks++;
        Fixture queued=new Fixture();XposedBridge.call("onResume",queued.activity);
        wex.ui.core.WexStore.INSTANCE.enabled=false;queued.window.decor.drain();
        eq(HOST_NAV,queued.window.getNavigationBarColor(),"disable before posted resume prevents stale write");checks++;
        wex.ui.core.WexStore.INSTANCE.enabled=true;queued.close();
        System.out.println("GREEN: "+checks+" navigation Window/wrapper/coverage/ownership/restore assertions; both actual coordinators");
    }
    static final class Home {
        final com.tencent.mm.ui.LauncherUI activity=new com.tencent.mm.ui.LauncherUI();
        final Window window=activity.getWindow();
        final android.view.ViewGroup content=new android.view.ViewGroup(activity);
        final boolean dark;
        Home(boolean dark){this.dark=dark;activity.dark=dark;content.id=android.R.id.content;
            content.background=new ColorDrawable(dark?0xFF191919:PAGE);window.decor.addView(content);}
        void resume(){XposedBridge.call("onResume",activity);window.decor.drain();}
        void focus(boolean gained){window.decor.focused=gained;XposedBridge.call("onWindowFocusChanged",activity,gained);window.decor.drain();}
        void close(){XposedBridge.call("onDestroy",activity);}
    }
    static void reviewChecks(String scenario) {
        if(!scenario.equals("review-green")) {
            Home home=new Home(false);ChattingUILayout chat=new ChattingUILayout(home.activity);
            home.content.addView(chat); // onCreate creates this BEFORE first onResume installs constructor hook.
            if(scenario.equals("review-hidden-view")) chat.visibility=View.GONE;
            if(scenario.equals("review-hidden-ancestor")) home.content.visibility=View.GONE;
            home.resume();
            if(scenario.equals("review-alpha")) {
                chat.alpha=0;chat.getViewTreeObserver().layout();home.window.decor.drain();
                eq(8,home.window.controller.getSystemBarsAppearance()&8,"observed chat alpha-zero layout must signal home");
            } else {
                chat.visibility=View.VISIBLE;home.content.visibility=View.VISIBLE;
                chat.getViewTreeObserver().layout();home.window.decor.drain();
                home.window.controller.setSystemBarsAppearance(0,8); // native chat page now paints light glyphs.
                chat.visibility=View.GONE;chat.getViewTreeObserver().layout();home.window.decor.drain();
                eq(8,home.window.controller.getSystemBarsAppearance()&8,"pre-resume hidden chat must observe same-Activity show/hide return");
            }
            return;
        }
        int checks=0;
        for(boolean ancestor:new boolean[]{false,true}) {
            Home home=new Home(false);ChattingUILayout chat=new ChattingUILayout(home.activity);home.content.addView(chat);
            if(ancestor) home.content.visibility=View.GONE; else chat.visibility=View.GONE;
            home.resume();eq(1,chat.attachListeners.size(),"first resume discovers hidden known chat");checks++;
            eq(1,chat.getViewTreeObserver().layouts.size(),"hidden known chat gets one lifecycle observer");checks++;
            chat.visibility=View.VISIBLE;home.content.visibility=View.VISIBLE;chat.getViewTreeObserver().layout();home.window.decor.drain();
            eq(0,home.window.controller.getSystemBarsAppearance()&8,"pre-resume hidden chat shown without Activity lifecycle");checks++;
            home.window.controller.setSystemBarsAppearance(0,8);chat.visibility=View.GONE;chat.getViewTreeObserver().layout();home.window.decor.drain();
            eq(8,home.window.controller.getSystemBarsAppearance()&8,"pre-resume hidden chat return without Activity lifecycle");checks++;
            home.close();check(chat.getViewTreeObserver().layouts.isEmpty()&&chat.attachListeners.isEmpty(),"hidden discovered chat destroyed cleanup");checks++;
        }
        for(boolean ancestor:new boolean[]{false,true}) {
            Home home=new Home(false);ChattingUILayout chat=new ChattingUILayout(home.activity);home.content.addView(chat);home.resume();
            View faded=ancestor?home.content:chat;faded.alpha=0;chat.getViewTreeObserver().layout();home.window.decor.drain();
            eq(8,home.window.controller.getSystemBarsAppearance()&8,"own or ancestor alpha-zero returns home");checks++;
            int writes=home.window.controller.writes;for(int i=0;i<100;i++)chat.getViewTreeObserver().layout();
            eq(0,home.window.decor.posts.size(),"unchanged effective visibility never queues tree scan");checks++;
            eq(writes,home.window.controller.writes,"unchanged alpha-zero never writes glyphs");checks++;
            faded.alpha=1;chat.getViewTreeObserver().layout();home.window.decor.drain();
            eq(0,home.window.controller.getSystemBarsAppearance()&8,"alpha restore returns visible native chat");checks++;
            XposedBridge.call("onPause",home.activity);faded.alpha=0;chat.getViewTreeObserver().layout();home.window.decor.drain();
            eq(0,home.window.controller.getSystemBarsAppearance()&8,"paused alpha transition cannot write home");checks++;
            home.close();
        }
        Home privateBody=new Home(false);ChattingUILayout shell=new ChattingUILayout(privateBody.activity);
        shell.visibility=View.GONE;privateBody.content.addView(shell);
        for(int i=0;i<250;i++)shell.addView(new View(privateBody.activity) {
            @Override public int getVisibility(){throw new AssertionError("discovery must not read message/input visibility");}
            @Override public float getAlpha(){throw new AssertionError("discovery must not read message/input alpha");}
        });
        privateBody.resume();eq(8,privateBody.window.controller.getSystemBarsAppearance()&8,"hidden chat body cannot exhaust discovery budget");checks++;
        eq(1,shell.attachListeners.size(),"only known chat root is observed");checks++;
        privateBody.close();
        Home multiple=new Home(false);ChattingUILayout visible=new ChattingUILayout(multiple.activity),hidden=new ChattingUILayout(multiple.activity);
        hidden.visibility=View.GONE;multiple.content.addView(visible);multiple.content.addView(hidden);multiple.resume();
        eq(1,hidden.attachListeners.size(),"visible chat cannot prevent discovering hidden sibling");checks++;
        multiple.close();
        Home clipped=new Home(false);
        // Zero geometry exercises the normal known root's visible-Rect edge.
        ChattingUILayout known=new ChattingUILayout(clipped.activity);clipped.content.addView(known);clipped.resume();
        known.height=0;known.getViewTreeObserver().layout();clipped.window.decor.drain();
        eq(8,clipped.window.controller.getSystemBarsAppearance()&8,"visible-Rect loss uses same predicate in observer and home");checks++;
        known.height=800;known.getViewTreeObserver().layout();clipped.window.decor.drain();
        eq(0,clipped.window.controller.getSystemBarsAppearance()&8,"visible-Rect recovery returns native chat");checks++;
        clipped.close();
        System.out.println("GREEN: "+checks+" pre-review blocker hidden-discovery/effective-alpha lifecycle assertions; actual production pair");
    }
    static void statusChecks() {
        int checks=0;
        for(boolean dark:new boolean[]{false,true}) {
            Home f=new Home(dark);int value=dark?0:8;
            f.window.controller.setSystemBarsAppearance((dark?8:0)|64,72);
            f.resume();eq(value,f.window.controller.getSystemBarsAppearance()&8,"resume status contrast");checks++;
            eq(64,f.window.controller.getSystemBarsAppearance()&64,"unrelated appearance preserved");checks++;
            eq(HOST_STATUS,f.window.getStatusBarColor(),"home status background not repainted");checks++;
            int writes=f.window.controller.writes;f.resume();f.focus(true);
            eq(writes,f.window.controller.writes,"stable resume/focus has no glyph writes");checks++;
            for(int i=0;i<6;i++) {
                f.window.controller.setSystemBarsAppearance(dark?8:0,8);
                f.focus(true);eq(value,f.window.controller.getSystemBarsAppearance()&8,"repeated chat return focus contrast");checks++;
            }
            f.window.controller.setSystemBarsAppearance(dark?8:0,8);f.focus(false);
            eq(dark?8:0,f.window.controller.getSystemBarsAppearance()&8,"focus loss not repainted");checks++;
            f.focus(true);f.close();eq(dark?8:0,f.window.controller.getSystemBarsAppearance()&8,"latest host appearance conditionally restored");checks++;
        }
        Home contentDark=new Home(false);contentDark.content.background=new ColorDrawable(0xFF101010);
        contentDark.window.controller.setSystemBarsAppearance(8,8);contentDark.resume();
        eq(0,contentDark.window.controller.getSystemBarsAppearance()&8,"opaque page overrides light configuration");checks++;
        contentDark.close();
        Home unknown=new Home(true);unknown.content.background=new ColorDrawable(0x00101010);unknown.resume();
        eq(0,unknown.window.controller.getSystemBarsAppearance()&8,"transparent content uses theme fallback");checks++;
        unknown.close();
        Home chat=new Home(false);ChattingUILayout layout=new ChattingUILayout(chat.activity);chat.content.addView(layout);
        chat.resume();eq(0,chat.window.controller.getSystemBarsAppearance()&8,"active chat skipped with TOP feature disabled");checks++;
        layout.visibility=View.GONE;chat.focus(true);eq(8,chat.window.controller.getSystemBarsAppearance()&8,"hidden chat permits home contrast");checks++;
        layout.visibility=View.VISIBLE;chat.focus(true);eq(0,chat.window.controller.getSystemBarsAppearance()&8,"home status ownership restored on visible chat");checks++;
        chat.close();
        Home top=new Home(true);top.resume();ChatWindowInsetsCoordinator.acquire(top.activity,1);
        top.activity.dark=false;top.window.controller.setSystemBarsAppearance(8,8);top.focus(true);
        eq(8,top.window.controller.getSystemBarsAppearance()&8,"TOP glyph ownership not overwritten");checks++;
        ChatWindowInsetsCoordinator.release(top.window,1);
        eq(8,top.window.controller.getSystemBarsAppearance()&8,"TOP release preserves later host request, not stale acquire snapshot");checks++;
        top.close();
        Home cleanTop=new Home(true);cleanTop.resume();ChatWindowInsetsCoordinator.acquire(cleanTop.activity,1);
        cleanTop.activity.dark=false;ChatWindowInsetsCoordinator.acquire(cleanTop.activity,1);
        ChatWindowInsetsCoordinator.release(cleanTop.window,1);
        eq(0,cleanTop.window.controller.getSystemBarsAppearance()&8,"unchanged TOP-owned request restores first TOP acquire snapshot");checks++;
        cleanTop.close();
        Home bottom=new Home(false);bottom.resume();ChatWindowInsetsCoordinator.acquire(bottom.activity,2);
        ChatWindowInsetsCoordinator.release(bottom.window,2);
        eq(8,bottom.window.controller.getSystemBarsAppearance()&8,"BOTTOM release cannot restore home status");checks++;
        bottom.close();
        Home disabled=new Home(false);disabled.resume();wex.ui.core.WexStore.INSTANCE.enabled=false;disabled.focus(true);
        eq(0,disabled.window.controller.getSystemBarsAppearance()&8,"disable restores status ownership");checks++;
        wex.ui.core.WexStore.INSTANCE.enabled=true;disabled.close();
        Home external=new Home(false);external.resume();external.window.controller.setSystemBarsAppearance(0,8);external.close();
        eq(0,external.window.controller.getSystemBarsAppearance()&8,"independent host write survives destroy");checks++;
        Home queued=new Home(false);XposedBridge.call("onResume",queued.activity);XposedBridge.call("onPause",queued.activity);queued.window.decor.drain();
        eq(0,queued.window.controller.getSystemBarsAppearance()&8,"pause cancels pending resume");checks++;
        eq(HOST_NAV,queued.window.getNavigationBarColor(),"pause cancels pending navigation");checks++;
        queued.close();
        Home destroyed=new Home(false);XposedBridge.call("onResume",destroyed.activity);destroyed.close();destroyed.window.decor.drain();
        eq(0,destroyed.window.controller.getSystemBarsAppearance()&8,"destroy cancels pending status");checks++;
        Home replaced=new Home(false);Window original=replaced.window;XposedBridge.call("onResume",replaced.activity);
        replaced.activity.window=new Window(replaced.activity);original.decor.drain();
        eq(HOST_NAV,original.getNavigationBarColor(),"stale window not written");checks++;
        Home budget=new Home(false);for(int i=0;i<200;i++)budget.content.addView(new View(budget.activity));budget.resume();
        eq(0,budget.window.controller.getSystemBarsAppearance()&8,"home scan budget exhausted fails closed");checks++;
        budget.close();
        Fixture other=new Fixture();other.resume();eq(0,other.window.controller.getSystemBarsAppearance()&8,"other Activity status untouched");checks++;
        other.close();
        Home same=new Home(false);ChattingUILayout sameChat=new ChattingUILayout(same.activity);same.content.addView(sameChat);same.resume();
        same.content.visibility=View.GONE;sameChat.getViewTreeObserver().layout();same.window.decor.drain();
        eq(8,same.window.controller.getSystemBarsAppearance()&8,"ancestor hide returns home without resume/focus");checks++;
        int statusWrites=same.window.controller.writes;for(int i=0;i<100;i++)sameChat.getViewTreeObserver().layout();
        eq(0,same.window.decor.posts.size(),"stable known-chat layout does not queue full-tree work");checks++;
        eq(statusWrites,same.window.controller.writes,"stable layout does not write glyphs");checks++;
        same.content.visibility=View.VISIBLE;sameChat.getViewTreeObserver().layout();same.window.decor.drain();
        eq(0,same.window.controller.getSystemBarsAppearance()&8,"visible chat restores native glyphs");checks++;
        same.content.removeView(sameChat);same.window.decor.drain();
        eq(8,same.window.controller.getSystemBarsAppearance()&8,"chat removal returns home without lifecycle event");checks++;
        check(sameChat.getViewTreeObserver().layouts.isEmpty(),"detached target removes global layout observer");checks++;
        same.close();check(sameChat.attachListeners.isEmpty(),"destroy removes target attach observer");checks++;
        Home constructed=new Home(false);constructed.resume();ChattingUILayout newly=new ChattingUILayout(constructed.activity);
        XposedBridge.call("construct",newly);constructed.content.addView(newly);newly.getViewTreeObserver().layout();constructed.window.decor.drain();
        eq(0,constructed.window.controller.getSystemBarsAppearance()&8,"late known chat constructor observed");checks++;
        XposedBridge.call("onPause",constructed.activity);constructed.content.removeView(newly);constructed.window.decor.drain();
        eq(0,constructed.window.controller.getSystemBarsAppearance()&8,"paused chat exit cannot queue home write");checks++;
        constructed.close();
        System.out.println("GREEN: "+checks+" home status lifecycle/contrast/ownership/conditional-restore assertions; actual production coordinators");
    }
    static Home hostHome() { Home f=new Home(false);f.activity.gateAvailable=true;f.window.setStatusBarColor(PAGE);return f; }
    static void hostChecks(String scenario) {
        if(scenario.equals("host-green")) {
            for(String s:new String[]{"host-four-pages","host-headerless-chat","host-top-surface","host-legacy-flags","host-event","host-top-release"})hostChecks(s);
            Home disabled=hostHome();disabled.resume();wex.ui.core.WexStore.INSTANCE.enabled=false;
            disabled.window.setStatusBarColor(0xFF101010);XposedBridge.call("w",disabled.activity.home,true);disabled.window.decor.drain();
            eq(0,disabled.window.controller.getSystemBarsAppearance()&8,"exact host event keeps disabled gate");
            wex.ui.core.WexStore.INSTANCE.enabled=true;disabled.close();
            Home paused=hostHome();paused.resume();XposedBridge.call("onPause",paused.activity);
            paused.window.controller.setSystemBarsAppearance(0,8);XposedBridge.call("w",paused.activity.home,true);paused.window.decor.drain();
            eq(0,paused.window.controller.getSystemBarsAppearance()&8,"host page event cannot write a paused window");paused.close();
            Home transparent=hostHome();transparent.window.setStatusBarColor(0);transparent.content.background=new ColorDrawable(0xFF101010);transparent.resume();
            eq(0,transparent.window.controller.getSystemBarsAppearance()&8,"transparent status uses existing opaque page, not fabricated wallpaper");
            eq(0,transparent.window.getStatusBarColor(),"transparent status remains transparent");transparent.close();
            Home unknown=hostHome();unknown.window.setStatusBarColor(0);unknown.content.background=null;unknown.window.controller.setSystemBarsAppearance(8,8);unknown.resume();
            eq(8,unknown.window.controller.getSystemBarsAppearance()&8,"unknown transparent surface preserves host request");unknown.close();
            Home timing=hostHome();timing.resume();timing.window.setStatusBarColor(0xFF101010);
            XposedBridge.callExact(com.tencent.mm.ui.LauncherUI.class,"onResume",timing.activity);timing.window.decor.drain();
            eq(0,timing.window.controller.getSystemBarsAppearance()&8,"exact Launcher resume after super rechecks later host surface");
            int stableWrites=timing.window.controller.writes,stableUi=timing.window.decor.uiWrites;
            for(int i=0;i<100;i++){XposedBridge.callExact(com.tencent.mm.ui.HomeUI.class,"w",timing.activity.home,true);XposedBridge.callExact(com.tencent.mm.ui.HomeUI.class,"v",timing.activity.home,0xFF101010);}
            eq(1,timing.window.decor.posts.size(),"100 stable host notifications remain one coalesced queue turn");timing.window.decor.drain();
            eq(stableWrites,timing.window.controller.writes,"stable known host event has no repeated API writes");
            eq(stableUi,timing.window.decor.uiWrites,"stable known host event has no repeated legacy writes");
            timing.activity.chatForeground=true;XposedBridge.callExact(com.tencent.mm.ui.HomeUI.class,"w",timing.activity.home,true);timing.window.decor.drain();
            timing.activity.chatForeground=false;XposedBridge.callExact(com.tencent.mm.ui.LauncherUI.class,"closeChatting",timing.activity,false);timing.window.decor.drain();
            eq(0,timing.window.controller.getSystemBarsAppearance()&8,"exact host closeChatting refreshes home without visibility changes");timing.close();
            for(boolean sync:new boolean[]{false,true}) {
                Home coupled=hostHome();coupled.window.decor.syncLegacyAppearance=sync;coupled.resume();
                coupled.window.controller.setSystemBarsAppearance(0,8);coupled.close();
                eq(0,coupled.window.controller.getSystemBarsAppearance()&8,"legacy restore cannot override independently updated API request");
                Home release=hostHome();release.window.decor.syncLegacyAppearance=sync;release.resume();
                ChatWindowInsetsCoordinator.acquire(release.activity,1);release.window.controller.setSystemBarsAppearance(0,8);
                ChatWindowInsetsCoordinator.release(release.window,1);
                eq(0,release.window.controller.getSystemBarsAppearance()&8,"TOP restores ownership based on pre-legacy API value");release.close();
            }
            Home directions=hostHome();ChatWindowInsetsCoordinator.acquire(directions.activity,2);
            directions.window.controller.setSystemBarsAppearance(0,8);directions.activity.dark=false;ChatWindowInsetsCoordinator.acquire(directions.activity,1);
            ChatWindowInsetsCoordinator.release(directions.window,1);eq(0,directions.window.controller.getSystemBarsAppearance()&8,"BOTTOM-first TOP snapshots at actual TOP acquire");
            check(ChatWindowInsetsCoordinator.isBottomOwned(directions.window),"TOP release keeps BOTTOM owner");
            directions.window.decor.ui=64;ChatWindowInsetsCoordinator.release(directions.window,2);
            eq(64,directions.window.decor.ui,"externally changed layout and non-owned flags survive last release");directions.close();
            System.out.println("GREEN: concrete Launcher.e7 / HomeUI.w,v / status-surface / legacy-api / TOP conditional-release contracts");return;
        }
        Home f=hostHome();
        if(scenario.equals("host-four-pages")) {
            ChattingUILayout cached=new ChattingUILayout(f.activity);f.content.addView(cached);
            for(int page=0;page<4;page++) {
                android.view.ViewGroup body=new android.view.ViewGroup(f.activity);f.content.addView(body);
                for(int i=0;i<80;i++)body.addView(new View(f.activity){
                    @Override public int getVisibility(){throw new AssertionError("known host foreground must not inspect private page bodies");}});
            }
            f.resume();eq(8,f.window.controller.getSystemBarsAppearance()&8,"four retained Launcher pages with attached cached chat are home by verified e7=false");
            ChatWindowInsetsCoordinator.acquire(f.activity,1);f.window.controller.setSystemBarsAppearance(0,8);f.focus(true);
            eq(8,f.window.controller.getSystemBarsAppearance()&8,"stale TOP bit cannot veto authoritative home foreground");
            ChatWindowInsetsCoordinator.release(f.window,1);f.close();return;
        }
        if(scenario.equals("host-headerless-chat")) {
            f.activity.chatForeground=true;ChattingUILayout active=new ChattingUILayout(f.activity);active.alpha=0;
            f.content.addView(active);f.resume();
            eq(0,f.window.controller.getSystemBarsAppearance()&8,"active host chat remains chat despite invisible header/root alpha/attachments");f.close();return;
        }
        if(scenario.equals("host-top-surface")) {
            f.window.setStatusBarColor(0xFF101010);f.window.controller.setSystemBarsAppearance(8,8);f.resume();
            eq(0,f.window.controller.getSystemBarsAppearance()&8,"same light theme, actual opaque dark status surface needs light glyphs");
            eq(0xFF101010,f.window.getStatusBarColor(),"status surface not repainted");f.close();return;
        }
        if(scenario.equals("host-legacy-flags")) {
            f.window.controller.setSystemBarsAppearance(8|64,72);f.window.decor.ui=64;f.resume();
            eq(8192,f.window.decor.getSystemUiVisibility()&8192,"legacy status bit aligned even when API controller already matches");
            eq(64,f.window.decor.getSystemUiVisibility()&64,"unrelated legacy flag preserved");
            eq(64,f.window.controller.getSystemBarsAppearance()&64,"unrelated controller appearance preserved");f.close();return;
        }
        if(scenario.equals("host-event")) {
            f.resume();f.window.setStatusBarColor(0xFF101010);f.window.decor.ui|=8192;
            XposedBridge.callExact(com.tencent.mm.ui.HomeUI.class,"w",f.activity.home,true);
            XposedBridge.callExact(com.tencent.mm.ui.HomeUI.class,"v",f.activity.home,0xFF101010);
            eq(1,f.window.decor.posts.size(),"host post-page concrete events merge into one UI turn");f.window.decor.drain();
            eq(0,f.window.controller.getSystemBarsAppearance()&8,"host four-page update after same Activity focus uses new actual surface");
            f.window.setStatusBarColor(PAGE);XposedBridge.callExact(com.tencent.mm.ui.HomeUI.class,"w",f.activity.home,true);f.window.decor.drain();
            eq(8,f.window.controller.getSystemBarsAppearance()&8,"host light surface recovers without Activity resume");f.close();return;
        }
        if(scenario.equals("host-top-release")) {
            f.resume();ChatWindowInsetsCoordinator.acquire(f.activity,1);
            f.window.setStatusBarColor(0xFF101010);f.window.controller.setSystemBarsAppearance(0,8);
            f.window.decor.ui=(f.window.decor.ui&~8192)|64;
            ChatWindowInsetsCoordinator.release(f.window,1);
            eq(0,f.window.controller.getSystemBarsAppearance()&8,"TOP release preserves later host status instead of stale acquire snapshot");
            eq(64,f.window.decor.getSystemUiVisibility()&64,"TOP release never replays full stale UI visibility");
            f.window.decor.drain();eq(0,f.window.controller.getSystemBarsAppearance()&8,"TOP release notifies home after owner removal");f.close();return;
        }
        throw new AssertionError("unknown host scenario "+scenario);
    }
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static void eq(int expected,int actual,String s){if(expected!=actual)throw new AssertionError(s+" expected="+Integer.toHexString(expected)+" actual="+Integer.toHexString(actual));}
}
