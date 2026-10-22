package gtnhtweaks.modules.debug;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Crashes the game on purpose with a mixin error, to test the crash screen. Public for the FML event bus. */
public final class CrashTest {

    /** Ticks in a world before the crash, so the world is shown first. */
    private static final int DELAY_TICKS = 60;

    private int ticksInWorld;

    private CrashTest() {}

    /** STARTUP: crashes now, during mod loading. IN_GAME: crashes a few seconds after a world is entered. */
    static void start(String mode) {
        if ("STARTUP".equals(mode)) CrashTestTarget.load();
        else if ("IN_GAME".equals(mode)) FMLCommonHandler.instance()
            .bus()
            .register(new CrashTest());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            ticksInWorld = 0;
            return;
        }
        if (++ticksInWorld == DELAY_TICKS) CrashTestTarget.load();
    }
}
