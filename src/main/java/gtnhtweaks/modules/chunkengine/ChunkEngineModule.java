package gtnhtweaks.modules.chunkengine;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;

/**
 * Faster chunk generation in Minecraft itself, under the mods: a chunk goes to the players once, when it is done; the
 * population of a chunk sets its blocks without waking up their neighbors; a block that changes nothing in the light
 * does not start a light update; the terrain of new chunks is made ahead on other threads. Every part has its own
 * switch, and the Chunk generation log counts what each one saved.
 */
public final class ChunkEngineModule extends TweakModule {

    public static final ChunkEngineModule INSTANCE = new ChunkEngineModule();

    public static final String CATEGORY_SENDING = "sending";
    public static final String CATEGORY_POPULATION = "population";
    public static final String CATEGORY_LIGHT = "light";
    public static final String CATEGORY_TERRAIN = "terrain";

    /** A chunk goes to the players once it and the neighbors that write into it are populated. */
    public static boolean sendFinishedChunks = true;
    /** Seconds a chunk waits for its neighbors before it is sent anyway. */
    public static int sendWaitSeconds = 10;
    /** No block update packets are made for a chunk that no player has yet. */
    public static boolean skipUnsentUpdates = true;
    /** The blocks set in a population do not notify their neighbors. */
    public static boolean quietPopulation = true;
    /** A set block whose light is the same as of the block before starts no light update. */
    public static boolean skipUnchangedLight = true;
    /** The terrain of new chunks is made ahead on other threads. */
    public static boolean parallelTerrain = true;
    /** Threads making terrain, 0 for one per four processor threads, from 1 to 4. */
    public static int terrainThreads = 0;
    /** The first chunks of a dimension made on a thread are compared with the chunks made the usual way. */
    public static int verifyChunks = 16;
    /** More chunk generators whose terrain may be made on other threads, by class name. */
    public static String[] extraGenerators = new String[0];

    private ChunkEngineModule() {
        super(
            "chunkEngine",
            "Faster chunk generation in Minecraft itself: chunks sent once when done, quiet population, fewer light "
                + "updates, terrain made on other threads",
            ModuleSide.SERVER);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_SENDING,
            "When chunks go to the players. Each change is counted in the Chunk generation log");
        sendFinishedChunks = config.getBoolean(
            "sendFinishedChunks",
            CATEGORY_SENDING,
            sendFinishedChunks,
            "A chunk goes to the players once it is populated and so are the neighbors whose population writes into "
                + "it, so it is sent once instead of again after each population. Copies of a chunk that a player "
                + "does not have yet are not sent either");
        sendWaitSeconds = config.getInt(
            "sendWaitSeconds",
            CATEGORY_SENDING,
            sendWaitSeconds,
            1,
            120,
            "Seconds a chunk waits for the population of its neighbors; then it is sent as it is");
        skipUnsentUpdates = config.getBoolean(
            "skipUnsentUpdates",
            CATEGORY_SENDING,
            skipUnsentUpdates,
            "No block update packets are made for a chunk that none of its players has yet: it goes to them whole "
                + "later anyway");

        config.addCustomCategoryComment(CATEGORY_POPULATION, "How the population of a new chunk sets its blocks");
        quietPopulation = config.getBoolean(
            "quietPopulation",
            CATEGORY_POPULATION,
            quietPopulation,
            "Blocks set while a chunk is populated do not notify their neighbors, as in newer versions: no block "
                + "updates, comparator updates or chunk loads by them. Water placed by a generator next to a hole "
                + "may then stay still until something touches it");

        config.addCustomCategoryComment(CATEGORY_LIGHT, "Light updates when blocks are set");
        skipUnchangedLight = config.getBoolean(
            "skipUnchangedLight",
            CATEGORY_LIGHT,
            skipUnchangedLight,
            "A block set where the block before gives the same light and lets through the same light starts no light "
                + "update, as in newer versions: ores in place of stone, a block set to itself");

        config.addCustomCategoryComment(
            CATEGORY_TERRAIN,
            "The terrain of new chunks made ahead on other threads, near the players. The population stays on the "
                + "server thread. Works for the chunk generators known to be safe for it: Realistic World Gen, the "
                + "Nether and the End");
        parallelTerrain = config.getBoolean(
            "parallelTerrain",
            CATEGORY_TERRAIN,
            parallelTerrain,
            "Make the terrain of new chunks ahead on other threads, so the server only populates them");
        terrainThreads = config.getInt(
            "terrainThreads",
            CATEGORY_TERRAIN,
            terrainThreads,
            0,
            16,
            "Threads making terrain; 0 picks one per four processor threads, from 1 to 4");
        verifyChunks = config.getInt(
            "verifyChunks",
            CATEGORY_TERRAIN,
            verifyChunks,
            0,
            1000,
            "The first chunks of each dimension made on a thread are also made the usual way and compared block by "
                + "block; a difference turns the threads off for that dimension and is written to the Chunk "
                + "generation log");
        extraGenerators = config.getStringList(
            "extraGenerators",
            CATEGORY_TERRAIN,
            extraGenerators,
            "More chunk generators to try on threads, by full class name. Only for generators that keep their state "
                + "in themselves; the comparison of the first chunks checks them");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new TerrainWorkers.Events());
    }
}
