package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.dynamx.DynamXCompat;
import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.List;
import java.util.UUID;

/**
 * Specialized mount action for DynamX vehicles.
 *
 * Extends MountingAction to carry:
 * - seatIndex: which seat of a multi-seat vehicle the actor occupies
 * - startX/Y/Z + startYaw/Pitch: vehicle position at recording time, so
 *   playback can teleport the vehicle back to its starting pose on each
 *   replay / loop / stop.
 *
 * On apply() for isMounting=true:
 * 1. Teleports the target vehicle back to its recorded start position/rotation
 *    (so replay is repeatable even if the vehicle moved during a previous run)
 * 2. Calls BasePartSeat.mountEntity() via DynamXCompat so the SeatsModule
 *    receives the passenger mapping
 *
 * Falls back to vanilla startRiding()/dismount via super.apply() if the
 * target is not a DynamX vehicle (for forward-compat with older records).
 */
public class VehicleMountAction extends MountingAction
{
    public int seatIndex;
    public double startX;
    public double startY;
    public double startZ;
    public float startYaw;
    public float startPitch;
    public boolean hasStartPose;

    /**
     * Full NBT snapshot of the vehicle at recording-start time. Used to respawn
     * the vehicle if someone destroyed it between recording and playback.
     * Includes the entity "id" tag so EntityList.createEntityFromNBT can
     * reconstruct the correct vehicle subclass (Car, Boat, etc.).
     */
    public NBTTagCompound vehicleSnapshot;

    public VehicleMountAction()
    {
        super();
    }

    public VehicleMountAction(UUID target, boolean isMounting, int seatIndex)
    {
        super(target, isMounting);
        this.seatIndex = seatIndex;
    }

    public VehicleMountAction(UUID target, boolean isMounting, int seatIndex,
                              double startX, double startY, double startZ,
                              float startYaw, float startPitch,
                              NBTTagCompound vehicleSnapshot)
    {
        super(target, isMounting);
        this.seatIndex = seatIndex;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.startYaw = startYaw;
        this.startPitch = startPitch;
        this.hasStartPose = true;
        this.vehicleSnapshot = vehicleSnapshot;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (!this.isMounting)
        {
            /* Dismount: do NOT teleport the vehicle here — the actor is still
             * riding it. Teleporting would snap the vehicle to the start pose
             * while the actor is still attached, leaving the actor floating
             * at the end-of-drive position. Vehicle reset happens at
             * playback stop via RecordPlayer.stopPlaying(). */
            super.apply(actor);
            return;
        }

        /* Ensure the vehicle exists — respawn from NBT snapshot if missing */
        Entity vehicle = this.ensureVehicle(actor.world);

        if (DynamXCompat.isVehicle(vehicle))
        {
            /* Teleport vehicle to start pose only if playback tick == mount tick.
             * This is the FIRST mount of this playback loop — reset so each
             * loop starts from the same position. */
            if (this.hasStartPose)
            {
                this.teleportVehicle(vehicle);
            }

            boolean mounted = DynamXCompat.mountSeat(vehicle, actor, this.seatIndex);

            if (mounted)
            {
                /* A fake player driving hands DynamX's physics authority to a
                 * client that does not exist, so the replayed controls never
                 * arrive and the car stays put with its engine running. Keep
                 * the simulation on the server for those.
                 *
                 * Deliberately NOT done for a real target player - they must
                 * keep authority on their own client or they lose control. */
                if (isFakePlayer(actor))
                {
                    DynamXCompat.forceServerSimulation(vehicle);
                }

                return;
            }
        }

        /* Fallback to vanilla mount if vehicle is not a DynamX entity */
        super.apply(actor);
    }

    /**
     * Find the vehicle by UUID, or respawn it from the NBT snapshot if the
     * original was destroyed. Returns null if no snapshot is available and
     * the vehicle cannot be found.
     */
    private Entity ensureVehicle(World world)
    {
        if (world.isRemote) return EntityUtils.entityByUUID(world, this.target);

        Entity vehicle = EntityUtils.entityByUUID(world, this.target);
        if (vehicle != null && !vehicle.isDead) return vehicle;

        /* Vehicle is missing or dead — try to respawn from snapshot */
        if (this.vehicleSnapshot == null) return null;

        try
        {
            /* Clone snapshot so we don't mutate the persistent recording NBT */
            NBTTagCompound tag = this.vehicleSnapshot.copy();

            /* Override stored position with start pose (in case snapshot was stale) */
            net.minecraft.nbt.NBTTagList posList = new net.minecraft.nbt.NBTTagList();
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(this.startX));
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(this.startY));
            posList.appendTag(new net.minecraft.nbt.NBTTagDouble(this.startZ));
            tag.setTag("Pos", posList);

            net.minecraft.nbt.NBTTagList rotList = new net.minecraft.nbt.NBTTagList();
            rotList.appendTag(new net.minecraft.nbt.NBTTagFloat(this.startYaw));
            rotList.appendTag(new net.minecraft.nbt.NBTTagFloat(this.startPitch));
            tag.setTag("Rotation", rotList);

