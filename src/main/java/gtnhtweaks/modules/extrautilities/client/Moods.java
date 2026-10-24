package gtnhtweaks.modules.extrautilities.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * How a companion feels where it is ({@link Mood}): the night, the rain falling on it, a hot biome or the Nether. Each
 * comes in smoothly, as the sun sets or the rain starts.
 */
final class Moods {

    /** Biome temperatures where the heat starts and where it is full: savanna is 1.2, desert 2. */
    private static final float WARM = 1.2F, HOT = 1.8F;

    private Moods() {}

    /** The mood where the entity is, or where the player is for the inventory. */
    static Mood at(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (entity == null) entity = mc.thePlayer;
        if (world == null || entity == null || entity.worldObj != world) return Mood.NEUTRAL;
        int x = MathHelper.floor_double(entity.posX);
        int y = MathHelper.floor_double(entity.posY);
        int z = MathHelper.floor_double(entity.posZ);
        if (!world.blockExists(x, y, z)) return Mood.NEUTRAL;
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);

        float heat = world.provider.isHellWorld ? 1
            : (biome.getFloatTemperature(x, y, z) - WARM) / (HOT - WARM);

        // only rain, not snow, and only falling on it: under the open sky
        float rain = 0;
        if (biome.canSpawnLightningBolt() && world.canBlockSeeTheSky(x, y, z)
            && world.getPrecipitationHeight(x, z) <= y + 1) {
            rain = world.getRainStrength(1);
        }

        float night = 0;
        if (world.provider.isSurfaceWorld() && !world.provider.hasNoSky) {
            // 0.25 is sunset, 0.5 midnight, 0.75 sunrise
            float angle = world.getCelestialAngle(1);
            night = Math.min(ramp(angle, 0.25F, 0.3F), 1 - ramp(angle, 0.7F, 0.75F));
        }
        return new Mood(night, rain, heat, world.provider.isHellWorld);
    }

    private static float ramp(float value, float from, float to) {
        return Math.max(0, Math.min(1, (value - from) / (to - from)));
    }
}
