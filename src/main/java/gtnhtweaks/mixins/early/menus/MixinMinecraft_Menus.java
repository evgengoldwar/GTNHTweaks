package gtnhtweaks.mixins.early.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.menus.MenusModule;

/**
 * Vanilla caps the frame rate at 30 while a screen is open without a world (main menu, settings, mod list...).
 * The frame rate limit of the video settings applies there too instead.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_Menus {

    @Shadow
    public WorldClient theWorld;
    @Shadow
    public GuiScreen currentScreen;
    @Shadow
    public GameSettings gameSettings;

    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$menuFramerate(CallbackInfoReturnable<Integer> cir) {
        if (MenusModule.fullFramerate && theWorld == null && currentScreen != null) {
            cir.setReturnValue(gameSettings.limitFramerate);
        }
    }
}
