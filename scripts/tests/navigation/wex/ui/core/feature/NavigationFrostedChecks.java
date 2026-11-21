package wex.ui.core.feature;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout;
import com.tencent.mm.ui.FrostedContentView;
import com.tencent.mm.ui.LauncherUI;

import java.util.ArrayList;
import java.util.List;

/** Runs the production controller against actual test view trees and host setter callbacks. */
public final class NavigationFrostedChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}

    private static final class Home implements NavigationFrostedController.Home {
        final Activity activity;
        final ViewGroup decor;
        final FrameLayout branch,pager,nativeBar,overlay;
        boolean active=true;
        final List<String> logs=new ArrayList<>();
        final NavigationFrostedController controller;
        Home(){this(new LauncherUI());}
        Home(Activity activity){
            this.activity=activity;decor=(ViewGroup)activity.getWindow().getDecorView();decor.layout(0,0,1080,2400);
            branch=new FrameLayout(activity);branch.layout(0,0,1080,2400);decor.addView(branch);
            pager=new FrameLayout(activity);pager.layout(0,0,1080,2320);branch.addView(pager);
            nativeBar=new FrameLayout(activity);nativeBar.layout(0,2210,1080,2320);branch.addView(nativeBar);nativeBar.setVisibility(View.GONE);
            overlay=new FrameLayout(activity);overlay.layout(0,2200,1080,2400);branch.addView(overlay);
            controller=new NavigationFrostedController(this,logs::add);
        }
        public Activity activity(){return activity;}
        public View pager(){return pager;}
        public View nativeBar(){return nativeBar;}
        public View overlay(){return overlay;}
        public boolean active(){return active;}
        FrostedContentView frosted(ViewGroup parent,int height){
            FrostedContentView view=new FrostedContentView(activity);view.layout(0,0,1080,2400);view.bottom=height;parent.addView(view);
            view.afterSet=value->controller.updated(view,value);
            return view;
        }
    }

    private static void untouched(FrostedContentView view){
        check(view.top==48&&view.radius==50&&view.color==0x55444444&&view.enabled,"top/radius/color/enable untouched");
    }

    private static void scopes(){
        Home home=new Home();
        FrostedContentView descendant=home.frosted(home.pager,120);
        FrostedContentView sibling=home.frosted(home.branch,150);
        home.controller.refresh();
        check(descendant.bottom==0&&sibling.bottom==0,"pager descendant and home sibling suppressed");
        untouched(descendant);untouched(sibling);
        check(home.logs.stream().anyMatch(value->value.contains("pager-descendant"))&&home.logs.stream().anyMatch(value->value.contains("home-sibling")),"safe association diagnostics");
        home.controller.release();
        check(descendant.bottom==120&&sibling.bottom==150,"independent baseline restoration");
        check(descendant.writes==2&&sibling.writes==2,"module setter recursion blocked");

        home=new Home();
        home.branch.removeView(home.pager);
        FrostedContentView ancestor=home.frosted(home.branch,180);ancestor.addView(home.pager);
        home.controller.refresh();
        check(ancestor.bottom==0,"pager ancestor suppressed");untouched(ancestor);
        home.controller.release();check(ancestor.bottom==180,"ancestor baseline restored");

        home=new Home();
        FrostedContentView outside=home.frosted(home.decor,190);
        FrostedContentView upper=home.frosted(home.branch,100);upper.layout(0,0,1080,800);
        FrostedContentView zero=home.frosted(home.branch,0);
        FrostedContentView hidden=home.frosted(home.branch,210);hidden.setVisibility(View.GONE);
        Home other=new Home();FrostedContentView second=other.frosted(other.branch,220);
        FrostedContentView token=home.frosted(home.branch,230);token.setWindowTokenForTest(new Object());
        home.controller.refresh();home.controller.updated(second,220);home.controller.beforeDraw(second);
        check(outside.bottom==190&&upper.bottom==100&&zero.bottom==0&&hidden.bottom==210&&second.bottom==220&&token.bottom==230,"unrelated branch/top-only/hidden/other window/token untouched");
        check(zero.writes==0&&outside.writes==0&&second.writes==0,"no unowned writes");

        Home plain=new Home(new Activity());FrostedContentView notLauncher=plain.frosted(plain.branch,110);
        plain.controller.refresh();check(notLauncher.bottom==110,"non-launcher windows excluded");
        Home tokenHome=new Home();FrostedContentView wrongToken=tokenHome.frosted(tokenHome.branch,115);
        tokenHome.nativeBar.setWindowTokenForTest(new Object());tokenHome.controller.refresh();
        check(wrongToken.bottom==115,"native bar with wrong window token excluded");
        Home large=new Home();FrostedContentView unbounded=large.frosted(large.branch,180);
        for(int i=0;i<1025;i++)large.branch.addView(new View(large.activity));
        large.controller.refresh();check(unbounded.bottom==180,"incomplete ownership scan fails closed");
    }

    private static void updates(){
        Home home=new Home();FrostedContentView view=home.frosted(home.pager,140);home.controller.refresh();
        view.setBottomBlurAreaHeight(200);
        check(view.bottom==0,"host setter reapplied to zero");
        home.controller.release();check(view.bottom==200,"latest setter value restored, not initial value");
        home.controller.refresh();
        NavigationFrostedController.beginConfiguration(view);
        view.setBottomBlurAreaHeight(333);
        view.configure(250);
        check(view.bottom==250,"config nested setter not prematurely suppressed");
        check(NavigationFrostedController.endConfiguration(view),"outer configuration completed");
        home.controller.updated(view,null);
        check(view.bottom==0,"direct configuration result suppressed");
        home.controller.release();check(view.bottom==250,"final configuration result restored");
        home.controller.refresh();
        view.bottom=275;home.controller.beforeDraw(view);
        check(view.bottom==0,"direct positive field overwrite fixed before draw");
        home.controller.release();check(view.bottom==275,"direct positive field baseline restored");
        home.controller.refresh();
        NavigationFrostedController.beginConfiguration(view);view.configure(0);
        NavigationFrostedController.endConfiguration(view);home.controller.updated(view,null);
        home.controller.release();check(view.bottom==0,"configuration direct zero replaces restore baseline");
        view.bottom=275;home.controller.refresh();
        view.setBottomBlurAreaHeight(0);view.setBottomBlurAreaHeight(0);
        home.controller.release();check(view.bottom==0,"explicit host zero becomes restore baseline");
        check(!NavigationFrostedController.writing(view),"write guard always cleared");
        view.bottom=300;home.controller.refresh();
        view.bottom=310;home.controller.release();
        check(view.bottom==310,"unsuppressed host change preserved at release");
        untouched(view);
        home.controller.refresh();
        int writes=view.writes;
        for(int i=0;i<20;i++){home.controller.beforeDraw(view);home.controller.refresh();}
        check(view.writes==writes,"zero draw/check does not repeatedly write");
        int logCount=home.logs.size();home.controller.release();home.controller.release();
        check(home.logs.size()==logCount+1,"release idempotent, diagnostics not spammed");
    }

    private static void lifecycle(){
        Home transition=new Home();FrostedContentView zero=transition.frosted(transition.pager,160);transition.controller.refresh();
        transition.active=false;zero.setBottomBlurAreaHeight(0);
        check(zero.bottom==0,"host zero during pause wins over previous baseline");
        transition.controller.release();check(zero.bottom==0,"pause zero remains after repeated release");
        Home home=new Home();FrostedContentView view=home.frosted(home.pager,160);home.controller.refresh();
        home.active=false;home.controller.refresh();check(view.bottom==160,"pause restores bottom");
        view.setBottomBlurAreaHeight(180);check(view.bottom==180,"paused host update not suppressed");
        home.active=true;home.controller.refresh();check(view.bottom==0,"resume reacquires");
        ChattingUILayout chat=new ChattingUILayout(home.activity);chat.layout(0,0,1080,2400);home.decor.addView(chat);
        FrostedContentView chatting=home.frosted(chat,200);
        home.controller.refresh();check(view.bottom==180&&chatting.bottom==200,"chat entry restores home and leaves chat blur");
        home.controller.beforeDraw(chatting);check(chatting.writes==0,"chat branch never acquired");
        chat.setVisibility(View.GONE);home.controller.refresh();check(view.bottom==0,"return from chat reacquires home");
        home.active=false;home.controller.release();check(view.bottom==180,"disable restores");
        home.controller.beforeDraw(view);check(view.bottom==180,"disabled draw never suppresses");
        home.active=true;home.controller.refresh();check(view.bottom==0,"reenable reacquires");
        home.pager.setVisibility(View.GONE);home.controller.refresh();check(view.bottom==180,"hidden home restores");
        home.pager.setVisibility(View.VISIBLE);home.controller.refresh();check(view.bottom==0,"shown home reacquires");
        home.overlay.setTranslationY(500);home.overlay.setVisibility(View.GONE);home.controller.refresh();home.controller.beforeDraw(view);
        check(view.bottom==0,"auto-hide translation/visibility does not restore the old full-width mask");
        home.activity.destroyForTest();home.controller.refresh();check(view.bottom==180,"destroy restores");
    }

    private static void reparentAndFailures(){
        Home home=new Home();FrostedContentView view=home.frosted(home.branch,165);home.controller.refresh();
        home.branch.removeView(view);home.decor.addView(view);home.controller.refresh();
        check(view.bottom==165,"view leaving home branch conditionally restored");
        home.decor.removeView(view);home.branch.addView(view);home.controller.beforeDraw(view);
        check(view.bottom==0,"view reentering home branch reacquired at draw");
        home.controller.release();
        view.failWrite=true;home.controller.refresh();
        check(view.bottom==165&&!NavigationFrostedController.writing(view),"failed setter does not leak guard or destroy baseline");
        view.failWrite=false;home.controller.beforeDraw(view);check(view.bottom==0,"successful retry acquires");
        home.controller.release();check(view.bottom==165,"retry restores correct baseline");
        check(home.logs.stream().filter(value->value.contains("失败")).count()==1,"failure diagnostic low frequency");
        home.controller.refresh();
        NavigationFrostedController.beginConfiguration(view);NavigationFrostedController.beginConfiguration(view);
        view.bottom=185;
        check(!NavigationFrostedController.endConfiguration(view),"nested config does not complete outer transaction");
        home.controller.updated(view,null);check(view.bottom==185,"inner config callback ignored");
        check(NavigationFrostedController.endConfiguration(view),"outer config released");home.controller.updated(view,null);
        check(view.bottom==0,"nested configuration final value suppressed");
        home.controller.release();check(view.bottom==185,"nested config final baseline restored");
        home.controller.refresh();view.failWrite=true;home.controller.release();
        check(view.bottom==0&&!NavigationFrostedController.writing(view),"failed restoration clears guard");
        view.failWrite=false;home.controller.release();check(view.bottom==185,"failed restoration baseline retained for retry");
    }

    public static void main(String[] args){
        scopes();updates();lifecycle();reparentAndFailures();
        System.out.println("PASS: "+checks+" production Frosted ownership/window/config/setter/draw/reentry/restoration assertions (host render remains device-only)");
    }
}
