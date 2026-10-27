package gtnhtweaks.mixins.early.itemphysics;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.itemphysics.client.ItemPhysicsRenderer;

/** Dropped items in the world are drawn lying on the ground. Items in frames and fake items of mods stay vanilla. */
@Mixin(RenderItem.class)
public class MixinRenderItem_ItemPhysics {

    @Shadow
    public boolean renderWithColor;

    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$doRender(EntityItem entity, double x, double y, double z, float yaw, float partialTicks,
        CallbackInfo ci) {
        if (ItemPhysicsRenderer.render(entity, x, y, z, partialTicks, renderWithColor)) ci.cancel();
    }
}
