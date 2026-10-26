package gtnhtweaks.mixins.late.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.NEIHoverBounce;

/** Finds the cell of an NEI grid under the mouse before the grid is drawn, the same way NEI does. */
@Pseudo
@Mixin(targets = "codechicken.nei.ItemsGrid", remap = false)
public abstract class MixinItemsGrid_HoverBounce {

    @Shadow
    public static int SLOT_SIZE;
    @Shadow
    protected int marginLeft;
    @Shadow
    protected int marginTop;
    @Shadow
    protected int paddingLeft;
    @Shadow
    protected int rows;
    @Shadow
    protected int columns;

    @Shadow
    public abstract boolean contains(int px, int py);

    @Inject(method = "draw(II)V", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$findHovered(int mouseX, int mouseY, CallbackInfo ci) {
        int size = SLOT_SIZE;
        if (size > 0 && contains(mouseX, mouseY)) {
            int row = (mouseY - marginTop) / size;
            int column = (mouseX - marginLeft - paddingLeft) / size;
            if (row < rows && column < columns) {
                NEIHoverBounce.update(this, marginLeft + paddingLeft + column * size, marginTop + row * size, size);
                return;
            }
        }
        NEIHoverBounce.update(this, 0, 0, -1);
    }
}
