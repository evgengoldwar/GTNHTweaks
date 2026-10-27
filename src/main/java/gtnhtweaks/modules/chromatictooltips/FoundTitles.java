package gtnhtweaks.modules.chromatictooltips;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipLines;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.component.SpaceComponent;
import com.slprime.chromatictooltips.component.TextComponent;

import gtnhtweaks.mixins.late.chromatictooltips.AccessorTooltipLines_ChromaticTooltips;

/**
 * The title of a tooltip of bare lines whose fluid or item was found: the title of a real fluid or item tooltip, next
 * to the icon and in the colors of the theme, in place of the first line. ModularUI 2 tooltips start with an empty
 * line put in by Chromatic Tooltips Compat, and then the name of the fluid, which moves to the title. Tooltips with
 * a title of their own, like the quest name in BetterQuesting, keep it.
 */
public final class FoundTitles {

    private FoundTitles() {}

    /** The found target to title the tooltip being built by, or null to keep its own title. */
    public static TooltipTarget titleTarget(TooltipContext context) {
        TooltipTarget target = PaletteTargets.requested();
        if (target == null || !PaletteTargets.isBare(context.getTarget())) return null;
        String name = nameOf(target);
        if (name.isEmpty()) return null;
        List<ITooltipComponent> lines = context.getContextTooltip();
        String first = lines.isEmpty() ? "" : textOf(lines.get(0));
        if (first == null) return null;
        return first.isEmpty() || first.equalsIgnoreCase(name) ? target : null;
    }

    /** Removes the name line from the lines under the title, now that the title shows the name. */
    public static void removeNameLine(TooltipContext context, TooltipLines body) {
        TooltipTarget target = titleTarget(context);
        if (target == null || body == null) return;
        String name = nameOf(target);
        List<Object> lines = ((AccessorTooltipLines_ChromaticTooltips) body).gtnhtweaks$getTextLines();
        while (!lines.isEmpty() && lines.get(0) instanceof SpaceComponent) lines.remove(0);
        if (!lines.isEmpty() && name.equalsIgnoreCase(textOf(lines.get(0)))) lines.remove(0);
    }

    private static String nameOf(TooltipTarget target) {
        try {
            String name;
            if (target.isFluid()) {
                FluidStack fluid = target.getFluid();
                name = fluid.getFluid()
                    .getLocalizedName(fluid);
            } else {
                name = target.getItem()
                    .getDisplayName();
            }
            return strip(name);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The text of a line with one line of text, without formatting; null for other components. */
    private static String textOf(Object line) {
        if (line instanceof String) return strip((String) line);
        if (line instanceof TextComponent) {
            List<String> text = ((TextComponent) line).getLines();
            return text.size() == 1 ? strip(text.get(0)) : null;
        }
        return null;
    }

    private static String strip(String text) {
        if (text == null) return "";
        String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return stripped == null ? "" : stripped.trim();
    }
}
