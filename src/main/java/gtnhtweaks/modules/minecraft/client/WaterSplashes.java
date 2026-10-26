package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityBubbleFX;
import net.minecraft.client.particle.EntitySplashFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL20;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Splashes of the items, mobs and players falling into water, as some mods of newer versions make them: a crown of
 * pixel spray rising from the surface and falling back, droplets and bubbles, and rings of small waves spreading on
 * the surface and fading away. Their color is the one of the water of the biome, their brightness the light there.
 * <p>
 * Only on the client: every tick the entities around the player are checked, and an entity whose feet were above the
 * water and now are in it, falling fast enough, makes a splash as big as its speed and its width. An entity bobbing on
 * the surface or floating in a stream does not: it is too slow, and every entity waits a second between two splashes.
 */
public final class WaterSplashes {

    /** Splashes farther from the player are not made. */
    private static final double MAX_DISTANCE = 48;
    /** Slowest fall in blocks per tick that makes a splash. */
    private static final double MIN_SPEED = 0.1;
    /** Ticks an entity waits between two splashes. */
    private static final int COOLDOWN = 20;
    /** Ticks a ring of waves lives. */
    private static final int RING_LIFE = 28;
    /** Ticks between the rings of a splash. */
    private static final int RING_GAP = 4;

    private static final class Tracked {

        double lastBottom;
        boolean inWater;
        int cooldown;
        long seen;
    }

    private static final class Splash {

        final double x, y, z;
        /** 0 to 1: a small item to a player jumping from high. */
        final float size;
        final float width;
        final int rings;
        final int crownLife;
        final float red, green, blue;
        int age;

        Splash(double x, double y, double z, float size, float width, int color, float light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.width = width;
            this.rings = 1 + Math.round(size * 2);
            this.crownLife = 10 + Math.round(size * 6);
            this.red = (color >> 16 & 0xFF) / 255F * light;
            this.green = (color >> 8 & 0xFF) / 255F * light;
            this.blue = (color & 0xFF) / 255F * light;
        }

        int life() {
            return Math.max(crownLife, (rings - 1) * RING_GAP + RING_LIFE);
        }
    }

    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static World world;
    private static long tick;
    private static DynamicTexture ringTexture, sprayTexture;

    private WaterSplashes() {}

    public static void register() {
        WaterSplashes splashes = new WaterSplashes();
        MinecraftForge.EVENT_BUS.register(splashes);
        FMLCommonHandler.instance()
            .bus()
            .register(splashes);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world) {
            world = mc.theWorld;
            TRACKED.clear();
            SPLASHES.clear();
        }
        if (world == null || mc.thePlayer == null || mc.isGamePaused()) return;
        tick++;

        for (Iterator<Splash> it = SPLASHES.iterator(); it.hasNext();) {
            Splash splash = it.next();
            if (++splash.age > splash.life()) it.remove();
        }

