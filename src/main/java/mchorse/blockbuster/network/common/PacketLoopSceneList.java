package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: alle Szenen mit Loops und ihr Zustand.
 */
public class PacketLoopSceneList implements IMessage
{
    public List<Entry> entries = new ArrayList<Entry>();

    public static class Entry
    {
        public String id = "";
        public String title = "";
        /** Dauerhaft an (steht in autoplay.txt). */
        public boolean active;
        /** Laeuft gerade, also Spieler in der Naehe. */
        public boolean playing;
        /** Mindestens eine Aufnahme der Szene fehlt. */
        public boolean broken;
        public int alive;
        public int total;
        public boolean hasPos;
        public double x;
        public double y;
        public double z;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        int count = buf.readInt();

        for (int i = 0; i < count; i++)
        {
            Entry entry = new Entry();

            entry.id = ByteBufUtils.readUTF8String(buf);
            entry.title = ByteBufUtils.readUTF8String(buf);
            entry.active = buf.readBoolean();
            entry.playing = buf.readBoolean();
            entry.broken = buf.readBoolean();
            entry.alive = buf.readInt();
            entry.total = buf.readInt();
            entry.hasPos = buf.readBoolean();
            entry.x = buf.readDouble();
            entry.y = buf.readDouble();
            entry.z = buf.readDouble();
            this.entries.add(entry);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entries.size());

        for (Entry entry : this.entries)
        {
            ByteBufUtils.writeUTF8String(buf, entry.id);
            ByteBufUtils.writeUTF8String(buf, entry.title == null ? "" : entry.title);
            buf.writeBoolean(entry.active);
            buf.writeBoolean(entry.playing);
            buf.writeBoolean(entry.broken);
            buf.writeInt(entry.alive);
            buf.writeInt(entry.total);
            buf.writeBoolean(entry.hasPos);
            buf.writeDouble(entry.x);
            buf.writeDouble(entry.y);
            buf.writeDouble(entry.z);
        }
    }
}
