package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/** The model of a player bends in the crouching pose and makes the strokes of swimming. */
@Mixin(ModelBiped.class)
public class MixinModelBiped_Movement {

    @Inject(method = "setRotationAngles", at = @At("HEAD"))
    private void gtnhtweaks$beforePose(float limbSwing, float limbSwingAmount, float age, float headYaw,
        float headPitch, float scale, Entity entity, CallbackInfo ci) {
        MovementClient.beforePose((ModelBiped) (Object) this, entity);
    }

    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void gtnhtweaks$afterPose(float limbSwing, float limbSwingAmount, float age, float headYaw,
        float headPitch, float scale, Entity entity, CallbackInfo ci) {
        MovementClient.afterPose((ModelBiped) (Object) this, limbSwing, entity);
    }
}
