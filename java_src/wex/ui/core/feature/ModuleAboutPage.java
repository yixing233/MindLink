package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** HyperChanger About hierarchy with MindLink identity and local, truthful project notices. */
final class ModuleAboutPage {
    private ModuleAboutPage(){}
    static void build(LinearLayout root,Activity activity,ModuleDashboardState state,Consumer<ModulePageState.Page> open,Runnable repository){
        FrameLayout hero=new FrameLayout(activity);hero.setTag("mdl_about_hero");hero.setMinimumHeight(ModulePreferenceViews.dp(activity,310));
        LinearLayout brand=ModulePreferenceViews.column(activity);brand.setGravity(Gravity.CENTER_HORIZONTAL);brand.setTranslationY(-ModulePreferenceViews.dp(activity,40));brand.setPadding(ModulePreferenceViews.dp(activity,18),0,ModulePreferenceViews.dp(activity,18),0);
        ImageView icon=new ImageView(activity);icon.setContentDescription("MindLink 图标");icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try{Drawable own=activity.getPackageManager().getApplicationIcon(activity.getPackageName());icon.setImageDrawable(own);}catch(Exception ignored){}
        brand.addView(icon,new LinearLayout.LayoutParams(ModulePreferenceViews.dp(activity,128),ModulePreferenceViews.dp(activity,128)));
        TextView name=ModulePreferenceViews.text(activity,"MindLink",32,true,SettingsTheme.ink(activity));name.setGravity(Gravity.CENTER);name.setPadding(0,ModulePreferenceViews.dp(activity,14),0,0);brand.addView(name,new LinearLayout.LayoutParams(-1,-2));
        TextView version=ModulePreferenceViews.text(activity,state.moduleVersion,14,true,SettingsTheme.sub(activity));version.setTag("mdl_about_version");version.setGravity(Gravity.CENTER);version.setPadding(0,ModulePreferenceViews.dp(activity,18),0,0);brand.addView(version,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(-1,-2);center.gravity=Gravity.CENTER;hero.addView(brand,center);root.addView(hero,ModulePreferenceViews.block(activity));
        LinearLayout identity=ModulePreferenceViews.panel(activity,true);
        ModulePreferenceViews.row(identity,activity,"project","MindLink","基于 Wex 改进，功能实现参考并移植自 WeKit",repository);root.addView(identity,ModulePreferenceViews.block(activity));
        LinearLayout info=ModulePreferenceViews.panel(activity,true);
        ModulePreferenceViews.info(info,activity,"设备型号",state.device);ModulePreferenceViews.info(info,activity,"Android 版本",state.androidVersion);
        ModulePreferenceViews.info(info,activity,"微信版本",state.wechatVersion);ModulePreferenceViews.info(info,activity,"兼容性参考基线","微信 8.0.76 (3141)；不代表全部功能已验证");root.addView(info,ModulePreferenceViews.block(activity));
        LinearLayout links=ModulePreferenceViews.panel(activity,true);
        ModulePreferenceViews.row(links,activity,"license","许可证","GNU General Public License v3.0",()->open.accept(ModulePageState.Page.LICENSE));
        ModulePreferenceViews.row(links,activity,"repository","项目仓库","github.com/lanqin-mimi/MindLink",repository);root.addView(links,ModulePreferenceViews.block(activity));
        LinearLayout notices=ModulePreferenceViews.panel(activity,true);
        ModulePreferenceViews.row(notices,activity,"privacy","隐私与安全","本地存储、资料读取与联网范围",()->open.accept(ModulePageState.Page.PRIVACY));
        ModulePreferenceViews.row(notices,activity,"credits","开源致谢","Wex、WeKit 与第三方组件",()->open.accept(ModulePageState.Page.CREDITS));root.addView(notices,ModulePreferenceViews.block(activity));
        TextView footer=ModulePreferenceViews.text(activity,"MindLink · GPL-3.0",14,false,SettingsTheme.sub(activity));footer.setPadding(ModulePreferenceViews.dp(activity,12),0,0,ModulePreferenceViews.dp(activity,12));root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
    }
    static String document(Activity activity,ModulePageState.Page page){
        String path=page==ModulePageState.Page.LICENSE?"mindlink/LICENSE.txt":page==ModulePageState.Page.CREDITS?"mindlink/THIRD-PARTY-NOTICES.md":"mindlink/README.md";
        try(InputStream input=activity.getAssets().open(path);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int size;
            while((size=input.read(buffer))!=-1){if(output.size()+size>512*1024)return"本地声明文件过大，无法显示";output.write(buffer,0,size);}
            String text=output.toString(StandardCharsets.UTF_8.name());
            if(page==ModulePageState.Page.PRIVACY){int start=text.indexOf("## 🔒 隐私与安全");if(start<0)return"本地隐私声明未找到";int end=text.indexOf("\n## ",start+3);text=text.substring(start,end<0?text.length():end);}
            return page==ModulePageState.Page.LICENSE?text:text.replaceAll("(?m)^#{1,4} +","").replace("**","").replace("`","");
        }catch(Exception error){return"本地声明不可读："+error.getClass().getSimpleName();}
    }
}
