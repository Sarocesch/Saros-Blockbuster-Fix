package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.mwf.PacketMWFFireReplay;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Cosmetic-only replay of a ModularWarfare (MWF) weapon fire.
 *
 * Recorded when a player fires an MWF weapon while Blockbuster is recording
 * (captured via WeaponFireEvent.Pre). On playback the actor only emits the
 * {@code PacketOtherShooterAnimation} — animation + sound + muzzle flash
 * visible on all clients. No damage, no ammo consumption, no server-side
 * fire validation. This means:
 * <ul>
 *   <li>No server crash from re-validating a historical fire</li>
 *   <li>No desync of ammo count / inventory</li>
 *   <li>No double-kills or phantom damage on replay</li>
 * </ul>
 * If MWF is not installed, apply() silently no-ops.
 */
public class MWFFireAction extends Action
{
    /** Weapon's internal registry name (e.g. "ak47_default"). */
    public String internalName = "";

    /** Fire tick delay from {@code GunType.fireTickDelay}. */
    public int fireTickDelay;

    public MWFFireAction()
    {}

    public MWFFireAction(String internalName, int fireTickDelay)
    {
        this.internalName = internalName == null ? "" : internalName;
        this.fireTickDelay = fireTickDelay;
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        if (actor == null || actor.world.isRemote) return;
        if (this.internalName.isEmpty()) return;
        if (!MWFCompat.isAvailable()) return;
        /* Use a Blockbuster packet that looks up by entity ID so EntityActor is
         * found correctly — MWF's own PacketOtherShooterAnimation uses
         * getPlayerEntityByUUID which returns null for non-player entities. */
        Dispatcher.sendToTracked(actor,
                new PacketMWFFireReplay(actor.getEntityId(), this.internalName, this.fireTickDelay));

        /* The animation packet only moves the model. MWF plays the shot from
         * FireManager, which an actor never runs, so the sound has to be issued
         * here - server side, exactly like MWF does it. */
        MWFCompat.playFireSound(actor, this.internalName, actor.getHeldItemMainhand());
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.internalName = ByteBufUtils.readUTF8String(buf);
        this.fireTickDelay = buf.readInt();
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        ByteBufUtils.writeUTF8String(buf, this.internalName);
        buf.writeInt(this.fireTickDelay);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.internalName = tag.getString("Gun");
        this.fireTickDelay = tag.getInteger("Delay");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setString("Gun", this.internalName);
        tag.setInteger("Delay", this.fireTickDelay);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
