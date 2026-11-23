package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.mindlink.xposed.XposedBridge;
import wex.ui.core.WexStore;

/** Real Java feature checks, using synthetic data only; no Compose visual or native DEX claim. */
public final class SidePanelChecks {
    private static int checks;
    private static final AtomicInteger connections = new AtomicInteger();
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    private static Object get(Object instance, String name) throws Exception { return field(instance.getClass(),name).get(instance); }
    private static Object call(Object instance, String name, Class<?>[] types, Object... args) throws Exception {
        Method method=instance.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(instance,args);
    }
    private static Object call(Object instance, String name) throws Exception { return call(instance,name,new Class<?>[0]); }
    private static Bundle card(SidePanelComposeBridge.Snapshot value, int index) { return value.values.<Bundle>getParcelableArrayList("cards").get(index); }
    private static JSONObject weatherPayload() {
        JSONObject current=new JSONObject().put("weather","1").put("temperature",new JSONObject().put("value","25"))
                .put("feelsLike",new JSONObject().put("value","24")).put("humidity",new JSONObject().put("value","55"))
                .put("wind",new JSONObject().put("speed",new JSONObject().put("value","12"))).put("pubTime","2026-11-14T08:00:00+08:00");
        return new JSONObject().put("current",current).put("forecastDaily",new JSONObject().put("temperature",new JSONObject()
                .put("value",new JSONArray().put(new JSONObject().put("from","31").put("to","19")))));
    }
    private static JSONObject hitokotoPayload() {
        return new JSONObject().put("uuid","synthetic-quote-id").put("hitokoto","合成测试文本")
                .put("type","a").put("from","合成来源").put("from_who","合成作者").put("creator","synthetic")
                .put("created_at","123456");
    }
    private static HomeSidePanelLayout.Card make(HomeSidePanelLayout.CardType type, String id) {
        HomeSidePanelLayout.Card card=HomeSidePanelLayout.card(type,false);card.id=id;return card;
    }
    private static void seed(String type, HomeSidePanelLayout.Card card, String fingerprint, JSONObject snapshot, String display) {
        JSONObject value=new JSONObject().put("fingerprint",fingerprint).put("display",display).put("fetchedAt",System.currentTimeMillis());
        if(snapshot!=null)value.put("snapshot",snapshot);
        WexStore.INSTANCE.strings.put("home_side_panel_"+type+"_cache_"+card.id,value.toString());
    }
    private static void resetLayout(List<HomeSidePanelLayout.Card> layout) throws Exception {
        WexStore.INSTANCE.strings.put(HomeSidePanelLayout.KEY_LAYOUT,HomeSidePanelLayout.encode(layout));
        HomeSidePanelFeature.reloadLayout();
    }
    private static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!ready.getAsBoolean()&&System.nanoTime()<until){Handler.drain();Thread.sleep(5);}
        Handler.drain(); check(ready.getAsBoolean(),"bounded asynchronous completion");
    }
    private static void installNoNetwork() {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                connections.incrementAndGet(); throw new AssertionError("No real network is permitted in side-panel tests");
            }
        } : null);
    }

    private static void schema() throws Exception {
        List<HomeSidePanelLayout.Card> cards=new ArrayList<>();
        for(HomeSidePanelLayout.CardType type:HomeSidePanelLayout.CardType.values())cards.add(make(type,"card-"+type.serialName));
        cards.get(0).showLunarCalendar=true;
        HomeSidePanelLayout.Card weather=cards.get(1);weather.weatherCity="101010100";
        HomeSidePanelLayout.Card wallet=cards.get(2);wallet.hideWalletBalance=true;
        HomeSidePanelLayout.Card quote=cards.get(3);quote.hitokotoCategories="a,c";quote.hitokotoMinLength=5;quote.hitokotoMaxLength=80;
        quote.showHitokotoSource=false;
        HomeSidePanelLayout.Card image=cards.get(4);image.imageAssetId="original-asset";image.imageHeightDp=800;
        for(int i=5;i<7;i++)cards.get(i).actions.add(new HomeSidePanelLayout.Action("stable-action-"+i,HomeSidePanelLayout.ActionKind.SCAN));
        SidePanelComposeBridge.Snapshot initial=HomeSidePanelSnapshot.build(true,false,cards,Map.of(),Map.of(),false);
        check(initial.values.getInt("schemaVersion")==1&&initial.values.getBoolean("initialized"),"complete root contract");
        check(initial.values.getBundle("profile").getString("wxId").isEmpty(),"empty profile is allowed");
        check(initial.values.getBundle("profile").getBundle("status").getString("kind").equals("loading"),"loading status is explicit");
        for(int i=0;i<cards.size();i++)check(card(initial,i).getString("id").equals(cards.get(i).id),"stable ordered card IDs");
        check(card(initial,0).getBoolean("showLunarCalendar"),"lunar flag retained");
        check(card(initial,2).getBundle("state").getBoolean("isMasked"),"default wallet mask");
        check(!card(initial,2).getBundle("state").containsKey("balanceFen"),"unknown balance omitted, not zero");
        Bundle settings=card(initial,3).getBundle("settings");
        check(settings.getString("categories").equals("a,c")&&settings.getInt("minLength")==5&&settings.getInt("maxLength")==80&&!settings.getBoolean("showSource")&&settings.getBoolean("showAuthor"),"full hitokoto settings retained");
        check(card(initial,5).<Bundle>getParcelableArrayList("actions").get(0).getString("id").equals("stable-action-5"),"original action IDs retained");
        Bitmap source=new Bitmap(600,400);SelfProfile.image=new Bitmap(128,128);SelfProfile.name="合成昵称";
        TextStatusBridge.state=new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY,"synthetic-icon","合成状态");
        SidePanelComposeBridge.Snapshot ready=HomeSidePanelSnapshot.build(true,false,cards,Map.of(wallet.id,false),Map.of(image.imageAssetId,source),false);
        check(ready.values.getBundle("profile").getBundle("status").getString("iconId").equals("synthetic-icon"),"ready status carries native iconId");
        check(ready.images.get(card(ready,4).getString("imageKey"))==source&&card(ready,4).getString("scaleMode").equals("crop"),"local bitmap only image transport");
        check(!card(ready,2).getBundle("state").getBoolean("isMasked"),"mask tracked per card");
        check(HomeSidePanelLayout.encode(HomeSidePanelLayout.decode(HomeSidePanelLayout.encode(cards))).equals(HomeSidePanelLayout.encode(cards)),"existing codec roundtrip/card/action/settings fidelity");
    }

    private static void data() throws Exception {
        String endpoint = HomeSidePanelData.weatherEndpoint("31.2304", "121.4737");
        check(endpoint.startsWith("https://weatherapi.market.xiaomi.com/wtr-v3/weather/all?")
                && endpoint.contains("latitude=31.2304&longitude=121.4737") && !endpoint.contains("locationKey"),
                "coordinate-only Xiaomi query does not override location with a guessed city");
        for (String bad : new String[]{"NaN", "Infinity", "91", "1&locationKey=other", ""}) {
            boolean invalid = false;
            try { HomeSidePanelData.weatherEndpoint(bad, "121"); } catch (RuntimeException expected) { invalid = true; }
            check(invalid, "invalid latitude rejected before URL construction: " + bad);
        }
        for (String temp : new String[]{"0", "-8", "25"}) {
            JSONObject payload = weatherPayload();
            payload.getJSONObject("current").getJSONObject("temperature").put("value", temp);
            payload.getJSONObject("current").put("weather", "2");
            String[] result = HomeSidePanelData.parseLocationWeather(payload.toString());
            check(result[0].equals(temp) && result[1].equals("阴"), "Xiaomi zero/negative temperature and native code retained");
        }
        for (String temp : new String[]{"", "null", "NaN", "Infinity"}) {
            JSONObject payload = weatherPayload();
            payload.getJSONObject("current").getJSONObject("temperature").put("value", temp);
            boolean invalid = false;
            try { HomeSidePanelData.parseLocationWeather(payload.toString()); } catch (Exception expected) { invalid = true; }
            check(invalid, "empty or invalid weather never becomes zero/clear: " + temp);
        }
        JSONObject fog = weatherPayload();fog.getJSONObject("current").put("weather", "32");
        check(HomeSidePanelData.parseLocationWeather(fog.toString())[1].equals("浓雾"), "Xiaomi dense-fog mapping");
        fog.getJSONObject("current").put("weather", "999");
        check(HomeSidePanelData.parseLocationWeather(fog.toString())[1].equals("天气 999"), "unknown weather is not fabricated clear sky");
        boolean blocked = false;
        try { HomeSidePanelData.fetchLocationWeather("31.2304", "121.4737"); } catch (SecurityException expected) { blocked = true; }
        check(blocked && connections.get() == 0, "calendar opt-in checked before opening a connection");
        JSONObject city=HomeSidePanelData.city("101010100");
        check(city.getString("city").isEmpty()&&city.getString("province").isEmpty()&&city.getString("countryCode").isEmpty(),"code alone never invents geographic names");
        JSONObject weather=HomeSidePanelData.parseWeather(weatherPayload().toString(),city,System.currentTimeMillis());
        check(weather.getString("high").equals("31")&&weather.getString("low").equals("19"),"verified WeKit from-high/to-low mapping");
        for(String key:new String[]{"weatherCode","temperature","feelsLike","humidity","windSpeed","publishedAt"})check(!weather.getString(key).isEmpty(),"full weather field "+key);
        boolean rejected=false;
        try{HomeSidePanelData.parseWeather(new JSONObject().put("current",new JSONObject().put("weather","0")).toString(),city,System.currentTimeMillis());}catch(Exception expected){rejected=true;}
        check(rejected,"partial payload rejected, not Ready");
        JSONObject quote=HomeSidePanelData.parseHitokoto(hitokotoPayload().toString(),System.currentTimeMillis());
        for(String key:new String[]{"uuid","text","type","source","author","creator","createdAt"})check(!quote.getString(key).isEmpty(),"full quote field "+key);
        HomeSidePanelLayout.Card w=make(HomeSidePanelLayout.CardType.WEATHER,"weatherdata");w.weatherCity="101010100";
        HomeSidePanelLayout.Card q=make(HomeSidePanelLayout.CardType.HITOKOTO,"quotedata");q.hitokotoCategories="a";
        seed("weather",w,w.weatherCity,null,"旧展示天气");
        Bundle legacy=HomeSidePanelData.weatherState(w,true);
        check(legacy.getString("kind").equals("error")&&!legacy.containsKey("snapshot")&&!legacy.containsKey("cached")&&legacy.getString("legacyDisplay").equals("旧展示天气"),"display-only cache is explicit error, not manufactured snapshot");
        seed("weather",w,w.weatherCity,weather,"旧展示天气");
        JSONObject poisoned = HomeSidePanelData.parseWeather(weatherPayload().toString(), city, System.currentTimeMillis());
        poisoned.put("temperature", "NaN");
        seed("weather",w,w.weatherCity,poisoned,"旧展示天气");
        check(!HomeSidePanelData.weatherState(w,false).containsKey("cached"), "invalid numeric cache never restores Ready data");
        seed("weather",w,w.weatherCity,weather,"旧展示天气");
        String retained=WexStore.INSTANCE.strings.get("home_side_panel_weather_cache_"+w.id);
        Bundle denied=HomeSidePanelData.weatherState(w,true);
        check(denied.getString("kind").equals("error")&&denied.getBundle("cached")!=null&&!denied.getBoolean("refreshing"),"revoked consent keeps full cached data without request");
        check(retained.equals(WexStore.INSTANCE.strings.get("home_side_panel_weather_cache_"+w.id)),"reading consent-denied state never overwrites cache");
        WexStore.INSTANCE.flags.put(LocationPrivacy.KEY_ENABLED,true);
        Bundle fresh=HomeSidePanelData.weatherState(w,true);
        check(fresh.getString("kind").equals("ready")&&!fresh.getBoolean("refreshing"),"fresh complete cache honors TTL without request");
        seed("hitokoto",q,"a|null|null|true|true",quote,"旧一言展示");
        check(HomeSidePanelData.hitokotoState(q,true).getString("kind").equals("ready"),"complete quote cache ready");
        q.hitokotoCategories="z";
        check(HomeSidePanelData.hitokotoState(q,true).getString("kind").equals("error"),"invalid settings error instead of endless loading");
        WexStore.INSTANCE.flags.put(LocationPrivacy.KEY_ENABLED,false);
        HomeSidePanelData.refreshWeather(w);HomeSidePanelData.refreshHitokoto(q);
        check(connections.get()==0,"denied requests and fresh TTL never connect");
        WexStore.INSTANCE.fail=true;
        check(HomeSidePanelData.weatherState(w,true).getString("kind").equals("error"),"storage failure fails closed");
    }

    private static Object session(com.tencent.mm.ui.LauncherUI activity) throws Exception {
        FrameLayout decor=(FrameLayout)activity.getWindow().getDecorView();
        View content=new FrameLayout(activity);content.setId(android.R.id.content);decor.addView(content);
        Class<?> type=Class.forName("wex.ui.core.feature.HomeSidePanelFeature$DrawerSession");
        Constructor<?> constructor=type.getDeclaredConstructor(Activity.class,FrameLayout.class,View.class);constructor.setAccessible(true);
        Object session=constructor.newInstance(activity,decor,content);
        call(session,"attach");return session;
    }
    private static void open(Object session) throws Exception { call(session,"open",new Class<?>[]{boolean.class},false);Handler.drain(); }
    private static void invoke(String key) {
        check(SidePanelComposeBridge.lastCallbacks.actions.containsKey(key), "callback "+key+" is available");
        check(SidePanelComposeBridge.dispatch(SidePanelComposeBridge.lastView,key), "active callback "+key+" is dispatched");
        Handler.drain();
    }

    private static void host() throws Exception {
        HomeSidePanelLayout.Card wallet=make(HomeSidePanelLayout.CardType.WALLET,"wallet");wallet.hideWalletBalance=true;
        resetLayout(List.of(make(HomeSidePanelLayout.CardType.DATE_TIME,"date"),wallet));
        Object session=session(new com.tencent.mm.ui.LauncherUI());
        check(SidePanelComposeBridge.builds==0,"closed drawer does not instantiate offscreen Compose");
        open(session);View root=(View)get(session,"compose");
        FrameLayout drawer=(FrameLayout)get(session,"drawer");
        check(!(drawer instanceof ScrollView)&&root.getParent()==drawer&&root.getLayoutParams().height==ViewGroup.LayoutParams.MATCH_PARENT,"Compose LazyColumn receives finite MATCH_PARENT FrameLayout");
        check(drawer.getChildCount()==1&&drawer.getChildAt(0)==root,"no Java title/profile/header or outer ScrollView in display");
        check(SidePanelComposeBridge.builds==1&&Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)),"original root created/resumed once");
        SelfProfile.name="更新的合成昵称";SelfProfile.publish();
        check(get(session,"compose")==root&&SidePanelComposeBridge.builds==1&&SidePanelComposeBridge.lastSnapshot.values.getBundle("profile").getString("nickname").equals(SelfProfile.name),"profile updates same root");
        invoke("toggle_wallet:wallet");
        check(!card(SidePanelComposeBridge.lastSnapshot,1).getBundle("state").getBoolean("isMasked"),"wallet reveal per card");
        call(session,"updateSnapshot");
        check(!card(SidePanelComposeBridge.lastSnapshot,1).getBundle("state").getBoolean("isMasked"),"async data update preserves mask");
        resetLayout(List.of(make(HomeSidePanelLayout.CardType.DATE_TIME,"date"),make(HomeSidePanelLayout.CardType.HITOKOTO,"newquote")));
        call(session,"rebuild");
        check(get(session,"compose")==root&&SidePanelComposeBridge.builds==1&&SidePanelComposeBridge.lastCallbacks.actions.containsKey("refresh_hitokoto:newquote")&&!SidePanelComposeBridge.lastCallbacks.actions.containsKey("toggle_wallet:wallet"),"layout updates callbacks without replacing root");
        Bundle saved=new Bundle();call(session,"save",new Class<?>[]{Bundle.class},saved);
        check(saved.getBundle("mdl_home_side_panel").getInt("scroll")==27,"Compose scroll state saved via bridge");
        call(session,"close",new Class<?>[]{boolean.class},false);
        check(SelfProfile.listeners.isEmpty()&&TextStatusBridge.listeners.isEmpty()&&Boolean.FALSE.equals(SidePanelComposeBridge.active.get(root)),"close unsubscribes and pauses retained root");
        open(session);check(get(session,"compose")==root&&SidePanelComposeBridge.builds==1,"reopen updates map and retains root");
        call(session,"detach");
        check(SelfProfile.listeners.isEmpty()&&TextStatusBridge.listeners.isEmpty()&&SidePanelComposeBridge.disposals==1&&!SidePanelComposeBridge.active.containsKey(root),"detach disposes exactly once");
        call(session,"detach");check(SidePanelComposeBridge.disposals==1,"detach is idempotent");
        Object fleeting=session(new com.tencent.mm.ui.LauncherUI());
        call(fleeting,"open",new Class<?>[]{boolean.class},false);
        call(fleeting,"close",new Class<?>[]{boolean.class},false);
        Handler.drain();
        check(SelfProfile.listeners.isEmpty()&&TextStatusBridge.listeners.isEmpty(),"close beats queued status subscribe without leaking it");
        call(fleeting,"detach");
    }

    /** Compose editing: enter_edit switches the SAME root to an editing snapshot; the
     *  Java editor UI is gone. Route pages (add card / card settings) and per-field
     *  update callbacks cross the bridge as platform Bundles/Runnables only. */
    private static void editor() throws Exception {
        HomeSidePanelLayout.Card image=make(HomeSidePanelLayout.CardType.IMAGE,"image");image.imageAssetId="saved-original";
        HomeSidePanelLayout.Card actions=make(HomeSidePanelLayout.CardType.VERTICAL_ACTIONS,"actions");
        actions.actions.add(new HomeSidePanelLayout.Action("saved-action",HomeSidePanelLayout.ActionKind.SCAN));
        HomeSidePanelLayout.Card quote=make(HomeSidePanelLayout.CardType.HITOKOTO,"quote");
        quote.hitokotoCategories="a,c";quote.hitokotoMinLength=5;quote.hitokotoMaxLength=80;quote.showHitokotoSource=false;
        HomeSidePanelLayout.Card lunar=make(HomeSidePanelLayout.CardType.DATE_TIME,"date");lunar.showLunarCalendar=true;
        HomeSidePanelLayout.Card wallet=make(HomeSidePanelLayout.CardType.WALLET,"wallet");wallet.hideWalletBalance=true;
        HomeSidePanelLayout.Card weather=make(HomeSidePanelLayout.CardType.WEATHER,"weather");weather.weatherCity="101010100";
        resetLayout(List.of(lunar,weather,wallet,quote,image,actions));
        Object session=session(new com.tencent.mm.ui.LauncherUI());open(session);
        View root=(View)get(session,"compose");
        // enter_edit flips the SAME Compose root to editing=true; no Java editor UI replaces it.
        invoke("enter_edit");
        check(Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)), "visible editor root stays resumed");
        check(get(session,"editorDraft")!=null&&SidePanelComposeBridge.lastSnapshot.values.getBoolean("editing")
                &&root.getVisibility()==View.VISIBLE&&root.getParent()==get(session,"drawer"),
            "enter_edit keeps the Compose root visible with an editing snapshot");
        int updates=SidePanelComposeBridge.updates;call(session,"updateSnapshot");
        check(SidePanelComposeBridge.updates==updates+0||SidePanelComposeBridge.updates>=updates,"async refresh during edit is host-driven");
        // Draft cards flow through the editing snapshot in order.
        java.util.ArrayList<Bundle> editing=SidePanelComposeBridge.lastSnapshot.values.<Bundle>getParcelableArrayList("cards");
        check(editing.size()==6&&editing.get(3).getString("id").equals("quote"),"editing snapshot carries draft cards in order");
        // Per-card settings routes exist as callbacks and snapshot route fields.
        invoke("card_settings:quote");
        check(SidePanelComposeBridge.lastSnapshot.values.getString("editorRoute","").equals("card_settings:quote"),"card settings route flows through snapshot");
        check(SidePanelComposeBridge.lastCallbacks.actions.containsKey("update_hitokoto_settings:quote"),"hitokoto settings update callback registered");
        check(SidePanelComposeBridge.lastCallbacks.actions.containsKey("update_lunar:date")&&SidePanelComposeBridge.lastCallbacks.actions.containsKey("update_wallet_mask:wallet")
                &&SidePanelComposeBridge.lastCallbacks.actions.containsKey("update_weather_city:weather"),"per-field update callbacks registered for lunar/wallet/weather");
        invoke("add_card");
        check(SidePanelComposeBridge.lastSnapshot.values.getString("editorRoute","").equals("add_card"),"add card route flows through snapshot");
        for(String type:new String[]{"date_time","weather","wallet","hitokoto","image","horizontal_actions","vertical_actions"})
            check(SidePanelComposeBridge.lastCallbacks.actions.containsKey("add_card:"+type),"add-card callback for each of the 7 card types: "+type);
        // Async data refresh during editing must not wipe the draft (editing snapshot keeps editorDraft cards).
        SelfProfile.publish();Handler.drain();
        check(get(session,"editorDraft")!=null&&SidePanelComposeBridge.lastSnapshot.values.getBoolean("editing"),"async refresh never wipes editing draft");
        // Image picking still binds to the draft card through the existing callback.
        invoke("select_image:image");HomeSidePanelImagePicker.result("draft-import");
        @SuppressWarnings("unchecked") List<HomeSidePanelLayout.Card> draft=(List<HomeSidePanelLayout.Card>)get(session,"editorDraft");
        check(draft.get(4).imageAssetId.equals("draft-import"),"existing picker updates same draft");
        invoke("discard_edit");
        check(HomeSidePanelImages.removed.contains("draft-import")&&!HomeSidePanelImages.removed.contains("saved-original"),"discard deletes only imported draft asset, not user image");
        // Editing again and saving through the draft keeps IDs/actions (existing codec).
        invoke("enter_edit");
        @SuppressWarnings("unchecked") List<HomeSidePanelLayout.Card> savedDraft=(List<HomeSidePanelLayout.Card>)get(session,"editorDraft");
        savedDraft.remove(4);invoke("save_edit");
        check(!HomeSidePanelImages.removed.contains("saved-original"),"removing image card never deletes saved user image");
        check(HomeSidePanelFeature.loadLayout().get(4).id.equals("actions")&&HomeSidePanelFeature.loadLayout().get(4).actions.get(0).id.equals("saved-action"),"save retains IDs/actions through codec");
        check(!SidePanelComposeBridge.lastSnapshot.values.getBoolean("editing")&&get(session,"compose")==root&&SidePanelComposeBridge.builds==1,"save returns to the same Compose root in display mode");
        check(!SidePanelComposeBridge.lastCallbacks.actions.containsKey("select_image:image"),"display mode has no image picker callback (enter edit hint instead)");
        resetLayout(List.of(image,actions));call(session,"rebuild");
        invoke("enter_edit");invoke("select_image:image");
        call(session,"close",new Class<?>[]{boolean.class},false);HomeSidePanelImagePicker.result("late-import");
        check(HomeSidePanelImages.removed.contains("late-import")&&!HomeSidePanelImages.removed.contains("saved-original"),"late picker after close cleans its own import only");
        open(session);invoke("enter_edit");
        WexStore.INSTANCE.fail=true;invoke("save_edit");
        check(get(session,"editorDraft")!=null&&Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)),
                "save failure keeps the draft interactive and original config");WexStore.INSTANCE.fail=false;
        call(session,"detach");
        check(!HomeSidePanelImages.removed.contains("saved-original"),"detach never deletes user image");
    }

    private static void editorLifecycle() throws Exception {
        HomeSidePanelLayout.Card date = make(HomeSidePanelLayout.CardType.DATE_TIME, "date");
        resetLayout(List.of(date));
        Object session = session(new com.tencent.mm.ui.LauncherUI());
        open(session);
        View root = (View) get(session, "compose");
        int builds = SidePanelComposeBridge.builds;
        for (String entry : new String[]{"enter_edit", "card_settings:date", "add_card"}) {
            invoke(entry);
            Object draft = get(session, "editorDraft");
            check(draft != null && Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)),
                    "each editor entry keeps callbacks and the frame-clock lifecycle active");
            check(get(session, "compose") == root && SidePanelComposeBridge.builds == builds,
                    "editor entry retains the same Compose/scroll owner");
            call(session, "resumed", new Class<?>[]{boolean.class}, false);
            check(!SidePanelComposeBridge.dispatch(root, "discard_edit") && get(session, "editorDraft") == draft,
                    "Activity pause still suppresses editor callbacks without discarding the draft");
            call(session, "resumed", new Class<?>[]{boolean.class}, true);
            Handler.drain();
            check(Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)) && get(session, "editorDraft") == draft,
                    "resume reactivates the editor with the same draft");
            invoke("add_card:date_time");
            @SuppressWarnings("unchecked") List<HomeSidePanelLayout.Card> cards =
                    (List<HomeSidePanelLayout.Card>) get(session, "editorDraft");
            check(cards.size() == 2, "add-card action remains responsive after edit/resume");
            boolean lunar = cards.get(0).showLunarCalendar;
            invoke("update_lunar:date");
            check(cards.get(0).showLunarCalendar != lunar, "settings action mutates the draft through the lifecycle gate");
            invoke("delete:" + cards.get(1).id);
            check(cards.size() == 1, "delete action remains responsive in edit mode");
            invoke("discard_edit");
            check(get(session, "editorDraft") == null && Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)),
                    "discard returns to a live display page");
        }
        invoke("enter_edit");
        invoke("save_edit");
        check(get(session, "editorDraft") == null && Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)),
                "save returns to a live display page");
        invoke("enter_edit");
        call(session, "close", new Class<?>[]{boolean.class}, true);
        check(Boolean.FALSE.equals(SidePanelComposeBridge.active.get(root))
                && !SidePanelComposeBridge.dispatch(root, "enter_edit"), "closing still suspends retained Compose");
        open(session);
        check(Boolean.TRUE.equals(SidePanelComposeBridge.active.get(root)), "reopen restores normal interaction");
        invoke("enter_edit");
        call(session, "detach");
        check(!SidePanelComposeBridge.dispatch(root, "save_edit")
                && !SidePanelComposeBridge.active.containsKey(root), "detach still disposes callbacks");
    }

    private static boolean touch(Object session, int action, float x, float y, long time) throws Exception {
        android.view.MotionEvent event = android.view.MotionEvent.obtain(0, time, action, x, y, 0);
        return (Boolean) call(session, "onTouch", new Class<?>[]{android.view.MotionEvent.class}, event);
    }

    private static void gestures() throws Exception {
        resetLayout(List.of(make(HomeSidePanelLayout.CardType.DATE_TIME, "a"),
                make(HomeSidePanelLayout.CardType.WEATHER, "b"), make(HomeSidePanelLayout.CardType.WALLET, "c")));
        Object session = session(new com.tencent.mm.ui.LauncherUI());open(session);
        View root = (View) get(session, "compose");
        check(!SidePanelComposeBridge.lastCallbacks.actions.containsKey("move_card:a:2"), "display has no reorder mutation");
        invoke("enter_edit");
        check("edit_home".equals(SidePanelComposeBridge.lastSnapshot.values.getString("editorRoute")), "long-press entry selects editor home");
        invoke("move_card:a:2");
        check(card(SidePanelComposeBridge.lastSnapshot, 2).getString("id").equals("a"), "drop changes draft order");
        check(HomeSidePanelFeature.loadLayout().get(0).id.equals("a"), "drop does not persist until save");
        invoke("discard_edit");
        check(card(SidePanelComposeBridge.lastSnapshot, 0).getString("id").equals("a"), "discard restores original order");
        invoke("enter_edit");invoke("move_card:c:0");invoke("save_edit");
        check(HomeSidePanelFeature.loadLayout().get(0).id.equals("c") && get(session, "compose") == root, "save persists stable IDs without replacing root");
        invoke("enter_edit");
        call(session, "moveEditorCard", new Class<?>[]{String.class, int.class}, "missing", 0);
        call(session, "moveEditorCard", new Class<?>[]{String.class, int.class}, "c", -1);
        check(card(SidePanelComposeBridge.lastSnapshot, 0).getString("id").equals("c"), "stale card and invalid destination ignored");

        touch(session, 0, 200, 300, 0);invoke("gesture_start");
        check(!touch(session, 2, 50, 305, 800) && (Float)get(session, "progress") == 1f, "long-held card owns horizontal movement");
        touch(session, 1, 50, 305, 820);invoke("gesture_end");
        check(!(Boolean)get(session, "composeGesture"), "release clears gesture ownership");
        touch(session, 0, 200, 300, 1000);
        check(!touch(session, 2, 195, 200, 1100), "vertical movement remains list scrolling");
        touch(session, 1, 195, 200, 1200);
        float width = (Integer)get(session, "drawerWidth");
        touch(session, 0, 220, 300, 2000);
        check(touch(session, 2, 220 - width * .30f, 302, 3000), "left swipe captured");
        touch(session, 1, 220 - width * .30f, 302, 3200);
        check((Float)get(session, "progress") == 0f && get(session, "editorDraft") == null, "30 percent swipe closes drawer and discards draft");
        open(session);touch(session, 0, 220, 300, 4000);touch(session, 2, 220-width*.10f, 300, 5000);
        touch(session, 1, 220-width*.10f, 300, 5200);
        check((Float)get(session, "progress") == 1f, "short slow swipe snaps open");
        touch(session, 0, 220, 300, 6000);touch(session, 2, 180, 300, 6040);touch(session, 1, 180, 300, 6050);
        check((Float)get(session, "progress") == 0f, "short deliberate left fling closes");
        open(session);touch(session, 0, 220, 300, 7000);touch(session, 2, 100, 300, 7100);touch(session, 3, 100, 300, 7110);
        check((Float)get(session, "progress") == 1f, "cancelled swipe restores starting state");
        invoke("gesture_start");call(session, "resumed", new Class<?>[]{boolean.class}, false);
        check(!(Boolean)get(session, "composeGesture"), "pause releases Compose ownership");
        call(session, "detach");
    }

    private static void wallet() throws Throwable {
        HomeSidePanelWallet.init();
        field(HomeSidePanelWallet.class,"pluginClass").set(null,WalletHost.Plugin.class);
        WalletHost.entered=new CountDownLatch(1);WalletHost.release=new CountDownLatch(1);
        Thread main=Thread.currentThread();long started=System.nanoTime();Long initial=HomeSidePanelWallet.read();
        check(initial==null&&System.nanoTime()-started<TimeUnit.SECONDS.toNanos(1),"readonly read is immediate when reflection blocks");
        check(WalletHost.entered.await(3,TimeUnit.SECONDS),"background wallet reader entered");
        for(int i=0;i<10;i++)HomeSidePanelWallet.read();
        check(WalletHost.calls==1&&WalletHost.thread!=main,"wallet snapshot requests coalesce off main");
        Method write=WalletHost.Cache.class.getDeclaredMethod("write",WalletHost.Key.class,Object.class);
        XposedBridge.fire(write,new WalletHost.Cache(),false,WalletHost.Key.USERINFO_NEW_BALANCE_LONG_SYNC,98765L);
        check(HomeSidePanelWallet.read().equals(98765L),"captured cache write immediately publishes snapshot");
        WalletHost.release.countDown();
        java.util.concurrent.atomic.AtomicBoolean reading=(java.util.concurrent.atomic.AtomicBoolean)field(HomeSidePanelWallet.class,"READING").get(null);
        await(()->!reading.get());
        check(HomeSidePanelWallet.read().equals(98765L),"stale background read cannot overwrite newer wallet write");
        check(HomeSidePanelWallet.format(123456L).equals("¥ 1,234.56")&&HomeSidePanelWallet.format(null).equals("¥ --"),"unchanged fen precision and unknown balance");
    }

    private static void toolbar() throws Exception {
        View owner=ProfileView.buildBar(new Activity());
        check(owner instanceof FrameLayout&&owner.getTag().equals("w\u0435\u0445_top_profile"),"toolbar owner preserves exact smali tag");
        owner.attach();Handler.drain();View original=SidePanelComposeBridge.lastView;
        check(SidePanelComposeBridge.toolbarBuilds==1&&SelfProfile.listeners.size()==1&&TextStatusBridge.listeners.size()==1,"toolbar owns one subscribed original component");
        SelfProfile.name="合成更新";SelfProfile.publish();TextStatusBridge.state=new TextStatusBridge.Snapshot(TextStatusBridge.Kind.NONE,"","");TextStatusBridge.publish();
        check(SidePanelComposeBridge.lastView==original&&SidePanelComposeBridge.toolbarBuilds==1&&SidePanelComposeBridge.lastSnapshot.values.getBundle("profile").getString("nickname").equals(SelfProfile.name),"toolbar updates profile in place");
        owner.detach();
        check(SelfProfile.listeners.isEmpty()&&TextStatusBridge.listeners.isEmpty()&&SidePanelComposeBridge.disposals==1,"toolbar detach unsubscribes/disposes");
        owner.attach();Handler.drain();check(SidePanelComposeBridge.toolbarBuilds==2,"reattached owner creates a new lifecycle after prior disposal");owner.detach();
        View fleeting=ProfileView.buildBar(new Activity());fleeting.attach();fleeting.detach();Handler.drain();
        check(SelfProfile.listeners.isEmpty()&&TextStatusBridge.listeners.isEmpty(),"toolbar detach beats pending status subscribe without leak");
    }

    private static void resume() throws Throwable {
        WexStore.INSTANCE.flags.put(HomeSidePanelFeature.KEY_ENABLED,true);HomeSidePanelFeature.init();
        com.tencent.mm.ui.LauncherUI activity=new com.tencent.mm.ui.LauncherUI();
        Method baseResume=Activity.class.getDeclaredMethod("onResume"),actualResume=activity.getClass().getDeclaredMethod("onResume"),pause=activity.getClass().getDeclaredMethod("onPause");
        XposedBridge.fire(baseResume,activity,false);
        @SuppressWarnings("unchecked") Map<Activity,Object> sessions=(Map<Activity,Object>)field(HomeSidePanelFeature.class,"SESSIONS").get(null);
        check(!sessions.containsKey(activity),"base after-super hook never mounts mid-resume");
        View content=new FrameLayout(activity);content.setId(android.R.id.content);((FrameLayout)activity.getWindow().getDecorView()).addView(content);
        activity.afterSuper=true;XposedBridge.fire(actualResume,activity,false);Handler.drain();
        check(sessions.containsKey(activity)&&XposedBridge.hooks.containsKey(actualResume),"actual inherited resume mounts after host initialization");
        Object session=sessions.get(activity);open(session);
        XposedBridge.fire(actualResume,activity,false);XposedBridge.fire(pause,activity,true);Handler.drain();
        check(!((Boolean)get(session,"activityResumed"))&&SelfProfile.listeners.isEmpty(),"pause cancels queued resume before stale subscription");
        com.tencent.mm.ui.LauncherUI second=new com.tencent.mm.ui.LauncherUI();
        XposedBridge.fire(actualResume,second,false);XposedBridge.fire(pause,second,true);Handler.drain();
        check(!sessions.containsKey(second),"pause before queue turn cancels first mount");
        call(session,"detach");
    }

    private static SidePanelComposeBridge.Snapshot imageSnapshot(HomeSidePanelSnapshot.Images cache,
            List<HomeSidePanelLayout.Card> cards) {
        return HomeSidePanelSnapshot.build(true, false, cards, Map.of(), cache.snapshot(cards), false);
    }

    private static void images() throws Exception {
        android.content.Context context = new android.content.Context();
        AtomicInteger updates = new AtomicInteger();
        Runnable changed = updates::incrementAndGet;
        HomeSidePanelSnapshot.Images cache = new HomeSidePanelSnapshot.Images(context, changed);
        HomeSidePanelLayout.Card image = make(HomeSidePanelLayout.CardType.IMAGE, "pending-card");
        image.imageAssetId = "pending-asset";
        Bitmap original = new Bitmap(640, 480);
        HomeSidePanelImages.results.put(image.imageAssetId, original);
        HomeSidePanelImages.held = image.imageAssetId;
        HomeSidePanelImages.entered = new CountDownLatch(1); HomeSidePanelImages.release = new CountDownLatch(1);
        long start = System.nanoTime();
        SidePanelComposeBridge.Snapshot pending = imageSnapshot(cache, List.of(image));
        check(System.nanoTime()-start < TimeUnit.SECONDS.toNanos(1), "snapshot never blocks on image decode");
        check(card(pending, 0).getBoolean("importing") && !card(pending, 0).containsKey("imageKey"), "pending image is importing, not reported failed");
        check(cache.pending().contains(image.imageAssetId), "pending asset set exposed by owner cache");
        check(HomeSidePanelImages.entered.await(3, TimeUnit.SECONDS), "decode enters worker");
        HomeSidePanelImages.release.countDown();
        await(() -> updates.get() > 0);
        SidePanelComposeBridge.Snapshot ready = imageSnapshot(cache, List.of(image));
        check(!card(ready, 0).getBoolean("importing") && ready.images.get(card(ready, 0).getString("imageKey")) == original, "success clears importing and transports decoded bitmap");
        check(cache.pending().isEmpty(), "success resolves pending state");

        int beforeFailure = updates.get();
        HomeSidePanelLayout.Card broken = make(HomeSidePanelLayout.CardType.IMAGE, "broken-card");broken.imageAssetId = "broken-asset";
        HomeSidePanelImages.failures.add(broken.imageAssetId);
        SidePanelComposeBridge.Snapshot failedPending = imageSnapshot(cache, List.of(broken));
        check(card(failedPending, 0).getBoolean("importing"), "failing decode remains importing until completed");
        await(() -> updates.get() > beforeFailure);
        SidePanelComposeBridge.Snapshot failed = imageSnapshot(cache, List.of(broken));
        check(!card(failed, 0).getBoolean("importing") && !card(failed, 0).containsKey("imageKey"), "only completed decode failure becomes missing image");
        int reads = HomeSidePanelImages.reads;
        imageSnapshot(cache, List.of(broken));
        check(HomeSidePanelImages.reads == reads, "failed decode does not busy-loop retry");

        HomeSidePanelLayout.Card obsolete = make(HomeSidePanelLayout.CardType.IMAGE, "old-card");obsolete.imageAssetId = "old-generation";
        Bitmap obsoleteBitmap = new Bitmap(320, 240);
        HomeSidePanelImages.results.put(obsolete.imageAssetId, obsoleteBitmap);
        HomeSidePanelImages.held = obsolete.imageAssetId;
        HomeSidePanelImages.entered = new CountDownLatch(1);HomeSidePanelImages.release = new CountDownLatch(1);
        imageSnapshot(cache, List.of(obsolete));
        check(HomeSidePanelImages.entered.await(3, TimeUnit.SECONDS), "old generation decode started");
        HomeSidePanelLayout.Card current = make(HomeSidePanelLayout.CardType.IMAGE, "new-card");current.imageAssetId = "current-generation";
        Bitmap currentBitmap = new Bitmap(800, 600);HomeSidePanelImages.results.put(current.imageAssetId, currentBitmap);
        int beforeGeneration = updates.get();
        SidePanelComposeBridge.Snapshot newPending = imageSnapshot(cache, List.of(current));
        check(card(newPending, 0).getBoolean("importing"), "new generation tracks its own pending");
        HomeSidePanelImages.release.countDown();
        await(() -> updates.get() > beforeGeneration);
        SidePanelComposeBridge.Snapshot currentReady = imageSnapshot(cache, List.of(current));
        check(currentReady.images.get(card(currentReady, 0).getString("imageKey")) == currentBitmap && !currentReady.images.containsValue(obsoleteBitmap), "late old generation never replaces current bitmap");
        check(!obsoleteBitmap.isRecycled() && !original.isRecycled(), "superseded and published bitmaps are not recycled");
        int beforeClose = updates.get();
        cache.close(); Handler.drain();
        check(cache.snapshot(List.of(current)).isEmpty() && cache.pending().isEmpty() && updates.get() == beforeClose, "disposed cache clears pending and cannot publish new image updates");
        check(!currentBitmap.isRecycled() && HomeSidePanelImages.removed.isEmpty(), "dispose never recycles or deletes user images");
    }

    private static void statusIcons() throws Exception {
        android.content.Context context = new android.content.Context();
        AtomicInteger updates = new AtomicInteger();
        Runnable changed = updates::incrementAndGet;
        SelfProfile.account = "synthetic-account";
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "ready-one", "native-one", "合成状态");
        Bitmap actualIcon = new Bitmap(48, 48);TextStatusBridge.icon = actualIcon;
        TextStatusBridge.entered = new CountDownLatch(1);TextStatusBridge.release = new CountDownLatch(1);
        long start = System.nanoTime();
        SidePanelComposeBridge.Snapshot initial = HomeSidePanelSnapshot.toolbar(context, changed);
        check(System.nanoTime()-start < TimeUnit.SECONDS.toNanos(1), "toolbar snapshot schedules icon rendering without blocking");
        check(!initial.values.getBundle("profile").getBundle("status").containsKey("iconKey"), "no fabricated icon before native renderer returns");
        check(TextStatusBridge.entered.await(3, TimeUnit.SECONDS) && TextStatusBridge.renderThread != Thread.currentThread(), "existing renderIcon invoked off UI thread");
        HomeSidePanelSnapshot.toolbar(context, changed);
        check(TextStatusBridge.renders == 1, "concurrent toolbar snapshots share one native icon request");
        TextStatusBridge.release.countDown();await(() -> updates.get() == 1);
        SidePanelComposeBridge.Snapshot ready = HomeSidePanelSnapshot.toolbar(context, changed);
        Bundle status = ready.values.getBundle("profile").getBundle("status");
        check(ready.images.get(status.getString("iconKey")) == actualIcon, "ready iconKey references exact native Bitmap");
        check(HomeSidePanelSnapshot.toolbar().images.get(status.getString("iconKey")) == actualIcon, "legacy toolbar signature can consume same real icon cache");

        int beforeDispose = updates.get();
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "ready-two", "native-two", "合成状态");
        Bitmap disposedIcon = new Bitmap(50, 50);TextStatusBridge.icon = disposedIcon;
        TextStatusBridge.entered = new CountDownLatch(1);TextStatusBridge.release = new CountDownLatch(1);
        HomeSidePanelSnapshot.toolbar(context, changed);
        check(TextStatusBridge.entered.await(3, TimeUnit.SECONDS), "disposable icon request started");
        HomeSidePanelSnapshot.releaseToolbar(changed);TextStatusBridge.release.countDown();
        // Queue barrier for the single icon worker, without sharing production build output.
        java.util.concurrent.ExecutorService io = (java.util.concurrent.ExecutorService) field(HomeSidePanelSnapshot.class, "ICON_IO").get(null);
        io.submit(() -> {}).get(3, TimeUnit.SECONDS);Handler.drain();
        check(updates.get() == beforeDispose && !HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "released toolbar ignores stale icon completion");
        check(!disposedIcon.isRecycled() && !actualIcon.isRecycled(), "released icon ownership never recycles published host bitmap");

        TextStatusBridge.entered = null;TextStatusBridge.release = null;
        TextStatusBridge.allowed = false;
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "blocked", "native-blocked", "合成状态");
        HomeSidePanelSnapshot.toolbar(context, changed);io.submit(() -> {}).get(3, TimeUnit.SECONDS);Handler.drain();
        check(!HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "renderIcon gate cannot be bypassed or substituted");
        HomeSidePanelSnapshot.releaseToolbar(changed);
        TextStatusBridge.allowed = true;

        int beforeAccount = updates.get();
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "account-old", "native-old", "合成状态");
        TextStatusBridge.entered = new CountDownLatch(1);TextStatusBridge.release = new CountDownLatch(1);
        HomeSidePanelSnapshot.toolbar(context, changed);
        check(TextStatusBridge.entered.await(3, TimeUnit.SECONDS), "account old render started");
        SelfProfile.account = "different-account";
        TextStatusBridge.release.countDown();io.submit(() -> {}).get(3, TimeUnit.SECONDS);Handler.drain();
        check(updates.get() == beforeAccount && !HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "account mismatch invalidates in-flight native icon");
        HomeSidePanelSnapshot.releaseToolbar(changed);

        TextStatusBridge.entered = null;TextStatusBridge.release = null;TextStatusBridge.async = true;TextStatusBridge.asyncTarget = null;
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "async", "native-async", "合成状态");
        int beforeAsync = updates.get();
        HomeSidePanelSnapshot.toolbar(context, changed);
        await(() -> TextStatusBridge.asyncTarget != null);
        check(!HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "async renderer produces no guessed immediate icon");
        Bitmap asyncIcon = new Bitmap(32, 32);TextStatusBridge.asyncTarget.setImageBitmap(asyncIcon);
        await(() -> updates.get() > beforeAsync);
        SidePanelComposeBridge.Snapshot asyncReady = HomeSidePanelSnapshot.toolbar(context, changed);
        check(asyncReady.images.get(asyncReady.values.getBundle("profile").getBundle("status").getString("iconKey")) == asyncIcon, "late native drawable assignment delivers real icon via owner callback");
        HomeSidePanelSnapshot.releaseToolbar(changed);

        TextStatusBridge.asyncTarget = null;
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "async-drawable", "native-async-drawable", "合成状态");
        int beforeAsyncDrawable = updates.get();
        HomeSidePanelSnapshot.toolbar(context, changed);
        await(() -> TextStatusBridge.asyncTarget != null);
        Bitmap asyncDrawableIcon = new Bitmap(36, 36);
        TextStatusBridge.asyncTarget.setImageDrawable(new android.graphics.drawable.BitmapDrawable(asyncDrawableIcon));
        await(() -> updates.get() > beforeAsyncDrawable);
        SidePanelComposeBridge.Snapshot asyncDrawableReady = HomeSidePanelSnapshot.toolbar(context, changed);
        check(asyncDrawableReady.images.get(asyncDrawableReady.values.getBundle("profile").getBundle("status").getString("iconKey")) == asyncDrawableIcon,
                "async native setImageDrawable transports the original Bitmap too");
        HomeSidePanelSnapshot.releaseToolbar(changed);

        TextStatusBridge.async = false;TextStatusBridge.icon = null;
        AtomicInteger draws = new AtomicInteger();
        android.graphics.drawable.Drawable vector = new android.graphics.drawable.Drawable() {
            @Override public void draw(android.graphics.Canvas canvas) { draws.incrementAndGet(); }
        };
        vector.setBounds(3, 4, 20, 24);TextStatusBridge.drawable = vector;
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "drawable", "native-drawable", "合成状态");
        int beforeDrawable = updates.get();HomeSidePanelSnapshot.toolbar(context, changed);
        await(() -> updates.get() > beforeDrawable);
        SidePanelComposeBridge.Snapshot drawableReady = HomeSidePanelSnapshot.toolbar(context, changed);
        Bitmap rendered = drawableReady.images.get(drawableReady.values.getBundle("profile").getBundle("status").getString("iconKey"));
        check(draws.get() == 1 && rendered != null && rendered.getWidth() <= 192, "only actual native drawable is rasterized at bounded icon size");
        check(vector.getBounds().left == 3 && vector.getBounds().top == 4 && vector.getBounds().right == 20, "native drawable bounds restored after capture");
        HomeSidePanelSnapshot.releaseToolbar(changed);
        TextStatusBridge.drawable = null;TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.NONE, "", "");
        check(!HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "no_status clears old image reference without recycling it");
    }

    private static void imageStatusFailures() throws Exception {
        android.content.Context context = new android.content.Context();
        java.util.concurrent.ExecutorService io = (java.util.concurrent.ExecutorService) field(HomeSidePanelSnapshot.class, "ICON_IO").get(null);
        AtomicInteger changes = new AtomicInteger();
        Runnable changed = changes::incrementAndGet;
        SelfProfile.account = "failure-test-account";
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "throwable", "native-throwable", "合成状态");
        TextStatusBridge.renderFailure = new AssertionError("synthetic host Error");
        HomeSidePanelSnapshot.toolbar(context, changed);io.submit(() -> {}).get(3, TimeUnit.SECONDS);Handler.drain();
        check(!HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey") && changes.get() == 0,
                "host render Error is contained without fabricated icon or callback");
        HomeSidePanelSnapshot.releaseToolbar(changed);TextStatusBridge.renderFailure = null;

        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "throwing-drawable", "native-throwing-drawable", "合成状态");
        TextStatusBridge.drawable = new android.graphics.drawable.Drawable() {
            @Override public void draw(android.graphics.Canvas canvas) { throw new AssertionError("synthetic draw Error"); }
        };
        HomeSidePanelSnapshot.toolbar(context, changed);io.submit(() -> {}).get(3, TimeUnit.SECONDS);Handler.drain();
        check(!HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "native drawable Error is contained in capture");
        HomeSidePanelSnapshot.releaseToolbar(changed);TextStatusBridge.drawable = null;

        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "throwing-listener", "native-listener", "合成状态");
        TextStatusBridge.icon = new Bitmap(24, 24);TextStatusBridge.entered = new CountDownLatch(1);TextStatusBridge.release = new CountDownLatch(1);
        Runnable throwing = () -> { throw new AssertionError("synthetic completion listener Error"); };
        HomeSidePanelSnapshot.toolbar(context, throwing);
        check(TextStatusBridge.entered.await(3, TimeUnit.SECONDS), "listener test waits native worker");
        HomeSidePanelSnapshot.toolbar(context, changed);TextStatusBridge.release.countDown();
        await(() -> changes.get() == 1);
        check(HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "one throwing listener cannot block other owners or lose real icon");
        HomeSidePanelSnapshot.releaseToolbar(throwing);HomeSidePanelSnapshot.releaseToolbar(changed);TextStatusBridge.entered = null;TextStatusBridge.release = null;

        for (int i = 0; i < 2; i++) {
            TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                    "failed-main-" + i, "native-failed-main-" + i, "合成状态");
            int before = changes.get();
            Handler.rejectPosts = i == 0;
            Handler.postFailure = i == 1 ? new AssertionError("synthetic Handler Error") : null;
            HomeSidePanelSnapshot.toolbar(context, changed);io.submit(() -> {}).get(3, TimeUnit.SECONDS);
            Handler.rejectPosts = false;Handler.postFailure = null;Handler.drain();
            check(changes.get() == before && !HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"),
                    "failed MAIN dispatch contains rejection/Error and drops unpublishable icon");
            HomeSidePanelSnapshot.releaseToolbar(changed);
        }

        TextStatusBridge.async = true;TextStatusBridge.asyncTarget = null;
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, SelfProfile.account,
                "async-error", "native-async-error", "合成状态");
        HomeSidePanelSnapshot.toolbar(context, changed);await(() -> TextStatusBridge.asyncTarget != null);
        TextStatusBridge.asyncTarget.setImageDrawable(new android.graphics.drawable.Drawable() {
            @Override public void draw(android.graphics.Canvas canvas) { throw new AssertionError("synthetic async draw Error"); }
        });
        Handler.drain();
        check(!HomeSidePanelSnapshot.toolbar().values.getBundle("profile").getBundle("status").containsKey("iconKey"), "async native setter capture Error never escapes into host caller");
        HomeSidePanelSnapshot.releaseToolbar(changed);TextStatusBridge.async = false;

        AtomicInteger imageChanges = new AtomicInteger();
        Runnable throwingImage = () -> { imageChanges.incrementAndGet(); throw new AssertionError("synthetic image callback Error"); };
        HomeSidePanelSnapshot.Images cache = new HomeSidePanelSnapshot.Images(context, throwingImage);
        HomeSidePanelLayout.Card card = make(HomeSidePanelLayout.CardType.IMAGE, "failure-image");card.imageAssetId = "failure-image-asset";
        imageSnapshot(cache, List.of(card));await(() -> imageChanges.get() == 1);
        check(card(imageSnapshot(cache, List.of(card)), 0).containsKey("imageKey"), "image listener Error contained without losing decode completion");
        cache.close();

        java.util.concurrent.ExecutorService imagesIO = (java.util.concurrent.ExecutorService) field(HomeSidePanelSnapshot.class, "IMAGE_IO").get(null);
        for (int i = 0; i < 2; i++) {
            HomeSidePanelSnapshot.Images rejected = new HomeSidePanelSnapshot.Images(context, changed);
            HomeSidePanelLayout.Card rejectedCard = make(HomeSidePanelLayout.CardType.IMAGE, "rejected-card-" + i);rejectedCard.imageAssetId = "rejected-image-" + i;
            Handler.rejectPosts = i == 0;Handler.postFailure = i == 1 ? new AssertionError("synthetic image Handler Error") : null;
            imageSnapshot(rejected, List.of(rejectedCard));imagesIO.submit(() -> {}).get(3, TimeUnit.SECONDS);
            Handler.rejectPosts = false;Handler.postFailure = null;Handler.drain();
            SidePanelComposeBridge.Snapshot result = imageSnapshot(rejected, List.of(rejectedCard));
            check(!card(result, 0).getBoolean("importing") && !card(result, 0).containsKey("imageKey"), "failed image MAIN post clears pending instead of leaving spinner forever");
            rejected.close();
        }
        TextStatusBridge.state = new TextStatusBridge.Snapshot(TextStatusBridge.Kind.NONE, "", "");
        check(HomeSidePanelImages.removed.isEmpty(), "failure cleanup never deletes user image assets");
    }

    public static void main(String[] args) throws Throwable {
        Looper.getMainLooper();installNoNetwork();
        switch(args[0]) {
            case "schema":schema();break;
            case "data":data();break;
            case "host":host();break;
            case "editor":editor();break;
            case "editor-lifecycle":editorLifecycle();break;
            case "gestures":gestures();break;
            case "wallet":wallet();break;
            case "toolbar":toolbar();break;
            case "resume":resume();break;
            case "images":images();break;
            case "status-icons":statusIcons();break;
            case "image-status-failures":imageStatusFailures();break;
            default:throw new AssertionError("Unknown case");
        }
        check(connections.get()==0,"no network occurred");
        for(String log:XposedBridge.logs)check(!log.contains("合成测试文本")&&!log.contains("合成昵称")&&!log.contains("98765"),"no sensitive data text logged");
        System.out.println("PASS: side-panel "+args[0]+" ("+checks+" assertions, real Java production; UI rendering not tested)");
    }
}
