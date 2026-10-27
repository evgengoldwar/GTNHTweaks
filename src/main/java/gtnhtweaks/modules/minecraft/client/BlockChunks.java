package gtnhtweaks.modules.minecraft.client;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * A broken block falls apart into little cubes of itself instead of the flat pixels of vanilla: every cube has the
 * textures of the faces of the block, flies out of the place of the block tumbling, bounces on the ground, comes to lie
 * flat and shrinks away after a moment.
 * <p>
 * Only solid blocks: plants, liquids and the like keep the particles of vanilla, and so do the blocks of mods that
 * make break effects of their own.
 */
public final class BlockChunks {

    private static final Random RANDOM = new Random();

    private BlockChunks() {}

    /**
     * Breaks the block into cubes, in place of the particles of vanilla.
     *
     * @return false if the particles of vanilla are made instead
     */
    public static boolean breakBlock(World world, EffectRenderer renderer, int x, int y, int z, Block block, int meta) {
        Minecraft mc = Minecraft.getMinecraft();
        if (MinecraftModule.blockChunks <= 0 || mc.gameSettings.particleSetting >= 2) return false;
        Material material = block.getMaterial();
        if (material == Material.air || material.isLiquid() || !material.isSolid()) return false;
        IIcon[] icons = new IIcon[6];
        for (int side = 0; side < 6; side++) {
            icons[side] = block.getIcon(side, meta);
            if (icons[side] == null) return false;
        }
        int color = block.colorMultiplier(world, x, y, z);
        // grass is green only on top
        boolean tintTopOnly = block == Blocks.grass;

        int count = Math.round(MinecraftModule.blockChunks * (mc.gameSettings.particleSetting == 1 ? 0.5F : 1F));
        double minX = block.getBlockBoundsMinX(), maxX = block.getBlockBoundsMaxX();
        double minY = block.getBlockBoundsMinY(), maxY = block.getBlockBoundsMaxY();
        double minZ = block.getBlockBoundsMinZ(), maxZ = block.getBlockBoundsMaxZ();
        for (int i = 0; i < count; i++) {
            double px = x + minX + (maxX - minX) * (0.15 + RANDOM.nextDouble() * 0.7);
            double py = y + minY + (maxY - minY) * (0.15 + RANDOM.nextDouble() * 0.7);
            double pz = z + minZ + (maxZ - minZ) * (0.15 + RANDOM.nextDouble() * 0.7);
            Chunk chunk = new Chunk(world, px, py, pz, icons, color, tintTopOnly);
            // out of the middle of the block, and up
            chunk.motionX = (px - x - 0.5) * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.08;
            chunk.motionY = 0.12 + RANDOM.nextDouble() * 0.15;
            chunk.motionZ = (pz - z - 0.5) * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.08;
            renderer.addEffect(chunk);
        }
        return true;
    }

    /** A little cube of the block, tumbling. */
    private static final class Chunk extends EntityFX {

        private final IIcon[] icons;
        private final float size;
        /** Where the cube takes its texture from on every face, in pixels of the texture. */
        private final float cellU, cellV, cellSize;
        private final int color;
        private final boolean tintTopOnly;
        /** Turn of the cube around its three axes in degrees, the tick before, and per tick. */
        private float rotX, rotY, rotZ, prevRotX, prevRotY, prevRotZ, spinX, spinY, spinZ;
        private float shrink = 1, prevShrink = 1;
        private int restTicks;
        private final int rest;

