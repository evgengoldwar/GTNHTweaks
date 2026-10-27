package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C03PacketPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.movement.Movement;

/**
 * The server takes the movement of a player in the box of its pose: the pose is updated right before, after the
 * sneaking and sprinting the client sent with it, so a player crouching or swimming into a low gap is not sent back.
 */
@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer_Movement {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "processPlayer", at = @At("HEAD"))
    private void gtnhtweaks$pose(C03PacketPlayer packet, CallbackInfo ci) {
        if (playerEntity != null) Movement.updateOnServer(playerEntity);
    }
}
