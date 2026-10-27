package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ScreenShake;

/** The explosions the client is told about: the server sends every one near the player, with its size. */
@Mixin(Explosion.class)
public class MixinExplosion_ScreenShake {

    @Shadow
    private World worldObj;

    @Inject(method = "doExplosionB", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$shake(boolean particles, CallbackInfo ci) {
        if (worldObj == null || !worldObj.isRemote) return;
        Explosion self = (Explosion) (Object) this;
        ScreenShake.explosion(self.explosionX, self.explosionY, self.explosionZ, self.explosionSize);
    }
}
