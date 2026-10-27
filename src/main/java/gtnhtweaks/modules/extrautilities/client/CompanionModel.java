package gtnhtweaks.modules.extrautilities.client;

/**
 * A little living thing drawn in place of the Healing Axe. It fills the unit cube centered on the origin, standing on
 * y = -0.5 (a hanging one hangs from y = 0.5), like a block drawn as an item. GL_TEXTURE_2D must be disabled; the
 * normals are set for the item lighting.
 * <p>
 * Everything is a function of the time, so nothing is kept between frames: two with other seeds just live out of step.
 */
public interface CompanionModel {

    /**
     * @param time      seconds
     * @param seed      companions with other seeds live out of step
     * @param faceYaw   degrees around y that turn the face (drawn toward +z) to the viewer
     * @param facePitch degrees the face tilts up to the viewer
     * @param mood      how it feels where it is
     */
    void render(double time, int seed, float faceYaw, float facePitch, Mood mood);
}
