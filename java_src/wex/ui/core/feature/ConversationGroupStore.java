package wex.ui.core.feature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Validated group snapshots; malformed persisted data is never overwritten with defaults. */
final class ConversationGroupStore {
    enum Type { MANUAL, PRESET_UNREAD, PRESET_GROUPS, PRESET_FRIENDS, PRESET_OFFICIALS, SQL }
    static final String ALL_ID = "mdl_group_all";
    static final String PREFIX = "mdl_group_";

    static final class Group {
        final String id, name, selectFields, whereClause;
        final Type type;
        final List<String> members;

        Group(String id, String name, Type type, List<String> members, String selectFields, String whereClause) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.type = type == null ? Type.MANUAL : type;
            this.members = Collections.unmodifiableList(new ArrayList<String>(members == null
                    ? Collections.<String>emptyList() : new LinkedHashSet<String>(members)));
            this.selectFields = selectFields == null ? "" : selectFields;
            this.whereClause = whereClause == null ? "" : whereClause;
        }

        boolean isAll() { return ALL_ID.equals(id); }
        String label() {
            if (!name.isEmpty()) return name;
            if (isAll()) return "全部";
            switch (type) {
                case PRESET_UNREAD: return "未读";
                case PRESET_GROUPS: return "群聊";
                case PRESET_FRIENDS: return "好友";
                case PRESET_OFFICIALS: return "公众号";
                default: return "未命名分组";
            }
        }
    }

    private ConversationGroupStore() { }

    static List<Group> defaults() {
        List<Group> groups = new ArrayList<Group>();
        groups.add(new Group(ALL_ID, "", Type.MANUAL, null, null, null));
        groups.add(new Group(PREFIX + "unread", "", Type.PRESET_UNREAD, null, null, null));
        groups.add(new Group(PREFIX + "groups", "", Type.PRESET_GROUPS, null, null, null));
        groups.add(new Group(PREFIX + "friends", "", Type.PRESET_FRIENDS, null, null, null));
        groups.add(new Group(PREFIX + "officials", "", Type.PRESET_OFFICIALS, null, null, null));
        return groups;
    }

    static String encode(List<Group> groups) {
        validate(groups);
        JSONArray array = new JSONArray();
        try {
            for (Group group : groups) {
                array.put(new JSONObject().put("id", group.id).put("name", group.name)
                        .put("type", group.type.name().toLowerCase(Locale.ROOT))
                        .put("members", new JSONArray(group.members))
                        .put("selectFields", group.selectFields).put("whereClause", group.whereClause));
            }
        } catch (JSONException error) { throw new IllegalArgumentException("Cannot encode groups", error); }
        return array.toString();
    }

    static List<Group> decode(String raw) throws JSONException {
        if (raw == null || raw.isEmpty()) return defaults();
        JSONArray array = new JSONArray(raw);
        if (array.length() > 100) throw new IllegalArgumentException("Too many groups");
        List<Group> groups = new ArrayList<Group>();
        boolean hasAll = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.getJSONObject(i);
            String id = value.getString("id");
            if ("wekit_group_all".equals(id)) id = ALL_ID;
            Type type = Type.valueOf(value.getString("type").toUpperCase(Locale.ROOT));
            JSONArray members = value.optJSONArray("members");
            List<String> names = new ArrayList<String>();
            if (members != null) for (int j = 0; j < members.length(); j++) {
                String name = members.getString(j);
                if (!name.isEmpty()) names.add(name);
            }
            Group group = new Group(id, value.optString("name", ""), type, names,
                    value.optString("selectFields", ""), value.optString("whereClause", ""));
            groups.add(group); hasAll |= group.isAll();
        }
        if (!hasAll) groups.add(0, defaults().get(0));
        validate(groups);
        return groups;
    }

    static void validate(List<Group> groups) {
        if (groups == null || groups.size() > 100) throw new IllegalArgumentException("Invalid group list");
        HashSet<String> ids = new HashSet<String>();
        boolean all = false;
        for (Group group : groups) {
            if (group == null || group.id.isEmpty() || !ids.add(group.id)) {
                throw new IllegalArgumentException("Empty or duplicate group ID");
            }
            if (group.name.length() > 128) throw new IllegalArgumentException("Group name is too long");
            all |= group.isAll();
        }
        if (!all) throw new IllegalArgumentException("The all-conversations tab cannot be removed");
    }
}
