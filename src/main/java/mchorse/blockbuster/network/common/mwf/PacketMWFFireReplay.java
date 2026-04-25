package mchorse.blockbuster.network.common.mwf;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server → Client: play a cosmetic MWF weapon fire animation on an actor.
 *
 * MWF's own PacketOtherShooterAnimation uses getPlayerEntityByUUID() on the
 * client, which cannot find EntityActor (not an EntityPlayer).  This packet
 * uses entity ID so any EntityLivingBase can be located.
 */
public class PacketMWFFireReplay implements IMessage
{
    public int entityId;
    public String gunName = "";
    public int fireTickDelay;

    public PacketMWFFireReplay()
    {}

    public PacketMWFFireReplay(int entityId, String gunName, int fireTickDelay)
    {
        this.entityId = entityId;
        this.gunName = gunName == null ? "" : gunName;
        this.fireTickDelay = fireTickDelay;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entityId = buf.readInt();
        this.fireTickDelay = buf.readInt();
        this.gunName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entityId);
        buf.writeInt(this.fireTickDelay);
        ByteBufUtils.writeUTF8String(buf, this.gunName);
    }
}
