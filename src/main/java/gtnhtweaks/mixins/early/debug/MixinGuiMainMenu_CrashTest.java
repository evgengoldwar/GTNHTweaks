package gtnhtweaks.mixins.early.debug;

import net.minecraft.client.gui.GuiMainMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mixin that can never be applied, to test the crash screen: it targets a method GuiMainMenu does not have, so the
 * game crashes with a mixin error when the main menu class is loaded. Only applied with crashTest.mixin in debug.cfg.
 */
@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu_CrashTest {

    @Inject(method = "gtnhtweaks$methodThatDoesNotExist", at = @At("HEAD"), require = 1, remap = false)
    private void gtnhtweaks$neverApplied(CallbackInfo ci) {}
}
