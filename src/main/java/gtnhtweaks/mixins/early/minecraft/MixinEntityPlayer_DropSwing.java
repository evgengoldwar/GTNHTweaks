package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player swings the hand when throwing an item out of an inventory: out of the window, with the drop key over a
 * slot, from the creative inventory or the item held by the mouse when the inventory is closed. Only the own player
 * on the client: its swing is sent to the server, which shows it to the other players.
 */
@Mixin(EntityPlayer.class)
public class MixinEntityPlayer_DropSwing {

    @Inject(
        method = "dropPlayerItemWithRandomChoice(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/item/EntityItem;",
        at = @At("HEAD"))
    private void gtnhtweaks$swingOnDrop(ItemStack stack, boolean randomly, CallbackInfoReturnable<EntityItem> cir) {
        Object self = this;
        if (stack != null && self instanceof EntityPlayerSP) ((EntityPlayerSP) self).swingItem();
    }
}
