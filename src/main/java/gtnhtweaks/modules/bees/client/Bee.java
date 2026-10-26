package gtnhtweaks.modules.bees.client;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeEffect;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeRoot;
import forestry.api.core.EnumTemperature;
import forestry.api.genetics.EnumTolerance;
import gtnhtweaks.common.model.Shapes;

/**
 * A bee item as it is drawn: the look of its species and what its genes add to it, and how it feels where it is.
 * <p>
 * Kept for each stack, since reading the genes from the stack each frame would be slow with a page of bees in NEI. What
 * it feels follows where it is with a delay of a few seconds, so it falls asleep and wakes up slowly, even when the
 * time is set by a command.
 */
final class Bee {

    /** Seconds to get most of the way to a new mood: about three times that to get there. */
    private static final float SETTLE = 0.9F;
    /** Not drawn for so long, it takes the mood where it is at once, as when it is first seen. */
    private static final long FORGET = 3_000_000_000L;

    private static final Map<ItemStack, Bee> BEES = new WeakHashMap<>();

    final SpeciesLook look;
    final EnumBeeType type;
    /** Awake at night and asleep in the day. */
    final boolean nocturnal;
    /** Flies in the rain, and likes it. */
    final boolean tolerantFlyer;
    /** Its effect is aggressive: it frowns whatever its species. */
    final boolean aggressive;
    private final EnumTemperature temperature;
    /** How many steps of temperature it bears above and below its own. */
    private final int bearsHotter, bearsColder;
    /** The tag it was read from: a new tag means new genes. */
    private final NBTTagCompound tag;

    /** How it feels, each from 0 to 1. */
    float sleep, hot, cold, rain;
    private long lastTime;

    private Bee(SpeciesLook look, EnumBeeType type, IBeeGenome genome, NBTTagCompound tag) {
        this.look = look;
        this.type = type;
        this.tag = tag;
        nocturnal = genome.getNocturnal();
        tolerantFlyer = genome.getTolerantFlyer();
        IAlleleBeeEffect effect = genome.getEffect();
        aggressive = effect != null && effect.getUID() != null
            && effect.getUID()
                .toLowerCase(Locale.ROOT)
                .contains("aggress");
        temperature = genome.getPrimary()
            .getTemperature();
        EnumTolerance tolerance = genome.getToleranceTemp();
        int up = 0, down = 0;
        if (tolerance != null && tolerance != EnumTolerance.NONE) {
            String name = tolerance.name();
            int steps = name.charAt(name.length() - 1) - '0';
            if (name.startsWith("BOTH") || name.startsWith("UP")) up = steps;
            if (name.startsWith("BOTH") || name.startsWith("DOWN")) down = steps;
        }
        bearsHotter = up;
        bearsColder = down;
    }

    /** The bee of a stack, or null if it is not a bee of Forestry or has no genes. */
    static Bee of(ItemStack stack) {
        // a bee with no tag has no genes: Forestry draws it as a plain bee
        if (stack == null || stack.getTagCompound() == null) return null;
        Bee bee = BEES.get(stack);
        if (bee != null && bee.tag == stack.getTagCompound()) return bee;
        IBeeRoot root = BeeManager.beeRoot;
        if (root == null) return null;
        EnumBeeType type = root.getType(stack);
        if (type == null || type == EnumBeeType.NONE) return null;
        IBee individual;
        try {
            individual = root.getMember(stack);
        } catch (RuntimeException e) {
            return null;
        }
        if (individual == null || individual.getGenome() == null
            || individual.getGenome()
                .getPrimary() == null)
            return null;
        IBeeGenome genome = individual.getGenome();
        bee = new Bee(SpeciesLook.of(genome.getPrimary()), type, genome, stack.getTagCompound());
        BEES.put(stack, bee);
        return bee;
    }

    /** Brings how it feels closer to how it feels where the entity is, or where the player is for the inventory. */
    void feel(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (entity == null) entity = mc.thePlayer;
        if (world == null || entity == null || entity.worldObj != world) {
            calm();
            return;
        }
        int x = MathHelper.floor_double(entity.posX);
        int y = MathHelper.floor_double(entity.posY);
        int z = MathHelper.floor_double(entity.posZ);
        if (!world.blockExists(x, y, z)) {
            calm();
            return;
        }
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);

        float night = 0;
        boolean sky = world.provider.isSurfaceWorld() && !world.provider.hasNoSky;
        if (sky) {
            // 0.25 is sunset, 0.5 midnight, 0.75 sunrise
            float angle = world.getCelestialAngle(1);
            night = Math.min(ramp(angle, 0.25F, 0.3F), 1 - ramp(angle, 0.7F, 0.75F));
        }
        float wantSleep = !sky ? 0 : nocturnal ? 1 - night : night;

        // too hot or too cold for it, past what it bears: half at one step past, all at two
        float wantHot = 0, wantCold = 0;
        if (temperature != null && temperature != EnumTemperature.NONE) {
            EnumTemperature here = EnumTemperature.getFromBiome(biome, x, y, z);
            int steps = here.ordinal() - temperature.ordinal();
            wantHot = Shapes.clamp((steps - bearsHotter) / 2F);
            wantCold = Shapes.clamp((-steps - bearsColder) / 2F);
        }

        // only rain, not snow, and only falling on it: under the open sky
        float wantRain = 0;
        if (biome.canSpawnLightningBolt() && world.canBlockSeeTheSky(x, y, z)
            && world.getPrecipitationHeight(x, z) <= y + 1) {
            wantRain = world.getRainStrength(1);
        }
        follow(wantSleep, wantHot, wantCold, wantRain);
    }

    /** Feels nothing in particular, with no world to feel. */
    void calm() {
        follow(0, 0, 0, 0);
    }

    private void follow(float wantSleep, float wantHot, float wantCold, float wantRain) {
        long now = System.nanoTime();
        if (lastTime == 0 || now - lastTime > FORGET) {
            sleep = wantSleep;
            hot = wantHot;
            cold = wantCold;
            rain = wantRain;
        } else {
            float step = 1 - (float) Math.exp(-(now - lastTime) / 1e9 / SETTLE);
            sleep += (wantSleep - sleep) * step;
            hot += (wantHot - hot) * step;
            cold += (wantCold - cold) * step;
            rain += (wantRain - rain) * step;
        }
        lastTime = now;
    }

    private static float ramp(float value, float from, float to) {
        return Math.max(0, Math.min(1, (value - from) / (to - from)));
    }
}
