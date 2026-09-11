package mchorse.blockbuster.recording.data;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.scene.Replay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

/**
 * Recording frame class
 *
 * This class stores state data about the player in the specific frame that was
 * captured.
 */
public class Frame
{
    public static DataParameter<Byte> FLAGS;

    /* Position */
    public double x;
    public double y;
    public double z;

    /* Rotation */
    public float yaw;
    public float yawHead;
    public float pitch;

    public boolean hasBodyYaw;
    public float bodyYaw;

    /* Mount's data */
    public float mountYaw;
    public float mountPitch;

    /* Transform eines DynamX-Fahrzeugs, RELATIV gespeichert: vx/vy/vz sind der
     * Versatz zu x/y/z, vq* die Rotation ohne die Gier yaw. So ziehen /record
     * origin, /record flip und jedes Werkzeug, das x/y/z und yaw bearbeitet, das
     * Auto automatisch mit. Nur gesetzt, wenn beim Aufnehmen in einem
     * DynamX-Fahrzeug gesessen wurde.
     * Damit faehrt das Auto beim Abspielen exakt die aufgenommene Bahn, statt
     * dass die Physik aus den Steuertasten eine eigene Linie faehrt. */
    public boolean hasVehicle;
    public float vx;
    public float vy;
    public float vz;
    public float vqx;
    public float vqy;
    public float vqz;
    public float vqw = 1F;

    public boolean isMounted;

    /* Motion */
    public double motionX;
    public double motionY;
    public double motionZ;

    /* Fall distance */
    public float fallDistance;

    /* Entity flags */
    public boolean isAirBorne;
    public boolean isSneaking;
    public boolean isSprinting;
    public boolean onGround;
    public boolean flyingElytra;

    /* Client data */
    public float roll;

    /* Active hand */
    public int activeHands;

    private int hotbarSlot;
    private int foodLevel;
    private int totalExperience;

    /* Methods for retrieving/applying state data */

    /**
     * Set frame fields from player entity.
     */
    public void fromPlayer(EntityPlayer player)
    {
        Entity mount = player.isRiding() ? player.getRidingEntity() : player;

        /* Position and rotation */
        this.x = mount.posX;
        this.y = player.isRiding() && player.getRidingEntity().posY > player.posY ? player.posY : mount.posY;
        this.z = mount.posZ;

        this.yaw = player.rotationYaw;
        this.yawHead = player.rotationYawHead;
        this.pitch = player.rotationPitch;

        this.hasBodyYaw = true;
        this.bodyYaw = player.renderYawOffset;

        /* Mount information */
        this.isMounted = mount != player;

        if (this.isMounted)
        {
            this.mountYaw = mount.rotationYaw;
            this.mountPitch = mount.rotationPitch;
        }

        this.hasVehicle = false;

        if (this.isMounted && mchorse.blockbuster.recording.dynamx.DynamXCompat.isVehicle(mount))
        {
            float[] transform = mchorse.blockbuster.recording.dynamx.DynamXVehiclePin.readTransform(mount);

            if (transform != null)
            {
                float[] q = yawTimes(-this.yaw, transform[3], transform[4], transform[5], transform[6]);

                this.hasVehicle = true;
                this.vx = (float) (transform[0] - this.x);
                this.vy = (float) (transform[1] - this.y);
                this.vz = (float) (transform[2] - this.z);
                this.vqx = q[0];
                this.vqy = q[1];
                this.vqz = q[2];
                this.vqw = q[3];
            }
        }

        /* Motion and fall distance */
        this.motionX = mount.motionX;
        this.motionY = mount.motionY;
        this.motionZ = mount.motionZ;

        this.fallDistance = mount.fallDistance;

        /* States */
        this.isSprinting = mount.isSprinting();
        this.isSneaking = player.isSneaking();
        this.flyingElytra = player.isElytraFlying();

        this.isAirBorne = mount.isAirBorne;
        this.onGround = mount.onGround;

        /* Active hands */
        this.activeHands = player.isHandActive() ? (player.getActiveHand() == EnumHand.OFF_HAND ? 2 : 1) : 0;

        if (player.world.isRemote)
        {
            this.fromPlayerClient(player);
        }

        this.hotbarSlot = player.inventory.currentItem;
        this.foodLevel = player.getFoodStats().getFoodLevel();
        this.totalExperience = player.experienceTotal;
    }

