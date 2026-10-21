package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

@Mixin(GuiScreen.class)
public class MixinGuiScreen_Menus {

    /** The dirt background of screens opened outside of a world. */
    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drawBackground(int tint, CallbackInfo ci) {
        if (!MenuStyle.isActive()) return;
        GuiScreen self = (GuiScreen) (Object) this;
        MenuStyle.drawScreenBackground(self.width, self.height);
        ci.cancel();
    }

    /** Added by Forge, used for every tooltip of a screen. */
    @Inject(method = "drawHoveringText", at = @At("HEAD"), remap = false, require = 0)
    private void gtnhtweaks$tooltipStart(CallbackInfo ci) {
        MenuStyle.setInTooltip(true);
    }

    @Inject(method = "drawHoveringText", at = @At("RETURN"), remap = false, require = 0)
    private void gtnhtweaks$tooltipEnd(CallbackInfo ci) {
        MenuStyle.setInTooltip(false);
    }
}
