package wex.ui.core.feature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Side-panel layout model / codec (ported from WeKit `HomeSidePanelLayout.kt`, GPL-3.0).
 * JSON is persisted through WexStore; ids use the WeKit naming scheme.
 */
final class HomeSidePanelLayout {

    static final String KEY_LAYOUT = "home_side_panel_layout";
    static final int VERSION = 1;
    static final int IMAGE_MIN_HEIGHT_DP = 80;
    static final int IMAGE_MAX_HEIGHT_DP = 800;
    static final int IMAGE_HEIGHT_STEP_DP = 8;

    enum CardType {
        DATE_TIME("date_time"),
        WEATHER("weather"),
        WALLET("wallet"),
        HITOKOTO("hitokoto"),
        IMAGE("image"),
        HORIZONTAL_ACTIONS("horizontal_actions"),
        VERTICAL_ACTIONS("vertical_actions");

        final String serialName;

        CardType(String serialName) {
            this.serialName = serialName;
        }

        static CardType from(String raw) {
            for (CardType type : values()) {
                if (type.serialName.equals(raw)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown card type " + raw);
        }
    }

    enum ActionKind {
        ADD_FRIEND, SCAN, MOMENTS, WALLET, CHANNELS, WECHAT_SETTINGS,
        FAVORITES, WEKIT_SETTINGS, RESTART_WECHAT, FORCE_STOP_WECHAT, MARK_ALL_READ;

        static ActionKind from(String raw) {
            for (ActionKind kind : values()) {
                if (kind.name().equals(raw)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown action kind " + raw);
        }
    }

    static final class Card {
        String id;
        CardType type;
        // DATE_TIME
        boolean showLunarCalendar;
        // WEATHER
        String weatherCity;
        // WALLET
        boolean hideWalletBalance;
        // HITOKOTO
        String hitokotoCategories;
        Integer hitokotoMinLength;
        Integer hitokotoMaxLength;
        boolean showHitokotoSource = true;
        boolean showHitokotoAuthor = true;
        // IMAGE
        String imageAssetId;
        Integer imageHeightDp = 240;
        // ACTIONS
        List<Action> actions = new ArrayList<Action>();
    }

    static final class Action {
        String id;
        ActionKind kind;
        Action(String id, ActionKind kind){this.id=id;this.kind=kind;}
    }

    private HomeSidePanelLayout() {
    }

    static List<Card> defaults() {
        List<Card> cards = new ArrayList<Card>();
        cards.add(card(CardType.DATE_TIME, false));
        Card weather = card(CardType.WEATHER, false);
        weather.weatherCity = "101010100";
        cards.add(weather);
        cards.add(card(CardType.WALLET, false));
        Card actions = card(CardType.VERTICAL_ACTIONS, false);
        actions.actions.add(action(ActionKind.ADD_FRIEND));
        actions.actions.add(action(ActionKind.MOMENTS));
        actions.actions.add(action(ActionKind.CHANNELS));
        actions.actions.add(action(ActionKind.MARK_ALL_READ));
        actions.actions.add(action(ActionKind.WEKIT_SETTINGS));
        cards.add(actions);
        cards.add(card(CardType.HITOKOTO, false));
        return cards;
    }

    static Action action(ActionKind kind){return new Action(java.util.UUID.randomUUID().toString(),kind);}

    static Card card(CardType type, boolean lunar) {
        Card card = new Card();
        card.id = java.util.UUID.randomUUID().toString();
        card.type = type;
        card.showLunarCalendar = lunar;
        return card;
    }

    static String encode(List<Card> cards) throws IllegalArgumentException {
        validate(cards);
        return encodeValues(cards);
    }

    private static String encodeValues(List<Card> cards) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            JSONArray values = new JSONArray();
            for (Card card : cards) {
                JSONObject value = new JSONObject();
                value.put("id", card.id);
                value.put("cardType", card.type.serialName);
                switch (card.type) {
                    case DATE_TIME:
                        value.put("showLunarCalendar", card.showLunarCalendar);
                        break;
                    case WEATHER:
                        value.put("city", card.weatherCity == null ? "" : card.weatherCity);
                        break;
                    case WALLET:
                        value.put("hideBalanceByDefault", card.hideWalletBalance);
                        break;
                    case HITOKOTO:
                        JSONObject settings = new JSONObject();
                        settings.put("categories", card.hitokotoCategories == null ? "" : card.hitokotoCategories);
                        if (card.hitokotoMinLength != null) settings.put("minLength", card.hitokotoMinLength);
                        if (card.hitokotoMaxLength != null) settings.put("maxLength", card.hitokotoMaxLength);
                        settings.put("showSource", card.showHitokotoSource);
                        settings.put("showAuthor", card.showHitokotoAuthor);
                        value.put("settings", settings);
                        break;
                    case IMAGE:
                        value.put("imageAssetId", card.imageAssetId == null ? JSONObject.NULL : card.imageAssetId);
                        value.put("heightDp", card.imageHeightDp == null ? 240 : card.imageHeightDp);
                        break;
                    case HORIZONTAL_ACTIONS:
                    case VERTICAL_ACTIONS:
                        JSONArray actions = new JSONArray();
                        for (int i = 0; i < card.actions.size(); i++) {
                            Action action=card.actions.get(i);
                            actions.put(new JSONObject().put("id", action.id)
                                    .put("kind", action.kind.name()));
                        }
                        value.put("actions", actions);
                        break;
                }
                values.put(value);
            }
            root.put("cards", values);
            return root.toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to encode side-panel layout", error);
        }
    }

    static List<Card> decode(String raw) throws Exception {
        List<Card> cards = new ArrayList<Card>();
        if (raw == null || raw.trim().length() == 0) return cards;
        JSONObject root = new JSONObject(raw);
        int version = root.optInt("version", VERSION);
        if (version > VERSION) throw new IllegalArgumentException("Unsupported layout version " + version);
        JSONArray values = root.getJSONArray("cards");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            Card card = new Card();
            card.id = value.getString("id");
            card.type = CardType.from(value.getString("cardType"));
            card.showLunarCalendar = value.optBoolean("showLunarCalendar", false);
            card.weatherCity = value.optString("city", "");
            card.hideWalletBalance = value.optBoolean("hideBalanceByDefault", false);
            card.imageAssetId = value.isNull("imageAssetId") ? null : value.optString("imageAssetId", null);
            card.imageHeightDp = value.has("heightDp") ? value.optInt("heightDp", 240) : 240;
            JSONObject settings = value.optJSONObject("settings");
            if (settings != null) {
                card.hitokotoCategories = settings.optString("categories", "");
                if (settings.has("minLength") && !settings.isNull("minLength")) card.hitokotoMinLength = settings.getInt("minLength");
                if (settings.has("maxLength") && !settings.isNull("maxLength")) card.hitokotoMaxLength = settings.getInt("maxLength");
                card.showHitokotoSource = settings.optBoolean("showSource", true);
                card.showHitokotoAuthor = settings.optBoolean("showAuthor", true);
            }
            JSONArray actions = value.optJSONArray("actions");
            if (actions != null) {
                for (int a = 0; a < actions.length(); a++) {
                    JSONObject action = actions.getJSONObject(a);
                    String actionId=action.optString("id",java.util.UUID.randomUUID().toString());
                    card.actions.add(new Action(actionId,ActionKind.from(action.getString("kind"))));
                }
            }
            cards.add(card);
        }
        // Existing invalid one-line settings remain editable/renderable as a local Error.
        // Only a new save is rejected; never replace the old stored layout with defaults here.
        validateStructure(cards);
        return cards;
    }

