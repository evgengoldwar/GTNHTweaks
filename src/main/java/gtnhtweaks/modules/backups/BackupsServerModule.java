package gtnhtweaks.modules.backups;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;

/**
 * Server side of the backups: watches the log of ServerUtilities and sends the state of a running backup to the
 * players, whose {@link BackupsModule} shows it. Runs on a dedicated server and in the singleplayer world.
 */
public final class BackupsServerModule extends TweakModule {

    public static final BackupsServerModule INSTANCE = new BackupsServerModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_DETECTION = "detection";

    public static int timeoutMinutes = 30;

    private BackupsServerModule() {
        super(
            "backupsServer",
            "Sends the real progress of ServerUtilities backups to the players, read from the server log",
            ModuleSide.SERVER);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "How the server follows a backup");
        timeoutMinutes = config.getInt(
            "timeoutMinutes",
            CATEGORY_GENERAL,
            timeoutMinutes,
            1,
            1440,
            "Tell the players the backup is over when nothing was heard about it for this many minutes");

        config.addCustomCategoryComment(
            CATEGORY_DETECTION,
            "How a backup is noticed in the server log. Regular expressions are matched against whole log lines "
                + "without color codes");
        BackupMessages.loggers = BackupMessages.compile(
            config.getString(
                "loggers",
                CATEGORY_DETECTION,
                BackupMessages.DEFAULT_LOGGERS,
                "Loggers whose lines with a percentage are taken as backup progress"),
            BackupMessages.DEFAULT_LOGGERS);
        BackupMessages.LOG.load(config, CATEGORY_DETECTION, "log line");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerBackupWatcher());
        ServerBackupWatcher.install();
    }
}
