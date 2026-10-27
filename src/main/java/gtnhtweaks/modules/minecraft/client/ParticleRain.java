package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL20;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Weather made of particles instead of the sheets of streaks of vanilla:
 * <ul>
 * <li>rain falls as separate drops, a bit slanted by the wind, that burst into a few splashes where they hit the
 * ground; on water the rain ripples of the splashes make the rest;</li>
 * <li>snow falls as flakes of two sizes that sway down slowly, lie on the ground for a moment and melt;</li>
 * <li>in sandy biomes without rain, deserts and mesas, the wind carries sand dust along the ground instead.</li>
 * </ul>
 * Which one falls is decided for every column as vanilla decides it, by the biome and its temperature at that height,
 * and nothing falls under a roof. The amount follows the strength of the rain. The particles take the light of where
 * they are, so the rain is dark at night. Worlds with a weather renderer of their own keep it.
 */
public final class ParticleRain {

    private static final int RAIN = 0;
    private static final int SPLASH = 1;
    private static final int SNOW = 2;
    private static final int DUST = 3;

    /** Columns tried per tick in full rain at 100 %; how far from the player, around and above. */
    private static final int TRIES = 140;
    private static final int REACH = 20;
    private static final int HEIGHT = 18;
    /** Of the columns tried, the part used for snow and dust: they live much longer than drops. */
    private static final float SNOW_PART = 0.13F;
    private static final float DUST_PART = 0.07F;
    private static final int MAX_PARTICLES = 4000;

    private static final class Particle {

        final int kind;
        double x, y, z, prevX, prevY, prevZ, motionX, motionY, motionZ;
        float red, green, blue, alpha, size;
        int age, life;
        boolean landed;
        final float phase;

        Particle(int kind, double x, double y, double z) {
            this.kind = kind;
            this.x = prevX = x;
            this.y = prevY = y;
            this.z = prevZ = z;
            this.phase = RANDOM.nextFloat() * 6.2832F;
        }

        void color(float red, float green, float blue, float light) {
            this.red = red * light;
            this.green = green * light;
            this.blue = blue * light;
        }
    }

    private static final Random RANDOM = new Random();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    /** Splashes made while the particles move, added to them after the move, not while they are gone through. */
    private static final List<Particle> BORN = new ArrayList<>();
    private static World world;
    /** The wind, the same for all, turning slowly: blocks per tick at full strength. */
    private static float windAngle = RANDOM.nextFloat() * 6.2832F;
    private static double windX, windZ;

    private ParticleRain() {}

    public static void register() {
        ParticleRain rain = new ParticleRain();
        MinecraftForge.EVENT_BUS.register(rain);
        FMLCommonHandler.instance()
            .bus()
            .register(rain);
    }

    /** True if the rain and snow of vanilla are not drawn, as the particles take their place. */
    public static boolean replacesVanilla() {
        Minecraft mc = Minecraft.getMinecraft();
        return active(mc) && MinecraftModule.hideVanillaRain;
    }

    private static boolean active(Minecraft mc) {
        return MinecraftModule.particleRain > 0 && mc.theWorld != null
            && mc.gameSettings.particleSetting < 2
            && mc.theWorld.provider.getWeatherRenderer() == null;
    }

