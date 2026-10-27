package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.SlotHoverBounce;

/** The item of the slot under the mouse squeezes and springs back when the mouse comes onto it. */
@Mixin(GuiContainer.class)
public class MixinGuiContainer_HoverBounce {

    @Shadow
    public Container inventorySlots;
    @Shadow
    protected int guiLeft;
    @Shadow
    protected int guiTop;

    @Inject(method = "drawScreen", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$findHovered(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        Slot underMouse = null;
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        for (Object object : inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            // the area of GuiContainer.isMouseOverSlot: the slot and its frame
            if (slot.func_111238_b() && x >= slot.xDisplayPosition - 1
                && x < slot.xDisplayPosition + 17
                && y >= slot.yDisplayPosition - 1
                && y < slot.yDisplayPosition + 17) {
                underMouse = slot;
                break;
            }
        }
        SlotHoverBounce.update(inventorySlots, underMouse);
    }

    /** func_146977_a draws one slot: its item, its count and its durability bar. */
    @Inject(method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$beginBounce(Slot slot, CallbackInfo ci) {
        SlotHoverBounce.begin(slot);
    }

    @Inject(method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$endBounce(Slot slot, CallbackInfo ci) {
        SlotHoverBounce.end();
    }
}