        boolean on = MinecraftModule.waterSplashes && mc.gameSettings.particleSetting < 2;
        if (!on) {
            TRACKED.clear();
            return;
        }
        double maxDistance = MAX_DISTANCE * MAX_DISTANCE;
        List<?> entities = world.loadedEntityList;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = (Entity) entities.get(i);
            if (!splashes(entity) || entity.getDistanceSqToEntity(mc.thePlayer) > maxDistance) continue;
            check(entity);
        }
        TRACKED.values()
            .removeIf(tracked -> tracked.seen != tick);
    }

    private static boolean splashes(Entity entity) {
        if (entity.isDead || entity.ridingEntity != null) return false;
        if (entity instanceof EntityItem) return MinecraftModule.splashItems;
        if (entity instanceof EntityPlayer) return MinecraftModule.splashPlayers;
        return entity instanceof EntityLivingBase && MinecraftModule.splashMobs;
    }

    private static void check(Entity entity) {
        double bottom = entity.boundingBox.minY;
        int x = MathHelper.floor_double(entity.posX);
        int z = MathHelper.floor_double(entity.posZ);
        int y = MathHelper.floor_double(bottom);
        double surface = surface(x, y, z);
        boolean inWater = !Double.isNaN(surface) && bottom <= surface;

        Tracked tracked = TRACKED.get(entity.getEntityId());
        if (tracked == null) {
            // first seen: no splash for the entities already in the water when they come into view
            tracked = new Tracked();
            TRACKED.put(entity.getEntityId(), tracked);
        } else {
            double speed = tracked.lastBottom - bottom;
            if (inWater && !tracked.inWater
                && tracked.cooldown == 0
                && speed >= MIN_SPEED
                && SPLASHES.size() < MinecraftModule.maxSplashes) {
                splash(entity, x, y, z, surface, speed);
                tracked.cooldown = COOLDOWN;
            }
        }
        if (tracked.cooldown > 0) tracked.cooldown--;
        tracked.lastBottom = bottom;
        tracked.inWater = inWater;
        tracked.seen = tick;
    }

    /** Height of the water surface over the column of the block, or NaN if the block is not water. */
    private static double surface(int x, int y, int z) {
        if (world.getBlock(x, y, z)
            .getMaterial() != Material.water) return Double.NaN;
        int top = y;
        // the water under the feet can be deeper than one block: its top is the surface
        while (top < y + 4 && world.getBlock(x, top + 1, z)
            .getMaterial() == Material.water) top++;
        if (top == y + 4) return Double.NaN;
        return top + 1 - BlockLiquid.getLiquidHeightPercent(world.getBlockMetadata(x, top, z));
    }

    private static void splash(Entity entity, int x, int y, int z, double surface, double speed) {
        Minecraft mc = Minecraft.getMinecraft();
        float width = Math.max(0.25F, entity.width);
        float size = (float) Math.min(1.0, speed / 0.9 * (0.6 + width * 0.7));
        size = Math.max(0.15F, size);
        int top = MathHelper.floor_double(surface);
        int color = tint(Blocks.water, x, top, z);
        float light = Math.max(0.25F, world.getLightBrightness(x, top + 1, z));
        Splash splash = new Splash(entity.posX, surface, entity.posZ, size, width, color, light);
        SPLASHES.add(splash);

        boolean fewer = mc.gameSettings.particleSetting == 1;
        int droplets = Math.round((4 + size * 18) * (fewer ? 0.5F : 1F));
        for (int i = 0; i < droplets; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double out = (0.03 + RANDOM.nextDouble() * 0.1) * (0.5 + size);
            double radius = width * 0.5 * RANDOM.nextDouble();
            EntitySplashFX drop = new EntitySplashFX(
                world,
                splash.x + Math.cos(angle) * radius,
                surface + 0.1,
                splash.z + Math.sin(angle) * radius,
                0,
                0,
                0);
            drop.motionX = Math.cos(angle) * out;
            drop.motionY = (0.12 + RANDOM.nextDouble() * 0.2) * (0.5 + size);
            drop.motionZ = Math.sin(angle) * out;
            mc.effectRenderer.addEffect(drop);
        }
        if (fewer) return;
        int bubbles = Math.round(2 + size * 8);
        for (int i = 0; i < bubbles; i++) {
            mc.effectRenderer.addEffect(
                new EntityBubbleFX(
                    world,
                    splash.x + (RANDOM.nextDouble() - 0.5) * width,
                    surface - 0.2 - RANDOM.nextDouble() * 0.6,
                    splash.z + (RANDOM.nextDouble() - 0.5) * width,
                    (RANDOM.nextDouble() - 0.5) * 0.05,
                    0.05 + RANDOM.nextDouble() * 0.05,
                    (RANDOM.nextDouble() - 0.5) * 0.05));
        }
    }

    /** The light blue of foam multiplied by the color of the water there, the one of its biome. */
    private static int tint(Block water, int x, int y, int z) {
        int multiplier = water.colorMultiplier(world, x, y, z);
        int foam = 0xDCEEFF;
        int r = (foam >> 16 & 0xFF) * (multiplier >> 16 & 0xFF) / 255;
        int g = (foam >> 8 & 0xFF) * (multiplier >> 8 & 0xFF) / 255;
        int b = (foam & 0xFF) * (multiplier & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (SPLASHES.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        makeTextures();
        float partialTicks = event.partialTicks;

        int program = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_DEPTH_BUFFER_BIT);
        mc.entityRenderer.disableLightmap(partialTicks);
        glPushMatrix();
        glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.004F);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);

        Tessellator tessellator = Tessellator.instance;
        glBindTexture(GL_TEXTURE_2D, ringTexture.getGlTextureId());
        tessellator.startDrawingQuads();
        for (Splash splash : SPLASHES) drawRings(tessellator, splash, splash.age + partialTicks);
        tessellator.draw();

        glBindTexture(GL_TEXTURE_2D, sprayTexture.getGlTextureId());
        tessellator.startDrawingQuads();
        for (Splash splash : SPLASHES) drawCrown(tessellator, splash, splash.age + partialTicks);
        tessellator.draw();

        glPopMatrix();
        mc.entityRenderer.enableLightmap(partialTicks);
        glPopAttrib();
        GL20.glUseProgram(program);
    }

    /** Rings of waves spreading from the splash and fading away, one after the other. */
    private static void drawRings(Tessellator tessellator, Splash splash, float age) {
        float maxRadius = splash.width * 0.6F + 0.6F + splash.size * 1.6F;
        for (int ring = 0; ring < splash.rings; ring++) {
            float t = (age - ring * RING_GAP) / RING_LIFE;
            if (t <= 0 || t >= 1) continue;
            // fast at first, slower as it spreads, like a wave
            float radius = splash.width * 0.4F + maxRadius * (1 - (1 - t) * (1 - t)) * (1 - ring * 0.15F);
            float alpha = (1 - t) * (1 - t) * (0.55F + splash.size * 0.35F);
            double y = splash.y + 0.01 + ring * 0.002;
            tessellator.setColorRGBA_F(splash.red, splash.green, splash.blue, alpha);
            tessellator.addVertexWithUV(splash.x - radius, y, splash.z - radius, 0, 0);
            tessellator.addVertexWithUV(splash.x - radius, y, splash.z + radius, 0, 1);
            tessellator.addVertexWithUV(splash.x + radius, y, splash.z + radius, 1, 1);
            tessellator.addVertexWithUV(splash.x + radius, y, splash.z - radius, 1, 0);
        }
    }

    /** A crown of spray around the splash: it rises fast, falls back slower and fades at the end. */
    private static void drawCrown(Tessellator tessellator, Splash splash, float age) {
        float t = age / splash.crownLife;
        if (t >= 1) return;
        float rise = t < 0.3F ? t / 0.3F : 1 - (t - 0.3F) / 0.7F;
        float height = (0.25F + splash.size * 1.1F) * (float) Math.sin(rise * Math.PI / 2);
        if (height < 0.01F) return;
        float bottomRadius = splash.width * 0.5F + 0.1F + t * 0.25F * (0.5F + splash.size);
        float topRadius = bottomRadius * (1.25F + t * 0.4F);
        float alpha = (t < 0.6F ? 1 : 1 - (t - 0.6F) / 0.4F) * 0.9F;
        int segments = splash.size > 0.5F ? 12 : 8;
        // the texture goes twice around, from the surface at the bottom of it to the tips at the top
        float uStep = 2F / segments;
        double y = splash.y;
        tessellator.setColorRGBA_F(splash.red, splash.green, splash.blue, alpha);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            float u0 = i * uStep, u1 = u0 + uStep;
            tessellator.addVertexWithUV(splash.x + c0 * bottomRadius, y, splash.z + s0 * bottomRadius, u0, 1);
            tessellator.addVertexWithUV(splash.x + c1 * bottomRadius, y, splash.z + s1 * bottomRadius, u1, 1);
            tessellator.addVertexWithUV(splash.x + c1 * topRadius, y + height, splash.z + s1 * topRadius, u1, 0);
            tessellator.addVertexWithUV(splash.x + c0 * topRadius, y + height, splash.z + s0 * topRadius, u0, 0);
        }
    }

    /** Pixel textures in the style of the game, drawn once: a ring of waves and the spray of the crown. */
    private static void makeTextures() {
        if (ringTexture != null) return;
        ringTexture = new DynamicTexture(32, 32);
        int[] ring = ringTexture.getTextureData();
        Random random = new Random(4);
        for (int py = 0; py < 32; py++) {
            for (int px = 0; px < 32; px++) {
                double distance = Math.sqrt((px - 15.5) * (px - 15.5) + (py - 15.5) * (py - 15.5));
                int argb = 0;
                if (distance >= 13.5 && distance < 15.5) {
                    // a ring two pixels wide, brighter outside, with a few gaps like foam
                    if (random.nextInt(9) != 0) argb = (distance >= 14.5 ? 0xFF : 0xA0) << 24 | 0xFFFFFF;
                } else if (distance >= 12.5 && distance < 13.5 && random.nextInt(3) == 0) {
                    argb = 0x60FFFFFF;
                }
                ring[py * 32 + px] = argb;
            }
        }
        ringTexture.updateDynamicTexture();

        sprayTexture = new DynamicTexture(16, 16);
        int[] spray = sprayTexture.getTextureData();
        random = new Random(7);
        for (int py = 0; py < 16; py++) {
            // py 0 is the top of the crown: few drops there, dense foam at the surface
            float density = 0.12F + 0.88F * (float) Math.pow(py / 15F, 1.4);
            for (int px = 0; px < 16; px++) {
                int argb = 0;
                if (random.nextFloat() < density) {
                    int alpha = py > 12 ? 0xE0 : 0xB0 + random.nextInt(0x40);
                    int shade = random.nextInt(4) == 0 ? 0xC8E4FF : 0xFFFFFF;
                    argb = alpha << 24 | shade;
                }
                spray[py * 16 + px] = argb;
            }
        }
        sprayTexture.updateDynamicTexture();
    }
}
