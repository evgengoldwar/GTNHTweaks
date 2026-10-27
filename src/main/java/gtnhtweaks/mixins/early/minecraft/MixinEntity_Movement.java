package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.movement.Movement;
import gtnhtweaks.modules.minecraft.movement.Pose;

/**
 * A swimming or crawling player is in water when water touches its box anywhere, as in newer versions. Vanilla looks
 * for water only in the middle of the box, 0.4 off its top and bottom, which leaves nothing of the 0.6 high box of the
 * swimming pose: the player would leave the water and stand up at once.
 */
@Mixin(Entity.class)
public class MixinEntity_Movement {

    @Shadow
    protected boolean inWater;

    @Inject(method = "handleWaterMovement", at = @At("RETURN"), cancellable = true)
    private void gtnhtweaks$lowBoxInWater(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) self;
        if (Movement.state(player).pose != Pose.SWIM || inWater) return;
        if (!Movement.touchingWater(player)) return;
        inWater = true;
        player.fallDistance = 0;
        player.extinguish();
        cir.setReturnValue(true);
    }
}
