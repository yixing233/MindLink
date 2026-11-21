package wex.ui.core.feature;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** Executes the real page against strict View contracts; not Android measurement/rendering. */
public final class ModuleDashboardChecks {
    private static int checks;

    private static void check(boolean value,String message){
        checks++;
        if(!value)throw new AssertionError(message);
    }

    private static TextView find(View root,String value){
        if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){TextView match=find(group.getChildAt(i),value);if(match!=null)return match;}
        }
        return null;
    }

    private static void click(View page,String title){
        TextView label=find(page,title);
        check(label!=null,"action exists: "+title);
        View current=label;
        boolean clicked=false;
        while(current!=null&&!clicked){clicked=current.performClick();current=current.getParent() instanceof View?(View)current.getParent():null;}
        check(clicked,"action callback: "+title);
    }

    private static void layoutContracts(View root){
        if(!(root instanceof ViewGroup))return;
        ViewGroup group=(ViewGroup)root;
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);
            check(child.getLayoutParams()!=null,"child layout params present");
            if(group instanceof LinearLayout)check(child.getLayoutParams() instanceof LinearLayout.LayoutParams,"linear child params");
            if(group instanceof FrameLayout)check(child.getLayoutParams() instanceof FrameLayout.LayoutParams,"frame child params");
            layoutContracts(child);
        }
    }

    private static Activity activity(boolean dark,boolean wechat){
        Activity activity=new Activity();
        activity.getResources().getConfiguration().uiMode=dark?0x20:0x10;
        if(wechat)activity.getPackageManager().packages.put(ModuleDashboardState.WECHAT,new PackageInfo("8.0.76",3141));
        return activity;
    }

    private static ModuleDashboardPage attach(Activity activity,android.os.Bundle saved){
        ModuleDashboardPage page=(ModuleDashboardPage)AboutPageBuilder.build(activity,saved);activity.setContentView(page);Handler.drainForTest();return page;
    }

    private static void build(boolean dark,boolean wechat){
        Activity activity=activity(dark,wechat);
        ModuleDashboardPage page=attach(activity,null);
        layoutContracts(page);
        check(((ColorDrawable)page.getBackground()).getColor()==ModulePreferenceViews.page(activity),"neutral desktop theme");
        ViewGroup width=(ViewGroup)page.findViewWithTag("mdl_page_width");
        LinearLayout root=(LinearLayout)page.findViewWithTag("mdl_page_SETTINGS");
        check(root!=null&&page.current()==ModulePageState.Page.SETTINGS,"settings is desktop root");
        width.layout(0,0,300,800);check(root.getLayoutParams().width==300,"narrow content fits");
        width.layout(0,0,2400,1400);
        int maximum=Math.round(600*activity.getResources().getDisplayMetrics().density);
        check(root.getLayoutParams().width==maximum,"wide content capped at 600dp");
        check(root.getPaddingLeft()==ModulePreferenceViews.dp(activity,16)&&root.getPaddingRight()==ModulePreferenceViews.dp(activity,16),"16dp outer gutters");
        TextView large=(TextView)page.findViewWithTag("mdl_large_title");check("设置".contentEquals(large.getText()),"large settings title");
        check(large.getTextSize()==32*activity.getResources().getDisplayMetrics().scaledDensity,"32sp large title");
        View wechatRow=page.findViewWithTag("mdl_row_wechat"),settingsRow=page.findViewWithTag("mdl_row_settings");
        check(wechatRow.getParent()==settingsRow.getParent(),"preferences share group container");
        check(((View)wechatRow.getParent()).getBackground() instanceof ModulePreferenceViews.Surface,"continuous-corner grouped surface");
        check(wechatRow.getBackground()==null&&wechatRow.getForeground()!=null,"row feedback without individual tile");
        check(wechatRow.getMinimumHeight()==ModulePreferenceViews.dp(activity,56)&&wechatRow.getLayoutParams().height==-2,"content-growing 56dp preference row");
        check(find(page,"开启项（已保存）")==null&&find(page,"微信宿主")==null,"old stats/dashboard removed");
        WindowInsets insets=new WindowInsets(Insets.of(4,25,6,34));page.dispatchApplyWindowInsets(insets);page.dispatchApplyWindowInsets(insets);
        check(page.getPaddingLeft()==4&&page.getPaddingTop()==25&&page.getPaddingRight()==6&&page.getPaddingBottom()==34,"insets never accumulate");
        page.dispatchApplyWindowInsets(new WindowInsets(Insets.of(0,10,0,12)));check(page.getPaddingTop()==10&&page.getPaddingBottom()==12,"changed insets replace previous values");
        android.widget.ScrollView scroll=(android.widget.ScrollView)page.findViewWithTag("mdl_page_scroll");
        scroll.scrollTo(0,ModulePreferenceViews.dp(activity,80));check(page.findViewWithTag("mdl_compact_title").getAlpha()==1,"collapsed centered title");
        scroll.scrollTo(0,0);check(page.findViewWithTag("mdl_compact_title").getAlpha()==0,"expanded compact title hidden");
        click(page,"复制脱敏诊断");String copied=((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).clip.text.toString();
        check(copied.contains("source=desktop")&&copied.contains("activation=unknown"),"desktop diagnosis independent of Xposed");
        for(String forbidden:new String[]{"account=","nickname=","avatar=","textStatus=","http"})check(!copied.contains(forbidden),"safe diagnosis excludes "+forbidden);
        click(page,"Mdl 功能设置");check(android.widget.Toast.lastText.contains("微信右上角"),"native toast works without host picker");
        click(page,"状态与环境");check(page.current()==ModulePageState.Page.DIAGNOSTICS,"diagnostics is secondary page");
        check(find(page,wechat?"宿主状态未知":"未检测到微信")!=null,"unknown activation distinct from missing host");
        check(page.back(),"diagnostics returns to settings");Handler.drainForTest();
        check(activity.launches.isEmpty(),"build/diagnosis/settings do not open host");
        page.dispose();activity.destroyForTest();
        Activity replacement=activity(dark,wechat);ModuleDashboardPage rebuilt=attach(replacement,null);rebuilt.dispatchApplyWindowInsets(insets);
        check(rebuilt.getPaddingTop()==25&&rebuilt.getPaddingBottom()==34,"rebuild fresh inset baseline");rebuilt.dispose();
        check(wex.ui.core.WexStore.calls==0&&FeatureDiagnostics.calls==0&&ImagePicker.calls==0,"no host store/diagnosis/picker access");
    }

    private static void navigation(){
        Activity activity=activity(false,true);ModuleDashboardPage page=attach(activity,null);
        android.widget.ScrollView initial=(android.widget.ScrollView)page.findViewWithTag("mdl_page_scroll");initial.scrollTo(0,123);
        click(page,"关于");Handler.drainForTest();check(page.current()==ModulePageState.Page.ABOUT,"about opens independently");layoutContracts(page);
        check(page.findViewWithTag("mdl_large_title")==null,"about has brand hero rather than large page title");
        check(page.findViewWithTag("mdl_about_hero").getMinimumHeight()==ModulePreferenceViews.dp(activity,310),"310dp about identity rhythm");
        check(find(page,"MindLink")!=null&&find(page,"1.0.0 (1)")!=null,"own brand and real version");
        check(page.findViewWithTag("mdl_info_设备型号")!=null,"vertical value-caption device information");
        check(page.backgroundRunning()&&Handler.delayedCountForTest()==1,"only active about gradient schedules frames");
        check(activity.getOnBackInvokedDispatcher().countForTest()==1,"secondary page registers system back");
        AboutPageBuilder.lifecycle(activity,false);check(!page.backgroundRunning()&&Handler.delayedCountForTest()==0,"pause stops background");
        AboutPageBuilder.lifecycle(activity,true);check(page.backgroundRunning(),"resume restarts background");
        android.widget.ScrollView about=(android.widget.ScrollView)page.findViewWithTag("mdl_page_scroll");about.scrollTo(0,321);
        android.os.Bundle saved=new android.os.Bundle();AboutPageBuilder.save(activity,saved);
        AboutPageBuilder.dispose(activity);check(Handler.delayedCountForTest()==0&&activity.getOnBackInvokedDispatcher().countForTest()==0,"destroy removes animation and back callback");
        Activity replacement=activity(true,true);ModuleDashboardPage restored=attach(replacement,saved);
        check(restored.current()==ModulePageState.Page.ABOUT&&((android.widget.ScrollView)restored.findViewWithTag("mdl_page_scroll")).getScrollY()==321,"about page and scroll restored");
        click(restored,"许可证");Handler.drainForTest();check(restored.current()==ModulePageState.Page.LICENSE,"local license page");
        check(((TextView)restored.findViewWithTag("mdl_local_document")).getText().toString().contains("GNU GENERAL PUBLIC LICENSE"),"license full local text");
        check(!restored.backgroundRunning()&&Handler.delayedCountForTest()==0,"leave about stops gradient");
        replacement.getOnBackInvokedDispatcher().backForTest();Handler.drainForTest();check(restored.current()==ModulePageState.Page.ABOUT,"system back returns to about");
        click(restored,"隐私与安全");Handler.drainForTest();String privacy=((TextView)restored.findViewWithTag("mdl_local_document")).getText().toString();
        check(privacy.contains("本模块无统计与埋点")&&!privacy.contains("## 🎨 图标"),"local privacy section not invented");
        restored.back();Handler.drainForTest();click(restored,"开源致谢");Handler.drainForTest();
        check(((TextView)restored.findViewWithTag("mdl_local_document")).getText().toString().contains("WeKit"),"local third-party attribution");
        restored.back();Handler.drainForTest();restored.back();Handler.drainForTest();
        check(restored.current()==ModulePageState.Page.SETTINGS&&((android.widget.ScrollView)restored.findViewWithTag("mdl_page_scroll")).getScrollY()==123,"settings scroll survives nested page history");
        check(!restored.back()&&replacement.getOnBackInvokedDispatcher().countForTest()==0,"root back delegated to activity");
        Activity independent=activity(false,true);ModuleDashboardPage other=attach(independent,null);check(other.current()==ModulePageState.Page.SETTINGS,"new activity not sharing static page");
        android.animation.ValueAnimator.enabled=false;click(other,"关于");Handler.drainForTest();check(!other.backgroundRunning(),"reduced animation preference respected");android.animation.ValueAnimator.enabled=true;
        other.dispose();restored.dispose();
        check(Handler.delayedCountForTest()==0,"no leaked animation callbacks");
        android.os.Bundle invalid=new android.os.Bundle();invalid.putString("mdl_desktop_pages","ABOUT,LICENSE");
        check(ModulePageState.restore(invalid).current()==ModulePageState.Page.SETTINGS,"invalid history fails safely to settings");
        LinearLayout root=ModulePreferenceViews.column(independent);LinearLayout group=ModulePreferenceViews.group(root,independent,"测试",false);
        View row=ModulePreferenceViews.row(group,independent,"long","很长的设置名称，允许多行显示","较大的字体和较长的说明不采用固定高度裁切",()->{});
        check(row.getLayoutParams().height==-2&&((ViewGroup)row).getChildAt(0).getLayoutParams().height==-2,"long text layout grows, no fixed row clipping");
    }

    private static void readonly(File directory)throws Exception{
        File config=new File(directory,"config-contract.db"),log=new File(directory,"host-log.txt");
        Files.write(config.toPath(),"existing host config".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(log.toPath(),"existing host log".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] configBefore=Files.readAllBytes(config.toPath()),logBefore=Files.readAllBytes(log.toPath());
        long configTime=config.lastModified(),logTime=log.lastModified(),now=System.currentTimeMillis();
        Activity activity=activity(false,true);
        SQLiteDatabase.reset();
        SQLiteDatabase.values.put("bottom_bar_enabled","true");
        SQLiteDatabase.values.put("bubble_enabled","true");
        SQLiteDatabase.values.put("chat_toolbar_enabled","false");
        SQLiteDatabase.values.put("unrelated_private_setting","secret-ignored");
        ModuleDashboardState state=ModuleDashboardState.read(activity,config,log,now);
        check(state.enabledCount==2,"only saved target toggles counted");
        check(state.heartbeatFresh&&state.verifiedWechat,"recent log and baseline readable");
        check(SQLiteDatabase.opens==1&&SQLiteDatabase.closes==1&&SQLiteDatabase.cursorCloses==1,"readonly query resources closed");
        check(SQLiteDatabase.flags==(SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS),"readonly/no localized migration flags");
        check(!state.stateTitle().equals("已运行"),"log update never claimed to verify activation");
        SQLiteDatabase.failQuery=true;
        check(ModuleDashboardState.read(activity,config,log,now).enabledCount==null,"query failure is unknown, not zero");
        check(SQLiteDatabase.closes==2,"database closed on query failure");
        SQLiteDatabase.failQuery=false;
        SQLiteDatabase.failOpen=true;
        check(ModuleDashboardState.read(activity,config,log,now).enabledCount==null,"storage rejection is unknown");
        SQLiteDatabase.failOpen=false;
        SQLiteDatabase.corrupt=true;
        check(ModuleDashboardState.read(activity,config,log,now).enabledCount==null,"corrupt database remains unknown");
        check(SQLiteDatabase.corruptionCallbacks==1&&config.exists(),"non-destructive corruption handler preserves host file");
        SQLiteDatabase.corrupt=false;
        SQLiteDatabase.values.put("bubble_enabled","malformed");
        check(ModuleDashboardState.read(activity,config,log,now).enabledCount==null,"invalid boolean never treated as false");
        SQLiteDatabase.reset();
        File denied=new File(config.getPath()){@Override public boolean canRead(){return false;}};
        File forbidden=new File(log.getPath()){@Override public boolean isFile(){throw new SecurityException("test denied");}};
        state=ModuleDashboardState.read(activity,denied,forbidden,now);
        check(state.enabledCount==null&&!state.heartbeatReadable,"unreadable storage distinct from activation");
        check(SQLiteDatabase.opens==0,"unreadable config never opened");
        File missing=new File(directory,"missing.db");
        state=ModuleDashboardState.read(activity,missing,new File(directory,"missing.log"),now);
        check(state.enabledCount==null&&!state.heartbeatFresh&&!missing.exists(),"missing file not created");
        check(SQLiteDatabase.opens==0,"no open for missing database");
        state=ModuleDashboardState.read(activity,config,log,now+25L*3600L*1000L);
        check(state.heartbeatReadable&&!state.heartbeatFresh,"old log is not activation failure");
        state=ModuleDashboardState.read(activity,config,log,log.lastModified()-1);
        check(!state.heartbeatFresh,"future mtime not treated as heartbeat");
        check(Arrays.equals(configBefore,Files.readAllBytes(config.toPath()))&&configTime==config.lastModified(),"host config bytes/time untouched");
        check(Arrays.equals(logBefore,Files.readAllBytes(log.toPath()))&&logTime==log.lastModified(),"host log bytes/time untouched");
        check(wex.ui.core.WexStore.calls==0,"zero host-store side effects");
    }

    private static void logs()throws Exception{
        Activity owner=activity(false,false);
        View page=AboutPageBuilder.build(owner);
        click(page,"查看日志");
        AlertDialog dialog=AlertDialog.lastShown;
        check(dialog!=null&&dialog.isShowing(),"local log dialog opens");
        check(find(dialog.getWindow().getDecorView(),"正在读取日志…")!=null,"log starts asynchronously");
        for(int i=0;i<200&&!Handler.hasPendingForTest();i++)Thread.sleep(5);
        check(Handler.hasPendingForTest(),"background result queued to main");
        owner.destroyForTest();
        Handler.drainForTest();
        check(find(dialog.getWindow().getDecorView(),"正在读取日志…")!=null,"stale read ignored after activity destroyed");
        dialog.dismiss();
        owner=activity(true,true);
        page=AboutPageBuilder.build(owner);
        click(page,"查看日志");
        dialog=AlertDialog.lastShown;
        check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(),"log diagnostic callback works");
        String copied=((ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE)).clip.text.toString();
        check(copied.contains("activation=unknown"),"log diagnosis still uses desktop snapshot");
        dialog.dismiss();
        for(int i=0;i<200&&!Handler.hasPendingForTest();i++)Thread.sleep(5);
        Handler.drainForTest();
        check(FeatureDiagnostics.calls==0&&ImagePicker.calls==0,"no host callbacks even on log actions");
    }

    public static void main(String[] args)throws Exception{
        android.content.res.AssetManager.root=java.nio.file.Path.of(args[1]);
        if("--no-host".equals(args[0])){
            for(boolean dark:new boolean[]{false,true}){
                Activity activity=activity(dark,true);ModuleDashboardPage page=attach(activity,null);
                click(page,"复制脱敏诊断");click(page,"Mdl 功能设置");click(page,"关于");page.back();Handler.drainForTest();click(page,"查看日志");
                AlertDialog dialog=AlertDialog.lastShown;
                check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(),"no-host log diagnosis");dialog.dismiss();
            }
            System.out.println("PASS: desktop cold build/actions with host store, host diagnostics, image picker and Xposed classes absent");
            return;
        }
        try{new View(new Context()).setLayoutParams(null);throw new AssertionError("null params accepted");}
        catch(NullPointerException expected){check("Layout parameters cannot be null".equals(expected.getMessage()),"Android non-null contract");}
        for(boolean dark:new boolean[]{false,true})for(boolean wechat:new boolean[]{false,true})build(dark,wechat);
        navigation();
        readonly(new File(args[0]));
        logs();
        System.out.println("PASS: "+checks+" desktop build/layout/inset/read-only/diagnostic/lifecycle assertions (strict doubles; device rendering still required)");
    }
}
