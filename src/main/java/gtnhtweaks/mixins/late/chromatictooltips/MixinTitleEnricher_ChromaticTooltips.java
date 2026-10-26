package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipLines;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.enricher.TitleEnricher;

import gtnhtweaks.modules.chromatictooltips.FoundTitles;

/** Titles a tooltip of bare lines by its found fluid or item: see {@link FoundTitles}. */
@Mixin(value = TitleEnricher.class, remap = false)
public abstract class MixinTitleEnricher_ChromaticTooltips {

    @Shadow
    protected abstract TooltipLines fluidTitle(TooltipTarget target);

    @Shadow
    protected abstract TooltipLines itemTitle(TooltipTarget target);

    @Inject(
        method = "build(Lcom/slprime/chromatictooltips/api/TooltipContext;)Lcom/slprime/chromatictooltips/api/TooltipLines;",
        at = @At("HEAD"),
        cancellable = true)
    private void gtnhtweaks$titleOfFoundTarget(TooltipContext context, CallbackInfoReturnable<TooltipLines> cir) {
        TooltipTarget target = FoundTitles.titleTarget(context);
        if (target != null) cir.setReturnValue(target.isFluid() ? fluidTitle(target) : itemTitle(target));
    }
}
