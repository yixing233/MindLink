package mdl.spacer;

import android.graphics.Rect;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.h3;
import androidx.recyclerview.widget.p2;
import java.lang.reflect.InvocationHandler;

/** Compile separately and load with the host ClassLoader as parent. */
public final class TailDecoration extends p2 {
    private final InvocationHandler callback;
    public TailDecoration(InvocationHandler callback) { this.callback = callback; }
    @Override public void getItemOffsets(Rect out, View child, RecyclerView parent, h3 state) {
        try {
            callback.invoke(this, null, new Object[]{out, child, parent, state});
        } catch (Throwable error) {
            android.util.Log.w("MdlGlass", "tail decoration failed", error);
        }
    }
}
