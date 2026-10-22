package gtnhtweaks.modules.backups;

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

    public BackupPacket() {}

    public BackupPacket(byte state, float progress, long elapsedMs) {
        this.state = state;
        this.progress = progress;
        this.elapsedMs = elapsedMs;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        state = buf.readByte();
        progress = buf.readFloat();
        elapsedMs = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(state);
        buf.writeFloat(progress);
        buf.writeLong(elapsedMs);
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
