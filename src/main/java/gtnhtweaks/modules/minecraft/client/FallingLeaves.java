package gtnhtweaks.modules.minecraft.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Leaves falling from the trees around the player: a bit of the texture of the leaf block, in the color of its biome,
 * comes off the bottom of the leaves, flutters down turning and swaying with the wind, lies on the ground or floats on
 * water for a while and fades away. Works for the leaves of every mod: a block is leaves when it says so.
 * <p>
 * Only on the client: every tick blocks around the player are tried at random, and a leaf block with air under it
 * lets a leaf fall now and then, so dense forests have many and lone trees a few.
 */
public final class FallingLeaves {

    /** Blocks tried per tick at 100 %, how far from the player, the chance of a leaf for each leaf block found. */
    private static final int TRIES = 40;
    private static final int REACH = 16;
    private static final float CHANCE = 0.25F;
    /** Most leaves at the same time. */
    private static final int MAX_LEAVES = 250;
    /** Ticks a leaf lives at most, falling and lying. */
    private static final int MAX_LIFE = 900;

    private static final Random RANDOM = new Random();
    private static final List<Leaf> LEAVES = new ArrayList<>();
    private static World world;
    /** The wind, the same for all the leaves, turning slowly: blocks per tick. */
    private static double windX, windZ;
    private static float windAngle = RANDOM.nextFloat() * 6.2832F;

