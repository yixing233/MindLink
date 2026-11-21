package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Owns only the bottom strip of homepage Frosted views, never their top blur or window colors. */
final class NavigationFrostedController {
    static final String FROSTED="com.tencent.mm.ui.FrostedContentView";
    private static final String CHATTING="com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout";
    private static final ThreadLocal<IdentityHashMap<View,Integer>> WRITES=ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<IdentityHashMap<View,Integer>> CONFIGURING=ThreadLocal.withInitial(IdentityHashMap::new);

    interface Home {
        Activity activity();
        View pager();
        View nativeBar();
        View overlay();
        boolean active();
    }

    private static final class State {
        int bottom,areaHeight;
        boolean owned;
        final String path;
        State(int bottom,String path){this.bottom=areaHeight=bottom;this.path=path;}
    }

    private final Home home;
    private final Consumer<String> log;
    private final WeakHashMap<View,State> states=new WeakHashMap<>();
    private WeakReference<View> branch=new WeakReference<>(null);
    private boolean active,warning;

    NavigationFrostedController(Home home,Consumer<String> log){this.home=home;this.log=log;}

    static void beginConfiguration(View view){enter(CONFIGURING.get(),view);}
    static boolean endConfiguration(View view){leave(CONFIGURING.get(),view);return !CONFIGURING.get().containsKey(view);}
    static boolean writing(View view){return WRITES.get().containsKey(view);}
    private static void enter(IdentityHashMap<View,Integer> depths,View view){Integer depth=depths.get(view);depths.put(view,depth==null?1:depth+1);}
    private static void leave(IdentityHashMap<View,Integer> depths,View view){Integer depth=depths.get(view);if(depth==null||depth<=1)depths.remove(view);else depths.put(view,depth-1);}

    void updated(View view,Integer requested){reconcile(view,requested,true);}
    void beforeDraw(View view){reconcile(view,null,false);}

