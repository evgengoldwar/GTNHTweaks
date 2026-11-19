package gtnhtweaks.modules.minecraft.client.maid;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/** Minecraft 1.7.10 player-model adapter for Extra Botany's original model geometry. */
public abstract class MaidArmorModel extends ModelBiped {

    /** Render passes: head, chest, legs, feet. */
    protected final int slot;

    protected MaidArmorModel(int slot) {
        this.slot = slot;
    }

    protected void setRotateAngle(ModelRenderer part, float x, float y, float z) {
        part.rotateAngleX = x;
        part.rotateAngleY = y;
        part.rotateAngleZ = z;
    }
}
