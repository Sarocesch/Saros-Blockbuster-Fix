package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.dynamx.DynamXCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Records the control bitmask of a DynamX vehicle that an actor is driving.
 *
 * Bitmask bits (BasicEngineModule):
 *   1  = Engine ON, 2 = Accelerate, 4 = Reverse,
 *   8  = Turn left, 16 = Turn right, 32 = Handbrake
 *
 * Delta-compression: Recorder only emits this action when the bitmask changes.
 * The vehicle retains the last set control state until the next change, so no
 * per-tick emission is needed.
 */
public class VehicleControlAction extends Action
{
    public int controls;

    public VehicleControlAction()
    {}

    public VehicleControlAction(int controls)
    {
        this.controls = controls;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null) return;

        Entity vehicle = actor.getRidingEntity();
        if (vehicle == null || !DynamXCompat.isVehicle(vehicle)) return;

        DynamXCompat.setVehicleControls(vehicle, this.controls);
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.controls = buf.readInt();
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeInt(this.controls);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.controls = tag.getInteger("Controls");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setInteger("Controls", this.controls);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
