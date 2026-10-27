package gtnhtweaks.mixins.late.chromatictooltips;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.slprime.chromatictooltips.api.TooltipLines;

@Mixin(value = TooltipLines.class, remap = false)
public interface AccessorTooltipLines_ChromaticTooltips {

    @Accessor("textLines")
    List<Object> gtnhtweaks$getTextLines();
}
