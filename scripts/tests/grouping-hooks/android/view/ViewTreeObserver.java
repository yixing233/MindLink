package android.view;
import java.util.ArrayList;
import java.util.List;
public class ViewTreeObserver {
    public interface OnPreDrawListener { boolean onPreDraw(); }
    public final List<OnPreDrawListener> listeners = new ArrayList<>();
    public boolean isAlive() { return true; }
    public void addOnPreDrawListener(OnPreDrawListener listener) { listeners.add(listener); }
    public void removeOnPreDrawListener(OnPreDrawListener listener) { listeners.remove(listener); }
}
