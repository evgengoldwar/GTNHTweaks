package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.movement.Movement;
import gtnhtweaks.modules.minecraft.movement.Pose;

/**
 * A player kept crouching or crawling under a low ceiling moves as slowly as sneaking, also without the sneak key.
 * Swimming in water keeps its speed.
 */
@Mixin(MovementInputFromOptions.class)
public class MixinMovementInputFromOptions_Movement {

    @Inject(method = "updatePlayerMoveState", at = @At("RETURN"))
    private void gtnhtweaks$slowWhenLow(CallbackInfo ci) {
        MovementInput input = (MovementInput) (Object) this;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (input.sneak || player == null || player.movementInput != input) return;
        Pose pose = Movement.state(player).pose;
        if (pose == Pose.STAND || pose == Pose.SWIM && player.isInWater()) return;
        input.moveStrafe *= 0.3F;
        input.moveForward *= 0.3F;
    }
}
