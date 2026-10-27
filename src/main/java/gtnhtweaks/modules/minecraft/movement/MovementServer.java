package gtnhtweaks.modules.minecraft.movement;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftServerModule;

/** The server side of the movement: tells the joining players the rules and keeps the poses of the players. */
public final class MovementServer {

    private MovementServer() {}

    public static void register() {
        MovementServer server = new MovementServer();
        MinecraftForge.EVENT_BUS.register(server);
        FMLCommonHandler.instance()
            .bus()
            .register(server);
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        Movement.forget(event.player.getUniqueID());
        if (MinecraftServerModule.crouch || MinecraftServerModule.swim) MovementPackets
            .sendRules((EntityPlayerMP) event.player, MinecraftServerModule.crouch, MinecraftServerModule.swim);
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Movement.forget(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof EntityPlayerMP)
            Movement.updateOnServer(event.player);
    }

    /** A player crawling in a gap of one block has its eyes low, not in the block above, so it does not suffocate. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (event.source != DamageSource.inWall || !(event.entityLiving instanceof EntityPlayerMP)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (Movement.state(player).pose != Pose.STAND && !Movement.eyesInBlock(player)) event.setCanceled(true);
    }
}
