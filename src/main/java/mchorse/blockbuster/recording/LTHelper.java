package mchorse.blockbuster.recording;

import mchorse.blockbuster.recording.data.Frame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * LittleTiles helper method
 *
 * This bad boy is responsible contacting LittleTile API (introduced in v1.5.0-pre199_31_mc1.12.2)
 * to allow opening the doors. Big thanks to CreativeMD for helping out with this issue!
 *
 * <p>LittleTileAPI.playerRightClickServer baut eine AxisAlignedBB aus zwei Vec3d -
 * den Konstruktor gibt es nur im Client. Auf einem Dedicated Server brach jeder
 * Tuer-Klick mit NoSuchMethodError ab, im Einzelspieler lief es. Deshalb macht
 * Blockbuster den Klick hier selbst, nur mit Server-tauglichen Bausteinen:
 * stehende Tueren ueber LittleActionActivated, laufende Tuer-Animationen ueber
 * LittleDoor.activate.</p>
 *
 * @link https://www.curseforge.com/minecraft/mc-mods/littletiles/files/2960578
 */
public class LTHelper
{
    private static boolean resolved;
    private static boolean available;
    private static Class<?> blockTile;
    private static Constructor<?> activated;
    private static Method activateServer;
    private static Class<?> animationClass;
    private static Field animationStructure;
    private static Field animationOrigin;
    private static Field animationFakeWorld;
    private static Method toFakeWorld;
    private static Method toWorld;
    private static Class<?> doorClass;
    private static Method doorActivate;
    private static Object rightClick;

