package wex.ui.core.feature;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.View;

import java.lang.ref.WeakReference;

/** Only listens while the owned glass is visible and the user enabled gravity highlights. */
final class GlassGravity implements SensorEventListener {
    private final WeakReference<View> viewRef;
    private SensorManager manager;
    private boolean listening;
    float x, y;

    GlassGravity(View view) { viewRef = new WeakReference<>(view); }

    void enable(boolean enabled) {
        if (enabled == listening) return;
        if (!enabled) { stop(); return; }
        View view = viewRef.get();
        if (view == null) return;
        manager = (SensorManager) view.getContext().getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (sensor != null) listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    void stop() {
        if (manager != null) manager.unregisterListener(this);
        listening = false; x = y = 0f;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        View view = viewRef.get();
        if (view == null || !view.isShown()) return;
        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        int rotation = view.getDisplay() == null ? Surface.ROTATION_0 : view.getDisplay().getRotation();
        float nx = gx, ny = gy;
        if (rotation == Surface.ROTATION_90) { nx = -gy; ny = gx; }
        else if (rotation == Surface.ROTATION_180) { nx = -gx; ny = -gy; }
        else if (rotation == Surface.ROTATION_270) { nx = gy; ny = -gx; }
        if (Math.abs(nx - x) + Math.abs(ny - y) < 0.005f) return;
        x = nx; y = ny; view.invalidate();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
