package gtnhtweaks.modules.minecraft.movement;

/** How a player stands, as in newer versions: the height of its box and of its eyes. */
public enum Pose {

    STAND(1.8F, 1.62F),
    /** Crouching: lower than the sneaking of 1.7.10, fits under one and a half blocks. */
    CROUCH(1.5F, 1.27F),
    /** Swimming, and crawling where there is no room to stand: fits through a gap of one block. */
    SWIM(0.6F, 0.4F);

    public final float height;
    public final float eye;

    Pose(float height, float eye) {
        this.height = height;
        this.eye = eye;
    }
}
