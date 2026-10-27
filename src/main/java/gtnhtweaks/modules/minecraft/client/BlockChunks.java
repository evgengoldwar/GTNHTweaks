package gtnhtweaks.modules.minecraft.client;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL20;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * A broken block falls apart into little cubes of itself instead of the flat pixels of vanilla: every cube has the
 * textures of the faces of the block, flies out of the place of the block tumbling, bounces on the ground, comes to lie
 * flat and shrinks away after a moment.
 * <p>
 * Only solid blocks: plants, liquids and the like keep the particles of vanilla, and so do the blocks of mods that
 * make break effects of their own.
 * <p>
 * The cubes are moved and drawn here, not by the particle renderer of vanilla: it draws without writing depth, so cubes
 * lying close showed their back faces and each other in the wrong order. Here they are solid, nearer faces hide the
 * farther ones.
 */
public final class BlockChunks {

    private static final Random RANDOM = new Random();
    /** Most cubes at the same time: more breaking at once makes fewer. */
    private static final int MAX_CHUNKS = 1500;
    private static final List<Chunk> CHUNKS = new ArrayList<>();
    private static World world;

    private BlockChunks() {}

    public static void register() {
        BlockChunks chunks = new BlockChunks();
        MinecraftForge.EVENT_BUS.register(chunks);
        FMLCommonHandler.instance()
            .bus()
            .register(chunks);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world) {
            world = mc.theWorld;
            CHUNKS.clear();
        }
        if (world == null || mc.isGamePaused()) return;
        for (Iterator<Chunk> it = CHUNKS.iterator(); it.hasNext();) {
            Chunk chunk = it.next();
            chunk.onUpdate();
            if (chunk.isDead) it.remove();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (CHUNKS.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int program = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        // lit by the light map, with the light of where each cube is
        mc.entityRenderer.enableLightmap(event.partialTicks);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_DEPTH_BUFFER_BIT);
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        // solid cubes: depth written, faces turned away culled, see-through pixels of the texture left out
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (Chunk chunk : CHUNKS) chunk.draw(tessellator, event.partialTicks);
        tessellator.draw();
        glPopAttrib();
        glColor4f(1, 1, 1, 1);
        GL20.glUseProgram(program);
    }

