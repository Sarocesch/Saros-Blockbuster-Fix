package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.mwf.PacketMMState;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Replays the ModularMovements pose (leaning, sitting, crawling, rolling) the
 * recording player was in.
 *
 * Cosmetic only: it just tells the tracking clients which pose to draw. No
 * hitbox, no movement and no server side state is touched, so a replay cannot
 * desync anything.
 */
public class MMStateAction extends Action
{
    public int code;

    public MMStateAction()
    {}

    public MMStateAction(int code)
    {
        this.code = code;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null || actor.world.isRemote) return;
        if (!MWFCompat.isAvailable()) return;

        Dispatcher.sendToTracked(actor, new PacketMMState(actor.getEntityId(), this.code));
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.code = buf.readInt();
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeInt(this.code);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.code = tag.getInteger("MMState");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setInteger("MMState", this.code);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
