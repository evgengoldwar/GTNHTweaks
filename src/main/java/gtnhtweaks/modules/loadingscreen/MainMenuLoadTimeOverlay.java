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
        String text = LoadingScreenModule.textLoadedIn + " " + LoadingTimer.formatHuman(LoadingTimer.getElapsedMs());
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, 2, 2, 0xFFFFFF);
    }
}
