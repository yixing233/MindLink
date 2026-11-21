package android.database;

public interface Cursor extends AutoCloseable {
    boolean moveToNext();
    String getString(int index);
    void close();
}
