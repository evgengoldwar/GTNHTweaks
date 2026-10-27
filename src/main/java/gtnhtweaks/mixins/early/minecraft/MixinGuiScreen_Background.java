package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ScreenAnimation;
import gtnhtweaks.modules.minecraft.client.ScreenBackground;

/**
 * The background of the screens opened in a world: dimmed with a darkness of its own, or blurred; and kept still while
 * the screen opens with its animation.
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreen_Background {

    /** While the screen opens with its animation, the background is drawn still, over the whole screen. */
    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$stillWorldBackground(int tint, CallbackInfo ci) {
        GuiScreen self = (GuiScreen) (Object) this;
        if (ScreenAnimation.drawUnscaled(self, () -> self.drawWorldBackground(tint))) ci.cancel();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$stillBackground(int tint, CallbackInfo ci) {
        GuiScreen self = (GuiScreen) (Object) this;
        if (ScreenAnimation.drawUnscaled(self, () -> self.drawBackground(tint))) ci.cancel();
    }

    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$drawBackground(int tint, CallbackInfo ci) {
        if (Minecraft.getMinecraft().theWorld == null) return;
        GuiScreen self = (GuiScreen) (Object) this;
        if (ScreenBackground.draw(self.width, self.height)) ci.cancel();
    }
}
