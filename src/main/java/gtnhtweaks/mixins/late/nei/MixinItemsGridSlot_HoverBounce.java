package gtnhtweaks.mixins.late.nei;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.lib.vec.Rectangle4i;
import gtnhtweaks.modules.minecraft.client.NEIHoverBounce;

/** Scales the item of a bouncing cell of an NEI grid. Every kind of cell draws its items through this method. */
@Pseudo
@Mixin(targets = "codechicken.nei.ItemsGrid$ItemsGridSlot", remap = false)
public class MixinItemsGridSlot_HoverBounce {

    @Inject(
        method = "drawItem(Lnet/minecraft/item/ItemStack;Lcodechicken/lib/vec/Rectangle4i;)V",
        at = @At("HEAD"),
        require = 0)
    private void gtnhtweaks$beginBounce(ItemStack stack, Rectangle4i rect, CallbackInfo ci) {
        NEIHoverBounce.begin(rect.x, rect.y, rect.w, rect.h);
    }

    @Inject(
        method = "drawItem(Lnet/minecraft/item/ItemStack;Lcodechicken/lib/vec/Rectangle4i;)V",
        at = @At("RETURN"),
        require = 0)
    private void gtnhtweaks$endBounce(ItemStack stack, Rectangle4i rect, CallbackInfo ci) {
        NEIHoverBounce.end();
    }
}
