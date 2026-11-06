package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Das Bild zu einer Skin-URL, als Bytes.
 *
 * <p>Client an Server: "ich habe es noch". Server an Client: "hier ist es".</p>
 */
public class PacketSkinData implements IMessage
{
    /** Groesser als das schickt niemand - ein Spieler-Skin hat wenige Kilobyte. */
    public static final int MAX_SIZE = 30000;

    public String url = "";
    public byte[] data = new byte[0];

    public PacketSkinData()
    {}

    public PacketSkinData(String url, byte[] data)
    {
        this.url = url;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.url = ByteBufUtils.readUTF8String(buf);

        int size = buf.readInt();

        if (size < 0 || size > MAX_SIZE || size > buf.readableBytes())
        {
            this.data = new byte[0];

            return;
        }

        this.data = new byte[size];
        buf.readBytes(this.data);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, this.url);

        buf.writeInt(this.data.length);
        buf.writeBytes(this.data);
    }
}
