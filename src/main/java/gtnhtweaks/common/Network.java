package gtnhtweaks.common;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import gtnhtweaks.modules.backups.BackupPacket;
import gtnhtweaks.modules.minecraft.movement.MovementPackets;

/**
 * The network channels of the mod. Registered on both sides whatever modules are on, so a client and a server agree
 * on them; the handlers do nothing for a module that is off.
 */
public final class Network {

    /** State of a server backup, to the clients. */
    public static SimpleNetworkWrapper backups;
    /** Which movement the server allows, to the clients, and the answer of a client that has the mod. */
    public static SimpleNetworkWrapper movement;

    private Network() {}

    public static void register() {
        backups = NetworkRegistry.INSTANCE.newSimpleChannel("gtnhtweaks:backups");
        backups.registerMessage(BackupPacket.Handler.class, BackupPacket.class, 0, Side.CLIENT);

        movement = NetworkRegistry.INSTANCE.newSimpleChannel("gtnhtweaks:move");
        movement.registerMessage(MovementPackets.Rules.Handler.class, MovementPackets.Rules.class, 0, Side.CLIENT);
        movement.registerMessage(MovementPackets.Ready.Handler.class, MovementPackets.Ready.class, 1, Side.SERVER);
    }
}
