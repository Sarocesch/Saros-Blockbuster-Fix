package mchorse.blockbuster.recording.capturing;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.client.SkinHandler;
import mchorse.blockbuster.client.render.tileentity.TileEntityGunItemStackRenderer;
import mchorse.blockbuster.client.render.tileentity.TileEntityGunItemStackRenderer.GunEntry;
import mchorse.blockbuster.client.render.tileentity.TileEntityModelItemStackRenderer;
import mchorse.blockbuster.client.render.tileentity.TileEntityModelItemStackRenderer.TEModel;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.blockbuster.recording.RecordManager;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.RecordRecorder;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.MMStateAction;
import mchorse.blockbuster.recording.actions.MWFAimAction;
import mchorse.blockbuster.recording.actions.MWFExtraSlotAction;
import mchorse.blockbuster.recording.actions.MwrpPoseAction;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import mchorse.blockbuster.recording.actions.AttackAction;
import mchorse.blockbuster.recording.actions.BreakBlockAction;
import mchorse.blockbuster.recording.actions.ChatAction;
import mchorse.blockbuster.recording.actions.CommandAction;
import mchorse.blockbuster.recording.actions.DropAction;
import mchorse.blockbuster.recording.actions.InteractBlockAction;
import mchorse.blockbuster.recording.actions.InteractEntityAction;
import mchorse.blockbuster.recording.actions.ItemUseAction;
import mchorse.blockbuster.recording.actions.ItemUseBlockAction;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.blockbuster.recording.actions.MorphActionAction;
import mchorse.blockbuster.recording.actions.MountingAction;
import mchorse.blockbuster.recording.actions.PlaceBlockAction;
import mchorse.blockbuster.recording.actions.ShootArrowAction;
import mchorse.blockbuster.recording.actions.VehicleMountAction;
import mchorse.blockbuster.recording.dynamx.DynamXCompat;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.metamorph.api.events.MorphActionEvent;
import mchorse.metamorph.api.events.MorphEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent;
import net.minecraftforge.event.world.BlockEvent.PlaceEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event handler for recording purposes.
 *
 * This event handler listens to different events and then writes them to
 * the recording event list (which in turn are being written to the disk
 * by RecordThread).
 *
 * Taken from Mocap mod and rewritten.
 */
public class ActionHandler
{
    /**
     * Last TE was spotted during block breaking action (used for
     * damage control of tile entities)
     */
    public static TileEntity lastTE;

    /**
     * Server-tick poller for DynamX vehicle controls. ControllerUpdate events
     * fire CLIENT-side only, so we poll engine.getControls() each server tick
     * for every recording player and emit a VehicleControlAction when it changes.
     */
    private final mchorse.blockbuster.recording.dynamx.DynamXVehicleHandler dynamxHandler
            = new mchorse.blockbuster.recording.dynamx.DynamXVehicleHandler();

    /**
     * Last-known ModularWarfare vest per recording player. MWF keeps vests in
     * its own extra slot capability rather than in a vanilla armor slot, so
     * EquipAction never sees them and they have to be polled.
     */
    private final Map<UUID, ItemStack> lastVests = new HashMap<UUID, ItemStack>();

    /** Last-known aiming state per recording player. */
    private final Map<UUID, Boolean> lastAiming = new HashMap<UUID, Boolean>();

    /** Last-known ModularMovements pose code per recording player. */
    private final Map<UUID, Integer> lastMovement = new HashMap<UUID, Integer>();

    /** Zuletzt aufgezeichnete MWRP-Pose je Aufnehmendem. */
    private final Map<UUID, String> lastPose = new HashMap<UUID, String>();

    /** Zu welcher Aufnahme der Delta-Zustand oben gehoert. */
    private final Map<UUID, Object> lastRecorder = new HashMap<UUID, Object>();

    /**
     * Tracks whether we've attempted to register soft-dependency event handlers.
     * Done lazily on first world load so Forge mod init has finished and the
     * soft-dependency mods (DynamX, MWF) have registered their event classes.
     */
    private static boolean softCompatsRegistered = false;

