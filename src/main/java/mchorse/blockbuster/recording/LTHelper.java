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

        Entity pointed = null;
        AxisAlignedBB box = new AxisAlignedBB(from.x, from.y, from.z, to.x, to.y, to.z).grow(1);

        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, box, (e) -> animationClass.isInstance(e)))
        {
            RayTraceResult hit = entity.getEntityBoundingBox().calculateIntercept(from, to);

            if (hit != null && from.distanceTo(hit.hitVec) < distance)
            {
                distance = from.distanceTo(hit.hitVec);
                pointed = entity;
            }
        }

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
        Vec3d hit = null;
        AxisAlignedBB box = new AxisAlignedBB(from.x, from.y, from.z, to.x, to.y, to.z).grow(1);

        for (Entity entity : player.world.getEntitiesWithinAABB(Entity.class, box, (e) -> animationClass.isInstance(e)))
        {
            RayTraceResult result = entity.getEntityBoundingBox().calculateIntercept(from, to);

            if (result != null && from.distanceTo(result.hitVec) < best)
            {
                best = from.distanceTo(result.hitVec);
                hit = result.hitVec;
            }
        }

        return hit == null ? null : new BlockPos(hit);
    }

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
