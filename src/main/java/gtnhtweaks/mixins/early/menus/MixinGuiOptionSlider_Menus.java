package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.GuiOptionSlider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import gtnhtweaks.common.render.ThemedButton;
import gtnhtweaks.modules.menus.MenuStyle;

/** The knob of the vanilla option sliders (FOV, volume, video settings...). */
@Mixin(GuiOptionSlider.class)
public class MixinGuiOptionSlider_Menus {

    @Redirect(
        method = "mouseDragged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiOptionSlider;drawTexturedModalRect(IIIIII)V"),
        require = 0)
    private void gtnhtweaks$drawKnob(GuiOptionSlider self, int x, int y, int u, int v, int width, int height) {
        if (MenuStyle.isActive()) ThemedButton.drawKnob(x, y, width, height, MenuStyle.theme());
        else self.drawTexturedModalRect(x, y, u, v, width, height);
    }
}
