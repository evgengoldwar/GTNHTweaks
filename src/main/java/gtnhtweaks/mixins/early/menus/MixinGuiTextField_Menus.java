package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import gtnhtweaks.modules.menus.MenuStyle;

/** Border and fill of text fields (server address, world name, search...). */
@Mixin(GuiTextField.class)
public class MixinGuiTextField_Menus {

    @ModifyArg(
        method = "drawTextBox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V"),
        index = 4,
        require = 0)
    private int gtnhtweaks$fieldColor(int color) {
        return MenuStyle.textFieldColor(color);
    }
}
