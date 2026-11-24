package wex.ui.core.feature;

import android.app.Activity;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Drawer-owned requests/results. Every result is tied to its card and request generation. */
final class WeatherPlaceController {
    interface Selection { void apply(String id, String config); }
    private final Activity activity;
    private final Runnable changed;
    private final Selection selection;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private WeatherLocation.Job automatic;
    private String locatingCard;
    private long lastAutomatic;
    private String automaticError = "";
    private int automaticGeneration;
    private static final class Entry {
        WeatherLocation.Job job;
        int generation;
        boolean busy;
        String message = "", token = "";
        List<JSONObject> choices = java.util.Collections.emptyList();
    }

    WeatherPlaceController(Activity activity, Runnable changed, Selection selection) {
        this.activity = activity; this.changed = changed; this.selection = selection;
    }

    void ensureCurrent(List<HomeSidePanelLayout.Card> cards, boolean retry) {
        boolean current = false;
        for (HomeSidePanelLayout.Card card : cards)
            if (card.type == HomeSidePanelLayout.CardType.WEATHER && WeatherLocation.CURRENT.equals(card.weatherCity)) current = true;
        if (!current || !LocationPrivacy.isEnabled()) {
            cancelAutomatic();
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (automatic != null || locatingCard != null || !retry && (!WeatherLocation.needsFix()
                || lastAutomatic != 0 && now - lastAutomatic < 60_000L)) return;
        lastAutomatic = now; automaticError = "";
        final int generation = ++automaticGeneration;
        automatic = WeatherLocation.locate(activity, (places, error) -> {
            if (generation != automaticGeneration) return;
            automatic = null;
            automaticError = error;
            if (!places.isEmpty()) {
                try { WeatherLocation.saveCurrent(places.get(0)); }
                catch (Exception ignored) { automaticError = "位置保存未完成，请重试"; }
            }
            changed.run();
        });
    }

    void locate(String id) {
        cancelAutomatic();
        if (locatingCard != null && !locatingCard.equals(id)) cancelCard(locatingCard);
        Entry entry = begin(id);
        locatingCard = id;
        entry.message = "正在获取精确位置…";
        int generation = entry.generation;
        entry.job = WeatherLocation.locate(activity, (places, error) -> {
            if (entry.generation != generation) return;
            locatingCard = null;
            lastAutomatic = android.os.SystemClock.elapsedRealtime();
            entry.busy = false; entry.job = null; entry.message = error;
            if (!places.isEmpty()) {
                try {
                    WeatherLocation.saveCurrent(places.get(0));
                    automaticError = "";
                    selection.apply(id, WeatherLocation.CURRENT);
                    entry.message = "已定位到 " + WeatherLocation.label(places.get(0));
                } catch (Exception ignored) { entry.message = "位置保存未完成，请重试"; }
            }
            changed.run();
        });
        changed.run();
    }

    void search(String id, String query) {
        Entry entry = begin(id); entry.message = "正在搜索…";
        int generation = entry.generation;
        entry.job = WeatherLocation.search(query, (places, error) -> {
            if (entry.generation != generation) return;
            entry.busy = false; entry.job = null; entry.choices = places;
            entry.token = java.util.UUID.randomUUID().toString();
            entry.message = error.isEmpty() ? places.isEmpty() ? "未找到地点，请加上省市名称再搜索" : "选择要显示天气的地点" : error;
            changed.run();
        });
        changed.run();
    }

    void choose(String id, String token, int index) {
        Entry entry = entries.get(id);
        if (!LocationPrivacy.isEnabled() || entry == null || entry.busy || !entry.token.equals(token)
                || index < 0 || index >= entry.choices.size()) return;
        selection.apply(id, entry.choices.get(index).toString());
        entry.message = "已选择 " + WeatherLocation.label(entry.choices.get(index));
        entry.choices = java.util.Collections.emptyList(); entry.token = "";
        changed.run();
    }

    private Entry begin(String id) {
        Entry entry = entries.get(id);
        if (entry == null) { entry = new Entry(); entries.put(id, entry); }
        if (entry.job != null) entry.job.cancel();
        if (id.equals(locatingCard)) locatingCard = null;
        entry.generation++; entry.busy = true; entry.token = ""; entry.choices = java.util.Collections.emptyList();
        return entry;
    }

    SidePanelComposeBridge.Snapshot decorate(SidePanelComposeBridge.Snapshot snapshot) {
        ArrayList<Bundle> cards = snapshot.values.getParcelableArrayList("cards");
        if (cards == null) return snapshot;
        for (Bundle card : cards) {
            if (!"weather".equals(card.getString("cardType"))) continue;
            Entry entry = entries.get(card.getString("id"));
            Bundle state = new Bundle();
            state.putBoolean("current", card.getBoolean("currentLocation", false));
            state.putBoolean("busy", entry != null && entry.busy);
            state.putString("message", entry == null ? "" : entry.message);
            state.putString("token", entry == null ? "" : entry.token);
            ArrayList<Bundle> choices = new ArrayList<>();
            if (entry != null) for (JSONObject place : entry.choices) choices.add(HomeSidePanelData.toBundle(place));
            state.putParcelableArrayList("choices", choices);
            card.putBundle("placeEditor", state);
            if (card.getBoolean("currentLocation", false) && (automatic != null || !automaticError.isEmpty())) {
                Bundle weather = card.getBundle("state");
                if (weather != null) {
                    Bundle cached = weather.getBundle("snapshot");
                    if (cached == null) cached = weather.getBundle("cached");
                    if (!automaticError.isEmpty()) {
                        weather.putString("kind", "error"); weather.putString("message", automaticError);
                        if (cached != null) weather.putBundle("cached", cached);
                    } else if (cached == null) weather.putString("kind", "loading");
                    else weather.putBoolean("refreshing", true);
                }
            }
        }
        return snapshot;
    }

    private void cancelAutomatic() {
        automaticGeneration++;
        if (automatic != null) { automatic.cancel(); automatic = null; lastAutomatic = 0; }
        automaticError = "";
    }

    void cancelCard(String id) {
        Entry entry = entries.remove(id);
        if (id.equals(locatingCard)) locatingCard = null;
        if (entry != null) {
            entry.generation++;
            if (entry.job != null) entry.job.cancel();
        }
    }

    void cancel() {
        cancelAutomatic();
        for (String id : new ArrayList<>(entries.keySet())) cancelCard(id);
    }
}
