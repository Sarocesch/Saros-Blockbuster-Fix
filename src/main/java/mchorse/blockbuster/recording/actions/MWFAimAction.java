package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.mwf.PacketMWFAim;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Replays whether the actor was aiming down sights.
 *
 * Cosmetic only: it just marks the actor as aiming on the tracking clients so
 * MWF raises the weapon in third person. Nothing about accuracy, recoil or
 * damage is affected, because the actor never really fires.
 */
public class MWFAimAction extends Action
{
    public boolean aiming;

    public MWFAimAction()
    {}

    public MWFAimAction(boolean aiming)
    {
        this.aiming = aiming;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null || actor.world.isRemote) return;
        if (!MWFCompat.isAvailable()) return;

        Dispatcher.sendToTracked(actor, new PacketMWFAim(actor.getEntityId(), this.aiming));
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.aiming = buf.readBoolean();
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeBoolean(this.aiming);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.aiming = tag.getBoolean("Aiming");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setBoolean("Aiming", this.aiming);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
