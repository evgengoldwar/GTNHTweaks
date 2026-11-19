package gtnhtweaks.modules.minecraft.client;

/** Stateless equip curves: vanilla owns item replacement, ticks and partial-tick interpolation. */
public final class ItemSwitchMotion {

    private ItemSwitchMotion() {}

    /** Smooth both ends without changing the point at which vanilla replaces the lowered item. */
    public static float ease(float progress) {
        float p = Math.max(0F, Math.min(1F, progress));
        return p * p * (3F - 2F * p);
    }

    /** At 100 percent each half takes five client ticks; never jump over a whole half in one tick. */
    public static float step(int speed) {
        return 0.2F * Math.max(50, Math.min(200, speed)) / 100F;
    }
}
