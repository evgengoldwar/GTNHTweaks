package gtnhtweaks.modules.minecraft.client.maid;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/** Minecraft 1.7.10 player-model adapter for Extra Botany's original model geometry. */
public abstract class MaidArmorModel extends ModelBiped {

    /** Render passes: head, chest, legs, feet. */
    protected final int slot;
    private ModelBiped playerPose;

    protected MaidArmorModel(int slot) {
        this.slot = slot;
    }

    public void usePlayerPose(ModelBiped player) {
        playerPose = player;
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
        float scale, Entity entity) {
        if (playerPose == null) {
            super.setRotationAngles(limbSwing, limbSwingAmount, age, headYaw, headPitch, scale, entity);
            return;
        }
        // The player has already been posed by vanilla and, when active, the movement backport.
        // Running ModelBiped's animation again on the outfit would reapply those hooks to custom anchors.
        copyPose(playerPose.bipedHead, bipedHead);
        copyPose(playerPose.bipedBody, bipedBody);
        copyPose(playerPose.bipedRightArm, bipedRightArm);
        copyPose(playerPose.bipedLeftArm, bipedLeftArm);
        copyPose(playerPose.bipedRightLeg, bipedRightLeg);
        copyPose(playerPose.bipedLeftLeg, bipedLeftLeg);
        if (slot == 2 && playerPose.isSneak) hangSkirtFromWaist(bipedBody);
    }

    private static void copyPose(ModelRenderer from, ModelRenderer to) {
        to.rotateAngleX = from.rotateAngleX;
        to.rotateAngleY = from.rotateAngleY;
        to.rotateAngleZ = from.rotateAngleZ;
        to.rotationPointX = from.rotationPointX;
        to.rotationPointY = from.rotationPointY;
        to.rotationPointZ = from.rotationPointZ;
        to.offsetX = from.offsetX;
        to.offsetY = from.offsetY;
        to.offsetZ = from.offsetZ;
    }

    /** Keep the skirt's top at its authored waist (y=9), but let the hem hang instead of pitching with the chest. */
    private static void hangSkirtFromWaist(ModelRenderer skirt) {
        float sinX = (float) Math.sin(skirt.rotateAngleX);
        float cosX = (float) Math.cos(skirt.rotateAngleX);
        float sinY = (float) Math.sin(skirt.rotateAngleY);
        float cosY = (float) Math.cos(skirt.rotateAngleY);
        float sinZ = (float) Math.sin(skirt.rotateAngleZ);
        float cosZ = (float) Math.cos(skirt.rotateAngleZ);
        // Compensate Rz * Ry * Rx around the waist, preserving yaw/roll and the actual torso height.
        skirt.rotationPointX += 9F * (sinY * sinX * cosZ + (1F - cosX) * sinZ);
        skirt.rotationPointY += 9F * (sinY * sinX * sinZ + (cosX - 1F) * cosZ);
        skirt.rotationPointZ += 9F * cosY * sinX;
        skirt.rotateAngleX = 0F;
    }

    protected void setRotateAngle(ModelRenderer part, float x, float y, float z) {
        part.rotateAngleX = x;
        part.rotateAngleY = y;
        part.rotateAngleZ = z;
    }
}
