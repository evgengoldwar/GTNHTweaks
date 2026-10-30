package gtnhtweaks.modules.cropsnh;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.Network;

/** The server side: tells the joining players it plants with crop sticks and does it when they ask. */
public final class CropsServer {

    private static final Queue<Request> REQUESTS = new ConcurrentLinkedQueue<>();

    private static final class Request {

        final EntityPlayerMP player;
        final CropsPackets.Plant plant;

        Request(EntityPlayerMP player, CropsPackets.Plant plant) {
            this.player = player;
            this.plant = plant;
        }
    }

    private CropsServer() {}

    static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new CropsServer());
    }

    static void queue(EntityPlayerMP player, CropsPackets.Plant plant) {
        REQUESTS.add(new Request(player, plant));
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (CropsNHModule.plantWithSticks && event.player instanceof EntityPlayerMP)
            Network.crops.sendTo(new CropsPackets.Ready(), (EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (Request request; (request = REQUESTS.poll()) != null;) {
            CropsPackets.Plant p = request.plant;
            if (request.player.isDead || request.player.playerNetServerHandler == null) continue;
            try {
                CropPlanting.plant(request.player, p.x, p.y, p.z, p.side, p.hitX, p.hitY, p.hitZ);
            } catch (RuntimeException | LinkageError e) {
                GTNHTweaks.LOG.warn("Could not plant with crop sticks for " + request.player.getCommandSenderName(), e);
            }
        }
    }
}
