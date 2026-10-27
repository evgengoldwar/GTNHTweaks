package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.menus.MenuStyle;

/**
 * Theme text colors on restyled screens. renderString is used by drawString and drawSplitString alike. Lower priority
 * than the default so the color is changed before other mods (e.g. Angelica's batching renderer) read it.
 */
@Mixin(value = FontRenderer.class, priority = 900)
public class MixinFontRenderer_Menus {

    @ModifyVariable(method = "renderString", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private int gtnhtweaks$themeColor(int color) {
        return MenuStyle.textColor(color);
    }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$skipShadow(String text, int x, int y, int color, boolean shadow,
        CallbackInfoReturnable<Integer> cir) {
        if (shadow && MenuStyle.suppressShadow()) cir.setReturnValue(0);
    }
}
