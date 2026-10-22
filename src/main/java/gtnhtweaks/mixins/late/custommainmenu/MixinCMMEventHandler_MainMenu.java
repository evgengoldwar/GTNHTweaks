package gtnhtweaks.mixins.late.custommainmenu;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.mainmenu.MainMenuRenderer;

/**
 * Custom Main Menu swaps every opened GuiMainMenu for its own screen, so the GTNH main menu would never be drawn. Keeps
 * the vanilla main menu open instead; the other Custom Main Menu screens are left alone.
 */
@Pseudo
@Mixin(targets = "lumien.custommainmenu.handler.CMMEventHandler", remap = false)
public class MixinCMMEventHandler_MainMenu {

    @Inject(method = "openGui", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$keepMainMenu(GuiOpenEvent event, CallbackInfo ci) {
        if (event.gui instanceof GuiMainMenu && MainMenuRenderer.isActive()) ci.cancel();
    }
}
