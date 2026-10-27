package gtnhtweaks.modules.chromatictooltips;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.slprime.chromatictooltips.api.TooltipTarget;

/** The widget of a ModularUI 2 window under the mouse, whose tooltip is drawn. Only loaded with ModularUI 2. */
final class ModularUI2Hover {

    private ModularUI2Hover() {}

    static TooltipTarget hoveredTarget() {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null) return null;
        IWidget hovered = screen.getContext()
            .getHovered();
        TooltipTarget fluid = PaletteTargets.fluidOf(hovered);
        if (fluid != null) return fluid;
        // Other widgets that show an item, like the item displays of GregTech
        if (hovered instanceof RecipeViewerIngredientProvider) {
            ItemStack stack = ((RecipeViewerIngredientProvider) hovered).getStackForRecipeViewer();
            if (stack != null && stack.getItem() != null) return TooltipTarget.ofItem(stack);
        }
        return null;
    }
}
