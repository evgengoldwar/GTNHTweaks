package gtnhtweaks.modules.minecraft.client;

/** A wrist-led change of grip, with different outgoing and incoming paths, rather than a vertical equip bob. */
public final class ItemSwitchMotion {

    private static final float CHANGE = 0.4F;
    // Phase, camera-space translation, then wrist pitch, yaw and roll in degrees.
    // Both items meet below the screen at CHANGE. The incoming item sweeps left and briefly overshoots its rest pose.
    private static final float[][] KEYS = {
        { 0F, 0F, 0F, 0F, 0F, 0F, 0F },
        { 0.18F, 0.10F, -0.12F, 0.04F, 22F, -10F, -20F },
        { CHANGE, 0.20F, -1.65F, 0.10F, 95F, -35F, -80F },
        { 0.58F, -0.42F, -1.25F, 0.06F, 55F, 20F, -65F },
        { 0.80F, -0.12F, 0.06F, -0.03F, -12F, 8F, 12F },
        { 1F, 0F, 0F, 0F, 0F, 0F, 0F } };

    /** Reused by each ItemRenderer, including the offhand renderer. */
    public static final class Pose {

        public float x, y, z;
        public float pitch, yaw, roll;

        public boolean isRest() {
            return x == 0F && y == 0F && z == 0F && pitch == 0F && yaw == 0F && roll == 0F;
        }
    }

    /** Blends a mid-flight reversal from its existing pose instead of teleporting onto the other path. */
    public static final class Transition {

        private static final int RECOVERY_TICKS = 3;
        private final Pose offset = new Pose();
        private final Pose before = new Pose();
        private final Pose after = new Pose();
        private boolean lowering;
        private int age = RECOVERY_TICKS;

        public void reset() {
            age = RECOVERY_TICKS;
            lowering = false;
        }

        public void tick(float previousProgress, float progress) {
            age = Math.min(RECOVERY_TICKS, age + 1);
            boolean nextLowering = progress < previousProgress;
            if (lowering != nextLowering && previousProgress > 0F && previousProgress < 1F) {
                ItemSwitchMotion.sample(previousProgress, lowering, 100, before);
                add(before, offset, remaining(age));
                ItemSwitchMotion.sample(previousProgress, nextLowering, 100, after);
                offset.x = before.x - after.x;
                offset.y = before.y - after.y;
                offset.z = before.z - after.z;
                offset.pitch = before.pitch - after.pitch;
                offset.yaw = before.yaw - after.yaw;
                offset.roll = before.roll - after.roll;
                age = 0;
            }
            lowering = nextLowering;
        }

        public void sample(float progress, float partialTicks, int strength, Pose pose) {
            ItemSwitchMotion.sample(progress, lowering, strength, pose);
            add(pose, offset, remaining(age + partialTicks) * Math.max(0, Math.min(200, strength)) / 100F);
        }

        private static float remaining(float ticks) {
            float t = Math.max(0F, Math.min(1F, ticks / RECOVERY_TICKS));
            return 1F - t * t * (3F - 2F * t);
        }

        private static void add(Pose pose, Pose delta, float amount) {
            pose.x += delta.x * amount;
            pose.y += delta.y * amount;
            pose.z += delta.z * amount;
            pose.pitch += delta.pitch * amount;
            pose.yaw += delta.yaw * amount;
            pose.roll += delta.roll * amount;
        }
    }

    private ItemSwitchMotion() {}

    /** Vanilla timing at 100 percent; only the trajectory changes. */
    public static float step(int speed) {
        return 0.4F * Math.max(50, Math.min(200, speed)) / 100F;
    }

    /**
     * Vanilla still decides which item is rendered. Its equip progress supplies only the clock; no vanilla lowering
     * is added to this pose. The two paths meet at progress zero; Transition handles reversals before that point.
     */
    public static void sample(float progress, boolean lowering, int strength, Pose pose) {
        float p = Math.max(0F, Math.min(1F, progress));
        float phase = lowering ? CHANGE * (1F - p) : CHANGE + (1F - CHANGE) * p;
        float scale = Math.max(0, Math.min(200, strength)) / 100F;
        pose.x = value(phase, 1) * scale;
        pose.y = value(phase, 2) * scale;
        pose.z = value(phase, 3) * scale;
        pose.pitch = value(phase, 4) * scale;
        pose.yaw = value(phase, 5) * scale;
        pose.roll = value(phase, 6) * scale;
    }

    /** Cubic Hermite interpolation keeps velocity continuous through the key poses and zero at rest. */
    private static float value(float phase, int component) {
        if (phase <= 0F || phase >= 1F) return 0F;
        int next = 1;
        while (phase > KEYS[next][0]) next++;
        int previous = next - 1;
        float span = KEYS[next][0] - KEYS[previous][0];
        float t = (phase - KEYS[previous][0]) / span;
        float t2 = t * t;
        float t3 = t2 * t;
        return (2F * t3 - 3F * t2 + 1F) * KEYS[previous][component]
            + (t3 - 2F * t2 + t) * span * tangent(previous, component)
            + (-2F * t3 + 3F * t2) * KEYS[next][component]
            + (t3 - t2) * span * tangent(next, component);
    }

    private static float tangent(int key, int component) {
        if (key == 0 || key == KEYS.length - 1) return 0F;
        return (KEYS[key + 1][component] - KEYS[key - 1][component]) / (KEYS[key + 1][0] - KEYS[key - 1][0]);
    }
}
