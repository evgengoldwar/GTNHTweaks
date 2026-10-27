package gtnhtweaks.mixins.early.mainmenu;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.mainmenu.MainMenuRenderer;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu_MainMenu extends GuiScreen {

    @Shadow
    private String splashText;

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drawMainMenu(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (MainMenuRenderer.draw(this, this.fontRendererObj, this.buttonList, mouseX, mouseY, splashText)) {
            ci.cancel();
        }
    }
}
