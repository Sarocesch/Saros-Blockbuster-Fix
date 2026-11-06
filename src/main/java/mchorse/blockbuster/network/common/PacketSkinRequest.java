package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Frage nach einem Skin, der hinter seiner URL nicht mehr zu holen ist.
 *
 * <p>Geht in beide Richtungen: Client fragt den Server, und der Server fragt die
 * uebrigen Spieler, ob jemand das Bild noch hat.</p>
 */
public class PacketSkinRequest implements IMessage
{
    public String url = "";

    public PacketSkinRequest()
    {}

    public PacketSkinRequest(String url)
    {
        this.url = url;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.url = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, this.url);
    }
}
