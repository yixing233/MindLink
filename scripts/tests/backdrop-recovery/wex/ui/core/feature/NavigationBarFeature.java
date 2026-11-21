package wex.ui.core.feature;
import java.util.ArrayList;
import java.util.List;
/** Private logging boundary only. The real sampler and layer are compiled from java_src. */
final class NavigationBarFeature {
    static final String KEY_EFFECT = "nav_bar_glass_mode", KEY_BLUR = "nav_bar_blur_radius";
    static final List<String> logs = new ArrayList<>();
    static void log(String message) { logs.add(message); }
}