    @SideOnly(Side.CLIENT)
    private void fromPlayerClient(EntityPlayer player)
    {
        EntityPlayerSP local = Minecraft.getMinecraft().player;

        if (player == local)
        {
            this.roll = CameraHandler.getRoll();
        }
    }

    /**
     * Apply frame properties on actor. Different actions will be made
     * depending on which side this method was invoked.
     *
     * Use second argument to force things to be cool.
     */
    public void apply(EntityLivingBase actor, boolean force)
    {
        this.apply(actor, null, force);
    }

    /**
     *
     * @param actor
     * @param replay the replay that is being used for this record - used for playback configuration
     * @param force
     */
    public void apply(EntityLivingBase actor, @Nullable Replay replay, boolean force)
    {
        boolean isRemote = actor.world.isRemote;

        Entity mount = actor.isRiding() ? actor.getRidingEntity() : actor;

        if (mount instanceof EntityActor)
        {
            mount = actor;
        }

        if (actor instanceof EntityActor)
        {
            EntityActor theActor = (EntityActor) actor;

            theActor.isMounted = this.isMounted;
            theActor.roll = this.roll;
        }

        /* This is most important part of the code that makes the recording
         * super smooth.
         *
         * By the way, this code is useful only on the client side, for more
         * reference see renderer classes (they use prev* and lastTick* stuff
         * for interpolation).
         */
        if (this.isMounted)
        {
            mount.prevRotationYaw = mount.rotationYaw;
            mount.prevRotationPitch = mount.rotationPitch;
        }

        actor.prevRotationYaw = actor.rotationYaw;
        actor.prevRotationPitch = actor.rotationPitch;
        actor.prevRotationYawHead = actor.rotationYawHead;

        /* Inject frame's values into actor */
        if (!isRemote || force)
        {
            /* Einem DynamX-Fahrzeug NICHT die aufgezeichnete Position
             * aufzwingen. Es rechnet seine eigene Physik, und die
             * abgespielten Lenkbefehle fahren es ohnehin - beides zusammen
             * riss es jeden Tick hin und her, sichtbar als starkes Zittern in
             * den ersten Sekunden. Der Actor selbst wird weiter gesetzt, der
             * sitzt dann sauber auf dem Sitz. */
            if (mount == actor || !mchorse.blockbuster.recording.dynamx.DynamXCompat.isVehicle(mount))
            {
                mount.setPosition(this.x, this.y, this.z);
            }
        }

        /* DynamX-Fahrzeug kinematisch auf die aufgenommene Bahn, auf Server UND
         * Client. Clients uebernehmen Serverpositionen nur bei aktivem Koerper, ein
         * kinematischer ist das nicht - deshalb nagelt jede Seite ihr eigenes
         * Exemplar fest. Jeder Client spielt die Aufnahme ohnehin selbst mit. */
        if (mchorse.blockbuster.recording.dynamx.DynamXCompat.isAvailable())
        {
            if (this.isMounted && mount != actor && mchorse.blockbuster.recording.dynamx.DynamXCompat.isVehicle(mount))
            {
                if (this.hasVehicle)
                {
                    float[] q = yawTimes(this.yaw, this.vqx, this.vqy, this.vqz, this.vqw);

                    mchorse.blockbuster.recording.dynamx.DynamXVehiclePin.pin(actor, mount,
                        (float) (this.x + this.vx), (float) (this.y + this.vy), (float) (this.z + this.vz),
                        q[0], q[1], q[2], q[3]);
                }
                else
                {
                    /* Alte Aufnahme ohne Transform: x/y/z sind schon die
                     * Fahrzeugposition, Rotation aus Gier und Neigung. */
                    mchorse.blockbuster.recording.dynamx.DynamXVehiclePin.pinFromYawPitch(actor, mount,
                        (float) this.x, (float) this.y, (float) this.z, this.mountYaw, this.mountPitch);
                }
            }
            else
            {
                mchorse.blockbuster.recording.dynamx.DynamXVehiclePin.release(actor);
            }
        }

        /* Rotation */
        if (isRemote || force)
        {
            if (this.isMounted)
            {
                mount.rotationYaw = this.mountYaw;
                mount.rotationPitch = this.mountPitch;

                if (actor == mount)
                {
                    actor.setPosition(this.x, this.y, this.z);
                }
            }

            actor.rotationYaw = this.yaw;
            actor.rotationPitch = this.pitch;
            actor.rotationYawHead = this.yawHead;
        }

        /* Motion and fall distance */
        mount.motionX = this.motionX;
        mount.motionY = this.motionY;
        mount.motionZ = this.motionZ;

        mount.fallDistance = this.fallDistance;

        /* Booleans */
        if (!isRemote || force)
        {
            mount.setSprinting(this.isSprinting);
            actor.setSneaking(this.isSneaking);

            this.setFlag(actor, 7, this.flyingElytra);
        }

        mount.isAirBorne = this.isAirBorne;
        mount.onGround = this.onGround;

        if (!isRemote)
        {
            if (this.activeHands > 0 && !actor.isHandActive())
            {
                actor.setActiveHand(this.activeHands == 1 ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND);
            }
            else if (this.activeHands == 0 && actor.isHandActive())
            {
                actor.stopActiveHand();
            }
        }

        if (actor instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) actor;
            player.inventory.currentItem = this.hotbarSlot;

            if (replay != null && replay.playBackXPFood)
            {
                player.getFoodStats().setFoodLevel(this.foodLevel);
                player.addExperience(this.totalExperience - player.experienceTotal);
            }
        }
    }

    /**
     * Set entity flags... if only vanilla could expose that shit 
     */
    private void setFlag(EntityLivingBase actor, int i, boolean flag)
    {
        if (FLAGS == null)
        {
            Field field = null;

            for (Field f : Entity.class.getDeclaredFields())
            {
                int mod = f.getModifiers();
                Type type = f.getGenericType();

                if (Modifier.isProtected(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod) && f.getType() == DataParameter.class)
                {
                    field = f;
                    break;
                }
            }

            if (field != null)
            {
                try
                {
                    field.setAccessible(true);
                    FLAGS = (DataParameter<Byte>) field.get(null);
                }
                catch (Exception e)
                {}
            }
        }

        if (FLAGS != null)
        {
            byte flags = actor.getDataManager().get(FLAGS).byteValue();

            actor.getDataManager().set(FLAGS, (byte) (flag ? flags | (1 << i) : flags & ~(1 << i)));
        }
    }

    /**
     * Create a copy of this frame 
     */
    public Frame copy()
    {
        Frame frame = new Frame();

        frame.x = this.x;
        frame.y = this.y;
        frame.z = this.z;

        frame.yaw = this.yaw;
        frame.yawHead = this.yawHead;
        frame.pitch = this.pitch;

        frame.hasBodyYaw = this.hasBodyYaw;
        frame.bodyYaw = this.bodyYaw;

        frame.isMounted = this.isMounted;

        if (frame.isMounted)
        {
            frame.mountYaw = this.mountYaw;
            frame.mountPitch = this.mountPitch;
        }

        frame.hasVehicle = this.hasVehicle;
        frame.vx = this.vx;
        frame.vy = this.vy;
        frame.vz = this.vz;
        frame.vqx = this.vqx;
        frame.vqy = this.vqy;
        frame.vqz = this.vqz;
        frame.vqw = this.vqw;

        frame.motionX = this.motionX;
        frame.motionY = this.motionY;
        frame.motionZ = this.motionZ;

        frame.fallDistance = this.fallDistance;

        frame.isAirBorne = this.isAirBorne;
        frame.isSneaking = this.isSneaking;
        frame.isSprinting = this.isSprinting;
        frame.onGround = this.onGround;
        frame.flyingElytra = this.flyingElytra;

        frame.activeHands = this.activeHands;

        frame.roll = this.roll;

        frame.hotbarSlot = this.hotbarSlot;
        frame.foodLevel = this.foodLevel;
        frame.totalExperience = this.totalExperience;

        return frame;
    }

    /* Save/load frame instance */
    public void toBytes(ByteBuf buf)
    {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);

        buf.writeFloat(this.yaw);
        buf.writeFloat(this.yawHead);
        buf.writeFloat(this.pitch);

        buf.writeBoolean(this.hasBodyYaw);

        if (this.hasBodyYaw)
        {
            buf.writeFloat(this.bodyYaw);
        }

        buf.writeBoolean(this.isMounted);

        if (this.isMounted)
        {
            buf.writeFloat(this.mountYaw);
            buf.writeFloat(this.mountPitch);
        }

        buf.writeFloat((float) this.motionX);
        buf.writeFloat((float) this.motionY);
        buf.writeFloat((float) this.motionZ);

        buf.writeFloat(this.fallDistance);

        buf.writeBoolean(this.isAirBorne);
        buf.writeBoolean(this.isSneaking);
        buf.writeBoolean(this.isSprinting);
        buf.writeBoolean(this.onGround);
        buf.writeBoolean(this.flyingElytra);

        buf.writeByte(this.activeHands);

        buf.writeFloat(this.roll);
        
        buf.writeInt(this.hotbarSlot);
        buf.writeInt(this.foodLevel);
        buf.writeInt(this.totalExperience);

        buf.writeBoolean(this.hasVehicle);

        if (this.hasVehicle)
        {
            buf.writeFloat(this.vx);
            buf.writeFloat(this.vy);
            buf.writeFloat(this.vz);
            buf.writeFloat(this.vqx);
            buf.writeFloat(this.vqy);
            buf.writeFloat(this.vqz);
            buf.writeFloat(this.vqw);
        }
    }

    public void fromBytes(ByteBuf buf)
    {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();

        this.yaw = buf.readFloat();
        this.yawHead = buf.readFloat();
        this.pitch = buf.readFloat();

        if (buf.readBoolean())
        {
            this.hasBodyYaw = true;
            this.bodyYaw = buf.readFloat();
        }

        this.isMounted = buf.readBoolean();

        if (this.isMounted)
        {
            this.mountYaw = buf.readFloat();
            this.mountPitch = buf.readFloat();
        }

        this.motionX = buf.readFloat();
        this.motionY = buf.readFloat();
        this.motionZ = buf.readFloat();

        this.fallDistance = buf.readFloat();

        this.isAirBorne = buf.readBoolean();
        this.isSneaking = buf.readBoolean();
        this.isSprinting = buf.readBoolean();
        this.onGround = buf.readBoolean();
        this.flyingElytra = buf.readBoolean();

        this.activeHands = buf.readByte();

        this.roll = buf.readFloat();
        this.hotbarSlot = buf.readInt();
        this.foodLevel = buf.readInt();
        this.totalExperience = buf.readInt();

        this.hasVehicle = buf.readBoolean();

        if (this.hasVehicle)
        {
            this.vx = buf.readFloat();
            this.vy = buf.readFloat();
            this.vz = buf.readFloat();
            this.vqx = buf.readFloat();
            this.vqy = buf.readFloat();
            this.vqz = buf.readFloat();
            this.vqw = buf.readFloat();
        }
    }

    /**
     * Write frame data to NBT tag. Used for saving the frame on the disk.
     *
     * This is probably going to be extracted in the future to support
     * compatibility, but I don't really know since writing the data happens
     * in one format, while reading is in different versions.
     */
    public void toNBT(NBTTagCompound tag)
    {
        tag.setDouble("X", this.x);
        tag.setDouble("Y", this.y);
        tag.setDouble("Z", this.z);

        tag.setFloat("MX", (float) this.motionX);
        tag.setFloat("MY", (float) this.motionX);
        tag.setFloat("MZ", (float) this.motionX);

        tag.setFloat("RX", this.yaw);
        tag.setFloat("RY", this.pitch);
        tag.setFloat("RZ", this.yawHead);

        if (this.hasBodyYaw)
        {
            tag.setFloat("RW", this.bodyYaw);
        }

        if (this.isMounted)
        {
            tag.setFloat("MRX", this.mountYaw);
            tag.setFloat("MRY", this.mountPitch);
        }

        tag.setFloat("Fall", this.fallDistance);

        tag.setBoolean("Airborne", this.isAirBorne);
        tag.setBoolean("Elytra", this.flyingElytra);
        tag.setBoolean("Sneaking", this.isSneaking);
        tag.setBoolean("Sprinting", this.isSprinting);
        tag.setBoolean("Ground", this.onGround);

        if (this.activeHands > 0)
        {
            tag.setByte("Hands", (byte) this.activeHands);
        }

        if (this.roll != 0)
        {
            tag.setFloat("Roll", this.roll);
        }

        tag.setInteger("HotbarSlot", this.hotbarSlot);
        tag.setInteger("FoodLevel", this.foodLevel);
        tag.setInteger("TotalExperience", this.totalExperience);

        if (this.hasVehicle)
        {
            NBTTagCompound vehicle = new NBTTagCompound();

            vehicle.setFloat("X", this.vx);
            vehicle.setFloat("Y", this.vy);
            vehicle.setFloat("Z", this.vz);
            vehicle.setFloat("QX", this.vqx);
            vehicle.setFloat("QY", this.vqy);
            vehicle.setFloat("QZ", this.vqz);
            vehicle.setFloat("QW", this.vqw);
            tag.setTag("Veh", vehicle);
        }
    }

    /**
     * Read frame data from NBT tag. Used for loading frame from disk.
     *
     * This is going to be extracted in the future to support compatibility.
     */
    public void fromNBT(NBTTagCompound tag)
    {
        this.x = tag.getDouble("X");
        this.y = tag.getDouble("Y");
        this.z = tag.getDouble("Z");

        this.motionX = tag.getFloat("MX");
        this.motionY = tag.getFloat("MY");
        this.motionZ = tag.getFloat("MZ");

        this.yaw = tag.getFloat("RX");
        this.pitch = tag.getFloat("RY");
        this.yawHead = tag.getFloat("RZ");

        if (tag.hasKey("RW"))
        {
            this.hasBodyYaw = true;
            this.bodyYaw = tag.getFloat("RW");
        }

        if (tag.hasKey("MRX") && tag.hasKey("MRY"))
        {
            this.isMounted = true;
            this.mountYaw = tag.getFloat("MRX");
            this.mountPitch = tag.getFloat("MRY");
        }

        this.fallDistance = tag.getFloat("Fall");

        this.isAirBorne = tag.getBoolean("Airborne");
        this.flyingElytra = tag.getBoolean("Elytra");
        this.isSneaking = tag.getBoolean("Sneaking");
        this.isSprinting = tag.getBoolean("Sprinting");
        this.onGround = tag.getBoolean("Ground");

        if (tag.hasKey("Hands"))
        {
            this.activeHands = tag.getByte("Hands");
        }

        if (tag.hasKey("Roll"))
        {
            this.roll = tag.getFloat("Roll");
        }

        this.hotbarSlot = tag.hasKey("HotbarSlot") ? tag.getInteger("HotbarSlot") : this.hotbarSlot;
        this.foodLevel = tag.hasKey("FoodLevel") ? tag.getInteger("FoodLevel") : this.foodLevel;
        this.totalExperience = tag.hasKey("TotalExperience") ? tag.getInteger("TotalExperience") : this.totalExperience;

        if (tag.hasKey("Veh"))
        {
            NBTTagCompound vehicle = tag.getCompoundTag("Veh");

            this.hasVehicle = true;
            this.vx = vehicle.getFloat("X");
            this.vy = vehicle.getFloat("Y");
            this.vz = vehicle.getFloat("Z");
            this.vqx = vehicle.getFloat("QX");
            this.vqy = vehicle.getFloat("QY");
            this.vqz = vehicle.getFloat("QZ");
            this.vqw = vehicle.getFloat("QW");
        }
    }

    /**
     * Gier-Drehung von links an eine Quaternion multiplizieren, gleichsinnig mit
     * yaw += degrees. DynamX rechnet Gier als Drehung um die Hochachse mit -yaw
     * (DynamXGeometry.eulerToQuaternion).
     */
    private static float[] yawTimes(double degrees, float qx, float qy, float qz, float qw)
    {
        double half = Math.toRadians(-degrees) / 2;
        float ry = (float) Math.sin(half);
        float rw = (float) Math.cos(half);

        return new float[] {rw * qx + ry * qz, rw * qy + ry * qw, rw * qz - ry * qx, rw * qw - ry * qy};
    }

    public enum RotationChannel
    {
        HEAD_YAW,
        HEAD_PITCH,
        BODY_YAW
    }
}