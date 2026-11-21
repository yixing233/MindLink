package wex.ui.core.feature;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;

/** Full production SettingsShell.build, attached to its real target Dialog contract. */
public final class SettingsBarsChecks {
    static int checks;
    static final class Fixture {
        final Activity activity=new Activity();
        final Dialog dialog=new Dialog(activity);
        final Window window=dialog.getWindow();
        final View root;
        Fixture(boolean dark) {
            activity.getResources().getConfiguration().uiMode=dark?0x20:0x10;
            // Actual SettingsPanelHelper calls the API before setContentView/show.
            SettingsShell.applyBarAppearance(window,dark);
            root=SettingsShell.build(activity,dialog);
            dialog.setContentView(root);
        }
        void show(){dialog.show();android.os.Handler.drainForTest();}
        void dismiss(){dialog.dismiss();android.os.Handler.drainForTest();}
    }
    public static void main(String[] args) {
        boolean baseline=!args[0].equals("green");
        if(baseline) {
            boolean dark=args[0].equals("settings-dark");Fixture f=new Fixture(dark);
            f.window.controller.setSystemBarsAppearance(dark?24:0,24);f.show();
            eq(dark?0:24,f.window.controller.getSystemBarsAppearance()&24,"attached settings window must use glyph contrast for its rendered page");return;
        }
        for(boolean dark:new boolean[]{false,true}) {
            Fixture f=new Fixture(dark);int value=dark?0:24;
            eq(0,f.window.controller.writes,"before show cannot reach controller");
            f.window.controller.setSystemBarsAppearance((dark?24:0)|64,88);f.show();
            eq(value,f.window.controller.getSystemBarsAppearance()&24,"attach status/nav contrast");
            eq(64,f.window.controller.getSystemBarsAppearance()&64,"unrelated appearance bits preserved");
            eq(0,f.window.getStatusBarColor(),"settings status sees its page rather than helper black paint");
            eq(0xFF000000,f.window.getNavigationBarColor(),"settings fix does not alter navigation background policy");
            eq(0,f.activity.getWindow().controller.writes,"underlying Activity untouched");
            int writes=f.window.controller.writes,colorWrites=f.window.colorWrites;
            f.root.focusForTest(true);android.os.Handler.drainForTest();
            eq(writes,f.window.controller.writes,"stable focus does not repeat glyph writes");
            eq(colorWrites,f.window.colorWrites,"stable focus does not repeat background writes");
            f.window.controller.setSystemBarsAppearance(dark?24:0,24);f.root.focusForTest(false);android.os.Handler.drainForTest();
            eq(dark?24:0,f.window.controller.getSystemBarsAppearance()&24,"focus loss not written");
            f.root.focusForTest(true);android.os.Handler.drainForTest();
            eq(value,f.window.controller.getSystemBarsAppearance()&24,"focus gain after child window reasserts contrast");
            f.dismiss();eq(0,f.root.getViewTreeObserver().listeners.size(),"focus observer removed on detach");
            eq(1,f.dialog.dismisses,"existing dismissal contract not replaced");
            writes=f.window.controller.writes;f.root.focusForTest(true);android.os.Handler.drainForTest();
            eq(writes,f.window.controller.writes,"detached callback cannot write dead window");
        }
        Fixture paused=new Fixture(false);paused.dialog.show();paused.dialog.dismiss();android.os.Handler.drainForTest();
        eq(0,paused.window.controller.writes,"dismiss cancels queued first-attach write");
        Fixture delayed=new Fixture(false);delayed.dialog.show();delayed.window.ready=false;android.os.Handler.drainForTest();
        eq(0,delayed.window.controller.writes,"null controller safe after attach");
        delayed.window.ready=true;delayed.root.focusForTest(true);android.os.Handler.drainForTest();
        eq(24,delayed.window.controller.getSystemBarsAppearance()&24,"later window focus recovers null controller");delayed.dismiss();
        android.os.Build.VERSION.SDK_INT=28;Fixture legacy=new Fixture(false);legacy.show();
        eq(0x2010,legacy.window.getDecorView().getSystemUiVisibility()&0x2010,"legacy status/nav contrast fallback");legacy.dismiss();
        android.os.Build.VERSION.SDK_INT=37;
        System.out.println("GREEN: "+checks+" settings attach/focus/lifecycle/contrast assertions; full actual SettingsShell + SettingsTheme");
    }
    static void eq(int expected,int actual,String label) {
        checks++;if(expected!=actual)throw new AssertionError(label+" expected="+Integer.toHexString(expected)+" actual="+Integer.toHexString(actual));
    }
}
