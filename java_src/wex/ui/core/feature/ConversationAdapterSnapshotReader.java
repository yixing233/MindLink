package wex.ui.core.feature;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the conversation models from an adapter's backing list without routing every row through
 * getItem(). Ported from WeKit's ConversationAdapterSnapshotReader (GPL-3.0).
 */
final class ConversationAdapterSnapshotReader {

    private static final class AccessPlan {
        final Field holderField;
        final Field listField;
        final Field itemField;

        AccessPlan(Field holderField, Field listField, Field itemField) {
            this.holderField = holderField;
            this.listField = listField;
            this.itemField = itemField;
        }

        List<Object> read(Object adapter) throws Exception {
            Object holder = holderField == null ? adapter : holderField.get(adapter);
            Object rawList = listField.get(holder);
            if (!(rawList instanceof List)) {
                return null;
            }
            final List<?> rows = (List<?>) rawList;
            if (itemField == null) {
                return new AbstractList<Object>() {
                    @Override
                    public Object get(int index) {
                        return rows.get(index);
                    }

                    @Override
                    public int size() {
                        return rows.size();
                    }
                };
            }
            final Field field = itemField;
            return new AbstractList<Object>() {
                @Override
                public Object get(int index) {
                    Object row = rows.get(index);
                    try {
                        return row == null ? null : field.get(row);
                    } catch (Throwable error) {
                        return null;
                    }
                }

                @Override
                public int size() {
                    return rows.size();
                }
            };
        }
    }

    private static final class Candidate {
        final Field holderField;
        final Object holder;
        final Field listField;

        Candidate(Field holderField, Object holder, Field listField) {
            this.holderField = holderField;
            this.holder = holder;
            this.listField = listField;
        }
    }

    private final Map<Class<?>, AccessPlan> plans = new ConcurrentHashMap<Class<?>, AccessPlan>();

    List<Object> read(Object adapter, Integer expectedCount, ItemAt originalItemAt) {
        AccessPlan plan = plans.get(adapter.getClass());
        if (plan != null) {
            List<Object> rows = readPlan(plan, adapter, expectedCount);
            if (rows != null) {
                return rows;
            }
        }
        if (expectedCount != null && expectedCount <= 0) {
            return java.util.Collections.emptyList();
        }

        Object expected = originalItemAt.at(0);
        for (Candidate candidate : candidates(adapter)) {
            Object rawRows;
            try {
                rawRows = candidate.listField.get(candidate.holder);
            } catch (Throwable ignored) {
                continue;
            }
            if (!(rawRows instanceof List)) {
                continue;
            }
            List<?> rows = (List<?>) rawRows;
            if (rows.isEmpty() || (expectedCount != null && rows.size() != expectedCount)) {
                continue;
            }
            Object row = rows.get(0);
            Field itemField = null;
            if (row == expected) {
                itemField = null;
            } else if (row != null) {
                itemField = findRowField(row, expected);
                if (itemField == null) {
                    continue;
                }
            } else {
                continue;
            }
            AccessPlan next = new AccessPlan(candidate.holderField, candidate.listField, itemField);
            List<Object> result = readPlan(next, adapter, expectedCount);
            if (result != null) {
                plans.put(adapter.getClass(), next);
                return result;
            }
        }
        return null;
    }

    interface ItemAt {
        Object at(int index);
    }

    private List<Object> readPlan(AccessPlan plan, Object adapter, Integer expectedCount) {
        try {
            List<Object> rows = plan.read(adapter);
            if (rows == null) {
                return null;
            }
            if (expectedCount != null && rows.size() != expectedCount) {
                return null;
            }
            return rows;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private java.util.List<Candidate> candidates(Object adapter) {
        java.util.List<Candidate> out = new java.util.ArrayList<Candidate>();
        for (Field field : instanceFields(adapter.getClass())) {
            Object value;
            try {
                value = field.get(adapter);
            } catch (Throwable ignored) {
                continue;
            }
            if (value == null) {
                continue;
            }
            if (value instanceof List) {
                out.add(new Candidate(null, adapter, field));
            }
            if (isLeafType(value.getClass())) {
                continue;
            }
            for (Field nested : instanceFields(value.getClass())) {
                if (List.class.isAssignableFrom(nested.getType())) {
                    out.add(new Candidate(field, value, nested));
                }
            }
        }
        return out;
    }

    private static java.util.List<Field> instanceFields(Class<?> type) {
        java.util.List<Field> out = new java.util.ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    out.add(field);
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return out;
    }

    private static Field findRowField(Object row, Object expected) {
        for (Field field : instanceFields(row.getClass())) {
            try {
                if (field.get(row) == expected) {
                    return field;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isLeafType(Class<?> type) {
        return type.isArray() || type.isEnum()
                || type.getName().startsWith("java.")
                || type.getName().startsWith("kotlin.");
    }
}
