package gtnhtweaks.modules.extrautilities.client;

import static org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX;
import static org.lwjgl.opengl.GL11.glGetFloat;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

/** The current modelview matrix, to bring points and directions of the eye space into the model space. */
final class EyeSpace {

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private final float a, b, c, d, e, f, g, h, k;
    private final float tx, ty, tz;
    private final float det;

    private EyeSpace() {
        MATRIX.clear();
        glGetFloat(GL_MODELVIEW_MATRIX, MATRIX);
        a = MATRIX.get(0);
        b = MATRIX.get(4);
        c = MATRIX.get(8);
        d = MATRIX.get(1);
        e = MATRIX.get(5);
        f = MATRIX.get(9);
        g = MATRIX.get(2);
        h = MATRIX.get(6);
        k = MATRIX.get(10);
        tx = MATRIX.get(12);
        ty = MATRIX.get(13);
        tz = MATRIX.get(14);
        det = a * (e * k - f * h) + b * (f * g - d * k) + c * (d * h - e * g);
    }

    static EyeSpace current() {
        return new EyeSpace();
    }

    /** Whether the model is drawn mirrored, as Backhand draws the off hand. */
    boolean mirrored() {
        return det < 0;
    }

    /**
     * A direction of the eye space in the model space, through the inverse of the linear part, whose columns are
     * (row 1 x row 2, row 2 x row 0, row 0 x row 1) / determinant.
     */
    float[] direction(float x, float y, float z) {
        float s = det == 0 ? 0 : 1 / det;
        return new float[] { s * (x * (e * k - f * h) + y * (h * c - k * b) + z * (b * f - c * e)),
            s * (x * (f * g - d * k) + y * (k * a - g * c) + z * (c * d - a * f)),
            s * (x * (d * h - e * g) + y * (g * b - h * a) + z * (a * e - b * d)) };
    }

    /**
     * The yaw and pitch, in degrees, that turn a face drawn toward +z to the viewer.
     *
     * @param towardEye true: to the eye, as seen in perspective, so a face in the right hand looks to the left and one
     *                  in the left hand to the right; false: to the screen, for the inventory
     */
    float[] facing(boolean towardEye) {
        float[] v = towardEye ? point(0, 0, 0) : direction(0, 0, 1);
        float x = v[0], y = v[1], z = v[2];
        float yaw = (float) Math.toDegrees(Math.atan2(x, z));
        float pitch = (float) Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
        return new float[] { yaw, Math.max(-25, Math.min(25, pitch * 0.5F)) };
    }

    /** Where the origin of the model space is in the eye space. */
    float[] origin() {
        return new float[] { tx, ty, tz };
    }

    /** A point of the eye space in the model space. The eye is at (0, 0, 0). */
    float[] point(float x, float y, float z) {
        return direction(x - tx, y - ty, z - tz);
    }
}
