package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.mwf.PacketMWFExtraSlot;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Replays the ModularWarfare extra slot content (the custom vests) an actor
 * was wearing at this point of the recording.
 *
 * Purely cosmetic. The stack is only pushed to the clients that track the
 * actor, it is never inserted into a capability, so nothing on the server can
 * be picked up, dropped or duplicated from it.
 */
public class MWFExtraSlotAction extends Action
{
    public int slot;
    public net.minecraft.item.ItemStack stack = net.minecraft.item.ItemStack.EMPTY;

    public MWFExtraSlotAction()
    {}

    public MWFExtraSlotAction(int slot, net.minecraft.item.ItemStack stack)
    {
        this.slot = slot;
        this.stack = stack == null ? net.minecraft.item.ItemStack.EMPTY : stack.copy();
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null || actor.world.isRemote) return;
        if (!MWFCompat.isAvailable()) return;

        Dispatcher.sendToTracked(actor, new PacketMWFExtraSlot(actor.getEntityId(), this.slot, this.stack));
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.slot = buf.readInt();
        this.stack = ByteBufUtils.readItemStack(buf);

        if (this.stack == null)
        {
            this.stack = net.minecraft.item.ItemStack.EMPTY;
        }
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeInt(this.slot);
        ByteBufUtils.writeItemStack(buf, this.stack);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.slot = tag.getInteger("Slot");
        this.stack = new net.minecraft.item.ItemStack(tag.getCompoundTag("Stack"));
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setInteger("Slot", this.slot);
        tag.setTag("Stack", this.stack.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
