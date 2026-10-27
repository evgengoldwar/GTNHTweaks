package gtnhtweaks.modules.extrautilities.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * How a companion feels where it is ({@link Mood}): the night, the rain falling on it, a hot biome or the Nether.
 * <p>
 * Each follows what it feels with a delay of a few seconds, so it falls asleep and wakes up slowly even when the time
 * is set by a command, or when it is carried out of the rain or out of the desert. What it feels is kept for each
 * entity holding it or lying on the ground.
 */
final class Moods {

    /** Biome temperatures where the heat starts and where it is full: savanna is 1.2, desert 2. */
    private static final float WARM = 1.2F, HOT = 1.8F;
    /** Seconds to get most of the way to a new mood: about three times that to get there. */
    private static final float SETTLE = 0.9F;
    /** Not drawn for so long, it takes the mood where it is at once, as when it is first seen. */
    private static final long FORGET = 3_000_000_000L;

    private static final Map<Integer, Feeling> FEELINGS = new HashMap<>();

    private static final class Feeling {

        float night, rain, heat;
        long lastTime;
    }

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

        float heat = world.provider.isHellWorld ? 1 : (biome.getFloatTemperature(x, y, z) - WARM) / (HOT - WARM);

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

        Feeling feeling = follow(entity.getEntityId(), night, rain, Math.max(0, Math.min(1, heat)));
        return new Mood(feeling.night, feeling.rain, feeling.heat, world.provider.isHellWorld);
    }

    /** Brings what the companion of the entity feels closer to the mood where it is. */
    private static Feeling follow(int entityId, float night, float rain, float heat) {
        long now = System.nanoTime();
        Feeling feeling = FEELINGS.get(entityId);
        if (feeling == null) {
            forgetOld(now);
            feeling = new Feeling();
            FEELINGS.put(entityId, feeling);
        }
        if (feeling.lastTime == 0 || now - feeling.lastTime > FORGET) {
            feeling.night = night;
            feeling.rain = rain;
            feeling.heat = heat;
        } else {
            float step = 1 - (float) Math.exp(-(now - feeling.lastTime) / 1e9 / SETTLE);
            feeling.night += (night - feeling.night) * step;
            feeling.rain += (rain - feeling.rain) * step;
            feeling.heat += (heat - feeling.heat) * step;
        }
        feeling.lastTime = now;
        return feeling;
    }

    /** Drops what the companions of entities not drawn for a while felt. */
    private static void forgetOld(long now) {
        if (FEELINGS.size() < 32) return;
        for (Iterator<Feeling> it = FEELINGS.values()
            .iterator(); it.hasNext();) {
            if (now - it.next().lastTime > FORGET) it.remove();
        }
    }

    private static float ramp(float value, float from, float to) {
        return Math.max(0, Math.min(1, (value - from) / (to - from)));
    }
}
