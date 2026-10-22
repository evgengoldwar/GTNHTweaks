package gtnhtweaks.mixins.early.debug;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.debug.CrashTestTarget;
import gtnhtweaks.modules.debug.DebugModule;

/** Test crash BEFORE_WINDOW: the mixin error happens when the game starts, before its window is made. */
@Mixin(Minecraft.class)
public class MixinMinecraft_CrashTest {

    @Inject(method = "startGame", at = @At("HEAD"))
    private void gtnhtweaks$crashBeforeWindow(CallbackInfo ci) {
        if ("BEFORE_WINDOW".equals(DebugModule.mixinCrash)) CrashTestTarget.load();
    }
}
