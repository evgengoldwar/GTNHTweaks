package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.movement.Movement;
import gtnhtweaks.modules.minecraft.movement.MovementHolder;
import gtnhtweaks.modules.minecraft.movement.MovementState;

/** Every player keeps its pose of the crouching and swimming, and has its eyes as low as the pose. */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer_Movement implements MovementHolder {

    @Unique
    private MovementState gtnhtweaks$movement;

    @Override
    public MovementState gtnhtweaks$movement() {
        if (gtnhtweaks$movement == null) gtnhtweaks$movement = new MovementState();
        return gtnhtweaks$movement;
    }

    @Inject(method = "getEyeHeight", at = @At("RETURN"), cancellable = true)
    private void gtnhtweaks$poseEyes(CallbackInfoReturnable<Float> cir) {
        float drop = Movement.eyeDrop((EntityPlayer) (Object) this);
        if (drop != 0) cir.setReturnValue(cir.getReturnValue() - drop);
    }
}
