package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client -> Server: Marker-Taste gedrueckt. Ohne Inhalt, der Server kennt die
 * laufende Aufnahme und den Tick.
 */
public class PacketRecordMarker implements IMessage
{
    public PacketRecordMarker()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
