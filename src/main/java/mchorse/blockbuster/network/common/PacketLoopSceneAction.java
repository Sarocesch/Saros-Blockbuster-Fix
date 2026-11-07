package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client -> Server: Loop-Szenen-Uebersicht. Liste anfordern, eine Szene
 * dauerhaft an- oder ausschalten, neu starten, zu ihr teleportieren. Antwort ist immer die
 * frische Liste.
 */
public class PacketLoopSceneAction implements IMessage
{
    public static final byte LIST = 0;
    public static final byte ON = 1;
    public static final byte OFF = 2;
    public static final byte TELEPORT = 3;
    public static final byte RESTART = 4;
    public static final byte SCHEDULE = 5;

    public byte action;
    public String id = "";

    public PacketLoopSceneAction()
    {}

    public PacketLoopSceneAction(byte action, String id)
    {
        this.action = action;
        this.id = id;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.action = buf.readByte();
        this.id = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeByte(this.action);
        ByteBufUtils.writeUTF8String(buf, this.id);
    }
}
