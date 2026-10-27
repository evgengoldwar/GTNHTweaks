package gtnhtweaks.modules.backups;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.backups.client.BackupClient;

/**
 * Shows a toast in the top right corner while ServerUtilities makes a world backup: elapsed time, progress bar and
 * the result.
 * <p>
 * The server side watches the ServerUtilities log and sends the progress to the players. Without GTNHTweaks on the
 * server the client falls back to the backup messages in the chat and estimates the progress from the previous backup.
 */
public final class BackupsModule extends TweakModule {

    public static final BackupsModule INSTANCE = new BackupsModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_DETECTION = "detection";
    private static final String CATEGORY_TEXT = "text";

    public static GTNHTheme style = GTNHTheme.SHADOW;
    public static boolean playSound = true;
    public static boolean hideChatMessages = true;
    public static int finishedToastSeconds = 6;
    public static int timeoutMinutes = 30;

    public static boolean detectFromServerLog = true;
    public static boolean detectFromChat = true;

    public static String textTitle = "Backing up the world";
    public static String textTitleDone = "Backup complete";
    public static String textTitleFailed = "Backup failed";
    public static String textElapsed = "Time";
    public static String textTook = "Took";
    public static String textLeft = "left";

    public static SimpleNetworkWrapper network;

    private BackupsModule() {
        super("backups", "Toast with the progress of ServerUtilities backups in the top right corner", false);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Look and behavior of the backup toast");
        style = readTheme(config, CATEGORY_GENERAL, "the toast");
        playSound = config.getBoolean("playSound", CATEGORY_GENERAL, playSound, "Play a sound when a backup ends");
        hideChatMessages = config.getBoolean(
            "hideChatMessages",
            CATEGORY_GENERAL,
            hideChatMessages,
            "Hide the backup messages of ServerUtilities in the chat, the toast shows them instead");
        finishedToastSeconds = config.getInt(
            "finishedToastSeconds",
            CATEGORY_GENERAL,
            finishedToastSeconds,
            1,
            60,
            "How long the toast stays after the backup ended, in seconds");
        timeoutMinutes = config.getInt(
            "timeoutMinutes",
            CATEGORY_GENERAL,
            timeoutMinutes,
            1,
            1440,
            "Hide the toast when nothing was heard about the running backup for this many minutes");

        config.addCustomCategoryComment(
            CATEGORY_DETECTION,
            "How a backup is noticed. Regular expressions are matched against whole log lines and chat messages "
                + "without color codes");
        detectFromServerLog = config.getBoolean(
            "detectFromServerLog",
            CATEGORY_DETECTION,
            detectFromServerLog,
            "Server side (and singleplayer): watch the log of ServerUtilities and send the real progress to players");
        detectFromChat = config.getBoolean(
            "detectFromChat",
            CATEGORY_DETECTION,
            detectFromChat,
            "Client side: notice backups by the messages in the chat. Used on servers without GTNHTweaks, the "
                + "progress is then estimated from the duration of the previous backup");
        BackupMessages.loggers = BackupMessages.compile(
            config.getString(
                "loggers",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_LOGGERS,
                "Loggers whose lines with a percentage are taken as backup progress"),
            BackupMessages.DEFAULT_LOGGERS);
        BackupMessages.keywords = BackupMessages.compile(
            config.getString(
                "keywords",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_KEYWORDS,
                "A message is about a backup only when it matches this"),
            BackupMessages.DEFAULT_KEYWORDS);
        BackupMessages.ignored = BackupMessages.compile(
            config.getString(
                "ignored",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_IGNORED,
                "Backup messages that are not a start, like \"Next backup in 30 minutes\""),
            BackupMessages.DEFAULT_IGNORED);
        BackupMessages.failed = BackupMessages.compile(
            config.getString(
                "failed",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_FAILED,
                "A backup message matching this means the backup failed"),
            BackupMessages.DEFAULT_FAILED);
        BackupMessages.finished = BackupMessages.compile(
            config.getString(
                "finished",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_FINISHED,
                "A backup message matching this means the backup is done. While a backup runs, a chat message "
                    + "matching this ends it even without the keywords, like \"Done! (00:12)\""),
            BackupMessages.DEFAULT_FINISHED);
        BackupMessages.started = BackupMessages.compile(
            config.getString(
                "started",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_STARTED,
                "A backup message matching this means a backup started"),
            BackupMessages.DEFAULT_STARTED);

        config
            .addCustomCategoryComment(CATEGORY_TEXT, "Texts of the toast. Latin and Cyrillic characters are supported");
        textTitle = config.getString("title", CATEGORY_TEXT, textTitle, "Title while the backup runs");
        textTitleDone = config.getString("titleDone", CATEGORY_TEXT, textTitleDone, "Title after a backup");
        textTitleFailed = config
            .getString("titleFailed", CATEGORY_TEXT, textTitleFailed, "Title after a failed backup");
        textElapsed = config.getString("elapsed", CATEGORY_TEXT, textElapsed, "Caption of the elapsed time");
        textTook = config.getString("took", CATEGORY_TEXT, textTook, "Caption of the backup duration");
        textLeft = config.getString("left", CATEGORY_TEXT, textLeft, "Word after the estimated remaining time");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        network = NetworkRegistry.INSTANCE.newSimpleChannel("gtnhtweaks:backups");
        network.registerMessage(BackupPacket.Handler.class, BackupPacket.class, 0, Side.CLIENT);

        if (detectFromServerLog) {
            ServerBackupWatcher watcher = new ServerBackupWatcher();
            FMLCommonHandler.instance()
                .bus()
                .register(watcher);
            ServerBackupWatcher.install();
        }
        if (event.getSide()
            .isClient()) BackupClient.register();
    }
}
