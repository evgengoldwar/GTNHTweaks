package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/** A swimming or crawling player is drawn lying, turned after the body faces its side as in newer versions. */
@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity_Movement {

    @Inject(method = "rotateCorpse(Lnet/minecraft/entity/EntityLivingBase;FFF)V", at = @At("TAIL"), require = 0)
    private void gtnhtweaks$lie(EntityLivingBase entity, float age, float yaw, float partialTicks, CallbackInfo ci) {
        MovementClient.rotateBody(entity, partialTicks);
    }
}