    /**
     * Adds a world event listener
     */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        World world = event.getWorld();

        if (!world.isRemote)
        {
            world.addEventListener(new WorldEventListener(world));
        }

        if (world instanceof WorldServer && ((WorldServer) world).provider.getDimension() == 0)
        {
            Blockbuster.reloadServerModels(true);
        }

        /* Lazy-register soft-dependency compat handlers on first world load */
        if (!softCompatsRegistered)
        {
            softCompatsRegistered = true;
            tryRegisterCompat("com.modularwarfare.api.WeaponFireEvent",
                    "mchorse.blockbuster.recording.mwf.MWFCompatHandler",
                    "MWF detected — cosmetic weapon fire recording enabled");
            tryRegisterCompat("fr.dynamx.common.entities.BaseVehicleEntity",
                    "mchorse.blockbuster.recording.dynamx.SceneVehicles",
                    "DynamX detected - scene vehicle ghost cleanup enabled");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new mchorse.blockbuster.recording.scene.SceneActorRescue());
        }
    }

    /**
     * Register a Forge event listener only if its anchor class is loadable.
     * Uses reflection so Blockbuster doesn't crash when the soft-dep mod is absent.
     */
    private static void tryRegisterCompat(String anchorClass, String handlerClass, String logMsg)
    {
        try
        {
            Class.forName(anchorClass);
            Object handler = Class.forName(handlerClass).getConstructor().newInstance();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(handler);
            System.out.println("[Blockbuster] " + logMsg);
        }
        catch (Throwable ignored)
        {
            /* Mod absent or handler failed to load — silently disabled */
        }
    }

    @SubscribeEvent
    public void onItemUse(PlayerInteractEvent.RightClickItem event)
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new ItemUseAction(event.getHand()));
        }
    }

    @SubscribeEvent
    public void onItemUseBlock(PlayerInteractEvent.RightClickBlock event)
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            if (isAimingWithGun(player))
            {
                return;
            }

            Vec3d hit = event.getHitVec();
            BlockPos pos = event.getPos();

            if (hit == null)
            {
                events.add(new ItemUseBlockAction(pos, event.getHand(), event.getFace()));
            }
            else
            {
                events.add(new ItemUseBlockAction(pos, event.getHand(), event.getFace(), (float) hit.x - pos.getX(), (float) hit.y - pos.getY(), (float) hit.z - pos.getZ()));
            }
        }
    }

    /**
     * Event listener for Action.BREAK_BLOCK
     */
    @SubscribeEvent
    public void onPlayerBreaksBlock(BreakEvent event)
    {
        EntityPlayer player = event.getPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new BreakBlockAction(event.getPos(), !player.isCreative()));
        }
    }

    /**
     * Event listener for Action.INTERACT_BLOCK (when player right clicks on
     * a block)
     */
    @SubscribeEvent
    public void onPlayerRightClickBlock(RightClickBlock event)
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            if (isAimingWithGun(player) || InteractBlockAction.blocksClick(player, player.isSneaking()))
            {
                return;
            }

            /* Forge meldet einen Klick fuer die Nebenhand nach, wenn die Haupthand
             * nichts ausgeloest hat. Abgespielt wird immer mit der Haupthand -
             * zwei Eintraege schalteten Tueren im selben Tick auf und wieder zu. */
            if (event.getHand() != net.minecraft.util.EnumHand.MAIN_HAND)
            {
                return;
            }

            events.add(new InteractBlockAction(event.getPos()));
        }
    }

    /**
     * Steigt ein echter Spieler in ein gerade kinematisch gefuehrtes Fahrzeug, geht
     * es sofort an die Physik zurueck - sonst liesse es sich nicht fahren. Laeuft auf
     * beiden Seiten; im Einzelspieler rechnet der Client die Physik.
     */
    @SubscribeEvent
    public void onEntityMount(net.minecraftforge.event.entity.EntityMountEvent event)
    {
        if (!event.isMounting() || !(event.getEntityMounting() instanceof EntityPlayer))
        {
            return;
        }

        /* Wer gerade abgespielt wird, ist kein Mensch - auch nicht, wenn er als
         * Fake Player ein EntityPlayer ist. Dessen Einsteigen darf das Auto nicht
         * freigeben, es soll ja auf der Bahn gefuehrt werden. */
        if (mchorse.blockbuster.utils.EntityUtils.getRecordPlayer((EntityPlayer) event.getEntityMounting()) != null)
        {
            return;
        }

        if (!mchorse.blockbuster.recording.dynamx.DynamXCompat.isAvailable())
        {
            return;
        }

        net.minecraft.entity.Entity vehicle = event.getEntityBeingMounted();

        if (mchorse.blockbuster.recording.dynamx.DynamXCompat.isVehicle(vehicle)
            && mchorse.blockbuster.recording.dynamx.DynamXVehiclePin.isPinned(vehicle))
        {
            mchorse.blockbuster.recording.dynamx.DynamXVehiclePin.unpin(vehicle);
        }
    }

    /**
     * Rechtsklick mit einer MWF-Waffe ist ZIELEN, kein Benutzen.
     *
     * <p>Ohne diese Unterscheidung landete jedes Zielen als Block-Interaktion in
     * der Aufnahme. Beim Abspielen hat der Actor dann an Tueren herumgedrueckt -
     * hoerbar als Tuergeraeusche, und im schlimmsten Fall geht auf dem Set eine
     * Tuer auf, die zubleiben soll.</p>
     */
    private static boolean isAimingWithGun(EntityPlayer player)
    {
        return MWFCompat.getHoldKind(player.getHeldItemMainhand()) == MWFCompat.HOLD_GUN;
    }

    /**
     * Event listener for entity interact event
     */
    @SubscribeEvent
    public void onRightClickEntity(PlayerInteractEvent.EntityInteract event)
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new InteractEntityAction(event.getHand()));
        }
    }

    /**
     * Event listener for Action.PLACE_BLOCK
     */
    @SubscribeEvent
    public void onPlayerPlacesBlock(PlaceEvent event)
    {
        EntityPlayer player = event.getPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            if (this.recordLittleTilesDoor(events, player, event.getWorld(), event.getPos()))
            {
                return;
            }

            IBlockState state = event.getPlacedBlock();
            Block block = state.getBlock();

            this.placeBlock(events, event.getPos(), block, state);
        }
    }

    /**
     * Another event listener for Action.PLACE_BLOCK
     */
    @SubscribeEvent
    public void onPlayerPlacesMultiBlock(MultiPlaceEvent event)
    {
        EntityPlayer player = event.getPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            if (this.recordLittleTilesDoor(events, player, event.getWorld(), event.getPos()))
            {
                return;
            }

            List<BlockSnapshot> blocks = event.getReplacedBlockSnapshots();

            for (BlockSnapshot snapshot : blocks)
            {
                IBlockState state = snapshot.getCurrentBlock();
                Block block = state.getBlock();

                this.placeBlock(events, snapshot.getPos(), block, state);
            }
        }
    }

    /**
     * Event listener for Action.ATTACK
     */
    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event)
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null && !Blockbuster.recordAttackOnSwipe.get())
        {
            events.add(new AttackAction());
        }
    }

    /**
     * Event listener for bucket using. When you place water or lava with
     * bucket it doesn't considered place block action like with any other
     * types of blocks.
     *
     * So here's my hack for placing water and lava blocks.
     */
    @SubscribeEvent
    public void onPlayerUseBucket(FillBucketEvent event)
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);
        RayTraceResult target = event.getTarget();

        if (!player.world.isRemote && events != null && target != null && target.typeOfHit == Type.BLOCK)
        {
            Item bucket = event.getEmptyBucket().getItem();
            BlockPos pos = target.getBlockPos().offset(target.sideHit);

            if (bucket == Items.LAVA_BUCKET)
            {
                this.placeBlock(events, pos, Blocks.FLOWING_LAVA, 0);
            }
            else if (bucket == Items.WATER_BUCKET)
            {
                this.placeBlock(events, pos, Blocks.FLOWING_WATER, 0);
            }
        }
    }

    private static java.lang.reflect.Field ltController;
    private static java.lang.reflect.Field ltActivator;
    private static boolean ltResolved;

    /**
     * LittleTiles-Tuer per Animation erkennen: mit dem Saro-Fix setzt LittleTiles
     * die Tuer ohne Spieler (keine Survival-Grenzen, kein AreaGuard), dann kommt
     * kein MultiPlaceEvent mehr. Der Ausloeser steht aber im DoorController.
     */
    @SubscribeEvent
    public void onLittleTilesAnimation(net.minecraftforge.event.entity.EntityJoinWorldEvent event)
    {
        Entity entity = event.getEntity();

        if (event.getWorld().isRemote || !entity.getClass().getName().equals("com.creativemd.littletiles.common.entity.EntityAnimation"))
        {
            return;
        }

        try
        {
            if (!ltResolved)
            {
                ltResolved = true;
                ltController = entity.getClass().getField("controller");
                ltActivator = Class.forName("com.creativemd.littletiles.common.entity.DoorController").getField("activator");
            }

            if (ltController == null || ltActivator == null)
            {
                return;
            }

            Object controller = ltController.get(entity);

            if (controller == null || !ltActivator.getDeclaringClass().isInstance(controller))
            {
                return;
            }

            Object activator = ltActivator.get(controller);

            if (activator instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer) activator;
                List<Action> events = CommonProxy.manager.getActions(player);

                if (events != null)
                {
                    addLittleTilesClick(events, new BlockPos(entity));
                }
            }
        }
        catch (Throwable t)
        {
            ltController = null;
        }
    }

    public static void addLittleTilesClick(List<Action> events, BlockPos pos)
    {
        for (Action action : events)
        {
            if (action.getClass() == InteractBlockAction.class)
            {
                return;
            }
        }

        events.add(new InteractBlockAction(pos));
    }

    /**
     * LittleTiles oeffnet und schliesst eine Tuer, indem es sie mit dem
     * klickenden Spieler in einer eigenen Animationswelt neu platziert. Der
     * Klick selbst kommt nie als RightClickBlock an - vorher landete nur
     * "place_block minecraft:air" in der Aufnahme. Daraus wird der Tuer-Klick,
     * einmal pro Tick.
     */
    private boolean recordLittleTilesDoor(List<Action> events, EntityPlayer player, net.minecraft.world.World world, BlockPos pos)
    {
        if (world == null || world == player.world)
        {
            return false;
        }

        if (InteractBlockAction.blocksClick(player, player.isSneaking()))
        {
            return true;
        }

        addLittleTilesClick(events, pos);

        return true;
    }

    private void placeBlock(List<Action> events, BlockPos pos, Block block, IBlockState state)
    {
        this.placeBlock(events, pos, block, block.getMetaFromState(state));
    }

    /**
     * Place block in given event list
     */
    private void placeBlock(List<Action> events, BlockPos pos, Block block, int metadata)
    {
        ResourceLocation id = block.getRegistryName();

        /* Luft setzt kein Spieler, und LittleTiles-Bloecke ohne ihre Kacheln
         * wuerden beim Abspielen nur kaputte Bloecke hinterlassen. */
        if (id == null || block == Blocks.AIR || "littletiles".equals(id.getResourceDomain()))
        {
            return;
        }

        events.add(new PlaceBlockAction(pos, (byte) metadata, id.toString()));
    }

    /**
     * Event listener for Action.MOUNTING (when player mounts other entity)
     */
    @SubscribeEvent
    public void onPlayerMountsSomething(EntityMountEvent event)
    {
        if (!(event.getEntityMounting() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityMounting();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (player.world.isRemote || events == null) return;

        Entity mountTarget = event.getEntityBeingMounted();

        /* For DynamX vehicles, record a full VehicleMountAction carrying the seat
         * index, vehicle start pose, and an NBT snapshot. Playback uses the snapshot
         * to respawn the vehicle if it was destroyed, and the start pose to teleport
         * it back on each loop/stop so playback is deterministic. */
        if (DynamXCompat.isVehicle(mountTarget))
        {
            int seatIndex = event.isMounting()
                    ? Math.max(DynamXCompat.getSeatIndex(mountTarget, player), 0)
                    : 0;

            NBTTagCompound snapshot = null;
            if (event.isMounting())
            {
                NBTTagCompound tag = new NBTTagCompound();
                try
                {
                    /* writeToNBTOptional embeds the entity "id" so EntityList
                     * can reconstruct the right vehicle subclass later. */
                    if (mountTarget.writeToNBTOptional(tag))
                    {
                        snapshot = tag;
                    }
                }
                catch (Throwable ignored) {}
            }

            events.add(new VehicleMountAction(
                    mountTarget.getUniqueID(),
                    event.isMounting(),
                    seatIndex,
                    mountTarget.posX,
                    mountTarget.posY,
                    mountTarget.posZ,
                    mountTarget.rotationYaw,
                    mountTarget.rotationPitch,
                    snapshot));

            /* Reset delta-compression baseline on mount so first control change
             * after mounting always emits a VehicleControlAction. */
            if (event.isMounting())
            {
                RecordRecorder recorder = CommonProxy.manager.recorders.get(player);
                if (recorder != null)
                {
                    recorder.lastVehicleControls = -1;
                }
            }
            return;
        }

        /* Fallback: vanilla mount */
        events.add(new MountingAction(mountTarget.getUniqueID(), event.isMounting()));
    }

    /**
     * Event listener for Action.SHOOT_ARROW
     */
    @SubscribeEvent
    public void onArrowLooseEvent(ArrowLooseEvent event) throws IOException
    {
        EntityPlayer player = event.getEntityPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new ShootArrowAction(event.getCharge()));
        }
    }

    /**
     * Event listener for Action.DROP (when player drops the item from his
     * inventory)
     */
    @SubscribeEvent
    public void onItemTossEvent(ItemTossEvent event) throws IOException
    {
        EntityPlayer player = event.getPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new DropAction(event.getEntityItem().getItem()));
        }
    }

    /**
     * Event listener for Action.CHAT (basically when the player enters
     * something in the chat)
     */
    @SubscribeEvent
    public void onServerChatEvent(ServerChatEvent event)
    {
        EntityPlayer player = event.getPlayer();
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new ChatAction(event.getMessage()));
        }
    }

    /**
     * Event listener for Action.COMMAND (basically when the player enters
     * a command in the chat). Adds an action only for server commands.
     */
    @SubscribeEvent
    public void onPlayerCommand(CommandEvent event)
    {
        if (!Blockbuster.recordCommands.get())
        {
            return;
        }

        ICommandSender sender = event.getSender();

        if (sender instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) sender;
            List<Action> events = CommonProxy.manager.getActions(player);

            if (!player.world.isRemote && events != null)
            {
                String command = "/" + event.getCommand().getName();

                for (String value : event.getParameters())
                {
                    command += " " + value;
                }

                events.add(new CommandAction(command));
            }
        }
    }

    /**
     * "/scene clear name|all" und "/scene stop" auf eine schon beendete Szene.
     *
     * CommandScene erbt von mclib und laesst sich mit build_and_deploy.bat nicht
     * kompilieren, deshalb wird hier abgefangen. Die Rechte hat Vanilla vor dem
     * Event schon geprueft. Abgebrochen landet der Befehl auch nicht in einer Aufnahme.
     */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onSceneCommand(CommandEvent event)
    {
        String[] args = event.getParameters();
        ICommandSender sender = event.getSender();

        if (!"scene".equals(event.getCommand().getName()) || args.length < 1 || sender.getServer() == null)
        {
            return;
        }

        String name;
        boolean cars = false;

        if (args[0].equals("recordall"))
        {
            if (args.length < 2)
            {
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("/scene recordall <name>"));
            }
            else
            {
                mchorse.blockbuster.recording.scene.GroupRecording.toggle(args[1], sender);
            }

            event.setCanceled(true);

            return;
        }

        /* Laeuft eine Gruppenaufnahme, beendet stop/toggle auch alle Aufnahmen -
         * sonst naehmen die Spieler ohne Szene weiter auf. */
        if ((args[0].equals("stop") || args[0].equals("toggle")) && args.length >= 2
            && mchorse.blockbuster.recording.scene.GroupRecording.stopIfActive(args[1], sender))
        {
            event.setCanceled(true);

            return;
        }

        /* CommandScene liest args[2], prueft aber nur auf zwei Argumente. */
        if (args[0].equals("loop") && args.length < 3)
        {
            sender.sendMessage(new net.minecraft.util.text.TextComponentString("/scene loop <name> <true|false>"));
            event.setCanceled(true);

            return;
        }

        if (args[0].equals("clear"))
        {
            if (args.length < 2)
            {
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("/scene clear <name|all> [cars]"));
                event.setCanceled(true);

                return;
            }

            name = args[1].equals("all") ? null : args[1];
            cars = args.length >= 3 && args[2].equals("cars");
        }
        else if (args[0].equals("stop") && args.length >= 2)
        {
            /* Eine laufende Szene stoppt CommandScene wie bisher. Nach dem Ende ist
             * sie nicht mehr geladen und der Befehl ging ins Leere - dann raeumen
             * wir stattdessen ihre stehen gebliebenen Actors weg. */
            mchorse.blockbuster.recording.scene.Scene scene = CommonProxy.scenes.getScenes().get(args[1]);

            if (scene != null && scene.playing)
            {
                return;
            }

            name = args[1];
        }
        else
        {
            return;
        }

        int removed = mchorse.blockbuster.recording.scene.SceneCleaner.clear(sender.getServer(), name, null, 0, 0, 0, -1);
        int vehicles = cars ? mchorse.blockbuster.recording.scene.SceneCleaner.clearVehicles(sender.getServer(), name) : 0;
        net.minecraft.util.text.TextComponentString message = new net.minecraft.util.text.TextComponentString(
            (name == null ? "Alle Szenen" : "Szene " + name) + " geleert: " + removed + " Actors"
            + (cars ? ", " + vehicles + " Fahrzeuge" : "") + " entfernt.");

        message.getStyle().setColor(net.minecraft.util.text.TextFormatting.GREEN);
        sender.sendMessage(message);
        event.setCanceled(true);
    }

    /**
     * Event listener when player logs out. This listener aborts the recording
     * for given player (well, if he records, but that {@link RecordManager}'s
     * job to find out).
     */
    @SubscribeEvent
    public void onPlayerLogOut(PlayerLoggedOutEvent event)
    {
        EntityPlayer player = event.player;

        if (!player.world.isRemote)
        {
            CommonProxy.manager.abort(player);
            this.dynamxHandler.clearPlayer(player.getUniqueID());
            this.lastVests.remove(player.getUniqueID());
            this.lastAiming.remove(player.getUniqueID());
            this.lastMovement.remove(player.getUniqueID());
            this.lastPose.remove(player.getUniqueID());
            this.lastRecorder.remove(player.getUniqueID());
        }
    }

    /**
     * Event listener for MORPH
     *
     * This is a new event listener for morphing. Before that, there was server
     * handler which was responsible for recoring MORPH action.
     */
    @SubscribeEvent
    public void onPlayerMorph(MorphEvent.Post event)
    {
        EntityPlayer player = event.player;
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null)
        {
            events.add(new MorphAction(event.morph));
        }
    }

    /**
     * Event listener for MORPH_ACTION
     *
     * This method will simply submit a {@link MorphActionAction} to the
     * event list, if action is valid.
     */
    @SubscribeEvent
    public void onPlayerMorphAction(MorphActionEvent event)
    {
        EntityPlayer player = event.player;
        List<Action> events = CommonProxy.manager.getActions(player);

        if (!player.world.isRemote && events != null && event.isValid())
        {
            events.add(new MorphActionAction());
        }
    }

    /**
     * Event listener for server tick event.
     *
     * This is probably not the optimal solution, but I'm not really sure how
     * to schedule things in Minecraft other way than timers and ticks.
     *
     * This method is responsible for scheduling record unloading and counting
     * down recording process.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent event)
    {
        if (event.phase == Phase.START)
        {
            return;
        }

        CommonProxy.manager.tick();
        CommonProxy.scenes.tick();

        /* Tab-Vervollstaendigung fuer /scene clear und /scene recordall. Der Befehl
         * wird erst beim Serverstart registriert, deshalb hier statt beim Laden. */
        mchorse.blockbuster.commands.SceneCommandCompleter.install(net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance());
    }

    /**
     * Event listener for world tick event.
     * 
     * This stuff will be called between networking and world tick. 
     * I think it's a good time to spawn actors and execute unsafe actions. 
     * 
     * Because if actions to modify the world are performed while the actor 
     * is updating, they will be displayed to the client with a delay of 
     * 1 tick.
     */
    @SubscribeEvent
    public void onWorldServerTick(WorldTickEvent event)
    {
        if (event.phase == Phase.END || event.world.isRemote)
        {
            return;
        }

        CommonProxy.scenes.worldTick(event.world);
    }

    /**
     * This is going to record the player actions
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event)
    {
        if (event.phase == Phase.START)
        {
            return;
        }

        EntityPlayer player = event.player;
        boolean server = !player.world.isRemote;

        if (server && CommonProxy.manager.recorders.containsKey(player))
        {
            RecordRecorder recorder = CommonProxy.manager.recorders.get(player);

            if (player.isDead)
            {
                CommonProxy.manager.halt(player, true, true);
                RecordUtils.broadcastInfo("recording.dead", recorder.record.filename);
            }
            else
            {
                /* Poll DynamX vehicle controls BEFORE frame capture so any
                 * VehicleControlAction emitted lands on the same tick as the
                 * frame showing the new state. No-op when DynamX is absent. */
                /* Der Delta-Zustand gehoert zu EINER Aufnahme. Ohne dieses
                 * Zuruecksetzen fehlten Weste, Zielen und Bewegungspose in jeder
                 * Aufnahme AUSSER der ersten nach dem Einloggen: der Wert war
                 * gegenueber der vorherigen Aufnahme unveraendert, also wurde
                 * nichts geschrieben. Bisher raeumte das nur der Logout auf. */
                if (this.lastRecorder.get(player.getUniqueID()) != recorder)
                {
                    this.lastRecorder.put(player.getUniqueID(), recorder);
                    this.lastVests.remove(player.getUniqueID());
                    this.lastAiming.remove(player.getUniqueID());
                    this.lastMovement.remove(player.getUniqueID());
                    this.lastPose.remove(player.getUniqueID());
                    this.dynamxHandler.clearPlayer(player.getUniqueID());
                }

                this.dynamxHandler.tickRecordingPlayer(player);
                this.tickMWFVest(player);
                this.tickMWFAim(player);
                this.tickMovementState(player);
                this.tickMwrpPose(player);
                recorder.record(player);
            }
        }

        IRecording recording = Recording.get(player);
        RecordPlayer record = recording.getRecordPlayer();

        if (record != null)
        {
            record.next();

            if (record.isFinished() && server)
            {
                record.stopPlaying();
            }
        }
    }

    /**
     * Capture the ModularWarfare vest a recording player is wearing.
     *
     * Emitted delta-compressed: vests change rarely, so this adds at most a
     * couple of actions to a recording. Wrapped defensively because this runs
     * inside the recording tick - a failure here must never abort a recording.
     */
    private static String describe(ItemStack stack)
    {
        return stack == null || stack.isEmpty() ? "<leer>" : stack.getItem().getClass().getSimpleName() + "/" + stack.getItem().getRegistryName();
    }

    private void tickMWFVest(EntityPlayer player)
    {
        try
        {
            if (!MWFCompat.isAvailable()) return;

            UUID id = player.getUniqueID();
            ItemStack current = MWFCompat.getExtraSlotStack(player, MWFCompat.SLOT_VEST);
            ItemStack previous = this.lastVests.get(id);

            if (previous != null && ItemStack.areItemStacksEqual(previous, current)) return;
            if (previous == null && current.isEmpty()) return;

            List<Action> actions = CommonProxy.manager.getActions(player);

            if (actions != null)
            {
                actions.add(new MWFExtraSlotAction(MWFCompat.SLOT_VEST, current));
                System.out.println("[Blockbuster] Weste in die Aufnahme geschrieben: " + describe(current));
            }
            else
            {
                System.out.println("[Blockbuster] Weste NICHT geschrieben: keine Aktionsliste fuer diesen Tick");
            }

            this.lastVests.put(id, current.copy());
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Capture whether a recording player is aiming down sights, so the actor
     * raises the weapon the same way in the replay. Delta-compressed.
     */
    private void tickMWFAim(EntityPlayer player)
    {
        try
        {
            if (!MWFCompat.isAvailable()) return;

            UUID id = player.getUniqueID();
            boolean aiming = MWFCompat.isAiming(player);
            Boolean previous = this.lastAiming.get(id);

            if (previous != null && previous.booleanValue() == aiming) return;
            if (previous == null && !aiming) return;

            List<Action> actions = CommonProxy.manager.getActions(player);

            if (actions != null)
            {
                actions.add(new MWFAimAction(aiming));
            }

            this.lastAiming.put(id, Boolean.valueOf(aiming));
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Capture the recording player's ModularMovements pose - leaning, sitting,
     * crawling, rolling. One int, delta-compressed like the vehicle state.
     */
    private void tickMovementState(EntityPlayer player)
    {
        try
        {
            if (!MWFCompat.isAvailable()) return;

            UUID id = player.getUniqueID();
            int code = MWFCompat.getMovementState(player);
            Integer previous = this.lastMovement.get(id);

            /* 0 = gerade stehen. Nur zu Beginn weglassen - zurueck in die Mitte muss
             * aufgezeichnet werden, sonst blieb der Actor nach dem Lehnen schief. */
            if (code == 0 && previous == null) return;
            if (previous != null && previous.intValue() == code) return;

            List<Action> actions = CommonProxy.manager.getActions(player);

            if (actions != null)
            {
                actions.add(new MMStateAction(code));
            }

            this.lastMovement.put(id, Integer.valueOf(code));
        }
        catch (Throwable ignored)
        {}
    }

    private static boolean poseResolved;
    private static java.lang.reflect.Method methodPoseGet;
    private static java.lang.reflect.Field fieldPoseId;

    /**
     * Die MWRP-Pose des Aufnehmenden (/pose, Posen-Menue) mitschreiben. Gefragt wird
     * MWRPs Server-Zustand, so zaehlt jeder Weg, eine Pose zu setzen. Abgelaufene
     * Zeit-Posen meldet MWRP als keine, das landet als "" in der Aufnahme.
     */
    private void tickMwrpPose(EntityPlayer player)
    {
        try
        {
            if (!poseResolved)
            {
                poseResolved = true;

                Class<?> manager = Class.forName("de.sarocesch.minewacheroleplaymod.animation.pose.PoseManager");

                methodPoseGet = manager.getMethod("get", UUID.class);
                fieldPoseId = Class.forName("de.sarocesch.minewacheroleplaymod.animation.pose.ActivePose").getField("poseId");
            }

            if (methodPoseGet == null) return;

            UUID id = player.getUniqueID();
            Object active = methodPoseGet.invoke(null, id);
            String pose = active == null ? null : (String) fieldPoseId.get(active);
            String previous = this.lastPose.get(id);

            if (pose == null) pose = "";

            /* Ohne Pose zu Beginn nichts schreiben, das Ende einer Pose aber schon. */
            if (pose.isEmpty() && previous == null) return;
            if (pose.equals(previous)) return;

            List<Action> actions = CommonProxy.manager.getActions(player);

            if (actions != null)
            {
                actions.add(new MwrpPoseAction(pose));
            }

            this.lastPose.put(id, pose);
        }
        catch (Throwable t)
        {
            if (methodPoseGet != null)
            {
                System.out.println("[Blockbuster] MWRP-Pose nicht aufgezeichnet: " + t);
            }

            methodPoseGet = null;
        }
    }
}