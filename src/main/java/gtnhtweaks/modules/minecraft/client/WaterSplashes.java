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
 * Splashes of the items, mobs and players falling into water, in the look of the splashes of newer versions' mods:
 * <ul>
 * <li>a square wall of water rises around the place of the fall, its top edge in pixel waves outlined with white
 * foam; it grows, falls back and breaks into spray, while it slowly widens;</li>
 * <li>a second, narrower and twice as high, jet rises in the middle a bit later;</li>
 * <li>a square ring of foam lies on the surface under the wall and breaks into its corners at the end;</li>
 * <li>pixel droplets fly out and fall back, and each one makes a little ripple ring where it falls into water.</li>
 * </ul>
 * The wall is as wide as twice the entity and as high as its speed times that width, so a dropped item makes a small
 * splash and a player jumping from high a big one. Its color is the one of the water of the biome, its brightness the
 * light there. The animation frames are drawn by code, once.
 * <p>
 * Only on the client: every tick the entities around the player are checked, and an entity whose feet were above the
 * water and now are in it, moving fast enough, makes a splash. An entity bobbing on the surface or floating in a
 * stream does not: it is too slow, and every entity waits a second between two splashes.
 */
public final class WaterSplashes {

    /** Splashes farther from the player are not made. */
    private static final double MAX_DISTANCE = 48;
    /** Slowest move in blocks per tick that makes a splash. */
    private static final double MIN_SPEED = 0.3;
    /** Ticks an entity waits between two splashes. */
    private static final int COOLDOWN = 20;

    /** Frames of the animation of the wall and the ring of foam: 0 to FRAMES - 1. */
    private static final int FRAMES = 13;
    /** Pixels of a side of the wall: wide and high; the 4 sides lie next to each other in a frame. */
    private static final int SIDE = 12;
    private static final int WALL_HEIGHT = 16;
    /** Width of a frame cell in the atlases. */
    private static final int CELL = 64;
    private static final int RING_CELL = 16;
    /** Half the width of the wall per unit of width, in blocks. */
    private static final float HALF_WIDTH = 0.375F;
    /** The wall widens by this part every tick. */
    private static final float WIDEN = 1.03F;
    /** The second jet: part of the width, times the height. */
    private static final float JET_WIDTH = 0.5F;
    private static final float JET_HEIGHT = 2F;

    /** Droplets: gravity and drag per tick, half their size in blocks, most at the same time. */
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;
    private static final double DROP_HALF = 0.03;
    private static final int MAX_DROPS = 600;
    private static final int DROP_LIFE = 100;

    private static final class Tracked {

        double lastX, lastBottom, lastZ;
        boolean inWater;
        int cooldown;
        long seen;
    }

    private static final class Splash {

        final double x, y, z;
        /** Width of the wall in units of {@link #HALF_WIDTH} * 2, widening every tick. */
        float width;
        final float height;
        final int wave1End, wave2Start, wave2End;
        final float red, green, blue, light;
        int age;

        Splash(double x, double y, double z, float width, float height, int color, float light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.wave1End = 10 + Math.round(width * 1.2F);
            this.wave2Start = 6 + Math.round(width * 0.7F);
            this.wave2End = 20 + Math.round(width * 2.4F);
            this.light = light;
            this.red = (color >> 16 & 0xFF) / 255F * light;
            this.green = (color >> 8 & 0xFF) / 255F * light;
            this.blue = (color & 0xFF) / 255F * light;
        }
    }

    private static final class Drop {

        double x, y, z, prevX, prevY, prevZ, motionX, motionY, motionZ;
        final float red, green, blue;
        int age;

        Drop(double x, double y, double z, double motionX, double motionY, double motionZ, Splash splash) {
            this.x = prevX = x;
            this.y = prevY = y;
            this.z = prevZ = z;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            // lighter than the water: drops catch the light
            red = Math.min(1, splash.red * 0.5F + splash.light * 0.5F);
            green = Math.min(1, splash.green * 0.5F + splash.light * 0.5F);
            blue = Math.min(1, splash.blue * 0.5F + splash.light * 0.5F);
        }
    }

