package gtnhtweaks.mixins.late.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.nei.client.BookmarkAmountChange;

@Pseudo
@Mixin(targets = "codechicken.nei.bookmark.BookmarkGrid", remap = false)
public class MixinBookmarkGrid_Quantity {

    @Inject(method = "shiftMultiplier(JJI)J", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$exactQuantity(long multiplier, long shift, int minimum, CallbackInfoReturnable<Long> cir) {
        Long value = BookmarkAmountChange.override(this, multiplier, minimum);
        if (value != null) cir.setReturnValue(value);
    }
}
