package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.client.GuiScrollingList;
import gtnhtweaks.modules.menus.MenuStyle;

/** The Forge list widget used by the mod list. It draws its dirt background itself. */
@Mixin(value = GuiScrollingList.class, remap = false)
public class MixinGuiScrollingList_Menus {

    @Shadow
    @Final
    protected int listWidth;
    @Shadow
    @Final
    protected int top;
    @Shadow
    @Final
    protected int bottom;
    @Shadow
    @Final
    protected int left;
    @Shadow
    @Final
    private int right;

    /** The first tessellator draw of drawScreen is the dirt behind the entries (only drawn outside of a world). */
    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()I",
            ordinal = 0,
            shift = At.Shift.AFTER,
            remap = true),
        require = 0)
    private void gtnhtweaks$drawListBackground(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (Minecraft.getMinecraft().theWorld != null || !MenuStyle.isActive()) return;
        MenuStyle.drawListPanel(left, top, right, bottom);
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$overlayBackground(int y0, int y1, int alpha0, int alpha1, CallbackInfo ci) {
        if (!MenuStyle.isActive()) return;
        MenuStyle.drawBackgroundSlice(0, y0, listWidth + 30, y1);
        ci.cancel();
    }

    @ModifyArg(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_I(I)V",
            remap = true),
        require = 0)
    private int gtnhtweaks$selectionColor(int color) {
        return MenuStyle.selectionColor(color);
    }
}
