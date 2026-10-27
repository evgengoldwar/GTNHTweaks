package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/**
 * Tints the textures of the creative inventory: its background, scroll bar and tabs. The tab icons and the player are
 * drawn after them and keep their colors.
 */
@Mixin(GuiContainerCreative.class)
public class MixinGuiContainerCreative_Menus {

    private static final String DRAW_TEXTURE = "Lnet/minecraft/client/gui/inventory/GuiContainerCreative;drawTexturedModalRect(IIIIII)V";

    @Inject(
        method = { "drawGuiContainerBackgroundLayer", "func_147051_a" },
        at = @At(value = "INVOKE", target = DRAW_TEXTURE),
        require = 0)
    private void gtnhtweaks$beginTint(CallbackInfo ci) {
        MenuStyle.beginCreativeTexture((GuiContainer) (Object) this);
    }

    @Inject(
        method = { "drawGuiContainerBackgroundLayer", "func_147051_a" },
        at = @At(value = "INVOKE", target = DRAW_TEXTURE, shift = At.Shift.AFTER),
        require = 0)
    private void gtnhtweaks$endTint(CallbackInfo ci) {
        MenuStyle.endCreativeTexture();
    }
}
