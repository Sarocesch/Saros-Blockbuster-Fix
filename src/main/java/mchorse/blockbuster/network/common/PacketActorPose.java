package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server -> Client: MWRP-Pose eines Actors. Leer heisst keine Pose.
 * Ueber die Entity-ID, MWRP selbst kennt Actors nicht.
 */
public class PacketActorPose implements IMessage
{
    public int entityId;
    public String pose = "";

    public PacketActorPose()
    {}

    public PacketActorPose(int entityId, String pose)
    {
        this.entityId = entityId;
        this.pose = pose;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entityId = buf.readInt();
        this.pose = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entityId);
        ByteBufUtils.writeUTF8String(buf, this.pose);
    }
}
