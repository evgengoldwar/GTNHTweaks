package gtnhtweaks.modules.backups;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gtnhtweaks.modules.backups.client.BackupClient;
import io.netty.buffer.ByteBuf;

/** State of the backup on the server, sent to every player whenever it changes. */
public class BackupPacket implements IMessage {

    public static final byte RUNNING = 0;
    public static final byte FINISHED = 1;
    public static final byte FAILED = 2;
    /** The server lost track of the backup: hide the toast. */
    public static final byte CANCELLED = 3;

    public byte state;
    /** From 0 to 1, or -1 when the server knows no progress. */
    public float progress;
    public long elapsedMs;
    /** Size of the finished backup, like "2.0 GB", empty if unknown. */
    public String size = "";
    /**
     * Handed over directly inside the singleplayer game instead of through the network. The integrated server can be
     * paused while the backup thread works, so its packets would only arrive at the end.
     */
    public transient boolean local;

    public BackupPacket() {}

    public BackupPacket(byte state, float progress, long elapsedMs, String size) {
        this.state = state;
        this.progress = progress;
        this.elapsedMs = elapsedMs;
        this.size = size;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        state = buf.readByte();
        progress = buf.readFloat();
        elapsedMs = buf.readLong();
        size = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(state);
        buf.writeFloat(progress);
        buf.writeLong(elapsedMs);
        ByteBufUtils.writeUTF8String(buf, size);
    }

    public static class Handler implements IMessageHandler<BackupPacket, IMessage> {

        @Override
        public IMessage onMessage(BackupPacket message, MessageContext ctx) {
            // Runs on the network thread, the client applies it on its next tick
            BackupClient.receive(message);
            return null;
        }
    }
}
