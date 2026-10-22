package gtnhtweaks.mixins.early.logs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import gtnhtweaks.modules.logs.client.CrashScreens;

/** Opens the GTNHTweaks crash screen where BetterCrashes opens its own. */
@Mixin(Minecraft.class)
public class MixinMinecraft_CrashScreen {

    @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true)
    private GuiScreen gtnhtweaks$replaceCrashScreen(GuiScreen screen) {
        return CrashScreens.replace(screen);
    }
}