    private FallingLeaves() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new FallingLeaves());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world) {
            world = mc.theWorld;
            LEAVES.clear();
        }
        if (world == null || mc.isGamePaused()) return;
        for (Iterator<Leaf> it = LEAVES.iterator(); it.hasNext();) {
            Leaf leaf = it.next();
            // by its time in the world: a leaf the particle renderer dropped is not updated any more
            if (leaf.isDead || world.getTotalWorldTime() - leaf.born > MAX_LIFE) it.remove();
        }

        windAngle += (RANDOM.nextFloat() - 0.5F) * 0.02F;
        double strength = 0.012 + (world.isRaining() ? 0.02 : 0);
        windX = Math.cos(windAngle) * strength;
        windZ = Math.sin(windAngle) * strength;

        Entity viewer = mc.renderViewEntity;
        if (viewer == null || MinecraftModule.fallingLeaves <= 0 || mc.gameSettings.particleSetting >= 2) return;
        float amount = MinecraftModule.fallingLeaves / 100F * (mc.gameSettings.particleSetting == 1 ? 0.5F : 1F);
        int tries = Math.round(TRIES * amount);
        int centerX = MathHelper.floor_double(viewer.posX);
        int centerY = MathHelper.floor_double(viewer.posY);
        int centerZ = MathHelper.floor_double(viewer.posZ);
        for (int i = 0; i < tries && LEAVES.size() < MAX_LEAVES; i++) {
            int x = centerX + RANDOM.nextInt(REACH * 2 + 1) - REACH;
            int y = centerY + RANDOM.nextInt(REACH + 9) - 6;
            int z = centerZ + RANDOM.nextInt(REACH * 2 + 1) - REACH;
            Block block = world.getBlock(x, y, z);
            if (!block.isLeaves(world, x, y, z) || !world.isAirBlock(x, y - 1, z)) continue;
            if (RANDOM.nextFloat() >= CHANCE) continue;
            IIcon icon = block.getIcon(world, x, y, z, 0);
            if (icon == null) continue;
            Leaf leaf = new Leaf(
                world,
                x + RANDOM.nextDouble(),
                y - 0.05,
                z + RANDOM.nextDouble(),
                icon,
                block.colorMultiplier(world, x, y, z));
            LEAVES.add(leaf);
            mc.effectRenderer.addEffect(leaf);
        }
    }

    /** A falling leaf: a flat piece of the leaf texture, turning as it flutters down. */
    private static final class Leaf extends EntityFX {

        private final float size;
        /** Which 4 by 4 pixels of the texture: 0 to 3 across and down. */
        private final int cellU, cellV;
        private final float swaySpeed, swayPhase, spinSpeed;
        /** Turn around the vertical and tilt, in degrees, now and the tick before. */
        private float yaw, prevYaw, tilt, prevTilt;
        private int groundTicks;
        private final int restTicks;
        private boolean floating;
        final long born;

        Leaf(World world, double x, double y, double z, IIcon icon, int color) {
            super(world, x, y, z);
            setParticleIcon(icon);
            motionX = motionY = motionZ = 0;
            particleRed = (color >> 16 & 0xFF) / 255F;
            particleGreen = (color >> 8 & 0xFF) / 255F;
            particleBlue = (color & 0xFF) / 255F;
            particleAlpha = 0;
            particleGravity = 0;
            particleMaxAge = MAX_LIFE;
            size = 0.08F + rand.nextFloat() * 0.05F;
            setSize(0.1F, 0.05F);
            setPosition(x, y, z);
            born = world.getTotalWorldTime();
            cellU = rand.nextInt(4);
            cellV = rand.nextInt(4);
            swaySpeed = 0.08F + rand.nextFloat() * 0.06F;
            swayPhase = rand.nextFloat() * 6.2832F;
            spinSpeed = (rand.nextFloat() - 0.5F) * 12F;
            yaw = prevYaw = rand.nextFloat() * 360F;
            restTicks = 100 + rand.nextInt(120);
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
            prevYaw = yaw;
            prevTilt = tilt;
            if (++particleAge >= particleMaxAge) {
                setDead();
                return;
            }
            // comes into sight over a second, fades away at the end of its rest
            int fadeStart = restTicks - 30;
            if (groundTicks > fadeStart) particleAlpha = Math.max(0, 1 - (groundTicks - fadeStart) / 30F);
            else particleAlpha = Math.min(1, particleAlpha + 0.1F);
            if (groundTicks >= restTicks) {
                setDead();
                return;
            }

            boolean inWater = worldObj
                .getBlock(MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ))
                .getMaterial()
                .isLiquid();
            if (inWater && !floating) {
                floating = true;
                motionY = 0;
            }
            if (floating) {
                // lies on the water, drifting with the wind, and goes up to the surface if under it
                groundTicks++;
                tilt *= 0.8F;
                motionX = motionX * 0.9 + windX * 0.3;
                motionZ = motionZ * 0.9 + windZ * 0.3;
                motionY = inWater ? 0.02 : -0.02;
                moveEntity(motionX, motionY, motionZ);
                return;
            }
            if (onGround) {
                groundTicks++;
                tilt *= 0.6F;
                motionX = motionY = motionZ = 0;
                return;
            }

            // flutters down: sways from side to side, turning and tilting, carried by the wind
            float sway = MathHelper.sin(particleAge * swaySpeed + swayPhase);
            motionY = Math.max(-0.06, motionY - 0.004);
            motionX = motionX * 0.9 + (windX + MathHelper.cos(yaw * 0.01745F) * sway * 0.02) * 0.1;
            motionZ = motionZ * 0.9 + (windZ + MathHelper.sin(yaw * 0.01745F) * sway * 0.02) * 0.1;
            // slower when it lies across its fall
            motionY *= 0.85 + 0.15 * Math.abs(sway);
            yaw += spinSpeed;
            tilt = sway * 50F;
            moveEntity(motionX, motionY, motionZ);
        }

        @Override
        public void renderParticle(Tessellator tessellator, float partialTicks, float rotationX, float rotationXZ,
            float rotationZ, float rotationYZ, float rotationXY) {
            float u0 = particleIcon.getInterpolatedU(cellU * 4), u1 = particleIcon.getInterpolatedU(cellU * 4 + 4);
            float v0 = particleIcon.getInterpolatedV(cellV * 4), v1 = particleIcon.getInterpolatedV(cellV * 4 + 4);
            float x = (float) (prevPosX + (posX - prevPosX) * partialTicks - interpPosX);
            float y = (float) (prevPosY + (posY - prevPosY) * partialTicks - interpPosY) + 0.01F;
            float z = (float) (prevPosZ + (posZ - prevPosZ) * partialTicks - interpPosZ);
            float yawRad = (prevYaw + (yaw - prevYaw) * partialTicks) * 0.01745F;
            float tiltRad = (prevTilt + (tilt - prevTilt) * partialTicks) * 0.01745F;
            float cosYaw = MathHelper.cos(yawRad), sinYaw = MathHelper.sin(yawRad);
            float cosTilt = MathHelper.cos(tiltRad), sinTilt = MathHelper.sin(tiltRad);

            // the corners of a flat square, tilted around its own x axis and turned around the vertical
            float[] cornerX = new float[4], cornerY = new float[4], cornerZ = new float[4];
            float[] a = { -size, -size, size, size };
            float[] b = { -size, size, size, -size };
            for (int i = 0; i < 4; i++) {
                float localY = b[i] * sinTilt, localZ = b[i] * cosTilt;
                cornerX[i] = x + a[i] * cosYaw + localZ * sinYaw;
                cornerY[i] = y + localY;
                cornerZ[i] = z - a[i] * sinYaw + localZ * cosYaw;
            }
            float[] u = { u0, u0, u1, u1 };
            float[] v = { v0, v1, v1, v0 };
            tessellator.setColorRGBA_F(particleRed, particleGreen, particleBlue, particleAlpha);
            // both sides, as the faces turned away may be culled
            for (int i = 0; i < 4; i++) tessellator.addVertexWithUV(cornerX[i], cornerY[i], cornerZ[i], u[i], v[i]);
            for (int i = 3; i >= 0; i--) tessellator.addVertexWithUV(cornerX[i], cornerY[i], cornerZ[i], u[i], v[i]);
        }
    }
}
