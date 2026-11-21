package android.view;
import java.util.ArrayList;
import java.util.List;
public final class ViewTreeObserver {
    public interface OnPreDrawListener { boolean onPreDraw(); }
    private final List<OnPreDrawListener> listeners = new ArrayList<>();
    public boolean isAlive() { return true; }
    public void addOnPreDrawListener(OnPreDrawListener value) { listeners.add(value); }
    public void removeOnPreDrawListener(OnPreDrawListener value) { listeners.remove(value); }
    public int listenerCount() { return listeners.size(); }
    public void dispatchOnPreDraw() { for (OnPreDrawListener value : List.copyOf(listeners)) value.onPreDraw(); }
}
