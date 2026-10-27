package gtnhtweaks.modules.chromatictooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui;
import com.slprime.chromatictooltips.api.TooltipTarget;

/** The widget of a ModularUI window under the mouse, whose tooltip is drawn. Only loaded with ModularUI. */
final class ModularUIHover {

    private ModularUIHover() {}

    static TooltipTarget hoveredTarget() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof ModularGui)) return null;
        return PaletteTargets.fluidOf(
            ((ModularGui) screen).getCursor()
                .getHovered());
    }
}
