package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.item.EntityItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player swings the hand when throwing the held item with the drop key, as in newer versions. The client only
 * asks the server to drop it, so the swing is started here, and only when there is an item in the hand.
 */
@Mixin(EntityClientPlayerMP.class)
public class MixinEntityClientPlayerMP_DropSwing {

    @Inject(method = "dropOneItem(Z)Lnet/minecraft/entity/item/EntityItem;", at = @At("HEAD"))
    private void gtnhtweaks$swingOnDrop(boolean wholeStack, CallbackInfoReturnable<EntityItem> cir) {
        EntityClientPlayerMP self = (EntityClientPlayerMP) (Object) this;
        if (self.getCurrentEquippedItem() != null) self.swingItem();
    }
}
