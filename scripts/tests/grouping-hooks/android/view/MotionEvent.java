package android.view;
public class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3;
    private int action;
    private float x, y;
    public static MotionEvent obtain(MotionEvent source) {
        MotionEvent copy = new MotionEvent(); copy.action = source.action; copy.x = source.x; copy.y = source.y; return copy;
    }
    public int getActionMasked() { return action; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRawX() { return x; }
    public float getRawY() { return y; }
    public void setAction(int value) { action = value; }
    public void offsetLocation(float dx, float dy) { x += dx; y += dy; }
    public void recycle() { }
}
