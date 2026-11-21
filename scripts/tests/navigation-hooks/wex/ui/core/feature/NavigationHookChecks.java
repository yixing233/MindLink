package wex.ui.core.feature;

import android.graphics.Canvas;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.tencent.mm.ui.FrostedContentView;
import com.tencent.mm.ui.LauncherUI;
import com.tencent.mm.ui.LauncherUIBottomTabView;
import com.tencent.mm.ui.MainTabUI;
import com.tencent.mm.ui.mogic.WxViewPager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;

/** Runs real Feature + Views + Session + NativeTabBridge + motion/controller, not a Views double. */
public final class NavigationHookChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Home {
        final LauncherUI activity=new LauncherUI();
        final FrameLayout branch=new FrameLayout(activity);
        final WxViewPager pager=new WxViewPager(activity);
        final LauncherUIBottomTabView nativeBar=new LauncherUIBottomTabView(activity);
        final MainTabUI.TabsAdapter adapter=new MainTabUI.TabsAdapter();
        final FrostedContentView frosted=new FrostedContentView(activity);
        Home(boolean ready){
            branch.layout(0,0,1080,2400);((ViewGroup)activity.getWindow().getDecorView()).addView(branch);
            pager.layout(0,0,1080,2320);branch.addView(pager);
            nativeBar.layout(0,2200,1080,2320);branch.addView(nativeBar);
            frosted.layout(0,0,1080,2400);pager.addView(frosted);
            if(ready)pager.adapter=adapter;
        }
        NavigationBarViews.NavigationBarView bar(){return (NavigationBarViews.NavigationBarView)branch.findViewWithTag("mdl_nav_bar_v2");}
        void resume(){activity.onResume();Handler.drainForTest();}
        void draw(){NavigationBarViews.NavigationBarView bar=bar();bar.layout(0,2200,1080,2400);bar.getViewTreeObserver().drawForTest();}
        void close(){activity.onDestroy();Handler.drainForTest();}
    }
    public static void main(String[] args)throws Throwable{
        XposedBridge.failMethod="onPageScrolled";
        XC_LoadPackage.LoadPackageParam load=new XC_LoadPackage.LoadPackageParam();load.classLoader=NavigationHookChecks.class.getClassLoader();
        NavigationBarFeature.init(load);
        check(hooked(MainTabUI.TabsAdapter.class,"onPageSelected"),"failed optional scrolled hook does not block selected");
        check(hooked(MainTabUI.TabsAdapter.class,"onPageScrollStateChanged"),"state hook survives previous failure");
        check(hooked(WxViewPager.class,"setCurrentItemNotify"),"missing setCurrentItem does not block notify");
        check(hooked(com.tencent.mm.ui.conversation.ConversationListView.class,"onScrollStateChanged"),"missing list onScroll does not block state");
        Method scrolled=MainTabUI.TabsAdapter.class.getDeclaredMethod("onPageScrolled",int.class,float.class,int.class);
        java.lang.reflect.Field hooksField=NavigationBarFeature.class.getDeclaredField("HOOKS");hooksField.setAccessible(true);
        check(!((java.util.Set<?>)hooksField.get(null)).contains(scrolled),"failed hook never marked installed");
        XposedBridge.failMethod=null;
        Method hook=NavigationBarFeature.class.getDeclaredMethod("hook",Method.class,XC_MethodHook.class);hook.setAccessible(true);
        hook.invoke(null,scrolled,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable()) NavigationBarViews.scrolled(param.thisObject,(Integer)param.args[0],(Float)param.args[1]);
            }
        });hook.invoke(null,scrolled,new XC_MethodHook(){});
        check(XposedBridge.hooks.get(scrolled).size()==1,"failed hook can retry and successful retry deduplicates");

        Home home=new Home(false);
        home.activity.afterSuperResume=()->{
            check(!NavigationBarFeature.resumed(home.activity),"base Activity after-hook is not outer LauncherUI completion");
            check(home.bar()==null&&home.nativeBar.getVisibility()==View.VISIBLE,"no takeover inside super.onResume");
        };
        home.activity.onResume();
        check(home.activity.completedResume&&!NavigationBarFeature.resumed(home.activity),"resume is queued after complete override");
        Handler.drainForTest();
        check(NavigationBarFeature.resumed(home.activity),"posted resume sees completed host");
        Handler.nextFrameForTest();
        check(home.bar()==null&&home.nativeBar.getVisibility()==View.VISIBLE,"null adapter retains native");
        check(Handler.frameCountForTest()==1,"exactly one retry queued");
        for(int i=0;i<5;i++)NavigationBarFeature.refresh();
        check(Handler.frameCountForTest()==1,"refresh/attach triggers coalesce");
        Handler.nextFrameForTest();home.pager.adapter=home.adapter;home.pager.current=2;Handler.nextFrameForTest();
        check(home.bar()!=null,"late adapter installs without leaving home");
        check(home.bar().lifecycleStarted&&home.bar().selected==2,"late Session inherits current lifecycle and pager state");
        check(home.bar().getVisibility()==View.VISIBLE,"cold Session is not INVISIBLE");
        check(home.nativeBar.getVisibility()==View.VISIBLE&&home.frosted.bottom==120,"native kept until visible overlay layout");
        home.draw();
        check(home.nativeBar.getVisibility()==View.GONE&&home.bar().isShown(),"first visible laid-out frame takes over");
        check(home.frosted.bottom==0,"production Session owns frosted only after native takeover");
        NavigationBarViews.NavigationBarView bar=home.bar();int additions=home.branch.additions;
        NavigationBarViews.install(home.nativeBar,home.pager,home.adapter);
        NavigationBarFeature.refresh();Handler.nextFrameForTest();
        check(home.bar()==bar&&home.branch.additions==additions,"repeated install has one Session and overlay");

        navigationGeometry(home, bar);

        home.pager.current=1;bar.pageScrolled(0,0.35f);home.draw();
        check(bar.selected==2&&bar.motion.target()==0.35f,"fractional callback/preDraw does not read back potentially stale current item");
        bar.pageSelected(1);home.draw();check(bar.selected==1&&bar.motion.target()==0.35f,"selected updates semantic state but does not restart fractional animation");
        bar.pageScrollState(0);check(bar.motion.target()==1f,"idle settles to actual selected page");
        home.pager.current=3;home.draw();check(bar.selected==3&&bar.motion.target()==3f,"idle missing selected callback recovers from actual pager");
        home.pager.current=2;XposedBridge.invoke(home.adapter,"onPageSelected",new Class<?>[]{int.class},2);
        check(bar.selected==2,"real optional selected callback dispatches to Session");
        NavigationBarFeature.fromTabTap(()->{try{XposedBridge.invoke(home.pager,"setCurrentItemNotify",new Class<?>[]{int.class,boolean.class},1,false);}catch(Throwable error){throw new AssertionError(error);}});
        check(home.pager.smooth,"remaining optional pager hook preserves animated native tap");
        for (int destination : new int[]{2,1}) {
            home.pager.current=3;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(3);
            final int target=destination;
            home.nativeBar.getChildAt(target).setOnClickListener(view->{
                bar.pageScrollState(2);
                bar.pageSelected(target);
                // Host setTo(3) styles the old offset-zero frame after selection was announced.
                NavigationBarViews.nativeSelected(home.nativeBar,3);
                Handler.drainForTest();
                check(bar.selected==target,"3->"+target+" intermediate native setTo cannot revert selection");
                bar.pageScrolled(2,0.75f);
                home.draw();
                check(bar.selected==target&&bar.motion.target()==2.75f,"3->"+target+" scrolling ignores old getter and retains fractional target");
                home.pager.current=target;
            });
            check(bar.targets.get(target).performClick(),"production target invokes native click");
            bar.dispatchTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_UP,0,0,0));
            check(bar.selected==target&&bar.motion.target()==2.75f,"3->"+target+" ACTION_UP never bounces motion to old Me selection");
            bar.pageScrolled(target,0);bar.pageScrollState(0);Handler.drainForTest();home.draw();
            check(bar.selected==target&&bar.motion.target()==target,"3->"+target+" completed native pager stays selected");
            // No selected/scrolled callbacks at all: synchronous native click must still read back.
            home.pager.current=3;bar.syncSelected();bar.motion.snap(3);
            home.nativeBar.getChildAt(target).setOnClickListener(view->home.pager.current=target);
            bar.targets.get(target).performClick();
            bar.dispatchTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_UP,0,0,0));
            check(bar.selected==target&&bar.motion.target()==target,"3->"+target+" missing callbacks repaired before ACTION_UP");
            Handler.drainForTest();
        }

        for(String name:new String[]{"a","setBottomBlurAreaHeight","dispatchDraw"})check(hooked(FrostedContentView.class,name),"frosted hook registered "+name);
        XposedBridge.invoke(home.frosted,"a",new Class<?>[]{boolean.class,int.class,float.class},true,210,10f);
        check(home.frosted.bottom==0,"real config callback suppresses final direct field result");
        home.activity.onPause();check(!bar.lifecycleStarted&&bar.getVisibility()==View.INVISIBLE&&home.frosted.bottom==200,"pause hides overlay and restores frosted");
        home.activity.afterSuperResume=null;home.resume();
        check(bar.lifecycleStarted&&bar.getVisibility()==View.VISIBLE,"posted resume repairs existing hidden state immediately");
        XposedBridge.invoke(home.frosted,"setBottomBlurAreaHeight",new Class<?>[]{int.class},225);
        check(home.frosted.bottom==0,"setter callback reaches production Session");
        home.activity.onPause();check(home.frosted.bottom==225,"latest native baseline restored");
        home.resume();home.frosted.bottom=230;
        XposedBridge.invoke(home.frosted,"dispatchDraw",new Class<?>[]{Canvas.class},new Canvas());
        check(home.frosted.drawnBottom==0,"draw callback runs before host draw");
        home.activity.onPause();check(home.frosted.bottom==230,"draw fallback baseline retained");
        home.resume();home.frosted.failConfiguration=true;
        try{XposedBridge.invoke(home.frosted,"a",new Class<?>[]{boolean.class,int.class,float.class},true,260,0f);throw new AssertionError("host exception swallowed");}
        catch(IllegalStateException expected){check(home.frosted.bottom==260,"failed config unchanged until draw");}
        home.frosted.failConfiguration=false;XposedBridge.invoke(home.frosted,"dispatchDraw",new Class<?>[]{Canvas.class},new Canvas());
        home.close();check(home.frosted.bottom==260&&home.nativeBar.getVisibility()==View.VISIBLE&&home.bar()==null,"destroy restores native and removes real overlay");

        Home paused=new Home(true);
        NavigationBarViews.install(paused.nativeBar,paused.pager,paused.adapter);
        check(!paused.bar().lifecycleStarted&&paused.bar().getVisibility()==View.INVISIBLE,"Session created before resume gets false lifecycle, not default true");
        paused.draw();check(paused.nativeBar.getVisibility()==View.VISIBLE,"inactive early Session cannot hide native");
        paused.resume();paused.draw();check(paused.bar().lifecycleStarted&&paused.bar().isShown(),"early Session recovers on first foreground without external page roundtrip");
        paused.close();

        Home hidden=new Home(true);hidden.pager.setVisibility(View.INVISIBLE);hidden.resume();Handler.nextFrameForTest();hidden.draw();
        check(hidden.nativeBar.getVisibility()==View.VISIBLE&&hidden.bar().getVisibility()==View.INVISIBLE,"temporarily hidden host keeps native before overlay ready");
        hidden.pager.setVisibility(View.VISIBLE);hidden.draw();check(hidden.bar().isShown()&&hidden.nativeBar.getVisibility()==View.GONE,"preDraw repairs temporary suspended state");hidden.close();

        Home cancel=new Home(false);cancel.activity.onResume();cancel.activity.onPause();Handler.drainForTest();Handler.nextFrameForTest();
        check(!NavigationBarFeature.resumed(cancel.activity)&&cancel.bar()==null&&Handler.frameCountForTest()==0,"pause cancels queued resume before execution");
        cancel.resume();Handler.nextFrameForTest();int reads=cancel.pager.reads;cancel.activity.onPause();cancel.pager.adapter=cancel.adapter;Handler.nextFrameForTest();
        check(cancel.pager.reads==reads&&cancel.bar()==null&&Handler.frameCountForTest()==0,"pause cancels outstanding attach");cancel.close();
        Home destroy=new Home(false);destroy.resume();Handler.nextFrameForTest();destroy.close();Handler.nextFrameForTest();
        check(destroy.bar()==null&&Handler.frameCountForTest()==0,"destroy cancels retry");
        Home detach=new Home(false);detach.resume();Handler.nextFrameForTest();detach.branch.removeView(detach.nativeBar);Handler.drainForTest();
        check(Handler.frameCountForTest()==0,"native detach cancels retry");detach.close();
        Home bounded=new Home(false);bounded.resume();for(int i=0;i<130;i++)Handler.nextFrameForTest();
        check(bounded.pager.reads==120&&Handler.frameCountForTest()==0&&bounded.nativeBar.getVisibility()==View.VISIBLE,"retry exhaustion is finite and retains native");bounded.close();
        Home disabled=new Home(false);disabled.resume();Handler.nextFrameForTest();wex.ui.core.WexStore.INSTANCE.enabled=false;NavigationBarFeature.refresh();
        check(Handler.frameCountForTest()==0,"disable cancels retry");disabled.close();wex.ui.core.WexStore.INSTANCE.enabled=true;
        idleScrollReconciliation();
        distantTabTransitions();
        bottomBarRepairs();
        NavigationContactsChecks.run();
        contactDiagnosticBudget();
        System.out.println("PASS: "+checks+" production navigation hook/session/lifecycle/first-frame/selection/cancellation assertions");
    }
    private static void idleScrollReconciliation() throws Throwable {
        Home home=new Home(true);home.resume();Handler.nextFrameForTest();home.draw();
        NavigationBarViews.NavigationBarView bar=home.bar();
        java.util.List<String> failures=new java.util.ArrayList<>();
        for(int destination:new int[]{2,1,0,3}) {
            int old=(destination+1)%4;
            home.pager.current=old;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(old);
            // A selected callback can precede an old offset-zero sample; the state hook is optional.
            // The getter is intentionally stale until the callback stack has returned.
            bar.pageSelected(destination);bar.motion.step(1f/60f);
            float before=bar.motion.position();
            bar.pageScrolled(old,0f);
            probe(bar.motion.target()==destination&&bar.motion.position()==before,
                    "idle old whole-page sample never retargets or snaps confirmed selection "+old+"->"+destination,failures);
            home.pager.current=destination;Handler.drainForTest();
            probe(bar.selected==destination&&bar.motion.target()==destination,
                    "deferred idle reconciliation keeps destination even when semantic selected is unchanged",failures);
            for(int i=0;i<180;i++)bar.motion.step(1f/60f);
            probe(Math.abs(bar.motion.position()-destination)<0.001f&&bar.motion.pressProgress()<0.001f,
                    "real spring settles and releases after idle whole-page sample",failures);
        }
        // A fractional/gesture target can also outlive its pager transition without changing selected.
        bar.motion.snap(3);bar.motion.target(0f);bar.motion.step(1f/60f);
        float before=bar.motion.position();bar.syncSelected();
        probe(bar.selected==3&&bar.motion.target()==3f&&bar.motion.position()==before,
                "same semantic selection reconciles mismatched motion target without snapping",failures);
        for(int i=0;i<180;i++)bar.motion.step(1f/60f);
        Handler.drainForTest();Handler.nextFrameForTest();
        boolean redundantFrames=false;
        for(int i=0;i<8;i++) {
            bar.pageScrolled(0,0f);Handler.drainForTest();
            redundantFrames|=Handler.frameCountForTest()!=0;Handler.nextFrameForTest();
        }
        probe(bar.motion.target()==3f&&bar.motion.position()==3f&&bar.motion.pressProgress()==0f,
                "repeated idle samples cannot restart movement or press",failures);
        probe(!redundantFrames&&Handler.frameCountForTest()==0,
                "stable idle samples never queue redundant animation frames",failures);
        // With no selected/state callbacks, wait for the host getter after the sample's stack unwinds.
        home.pager.current=3;bar.motion.snap(3);bar.pageScrolled(1,0f);
        probe(bar.selected==3&&bar.motion.target()==3f,"whole-page sample alone does not guess a selection",failures);
        home.pager.current=1;Handler.drainForTest();
        probe(bar.selected==1&&bar.motion.target()==1f,"missing selected callback reconciles actual getter on next queue turn",failures);
        // Fractional motion remains authoritative until an idle boundary, including gesture cancellation.
        bar.motion.snap(1);bar.pageScrolled(0,0.4f);bar.motion.step(1f/60f);bar.syncSelected();
        probe(bar.selected==1&&bar.motion.target()==0.4f,"fractional samples retain target and reject stale idle getter",failures);
        bar.pageScrollState(1);bar.pageScrolled(1,0f);Handler.drainForTest();
        probe(bar.motion.target()==1f,"moving whole-page boundary remains a real pager sample",failures);
        bar.pageScrollState(0);Handler.drainForTest();home.close();
        if(!failures.isEmpty())throw new AssertionError("idle navigation RED: "+failures);
    }
    private static void nativeEvent(Object owner,String method,Class<?>[] types,Object... args) {
        try { XposedBridge.invoke(owner,method,types,args); }
        catch(RuntimeException error) { throw error; }
        catch(Throwable error) { throw new AssertionError(error); }
    }
    private static void distantTabTransitions() throws Throwable {
        Home home=new Home(true);home.resume();Handler.nextFrameForTest();home.draw();
        NavigationBarViews.NavigationBarView bar=home.bar();
        java.util.List<String> failures=new java.util.ArrayList<>();
        Class<?>[] page={int.class}, sample={int.class,float.class,int.class}, request={int.class,boolean.class};
        // Static WxViewPager path: actual native listener -> hooked request -> populate getter -> state/selected.
        // These are deterministic host-path replays, NOT an observed PID25927 callback timeline.
        for(boolean stateHook:new boolean[]{true,false}) for(int[] route:new int[][]{{0,2},{2,0},{0,3},{3,0},{3,1}}) {
            int origin=route[0],destination=route[1];
            home.pager.current=origin;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(origin);
            home.pager.duringSwitch=()->{
                if(stateHook)nativeEvent(home.adapter,"onPageScrollStateChanged",page,2);
                nativeEvent(home.adapter,"onPageSelected",page,destination);
            };
            home.nativeBar.getChildAt(destination).setOnClickListener(view->
                    nativeEvent(home.pager,"setCurrentItemNotify",request,destination,false));
            bar.targets.get(destination).performClick();
            probe(home.pager.smooth&&bar.selected==destination,"native remote tap confirms getter/selected "+origin+"->"+destination,failures);
            probe(bar.motion.target()==destination&&bar.motion.position()==origin,
                    "confirmed remote tap targets endpoint without snap state="+stateHook,failures);
            if(!stateHook) {
                nativeEvent(home.nativeBar,"setTo",page,origin);
                nativeEvent(home.adapter,"onPageScrolled",sample,origin,0f,0);
                home.pager.current=origin;Handler.drainForTest();home.draw();home.pager.current=destination;
                probe(bar.selected==destination&&bar.motion.target()==destination,
                        "old whole-page before first fraction cannot undo confirmed remote goal",failures);
            }
            float previous=bar.motion.position();boolean monotonic=true,endpoint=true;
            int direction=destination>origin?1:-1;
            for(float progress:new float[]{0.25f,0.75f,1f,1.15f,1.75f,Math.abs(destination-origin)}) {
                float logical=origin+direction*Math.min(progress,Math.abs(destination-origin));
                int position=(int)Math.floor(logical);float offset=logical-position;
                if(offset==0f)nativeEvent(home.nativeBar,"setTo",page,position);
                nativeEvent(home.adapter,"onPageScrolled",sample,position,offset,0);
                int confirmedCurrent=home.pager.current;
                // A delayed getter at an intermediate whole-page boundary is not an idle acknowledgement.
                if(!stateHook&&logical!=destination&&offset==0f)home.pager.current=origin;
                int observedCurrent=home.pager.current;
                Handler.drainForTest();home.draw();home.pager.current=confirmedCurrent;
                endpoint&=bar.motion.target()==destination;
                for(int frame=0;frame<6;frame++) {
                    bar.motion.step(1f/60f);float next=bar.motion.position();
                    monotonic&=direction*(next-previous)>=-0.0001f;previous=next;
                }
                System.out.println("DIAG replay route="+origin+"->"+destination+" state="+(stateHook?2:-1)
                        +" sample="+logical+" current="+observedCurrent+" selected="+bar.selected
                        +" target="+bar.motion.target()+" position="+bar.motion.position());
            }
            probe(endpoint,"remote integer/fraction samples never retarget through Contacts state="+stateHook,failures);
            probe(monotonic,"real remote spring never reverses at intermediate page state="+stateHook,failures);
            nativeEvent(home.adapter,"onPageScrollStateChanged",page,0);Handler.drainForTest();
            for(int i=0;i<180;i++)bar.motion.step(1f/60f);
            probe(bar.motion.position()==destination&&bar.motion.pressProgress()==0f,
                    "remote idle settles/releases real spring at authoritative getter",failures);
        }
        home.pager.duringSwitch=null;
        home.pager.current=0;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(0);
        // A true performClick is not an acknowledgement that the host accepted a switch.
        home.nativeBar.getChildAt(2).setOnClickListener(view->{});bar.targets.get(2).performClick();Handler.drainForTest();
        probe(bar.selected==0&&bar.motion.target()==0f,"listener no-op never confirms requested destination",failures);
        home.pager.ignoreSwitch=true;home.nativeBar.getChildAt(2).setOnClickListener(view->nativeEvent(home.pager,"setCurrentItemNotify",request,2,false));
        bar.targets.get(2).performClick();Handler.drainForTest();
        probe(bar.selected==0&&bar.motion.target()==0f,"native request rejected by getter never locks endpoint",failures);home.pager.ignoreSwitch=false;
        home.pager.failSwitch=true;bar.targets.get(2).performClick();Handler.drainForTest();
        probe(bar.selected==0&&bar.motion.target()==0f,"failed native request never locks endpoint",failures);home.pager.failSwitch=false;
        home.pager.failGetter=true;home.pager.ignoreSwitch=true;bar.targets.get(2).performClick();Handler.drainForTest();
        probe(bar.selected==0&&bar.motion.target()==0f,"unavailable getter with no selected confirmation never locks endpoint",failures);
        home.pager.failGetter=false;home.pager.ignoreSwitch=false;
        // No selected/state/scrolled at all: the getter after the native request is still authoritative.
        bar.targets.get(2).performClick();Handler.drainForTest();
        probe(bar.selected==2&&bar.motion.target()==2f&&bar.motion.position()==0f,
                "native getter-only remote confirmation animates without snap",failures);
        // A new native request/gesture may supersede the old remote goal; never infer it from page samples.
        home.pager.current=0;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(0);
        home.pager.duringSwitch=()->{nativeEvent(home.adapter,"onPageScrollStateChanged",page,2);nativeEvent(home.adapter,"onPageSelected",page,2);};
        bar.targets.get(2).performClick();
        nativeEvent(home.adapter,"onPageScrollStateChanged",page,1);
        nativeEvent(home.adapter,"onPageScrolled",sample,1,0.4f,0);home.draw();
        probe(bar.motion.target()==1.4f,"user pager drag takes fractional authority back from remote tap",failures);
        home.pager.current=0;nativeEvent(home.adapter,"onPageSelected",page,0);
        nativeEvent(home.adapter,"onPageScrollStateChanged",page,0);Handler.drainForTest();
        probe(bar.selected==0&&bar.motion.target()==0f,"cancelled pager gesture returns to actual getter",failures);
        // Without a state hook, a confirmed destination's final whole-page sample ends tap authority.
        home.pager.current=0;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(0);
        home.pager.duringSwitch=()->nativeEvent(home.adapter,"onPageSelected",page,2);
        bar.targets.get(2).performClick();
        nativeEvent(home.adapter,"onPageScrolled",sample,2,0f,0);Handler.drainForTest();
        nativeEvent(home.adapter,"onPageScrolled",sample,1,0.6f,0);home.draw();
        probe(bar.motion.target()==1.6f,"destination sample releases tap authority without optional state hook",failures);
        bar.pageScrollState(0);
        // A false smooth flag is still a real new switch, not an intermediate page sample.
        home.pager.current=0;bar.syncSelected();bar.motion.snap(0);
        home.pager.duringSwitch=()->{nativeEvent(home.adapter,"onPageScrollStateChanged",page,2);nativeEvent(home.adapter,"onPageSelected",page,2);};
        bar.targets.get(2).performClick();home.pager.duringSwitch=null;
        nativeEvent(home.pager,"setCurrentItemNotify",request,1,false);
        nativeEvent(home.adapter,"onPageSelected",page,1);
        nativeEvent(home.adapter,"onPageScrolled",sample,0,0.7f,0);home.draw();
        probe(!home.pager.smooth&&bar.selected==1&&bar.motion.target()==0.7f,
                "actual non-tap native false request supersedes old endpoint and preserves fractional authority",failures);
        nativeEvent(home.adapter,"onPageScrollStateChanged",page,0);
        probe(bar.motion.target()==1f,"redirect idle settles to actual new native getter",failures);
        // A selected acknowledgement is sufficient even when the optional getter is unavailable.
        home.pager.current=0;bar.syncSelected();bar.motion.snap(0);home.pager.failGetter=true;
        home.pager.duringSwitch=()->nativeEvent(home.adapter,"onPageSelected",page,2);
        bar.targets.get(2).performClick();nativeEvent(home.adapter,"onPageScrolled",sample,1,0.2f,0);
        probe(bar.selected==2&&bar.motion.target()==2f,"native selected confirms remote goal without getter",failures);
        home.pager.failGetter=false;bar.pageScrollState(0);home.pager.duringSwitch=null;
        // Selected body can synchronously publish HomeUITabChangeEvent and reenter a newer switch.
        // The outer after-hook is then stale even though its host method completed successfully.
        home.pager.current=0;bar.pageScrollState(0);bar.syncSelected();bar.motion.snap(0);
        home.pager.duringSwitch=()->{
            nativeEvent(home.adapter,"onPageScrollStateChanged",page,2);
            nativeEvent(home.adapter,"onPageSelected",page,home.pager.current);
        };
        home.nativeBar.getChildAt(1).setOnClickListener(view->nativeEvent(home.pager,"setCurrentItemNotify",request,1,false));
        home.adapter.duringSelected=()->{
            home.adapter.duringSelected=null;
            bar.targets.get(1).performClick();
        };
        bar.targets.get(2).performClick();
        probe(home.pager.current==1&&bar.selected==1&&bar.motion.target()==1f,
                "reentrant selected body newer native tap wins over outer selected after-hook",failures);
        nativeEvent(home.adapter,"onPageScrolled",sample,0,0.5f,0);Handler.drainForTest();home.draw();
        probe(bar.selected==1&&bar.motion.target()==1f,
                "reentrant request preserves newest confirmed goal through fractional samples",failures);
        bar.pageScrollState(0);home.pager.duringSwitch=null;
        home.pager.current=0;bar.syncSelected();bar.motion.snap(0);
        home.pager.duringSwitch=()->{
            nativeEvent(home.adapter,"onPageScrollStateChanged",page,2);
            nativeEvent(home.adapter,"onPageSelected",page,home.pager.current);
        };
        home.nativeBar.getChildAt(3).setOnClickListener(view->nativeEvent(home.pager,"setCurrentItemNotify",request,3,false));
        home.adapter.duringSelected=()->{
            home.adapter.duringSelected=null;home.pager.failSwitch=true;
            try { bar.targets.get(3).performClick(); } finally { home.pager.failSwitch=false; }
        };
        bar.targets.get(2).performClick();
        nativeEvent(home.adapter,"onPageScrolled",sample,1,0.1f,0);
        probe(home.pager.current==2&&bar.selected==2&&bar.motion.target()==2f,
                "failed nested request preserves outer goal only with authoritative native getter",failures);
        // Same-index inner failure must not consume an outer successful setter's acknowledgement.
        bar.pageScrollState(0);home.pager.current=0;bar.syncSelected();bar.motion.snap(0);
        home.adapter.duringSelected=()->{
            home.adapter.duringSelected=null;home.pager.failSwitch=true;
            try { bar.targets.get(2).performClick(); } finally { home.pager.failSwitch=false; }
        };
        bar.targets.get(2).performClick();nativeEvent(home.adapter,"onPageScrolled",sample,1,0.3f,0);
        probe(home.pager.current==2&&bar.selected==2&&bar.motion.target()==2f,
                "same-index failed inner setter does not consume successful outer request token",failures);
        bar.pageScrollState(0);home.pager.current=0;bar.syncSelected();bar.motion.snap(0);
        home.adapter.duringSelected=()->{
            home.adapter.duringSelected=null;home.pager.failSwitch=true;
            home.pager.current=0; // Native getter no longer confirms the outer destination.
            try { bar.targets.get(3).performClick(); } finally { home.pager.failSwitch=false; }
        };
        bar.targets.get(2).performClick();nativeEvent(home.adapter,"onPageScrolled",sample,0,0.4f,0);
        probe(bar.motion.target()==0.4f,"failed nested request cannot resurrect unconfirmed outer destination",failures);
        bar.pageScrollState(0);home.pager.duringSwitch=null;
        // Real overlay pointer preview remains fractional, and CANCEL returns without native activation.
        home.pager.current=0;bar.syncSelected();home.draw();
        float x=bar.targets.get(0).getLeft()+bar.targets.get(0).getWidth()/2f,y=bar.surface.centerY();
        bar.dispatchTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,x,y,0));
        bar.dispatchTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_MOVE,x+bar.targets.get(0).getWidth()*1.35f,y,0));
        probe(Math.abs(bar.motion.target()-1.35f)<0.001f,"overlay gesture follows fractional touch preview",failures);
        bar.dispatchTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_CANCEL,x,y,0));
        probe(bar.motion.target()==0f&&home.pager.current==0,"overlay CANCEL restores getter without activating preview",failures);
        home.close();
        if(!failures.isEmpty())throw new AssertionError("remote navigation RED: "+failures);
    }
    private static void contactDiagnosticBudget() throws Throwable {
        NavigationContactViewport diagnostic=new NavigationContactViewport();
        java.lang.reflect.Field attempts=NavigationContactViewport.class.getDeclaredField("diagnosticAttempts");attempts.setAccessible(true);
        java.lang.reflect.Field count=NavigationContactViewport.class.getDeclaredField("diagnosticCount");count.setAccessible(true);
        java.lang.reflect.Field time=NavigationContactViewport.class.getDeclaredField("lastDiagnosticTime");time.setAccessible(true);
        diagnostic.diagnose("missing",null,null,0);
        for(int i=0;i<100;i++)diagnostic.diagnose("missing",null,null,0);
        check(attempts.getInt(diagnostic)==1,"contact diagnostic throttles immediate predraw repetitions");
        for(int i=0;i<60;i++){time.setLong(diagnostic,0);diagnostic.diagnose("missing",null,null,0);}
        check(count.getInt(diagnostic)==1&&attempts.getInt(diagnostic)==48,"unchanged contact diagnostic text deduplicates and stops after bounded inspections");
        diagnostic=new NavigationContactViewport();
        for(int i=0;i<30;i++){time.setLong(diagnostic,0);diagnostic.diagnose("event-"+i,null,null,0);}
        check(count.getInt(diagnostic)==12,"changing contact diagnostics have a twelve-event output budget");
        NavigationContactViewport.currentList(null,null,diagnostic,0);
        check(count.getInt(diagnostic)==12,"missing page never replenishes diagnostic budget");
    }
    private static void bottomBarRepairs() throws Throwable {
        Home home=new Home(true);home.resume();Handler.nextFrameForTest();home.draw();
        NavigationBarViews.NavigationBarView bar=home.bar();
        java.util.List<String> failures=new java.util.ArrayList<>();
        home.nativeBar.unread[0]=7;bar.refreshBadges();
        int parent=bar.invalidations;
        home.nativeBar.unread[0]=0;bar.refreshBadges();
        probe(bar.counts[0]==0&&bar.invalidations>parent,"N->0 invalidates parent combination",failures);
        parent=bar.invalidations;int child=bar.targets.get(0).invalidations;
        bar.refreshBadges();probe(parent==bar.invalidations&&child==bar.targets.get(0).invalidations,"unchanged badges do not redraw",failures);
        home.nativeBar.failFriendUnread=true;home.nativeBar.friendPoint=true;
        bar.refreshBadges();probe(bar.counts[2]==-1&&bar.dots[2],"point survives failed unread getter",failures);
        home.nativeBar.failFriendUnread=false;home.nativeBar.failSettingsPoint=true;home.nativeBar.unread[3]=4;
        bar.refreshBadges();probe(bar.counts[3]==4&&!bar.dots[3],"unread survives failed point getter",failures);
        home.nativeBar.failSettingsPoint=false;
        home.nativeBar.unread[0]=9;bar.refreshBadges();home.nativeBar.unread[0]=0;
        HostTargetResolver.complete("nav_main_unread",LauncherUIBottomTabView.class.getDeclaredMethod("updateMainUnread",int.class));
        Handler.drainForTest();probe(bar.counts[0]==0,"late hook reconciles pre-registration clear",failures);
        XposedBridge.invoke(home.nativeBar,"updateMainUnread",new Class<?>[]{int.class},5);
        Handler.drainForTest();probe(bar.counts[0]==5,"host unread event updates real value",failures);
        home.pager.setVisibility(View.INVISIBLE);home.draw();home.nativeBar.unread[0]=0;
        home.pager.setVisibility(View.VISIBLE);home.draw();Handler.drainForTest();
        probe(bar.counts[0]==0,"same Activity visibility recovery clears badge",failures);
        home.nativeBar.unread[0]=6;home.pager.current=1;bar.pageSelected(1);Handler.drainForTest();
        probe(bar.counts[0]==6,"confirmed page selection reconciles without fake clearing",failures);
        int reads=home.nativeBar.badgeReads;for(int i=0;i<4;i++)home.draw();Handler.drainForTest();
        probe(home.nativeBar.badgeReads==reads,"steady predraw never polls badge getters",failures);
        XposedBridge.invoke(home.nativeBar,"updateMainUnread",new Class<?>[]{int.class},8);
        XposedBridge.invoke(home.nativeBar,"updateMainUnread",new Class<?>[]{int.class},0);
        Handler.drainForTest();probe(bar.counts[0]==0&&home.nativeBar.badgeReads==reads+4,"burst events coalesce one true badge read",failures);
        home.nativeBar.unread[0]=23;bar.refreshBadges();
        Canvas badgeFrame=new Canvas();badgeFrame.hardware=true;bar.draw(badgeFrame);
        probe(bar.combined.last.texts.contains("23"),"actual combined recording contains current badge",failures);
        home.nativeBar.unread[0]=0;bar.refreshBadges();bar.draw(badgeFrame);
        probe(!bar.combined.last.texts.contains("23"),"parent redraw removes stale badge from actual combined recording",failures);
        bar.motion.snap(1);bar.motion.animateTo(2);bar.motion.press();bar.motion.step(0.05f);
        Canvas hardware=new Canvas();hardware.hardware=true;bar.draw(hardware);
        probe(bar.decoration.touches==0&&bar.decoration.highlights>0,"remove wide touch light preserve edge highlight",failures);
        Canvas software=new Canvas();bar.draw(software);
        probe(software.pathColors.contains(0xFF07C160),"software fallback draws selected accent",failures);
        bar.indicatorGlass.indicatorReady=false;hardware=new Canvas();hardware.hardware=true;bar.draw(hardware);
        probe(hardware.pathColors.contains(0xFF07C160),"indicator failure draws selected accent",failures);
        bar.indicatorGlass.indicatorReady=true;hardware=new Canvas();hardware.hardware=true;hardware.failNodeDraw=true;
        bar.draw(hardware);probe(hardware.pathColors.contains(0xFF07C160),"indicator node draw failure selected fallback",failures);
        bar.combined.failRecording=true;hardware=new Canvas();hardware.hardware=true;
        try { bar.draw(hardware);probe(hardware.pathColors.contains(0xFF07C160),"combined recording failure selected fallback",failures); }
        catch (RuntimeException error) { probe(false,"combined recording failure selected fallback",failures); }
        bar.combined.failRecording=false;bar.motion.snap(0);home.pager.current=0;bar.pageSelected(0);Handler.drainForTest();
        com.tencent.mm.ui.conversation.ConversationListView list=new com.tencent.mm.ui.conversation.ConversationListView(home.activity);
        home.pager.addView(list);View first=new View(home.activity);first.layout(0,-30,100,30);list.displayNativeRow(first);
        wex.ui.core.WexStore.INSTANCE.autoHide=false;NavigationBarFeature.refresh();Handler.drainForTest();
        int revisions=bar.sampler.contentChanges;
        Class<?>[] signature={android.widget.AbsListView.class,int.class,int.class,int.class};
        XposedBridge.invoke(list,"onScroll",signature,list,3,1,20);
        first.layout(0,0,100,60);XposedBridge.invoke(list,"onScroll",signature,list,0,1,20);
        probe(bar.sampler.contentChanges==revisions+2,"actual list top notifications independent of autoHide",failures);
        revisions=bar.sampler.contentChanges;XposedBridge.invoke(list,"onScroll",signature,list,0,1,20);
        probe(bar.sampler.contentChanges==revisions,"identical list callback deduplicated",failures);
        bar.sampler.capturing=true;first.layout(0,-8,100,52);XposedBridge.invoke(list,"onScroll",signature,list,0,1,20);bar.sampler.capturing=false;
        probe(bar.sampler.contentChanges==revisions,"capture self callback suppressed",failures);
        XposedBridge.invoke(list,"onScroll",signature,list,0,1,20);
        probe(bar.sampler.contentChanges==revisions+1,"suppressed callback does not poison real revision",failures);
        list.clearNativeRows();XposedBridge.invoke(list,"onScroll",signature,list,0,0,0);
        probe(bar.sampler.contentChanges==revisions+2,"empty transition notifies real content",failures);
        XposedBridge.invoke(list,"onScroll",signature,list,0,0,0);
        probe(bar.sampler.contentChanges==revisions+2,"empty callback deduplicated",failures);
        home.close();wex.ui.core.WexStore.INSTANCE.autoHide=true;
        if(!failures.isEmpty())throw new AssertionError("bottom-bar RED: "+failures);
    }
    private static void probe(boolean value,String message,java.util.List<String> failures) {
        checks++;System.out.println((value?"PASS: ":"RED: ")+message);if(!value)failures.add(message);
    }
    private static void navigationGeometry(Home home, NavigationBarViews.NavigationBarView bar) {
        View root = home.branch.getRootView();
        float originalDensity = home.activity.getResources().getDisplayMetrics().density;
        home.activity.getResources().getDisplayMetrics().density=1f; bar.reload();
        root.rootInsets = new android.view.WindowInsets(android.graphics.Insets.of(0,0,0,80),
                android.graphics.Insets.of(0,0,0,80), android.graphics.Insets.of(0,0,0,0));
        bar.measure(1080,2400);
        check(bar.measuredHeight==84,"measure uses fixed 64dp capsule + 20dp before real post-layout nav cache");
        bar.onPreDraw(); bar.measure(1080,2400);
        check(bar.measuredHeight==164,"actual full-height parent needs nav-only 80px extra");
        int requests=bar.layoutRequests;
        for(int i=0;i<8;i++){bar.onPreDraw();bar.measure(1080,2400);}
        check(bar.measuredHeight==164&&bar.layoutRequests==requests,"settled parent never oscillates or requests repeated layout");
        home.branch.setPadding(0,0,0,80);bar.onPreDraw();bar.measure(1080,2400);
        check(bar.measuredHeight==84,"parent padding already consumes navigation bottom exactly once");
        home.branch.setPadding(0,0,0,40);bar.onPreDraw();bar.measure(1080,2400);
        check(bar.measuredHeight==124,"partial parent bottom padding leaves only residual nav");
        home.branch.setPadding(0,0,0,0);home.branch.layout(0,0,1080,2320);bar.onPreDraw();bar.measure(1080,2400);
        check(bar.measuredHeight==84,"parent geometry already above nav needs no duplicate bottom space");
        root.rootInsets = new android.view.WindowInsets(android.graphics.Insets.of(0,0,0,80),
                android.graphics.Insets.of(0,0,0,80), android.graphics.Insets.of(0,0,0,700));
        home.branch.layout(0,0,1080,1700);bar.onPreDraw();bar.measure(1080,1700);
        check(bar.measuredHeight==84,"adjustResize parent never adds IME twice to capsule");
        home.branch.layout(0,0,1080,2400);bar.onPreDraw();bar.measure(1080,2400);
        check(bar.measuredHeight==164,"unshrunk parent uses only nav even when IME is 700");
        float oldScale=bar.px(64);bar.onLayout(true,0,0,1080,bar.measuredHeight);
        check(bar.surface.height()==oldScale,"capsule height stays constant independently of outer nav padding");
        home.activity.getResources().getDisplayMetrics().density=2f;bar.reload();bar.onPreDraw();bar.measure(1080,2400);
        bar.onLayout(true,0,0,1080,bar.measuredHeight);
        check(bar.surface.height()==128f&&bar.measuredHeight==248,"density changes real capsule to 64*d with 20dp outer gap");
        check(bar.sampler.source==home.branch&&bar.sampler.content==home.pager,
                "floating source uses actual parent bounds and native pager content rather than short pager extent");
        int drawn=bar.drawCalls;bar.sampler.capturing=true;bar.draw(new Canvas());bar.sampler.capturing=false;
        check(bar.drawCalls==drawn,"top-level draw skips every module foreground/indicator/child during capture");
        FrameLayout replacement=new FrameLayout(home.activity);replacement.layout(0,0,1080,2320);
        ((ViewGroup)root).addView(replacement);home.branch.removeView(home.pager);replacement.addView(home.pager);
        bar.session.attach();bar.onPreDraw();bar.measure(1080,2320);
        check(bar.measuredHeight==168&&bar.getParent()==replacement&&bar.sampler.source==replacement,
                "parent replacement recomputes residual geometry and rebinds real source");
        replacement.removeView(home.pager);home.branch.addView(home.pager);bar.session.attach();
        ((ViewGroup)root).removeView(replacement);
        home.activity.getResources().getDisplayMetrics().density=1f;home.branch.setPadding(0,0,0,0);
        root.rootInsets=null;bar.reload();bar.onPreDraw();bar.measure(1080,2400);
        check(bar.measuredHeight==84,"insets removal clears cached residual without shared geometry changes");
        home.activity.getResources().getDisplayMetrics().density=originalDensity;bar.reload();
    }
    private static boolean hooked(Class<?> owner,String name){return XposedBridge.hooks.keySet().stream().anyMatch(method->method.getDeclaringClass()==owner&&method.getName().equals(name));}
}
