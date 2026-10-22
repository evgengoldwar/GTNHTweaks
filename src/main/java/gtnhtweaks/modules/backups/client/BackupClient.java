package gtnhtweaks.modules.backups.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.modules.backups.BackupMessages;
import gtnhtweaks.modules.backups.BackupPacket;
import gtnhtweaks.modules.backups.BackupsModule;

/**
 * Client side state of the backup shown by {@link BackupToast}. Fed by the packets of the server and, as a fallback,
 * by the backup messages in the chat. Everything except {@link #receive} runs on the client thread.
 */
public class BackupClient {

    enum Phase {
        HIDDEN,
        RUNNING,
        FINISHED,
        FAILED
    }

    static final long SLIDE_MS = 350;

    private static final ConcurrentLinkedQueue<BackupPacket> INBOX = new ConcurrentLinkedQueue<>();
    private static volatile boolean disconnected;
    private static volatile String serverKey = "singleplayer";

    static Phase phase = Phase.HIDDEN;
    static long startedAt;
    static long durationMs;
    static long shownAt;
    /** When the toast starts sliding out, Long.MAX_VALUE while it stays. */
    static long hideAt = Long.MAX_VALUE;
    /** Real progress from 0 to 1, -1 when unknown. */
    static float knownProgress = -1;
    private static boolean fromServer;
    private static long lastActivity;

    private static Properties stats;
    private static File statsFile;

    private final BackupToast toast = new BackupToast();

    public static void register() {
        BackupClient client = new BackupClient();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance()
            .bus()
            .register(client);
    }

    /** Called on the network thread. */
    public static void receive(BackupPacket packet) {
        INBOX.add(packet);
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        String key = "singleplayer";
        if (!event.isLocal && event.manager != null && event.manager.getSocketAddress() != null) {
            key = event.manager.getSocketAddress()
                .toString();
        }
        serverKey = key;
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        disconnected = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (disconnected) {
            disconnected = false;
            INBOX.clear();
            phase = Phase.HIDDEN;
        }
        BackupPacket packet;
        while ((packet = INBOX.poll()) != null) apply(packet, now);

        // The server times out its own backups, only chat backups can get lost here
        if (phase == Phase.RUNNING && !fromServer
            && now - lastActivity > BackupsModule.timeoutMinutes * 60_000L) hide(now);
        if (phase != Phase.HIDDEN && now > hideAt + SLIDE_MS) phase = Phase.HIDDEN;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.HIDDEN) return;
        try {
            toast.render(System.currentTimeMillis());
        } catch (RuntimeException e) {
            GTNHTweaks.LOG.error("Failed to draw the backup toast", e);
            phase = Phase.HIDDEN;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!BackupsModule.detectFromChat || event.message == null) return;
        if (isPlayerChat(event.message)) return;
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        BackupMessages.Kind kind = keyKind(event.message);
        if (kind == BackupMessages.Kind.NONE) kind = BackupMessages.classify(text, false, phase == Phase.RUNNING);
        if (kind == BackupMessages.Kind.NONE) return;

