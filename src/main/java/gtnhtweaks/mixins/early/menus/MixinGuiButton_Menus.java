package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.common.render.ThemedButton;
import gtnhtweaks.modules.menus.MenuStyle;

/**
 * Plain buttons in the GTNH style. Only exact classes: sliders and custom buttons rely on their own drawing code, so
 * they keep the vanilla look.
 */
@Mixin(GuiButton.class)
public class MixinGuiButton_Menus {

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        GuiButton self = (GuiButton) (Object) this;
        Class<?> type = self.getClass();
        if (type != GuiButton.class && type != GuiOptionButton.class) return;
        if (!MenuStyle.isActive()) return;
        ThemedButton.draw(self, self.displayString, mc.fontRenderer, mouseX, mouseY, MenuStyle.theme());
        ci.cancel();
    }
}
