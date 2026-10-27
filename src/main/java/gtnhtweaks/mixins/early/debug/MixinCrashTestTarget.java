package gtnhtweaks.mixins.early.debug;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mixin that can never be applied, to test the crash screen: it targets a method that does not exist, so loading
 * CrashTestTarget fails with a mixin error. Only applied when crashTest/mixinCrash in debug.cfg is not OFF.
 */
@Mixin(targets = "gtnhtweaks.modules.debug.CrashTestTarget", remap = false)
public class MixinCrashTestTarget {

    @Inject(method = "gtnhtweaks$methodThatDoesNotExist", at = @At("HEAD"), require = 1)
    private static void gtnhtweaks$neverApplied(CallbackInfo ci) {}
}
