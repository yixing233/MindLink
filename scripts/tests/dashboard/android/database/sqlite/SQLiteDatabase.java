package android.database.sqlite;

import android.database.Cursor;

/** Read-only contract double; opens and queries are observable, not a SQL engine. */
public final class SQLiteDatabase implements AutoCloseable {
    public static final int OPEN_READONLY = 1, NO_LOCALIZED_COLLATORS = 16;
    public static int opens, closes, cursorCloses, flags;
    public static boolean failOpen, failQuery, corrupt;
    public static int corruptionCallbacks;
    public static String path;
    public static final java.util.Map<String,String> values = new java.util.LinkedHashMap<>();
    public static SQLiteDatabase openDatabase(String file, Object factory, int mode, android.database.DatabaseErrorHandler errors) {
        opens++; path = file; flags = mode;
        if (mode != (OPEN_READONLY | NO_LOCALIZED_COLLATORS)) throw new AssertionError("not read-only");
        if (errors == null) throw new AssertionError("default corruption handler can delete host database");
        if (failOpen) throw new SecurityException("test storage rejection");
        if (corrupt) {
            corruptionCallbacks++;
            errors.onCorruption(new SQLiteDatabase());
            throw new IllegalStateException("test corrupt database");
        }
        return new SQLiteDatabase();
    }
    public Cursor rawQuery(String query, String[] keys) {
        if (failQuery) throw new IllegalStateException("test query failure");
        if (!query.startsWith("SELECT key,value FROM config WHERE key IN (")) throw new AssertionError("unexpected query");
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (String key : keys) if (values.containsKey(key)) rows.add(new String[]{key, values.get(key)});
        return new Cursor() {
            private int position = -1;
            public boolean moveToNext() { return ++position < rows.size(); }
            public String getString(int index) { return rows.get(position)[index]; }
            public void close() { cursorCloses++; }
        };
    }
    public void close() { closes++; }
    public static void reset() {
        opens = closes = cursorCloses = flags = corruptionCallbacks = 0; path = null; failOpen = failQuery = corrupt = false; values.clear();
    }
}
