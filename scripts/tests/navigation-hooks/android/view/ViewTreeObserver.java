package android.view;
public class ViewTreeObserver {
    public interface OnPreDrawListener { boolean onPreDraw(); }
    private final java.util.List<OnPreDrawListener> listeners = new java.util.ArrayList<>();
    public void addOnPreDrawListener(OnPreDrawListener value) { listeners.add(value); }
    public void removeOnPreDrawListener(OnPreDrawListener value) { listeners.remove(value); }
    public boolean isAlive() { return true; }
    public void drawForTest() { for (OnPreDrawListener value : new java.util.ArrayList<>(listeners)) value.onPreDraw(); }
}
