package gtnhtweaks.modules.extrautilities.client;

import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * Hangs a held lantern from its handle, straight down whatever way the camera looks, swinging: it lags behind when its
 * holder turns or walks, swings back and settles, like the lanterns held in hand in newer versions.
 * <p>
 * The swing of each hand of each entity is a damped spring, kept between frames.
 */
final class LanternSwing {

    /**
     * How far the lantern may swing toward the arm holding it, as a fraction of its hanging length, before it knocks
     * against it, and how much of its speed it keeps bouncing back.
     */
    private static final float ARM_WALL = 0.04F, BOUNCE = 0.35F;
    /** In first person looking up, the part of the pitch of the camera the lantern follows, and the most (degrees). */
    private static final float FIRST_PERSON_TILT = 0.3F, FIRST_PERSON_PITCH = 15;
    /** How hard the lantern is pulled back under its hand, and how fast its swing dies down. */
    private static final float STIFFNESS = 55, DAMPING = 3.2F;

    private static final Map<Long, Swing> SWINGS = new HashMap<>();

    private static final class Swing {

        /** Tilt toward the right of the holder and toward its back, as fractions of the hanging length. */
        float side, back, sideSpeed, backSpeed;
        /** Smoothed motion of the holder: positions change once a tick only. */
        float velocityX, velocityZ, turning;
        double lastX, lastZ;
        float lastYaw;
        long lastTime;
    }

    private LanternSwing() {}

    /**
     * Turns the current space so that a model hanging from its top, (0, 0.5, 0), hangs from the current origin.
     *
     * @param holder      whose hand it is in, or null for no swing
     * @param offhand     in the off hand of Backhand: it swings on its own
     * @param time        seconds, for a gentle sway at rest
     * @param firstPerson held by the player in first person: looking up, it does not hang quite straight down, which
     *                    would bring it at the eye, but only a little toward it; looking down it hangs straight down
     * @param armSide     where the arm holding the lantern is, beside it, which it knocks against instead of going
     *                    through: -1 to the left of its holder, 1 to the right, 0 for no arm
     */
    static void hang(Entity holder, boolean offhand, double time, boolean firstPerson, int armSide) {
        float side = 0.025F * (float) Math.sin(time * 1.3), back = 0.02F * (float) Math.sin(time * 0.9 + 1);
        float yaw = 0;
        if (holder != null) {
            Swing swing = update(holder, offhand, armSide);
            side += swing.side;
            back += swing.back;
            yaw = holder.rotationYaw;
        }
        if (armSide != 0 && side * armSide > ARM_WALL) side = ARM_WALL * armSide;
        // down in the world, pulled toward the right and the back of the holder
        double yawRadians = Math.toRadians(yaw);
        float rightX = (float) -Math.cos(yawRadians), rightZ = (float) -Math.sin(yawRadians);
        float backX = (float) Math.sin(yawRadians), backZ = (float) -Math.cos(yawRadians);
        float x = side * rightX + back * backX, y = -1, z = side * rightZ + back * backZ;

        // into the eye space, as the camera turns the world: glRotatef(pitch, 1, 0, 0), glRotatef(yaw + 180, 0, 1, 0)
        double cameraYaw = Math.toRadians(RenderManager.instance.playerViewY + 180);
        float pitch = RenderManager.instance.playerViewX;
        // the pitch is negative looking up
        if (firstPerson && pitch < 0) pitch = Math.max(-FIRST_PERSON_PITCH, pitch * FIRST_PERSON_TILT);
        double cameraPitch = Math.toRadians(pitch);
        float ex = (float) (x * Math.cos(cameraYaw) + z * Math.sin(cameraYaw));
        float ez = (float) (-x * Math.sin(cameraYaw) + z * Math.cos(cameraYaw));
        float ey = (float) (y * Math.cos(cameraPitch) - ez * Math.sin(cameraPitch));
        ez = (float) (y * Math.sin(cameraPitch) + ez * Math.cos(cameraPitch));

        // and into the space of the model: its +y goes up the string, opposite to the way it hangs
        float[] down = EyeSpace.current()
            .direction(ex, ey, ez);
        float length = (float) Math.sqrt(down[0] * down[0] + down[1] * down[1] + down[2] * down[2]);
        if (length > 0) {
            float ux = -down[0] / length, uy = -down[1] / length, uz = -down[2] / length;
            float axis = (float) Math.sqrt(ux * ux + uz * uz);
            if (axis > 1e-5F) {
                glRotatef((float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, uy)))), uz / axis, 0, -ux / axis);
            } else if (uy < 0) {
                glRotatef(180, 1, 0, 0);
            }
        }
        glTranslatef(0, -0.5F, 0);
    }

    private static Swing update(Entity holder, boolean offhand, int armSide) {
        long key = holder.getEntityId() * 2L + (offhand ? 1 : 0);
        long now = System.nanoTime();
        Swing swing = SWINGS.get(key);
        if (swing == null) {
            forgetOld(now);
            swing = new Swing();
            swing.lastX = holder.posX;
            swing.lastZ = holder.posZ;
            swing.lastYaw = holder.rotationYaw;
            swing.lastTime = now;
            SWINGS.put(key, swing);
            return swing;
        }
        float dt = (now - swing.lastTime) / 1e9F;
        if (dt <= 0) return swing;
        dt = Math.min(dt, 0.1F);
        swing.lastTime = now;

        float smooth = 1 - (float) Math.exp(-dt * 8);
        swing.velocityX += ((float) (holder.posX - swing.lastX) / dt - swing.velocityX) * smooth;
        swing.velocityZ += ((float) (holder.posZ - swing.lastZ) / dt - swing.velocityZ) * smooth;
        swing.turning += (MathHelper.wrapAngleTo180_float(holder.rotationYaw - swing.lastYaw) / dt - swing.turning)
            * smooth;
        swing.lastX = holder.posX;
        swing.lastZ = holder.posZ;
        swing.lastYaw = holder.rotationYaw;

        // walking forward it trails behind, turning right it lags to the left
        double yaw = Math.toRadians(holder.rotationYaw);
        float lookX = (float) -Math.sin(yaw), lookZ = (float) Math.cos(yaw);
        float forward = swing.velocityX * lookX + swing.velocityZ * lookZ;
        float sideways = swing.velocityX * -lookZ + swing.velocityZ * lookX;
        float wantedBack = clamp(forward * 0.05F, 0.5F);
        float wantedSide = clamp(-sideways * 0.05F - swing.turning * 0.0025F, 0.6F);

        swing.backSpeed += ((wantedBack - swing.back) * STIFFNESS - swing.backSpeed * DAMPING) * dt;
        swing.sideSpeed += ((wantedSide - swing.side) * STIFFNESS - swing.sideSpeed * DAMPING) * dt;
        swing.back += swing.backSpeed * dt;
        swing.side += swing.sideSpeed * dt;
        // the arm stops it, and it bounces back off it
        if (armSide != 0 && swing.side * armSide > ARM_WALL) {
            swing.side = ARM_WALL * armSide;
            if (swing.sideSpeed * armSide > 0) swing.sideSpeed *= -BOUNCE;
        }
        return swing;
    }

    /** Drops the swings of entities not drawn for a while. */
    private static void forgetOld(long now) {
        if (SWINGS.size() < 32) return;
        for (Iterator<Swing> it = SWINGS.values()
            .iterator(); it.hasNext();) {
            if (now - it.next().lastTime > 10_000_000_000L) it.remove();
        }
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
