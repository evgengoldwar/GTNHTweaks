package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.Gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/**
 * Slider knobs: every slider (vanilla options, sound volumes, Forge config, mod sliders) draws its knob with
 * drawTexturedModalRect from mouseDragged. While a themed button runs mouseDragged, those draws become themed knobs.
 */
@Mixin(Gui.class)
public class MixinGui_Menus {

    @Inject(method = "drawTexturedModalRect(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drawKnob(int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        if (MenuStyle.drawSliderKnob(x, y, width, height)) ci.cancel();
    }
}
