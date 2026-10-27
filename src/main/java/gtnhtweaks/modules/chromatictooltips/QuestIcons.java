package gtnhtweaks.modules.chromatictooltips;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.slprime.chromatictooltips.api.TooltipLines;
import com.slprime.chromatictooltips.api.TooltipTarget;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import gtnhtweaks.mixins.late.chromatictooltips.AccessorTooltipLines_ChromaticTooltips;

/**
 * The icon of the last quest whose tooltip BetterQuesting built: the quest tooltip is passed to Chromatic Tooltips as
 * bare lines, which are matched to it. Only loaded with BetterQuesting.
 */
public final class QuestIcons {

    private static final List<Object> lines = new ArrayList<>();
    private static ItemStack icon;

    private QuestIcons() {}

    /** Called with the tooltip of a quest button of the quest line. */
    @SuppressWarnings("unchecked")
    public static void onQuestTooltip(Object button, List<String> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) return;
        Map.Entry<UUID, IQuest> quest = ((PanelButtonStorage<Map.Entry<UUID, IQuest>>) button).getStoredValue();
        if (quest == null || quest.getValue() == null) return;
        BigItemStack questIcon = quest.getValue()
            .getProperty(NativeProps.ICON);
        lines.clear();
        PaletteTargets.splitLines(tooltip, lines);
        icon = questIcon != null ? questIcon.getBaseStack() : null;
    }

    static TooltipTarget targetFor(TooltipLines tooltip) {
        if (icon == null || icon.getItem() == null) return null;
        List<Object> requested = ((AccessorTooltipLines_ChromaticTooltips) tooltip).gtnhtweaks$getTextLines();
        return lines.equals(requested) ? TooltipTarget.ofItem(icon) : null;
    }
}
