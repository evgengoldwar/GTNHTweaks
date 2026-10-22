package gtnhtweaks.mixins;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

import javax.annotation.Nonnull;

public enum TargetMods implements ITargetMod {

    BETTER_LOADING_SCREEN("alexiil.mods.load.coremod.LoadingScreenLoadPlugin", "betterloadingscreen"),
    GREGTECH(null, "gregtech");

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
