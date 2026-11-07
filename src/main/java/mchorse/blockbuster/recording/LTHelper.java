package mchorse.blockbuster.recording;

import mchorse.blockbuster.recording.data.Frame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;

/**
 * LittleTiles helper method
 *
 * This bad boy is responsible contacting LittleTile API (introduced in v1.5.0-pre199_31_mc1.12.2)
 * to allow opening the doors. Big thanks to CreativeMD for helping out with this issue!
 *
 * @link https://www.curseforge.com/minecraft/mc-mods/littletiles/files/2960578
 */
public class LTHelper
{
    private static Method method;
    private static boolean weTried;

    public static boolean playerRightClickServer(EntityPlayer player, Frame frame)
    {
        try
        {
            if (method == null && !weTried)
            {
                weTried = true;

                Class clazz = Class.forName("com.creativemd.littletiles.common.api.LittleTileAPI");

                method = clazz.getMethod("playerRightClickServer", EntityPlayer.class, Vec3d.class, Vec3d.class);
            }
        }
        catch (Exception e)
        {}

        if (method != null)
        {
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
        }

        return false;
    }

    /**
     * Notfall: Blick direkt auf die Mitte des aufgenommenen Blocks, falls der
     * aufgenommene Blick knapp an der Tuer vorbeigeht.
     */
    public static boolean playerRightClickAt(EntityPlayer player, Frame frame, net.minecraft.util.math.BlockPos block)
    {
        if (method != null)
        {
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
        }

        return false;
    }

    private static boolean rightClick(EntityPlayer player, Vec3d from, Vec3d to) throws Exception
    {
        Object object = method.invoke(null, player, from, to);

        return object instanceof Boolean && ((Boolean) object).booleanValue();
    }
}