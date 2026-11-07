package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketActorPose;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Spielt die MWRP-Pose (/pose, Posen-Menue) nach, in der der Aufnehmende war.
 * Leer heisst: keine Pose.
 *
 * Nur Aussehen: der Actor merkt sich die Pose (fuer Spieler, die ihn spaeter
 * sehen) und die Clients zeichnen sie. Echte Spieler werden nie angefasst.
 */
public class MwrpPoseAction extends Action
{
    public String pose = "";

    public MwrpPoseAction()
    {}

    public MwrpPoseAction(String pose)
    {
        this.pose = pose == null ? "" : pose;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (!(actor instanceof EntityActor) || actor.world.isRemote) return;

        EntityActor entity = (EntityActor) actor;

        if (entity.mwrpPose.equals(this.pose)) return;

        entity.mwrpPose = this.pose;
        Dispatcher.sendToTracked(entity, new PacketActorPose(entity.getEntityId(), this.pose));
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.pose = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        ByteBufUtils.writeUTF8String(buf, this.pose);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.pose = tag.getString("Pose");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setString("Pose", this.pose);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
