package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ScreenBackground;

/** The background of the screens opened in a world: dimmed with a darkness of its own, or blurred. */
@Mixin(GuiScreen.class)
public class MixinGuiScreen_Background {

    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$drawBackground(int tint, CallbackInfo ci) {
        if (Minecraft.getMinecraft().theWorld == null) return;
        GuiScreen self = (GuiScreen) (Object) this;
        if (ScreenBackground.draw(self.width, self.height)) ci.cancel();
    }
}
