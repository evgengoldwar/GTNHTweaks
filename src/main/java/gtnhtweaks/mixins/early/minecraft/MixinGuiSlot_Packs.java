package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.gui.GuiSlot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import gtnhtweaks.modules.minecraft.client.PackListSlot;
import gtnhtweaks.modules.minecraft.client.ResourcePackScreen;

/**
 * The lists of the resource pack screen: their scroll, and the mouse held still for them while a pack is dragged, so
 * pressing on a pack does not scroll the list as vanilla does.
 */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot_Packs implements PackListSlot {

    @Shadow
    protected float amountScrolled;
    @Shadow
    protected int headerPadding;

    @Override
    public float gtnhtweaks$getScrolled() {
        return amountScrolled;
    }

    @Override
    public void gtnhtweaks$setScrolled(float scrolled) {
        amountScrolled = scrolled;
    }

    @Override
    public int gtnhtweaks$getHeaderPadding() {
        return headerPadding;
    }

    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int gtnhtweaks$mouseX(int mouseX) {
        return ResourcePackScreen.listMouseX((GuiSlot) (Object) this, mouseX);
    }

    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private int gtnhtweaks$mouseY(int mouseY) {
        return ResourcePackScreen.listMouseY((GuiSlot) (Object) this, mouseY);
    }
}
