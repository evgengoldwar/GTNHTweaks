package gtnhtweaks.mixins.early.itemphysics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.itemphysics.client.ItemPhysicsRenderer;

/**
 * Places and speeds of dropped items sent by the server: an item lying on the client keeps its place when the server
 * puts it close by, and glides to a farther place instead of jumping. Both methods are only called by the packets of
 * the server on the client.
 */
@Mixin(Entity.class)
public class MixinEntity_ItemPhysics {

    @Inject(method = "setPositionAndRotation2(DDDFFI)V", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$keepPlace(double x, double y, double z, float yaw, float pitch, int increments,
        CallbackInfo ci) {
        Object self = this;
        if (self instanceof EntityItem && ItemPhysicsRenderer.keepPlace((EntityItem) self, x, y, z)) ci.cancel();
    }

    @Inject(method = "setPositionAndRotation2(DDDFFI)V", at = @At("RETURN"))
    private void gtnhtweaks$tookPlace(double x, double y, double z, float yaw, float pitch, int increments,
        CallbackInfo ci) {
        Object self = this;
        if (self instanceof EntityItem) ItemPhysicsRenderer.tookPlace((EntityItem) self);
    }

    @Inject(method = "setVelocity(DDD)V", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$keepSpeed(double motionX, double motionY, double motionZ, CallbackInfo ci) {
        Object self = this;
        if (self instanceof EntityItem && ItemPhysicsRenderer.keepSpeed((EntityItem) self, motionX, motionY, motionZ)) {
            ci.cancel();
        }
    }
}
