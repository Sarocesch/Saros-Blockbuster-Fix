package mchorse.blockbuster.network.common.mwf;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server -> Client: whether an actor is aiming down sights.
 *
 * MWF's own PacketAimingResponse carries the same information but is keyed by
 * player UUID, and its handler only ever resolves players. Addressed by entity
 * ID here so an EntityActor is found.
 */
public class PacketMWFAim implements IMessage
{
    public int entityId;
    public boolean aiming;

    public PacketMWFAim()
    {}

    public PacketMWFAim(int entityId, boolean aiming)
    {
        this.entityId = entityId;
        this.aiming = aiming;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entityId = buf.readInt();
        this.aiming = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.aiming);
    }
}
