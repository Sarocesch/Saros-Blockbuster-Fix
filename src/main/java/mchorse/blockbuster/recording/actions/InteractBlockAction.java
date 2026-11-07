package mchorse.blockbuster.recording.actions;

import com.google.common.collect.ImmutableSet;
import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.block.BlockDirector;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.LTHelper;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * Interact block action
 *
 * Makes actor interact with a block (press button, switch lever, open the door,
 * etc.)
 *
 * If there was CL4P-TP actor in this mod, this action would be called
 * IntergradeBlockAction :D
 */
public class InteractBlockAction extends Action
{
    public static final Set<ResourceLocation> BLACKLIST = ImmutableSet.of(
        new ResourceLocation("littletiles:blocklittletiles")
    );

    public BlockPos pos = BlockPos.ORIGIN;

    public InteractBlockAction()
    {}

    public InteractBlockAction(BlockPos pos)
    {
        this.pos = pos;
    }

    /** Letzter Klick je Actor: Welt-Tick und Block. */
    private static final java.util.Map<EntityLivingBase, String> LAST_CLICK = new java.util.WeakHashMap<EntityLivingBase, String>();

    /**
     * Kein Block-Klick: MWF-Waffe in der Hand (Zielen haelt die rechte Taste und
     * klickte so alle 4 Ticks auf die Tuer) oder Schleichen mit etwas in der Hand
     * (bei echten Spielern oeffnet das keine Tuer).
     */
    public static boolean blocksClick(EntityLivingBase entity, boolean sneaking)
    {
        net.minecraft.item.ItemStack main = entity.getHeldItemMainhand();

        if (mchorse.blockbuster.recording.mwf.MWFCompat.getHoldKind(main) != mchorse.blockbuster.recording.mwf.MWFCompat.HOLD_NONE)
        {
            return true;
        }

        return sneaking && (!main.isEmpty() || !entity.getHeldItemOffhand().isEmpty());
    }

    @Override
    public void apply(EntityLivingBase actor)
    {
        /* Aeltere Aufnahmen haben jeden Klick doppelt (Haupt- und Nebenhand).
         * Zweimal im selben Tick schaltete eine Tuer auf und sofort wieder zu. */
        if (this.getClass() == InteractBlockAction.class)
        {
            String key = actor.world.getTotalWorldTime() + ":" + this.pos.toLong();

            if (key.equals(LAST_CLICK.put(actor, key)))
            {
                return;
            }
        }

        IBlockState state = actor.world.getBlockState(this.pos);

        /* Black listed block */
        if (state.getBlock() instanceof BlockDirector)
        {
            return;
        }

        Frame frame = EntityUtils.getRecordPlayer(actor).getCurrentFrame();
        EntityPlayer player = actor instanceof EntityActor ? ((EntityActor) actor).fakePlayer : (EntityPlayer) actor;

        if (frame == null) return;

        /* Auch fuer bestehende Aufnahmen: Klicks mit Waffe oder geschlichen mit
         * etwas in der Hand oeffneten keine Tuer und sollen es hier auch nicht. */
        if (this.getClass() == InteractBlockAction.class && blocksClick(actor, frame.isSneaking))
        {
            return;
        }

        if (player != actor)
        {
            this.copyActor(actor, player, frame);
        }

        /* LittleTiles nur fuer LittleTiles-Bloecke und Luft (offene Tuer, die
         * gerade als Animation unterwegs ist). Alles andere - Vanilla-Tueren,
         * Hebel, Knoepfe - laeuft wie immer: auf dem Server ist der
         * LittleTiles-Weg frueher ohnehin jedes Mal abgestuerzt, alte
         * Aufnahmen bekommen so exakt das bisherige Verhalten. */
        boolean littleTiles = BLACKLIST.contains(state.getBlock().getRegistryName());

        if (littleTiles || state.getBlock().isAir(state, actor.world, this.pos))
        {
            if (!LTHelper.playerRightClickServer(player, frame) && littleTiles)
            {
                LTHelper.playerRightClickAt(player, frame, this.pos);
            }

            return;
        }

        state.getBlock().onBlockActivated(actor.world, this.pos, state, player, EnumHand.MAIN_HAND, null, this.pos.getX(), this.pos.getY(), this.pos.getZ());
    }

    @Override
    public void changeOrigin(double rotation, double newX, double newY, double newZ, double firstX, double firstY, double firstZ)
    {
        /* I don't like wasting variables */
        firstX = this.pos.getX() - firstX;
        firstY = this.pos.getY() - firstY;
        firstZ = this.pos.getZ() - firstZ;

        if (rotation != 0)
        {
            float cos = (float) Math.cos(rotation / 180 * Math.PI);
            float sin = (float) Math.sin(rotation / 180 * Math.PI);

            double xx = firstX * cos - firstZ * sin;
            double zz = firstX * sin + firstZ * cos;

            firstX = xx;
            firstZ = zz;
        }

        newX += firstX;
        newY += firstY;
        newZ += firstZ;

        this.pos = new BlockPos(newX, newY, newZ);
    }

    @Override
    public void flip(String axis, double coordinate)
    {
        if (axis.equals("x"))
        {
            double diff = coordinate - this.pos.getX();

            this.pos = new BlockPos(coordinate + diff, this.pos.getY(), this.pos.getZ());
        }
        else
        {
            double diff = coordinate - this.pos.getZ();

            this.pos = new BlockPos(this.pos.getX(), this.pos.getY(), coordinate + diff);
        }
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.pos = new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setInteger("X", this.pos.getX());
        tag.setInteger("Y", this.pos.getY());
        tag.setInteger("Z", this.pos.getZ());
    }
}