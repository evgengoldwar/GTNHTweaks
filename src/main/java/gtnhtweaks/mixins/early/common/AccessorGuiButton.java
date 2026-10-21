package gtnhtweaks.mixins.early.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

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

    /** The hovered flag that vanilla drawButton updates and isMouseOver (func_146115_a) reads. */
    @Accessor("field_146123_n")
    void gtnhtweaks$setHovered(boolean hovered);

    /** Sliders update their value and draw their knob here; vanilla drawButton calls it between frame and label. */
    @Invoker("mouseDragged")
    void gtnhtweaks$mouseDragged(Minecraft mc, int mouseX, int mouseY);
}