    /**
     * Breaks the block into cubes, in place of the particles of vanilla.
     *
     * @return false if the particles of vanilla are made instead
     */
    public static boolean breakBlock(World world, int x, int y, int z, Block block, int meta) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!MinecraftModule.blockChunksEnabled) return false;
        // no particles at all, the block just disappears
        if (MinecraftModule.blockChunks <= 0) return true;
        if (mc.gameSettings.particleSetting >= 2) return false;
        Material material = block.getMaterial();
        if (material == Material.air || material.isLiquid() || !material.isSolid()) return false;
        Layer[][] layers = layers(world, x, y, z, block, meta);
        if (layers == null) return false;

        int count = Math.round(MinecraftModule.blockChunks * (mc.gameSettings.particleSetting == 1 ? 0.5F : 1F));
        double minX = block.getBlockBoundsMinX(), maxX = block.getBlockBoundsMaxX();
        double minY = block.getBlockBoundsMinY(), maxY = block.getBlockBoundsMaxY();
        double minZ = block.getBlockBoundsMinZ(), maxZ = block.getBlockBoundsMaxZ();
        for (int i = 0; i < count && CHUNKS.size() < MAX_CHUNKS; i++) {
            double px = x + minX + (maxX - minX) * (0.15 + RANDOM.nextDouble() * 0.7);
            double py = y + minY + (maxY - minY) * (0.15 + RANDOM.nextDouble() * 0.7);
            double pz = z + minZ + (maxZ - minZ) * (0.15 + RANDOM.nextDouble() * 0.7);
            Chunk chunk = new Chunk(world, px, py, pz, layers);
            // out of the middle of the block, and up
            chunk.motionX = (px - x - 0.5) * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.08;
            chunk.motionY = 0.12 + RANDOM.nextDouble() * 0.15;
            chunk.motionZ = (pz - z - 0.5) * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.08;
            CHUNKS.add(chunk);
        }
        return true;
    }

    /** A texture drawn on a face of the cubes, in a color. */
    static final class Layer {

        final IIcon icon;
        final float red, green, blue;

        Layer(IIcon icon, int rgb) {
            this.icon = icon;
            this.red = (rgb >> 16 & 0xFF) / 255F;
            this.green = (rgb >> 8 & 0xFF) / 255F;
            this.blue = (rgb & 0xFF) / 255F;
        }
    }

    private static boolean gregtech = Loader.isModLoaded("gregtech");

    /**
     * The textures of every side of the block, bottom first, as drawn in the world: one per side for most blocks; for
     * the blocks of GregTech, drawn by layers in the colors of their materials, its layers. Null if a side has none.
     */
    private static Layer[][] layers(World world, int x, int y, int z, Block block, int meta) {
        if (gregtech) {
            try {
                Layer[][] layers = GregTechTextures.layers(world, x, y, z, block, meta);
                if (layers != null) return layers;
            } catch (Exception | LinkageError e) {
                // another version of GregTech: its blocks break as the others
                gregtech = false;
                GTNHTweaks.LOG.warn("Could not read the textures of the GregTech blocks for the block chunks", e);
            }
        }
        int color = block.colorMultiplier(world, x, y, z);
        Layer[][] layers = new Layer[6][];
        for (int side = 0; side < 6; side++) {
            IIcon icon = block.getIcon(side, meta);
            if (icon == null) return null;
            // grass is green only on top
            boolean tinted = block != Blocks.grass || side == 1;
            layers[side] = new Layer[] { new Layer(icon, tinted ? color : 0xFFFFFF) };
        }
        return layers;
    }

    /** A little cube of the block, tumbling. */
    private static final class Chunk extends EntityFX {

        private final Layer[][] layers;
        private final float size;
        /** Where the cube takes its texture from on every face, in pixels of the texture. */
        private final float cellU, cellV, cellSize;
        /** Turn of the cube around its three axes in degrees, the tick before, and per tick. */
        private float rotX, rotY, rotZ, prevRotX, prevRotY, prevRotZ, spinX, spinY, spinZ;
        private float shrink = 1, prevShrink = 1;
        private int restTicks;
        private final int rest;

        Chunk(World world, double x, double y, double z, Layer[][] layers) {
            super(world, x, y, z);
            this.layers = layers;
            size = (0.06F + rand.nextFloat() * 0.06F) * MinecraftModule.blockChunkSize / 100F;
            setSize(size * 2, size * 2);
            // the position is the bottom of the cube, where it touches the ground
            yOffset = 0;
            setPosition(x, y, z);
            cellSize = Math.max(1, Math.min(16, Math.round(size * 2 * 16)));
            cellU = rand.nextInt(Math.max(1, 17 - (int) cellSize));
            cellV = rand.nextInt(Math.max(1, 17 - (int) cellSize));
            rotX = prevRotX = rand.nextFloat() * 360;
            rotY = prevRotY = rand.nextFloat() * 360;
            rotZ = prevRotZ = rand.nextFloat() * 360;
            spinX = (rand.nextFloat() - 0.5F) * 40;
            spinY = (rand.nextFloat() - 0.5F) * 40;
            spinZ = (rand.nextFloat() - 0.5F) * 40;
            particleGravity = 0;
            // lies its time, a bit more or less for each cube, so they do not all go at once
            rest = Math.max(1, Math.round(MinecraftModule.blockChunkSeconds * 20 * (0.7F + rand.nextFloat() * 0.6F)));
            particleMaxAge = rest + 200;
            particleRed = particleGreen = particleBlue = 1;
            particleAlpha = 1;
        }

        @Override
        public void onUpdate() {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
            prevRotX = rotX;
            prevRotY = rotY;
            prevRotZ = rotZ;
            prevShrink = shrink;
            if (++particleAge >= particleMaxAge) {
                setDead();
                return;
            }
            double fall = motionY;
            motionY -= 0.04;
            moveEntity(motionX, motionY, motionZ);
            motionX *= 0.98;
            motionY *= 0.98;
            motionZ *= 0.98;
            if (onGround) {
                // bounces a bit when it lands fast, then slides to a stop and lies flat
                if (fall < -0.1) motionY = -fall * 0.35;
                motionX *= 0.6;
                motionZ *= 0.6;
                spinX *= 0.5;
                spinY *= 0.7;
                spinZ *= 0.5;
                // turned around x by right angles and around z by half turns, a face stays down whatever its turn
                // around the vertical
                rotX = flatten(rotX, 90F);
                rotZ = flatten(rotZ, 180F);
                if (Math.abs(fall) < 0.1) restTicks++;
            }
            rotX += spinX;
            rotY += spinY;
            rotZ += spinZ;
            // shrinks away at the end of its rest
            if (restTicks > rest) shrink = Math.max(0, 1 - (restTicks - rest) / 10F);
            if (shrink <= 0) setDead();
        }

        /** Turns toward the nearest multiple of the step, so a cube on the ground lies on a face. */
        private static float flatten(float angle, float step) {
            float target = Math.round(angle / step) * step;
            return angle + (target - angle) * 0.5F;
        }

        void draw(Tessellator tessellator, float partialTicks) {
            float x = (float) (prevPosX + (posX - prevPosX) * partialTicks - RenderManager.renderPosX);
            float y = (float) (prevPosY + (posY - prevPosY) * partialTicks - RenderManager.renderPosY);
            float z = (float) (prevPosZ + (posZ - prevPosZ) * partialTicks - RenderManager.renderPosZ);
            float half = size * (prevShrink + (shrink - prevShrink) * partialTicks);
            if (half <= 0.001F) return;
            // lies on its bottom: its middle is half its size above the box of the entity
            y += half;
            float ax = (prevRotX + (rotX - prevRotX) * partialTicks) * 0.01745F;
            float ay = (prevRotY + (rotY - prevRotY) * partialTicks) * 0.01745F;
            float az = (prevRotZ + (rotZ - prevRotZ) * partialTicks) * 0.01745F;
            float[] m = matrix(ax, ay, az);
            // the light of where it lies, through the light map
            tessellator.setBrightness(getBrightnessForRender(partialTicks));

            // the faces of the cube: down, up, north, south, west, east, as the sides of a block
            float[][] normals = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
            for (int side = 0; side < 6; side++) {
                float[] n = normals[side];
                // two axes along the face, so its corners are n + a * u + b * v
                float[] u = Math.abs(n[1]) > 0 ? new float[] { 1, 0, 0 } : new float[] { 0, 1, 0 };
                float[] v = cross(n, u);
                // lit from above, as the faces of blocks are
                float worldNormalY = m[3] * n[0] + m[4] * n[1] + m[5] * n[2];
                float shade = 0.65F + 0.35F * (worldNormalY * 0.5F + 0.5F);
                Layer[] faceLayers = layers[side];
                for (int l = 0; l < faceLayers.length; l++) {
                    Layer layer = faceLayers[l];
                    IIcon icon = layer.icon;
                    float u0 = icon.getInterpolatedU(cellU), u1 = icon.getInterpolatedU(cellU + cellSize);
                    float v0 = icon.getInterpolatedV(cellV), v1 = icon.getInterpolatedV(cellV + cellSize);
                    tessellator.setColorRGBA_F(shade * layer.red, shade * layer.green, shade * layer.blue, 1);
                    // every layer a hair over the one under it, so they do not flicker
                    float out = half + l * 0.002F;
                    // counterclockwise seen from outside, so the faces turned away are the ones culled
                    float[][] corners = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
                    float[] us = { u0, u1, u1, u0 };
                    float[] vs = { v1, v1, v0, v0 };
                    for (int c = 0; c < 4; c++) {
                        float lx = n[0] * out + (u[0] * corners[c][0] + v[0] * corners[c][1]) * half;
                        float ly = n[1] * out + (u[1] * corners[c][0] + v[1] * corners[c][1]) * half;
                        float lz = n[2] * out + (u[2] * corners[c][0] + v[2] * corners[c][1]) * half;
                        tessellator.addVertexWithUV(
                            x + m[0] * lx + m[1] * ly + m[2] * lz,
                            y + m[3] * lx + m[4] * ly + m[5] * lz,
                            z + m[6] * lx + m[7] * ly + m[8] * lz,
                            us[c],
                            vs[c]);
                    }
                }
            }
        }

        /** Rotation matrix, row by row, of turns around x, then y, then z. */
        private static float[] matrix(float ax, float ay, float az) {
            float cx = MathHelper.cos(ax), sx = MathHelper.sin(ax);
            float cy = MathHelper.cos(ay), sy = MathHelper.sin(ay);
            float cz = MathHelper.cos(az), sz = MathHelper.sin(az);
            return new float[] { cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz, cy * sz,
                sx * sy * sz + cx * cz, cx * sy * sz - sx * cz, -sy, sx * cy, cx * cy };
        }

        private static float[] cross(float[] a, float[] b) {
            return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
        }
    }
}
