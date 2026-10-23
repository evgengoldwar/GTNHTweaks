package gtnhtweaks.mixins.late.nei;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Ctrl + Shift + click on the "+" of a recipe moves the items of one craft into the crafting grid. Shift + click
 * still moves as many crafts as possible: NEI asks for the most with a multiplier of 0, and 1 means a single craft.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.GuiOverlayButton", remap = false)
public class MixinGuiOverlayButton_NEI {

    @ModifyArg(
        method = "overlayRecipe(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/nei/recipe/RecipeHandlerRef;fillCraftingGrid(Lnet/minecraft/client/gui/inventory/GuiContainer;I)V"),
        index = 1,
        require = 0)
    private int gtnhtweaks$oneCraftWithCtrl(int multiplier) {
        return GuiScreen.isCtrlKeyDown() ? 1 : multiplier;
    }
}
