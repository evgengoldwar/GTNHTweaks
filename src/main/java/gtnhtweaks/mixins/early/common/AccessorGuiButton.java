package gtnhtweaks.mixins.early.common;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiButton.class)
public interface AccessorGuiButton {

    @Accessor("width")
    int gtnhtweaks$getWidth();

    @Accessor("width")
    void gtnhtweaks$setWidth(int width);

    @Accessor("height")
    int gtnhtweaks$getHeight();

    @Accessor("height")
    void gtnhtweaks$setHeight(int height);
}
