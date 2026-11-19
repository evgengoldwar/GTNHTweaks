package gtnhtweaks.mixins.late.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.nei.client.BookmarkQuantityEditor;

/** Capture input before NEI's passive handlers, so editing never types into search or moves inventory items. */
@Pseudo
@Mixin(targets = "codechicken.nei.guihook.GuiContainerManager", remap = false)
public class MixinGuiContainerManager_Quantity {

    @Shadow
    public GuiContainer window;
    @Shadow
    private int clickHandled;
    @Unique
    private BookmarkQuantityEditor gtnhtweaks$quantity = new BookmarkQuantityEditor();

    @Inject(method = "load()V", at = @At("HEAD"), require = 1)
    private void gtnhtweaks$reset(CallbackInfo ci) {
        gtnhtweaks$quantity = new BookmarkQuantityEditor();
    }

    @Inject(method = "firstKeyTyped(CI)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$key(char character, int key, CallbackInfoReturnable<Boolean> cir) {
        if (gtnhtweaks$quantity.key(window, character, key)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked(III)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$click(int x, int y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (gtnhtweaks$quantity.click(window, x, y, button)) {
            clickHandled |= 1 << button;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled(I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$wheel(int scroll, CallbackInfo ci) {
        if (gtnhtweaks$quantity.active(window)) ci.cancel();
    }

    @Inject(method = "mouseDragged(IIIJ)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$drag(int x, int y, int button, long time, CallbackInfo ci) {
        if (gtnhtweaks$quantity.active(window)) ci.cancel();
    }

    @Inject(method = "mouseUp(III)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$up(int x, int y, int button, CallbackInfo ci) {
        if (gtnhtweaks$quantity.active(window)) ci.cancel();
    }

    @Inject(method = "renderToolTips(II)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnhtweaks$tooltip(int x, int y, CallbackInfo ci) {
        if (gtnhtweaks$quantity.active(window)) ci.cancel();
    }

    // Draw before NEI restores the container-relative matrix; no access to protected GUI offsets is needed.
    @Inject(
        method = "renderObjects(II)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 1),
        require = 1)
    private void gtnhtweaks$draw(int x, int y, CallbackInfo ci) {
        gtnhtweaks$quantity.draw(window, x, y);
    }
}