    // ---- moving

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world) {
            world = mc.theWorld;
            PARTICLES.clear();
        }
        if (world == null || mc.isGamePaused()) return;

        windAngle += (RANDOM.nextFloat() - 0.5F) * 0.01F;
        double wind = world.isThundering() ? 0.12 : 0.06;
        windX = Math.cos(windAngle) * wind;
        windZ = Math.sin(windAngle) * wind;

        for (Iterator<Particle> it = PARTICLES.iterator(); it.hasNext();) {
            if (!move(it.next())) it.remove();
        }
        int room = MAX_PARTICLES - PARTICLES.size();
        PARTICLES.addAll(BORN.size() <= room ? BORN : BORN.subList(0, Math.max(0, room)));
        BORN.clear();
        if (active(mc) && world.isRaining()) spawn(mc);
    }

    /** Moves a particle one tick; false when it is gone. */
    private static boolean move(Particle p) {
        p.prevX = p.x;
        p.prevY = p.y;
        p.prevZ = p.z;
        p.age++;
        switch (p.kind) {
            case RAIN: {
                p.x += p.motionX;
                p.y += p.motionY;
                p.z += p.motionZ;
                int top = world.getPrecipitationHeight(MathHelper.floor_double(p.x), MathHelper.floor_double(p.z));
                if (p.y > top) return p.age < 200;
                // on water the ripples of the rain do the rest, on the ground it bursts
                if (!world.getBlock(MathHelper.floor_double(p.x), top - 1, MathHelper.floor_double(p.z))
                    .getMaterial()
                    .isLiquid()) burst(p, top);
                return false;
            }
            case SPLASH:
                p.motionY -= 0.04;
                p.x += p.motionX;
                p.y += p.motionY;
                p.z += p.motionZ;
                p.alpha = 0.8F * (1 - p.age / (float) p.life);
                return p.age < p.life;
            case SNOW: {
                if (p.landed) {
                    p.alpha = Math.min(0.95F, (p.life - p.age) / 20F);
                    return p.age < p.life;
                }
                // sways down, carried a little by the wind
                p.x += windX * 0.4 + MathHelper.sin(p.age * 0.12F + p.phase) * 0.02;
                p.y += p.motionY;
                p.z += windZ * 0.4 + MathHelper.cos(p.age * 0.1F + p.phase) * 0.02;
                int x = MathHelper.floor_double(p.x), z = MathHelper.floor_double(p.z);
                int top = world.getPrecipitationHeight(x, z);
                if (p.y <= top) {
                    if (world.getBlock(x, top - 1, z)
                        .getMaterial()
                        .isLiquid()) return false;
                    p.y = top + 0.02;
                    p.landed = true;
                    p.life = p.age + 30 + RANDOM.nextInt(40);
                }
                return p.age < 600;
            }
            default: {
                // dust is blown along the ground, bobbing, and fades in and out
                p.x += p.motionX;
                p.y += MathHelper.sin(p.age * 0.15F + p.phase) * 0.015;
                p.z += p.motionZ;
                float t = p.age / (float) p.life;
                p.alpha = 0.45F * Math.min(1, Math.min(t * 5, (1 - t) * 5));
                int top = world.getPrecipitationHeight(MathHelper.floor_double(p.x), MathHelper.floor_double(p.z));
                return p.age < p.life && p.y > top;
            }
        }
    }

    /** A drop hitting the ground: a few bits of water bounce up and out. */
    private static void burst(Particle drop, int top) {
        int count = 2 + RANDOM.nextInt(2);
        for (int i = 0; i < count; i++) {
            Particle bit = new Particle(SPLASH, drop.x, top + 0.05, drop.z);
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double out = 0.03 + RANDOM.nextDouble() * 0.05;
            bit.motionX = Math.cos(angle) * out;
            bit.motionY = 0.1 + RANDOM.nextDouble() * 0.08;
            bit.motionZ = Math.sin(angle) * out;
            bit.red = Math.min(1, drop.red * 1.15F);
            bit.green = Math.min(1, drop.green * 1.15F);
            bit.blue = Math.min(1, drop.blue * 1.15F);
            bit.alpha = 0.8F;
            bit.size = 0.025F;
            bit.life = 6 + RANDOM.nextInt(5);
            BORN.add(bit);
        }
    }

    private static void spawn(Minecraft mc) {
        Entity viewer = mc.renderViewEntity;
        if (viewer == null) return;
        float strength = world.getRainStrength(1F) * MinecraftModule.particleRain / 100F;
        if (mc.gameSettings.particleSetting == 1) strength *= 0.5F;
        int tries = Math.round(TRIES * strength);
        int centerX = MathHelper.floor_double(viewer.posX);
        int centerZ = MathHelper.floor_double(viewer.posZ);
        for (int i = 0; i < tries && PARTICLES.size() < MAX_PARTICLES; i++) {
            int x = centerX + RANDOM.nextInt(REACH * 2 + 1) - REACH;
            int z = centerZ + RANDOM.nextInt(REACH * 2 + 1) - REACH;
            int top = world.getPrecipitationHeight(x, z);
            BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
            double px = x + RANDOM.nextDouble(), pz = z + RANDOM.nextDouble();
            float light;
            if (biome.canSpawnLightningBolt() || biome.getEnableSnow()) {
                double y = viewer.posY + 2 + RANDOM.nextDouble() * HEIGHT;
                // nothing falls under a roof
                if (y <= top) continue;
                light = light(px, y, pz);
                if (biome.getFloatTemperature(x, top, z) < 0.15F) {
                    if (RANDOM.nextFloat() >= SNOW_PART) continue;
                    Particle flake = new Particle(SNOW, px, y, pz);
                    flake.motionY = -0.05 - RANDOM.nextDouble() * 0.04;
                    flake.size = RANDOM.nextFloat() < 0.35F ? 0.06F : 0.035F;
                    flake.alpha = 0.95F;
                    flake.color(1F, 1F, 1F, light);
                    PARTICLES.add(flake);
                } else {
                    Particle drop = new Particle(RAIN, px, y, pz);
                    drop.motionX = windX;
                    drop.motionY = -0.85 - RANDOM.nextDouble() * 0.25;
                    drop.motionZ = windZ;
                    drop.alpha = 0.55F;
                    drop.color(0.62F, 0.72F, 0.92F, light);
                    PARTICLES.add(drop);
                }
            } else if (biome.topBlock == Blocks.sand) {
                if (RANDOM.nextFloat() >= DUST_PART) continue;
                double ground = Math.max(top, viewer.posY - 3);
                double y = ground + RANDOM.nextDouble() * 5;
                if (y <= top) continue;
                Particle dust = new Particle(DUST, px, y, pz);
                double speed = 1.2 + RANDOM.nextDouble() * 1.2;
                dust.motionX = windX * speed;
                dust.motionZ = windZ * speed;
                dust.size = RANDOM.nextFloat() < 0.3F ? 0.09F : 0.04F;
                dust.life = 60 + RANDOM.nextInt(60);
                light = light(px, y, pz);
                // the red sand of mesas, the yellow one of deserts
                boolean mesa = biome.biomeName != null && biome.biomeName.startsWith("Mesa");
                if (mesa) dust.color(0.78F, 0.47F, 0.27F, light);
                else dust.color(0.86F, 0.77F, 0.54F, light);
                PARTICLES.add(dust);
            }
        }
    }

    private static float light(double x, double y, double z) {
        return Math.max(0.15F, world.getLightBrightness(
            MathHelper.floor_double(x),
            MathHelper.floor_double(y),
            MathHelper.floor_double(z)));
    }

    // ---- drawing

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (PARTICLES.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        float partialTicks = event.partialTicks;
        int program = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_DEPTH_BUFFER_BIT);
        mc.entityRenderer.disableLightmap(partialTicks);
        glPushMatrix();
        glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_ALPHA_TEST);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);

        // the right of the camera, and its up, for the particles facing it
        float rightX = ActiveRenderInfo.rotationX, rightZ = ActiveRenderInfo.rotationZ;
        float upX = ActiveRenderInfo.rotationYZ, upY = ActiveRenderInfo.rotationXZ, upZ = ActiveRenderInfo.rotationXY;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (Particle p : PARTICLES) {
            double x = p.prevX + (p.x - p.prevX) * partialTicks;
            double y = p.prevY + (p.y - p.prevY) * partialTicks;
            double z = p.prevZ + (p.z - p.prevZ) * partialTicks;
            tessellator.setColorRGBA_F(p.red, p.green, p.blue, p.alpha);
            if (p.kind == RAIN) {
                // a thin streak along its fall, turned to face the camera around the vertical
                double w = 0.012;
                double topX = x - p.motionX * 0.45, topY = y - p.motionY * 0.45, topZ = z - p.motionZ * 0.45;
                tessellator.addVertex(x - rightX * w, y, z - rightZ * w);
                tessellator.addVertex(x + rightX * w, y, z + rightZ * w);
                tessellator.addVertex(topX + rightX * w, topY, topZ + rightZ * w);
                tessellator.addVertex(topX - rightX * w, topY, topZ - rightZ * w);
            } else {
                float s = p.size;
                tessellator.addVertex(x - rightX * s - upX * s, y - upY * s, z - rightZ * s - upZ * s);
                tessellator.addVertex(x - rightX * s + upX * s, y + upY * s, z - rightZ * s + upZ * s);
                tessellator.addVertex(x + rightX * s + upX * s, y + upY * s, z + rightZ * s + upZ * s);
                tessellator.addVertex(x + rightX * s - upX * s, y - upY * s, z + rightZ * s - upZ * s);
            }
        }
        tessellator.draw();

        glPopMatrix();
        mc.entityRenderer.enableLightmap(partialTicks);
        glPopAttrib();
        GL20.glUseProgram(program);
    }
}
