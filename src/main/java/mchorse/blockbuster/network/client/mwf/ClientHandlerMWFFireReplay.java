package mchorse.blockbuster.network.client.mwf;

import mchorse.blockbuster.network.common.mwf.PacketMWFFireReplay;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client handler for PacketMWFFireReplay.
 *
 * Looks up the actor entity by ID (works for EntityActor, unlike MWF's own
 * PacketOtherShooterAnimation which uses getPlayerEntityByUUID), then triggers
 * the weapon fire animation via MWFCompat reflection helpers.
 */
public class ClientHandlerMWFFireReplay extends ClientMessageHandler<PacketMWFFireReplay>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketMWFFireReplay message)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null) return;

        Entity entity = mc.world.getEntityByID(message.entityId);

        if (entity instanceof EntityLivingBase)
        {
            MWFCompat.triggerClientFireAnimation((EntityLivingBase) entity, message.gunName, message.fireTickDelay);
        }
    }
}
