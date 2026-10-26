package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.slprime.chromatictooltips.api.TooltipTarget;

import gtnhtweaks.modules.chromatictooltips.PaletteTargets;

/**
 * Chromatic Spectrum picks the palette of every drawn tooltip here, from the item or fluid of the tooltip, and the
 * gray palette when there is none. A tooltip of bare lines gets the palette of the target found for it.
 */
@Pseudo
@Mixin(targets = "com.danastr.chromaticspectrum.handler.SpectrumEventHandler", remap = false)
public class MixinSpectrumEventHandler_ChromaticTooltips {

    @ModifyVariable(
        method = "updateActivePalette(Lcom/slprime/chromatictooltips/api/TooltipTarget;)V",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0)
    private static TooltipTarget gtnhtweaks$paletteTarget(TooltipTarget target) {
        return PaletteTargets.forPalette(target);
    }
}
