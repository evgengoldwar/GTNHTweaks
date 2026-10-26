package gtnhtweaks.mixins.late.chromatictooltips;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slprime.chromatictooltips.util.TooltipFontContext;

import gtnhtweaks.modules.chromatictooltips.DrawnTextWidths;

/**
 * Sizes tooltip lines by how wide they draw. Every component of Chromatic Tooltips measures and draws text through
 * TooltipFontContext, so the frame grows to text that draws wider than FontRenderer measures it.
 */
@Mixin(value = TooltipFontContext.class, remap = false)
public class MixinTooltipFontContext_ChromaticTooltips {

    @Redirect(
        method = "drawString(Ljava/lang/String;IIIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;IIIZ)I",
            remap = true))
    private static int gtnhtweaks$rememberDrawnWidth(FontRenderer font, String text, int x, int y, int color,
        boolean shadow) {
        int end = font.drawString(text, x, y, color, shadow);
        DrawnTextWidths.onDrawn(font, text, x, shadow, end);
        return end;
    }

    @Inject(method = "getStringWidth(Ljava/lang/String;)I", at = @At("RETURN"), cancellable = true)
    private static void gtnhtweaks$fitDrawnWidth(String text, CallbackInfoReturnable<Integer> cir) {
        int measured = cir.getReturnValueI();
        int fitted = DrawnTextWidths.fit(text, measured);
        if (fitted != measured) cir.setReturnValue(fitted);
    }
}
