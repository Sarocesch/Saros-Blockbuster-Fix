package mchorse.blockbuster.network.client;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.common.PacketActorPose;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Merkt sich die Pose am Actor. Gesetzt wird sie bei MWRP erst beim Zeichnen
 * (MwrpPoseBridge), dann passt sie auch zu einem neu gebauten Spielermodell.
 */
public class ClientHandlerActorPose extends ClientMessageHandler<PacketActorPose>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketActorPose message)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null) return;

        Entity entity = mc.world.getEntityByID(message.entityId);

        if (entity instanceof EntityActor)
        {
            ((EntityActor) entity).mwrpPose = message.pose == null ? "" : message.pose;
        }
    }
}
