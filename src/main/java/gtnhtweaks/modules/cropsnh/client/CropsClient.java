package gtnhtweaks.modules.cropsnh.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import gtnhtweaks.common.Network;
import gtnhtweaks.modules.cropsnh.CropPlanting;
import gtnhtweaks.modules.cropsnh.CropsPackets;
import gtnhtweaks.modules.cropsnh.CropsNHModule;

/**
 * The client side: a right click with a seed in the main hand and crop sticks in the off hand on a soil the seed grows
 * on asks the server to place the sticks with the seed, instead of the usual use of the seed and the sticks.
 */
public final class CropsClient {

    private CropsClient() {}

    public static void register() {
        CropsClient client = new CropsClient();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance()
            .bus()
            .register(client);
    }

    /** Early, before other mods act on the click. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || !CropsNHModule.serverPlants) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = event.entityPlayer;
        if (player != mc.thePlayer || !player.worldObj.isRemote) return;
        if (CropPlanting.target(player.worldObj, player, event.x, event.y, event.z, event.face) == null) return;

        // the click is the server's now: no packet of the game goes out, ours does
        event.setCanceled(true);
        MovingObjectPosition hit = mc.objectMouseOver;
        float hitX = 0.5F, hitY = 1, hitZ = 0.5F;
        if (hit != null && hit.hitVec != null) {
            hitX = (float) hit.hitVec.xCoord - event.x;
            hitY = (float) hit.hitVec.yCoord - event.y;
            hitZ = (float) hit.hitVec.zCoord - event.z;
        }
        Network.crops.sendToServer(new CropsPackets.Plant(event.x, event.y, event.z, event.face, hitX, hitY, hitZ));
        player.swingItem();
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        CropsNHModule.serverPlants = false;
    }
}
