package mchorse.blockbuster.common.entity;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.block.BlockModel;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.common.tileentity.TileEntityModelSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.List;

/**
 * Unsichtbarer Sitz eines Modellblocks.
 *
 * <p>Der Modellblock selbst kann keinen Reiter tragen - in Minecraft sitzt man immer
 * auf einer Entity. Diese hier ist nichts weiter als ein Aufhaengepunkt: kein Modell,
 * kein Renderer (der RenderManager ueberspringt Entities ohne registrierten Renderer),
 * keine Hitbox, keine Schwerkraft, nicht anklickbar und nicht speicherbar. Sie
 * entsteht beim Hinsetzen und verschwindet, sobald niemand mehr draufsitzt.</p>
 *
 * <p>Position und Blickrichtung werden <b>jeden Tick</b> neu aus den Blockeinstellungen
 * gelesen. Das ist Absicht: man setzt sich hin und schiebt die Regler, bis es passt,
 * statt nach jeder Aenderung neu aufstehen zu muessen.</p>
 */
public class EntitySeat extends Entity
{
    /** Der Modellblock, zu dem dieser Sitz gehoert. */
    private BlockPos block = BlockPos.ORIGIN;

    /** Wird die Koerperrichtung des Sitzenden festgehalten? */
    private boolean forceYaw;

    public EntitySeat(World world)
    {
        super(world);

        this.setSize(0.01F, 0.01F);
        this.noClip = true;
        this.preventEntitySpawning = false;
    }

    public void setup(BlockPos pos, TileEntityModelSettings settings)
    {
        this.block = pos.toImmutable();
        this.apply(settings);
    }

    /** Sitzhoehe, Versatz und erzwungene Richtung aus den Blockeinstellungen uebernehmen. */
    private void apply(TileEntityModelSettings settings)
    {
        this.forceYaw = settings.isSeatRotate();
        this.rotationYaw = this.prevRotationYaw = MathHelper.wrapDegrees(settings.getSeatYaw());

        this.setPosition(
            this.block.getX() + 0.5D + settings.getSeatX(),
            this.block.getY() + settings.getSeatY(),
            this.block.getZ() + 0.5D + settings.getSeatZ());
    }

    @Override
    protected void entityInit()
    {}

    @Override
    public void onUpdate()
    {
        super.onUpdate();

        if (this.world.isRemote)
        {
            return;
        }

        /* Niemand mehr drauf: der Sitz hat keinen Zweck mehr */
        if (!this.isBeingRidden())
        {
            this.setDead();

            return;
        }

        final TileEntity te = this.world.getTileEntity(this.block);

        /* Block abgebaut oder Sitz wieder abgeschaltet: aufstehen lassen */
        if (!(te instanceof TileEntityModel) || !((TileEntityModel) te).getSettings().isSeat())
        {
            this.removePassengers();
            this.setDead();

            return;
        }

        this.apply(((TileEntityModel) te).getSettings());
    }

    @Override
    public void updatePassenger(Entity passenger)
    {
        if (!this.isPassenger(passenger))
        {
            return;
        }

        /* Genau auf den Sitzpunkt, ohne den sonst ueblichen Reit-Versatz - damit die
         * Hoehe im Kontrollfeld auch wirklich die Hoehe ist, die man einstellt */
        passenger.setPosition(this.posX, this.posY, this.posZ);

        if (this.forceYaw)
        {
            this.applyYawToEntity(passenger);
        }
    }

    /**
     * Beine und Oberkoerper auf die Sitzrichtung festnageln; der Kopf darf sich in dem
     * Rahmen drehen, den Minecraft auch beim Boot zulaesst.
     */
    private void applyYawToEntity(Entity passenger)
    {
        passenger.setRenderYawOffset(this.rotationYaw);

        final float diff = MathHelper.wrapDegrees(passenger.rotationYaw - this.rotationYaw);
        final float clamped = MathHelper.clamp(diff, -105.0F, 105.0F);

        passenger.prevRotationYaw += clamped - diff;
        passenger.rotationYaw += clamped - diff;
        passenger.setRotationYawHead(passenger.rotationYaw);
    }

