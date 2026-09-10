package mchorse.blockbuster.network.common.mwf;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Server -> Client: the ModularWarfare extra slot content of an actor.
 *
 * MWF vests live in a capability that only players carry, and MWF syncs them
 * with a packet that resolves the receiver by player UUID. Neither works for an
 * EntityActor, so the actor's vest is carried here by entity ID instead.
 */
public class PacketMWFExtraSlot implements IMessage
{
    public int entityId;
    public int slot;
    public ItemStack stack = ItemStack.EMPTY;

    public PacketMWFExtraSlot()
    {}

    public PacketMWFExtraSlot(int entityId, int slot, ItemStack stack)
    {
        this.entityId = entityId;
        this.slot = slot;
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entityId = buf.readInt();
        this.slot = buf.readInt();
        this.stack = ByteBufUtils.readItemStack(buf);

        if (this.stack == null)
        {
            this.stack = ItemStack.EMPTY;
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entityId);
        buf.writeInt(this.slot);
        ByteBufUtils.writeItemStack(buf, this.stack);
    }
}
