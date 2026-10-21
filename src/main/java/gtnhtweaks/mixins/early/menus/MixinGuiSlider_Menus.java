package gtnhtweaks.mixins.early.menus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cpw.mods.fml.client.config.GuiSlider;
import gtnhtweaks.common.render.ThemedButton;
import gtnhtweaks.modules.menus.MenuStyle;

/**
 * The knob of the Forge config sliders. Forge classes are not remapped by the annotation processor, so the method and
 * the call are named in both dev (MCP) and srg form.
 */
@Mixin(value = GuiSlider.class, remap = false)
public class MixinGuiSlider_Menus {

    @Redirect(
        method = { "mouseDragged", "func_146119_b" },
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/client/config/GuiSlider;drawTexturedModalRect(IIIIII)V"),
        require = 0)
    private void gtnhtweaks$drawKnob(GuiSlider self, int x, int y, int u, int v, int width, int height) {
        if (MenuStyle.isActive()) ThemedButton.drawKnob(x, y, width, height, MenuStyle.theme());
        else self.drawTexturedModalRect(x, y, u, v, width, height);
    }

    @Redirect(
        method = { "mouseDragged", "func_146119_b" },
        at = @At(value = "INVOKE", target = "Lcpw/mods/fml/client/config/GuiSlider;func_73729_b(IIIIII)V"),
        require = 0)
    private void gtnhtweaks$drawKnobSrg(GuiSlider self, int x, int y, int u, int v, int width, int height) {
        if (MenuStyle.isActive()) ThemedButton.drawKnob(x, y, width, height, MenuStyle.theme());
        else self.drawTexturedModalRect(x, y, u, v, width, height);
    }
}
