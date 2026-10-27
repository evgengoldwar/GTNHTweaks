package gtnhtweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import gtnhtweaks.client.settings.SettingsScreen;

/** Key that opens the GTNHTweaks settings in game, F7 by default, changeable in the controls. */
public class SettingsKey {

    private final KeyBinding key = new KeyBinding(
        "key.gtnhtweaks.settings",
        Keyboard.KEY_F7,
        "key.categories.gtnhtweaks");

    public static void register() {
        SettingsKey handler = new SettingsKey();
        ClientRegistry.registerKeyBinding(handler.key);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    /** Posted only while no screen is open. */
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!key.isPressed()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) mc.displayGuiScreen(new SettingsScreen(null));
    }
}
