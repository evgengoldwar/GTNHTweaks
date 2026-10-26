package gtnhtweaks.modules.extrautilities.client;

import gtnhtweaks.common.model.Shapes;

/** How a companion feels where it is, each from 0 to 1. Heat wins over the rest, rain over the night. */
public final class Mood {

    public static final Mood NEUTRAL = new Mood(0, 0, 0, false);

    /** At night. */
    final float night;
    /** In the rain falling on it. */
    final float rain;
    /** In a hot biome or in the Nether. */
    final float heat;
    /** In the Nether: a fire burns with the blue flame of soul fire there. */
    final boolean nether;

    public Mood(float night, float rain, float heat, boolean nether) {
        this.nether = nether;
        this.heat = Shapes.clamp(heat);
        this.rain = Shapes.clamp(rain) * (1 - this.heat);
        this.night = Shapes.clamp(night) * (1 - this.rain) * (1 - this.heat);
    }
}
