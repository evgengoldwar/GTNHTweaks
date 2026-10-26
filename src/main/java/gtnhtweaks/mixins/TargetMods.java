package gtnhtweaks.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

public enum TargetMods implements ITargetMod {

    GREGTECH(null, "gregtech"),
    NEI(null, "NotEnoughItems"),
    CHROMATIC_TOOLTIPS(null, "chromatictooltips"),
    CHROMATIC_SPECTRUM(null, "chromaticspectrum"),
    BETTER_QUESTING(null, "betterquesting");

    private final TargetModBuilder builder;

    TargetMods(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setModId(modId);
        if (coreModClass != null) builder.setCoreModClass(coreModClass);
    }

    @Nonnull
    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }
}
