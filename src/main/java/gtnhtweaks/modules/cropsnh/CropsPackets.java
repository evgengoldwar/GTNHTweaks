package gtnhtweaks.modules.cropsnh;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * The server tells a joining client that it plants with crop sticks, and the client asks it to on a right click. A
 * server without the module sends nothing, so the client leaves the clicks to the game.
 */
public final class CropsPackets {

    private CropsPackets() {}

    /** Server to client: the server places crop sticks with a seed when asked. */
    public static class Ready implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}

        public static class Handler implements IMessageHandler<Ready, IMessage> {

            @Override
            public IMessage onMessage(Ready message, MessageContext ctx) {
                if (CropsNHModule.isActive()) CropsNHModule.serverPlants = true;
                return null;
            }
        }
    }

    /** Client to server: a right click on the side of a block, to place crop sticks there and plant the seed. */
    public static class Plant implements IMessage {

        int x, y, z, side;
        float hitX, hitY, hitZ;

        public Plant() {}

        public Plant(int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            side = buf.readByte();
            hitX = buf.readFloat();
            hitY = buf.readFloat();
            hitZ = buf.readFloat();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeByte(side);
            buf.writeFloat(hitX);
            buf.writeFloat(hitY);
            buf.writeFloat(hitZ);
        }

        public static class Handler implements IMessageHandler<Plant, IMessage> {

            @Override
            public IMessage onMessage(Plant message, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                // the network thread: the world is changed in the next server tick
                if (player != null && CropsNHModule.isActive()) CropsServer.queue(player, message);
                return null;
            }
        }
    }
}
