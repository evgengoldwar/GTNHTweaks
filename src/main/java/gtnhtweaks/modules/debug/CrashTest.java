package gtnhtweaks.modules.debug;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;

/** Crashes the game on purpose with a mixin error, to test the crash screen. Public for the FML event bus. */
public final class CrashTest {

    /** Ticks in a world before the crash, so the world is shown first. */
    private static final int DELAY_TICKS = 60;

    private int ticksInWorld;

    private CrashTest() {}

    /** STARTUP: crashes now, during mod loading. IN_GAME: crashes a few seconds after a world is entered. */
    static void start(String mode) {
        GTNHTweaks.LOG.warn("Crash test {} is on (crashTest/mixinCrash in debug.cfg)", mode);
        if ("STARTUP".equals(mode)) crash();
        else if ("IN_GAME".equals(mode)) FMLCommonHandler.instance()
            .bus()
            .register(new CrashTest());
    }

    /** Loads the class the broken mixin targets; if that works, the mixin was not applied: crashes anyway. */
    private static void crash() {
        GTNHTweaks.LOG.warn("Crash test: crashing now");
        CrashTestTarget.load();
        GTNHTweaks.LOG.warn("Crash test: the test mixin was not applied, crashing with an exception instead");
        throw new IllegalStateException("GTNHTweaks crash test");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            ticksInWorld = 0;
            return;
        }
        if (++ticksInWorld == DELAY_TICKS) crash();
    }
}