        long now = System.currentTimeMillis();
        switch (kind) {
            case STARTED:
                if (phase != Phase.RUNNING) begin(now);
                break;
            case PROGRESS:
                if (phase != Phase.RUNNING) begin(now);
                float value = BackupMessages.progress(text);
                if (value >= 0) knownProgress = value;
                break;
            case FINISHED:
                if (phase == Phase.RUNNING) finish(Phase.FINISHED, now, now - startedAt);
                break;
            case FAILED:
                if (phase == Phase.RUNNING) finish(Phase.FAILED, now, now - startedAt);
                break;
            default:
                break;
        }
        if (phase == Phase.RUNNING) lastActivity = now;
        if (BackupsModule.hideChatMessages && kind != BackupMessages.Kind.OTHER) event.setCanceled(true);
    }

    private static void apply(BackupPacket packet, long now) {
        switch (packet.state) {
            case BackupPacket.RUNNING:
                if (phase != Phase.RUNNING) begin(now);
                startedAt = now - packet.elapsedMs;
                if (packet.progress >= 0) knownProgress = packet.progress;
                fromServer = true;
                lastActivity = now;
                break;
            case BackupPacket.FINISHED:
            case BackupPacket.FAILED:
                if (phase != Phase.RUNNING) {
                    // Already ended by the chat, or the start was missed
                    if (phase != Phase.HIDDEN) break;
                    begin(now);
                }
                finish(
                    packet.state == BackupPacket.FINISHED ? Phase.FINISHED : Phase.FAILED,
                    now,
                    packet.elapsedMs);
                break;
            case BackupPacket.CANCELLED:
                if (phase == Phase.RUNNING) hide(now);
                break;
            default:
                break;
        }
    }

    private static void begin(long now) {
        if (phase == Phase.HIDDEN || now > hideAt) shownAt = now;
        phase = Phase.RUNNING;
        startedAt = now;
        knownProgress = -1;
        fromServer = false;
        lastActivity = now;
        hideAt = Long.MAX_VALUE;
    }

    private static void finish(Phase result, long now, long duration) {
        phase = result;
        durationMs = Math.max(0, duration);
        hideAt = now + BackupsModule.finishedToastSeconds * 1000L;
        if (result == Phase.FINISHED) {
            knownProgress = 1;
            if (durationMs >= 1000) savePreviousDuration(durationMs);
        }
        if (BackupsModule.playSound) {
            Minecraft.getMinecraft()
                .getSoundHandler()
                .playSound(
                    PositionedSoundRecord.func_147674_a(
                        new ResourceLocation(result == Phase.FINISHED ? "random.orb" : "note.bass"),
                        result == Phase.FINISHED ? 1.2F : 0.6F));
        }
    }

    private static void hide(long now) {
        hideAt = Math.min(hideAt, now);
    }

    /** Progress shown by the bar: the real one, else estimated from the previous backup, -1 if neither is known. */
    static float progress(long now) {
        if (phase == Phase.FINISHED) return 1;
        if (knownProgress >= 0) return knownProgress;
        long previous = previousDuration();
        if (previous <= 0) return -1;
        return Math.min(0.97F, (now - startedAt) / (float) previous);
    }

    /** Estimated time left in ms, -1 if unknown. */
    static long remaining(long now) {
        long elapsed = now - startedAt;
        if (knownProgress >= 0.02F) return (long) (elapsed * (1 - knownProgress) / knownProgress);
        if (knownProgress >= 0) return -1;
        long previous = previousDuration();
        return previous > elapsed ? previous - elapsed : -1;
    }

    private static boolean isPlayerChat(IChatComponent component) {
        if (component instanceof ChatComponentTranslation) {
            String key = ((ChatComponentTranslation) component).getKey();
            if (key.startsWith("chat.type.") || key.startsWith("commands.message.display")) return true;
        }
        return false;
    }

    /** Kind given by a ServerUtilities translation key anywhere in the message. */
    private static BackupMessages.Kind keyKind(Object component) {
        if (!(component instanceof IChatComponent)) return BackupMessages.Kind.NONE;
        if (component instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation = (ChatComponentTranslation) component;
            BackupMessages.Kind kind = BackupMessages.classifyKey(translation.getKey());
            if (kind != BackupMessages.Kind.NONE) return kind;
            for (Object arg : translation.getFormatArgs()) {
                kind = keyKind(arg);
                if (kind != BackupMessages.Kind.NONE) return kind;
            }
        }
        for (Object sibling : ((IChatComponent) component).getSiblings()) {
            BackupMessages.Kind kind = keyKind(sibling);
            if (kind != BackupMessages.Kind.NONE) return kind;
        }
        return BackupMessages.Kind.NONE;
    }

    private static long previousDuration() {
        try {
            return Long.parseLong(loadStats().getProperty(serverKey, "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void savePreviousDuration(long ms) {
        Properties properties = loadStats();
        properties.setProperty(serverKey, Long.toString(ms));
        try (OutputStream out = new FileOutputStream(statsFile)) {
            properties.store(out, "Duration of the last backup in ms per server, for the backup toast");
        } catch (IOException e) {
            GTNHTweaks.LOG.warn("Could not write " + statsFile, e);
        }
    }

    private static Properties loadStats() {
        if (stats != null) return stats;
        stats = new Properties();
        statsFile = new File(ModuleManager.getConfigDir(), "backups-stats.properties");
        if (statsFile.isFile()) {
            try (InputStream in = new FileInputStream(statsFile)) {
                stats.load(in);
            } catch (IOException e) {
                GTNHTweaks.LOG.warn("Could not read " + statsFile, e);
            }
        }
        return stats;
    }
}