    @Override
    public void applyOrientationToEntity(Entity passenger)
    {
        if (this.forceYaw)
        {
            this.applyYawToEntity(passenger);
        }
    }

    @Override
    public double getMountedYOffset()
    {
        return 0.0D;
    }

    @Override
    public boolean shouldRiderSit()
    {
        return true;
    }

    @Override
    protected boolean canBeRidden(Entity entity)
    {
        return true;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean canBePushed()
    {
        return false;
    }

    /** Nicht in die Welt speichern: nach einem Neustart sitzt ohnehin niemand mehr. */
    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound)
    {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound)
    {
        this.block = new BlockPos(compound.getInteger("BlockX"), compound.getInteger("BlockY"), compound.getInteger("BlockZ"));
        this.forceYaw = compound.getBoolean("ForceYaw");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound)
    {
        compound.setInteger("BlockX", this.block.getX());
        compound.setInteger("BlockY", this.block.getY());
        compound.setInteger("BlockZ", this.block.getZ());
        compound.setBoolean("ForceYaw", this.forceYaw);
    }

    /**
     * Hinsetzen per Schleichen + Rechtsklick.
     *
     * <p>Warum ueber dieses Event und nicht ueber {@code onBlockActivated}: Minecraft
     * ruft die Blockaktivierung beim Schleichen gar nicht erst auf, wenn etwas in der
     * Hand liegt - man wuerde stattdessen den Block platzieren. Das Event kommt vorher
     * und laesst sich abbrechen, also funktioniert das Hinsetzen auch mit vollen
     * Haenden.</p>
     */
    @Mod.EventBusSubscriber(modid = Blockbuster.MOD_ID)
    public static class Handler
    {
        /**
         * Registrierung ueber das Registry-Event statt ueber den CommonProxy: dessen
         * Klasse im Basis-JAR ist aelter als dieser Quellbaum, sie mitzuersetzen wuerde
         * halb Blockbuster nachziehen. Netz-ID bewusst hoch, damit sie nicht mit den
         * fortlaufenden IDs der bestehenden Entities kollidiert.
         */
        @SubscribeEvent
        public static void onRegisterEntities(RegistryEvent.Register<EntityEntry> event)
        {
            event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntitySeat.class)
                .id(new ResourceLocation(Blockbuster.MOD_ID, "seat"), 200)
                .name("blockbuster.Seat")
                .tracker(64, 20, false)
                .build());
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
        {
            final EntityPlayer player = event.getEntityPlayer();

            if (player == null || !player.isSneaking() || player.isRiding())
            {
                return;
            }

            final World world = event.getWorld();
            final BlockPos pos = event.getPos();

            if (!(world.getBlockState(pos).getBlock() instanceof BlockModel))
            {
                return;
            }

            final TileEntity te = world.getTileEntity(pos);

            if (!(te instanceof TileEntityModel))
            {
                return;
            }

            final TileEntityModelSettings settings = ((TileEntityModel) te).getSettings();

            if (!settings.isSeat())
            {
                return;
            }

            /* Auf beiden Seiten abbrechen, sonst platziert der Client kurz einen
             * Geisterblock, den der Server danach wieder zurueckholt */
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);

            if (world.isRemote)
            {
                return;
            }

            final List<EntitySeat> seats = world.getEntitiesWithinAABB(EntitySeat.class, new AxisAlignedBB(pos).grow(2.0D));

            for (EntitySeat seat : seats)
            {
                if (seat.block.equals(pos))
                {
                    /* Schon besetzt - kein zweiter Spieler auf denselben Sitz */
                    if (!seat.isBeingRidden())
                    {
                        player.startRiding(seat);
                    }

                    return;
                }
            }

            final EntitySeat seat = new EntitySeat(world);

            seat.setup(pos, settings);
            world.spawnEntity(seat);
            player.startRiding(seat);
        }
    }
}
