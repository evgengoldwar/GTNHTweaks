package gtnhtweaks.modules.backups;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.backups.client.BackupClient;

/**
 * Client side of the backups: shows a toast in the top right corner while ServerUtilities makes a world backup: elapsed
 * time, progress bar and the result.
 * <p>
 * The progress comes from {@link BackupsServerModule} on the server. Without GTNHTweaks on the server the client falls
 * back to the backup messages in the chat and estimates the progress from the previous backup.
 */
public final class BackupsModule extends TweakModule {

    public static final BackupsModule INSTANCE = new BackupsModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_DETECTION = "detection";
    private static final String CATEGORY_TEXT = "text";

    public static GTNHTheme style = GTNHTheme.SHADOW;
    public static boolean playSound = false;
    public static boolean hideChatMessages = false;
    public static int finishedToastSeconds = 6;
    public static int timeoutMinutes = 30;

    public static boolean detectFromChat = false;

    public static String textTitle = "Backing up the world";
    public static String textTitleDone = "Backup complete";
    public static String textTitleFailed = "Backup failed";
    public static String textElapsed = "Time";
    public static String textTook = "Took";
    public static String textLeft = "left";

    private BackupsModule() {
        super(
            "backups",
            "Toast with the progress of ServerUtilities backups in the top right corner",
            ModuleSide.CLIENT);
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
            "Hide the toast of a backup noticed in the chat when nothing was heard about it for this many minutes");

        config.addCustomCategoryComment(
            CATEGORY_DETECTION,
            "How a backup is noticed in the chat, on servers without GTNHTweaks. Regular expressions are matched "
                + "against whole chat messages without color codes");
        // moved to the server module
        removeKey(config, CATEGORY_DETECTION, "detectFromServerLog");
        removeKey(config, CATEGORY_DETECTION, "loggers");
        detectFromChat = config.getBoolean(
            "detectFromChat",
            CATEGORY_DETECTION,
            detectFromChat,
            "Notice backups by the messages in the chat. Used on servers without GTNHTweaks, the progress is then "
                + "estimated from the duration of the previous backup");
        BackupMessages.CHAT.load(config, CATEGORY_DETECTION, "chat message");

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
        BackupClient.register();
    }
}
