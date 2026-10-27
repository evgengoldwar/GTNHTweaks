package gtnhtweaks.modules.minecraft.movement;

/** The pose of a player and, on the client, its animations. Kept on the player by a mixin. */
public final class MovementState {

    public Pose pose = Pose.STAND;
    /** The box of the player was made lower for the pose, so it is made high again when the player stands up. */
    boolean resized;
    /** How far the camera of the own player is lowered under the camera of vanilla, the tick before and now. */
    public float prevDrop, drop;
    /** How far the player is turned into the swimming pose, 0 to 1, the tick before and now. */
    public float prevSwim, swim;

    public float drop(float partialTicks) {
        return prevDrop + (drop - prevDrop) * partialTicks;
    }

    public float swim(float partialTicks) {
        return prevSwim + (swim - prevSwim) * partialTicks;
    }
}
