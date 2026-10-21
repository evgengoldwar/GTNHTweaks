package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.gui.GuiSlot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.menus.MenuStyle;

/** Vanilla scrolling lists: worlds, servers, languages and the Forge config entries. */
@Mixin(GuiSlot.class)
public class MixinGuiSlot_Menus {

    @Shadow
    public int width;
    @Shadow
    public int top;
    @Shadow
    public int bottom;
    @Shadow
    public int left;
    @Shadow
    public int right;

    /** Added by Forge: the dirt behind the list entries. */
    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtnhtweaks$drawContainerBackground(CallbackInfo ci) {
        if (!MenuStyle.themesBackground()) return;
        MenuStyle.drawListPanel(left, top, right, bottom);
        ci.cancel();
    }

    /** The dirt strips above and below the list. */
    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$overlayBackground(int y0, int y1, int alpha0, int alpha1, CallbackInfo ci) {
        if (!MenuStyle.themesBackground()) return;
        MenuStyle.drawBackgroundSlice(0, y0, width, y1);
        ci.cancel();
    }

    @ModifyArg(
        method = "drawSelectionBox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_I(I)V"),
        require = 0)
    private int gtnhtweaks$selectionColor(int color) {
        return MenuStyle.selectionColor(color);
    }
}