    void refresh(){
        View next=homeBranch();
        if(!available()||next==null){release();return;}
        if(!active||branch.get()!=next){
            release();active=true;branch=new WeakReference<>(next);
            View pager=home.pager();
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(pager.getRootView());
            for(int visited=0;!queue.isEmpty()&&visited<768;visited++){
                View view=queue.removeFirst();
                if(FROSTED.equals(view.getClass().getName()))beforeDraw(view);
                if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount()&&queue.size()<768;i++)queue.addLast(group.getChildAt(i));}
            }
        }
        for(Map.Entry<View,State> entry:new ArrayList<>(states.entrySet())){
            if(path(entry.getKey(),entry.getValue().areaHeight)==null)restore(entry.getKey(),entry.getValue());
        }
    }

    void release(){
        for(Map.Entry<View,State> entry:new ArrayList<>(states.entrySet()))restore(entry.getKey(),entry.getValue());
        active=false;branch.clear();
    }

    private void reconcile(View view,Integer requested,boolean hostUpdate){
        if(!FROSTED.equals(view.getClass().getName())||writing(view)||CONFIGURING.get().containsKey(view))return;
        try{
            int value=height(view);
            State state=states.get(view);
            int hostValue=requested==null?value:requested;
            if(state!=null&&hostUpdate){state.bottom=hostValue;if(hostValue>0)state.areaHeight=hostValue;}
            if(!available()){release();return;}
            int area=hostValue>0?hostValue:state==null?0:state.areaHeight;
            String path=path(view,area);
            if(path==null){if(state!=null)restore(view,state);return;}
            if(state==null){
                if(area<=0)return;
                state=new State(hostValue,path);states.put(view,state);
                log.accept("原生底部遮罩接管 "+FROSTED+" path="+path+" bottom="+hostValue+" -> 0");
            }else if(!hostUpdate&&value!=0){state.bottom=value;if(value>0)state.areaHeight=value;}
            if(value!=0){write(view,0);state.owned=true;view.invalidate();}
        }catch(ReflectiveOperationException|RuntimeException error){warn("接管",error);}
    }

    private void restore(View view,State state){
        try{
            int value=height(view);
            if(state.owned&&value==0){
                if(state.bottom!=0){write(view,state.bottom);view.invalidate();}
                log.accept("原生底部遮罩恢复 "+FROSTED+" path="+state.path+" bottom=0 -> "+state.bottom);
            }
            states.remove(view);
        }catch(ReflectiveOperationException|RuntimeException error){warn("恢复",error);}
    }

    private void warn(String stage,Exception error){if(!warning){warning=true;log.accept("原生底部遮罩"+stage+"失败 "+error.getClass().getSimpleName());}}
    private static int height(View view)throws ReflectiveOperationException{return ((Number)view.getClass().getMethod("getBottomBlurAreaHeight").invoke(view)).intValue();}
    private static void write(View view,int value)throws ReflectiveOperationException{
        enter(WRITES.get(),view);
        try{view.getClass().getMethod("setBottomBlurAreaHeight",int.class).invoke(view,value);}
        finally{leave(WRITES.get(),view);}
    }

    private boolean available(){
        Activity activity=home.activity();View pager=home.pager(),nativeBar=home.nativeBar();
        if(!home.active()||activity==null||activity.isDestroyed()||activity.isFinishing()
                ||!"com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName())||pager==null||nativeBar==null)return false;
        if(activity.getWindow()==null)return false;
        View root=activity.getWindow().getDecorView();
        if(pager.getRootView()!=root||nativeBar.getRootView()!=root||!pager.isShown()||!pager.isAttachedToWindow()
                ||!nativeBar.isAttachedToWindow()||pager.getWindowToken()==null||!pager.getWindowToken().equals(root.getWindowToken())
                ||!pager.getWindowToken().equals(nativeBar.getWindowToken()))return false;
        ArrayDeque<View> queue=new ArrayDeque<>();queue.add(root);
        Rect visible=new Rect();
        for(int visited=0;!queue.isEmpty()&&visited<1024;visited++){
            View view=queue.removeFirst();
            if(!view.isShown()||view.getAlpha()<=0f)continue;
            if(CHATTING.equals(view.getClass().getName())&&view.getGlobalVisibleRect(visible))return false;
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;if(queue.size()+group.getChildCount()>1024)return false;for(int i=0;i<group.getChildCount();i++)queue.addLast(group.getChildAt(i));}
        }
        return queue.isEmpty();
    }

    private View homeBranch(){
        View pager=home.pager(),nativeBar=home.nativeBar();
        if(pager==null||nativeBar==null)return null;
        for(View current=nativeBar;current!=null;current=parent(current))if(descends(pager,current))return current==pager?parent(current):current;
        return null;
    }

    private String path(View view,int bottom){
        View pager=home.pager(),nativeBar=home.nativeBar(),homeRoot=homeBranch();
        if(pager==null||nativeBar==null||homeRoot==null||bottom<=0||!view.isAttachedToWindow()||!view.isShown()
                ||view.getAlpha()<=0f||view.getRootView()!=pager.getRootView()||view.getWindowToken()==null
                ||!view.getWindowToken().equals(pager.getWindowToken()))return null;
        for(View current=view;current!=null;current=parent(current))if(CHATTING.equals(current.getClass().getName()))return null;
        String relation;
        if(descends(view,pager))relation="pager-descendant";
        else if(descends(pager,view))relation="pager-ancestor";
        else if(homeRoot!=pager.getRootView()&&descends(view,homeRoot))relation="home-sibling";
        else return null;
        Rect visible=new Rect();
        if(!view.getGlobalVisibleRect(visible))return null;
        int[] position=new int[2];view.getLocationInWindow(position);
        Rect strip=new Rect(position[0],position[1]+view.getHeight()-Math.min(bottom,view.getHeight()),position[0]+view.getWidth(),position[1]+view.getHeight());
        pager.getLocationInWindow(position);
        int left=position[0],right=left+pager.getWidth(),end=position[1]+pager.getHeight();
        View overlay=home.overlay();
        int start=end-Math.max(nativeBar.getHeight(),Math.round(96*pager.getResources().getDisplayMetrics().density));
        if(overlay!=null&&overlay.getRootView()==pager.getRootView()&&overlay.getHeight()>0&&parent(overlay)!=null){View parent=parent(overlay);parent.getLocationInWindow(position);start=position[1]+overlay.getTop()-parent.getScrollY();end=Math.max(end,start+overlay.getHeight());}
        if(nativeBar.getHeight()>0){nativeBar.getLocationInWindow(position);int nativeEnd=position[1]+nativeBar.getHeight();if(nativeEnd>start&&position[1]<end){start=Math.min(start,position[1]);end=Math.max(end,nativeEnd);}}
        return strip.left<right&&strip.right>left&&strip.top<end&&strip.bottom>start?relation:null;
    }

    private static View parent(View view){return view.getParent() instanceof View?(View)view.getParent():null;}
    private static boolean descends(View view,View ancestor){for(int depth=0;view!=null&&depth<96;depth++,view=parent(view))if(view==ancestor)return true;return false;}
}
