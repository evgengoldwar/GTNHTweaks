package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.movement.Movement;

/**
 * The players on the server have their eyes as low as their pose too: their eye height is a fixed 1.62 of their own,
 * not that of every player.
 */
@Mixin(EntityPlayerMP.class)
public class MixinEntityPlayerMP_Movement {

    @Inject(method = "getEyeHeight", at = @At("RETURN"), cancellable = true)
    private void gtnhtweaks$poseEyes(CallbackInfoReturnable<Float> cir) {
        float drop = Movement.eyeDrop((EntityPlayer) (Object) this);
        if (drop != 0) cir.setReturnValue(cir.getReturnValue() - drop);
    }
}
