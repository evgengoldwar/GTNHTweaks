package gtnhtweaks.modules.logs.client;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.logs.LogsModule;

/**
 * Swaps the crash screens of BetterCrashes for {@link CrashScreen}. BetterCrashes still catches the crash, saves the
 * report and recovers the game; only the screen it opens is replaced.
 */
public final class CrashScreens {

    private static final String PROBLEM_SCREEN = "vfyjxf.bettercrashes.utils.GuiProblemScreen";
    private static final String INIT_ERROR_SCREEN = "vfyjxf.bettercrashes.utils.GuiInitErrorScreen";

    private static boolean failed;

    private CrashScreens() {}

    /** Called with every screen about to be opened: a BetterCrashes crash screen becomes the GTNHTweaks one. */
    public static GuiScreen replace(GuiScreen screen) {
        if (screen == null || failed || !LogsModule.replaceCrashScreen) return screen;
        try {
            Class<?> problemScreen = null;
            for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
                if (PROBLEM_SCREEN.equals(c.getName())) {
                    problemScreen = c;
                    break;
                }
            }
            if (problemScreen == null) return screen;
            Field field = problemScreen.getDeclaredField("report");
            field.setAccessible(true);
            CrashReport report = (CrashReport) field.get(screen);
            if (report == null) return screen;
            boolean initError = INIT_ERROR_SCREEN.equals(
                screen.getClass()
                    .getName());
            return new CrashScreen(report, initError);
        } catch (Throwable t) {
            // an unknown BetterCrashes version: keep its screen from now on
            failed = true;
            GTNHTweaks.LOG.error("Could not replace the BetterCrashes crash screen", t);
            return screen;
        }
    }
}
