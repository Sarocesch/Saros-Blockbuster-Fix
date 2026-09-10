package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.dynamx.DynamXCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Records the BasicsAddon state bitmask of a DynamX vehicle an actor is riding.
 *
 * This is what {@link VehicleControlAction} does not cover: that action carries
 * the engine controls (throttle, brake, steering), while siren, beacons, head
 * lights, DRL and turn signals live in a separate synchronized int on
 * BasicsAddonModule. Without this, a replayed actor drives the car but never
 * turns the blue lights or the siren on or off.
 *
 * Delta-compression: the recorder only emits this action when the bitmask
 * changes. The vehicle keeps the last state until the next change.
 */
public class VehicleBasicsAction extends Action
{
    public int state;

    public VehicleBasicsAction()
    {}

    public VehicleBasicsAction(int state)
    {
        this.state = state;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null) return;

        Entity vehicle = actor.getRidingEntity();

        if (vehicle == null || !DynamXCompat.isVehicle(vehicle)) return;

        DynamXCompat.setVehicleBasicsState(vehicle, this.state);
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.state = buf.readInt();
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeInt(this.state);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.state = tag.getInteger("BasicsState");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setInteger("BasicsState", this.state);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