    public static boolean playerRightClickServer(EntityPlayer player, Frame frame)
    {
        if (!resolve())
        {
            return false;
        }

        try
        {
            player.rotationPitch = frame.pitch;
            player.rotationYaw = frame.yaw;
            /* getLookVec nimmt die Kopfdrehung. Der Hilfsspieler tickt nie,
             * ohne das zielte er immer nach Sueden und verfehlte die Tuer. */
            player.rotationYawHead = frame.yaw;

            Vec3d pos = new Vec3d(frame.x, frame.y, frame.z);
            Vec3d look = player.getLookVec().scale(8);

            pos = pos.addVector(0, player.getEyeHeight(), 0);

            return rightClick(player, pos, pos.add(look));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Notfall: Blick direkt auf die Mitte des aufgenommenen Blocks, falls der
     * aufgenommene Blick knapp an der Tuer vorbeigeht.
     */
    public static boolean playerRightClickAt(EntityPlayer player, Frame frame, BlockPos block)
    {
        if (!resolve())
        {
            return false;
        }

        try
        {
            Vec3d pos = new Vec3d(frame.x, frame.y + player.getEyeHeight(), frame.z);
            Vec3d target = new Vec3d(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
            Vec3d dir = target.subtract(pos).normalize().scale(8);

            return rightClick(player, pos, pos.add(dir));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Wie LittleTileAPI.playerRightClickServer: naechster Treffer zwischen
     * stehendem LittleTiles-Block und laufender Tuer-Animation.
     */
    private static boolean rightClick(EntityPlayer player, Vec3d from, Vec3d to) throws Exception
    {
        World world = player.world;
        RayTraceResult result = world.rayTraceBlocks(from, to);
        double distance = result != null && result.hitVec != null ? from.distanceTo(result.hitVec) : Double.MAX_VALUE;

        Object[] pointedHit = pointedAnimation(world, from, to, distance);
        Entity pointed = pointedHit == null ? null : (Entity) pointedHit[0];

        if (pointed != null)
        {
            Object structure = animationStructure.get(pointed);

            if (doorClass.isInstance(structure))
            {
                return doorActivate.invoke(structure, rightClick, player, pointed.getUniqueID()) != null;
            }

            return false;
        }

        if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK)
        {
            return false;
        }

        BlockPos pos = result.getBlockPos();

        if (!blockTile.isInstance(world.getBlockState(pos).getBlock()))
        {
            return false;
        }

        Object action = activated.newInstance(world, pos, from, to, false);

        return Boolean.TRUE.equals(activateServer.invoke(action, player));
    }

    /**
     * Blickpunkt auf einer LittleTiles-Tuer-Animation in Reichweite, oder null.
     * Nur wenn kein Block naeher davor liegt.
     */
    public static BlockPos animationHit(EntityPlayer player)
    {
        if (!resolve())
        {
            return null;
        }

        Vec3d from = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3d to = from.add(Vec3d.fromPitchYaw(player.rotationPitch, player.rotationYaw).scale(5));
        RayTraceResult block = player.world.rayTraceBlocks(from, to);
        double best = block != null && block.hitVec != null ? from.distanceTo(block.hitVec) : Double.MAX_VALUE;
        try
        {
            Object[] pointed = pointedAnimation(player.world, from, to, best);

            return pointed == null ? null : new BlockPos((Vec3d) pointed[1]);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Wie LittleAnimationHandlerClient.getTarget: Strahl in die eigene Welt
     * jeder Tuer-Animation umrechnen und dort die Kacheln treffen. Die Hitbox
     * der Animation passt nicht zur Tuer. Liefert {Entity, Treffer in Weltkoordinaten}.
     */
    private static Object[] pointedAnimation(World world, Vec3d from, Vec3d to, double maxDistance) throws Exception
    {
        Object[] best = null;
        int checked = 0;

        /* Ueber die geladenen Entities, nicht per Hitbox-Suche: die Hitbox einer
         * Tuer-Animation liegt nicht dort, wo die Tuer ist. */
        for (Entity entity : new java.util.ArrayList<Entity>(world.loadedEntityList))
        {
            if (!animationClass.isInstance(entity) || entity.isDead)
            {
                continue;
            }

            checked++;

            Object origin = animationOrigin.get(entity);
            World fake = (World) animationFakeWorld.get(entity);

            if (origin == null || fake == null)
            {
                continue;
            }

            Vec3d fakeFrom = (Vec3d) toFakeWorld.invoke(origin, from);
            Vec3d fakeTo = (Vec3d) toFakeWorld.invoke(origin, to);
            RayTraceResult result = fake.rayTraceBlocks(fakeFrom, fakeTo);

            if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK || result.hitVec == null)
            {
                continue;
            }

            Vec3d hit = (Vec3d) toWorld.invoke(origin, result.hitVec);
            double distance = from.distanceTo(hit);

            if (distance < maxDistance)
            {
                maxDistance = distance;
                best = new Object[] {entity, hit};
            }
        }

        lastChecked = checked;

        return best;
    }

    /** Fuer das Server-Log: wie viele Tuer-Animationen der letzte Strahl geprueft hat. */
    public static int lastChecked;

    private static boolean resolve()
    {
        if (resolved)
        {
            return available;
        }

        resolved = true;

        try
        {
            blockTile = Class.forName("com.creativemd.littletiles.common.block.BlockTile");

            Class<?> action = Class.forName("com.creativemd.littletiles.common.action.block.LittleActionActivated");

            activated = action.getConstructor(World.class, BlockPos.class, Vec3d.class, Vec3d.class, boolean.class);
            activateServer = action.getMethod("activateServer", EntityPlayer.class);

            animationClass = Class.forName("com.creativemd.littletiles.common.entity.EntityAnimation");
            animationStructure = animationClass.getField("structure");
            animationOrigin = animationClass.getField("origin");
            animationFakeWorld = animationClass.getField("fakeWorld");

            Class<?> vecOrigin = Class.forName("com.creativemd.creativecore.common.utils.math.vec.IVecOrigin");

            toFakeWorld = vecOrigin.getMethod("transformPointToFakeWorld", Vec3d.class);
            toWorld = vecOrigin.getMethod("transformPointToWorld", Vec3d.class);

            doorClass = Class.forName("com.creativemd.littletiles.common.structure.type.door.LittleDoor");

            Class<?> activator = Class.forName("com.creativemd.littletiles.common.structure.type.door.LittleDoor$DoorActivator");

            doorActivate = doorClass.getMethod("activate", activator, EntityPlayer.class, UUID.class);
            rightClick = activator.getField("RIGHTCLICK").get(null);
            available = true;
        }
        catch (Throwable t)
        {
            available = false;
        }

        return available;
    }
}
