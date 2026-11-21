package android.view;

public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    private int visibility = VISIBLE;
    public int getVisibility() { return visibility; }
    public void setVisibility(int value) { visibility = value; }
}
