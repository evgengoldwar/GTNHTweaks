package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/**
 * Buttons in the GTNH style: plain buttons, option buttons, sliders and subclasses that only change behavior. Buttons
 * whose class draws itself keep their look.
 */
@Mixin(GuiButton.class)
public class MixinGuiButton_Menus {

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        GuiButton self = (GuiButton) (Object) this;
        if (!MenuStyle.isActive() || !MenuStyle.canTheme(self, GuiButton.class)) return;
        MenuStyle.drawButton(self, mc, mouseX, mouseY);
        ci.cancel();
    }
}
