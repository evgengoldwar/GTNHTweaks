package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.client.config.GuiButtonExt;
import gtnhtweaks.modules.menus.MenuStyle;

/**
 * The Forge config screen buttons (and the config sliders extending them) override drawButton, so they need their own
 * hook. Forge classes are not remapped by the annotation processor, so the method is named in both dev (MCP) and srg
 * form.
 */
@Mixin(value = GuiButtonExt.class, remap = false)
public class MixinGuiButtonExt_Menus {

    @Inject(method = { "drawButton", "func_146112_a" }, at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$drawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        GuiButton self = (GuiButton) (Object) this;
        if (!MenuStyle.isActive() || !MenuStyle.canTheme(self, GuiButtonExt.class)) return;
        MenuStyle.drawButton(self, mc, mouseX, mouseY);
        ci.cancel();
    }
}