            Entity spawned = EntityList.createEntityFromNBT(tag, world);
            if (spawned == null) return null;

            /* Keep the original UUID so subsequent control actions find this entity */
            spawned.setUniqueId(this.target);
            spawned.setPositionAndRotation(this.startX, this.startY, this.startZ, this.startYaw, this.startPitch);
            world.spawnEntity(spawned);
            return spawned;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private void teleportVehicle(Entity vehicle)
    {
        if (vehicle == null) return;

        /* Remove any passengers first so they don't get stranded at the vehicle's
         * old position when we teleport (passenger positions only sync on the
         * vehicle's next onUpdate tick, which can leave them floating). */
        if (!vehicle.getPassengers().isEmpty())
        {
            vehicle.removePassengers();
        }

        vehicle.setLocationAndAngles(this.startX, this.startY, this.startZ, this.startYaw, this.startPitch);
        vehicle.prevPosX = vehicle.lastTickPosX = this.startX;
        vehicle.prevPosY = vehicle.lastTickPosY = this.startY;
        vehicle.prevPosZ = vehicle.lastTickPosZ = this.startZ;
        vehicle.prevRotationYaw = this.startYaw;
        vehicle.prevRotationPitch = this.startPitch;
        vehicle.motionX = 0;
        vehicle.motionY = 0;
        vehicle.motionZ = 0;
    }

    /**
     * Called when playback stops/starts/loops — scans the record for
     * VehicleMountActions and restores each vehicle:
     * - Teleports it back to its recorded start pose
     * - Respawns it from NBT snapshot if it was destroyed
     * - Stops the engine (controls=0) so it comes to rest
     */
    public static void resetVehicles(Record record, World world)
    {
        if (record == null || world == null || world.isRemote) return;
        if (record.actions == null) return;

        for (List<Action> tickActions : record.actions)
        {
            if (tickActions == null) continue;

            for (Action action : tickActions)
            {
                if (!(action instanceof VehicleMountAction)) continue;
                VehicleMountAction mount = (VehicleMountAction) action;
                if (!mount.hasStartPose) continue;
                if (!mount.isMounting) continue; /* only first mount carries snapshot */

                Entity vehicle = mount.ensureVehicle(world);
                if (DynamXCompat.isVehicle(vehicle))
                {
                    mount.teleportVehicle(vehicle);
                    DynamXCompat.setVehicleControls(vehicle, 0);
                    vehicle.setDead();
                }
            }
        }
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.seatIndex = buf.readInt();
        this.hasStartPose = buf.readBoolean();
        if (this.hasStartPose)
        {
            this.startX = buf.readDouble();
            this.startY = buf.readDouble();
            this.startZ = buf.readDouble();
            this.startYaw = buf.readFloat();
            this.startPitch = buf.readFloat();
        }
        buf.readBoolean(); /* snapshot not sent over network */
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeInt(this.seatIndex);
        buf.writeBoolean(this.hasStartPose);
        if (this.hasStartPose)
        {
            buf.writeDouble(this.startX);
            buf.writeDouble(this.startY);
            buf.writeDouble(this.startZ);
            buf.writeFloat(this.startYaw);
            buf.writeFloat(this.startPitch);
        }
        buf.writeBoolean(false); /* snapshot is disk-only; too large for network packets */
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        super.fromNBT(tag);
        this.seatIndex = tag.getInteger("Seat");
        if (tag.hasKey("StartX"))
        {
            this.startX = tag.getDouble("StartX");
            this.startY = tag.getDouble("StartY");
            this.startZ = tag.getDouble("StartZ");
            this.startYaw = tag.getFloat("StartYaw");
            this.startPitch = tag.getFloat("StartPitch");
            this.hasStartPose = true;
        }
        if (tag.hasKey("Snapshot"))
        {
            this.vehicleSnapshot = tag.getCompoundTag("Snapshot");
        }
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        super.toNBT(tag);
        tag.setInteger("Seat", this.seatIndex);
        if (this.hasStartPose)
        {
            tag.setDouble("StartX", this.startX);
            tag.setDouble("StartY", this.startY);
            tag.setDouble("StartZ", this.startZ);
            tag.setFloat("StartYaw", this.startYaw);
            tag.setFloat("StartPitch", this.startPitch);
        }
        if (this.vehicleSnapshot != null)
        {
            tag.setTag("Snapshot", this.vehicleSnapshot);
        }
    }

    /**
     * Is this actor one of Blockbuster's fake players (as opposed to a real
     * player being replayed)?
     */
    private static boolean isFakePlayer(EntityLivingBase actor)
    {
        if (!(actor instanceof net.minecraft.entity.player.EntityPlayer)) return false;

        try
        {
            mchorse.blockbuster.capabilities.recording.IRecording recording =
                    mchorse.blockbuster.capabilities.recording.Recording.get((net.minecraft.entity.player.EntityPlayer) actor);

            return recording != null && recording.isFakePlayer();
        }
        catch (Throwable ignored) {}

        return false;
    }
}
