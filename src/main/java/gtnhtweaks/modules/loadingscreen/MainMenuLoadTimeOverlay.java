package gtnhtweaks.modules.loadingscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.modules.mainmenu.MainMenuModule;

/** Shows the total loading time in the vanilla main menu. The GTNH main menu module draws it itself. */
public class MainMenuLoadTimeOverlay {

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMainMenu) || !LoadingTimer.isFinished()) return;
        if (MainMenuModule.INSTANCE.isEnabled()) return;
        String text = LoadingScreenModule.textLoadedIn + " " + LoadingTimer.format(LoadingTimer.getElapsedMs());
        long previous = LoadingTimer.getPreviousMs();
        if (previous > 0) {
            text += " (" + LoadingScreenModule.textPreviousLaunch + ": " + LoadingTimer.format(previous) + ")";
        }
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, 2, 2, 0xFFFFFF);
    }
}
