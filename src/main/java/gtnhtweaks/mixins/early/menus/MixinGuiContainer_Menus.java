package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/** Tints the background texture of vanilla inventories, before slots and items are drawn on it. */
@Mixin(GuiContainer.class)
public class MixinGuiContainer_Menus {

    @Shadow
    protected int xSize;
    @Shadow
    protected int ySize;
    @Shadow
    protected int guiLeft;
    @Shadow
    protected int guiTop;

    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V"),
        require = 0)
    private void gtnhtweaks$recordBackgroundTextures(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MenuStyle.beginInventoryBackground();
    }

    /** Skipped if a resource pack replaces a texture the background was drawn with. */
    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V",
            shift = At.Shift.AFTER),
        require = 0)
    private void gtnhtweaks$tintBackground(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        boolean replacedByPack = MenuStyle.endInventoryBackground();
        // The creative inventory tints its textures itself, see MixinGuiContainerCreative_Menus
        if (!replacedByPack && !((Object) this instanceof GuiContainerCreative)
            && MenuStyle.tintsInventory((GuiContainer) (Object) this)) {
            MenuStyle.tintInventory(guiLeft, guiTop, xSize, ySize);
        }
    }
}
