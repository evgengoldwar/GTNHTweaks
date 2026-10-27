package gtnhtweaks.modules.minecraft.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Shakes the camera when something explodes near the player: TNT, creepers, the machines of GregTech and anything else
 * that makes a Minecraft explosion. The bigger and the closer the explosion, the stronger the shake.
 * <p>
 * Explosions add trauma, from 0 to 1, that goes down by itself; the camera turns by the square of it, along smooth
 * noise, so a small explosion only trembles the view and a big one throws it about.
 */
public final class ScreenShake {

    /** Trauma lost per second. */
    private static final float RECOVERY = 0.9F;
    /** Most turn at full trauma and 100 % strength, in degrees: pitch and yaw, and roll. */
    private static final float MAX_TURN = 4F;
    private static final float MAX_ROLL = 3F;
    /** Speed of the noise the camera follows, in radians per second. */
    private static final float SPEED = 30F;

    private static final float[] PHASES = new float[9];
    private static float trauma;
    private static long lastNanos;
    private static float time;

    static {
        Random random = new Random();
        for (int i = 0; i < PHASES.length; i++) PHASES[i] = random.nextFloat() * 6.2832F;
    }

    private ScreenShake() {}

    /** Called for every explosion on the client, with its middle and size: 4 for TNT, 3 for a creeper. */
    public static void explosion(double x, double y, double z, float size) {
        if (!MinecraftModule.screenShake || MinecraftModule.screenShakeStrength <= 0) return;
        Entity viewer = Minecraft.getMinecraft().renderViewEntity;
        if (viewer == null || size <= 0) return;
        double dx = viewer.posX - x, dy = viewer.posY - y, dz = viewer.posZ - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // heard from far, felt only nearer: the reach grows with the size
        double reach = 12 + size * 5;
        if (distance >= reach) return;
        double near = 1 - distance / reach;
        float amount = (float) (Math.min(1.5, size / 4.0) * near * near);
        trauma = Math.min(1, trauma + amount);
    }

    /** Turns the camera by the shake of now; called where the camera is set up for a frame. */
    public static void apply() {
        long now = System.nanoTime();
        float seconds = lastNanos == 0 ? 0 : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        if (trauma <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isGamePaused()) {
            trauma = Math.max(0, trauma - RECOVERY * seconds);
            time += seconds;
        }
        float shake = trauma * trauma * MinecraftModule.screenShakeStrength / 100F;
        if (shake <= 0) return;
        GL11.glRotatef(MAX_ROLL * shake * noise(0), 0F, 0F, 1F);
        GL11.glRotatef(MAX_TURN * shake * noise(3), 1F, 0F, 0F);
        GL11.glRotatef(MAX_TURN * shake * noise(6), 0F, 1F, 0F);
    }

    /** Smooth noise from -1 to 1: three waves of their own speeds and phases. */
    private static float noise(int first) {
        float t = time * SPEED;
        return (float) (Math.sin(t + PHASES[first]) * 0.5 + Math.sin(t * 1.7 + PHASES[first + 1]) * 0.3
            + Math.sin(t * 2.9 + PHASES[first + 2]) * 0.2);
    }
}
