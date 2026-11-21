package wex.ui.core.feature;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Desktop snapshot; never enters the host store, migration or hook initialization. */
final class ModuleDashboardState {
    static final String LOG_PATH = "/sdcard/Android/media/com.tencent.mm/Mdl/log.txt";
    static final String STAGE_PATH = "/sdcard/Android/media/com.tencent.mm/Mdl/feature-stages.txt";
    static final String CONFIG_PATH = "/sdcard/Android/media/com.tencent.mm/Mdl/wex_config.db";
    static final String WECHAT = "com.tencent.mm";
    private static final String[] TOGGLES = {
            "top_profile_enabled", "top_scan_enabled", "conversation_filter_enabled",
            "bottom_bar_enabled", "home_side_panel_enabled", "floating_chat_header_enabled",
            "chat_toolbar_enabled", "floating_chat_footer_enabled", "bubble_enabled",
            "global_background_enabled", "mark_all_read_enabled"
    };

    final String moduleVersion, wechatVersion, androidVersion, device, lastHeartbeat;
    final boolean wechatInstalled, verifiedWechat, heartbeatReadable, heartbeatFresh;
    final Integer enabledCount;

    private ModuleDashboardState(String moduleVersion,String wechatVersion,String androidVersion,String device,
                                 String lastHeartbeat,boolean wechatInstalled,boolean verifiedWechat,
                                 boolean heartbeatReadable,boolean heartbeatFresh,Integer enabledCount){
        this.moduleVersion=moduleVersion;this.wechatVersion=wechatVersion;this.androidVersion=androidVersion;
        this.device=device;this.lastHeartbeat=lastHeartbeat;this.wechatInstalled=wechatInstalled;
        this.verifiedWechat=verifiedWechat;this.heartbeatReadable=heartbeatReadable;
        this.heartbeatFresh=heartbeatFresh;this.enabledCount=enabledCount;
    }

    static ModuleDashboardState read(Activity activity){
        return read(activity,new File(CONFIG_PATH),new File(LOG_PATH),System.currentTimeMillis());
    }

    static ModuleDashboardState read(Activity activity,File config,File log,long now){
        String module="未知",wechat="未知";boolean installed=false,verified=false;
        try{PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);module=info.versionName+" ("+info.getLongVersionCode()+")";}catch(Exception ignored){}
        try{PackageInfo info=activity.getPackageManager().getPackageInfo(WECHAT,0);installed=true;wechat=info.versionName+" ("+info.getLongVersionCode()+")";verified="8.0.76".equals(info.versionName)&&info.getLongVersionCode()==3141L;}
        catch(PackageManager.NameNotFoundException missing){wechat="未安装";}catch(Exception ignored){}
        boolean readable=false,fresh=false;String heartbeat="不可读或不存在";
        try{
            readable=log.isFile()&&log.canRead();
            if(readable){long modified=log.lastModified(),age=now-modified;fresh=modified>0&&age>=0&&age<24L*3600L*1000L;heartbeat=new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(modified));}
        }catch(SecurityException ignored){}
        return new ModuleDashboardState(module,wechat,Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")",
                Build.MANUFACTURER+" "+Build.MODEL,heartbeat,installed,verified,readable,fresh,readEnabledCount(config));
    }

    private static Integer readEnabledCount(File file){
        try{
            if(!file.isFile()||!file.canRead())return null;
            StringBuilder query=new StringBuilder("SELECT key,value FROM config WHERE key IN (");
            for(int i=0;i<TOGGLES.length;i++){if(i!=0)query.append(',');query.append('?');}
            query.append(')');
            // The default corruption handler may delete the host database, even on a read-only open.
            try(SQLiteDatabase database=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
                    SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS,corrupt->{});
                Cursor cursor=database.rawQuery(query.toString(),TOGGLES)){
                int count=0;
                while(cursor.moveToNext()){
                    String value=cursor.getString(1);
                    if("true".equals(value))count++;
                    else if(!"false".equals(value))return null;
                }
                return count;
            }
        }catch(Exception ignored){return null;}
    }

    String summary(){
        return "MindLink desktop diagnostics\nsource=desktop (host hooks not queried)\nmodule="+moduleVersion
                +"\nwechat="+wechatVersion+"\nandroid="+androidVersion+"\ndevice="+device
                +"\nbaselineMatch="+verifiedWechat+"\nactivation=unknown\nlogReadable="+heartbeatReadable
                +"\nlogUpdatedWithin24h="+heartbeatFresh+"\nsavedEnabledToggles="+(enabledCount==null?"unknown":enabledCount)+"\n";
    }

    String stateTitle(){if(!wechatInstalled)return"未检测到微信";if(heartbeatFresh)return verifiedWechat?"已观察到宿主日志":"需确认兼容性";return heartbeatReadable?"未观察到近期宿主日志":"宿主状态未知";}
    String stateDetail(){if(!wechatInstalled)return"请确认微信已安装并可被模块查询";if(!heartbeatReadable)return"独立进程无法读取微信日志，不等同于模块未激活";if(!heartbeatFresh)return"最近 24 小时未观察到日志更新；激活状态请在 LSPosed 中确认";return verifiedWechat?"微信 8.0.76 基线匹配；日志更新不代表全部功能已通过验证":"当前微信版本尚未完成实机验证";}
    int severity(){if(!wechatInstalled)return 2;return heartbeatFresh&&verifiedWechat?0:1;}
}
