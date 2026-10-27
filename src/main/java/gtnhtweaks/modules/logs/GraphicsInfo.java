package gtnhtweaks.modules.logs;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;

import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Reads the graphics card and driver when the main menu opens for the first time: then the OpenGL context surely
 * belongs to the client thread, while during loading a splash screen thread may hold it.
 */
public final class GraphicsInfo {

    private boolean done;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (done || !(event.gui instanceof GuiMainMenu)) return;
        done = true;
        try {
            String text = GL11.glGetString(GL11.GL_RENDERER) + " | "
                + GL11.glGetString(GL11.GL_VENDOR)
                + " | OpenGL "
                + GL11.glGetString(GL11.GL_VERSION);
            try {
                text += " | LWJGL " + Sys.getVersion();
            } catch (Throwable ignored) {}
            ModsReport.setGraphics(text);
            ModsReport.write();
        } catch (Throwable ignored) {
            // The mod list is already written, only the graphics line is missing
        }
    }
}
