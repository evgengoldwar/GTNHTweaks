package gtnhtweaks.mixins.early.mainmenu;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiButton.class)
public interface AccessorGuiButton_MainMenu {

    @Accessor("field_146120_f")
    int gtnhtweaks$getWidth();

    @Accessor("field_146120_f")
    void gtnhtweaks$setWidth(int width);

    @Accessor("field_146121_g")
    int gtnhtweaks$getHeight();

    @Accessor("field_146121_g")
    void gtnhtweaks$setHeight(int height);
}