    private static final class Ripple {

        final double x, y, z;
        final float size, red, green, blue;
        final int life;
        int age;

        Ripple(double x, double y, double z, Drop drop) {
            this.x = x;
            this.y = y;
            this.z = z;
            int spread = 1 + RANDOM.nextInt(10);
            this.size = 0.3F + RANDOM.nextFloat() * 0.015F * spread;
            this.life = 10 + RANDOM.nextInt(spread);
            this.red = drop.red;
            this.green = drop.green;
            this.blue = drop.blue;
        }
    }

    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final List<Drop> DROPS = new ArrayList<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static World world;
    private static long tick;
    private static DynamicTexture wallTexture, foamTexture, rippleTexture;

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
            DROPS.clear();
            RIPPLES.clear();
        }
        if (world == null || mc.thePlayer == null || mc.isGamePaused()) return;
        tick++;

        tickSplashes(mc);
        tickDrops();
        for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext();) {
            Ripple ripple = it.next();
            if (++ripple.age >= ripple.life) it.remove();
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

    private static void tickSplashes(Minecraft mc) {
        float drops = mc.gameSettings.particleSetting == 1 ? 0.5F : 1F;
        for (Iterator<Splash> it = SPLASHES.iterator(); it.hasNext();) {
            Splash splash = it.next();
            splash.width *= WIDEN;
            splash.age++;
            if (splash.age == 1) {
                // the spray of the wall: out to the sides and up
                int count = Math.round(splash.width * 10 * drops);
                for (int i = 0; i < count; i++) {
                    addDrop(
                        splash,
                        splash.width / 5,
                        signed() / 5 * splash.width / 2.5,
                        RANDOM.nextFloat() / 10 + splash.height / 2.8,
                        signed() / 10 * splash.width / 2.5);
                }
            } else if (splash.age == splash.wave2Start) {
                // the spray of the jet: higher and closer to the middle
                int count = Math.round(splash.width * 5 * drops);
                for (int i = 0; i < count; i++) {
                    addDrop(
                        splash,
                        splash.width / 5 * JET_WIDTH,
                        signed() / 10 * splash.width / 5,
                        RANDOM.nextFloat() / 10 + splash.height / 2.2,
                        signed() / 10 * splash.width / 5);
                }
            }
            if (splash.age >= splash.wave2End) it.remove();
        }
    }

    /** A droplet thrown from up to {@code spread} blocks around the middle of the splash. */
    private static void addDrop(Splash splash, double spread, double motionX, double motionY, double motionZ) {
        if (DROPS.size() >= MAX_DROPS) return;
        double x = splash.x + signed() * spread, z = splash.z + signed() * spread;
        DROPS.add(new Drop(x, splash.y, z, motionX, motionY, motionZ, splash));
    }

    private static void tickDrops() {
        for (Iterator<Drop> it = DROPS.iterator(); it.hasNext();) {
            Drop drop = it.next();
            drop.prevX = drop.x;
            drop.prevY = drop.y;
            drop.prevZ = drop.z;
            if (++drop.age >= DROP_LIFE) {
                it.remove();
                continue;
            }
            double nextY = drop.y + drop.motionY;
            int x = MathHelper.floor_double(drop.x);
            int z = MathHelper.floor_double(drop.z);
            Block next = world.getBlock(x, MathHelper.floor_double(nextY), z);
            if (next.getMaterial() == Material.water && drop.motionY < 0) {
                // falls into water: a ripple on its surface
                int y = MathHelper.floor_double(nextY);
                double surface = surface(x, y, z);
                if (!Double.isNaN(surface) && drop.y >= surface - 0.5) {
                    RIPPLES.add(new Ripple(drop.x, surface, drop.z, drop));
                }
                it.remove();
                continue;
            }
            if (next.getMaterial()
                .isSolid()) {
                it.remove();
                continue;
            }
            drop.motionX *= DRAG;
            drop.motionY -= GRAVITY;
            drop.motionZ *= DRAG;
            drop.x += drop.motionX;
            drop.y = nextY;
            drop.z += drop.motionZ;
        }
    }

    /** A random number from -1 to 1. */
    private static double signed() {
        return RANDOM.nextDouble() * 2 - 1;
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
            double fall = tracked.lastBottom - bottom;
            double dx = entity.posX - tracked.lastX, dz = entity.posZ - tracked.lastZ;
            double speed = Math.sqrt(dx * dx + fall * fall + dz * dz);
            if (inWater && !tracked.inWater
                && tracked.cooldown == 0
                && fall > 0
                && speed >= MIN_SPEED
                && SPLASHES.size() < MinecraftModule.maxSplashes) {
                splash(entity, x, z, surface, fall);
                tracked.cooldown = COOLDOWN;
            }
        }
        if (tracked.cooldown > 0) tracked.cooldown--;
        tracked.lastX = entity.posX;
        tracked.lastBottom = bottom;
        tracked.lastZ = entity.posZ;
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

    private static void splash(Entity entity, int x, int z, double surface, double fall) {
        Minecraft mc = Minecraft.getMinecraft();
        float entityWidth = Math.max(0.25F, entity.width);
        float width = entityWidth * 2;
        float height = (float) Math.min(4, fall) * width;
        int top = MathHelper.floor_double(surface);
        int color = tint(Blocks.water, x, top, z);
        float light = Math.max(0.25F, world.getLightBrightness(x, top + 1, z));
        Splash splash = new Splash(entity.posX, surface, entity.posZ, width, height, color, light);
        SPLASHES.add(splash);

        // the water thrown up by the body itself, over its whole width
        int count = Math.round(entityWidth * 25 * (mc.gameSettings.particleSetting == 1 ? 0.5F : 1F));
        for (int i = 0; i < count && DROPS.size() < MAX_DROPS; i++) {
            DROPS.add(
                new Drop(
                    entity.posX + signed() * entityWidth / 5,
                    surface,
                    entity.posZ + signed() * entityWidth,
                    signed() / 15,
                    RANDOM.nextDouble() / 2.5,
                    signed() / 15,
                    splash));
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

    // ---- drawing

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (SPLASHES.isEmpty() && DROPS.isEmpty() && RIPPLES.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        makeTextures();
        float partialTicks = event.partialTicks;

        int program = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_DEPTH_BUFFER_BIT);
        mc.entityRenderer.disableLightmap(partialTicks);
        glPushMatrix();
        glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        Tessellator tessellator = Tessellator.instance;

        // the droplets are solid little cubes
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
        tessellator.startDrawingQuads();
        for (Drop drop : DROPS) drawDrop(tessellator, drop, partialTicks);
        tessellator.draw();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.004F);
        glDepthMask(false);

        glBindTexture(GL_TEXTURE_2D, rippleTexture.getGlTextureId());
        tessellator.startDrawingQuads();
        for (Ripple ripple : RIPPLES) drawRipple(tessellator, ripple, partialTicks);
        tessellator.draw();

        glBindTexture(GL_TEXTURE_2D, foamTexture.getGlTextureId());
        tessellator.startDrawingQuads();
        for (Splash splash : SPLASHES) {
            if (splash.age <= splash.wave1End) drawFoam(tessellator, splash, frame1(splash), 1, partialTicks);
            if (splash.age >= splash.wave2Start) drawFoam(tessellator, splash, frame2(splash), JET_WIDTH, partialTicks);
        }
        tessellator.draw();

        glBindTexture(GL_TEXTURE_2D, wallTexture.getGlTextureId());
        // the water of the walls in its color, then their white foam edges over it
        for (int rim = 0; rim < 2; rim++) {
            tessellator.startDrawingQuads();
            for (Splash splash : SPLASHES) {
                if (splash.age <= splash.wave1End) {
                    drawWall(tessellator, splash, frame1(splash), 1, 1, rim == 1, partialTicks);
                }
                if (splash.age >= splash.wave2Start) {
                    drawWall(tessellator, splash, frame2(splash), JET_WIDTH, JET_HEIGHT, rim == 1, partialTicks);
                }
            }
            tessellator.draw();
        }

        glPopMatrix();
        mc.entityRenderer.enableLightmap(partialTicks);
        glPopAttrib();
        GL20.glUseProgram(program);
    }

    private static int frame1(Splash splash) {
        return clampFrame(Math.round(splash.age / (float) splash.wave1End * (FRAMES - 1)));
    }

    private static int frame2(Splash splash) {
        float t = (splash.age - splash.wave2Start) / (float) (splash.wave2End - splash.wave2Start);
        return clampFrame(Math.round(t * (FRAMES - 1)));
    }

    private static int clampFrame(int frame) {
        return Math.max(0, Math.min(FRAMES - 1, frame));
    }

    private static float halfWidth(Splash splash, float widthPart, float partialTicks) {
        return HALF_WIDTH * splash.width * (1 + (WIDEN - 1) * partialTicks) * widthPart;
    }

    /**
     * The four sides of a wall, from the surface up, with a fainter copy mirrored under the surface, as seen through
     * the water.
     */
    private static void drawWall(Tessellator t, Splash splash, int frame, float widthPart, float heightPart,
        boolean rim, float partialTicks) {
        float half = halfWidth(splash, widthPart, partialTicks);
        float height = splash.height * heightPart;
        if (height < 0.01F) return;
        double v0 = rim ? 0.5 : 0, v1 = v0 + WALL_HEIGHT / 32.0;
        double[] cornerX = { -half, half, half, -half, -half };
        double[] cornerZ = { -half, -half, half, half, -half };
        for (int mirror = 0; mirror < 2; mirror++) {
            if (rim) t.setColorRGBA_F(splash.light, splash.light, splash.light, mirror == 0 ? 1 : 0.35F);
            else t.setColorRGBA_F(splash.red, splash.green, splash.blue, mirror == 0 ? 1 : 0.35F);
            double top = splash.y + (mirror == 0 ? height : -height);
            for (int side = 0; side < 4; side++) {
                double u0 = (frame * CELL + side * SIDE) / 1024.0, u1 = u0 + SIDE / 1024.0;
                t.addVertexWithUV(splash.x + cornerX[side], top, splash.z + cornerZ[side], u0, v0);
                t.addVertexWithUV(splash.x + cornerX[side + 1], top, splash.z + cornerZ[side + 1], u1, v0);
                t.addVertexWithUV(splash.x + cornerX[side + 1], splash.y, splash.z + cornerZ[side + 1], u1, v1);
                t.addVertexWithUV(splash.x + cornerX[side], splash.y, splash.z + cornerZ[side], u0, v1);
            }
        }
    }

    /** The square ring of foam on the surface, under a wall. */
    private static void drawFoam(Tessellator tessellator, Splash splash, int frame, float widthPart,
        float partialTicks) {
        float half = halfWidth(splash, widthPart, partialTicks);
        double y = splash.y + 0.002;
        double u0 = frame * RING_CELL / 256.0, u1 = u0 + SIDE / 256.0, v1 = SIDE / 16.0;
        tessellator.setColorRGBA_F(splash.light, splash.light, splash.light, 1);
        tessellator.addVertexWithUV(splash.x - half, y, splash.z - half, u0, 0);
        tessellator.addVertexWithUV(splash.x - half, y, splash.z + half, u0, v1);
        tessellator.addVertexWithUV(splash.x + half, y, splash.z + half, u1, v1);
        tessellator.addVertexWithUV(splash.x + half, y, splash.z - half, u1, 0);
    }

    private static void drawRipple(Tessellator tessellator, Ripple ripple, float partialTicks) {
        float t = (ripple.age + partialTicks) / ripple.life;
        if (t >= 1) return;
        int frame = Math.min(5, (int) (t * 6));
        float half = ripple.size * (0.5F + frame * 0.1F);
        double y = ripple.y + 0.004;
        double u0 = frame / 8.0, u1 = u0 + 1 / 8.0;
        tessellator.setColorRGBA_F(ripple.red, ripple.green, ripple.blue, 1 - t * 0.6F);
        tessellator.addVertexWithUV(ripple.x - half, y, ripple.z - half, u0, 0);
        tessellator.addVertexWithUV(ripple.x - half, y, ripple.z + half, u0, 1);
        tessellator.addVertexWithUV(ripple.x + half, y, ripple.z + half, u1, 1);
        tessellator.addVertexWithUV(ripple.x + half, y, ripple.z - half, u1, 0);
    }

    private static void drawDrop(Tessellator t, Drop drop, float partialTicks) {
        double x = drop.prevX + (drop.x - drop.prevX) * partialTicks;
        double y = drop.prevY + (drop.y - drop.prevY) * partialTicks;
        double z = drop.prevZ + (drop.z - drop.prevZ) * partialTicks;
        double h = DROP_HALF;
        // lit from above: the top is the brightest, the sides a bit darker
        for (int face = 0; face < 6; face++) {
            float shade = face == 0 ? 1 : face == 1 ? 0.6F : 0.8F;
            t.setColorOpaque_F(drop.red * shade, drop.green * shade, drop.blue * shade);
            switch (face) {
                case 0:
                    quad(t, x - h, y + h, z - h, x - h, y + h, z + h, x + h, y + h, z + h, x + h, y + h, z - h);
                    break;
                case 1:
                    quad(t, x - h, y - h, z - h, x + h, y - h, z - h, x + h, y - h, z + h, x - h, y - h, z + h);
                    break;
                case 2:
                    quad(t, x - h, y - h, z - h, x - h, y + h, z - h, x + h, y + h, z - h, x + h, y - h, z - h);
                    break;
                case 3:
                    quad(t, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y + h, z + h, x - h, y + h, z + h);
                    break;
                case 4:
                    quad(t, x - h, y - h, z - h, x - h, y - h, z + h, x - h, y + h, z + h, x - h, y + h, z - h);
                    break;
                default:
                    quad(t, x + h, y - h, z - h, x + h, y + h, z - h, x + h, y + h, z + h, x + h, y - h, z + h);
                    break;
            }
        }
    }

    private static void quad(Tessellator tessellator, double x0, double y0, double z0, double x1, double y1, double z1,
        double x2, double y2, double z2, double x3, double y3, double z3) {
        tessellator.addVertex(x0, y0, z0);
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
        tessellator.addVertex(x3, y3, z3);
    }

    // ---- the frames, drawn once

    private static void makeTextures() {
        if (wallTexture != null) return;
        wallTexture = new DynamicTexture(1024, 32);
        makeWall(wallTexture.getTextureData());
        wallTexture.updateDynamicTexture();
        foamTexture = new DynamicTexture(256, 16);
        makeFoam(foamTexture.getTextureData());
        foamTexture.updateDynamicTexture();
        rippleTexture = new DynamicTexture(128, 16);
        makeRipples(rippleTexture.getTextureData());
        rippleTexture.updateDynamicTexture();
    }

    /**
     * The frames of the wall, each 4 sides of 12 by 16 pixels: rows 0 to 15 the water, rows 16 to 31 its white edges.
     * The top edge is a row of pixel waves that rise to their highest in frame 4 and fall back; from frame 7 the wall
     * comes off the surface from below, as spray thrown up.
     */
    private static void makeWall(int[] pixels) {
        int columns = SIDE * 4;
        // the waves of the top edge: two crests on each side, of their own heights and widths
        Random random = new Random(11);
        float[] shape = new float[columns];
        for (int crest = 0; crest < 8; crest++) {
            float middle = crest * 6 + 2 + random.nextFloat() * 2;
            float reach = 2.5F + random.nextFloat() * 1.5F;
            float top = 0.65F + random.nextFloat() * 0.35F;
            for (int c = 0; c < columns; c++) {
                float distance = Math.abs(c - middle);
                distance = Math.min(distance, columns - distance);
                shape[c] = Math.max(shape[c], top * Math.max(0, 1 - distance / reach));
            }
        }
        for (int c = 0; c < columns; c++) shape[c] = 0.35F + shape[c] * 0.65F;

        for (int frame = 0; frame < FRAMES; frame++) {
            float rise = frame <= 4 ? (frame + 1) / 5F : Math.max(0, 1 - (frame - 4) / 9F);
            Random jitter = new Random(100 + frame);
            int[] tops = new int[columns];
            int[] bottoms = new int[columns];
            for (int c = 0; c < columns; c++) {
                int height = Math.round(WALL_HEIGHT * rise * shape[c]) + (jitter.nextInt(5) == 0 ? 1 : 0);
                height = Math.max(0, Math.min(WALL_HEIGHT, height));
                int lifted = frame < 7 ? 0 : Math.round((frame - 6) / 6F * height * 0.9F);
                tops[c] = WALL_HEIGHT - height;
                bottoms[c] = WALL_HEIGHT - lifted;
            }
            for (int c = 0; c < columns; c++) {
                int left = (c + columns - 1) % columns, right = (c + 1) % columns;
                for (int row = tops[c]; row < bottoms[c]; row++) {
                    // the water, clearer low down
                    int alpha = row - tops[c] < 3 ? 0xC8 : 0x90;
                    pixels[row * 1024 + frame * CELL + c] = alpha << 24 | 0xFFFFFF;
                    boolean edge = row == tops[c] || row == bottoms[c] - 1 && bottoms[c] < WALL_HEIGHT
                        || row < tops[left]
                        || row < tops[right];
                    if (edge) pixels[(row + 16) * 1024 + frame * CELL + c] = 0xFFFFFFFF;
                }
            }
        }
    }

    /**
     * The frames of the ring of foam on the surface, 12 by 12 pixels: a square ring with round corners inside, that
     * thins and from frame 9 breaks into its four corners, which shrink.
     */
    private static void makeFoam(int[] pixels) {
        for (int frame = 0; frame < FRAMES; frame++) {
            int thickness = frame < 6 ? 2 : 1;
            int corner = frame < 9 ? SIDE : 5 - (frame - 9);
            for (int y = 0; y < SIDE; y++) {
                for (int x = 0; x < SIDE; x++) {
                    int fromEdge = Math.min(Math.min(x, SIDE - 1 - x), Math.min(y, SIDE - 1 - y));
                    boolean ring = fromEdge < thickness;
                    // the round inside of the corners
                    int inX = Math.min(x, SIDE - 1 - x), inY = Math.min(y, SIDE - 1 - y);
                    if (!ring && inX == thickness && inY == thickness) ring = true;
                    if (!ring) continue;
                    int alongX = Math.min(x, SIDE - 1 - x), alongY = Math.min(y, SIDE - 1 - y);
                    // broken: only what is near a corner stays
                    if (Math.max(alongX, alongY) >= corner) continue;
                    pixels[y * 256 + frame * RING_CELL + x] = 0xFFFFFFFF;
                }
            }
        }
    }

    /** The frames of a ripple, 16 by 16 pixels: a round ring one pixel wide that widens and breaks up. */
    private static void makeRipples(int[] pixels) {
        Random random = new Random(5);
        for (int frame = 0; frame < 6; frame++) {
            double radius = 3 + frame * 1.0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double distance = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 7.5) * (y - 7.5));
                    if (Math.abs(distance - radius) >= 0.5) continue;
                    if (frame >= 4 && random.nextInt(frame - 2) != 0) continue;
                    pixels[y * 128 + frame * 16 + x] = 0xFFFFFFFF;
                }
            }
        }
    }
}
