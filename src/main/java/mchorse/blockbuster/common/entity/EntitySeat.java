package mchorse.blockbuster.common.entity;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.block.BlockModel;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.common.tileentity.TileEntityModelSettings;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketSitOnModelBlock;
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
public class EntitySeat extends Entity implements net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData
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

        final TileEntity te = this.world.getTileEntity(this.block);
        final boolean valid = te instanceof TileEntityModel && ((TileEntityModel) te).getSettings().isSeat();

        /*
         * Auch auf dem Client nachziehen. Die Sperre der Koerperrichtung wirkt nur
         * dort, wo der Spieler gezeichnet wird - laeuft das hier nur auf dem Server,
         * steht die Richtung beim Betrachter auf 0 und die Sperre tut sichtbar nichts.
         * Beide Seiten lesen dieselbe TileEntity, es kann also nicht auseinanderlaufen.
         */
        if (valid)
        {
            this.apply(((TileEntityModel) te).getSettings());
        }

        if (this.world.isRemote)
        {
            return;
        }

        /* Block abgebaut oder Sitz wieder abgeschaltet: aufstehen lassen */
        if (!valid)
        {
            this.removePassengers();
            this.setDead();

            return;
        }

        /* Niemand mehr drauf: der Sitz hat keinen Zweck mehr */
        if (!this.isBeingRidden())
        {
            this.setDead();
        }
    }

    /*
     * Blockposition und Sitzrichtung beim Spawnen mitschicken. Ohne das kennt die
     * Kopie auf dem Client ihren Block nicht (BlockPos.ORIGIN) und koennte die
     * Einstellungen gar nicht erst nachlesen.
     */
    @Override
    public void writeSpawnData(ByteBuf buf)
    {
        buf.writeInt(this.block.getX());
        buf.writeInt(this.block.getY());
        buf.writeInt(this.block.getZ());
        buf.writeBoolean(this.forceYaw);
        buf.writeFloat(this.rotationYaw);
    }

    @Override
    public void readSpawnData(ByteBuf buf)
    {
        this.block = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.forceYaw = buf.readBoolean();
        this.rotationYaw = this.prevRotationYaw = buf.readFloat();
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
     * Setzt den Spieler auf den Sitz des Blocks. Legt ihn an, wenn es noch keinen gibt,
     * und setzt niemanden auf einen bereits besetzten Sitz.
     */
    public static void sit(EntityPlayer player, BlockPos pos, TileEntityModelSettings settings)
    {
        final World world = player.world;
        final List<EntitySeat> seats = world.getEntitiesWithinAABB(EntitySeat.class, new AxisAlignedBB(pos).grow(2.0D));

        for (EntitySeat seat : seats)
        {
            if (seat.block.equals(pos))
            {
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

    /** Registrierung - laeuft auf beiden Seiten. */
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

        /**
         * Eigener Aussteigepunkt.
         *
         * <p>Minecraft sucht sich den Platz zum Aufstehen selbst, und bei einem Sitz,
         * der frei in der Luft oder auf einem Modell steht, landet man damit gern mal
         * im Boden. Steht im Block ein Ausstieg, wird der stattdessen genommen.</p>
         *
         * <p>Der Sprung muss einen Tick warten: {@code EntityLivingBase} sucht sich
         * seinen Platz erst NACH diesem Event, ein sofortiges Setzen wuerde also
         * gleich wieder ueberschrieben.</p>
         */
        @SubscribeEvent
        public static void onDismount(net.minecraftforge.event.entity.EntityMountEvent event)
        {
            if (!event.isDismounting() || !(event.getEntityBeingMounted() instanceof EntitySeat))
            {
                return;
            }

            final World world = event.getWorldObj();

            if (world == null || world.isRemote)
            {
                return;
            }

            final EntitySeat seat = (EntitySeat) event.getEntityBeingMounted();
            final TileEntity te = world.getTileEntity(seat.block);

            if (!(te instanceof TileEntityModel))
            {
                return;
            }

            final TileEntityModelSettings settings = ((TileEntityModel) te).getSettings();

            if (!settings.hasSeatExit())
            {
                return;
            }

            final net.minecraft.server.MinecraftServer server = world.getMinecraftServer();

            if (server == null)
            {
                return;
            }

            final Entity rider = event.getEntityMounting();
            final double x = seat.block.getX() + 0.5D + settings.getSeatExitX();
            final double y = seat.block.getY() + settings.getSeatExitY();
            final double z = seat.block.getZ() + 0.5D + settings.getSeatExitZ();

            server.addScheduledTask(new Runnable()
            {
                @Override
                public void run()
                {
                    rider.setPositionAndUpdate(x, y, z);
                }
            });
        }

    }

    /**
     * Client-only, damit ein dedizierter Server die Render-Klassen hier nie anfassen muss.
     */
    @Mod.EventBusSubscriber(modid = Blockbuster.MOD_ID, value = net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static class ClientHandler
    {
        /**
         * Beinrichtung festhalten.
         *
         * <p>{@code updatePassenger} allein reicht nicht: {@code EntityLivingBase}
         * rechnet {@code renderYawOffset} in seinem eigenen Tick aus der Blickrichtung
         * neu, und je nach Tick-Reihenfolge gewinnt mal das eine, mal das andere - die
         * Sperre wirkte dadurch gar nicht oder flackerte. Hier steht sie unmittelbar
         * vor dem Zeichnen, danach kommt nichts mehr dazwischen.</p>
         */
        @SubscribeEvent
        public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event)
        {
            final EntityPlayer player = event.getEntityPlayer();
            final Entity riding = player.getRidingEntity();

            if (!(riding instanceof EntitySeat))
            {
                return;
            }

            final EntitySeat seat = (EntitySeat) riding;

            if (!seat.forceYaw)
            {
                return;
            }

            player.renderYawOffset = player.prevRenderYawOffset = seat.rotationYaw;
        }

        /**
         * Rechtsklick auf einen Modellblock, <b>rein client-seitig</b> ausgewertet.
         *
         * <p>Strg oeffnet das Kontrollfeld (macht {@code BlockModel#onBlockActivated}
         * selbst), alles andere setzt hin. Diese Unterscheidung kann nur hier fallen:
         * der Server sieht Strg nicht, deshalb geht das Hinsetzen als eigenes Paket
         * raus - sonst wuerde Strg + Rechtsklick das Kontrollfeld oeffnen UND
         * hinsetzen.</p>
         *
         * <p>Warum ueber dieses Event und nicht ueber {@code onBlockActivated}:
         * Minecraft ruft die Blockaktivierung beim Schleichen gar nicht erst auf, wenn
         * etwas in der Hand liegt. Das Event kommt vorher und laesst sich abbrechen.</p>
         */
        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
        {
            final World world = event.getWorld();

            /* Nur der Client entscheidet - der Server bekommt das Paket */
            if (!world.isRemote)
            {
                return;
            }

            final EntityPlayer player = event.getEntityPlayer();

            if (player == null || player.isRiding())
            {
                return;
            }

            final BlockPos pos = event.getPos();

            if (!(world.getBlockState(pos).getBlock() instanceof BlockModel))
            {
                return;
            }

            final TileEntity te = world.getTileEntity(pos);

            if (!(te instanceof TileEntityModel) || !((TileEntityModel) te).getSettings().isSeat())
            {
                return;
            }

            /* Strg gehoert dem Kontrollfeld, das oeffnet der Block selbst */
            if (net.minecraft.client.gui.GuiScreen.isCtrlKeyDown())
            {
                return;
            }

            /* Abbrechen, damit kein Block in der Hand stattdessen gesetzt wird */
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);

            Dispatcher.sendToServer(new PacketSitOnModelBlock(pos));
        }
    }
}
