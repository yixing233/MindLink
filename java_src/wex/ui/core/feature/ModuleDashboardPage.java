package wex.ui.core.feature;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Desktop settings/about navigation; feature editing remains in the WeChat process. */
final class ModuleDashboardPage extends FrameLayout {
    static final String TAG="mdl_desktop_pages";
    private static final String REPO="https://github.com/lanqin-mimi/MindLink";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService READS=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"MindLink-log-preview");thread.setDaemon(true);return thread;});
    private final Activity activity;
    private final ModulePageState pages;
    private final OnBackInvokedCallback backCallback=()->back();
    private ScrollView scroll;
    private ModuleAboutBackdrop backdrop;
    private boolean resumed=true,backRegistered,disposed;
    private Runnable restoreScroll;
    private AlertDialog logDialog;

    private ModuleDashboardPage(Activity activity,Bundle saved){
        super(activity);this.activity=activity;pages=ModulePageState.restore(saved);setTag(TAG);
        setOnApplyWindowInsetsListener((view,insets)->{Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});
        addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){view.requestApplyInsets();updateBack();if(backdrop!=null)backdrop.resume(resumed);}
            @Override public void onViewDetachedFromWindow(View view){unregisterBack();if(backdrop!=null)backdrop.resume(false);if(logDialog!=null)logDialog.dismiss();}
        });
        render();
    }
    static View build(Activity activity){return build(activity,null);}
    static View build(Activity activity,Bundle saved){return new ModuleDashboardPage(activity,saved);}
    static ModuleDashboardPage find(Activity activity){View found=activity.getWindow().getDecorView().findViewWithTag(TAG);return found instanceof ModuleDashboardPage?(ModuleDashboardPage)found:null;}
    ModulePageState.Page current(){return pages.current();}
    boolean backgroundRunning(){return backdrop!=null&&backdrop.running();}
    void save(Bundle bundle){if(scroll!=null)pages.scroll(scroll.getScrollY());pages.save(bundle);}
    void lifecycle(boolean value){resumed=value;unregisterBack();if(value)updateBack();if(backdrop!=null)backdrop.resume(value);}
    void dispose(){disposed=true;unregisterBack();if(scroll!=null&&restoreScroll!=null)scroll.removeCallbacks(restoreScroll);if(backdrop!=null)backdrop.resume(false);if(logDialog!=null)logDialog.dismiss();}
    void open(ModulePageState.Page page){if(disposed||pages.current()==page)return;pages.scroll(scroll.getScrollY());pages.open(page);render();}
    boolean back(){if(disposed)return false;pages.scroll(scroll.getScrollY());if(!pages.back())return false;render();return true;}

    private void render(){
        if(backdrop!=null)backdrop.resume(false);
        if(scroll!=null&&restoreScroll!=null)scroll.removeCallbacks(restoreScroll);
        removeAllViews();backdrop=null;
        boolean about=pages.current()==ModulePageState.Page.ABOUT;
        setBackgroundColor(ModulePreferenceViews.page(activity));
        if(about){backdrop=new ModuleAboutBackdrop(activity);addView(backdrop,new FrameLayout.LayoutParams(-1,-1));backdrop.resume(resumed);}
        scroll=new ScrollView(activity);scroll.setTag("mdl_page_scroll");scroll.setFillViewport(true);scroll.setClipToPadding(false);addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout width=new FrameLayout(activity);width.setTag("mdl_page_width");scroll.addView(width,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout root=ModulePreferenceViews.column(activity);root.setTag("mdl_page_"+pages.current().name());root.setPadding(dp(16),dp(56),dp(16),dp(28));
        FrameLayout.LayoutParams rootParams=new FrameLayout.LayoutParams(-1,-2);rootParams.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;width.addView(root,rootParams);
        width.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{int available=view.getWidth();if(available<=0)return;ViewGroup.LayoutParams params=root.getLayoutParams();int target=Math.min(dp(600),available);if(params.width!=target){params.width=target;root.setLayoutParams(params);}});
        String title=title(pages.current());
        if(!about){TextView large=ModulePreferenceViews.text(activity,title,32,false,SettingsTheme.ink(activity));large.setTag("mdl_large_title");large.setPadding(dp(10),dp(12),dp(10),dp(22));root.addView(large,new LinearLayout.LayoutParams(-1,-2));}
        ModuleDashboardState state=ModuleDashboardState.read(activity);
        if(pages.current()==ModulePageState.Page.SETTINGS)settings(root);
        else if(about)ModuleAboutPage.build(root,activity,state,this::open,()->openLink(REPO));
        else if(pages.current()==ModulePageState.Page.DIAGNOSTICS)diagnostics(root,state);
        else{
            TextView content=ModulePreferenceViews.text(activity,ModuleAboutPage.document(activity,pages.current()),15,false,SettingsTheme.ink(activity));content.setTag("mdl_local_document");content.setTextIsSelectable(true);content.setPadding(dp(12),dp(8),dp(12),dp(20));
            if(pages.current()==ModulePageState.Page.LICENSE)content.setTypeface(Typeface.MONOSPACE);root.addView(content,new LinearLayout.LayoutParams(-1,-2));
        }
        FrameLayout toolbar=new FrameLayout(activity);toolbar.setTag("mdl_topbar");toolbar.setBackgroundColor(ModulePreferenceViews.page(activity));toolbar.getBackground().setAlpha(0);
        FrameLayout.LayoutParams barParams=new FrameLayout.LayoutParams(-1,dp(52));barParams.gravity=Gravity.TOP;addView(toolbar,barParams);
        TextView compact=ModulePreferenceViews.text(activity,title,18,true,SettingsTheme.ink(activity));compact.setTag("mdl_compact_title");compact.setGravity(Gravity.CENTER);compact.setAlpha(0);compact.setPadding(dp(64),0,dp(64),0);toolbar.addView(compact,new FrameLayout.LayoutParams(-1,-1));
        if(pages.current()!=ModulePageState.Page.SETTINGS){FrameLayout back=ModulePreferenceViews.back(activity,()->back());FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(46),dp(46));params.gravity=Gravity.START|Gravity.CENTER_VERTICAL;params.setMarginStart(dp(10));toolbar.addView(back,params);}
        scroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->{float collapse=Math.max(0,Math.min(1,y/(float)dp(about?180:80)));compact.setAlpha(collapse);toolbar.getBackground().setAlpha(Math.round(collapse*245));});
        int offset=pages.scroll();ScrollView target=scroll;ModulePageState.Page page=pages.current();
        restoreScroll=()->{if(!disposed&&scroll==target&&pages.current()==page)target.scrollTo(0,offset);};scroll.post(restoreScroll);
        updateBack();
    }
    private void settings(LinearLayout root){
        LinearLayout guide=ModulePreferenceViews.panel(activity,false);guide.setPadding(dp(16),dp(16),dp(16),dp(16));guide.setBackground(new ModulePreferenceViews.Surface((SettingsTheme.accent(activity)&0xFFFFFF)|0x14000000,dp(22.5f)));guide.setTag("mdl_usage_notice");
        guide.addView(ModulePreferenceViews.text(activity,"MindLink",17,true,SettingsTheme.ink(activity)),new LinearLayout.LayoutParams(-1,-2));
        TextView note=ModulePreferenceViews.text(activity,"功能配置在微信内；模块激活状态请在 LSPosed 中确认",14,false,SettingsTheme.sub(activity));note.setPadding(0,dp(4),0,0);guide.addView(note,new LinearLayout.LayoutParams(-1,-2));root.addView(guide,ModulePreferenceViews.block(activity));
        LinearLayout usage=ModulePreferenceViews.group(root,activity,"模块使用",false);
        ModulePreferenceViews.row(usage,activity,"wechat","打开微信",null,()->launchWechat());
        ModulePreferenceViews.row(usage,activity,"settings","Mdl 功能设置","微信右上角“+” → “Mdl 设置”",()->toast("请在微信右上角“+”→“Mdl 设置”进入"));
        LinearLayout diagnosis=ModulePreferenceViews.group(root,activity,"诊断",false);
        ModulePreferenceViews.row(diagnosis,activity,"diagnostics","状态与环境",null,()->open(ModulePageState.Page.DIAGNOSTICS));
        ModulePreferenceViews.row(diagnosis,activity,"log","查看日志","最近 200 行，最多 64 KiB",()->showLog());
        ModulePreferenceViews.row(diagnosis,activity,"copy","复制脱敏诊断","不包含聊天、联系人、昵称或账号全文",()->copy(ModuleDashboardState.read(activity).summary()));
        LinearLayout info=ModulePreferenceViews.group(root,activity,"应用信息",false);
        ModulePreferenceViews.row(info,activity,"about","关于",null,()->open(ModulePageState.Page.ABOUT));
    }
    private void diagnostics(LinearLayout root,ModuleDashboardState state){
        LinearLayout status=ModulePreferenceViews.group(root,activity,"宿主状态",false);
        ModulePreferenceViews.info(status,activity,"只读观察",state.stateTitle());ModulePreferenceViews.info(status,activity,"说明",state.stateDetail());ModulePreferenceViews.info(status,activity,"模块激活","未知；请在 LSPosed 中确认");
        LinearLayout environment=ModulePreferenceViews.group(root,activity,"环境信息",false);
        ModulePreferenceViews.info(environment,activity,"MindLink",state.moduleVersion);ModulePreferenceViews.info(environment,activity,"微信版本",state.wechatVersion);ModulePreferenceViews.info(environment,activity,"Android",state.androidVersion);ModulePreferenceViews.info(environment,activity,"设备",state.device);ModulePreferenceViews.info(environment,activity,"最后日志更新",state.lastHeartbeat);ModulePreferenceViews.info(environment,activity,"开启项（已保存）",state.enabledCount==null?"未知":String.valueOf(state.enabledCount));
        LinearLayout actions=ModulePreferenceViews.group(root,activity,"诊断操作",false);ModulePreferenceViews.row(actions,activity,"copy","复制脱敏诊断",null,()->copy(state.summary()));ModulePreferenceViews.row(actions,activity,"log","查看日志",null,()->showLog());
    }
    private void updateBack(){if(!disposed&&resumed&&isAttachedToWindow()&&pages.current()!=ModulePageState.Page.SETTINGS){if(!backRegistered){activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);backRegistered=true;}}else unregisterBack();}
    private void unregisterBack(){if(backRegistered){activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);backRegistered=false;}}
    private static String title(ModulePageState.Page page){switch(page){case ABOUT:return"关于";case DIAGNOSTICS:return"状态与环境";case LICENSE:return"许可证";case PRIVACY:return"隐私与安全";case CREDITS:return"开源致谢";default:return"设置";}}
    private void showLog(){
        if(logDialog!=null&&logDialog.isShowing())return;
        TextView content=ModulePreferenceViews.text(activity,"正在读取日志…",12,false,SettingsTheme.ink(activity));content.setTypeface(Typeface.MONOSPACE);content.setTextIsSelectable(true);content.setPadding(dp(16),dp(12),dp(16),dp(12));
        ScrollView preview=new ScrollView(activity);preview.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("宿主日志").setView(preview).setPositiveButton("复制诊断",(d,w)->copy(ModuleDashboardState.read(activity).summary())).setNegativeButton("关闭",null).show();logDialog=dialog;
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(SettingsTheme.popupBackground(activity));
        WeakReference<Activity> owner=new WeakReference<>(activity);WeakReference<AlertDialog> popup=new WeakReference<>(dialog);WeakReference<TextView> output=new WeakReference<>(content);
        Future<?> task=READS.submit(()->{
            String value;try{value=LogPreview.read(new File(ModuleDashboardState.LOG_PATH));}catch(Exception error){value="日志不可读："+error.getClass().getSimpleName();}
            String result=value;MAIN.post(()->{Activity current=owner.get();AlertDialog open=popup.get();TextView target=output.get();if(current!=null&&!current.isFinishing()&&!current.isDestroyed()&&open!=null&&open.isShowing()&&target!=null)target.setText(result);});
        });
        dialog.setOnDismissListener(d->task.cancel(true));
    }
    private void copy(String value){try{ClipboardManager manager=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);if(manager==null){toast("剪贴板不可用");return;}manager.setPrimaryClip(ClipData.newPlainText("MindLink diagnostics",value));toast("已复制脱敏诊断");}catch(RuntimeException error){toast("无法复制诊断");}}
    private void launchWechat(){try{Intent intent=activity.getPackageManager().getLaunchIntentForPackage(ModuleDashboardState.WECHAT);if(intent==null)throw new IllegalStateException();activity.startActivity(intent);}catch(Exception error){toast("无法打开微信");}}
    private void openLink(String uri){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri)));}catch(Exception error){toast("无法打开链接");}}
    private void toast(String value){android.widget.Toast.makeText(activity,value,android.widget.Toast.LENGTH_SHORT).show();}
    private int dp(float value){return ModulePreferenceViews.dp(activity,value);}
}
