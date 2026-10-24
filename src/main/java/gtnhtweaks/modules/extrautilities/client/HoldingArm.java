package gtnhtweaks.modules.extrautilities.client;

import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;

/**
 * The bare arm of the player in first person, holding the pot on its hand: vanilla draws no arm while an item is held.
 * It is the arm vanilla draws when the hand is empty, at the same place, so it looks like the other, empty hand; in
 * the off hand of Backhand, drawn mirrored, it is the left arm, as Backhand draws an empty off hand.
 */
final class HoldingArm {

    /**
     * From where vanilla holds an item in first person to where it shows the bare arm: (0.8, -0.75, -0.9) * 0.8 less
     * (0.7, -0.65, -0.9) * 0.8, turned back by the 45 degrees both are turned by.
     */
    private static final float BARE_X = 0.0566F, BARE_Y = -0.08F, BARE_Z = 0.0566F;
    /** The point the pot stands on, in pixels of the arm model: the end of the hand, a little inside it. */
    private static final float HAND_X = -1, HAND_Y = 9, HAND_Z = 0;

    private HoldingArm() {}

    /**
     * Draws the arm and gives the point of its hand in the current space, which must be the space of the item held in
     * first person before its scale by 0.4, so after its turn by 45 degrees and the swing. Binds the skin.
     */
    static float[] render(AbstractClientPlayer player) {
        RenderPlayer render = (RenderPlayer) RenderManager.instance.getEntityRenderObject(player);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(player.getLocationSkin());
        ModelBiped model = render.modelBipedMain;
        // set when the player was last drawn in third person, holding this item: the bare hand holds nothing
        int held = model.heldItemRight;
        model.heldItemRight = 0;
        float[] hand;
        glPushMatrix();
        try {
            // as ItemRenderer.renderItemInFirstPerson places the empty hand
            glTranslatef(BARE_X, BARE_Y, BARE_Z);
            glTranslatef(-1.0F, 3.6F, 3.5F);
            glRotatef(120.0F, 0.0F, 0.0F, 1.0F);
            glRotatef(200.0F, 1.0F, 0.0F, 0.0F);
            glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            glTranslatef(5.6F, 0.0F, 0.0F);
            render.renderFirstPersonArm(player);
            model.bipedRightArm.postRender(0.0625F);
            glTranslatef(HAND_X / 16, HAND_Y / 16, HAND_Z / 16);
            hand = EyeSpace.current()
                .origin();
        } finally {
            glPopMatrix();
            model.heldItemRight = held;
        }
        return EyeSpace.current()
            .point(hand[0], hand[1], hand[2]);
    }
}
