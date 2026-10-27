package gtnhtweaks.mixins.early.logs;

import java.util.Queue;
import java.util.concurrent.FutureTask;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MinecraftError;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.client.SplashProgress;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.modules.logs.EarlyCrashWindow;
import gtnhtweaks.modules.logs.client.CrashInfo;
import gtnhtweaks.modules.logs.client.CrashScreen;

/**
 * Keeps the game open after a crash and shows the GTNHTweaks crash screen, without BetterCrashes (applied only when
 * it is not installed):
 * <ul>
 * <li>a crash while starting, mod loading included: the screen is shown before the game closes;</li>
 * <li>a crash in game: the world is closed and the game goes on with the screen;</li>
 * <li>a crash of the integrated server: the same, instead of closing the game.</li>
 * </ul>
 * The way the game is recovered and the screen is shown during startup follow VanillaFix by Runemoro (MIT license),
 * as BetterCrashes does.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_CrashHandler {

    @Shadow
    volatile boolean running;
    @Shadow
    public static byte[] memoryReserve;
    @Shadow
    @Final
    private Queue<FutureTask<?>> field_152351_aB;
    @Shadow
    public EntityRenderer entityRenderer;
    @Shadow
    public GameSettings gameSettings;
    @Shadow
    public GuiIngame ingameGUI;
    @Shadow
    private IReloadableResourceManager mcResourceManager;
    @Shadow
    public FontRenderer fontRenderer;
    @Shadow
    public TextureManager renderEngine;
    @Shadow
    private LanguageManager mcLanguageManager;
    @Shadow
    private SoundHandler mcSoundHandler;
    @Shadow
    @Final
    private IMetadataSerializer metadataSerializer_;
    @Shadow
    private Framebuffer framebufferMc;
    @Shadow
    public GuiScreen currentScreen;
    @Shadow
    private int leftClickCounter;
    @Shadow
    public int displayWidth;
    @Shadow
    public int displayHeight;

    @Shadow
    public abstract void displayGuiScreen(GuiScreen screen);

    @Shadow
    public abstract CrashReport addGraphicsAndWorldToCrashReport(CrashReport report);

    @Shadow
    private void runGameLoop() {}

    @Shadow
    public abstract NetHandlerPlayClient getNetHandler();

    @Shadow
    public abstract void loadWorld(WorldClient world);

    @Shadow
    public abstract void refreshResources();

    @Shadow
    public abstract void func_147120_f();

    /** Set by the integrated server thread, handled on the client thread. */
    @Unique
    private static volatile CrashReport gtnhtweaks$serverCrash;
    @Unique
    private boolean gtnhtweaks$started;
    @Unique
    private boolean gtnhtweaks$showingStartupCrash;
    @Unique
    private long gtnhtweaks$lastCrash;
    @Unique
    private int gtnhtweaks$crashesInARow;

    @Inject(method = "startGame", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$startedGame(CallbackInfo ci) {
        gtnhtweaks$started = true;
    }

    // ---- in game

    /** Runs one frame; a crash in it closes the world and opens the crash screen instead of closing the game. */
    @Redirect(
        method = "run",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runGameLoop()V"),
        require = 0)
    private void gtnhtweaks$runGameLoop(Minecraft self) {
        CrashReport serverCrash = gtnhtweaks$serverCrash;
        if (serverCrash != null) {
            gtnhtweaks$serverCrash = null;
            gtnhtweaks$recover(serverCrash, CrashInfo.Kind.SERVER);
            return;
        }
        try {
            runGameLoop();
        } catch (MinecraftError | OutOfMemoryError e) {
            // closing the game, and the vanilla out of memory screen
            throw e;
        } catch (ReportedException e) {
            gtnhtweaks$recover(e.getCrashReport(), CrashInfo.Kind.IN_GAME);
        } catch (Throwable t) {
            gtnhtweaks$recover(new CrashReport("Unexpected error", t), CrashInfo.Kind.IN_GAME);
        }
    }

    /** The integrated server crashed: vanilla closes the game, the crash screen is shown instead. */
    @Inject(method = "crashed", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$serverCrashed(CrashReport report, CallbackInfo ci) {
        gtnhtweaks$serverCrash = report;
        ci.cancel();
    }

    @Unique
    private void gtnhtweaks$recover(CrashReport report, CrashInfo.Kind kind) {
        long now = System.currentTimeMillis();
        gtnhtweaks$crashesInARow = now - gtnhtweaks$lastCrash < 3000 ? gtnhtweaks$crashesInARow + 1 : 1;
        gtnhtweaks$lastCrash = now;
        boolean repeated = currentScreen instanceof CrashScreen || gtnhtweaks$crashesInARow > 3;

        try {
            addGraphicsAndWorldToCrashReport(report);
        } catch (Throwable ignored) {}
        CrashInfo.save(report);

        if (repeated) {
            // the game crashes again with the crash screen open, e.g. in every tick: the game cannot go on. The screen
            // is drawn in a loop of its own, without game ticks, until it is left; then the game closes as vanilla
            GTNHTweaks.LOG
                .fatal("The game keeps crashing, it will close after the crash screen", report.getCrashCause());
            try {
                gtnhtweaks$runScreen(new CrashScreen(CrashInfo.fromReport(report, CrashInfo.Kind.REPEATED)));
            } catch (Throwable t) {
                GTNHTweaks.LOG.error("Could not show the crash screen", t);
            }
            throw new ReportedException(report);
        }
        GTNHTweaks.LOG.fatal(
            "The game crashed, the crash screen is shown. Report: " + report.getFile()
                + "\n"
                + report.getCompleteReport());
        gtnhtweaks$resetGameState();
        try {
            gameSettings.showDebugInfo = false;
            ingameGUI.getChatGUI()
                .clearChatMessages();
        } catch (Throwable ignored) {}
        gtnhtweaks$showCrashScreen(new CrashScreen(CrashInfo.fromReport(report, kind)), report);
    }

    /**
     * Opens the crash screen. Opening it closes the current screen first, which can crash again when that screen is the
     * broken one: then it is dropped without closing, and as a last resort the crash screen gets a loop of its own.
     */
    @Unique
    private void gtnhtweaks$showCrashScreen(GuiScreen screen, CrashReport report) {
        try {
            displayGuiScreen(screen);
            return;
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("The open screen crashed while closing, it is dropped", t);
        }
        try {
            currentScreen = null;
            displayGuiScreen(screen);
            return;
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Could not open the crash screen", t);
        }
        try {
            currentScreen = null;
            gtnhtweaks$runScreen(screen);
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Could not show the crash screen", t);
        }
        throw new ReportedException(report);
    }

    /** Leaves the world and frees memory, as after a disconnect. */
    @Unique
    private void gtnhtweaks$resetGameState() {
        try {
            int reserve = -1;
            if (memoryReserve != null) {
                reserve = memoryReserve.length;
                memoryReserve = new byte[0];
            }
            if (getNetHandler() != null) {
                getNetHandler().getNetworkManager()
                    .closeChannel(new ChatComponentText("[GTNHTweaks] Client crashed"));
            }
            loadWorld(null);
            if (entityRenderer.isShaderActive()) entityRenderer.deactivateShader();
            field_152351_aB.clear();
            if (reserve != -1) memoryReserve = new byte[reserve];
            System.gc();
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Failed to reset the game after a crash", t);
        }
    }

    // ---- while starting

    /**
     * A crash before the game finished starting, from mod loading too: shows the crash screen until it is left, then
     * vanilla closes the game. The report is saved first, so vanilla does not save it again.
     */
    @Inject(method = "displayCrashReport", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$startupCrash(CrashReport report, CallbackInfo ci) {
        if (gtnhtweaks$started || gtnhtweaks$showingStartupCrash) return;
        gtnhtweaks$showingStartupCrash = true;
        CrashInfo.save(report);
        try {
            if (!Display.isCreated()) {
                // no game window to draw the screen in: a plain Java window instead
                EarlyCrashWindow.show(report.getCrashCause(), report.getCompleteReport(), report.getFile());
                return;
            }
            gtnhtweaks$prepareStartupScreen();
            gtnhtweaks$runScreen(new CrashScreen(CrashInfo.fromReport(report, CrashInfo.Kind.STARTUP)));
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Could not show the crash screen, closing the game", t);
        }
    }

    /** The game may have crashed before its resources and font were made: makes them again. */
    @Unique
    private void gtnhtweaks$prepareStartupScreen() {
        try {
            SplashProgress.finish();
        } catch (Throwable ignored) {}
        mcResourceManager = new SimpleReloadableResourceManager(metadataSerializer_);
        renderEngine = new TextureManager(mcResourceManager);
        mcResourceManager.registerReloadListener(renderEngine);
        mcLanguageManager = new LanguageManager(metadataSerializer_, gameSettings.language);
        mcResourceManager.registerReloadListener(mcLanguageManager);
        refreshResources();
        fontRenderer = new FontRenderer(
            gameSettings,
            new ResourceLocation("textures/font/ascii.png"),
            renderEngine,
            false);
        mcResourceManager.registerReloadListener(fontRenderer);
        mcSoundHandler = new SoundHandler(mcResourceManager, gameSettings);
        mcResourceManager.registerReloadListener(mcSoundHandler);
        running = true;
    }

    /** Draws the screen in a loop of its own, until it is left or the window is closed. */
    @Unique
    private void gtnhtweaks$runScreen(GuiScreen screen) {
        try {
            displayGuiScreen(screen);
        } catch (Throwable t) {
            // the open screen crashed while closing: dropped without closing
            currentScreen = null;
            displayGuiScreen(screen);
        }
        while (running && currentScreen == screen) {
            if (Display.isCloseRequested()) break;
            leftClickCounter = 10000;
            currentScreen.handleInput();
            if (currentScreen != screen) break;
            currentScreen.updateScreen();

            GL11.glPushMatrix();
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            if (framebufferMc != null) framebufferMc.bindFramebuffer(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glViewport(0, 0, displayWidth, displayHeight);
            ScaledResolution resolution = new ScaledResolution((Minecraft) (Object) this, displayWidth, displayHeight);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0, 1000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glTranslatef(0, 0, -2000);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            int width = resolution.getScaledWidth();
            int height = resolution.getScaledHeight();
            int mouseX = Mouse.getX() * width / displayWidth;
            int mouseY = height - Mouse.getY() * height / displayHeight - 1;
            currentScreen.drawScreen(mouseX, mouseY, 0);

            if (framebufferMc != null) framebufferMc.unbindFramebuffer();
            GL11.glPopMatrix();
            if (framebufferMc != null) {
                GL11.glPushMatrix();
                framebufferMc.framebufferRender(displayWidth, displayHeight);
                GL11.glPopMatrix();
            }
            func_147120_f();
            Thread.yield();
            Display.sync(60);
        }
    }
}
