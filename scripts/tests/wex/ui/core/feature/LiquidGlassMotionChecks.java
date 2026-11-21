package wex.ui.core.feature;

public final class LiquidGlassMotionChecks {
    public static void main(String[] args) {
        LiquidGlassMotion motion = new LiquidGlassMotion();
        motion.snap(0f);
        motion.press(); motion.target(2f);
        boolean moved = false;
        for (int i = 0; i < 90; i++) {
            moved |= motion.step(1f / 60f);
            check(Float.isFinite(motion.position()) && Float.isFinite(motion.scaleX()), "spring remains finite");
        }
        check(moved && Math.abs(motion.position() - 2f) < 0.02f, "pressed pill settles on target");
        check(motion.pressProgress() > 0.95f && motion.scaleX() > 1.35f, "WeKit pressed scale and progress apply");
        motion.release();
        for (int i = 0; i < 180; i++) motion.step(1f / 60f);
        check(motion.pressProgress() < 0.01f && Math.abs(motion.scaleX() - 1f) < 0.01f,
                "release waits for movement then restores scale");
        motion.target(10f);
        for (int i = 0; i < 180; i++) motion.step(1f / 60f);
        check(Math.abs(motion.position() - 3f) < 0.01f, "target stays inside four pages");
        System.out.println("PASS: liquid-glass target, pressed scale, finite spring and release sequencing");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
