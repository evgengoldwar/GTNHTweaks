package gtnhtweaks.modules.minecraft.movement;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gtnhtweaks.common.Network;
import gtnhtweaks.modules.minecraft.MinecraftModule;
import io.netty.buffer.ByteBuf;

/**
 * The handshake of the movement: the server tells a joining client what it allows, a client that has the movement on
 * answers, and only then the server gives that player the new poses. A server without the mod sends nothing, so the
 * client keeps the movement of vanilla.
 */
public final class MovementPackets {

    private MovementPackets() {}

    /** Server to client: the movement the server allows. */
    public static class Rules implements IMessage {

        public boolean crouch, swim;

        public Rules() {}

        public Rules(boolean crouch, boolean swim) {
            this.crouch = crouch;
            this.swim = swim;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            byte flags = buf.readByte();
            crouch = (flags & 1) != 0;
            swim = (flags & 2) != 0;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte((crouch ? 1 : 0) | (swim ? 2 : 0));
        }

        public static class Handler implements IMessageHandler<Rules, IMessage> {

            @Override
            public IMessage onMessage(Rules message, MessageContext ctx) {
                // a client with the movement off answers nothing and keeps the movement of vanilla
                if (!MinecraftModule.INSTANCE.isEnabled() || !MinecraftModule.modernMovement) return null;
                Movement.clientCrouch = message.crouch;
                Movement.clientSwim = message.swim;
                return new Ready();
            }
        }
    }

    /** Client to server: the client has the movement and moves its player by the rules. */
    public static class Ready implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}

        public static class Handler implements IMessageHandler<Ready, IMessage> {

            @Override
            public IMessage onMessage(Ready message, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                if (player != null) Movement.READY.add(player.getUniqueID());
                return null;
            }
        }
    }

    /** Sends the rules of the server to a player that joined. */
    static void sendRules(EntityPlayerMP player, boolean crouch, boolean swim) {
        Network.movement.sendTo(new Rules(crouch, swim), player);
    }
}
