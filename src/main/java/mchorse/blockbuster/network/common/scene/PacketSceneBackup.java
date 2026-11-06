package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server an Client: ein Stueck einer Datei fuer die lokale Sicherung einer Szene.
 */
public class PacketSceneBackup implements IMessage
{
    /** Ordnername der Sicherung, z.B. "meineszene_2026-11-06_14-30-00". */
    public String folder = "";

    /** Pfad in der Sicherung, z.B. "scenes/meineszene.dat" oder "records/x.dat". */
    public String path = "";

    public int file;
    public int files;
    public int chunk;
    public int chunks;
    public byte[] data = new byte[0];

    public PacketSceneBackup()
    {}

    public PacketSceneBackup(String folder, String path, int file, int files, int chunk, int chunks, byte[] data)
    {
        this.folder = folder;
        this.path = path;
        this.file = file;
        this.files = files;
        this.chunk = chunk;
        this.chunks = chunks;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.folder = ByteBufUtils.readUTF8String(buf);
        this.path = ByteBufUtils.readUTF8String(buf);
        this.file = buf.readInt();
        this.files = buf.readInt();
        this.chunk = buf.readInt();
        this.chunks = buf.readInt();
        this.data = new byte[buf.readInt()];
        buf.readBytes(this.data);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, this.folder);
        ByteBufUtils.writeUTF8String(buf, this.path);
        buf.writeInt(this.file);
        buf.writeInt(this.files);
        buf.writeInt(this.chunk);
        buf.writeInt(this.chunks);
        buf.writeInt(this.data.length);
        buf.writeBytes(this.data);
    }
}
