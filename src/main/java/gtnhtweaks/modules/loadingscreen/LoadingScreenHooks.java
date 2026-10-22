package gtnhtweaks.modules.loadingscreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.minecraft.launchwrapper.Launch;

import org.lwjgl.opengl.Display;

import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.loadingscreen.render.LoadingScreenRenderer;
import gtnhtweaks.modules.loadingscreen.render.ProgressLine;

/** Entry points called from the loading screen mixins. */
public final class LoadingScreenHooks {

    /** Set after a rendering error, so a broken screen falls back to the original one instead of spamming the log. */
    private static volatile boolean broken;

    private LoadingScreenHooks() {}

    /**
     * Replaces {@code Minecraft.loadScreen}, the Mojang logo screen drawn on the main thread before mods load.
     *
     * @return true if the GTNH screen was drawn and the vanilla one must be skipped
     */
    public static boolean onVanillaLoadScreen() {
        if (!LoadingScreenModule.replaceVanillaScreen || broken) return false;
        // BetterLoadingScreen renders the loading screen from its own thread, which owns the GL context by now; it
        // also empties loadScreen itself. Our screen is drawn over its frames instead.
        if (isBetterLoadingScreenInstalled() || !isContextCurrent()) return false;
        if (!render(Collections.<ProgressLine>emptyList())) return false;
        try {
            Display.update();
        } catch (Throwable t) {
            broken = true;
            GTNHTweaks.LOG.error("Could not present the GTNH loading screen, falling back to the vanilla one", t);
            return false;
        }
        return true;
    }

    private static boolean isBetterLoadingScreenInstalled() {
        try {
            Class.forName("alexiil.mods.load.coremod.LoadingScreenLoadPlugin", false, Launch.classLoader);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** False if another thread (a splash screen) owns the window's GL context, so this thread must not draw. */
    private static boolean isContextCurrent() {
        try {
            return Display.getDrawable()
                .isCurrent();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called by the Forge splash thread right before it presents a frame. */
    public static void onForgeSplashFrame() {
        if (!LoadingScreenModule.replaceForgeSplash || broken) return;
        // Same bars as the Forge splash shows: the first one and the two innermost ones
        ProgressBar first = null, penult = null, last = null;
        Iterator<ProgressBar> bars = ProgressManager.barIterator();
        while (bars.hasNext()) {
            ProgressBar bar = bars.next();
            if (first == null) first = bar;
            else {
                penult = last;
                last = bar;
            }
        }
        List<ProgressLine> lines = new ArrayList<>(3);
        for (ProgressBar bar : new ProgressBar[] { first, penult, last }) {
            if (bar != null) {
                lines.add(ProgressLine.ofSteps(bar.getTitle() + " - " + bar.getMessage(), bar.getStep(), bar.getSteps()));
            }
        }
        render(lines);
    }

    /** Called after BetterLoadingScreen has drawn a frame, before it is presented. */
    public static void onBetterLoadingScreenFrame(String text, float percent, String subText, float subPercent) {
        if (!LoadingScreenModule.drawOverBetterLoadingScreen || broken) return;
        List<ProgressLine> lines = new ArrayList<>(2);
        if (text != null) lines.add(ProgressLine.ofPercent(text, percent));
        if (subText != null && !subText.isEmpty()) lines.add(ProgressLine.ofPercent(subText, subPercent));
        else {
            // BetterLoadingScreen often gives no sub-progress; the mods of the current phase come from Forge then
            ProgressLine mods = innermostForgeBar();
            if (mods != null) lines.add(mods);
        }
        render(lines);
    }

    /** The innermost Forge progress bar, such as "PreInitialization - The Twilight Forest 42/180", or null. */
    private static ProgressLine innermostForgeBar() {
        try {
            ProgressBar inner = null;
            Iterator<ProgressBar> bars = ProgressManager.barIterator();
            boolean first = true;
            while (bars.hasNext()) {
                ProgressBar bar = bars.next();
                // The first bar is the overall "Loading" one, which the main line already covers
                if (!first && bar.getSteps() > 1) inner = bar;
                first = false;
            }
            if (inner == null) return null;
            String message = inner.getMessage();
            String text = message == null || message.isEmpty() ? inner.getTitle() : inner.getTitle() + " - " + message;
            return ProgressLine.ofSteps(text, inner.getStep(), inner.getSteps());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Called at the end of {@code Minecraft.startGame}, when every loading screen is gone. */
    public static void onGameStarted() {
        LoadingTimer.finish();
        try {
            LoadingScreenRenderer.dispose();
        } catch (Throwable t) {
            GTNHTweaks.LOG.warn("Could not free the loading screen textures", t);
        }
    }

    private static boolean render(List<ProgressLine> lines) {
        try {
            LoadingScreenRenderer.render(lines);
            return true;
        } catch (Throwable t) {
            broken = true;
            GTNHTweaks.LOG.error("GTNH loading screen failed, falling back to the original loading screen", t);
            return false;
        }
    }
}
