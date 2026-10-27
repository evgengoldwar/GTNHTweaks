package gtnhtweaks.modules.minecraft;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.minecraft.movement.MovementServer;

/**
 * Server side tweaks of Minecraft itself: the crouching and swimming of newer versions. The server decides: it gives
 * the new poses only to the players whose client has GTNHTweaks with the movement on, and a client keeps the movement
 * of vanilla on a server without them. Runs on a dedicated server and in the singleplayer world.
 */
public final class MinecraftServerModule extends TweakModule {

    public static final MinecraftServerModule INSTANCE = new MinecraftServerModule();

    public static final String CATEGORY_MOVEMENT = "movement";

    /** Crouching in a box one and a half blocks high, and staying crouched under a low ceiling. */
    public static boolean crouch = true;
    /** Swimming while sprinting in water, and crawling through gaps one block high. */
    public static boolean swim = true;

    private MinecraftServerModule() {
        super(
            "minecraftServer",
            "Crouching and swimming of newer versions, for the players whose client has them too",
            ModuleSide.SERVER);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_MOVEMENT,
            "Crouching and swimming of newer versions. Given only to the players whose client has GTNHTweaks with "
                + "the movement on (the movement setting of the Minecraft client module), others move as in vanilla. "
                + "Players get the changes when they join the world again");
        crouch = config.getBoolean(
            "crouch",
            CATEGORY_MOVEMENT,
            crouch,
            "Sneaking crouches lower, in a box one and a half blocks high: the player fits under slabs one and a half "
                + "blocks above the ground. Under a ceiling too low to stand it stays crouched");
        swim = config.getBoolean(
            "swim",
            CATEGORY_MOVEMENT,
            swim,
            "Sprinting with the head under water swims: the player lies in the water, swims where it looks, faster "
                + "than vanilla, and fits through gaps one block high. Where there is no room to stand or crouch it "
                + "crawls, also on land");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MovementServer.register();
    }
}
