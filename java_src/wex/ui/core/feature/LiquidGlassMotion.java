// SPDX-License-Identifier: GPL-3.0-only
// Spring parameters and release sequencing from WeKit / InstallerX-Revived DampedDragAnimation.
package wex.ui.core.feature;

final class LiquidGlassMotion {
    private final Spring value = new Spring(1f, 1000f, 0f);
    private final Spring velocity = new Spring(0.5f, 300f, 0f);
    private final Spring press = new Spring(1f, 1000f, 0f);
    private final Spring scaleX = new Spring(0.6f, 250f, 1f);
    private final Spring scaleY = new Spring(0.7f, 250f, 1f);
    private boolean releasing;

    void snap(float position) {
        value.snap(clamp(position)); velocity.snap(0f); press.snap(0f); scaleX.snap(1f); scaleY.snap(1f);
        releasing = false;
    }

    void press() {
        releasing = false; press.target = 1f; scaleX.target = scaleY.target = 78f / 56f;
    }

    void target(float position) { value.target = clamp(position); }
    void release() { releasing = true; }
    void animateTo(float position) { if (value.target != clamp(position)) { press(); target(position); release(); } }
    float position() { return value.value; }
    float target() { return value.target; }
    float pressProgress() { return Math.max(0f, Math.min(1f, press.value)); }
    float scaleX() { return scaleX.value; }
    float scaleY() { return scaleY.value; }
    float velocity() { return velocity.value; }

    boolean step(float seconds) {
        value.step(seconds);
        velocity.target = releasing ? 0f : value.speed / 3f;
        velocity.step(seconds);
        if (releasing && Math.abs(value.value - value.target) < 0.075f) {
            press.target = 0f; scaleX.target = scaleY.target = 1f; releasing = false;
        }
        press.step(seconds); scaleX.step(seconds); scaleY.step(seconds);
        return releasing || value.moving() || velocity.moving() || press.moving() || scaleX.moving() || scaleY.moving();
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(3f, value)); }

    private static final class Spring {
        final double damping, omega;
        float value, target, speed;
        Spring(float damping, float stiffness, float initial) {
            this.damping = damping; omega = Math.sqrt(stiffness); value = target = initial;
        }
        void snap(float next) { value = target = next; speed = 0f; }
        boolean moving() { return Math.abs(target - value) > 0.001f || Math.abs(speed) > 0.01f; }
        void step(float seconds) {
            if (!moving()) { snap(target); return; }
            double t = Math.max(0f, seconds), y = value - target;
            double decay = Math.exp(-damping * omega * t);
            double next, nextSpeed;
            if (damping == 1.0) {
                double b = speed + omega * y;
                next = decay * (y + b * t);
                nextSpeed = decay * (speed - omega * b * t);
            } else {
                double frequency = omega * Math.sqrt(1.0 - damping * damping);
                double b = (speed + damping * omega * y) / frequency;
                double sine = Math.sin(frequency * t), cosine = Math.cos(frequency * t);
                next = decay * (y * cosine + b * sine);
                nextSpeed = decay * ((b * frequency - damping * omega * y) * cosine
                        - (y * frequency + damping * omega * b) * sine);
            }
            value = target + (float) next; speed = (float) nextSpeed;
        }
    }
}
