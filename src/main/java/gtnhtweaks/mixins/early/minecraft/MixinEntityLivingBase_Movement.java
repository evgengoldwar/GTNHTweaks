package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/** The own player looks at blocks and entities from the lowered camera of its pose, so the crosshair stays true. */
@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBase_Movement {

    @Inject(method = "getPosition", at = @At("RETURN"), cancellable = true, require = 0)
    private void gtnhtweaks$eyes(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        float drop = MovementClient.cameraDrop((Entity) (Object) this, partialTicks);
        if (drop != 0) cir.setReturnValue(
            cir.getReturnValue()
                .addVector(0, -drop, 0));
    }
}
