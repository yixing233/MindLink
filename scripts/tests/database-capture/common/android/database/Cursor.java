package android.database;

public interface Cursor {
    int FIELD_TYPE_NULL = 0, FIELD_TYPE_INTEGER = 1, FIELD_TYPE_FLOAT = 2,
            FIELD_TYPE_STRING = 3, FIELD_TYPE_BLOB = 4;
    String[] getColumnNames();
    boolean moveToNext();
    int getType(int index);
    long getLong(int index);
    double getDouble(int index);
    byte[] getBlob(int index);
    String getString(int index);
    void close();
}
