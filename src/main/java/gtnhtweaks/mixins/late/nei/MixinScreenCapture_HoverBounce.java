package gtnhtweaks.mixins.late.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.minecraft.client.NEIHoverBounce;

/** A grid drawn by NEI into a cached picture is drawn anew on every frame while one of its items bounces. */
@Pseudo
@Mixin(targets = "codechicken.nei.ItemsGrid$ScreenCapture", remap = false)
public class MixinScreenCapture_HoverBounce {

    @Inject(method = "needRefresh(I)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$refreshWhileBouncing(int mode, CallbackInfoReturnable<Boolean> cir) {
        if (NEIHoverBounce.animating()) cir.setReturnValue(true);
    }
}