    static List<Card> copy(List<Card> cards) {
        try {
            validateStructure(cards);
            return decode(encodeValues(cards));
        } catch (Exception error) { throw new IllegalArgumentException("Unable to copy side-panel layout", error); }
    }

    static void validate(List<Card> cards) throws IllegalArgumentException {
        validateStructure(cards);
        for (Card card : cards) {
            if (card.type != CardType.HITOKOTO) continue;
            Integer min = card.hitokotoMinLength, max = card.hitokotoMaxLength;
            String problem = null;
            if (min != null && min < 0 || max != null && max < 0) {
                problem = "长度不能为负数";
            } else {
                String raw = card.hitokotoCategories == null ? "" : card.hitokotoCategories.trim();
                // Empty is the existing Java schema's all-categories default. Separators alone are not a category.
                if (!raw.isEmpty()) {
                    boolean any = false;
                    for (String category : raw.split("[,，\\s]+")) {
                        if (category.isEmpty()) continue;
                        any = true;
                        if (!category.matches("[a-l]")) problem = "包含不支持的一言分类";
                    }
                    if (!any) problem = "至少选择一个分类";
                }
                if (problem == null && min != null && max != null && max < min)
                    problem = "最大长度不能小于最小长度";
            }
            if (problem != null) throw new IllegalArgumentException("一言设置无效：" + problem + "；原配置未改变");
        }
    }

    private static void validateStructure(List<Card> cards) throws IllegalArgumentException {
        List<String> ids = new ArrayList<String>();
        for (Card card : cards) {
            if (card.id == null || card.id.length() == 0) {
                throw new IllegalArgumentException("Card ids must not be blank");
            }
            if (ids.contains(card.id)) {
                throw new IllegalArgumentException("Duplicate card id " + card.id);
            }
            ids.add(card.id);
            if (card.type == null) {
                throw new IllegalArgumentException("Card type must not be null");
            }
            if(card.type==CardType.HORIZONTAL_ACTIONS||card.type==CardType.VERTICAL_ACTIONS){java.util.HashSet<String> actionIds=new java.util.HashSet<>();for(Action action:card.actions){if(action==null||action.kind==null||action.id==null||action.id.isEmpty()||!actionIds.add(action.id))throw new IllegalArgumentException("Invalid or duplicate action id");}}
            if (card.type == CardType.IMAGE) {
                Integer height = card.imageHeightDp;
                if (height != null
                        && (height < IMAGE_MIN_HEIGHT_DP || height > IMAGE_MAX_HEIGHT_DP
                        || height % IMAGE_HEIGHT_STEP_DP != 0)) {
                    throw new IllegalArgumentException("Invalid image height " + height);
                }
            }
        }
    }


}