        Chunk(World world, double x, double y, double z, IIcon[] icons, int color, boolean tintTopOnly) {
            super(world, x, y, z);
            this.icons = icons;
            this.color = color;
            this.tintTopOnly = tintTopOnly;
            size = 0.06F + rand.nextFloat() * 0.06F;
            setSize(size * 2, size * 2);
            // the position is the bottom of the cube, where it touches the ground
            yOffset = 0;
            setPosition(x, y, z);
            cellSize = Math.max(1, Math.round(size * 2 * 16));
            cellU = rand.nextInt(Math.max(1, 17 - (int) cellSize));
            cellV = rand.nextInt(Math.max(1, 17 - (int) cellSize));
            rotX = prevRotX = rand.nextFloat() * 360;
            rotY = prevRotY = rand.nextFloat() * 360;
            rotZ = prevRotZ = rand.nextFloat() * 360;
            spinX = (rand.nextFloat() - 0.5F) * 40;
            spinY = (rand.nextFloat() - 0.5F) * 40;
            spinZ = (rand.nextFloat() - 0.5F) * 40;
            particleGravity = 0;
            particleMaxAge = 400;
            rest = 30 + rand.nextInt(40);
            particleRed = particleGreen = particleBlue = 1;
            particleAlpha = 1;
        }

        @Override
        public int getFXLayer() {
            return 1;
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

        @Override
        public void renderParticle(Tessellator tessellator, float partialTicks, float rotationX, float rotationXZ,
            float rotationZ, float rotationYZ, float rotationXY) {
            float x = (float) (prevPosX + (posX - prevPosX) * partialTicks - interpPosX);
            float y = (float) (prevPosY + (posY - prevPosY) * partialTicks - interpPosY);
            float z = (float) (prevPosZ + (posZ - prevPosZ) * partialTicks - interpPosZ);
            float half = size * (prevShrink + (shrink - prevShrink) * partialTicks);
            if (half <= 0.001F) return;
            // lies on its bottom: its middle is half its size above the box of the entity
            y += half;
            float ax = (prevRotX + (rotX - prevRotX) * partialTicks) * 0.01745F;
            float ay = (prevRotY + (rotY - prevRotY) * partialTicks) * 0.01745F;
            float az = (prevRotZ + (rotZ - prevRotZ) * partialTicks) * 0.01745F;
            float[] m = matrix(ax, ay, az);

            // the faces of the cube: down, up, north, south, west, east, as the sides of a block
            float[][] normals = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
            for (int side = 0; side < 6; side++) {
                float[] n = normals[side];
                // two axes along the face, so its corners are n + a * u + b * v
                float[] u = Math.abs(n[1]) > 0 ? new float[] { 1, 0, 0 } : new float[] { 0, 1, 0 };
                float[] v = cross(n, u);
                IIcon icon = icons[side];
                float u0 = icon.getInterpolatedU(cellU), u1 = icon.getInterpolatedU(cellU + cellSize);
                float v0 = icon.getInterpolatedV(cellV), v1 = icon.getInterpolatedV(cellV + cellSize);
                // lit from above, as the faces of blocks are
                float worldNormalY = m[3] * n[0] + m[4] * n[1] + m[5] * n[2];
                float shade = 0.65F + 0.35F * (worldNormalY * 0.5F + 0.5F);
                boolean tinted = !tintTopOnly || side == 1;
                float r = shade * (tinted ? (color >> 16 & 0xFF) / 255F : 1);
                float g = shade * (tinted ? (color >> 8 & 0xFF) / 255F : 1);
                float b = shade * (tinted ? (color & 0xFF) / 255F : 1);
                tessellator.setColorRGBA_F(r, g, b, 1);
                // counterclockwise seen from outside, so the faces turned away are the ones culled
                float[][] corners = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
                float[] us = { u0, u1, u1, u0 };
                float[] vs = { v1, v1, v0, v0 };
                for (int c = 0; c < 4; c++) {
                    float lx = (n[0] + u[0] * corners[c][0] + v[0] * corners[c][1]) * half;
                    float ly = (n[1] + u[1] * corners[c][0] + v[1] * corners[c][1]) * half;
                    float lz = (n[2] + u[2] * corners[c][0] + v[2] * corners[c][1]) * half;
                    tessellator.addVertexWithUV(
                        x + m[0] * lx + m[1] * ly + m[2] * lz,
                        y + m[3] * lx + m[4] * ly + m[5] * lz,
                        z + m[6] * lx + m[7] * ly + m[8] * lz,
                        us[c],
                        vs[c]);
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
