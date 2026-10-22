package gtnhtweaks.modules.backups;

import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;

/**
 * Server side: follows the backups of ServerUtilities through its log and sends their state to the players. Log lines
 * arrive on the backup thread, the packets go out on the server thread.
 */
public class ServerBackupWatcher {

    private static final byte IDLE = -1;
    /** Lines of players talking in the chat, like {@code <Steve> backup started?}. */
    private static final Pattern PLAYER_CHAT = Pattern.compile("^(<|\\* |\\[[^\\]|]+\\] ).*");

    private static final Object LOCK = new Object();
    private static byte state = IDLE;
    private static float progress = -1;
    private static long startedAt;
    private static long lastActivity;
    private static boolean dirty;
    private static boolean installed;

    private int ticks;

    static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();
            Capture appender = new Capture();
            appender.start();
            LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            root.addAppender(appender, Level.ALL, null);
            for (LoggerConfig logger : config.getLoggers()
                .values()) {
                if (logger != root && !logger.isAdditive()) logger.addAppender(appender, Level.ALL, null);
            }
            context.updateLoggers();
        } catch (Throwable t) {
            GTNHTweaks.LOG.error("Could not watch the log for backups", t);
        }
    }

    static void onLine(String logger, String text) {
        boolean fromMod = BackupMessages.isBackupLogger(logger);
        if (!fromMod && PLAYER_CHAT.matcher(text)
            .matches()) return;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            boolean running = state == BackupPacket.RUNNING;
            BackupMessages.Kind kind = BackupMessages.classify(text, fromMod, false);
            switch (kind) {
                case STARTED:
                    if (!running) start(now);
                    break;
                case PROGRESS:
                    if (!running) start(now);
                    float value = BackupMessages.progress(text);
                    if (value >= 0 && Math.abs(value - progress) >= 0.001F) {
                        progress = value;
                        dirty = true;
                    }
                    break;
                case FINISHED:
                    if (running) {
                        state = BackupPacket.FINISHED;
                        progress = 1;
                        dirty = true;
                    }
                    break;
                case FAILED:
                    if (running) {
                        state = BackupPacket.FAILED;
                        dirty = true;
                    }
                    break;
                default:
                    break;
            }
            if (kind != BackupMessages.Kind.NONE) lastActivity = now;
        }
    }

    private static void start(long now) {
        state = BackupPacket.RUNNING;
        progress = -1;
        startedAt = now;
        dirty = true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 5 != 0) return;
        BackupPacket packet = null;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (state == BackupPacket.RUNNING && now - lastActivity > BackupsModule.timeoutMinutes * 60_000L) {
                packet = new BackupPacket(BackupPacket.CANCELLED, -1, now - startedAt);
                state = IDLE;
                dirty = false;
            } else if (dirty && state != IDLE) {
                packet = new BackupPacket(state, progress, now - startedAt);
                if (state != BackupPacket.RUNNING) state = IDLE;
                dirty = false;
            }
        }
        if (packet != null && BackupsModule.network != null) BackupsModule.network.sendToAll(packet);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || BackupsModule.network == null) return;
        BackupPacket packet;
        synchronized (LOCK) {
            if (state != BackupPacket.RUNNING) return;
            packet = new BackupPacket(state, progress, System.currentTimeMillis() - startedAt);
        }
        BackupsModule.network.sendTo(packet, (EntityPlayerMP) event.player);
    }

    private static final class Capture extends AbstractAppender {

        Capture() {
            super("GTNHTweaksBackups", null, null, true);
        }

        @Override
        public void append(LogEvent event) {
            try {
                if (event.getMessage() == null) return;
                onLine(
                    event.getLoggerName(),
                    event.getMessage()
                        .getFormattedMessage());
            } catch (Throwable ignored) {
                // Never break the logging of the game
            }
        }
    }
}
