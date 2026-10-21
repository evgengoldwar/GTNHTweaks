package gtnhtweaks.mixins.early.menus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/** Tooltips of the Forge mod config screens. */
@Pseudo
@Mixin(targets = "cpw.mods.fml.client.config.GuiUtils", remap = false)
public class MixinGuiUtils_Menus {

    @Inject(method = "drawHoveringText", at = @At("HEAD"), require = 0)
    private static void gtnhtweaks$tooltipStart(CallbackInfo ci) {
        MenuStyle.setInTooltip(true);
    }

    @Inject(method = "drawHoveringText", at = @At("RETURN"), require = 0)
    private static void gtnhtweaks$tooltipEnd(CallbackInfo ci) {
        MenuStyle.setInTooltip(false);
    }
}
