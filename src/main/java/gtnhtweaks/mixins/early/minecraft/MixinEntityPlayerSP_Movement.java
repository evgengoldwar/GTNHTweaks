package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.movement.Movement;
import gtnhtweaks.modules.minecraft.movement.Pose;

/**
 * A crouching, swimming or crawling own player is not pushed out of the blocks above it: vanilla pushes a player out of
 * a gap lower than two blocks, as a standing player would be in the blocks there.
 */
@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP_Movement {

    @Inject(method = "func_145771_j", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$stayInGap(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Movement.state((EntityPlayer) (Object) this).pose != Pose.STAND) cir.setReturnValue(false);
    }
}
