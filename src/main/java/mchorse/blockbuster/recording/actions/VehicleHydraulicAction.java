package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.dynamx.DynamXCompat;
import mchorse.blockbuster.recording.dynamx.HydraulicCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Records the Saros Hydraulic Addon state of a DynamX vehicle the actor drives:
 * aircraft controls (landing gear, flaps, control surfaces, throttle), the gear
 * and engine spool position, Hydraulic_ parts and detached engines.
 *
 * {@link VehicleControlAction} only carries the engine bitmask, so without this a
 * replayed aircraft flies its recorded path with the gear frozen as it was at
 * mount time. Delta-compressed like {@link VehicleBasicsAction}: the recorder
 * only emits it when the captured state changes.
 */
public class VehicleHydraulicAction extends Action
{
    public NBTTagCompound state = new NBTTagCompound();

    public VehicleHydraulicAction()
    {}

    public VehicleHydraulicAction(NBTTagCompound state)
    {
        this.state = state;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null) return;

        Entity vehicle = actor.getRidingEntity();

        if (vehicle == null || !DynamXCompat.isVehicle(vehicle)) return;

        HydraulicCompat.apply(vehicle, this.state);
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.state = tag == null ? new NBTTagCompound() : tag;
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        ByteBufUtils.writeTag(buf, this.state);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.state = tag.getCompoundTag("HydraulicState");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setTag("HydraulicState", this.state);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
