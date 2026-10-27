package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/**
 * The own player is lit by the light where it stands, also while a frame has it lowered to its eyes: vanilla takes the
 * light at a height from its position, which would be in the floor under a crawling player.
 */
@Mixin(Entity.class)
public class MixinEntity_MovementLight {

    @Inject(method = { "getBrightnessForRender", "getBrightness" }, at = @At("HEAD"))
    private void gtnhtweaks$standForLight(float partialTicks, CallbackInfoReturnable<?> cir) {
        MovementClient.standForLight((Entity) (Object) this, true);
    }

    @Inject(method = { "getBrightnessForRender", "getBrightness" }, at = @At("RETURN"))
    private void gtnhtweaks$backToEyes(float partialTicks, CallbackInfoReturnable<?> cir) {
        MovementClient.standForLight((Entity) (Object) this, false);
    }
}
