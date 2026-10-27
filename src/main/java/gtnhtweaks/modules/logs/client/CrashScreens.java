package gtnhtweaks.modules.logs.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.logs.LogsModule;

/**
 * Swaps other crash and error screens for {@link CrashScreen}: the crash screens of BetterCrashes (BetterCrashes
 * still catches the crash, saves the report and recovers the game) and the screens Forge shows for missing,
 * duplicate or wrong mods.
 */
public final class CrashScreens {

    private static final String PROBLEM_SCREEN = "vfyjxf.bettercrashes.utils.GuiProblemScreen";
    private static final String INIT_ERROR_SCREEN = "vfyjxf.bettercrashes.utils.GuiInitErrorScreen";
    /** Forge screens of mod loading problems; the exact classes, not the server mod list check that extends one. */
    private static final Set<String> FORGE_SCREENS = new HashSet<>(
        Arrays.asList(
            "cpw.mods.fml.client.GuiModsMissing",
            "cpw.mods.fml.client.GuiWrongMinecraft",
            "cpw.mods.fml.client.GuiDupesFound",
            "cpw.mods.fml.client.GuiSortingProblem"));

    private static boolean failed;

    private CrashScreens() {}

    /** Called with every screen about to be opened: a crash or error screen becomes the GTNHTweaks one. */
    public static GuiScreen replace(GuiScreen screen) {
        if (screen == null || failed || !LogsModule.replaceCrashScreen) return screen;
        try {
            String name = screen.getClass()
                .getName();
            if (FORGE_SCREENS.contains(name)) return new CrashScreen(CrashInfo.fromModProblem(problemOf(screen), name));

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
            CrashInfo.Kind kind = INIT_ERROR_SCREEN.equals(name) ? CrashInfo.Kind.STARTUP : CrashInfo.Kind.IN_GAME;
            return new CrashScreen(CrashInfo.fromReport(report, kind));
        } catch (Throwable t) {
            // an unknown version of the other screen: keep it from now on
            failed = true;
            GTNHTweaks.LOG.error("Could not replace the crash screen " + screen.getClass(), t);
            return screen;
        }
    }

    /** The exception, or else the first other value, a Forge error screen was made with. */
    private static Object problemOf(GuiScreen screen) throws IllegalAccessException {
        Object fallback = null;
        for (Field field : screen.getClass()
            .getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof Throwable) return value;
            if (fallback == null && value != null) fallback = value;
        }
        return fallback;
    }
}
