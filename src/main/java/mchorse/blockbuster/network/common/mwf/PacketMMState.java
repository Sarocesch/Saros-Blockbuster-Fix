package mchorse.blockbuster.network.common.mwf;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server -> Client: an actor's ModularMovements pose (lean, sit, crawl, roll).
 *
 * The whole state is one int, see PlayerState.writeCode/readCode. Addressed by
 * entity ID because that is the key ModularMovements itself uses on the client.
 */
public class PacketMMState implements IMessage
{
    public int entityId;
    public int code;

    public PacketMMState()
    {}

    public PacketMMState(int entityId, int code)
    {
        this.entityId = entityId;
        this.code = code;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entityId = buf.readInt();
        this.code = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entityId);
        buf.writeInt(this.code);
    }
}
