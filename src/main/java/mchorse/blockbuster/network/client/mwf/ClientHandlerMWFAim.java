package mchorse.blockbuster.network.client.mwf;

import mchorse.blockbuster.network.common.mwf.PacketMWFAim;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Puts the actor's UUID into MWF's aiming map so its third person pose shows
 * the weapon raised.
 */
public class ClientHandlerMWFAim extends ClientMessageHandler<PacketMWFAim>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketMWFAim message)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null) return;

        Entity entity = mc.world.getEntityByID(message.entityId);

        if (entity != null)
        {
            MWFCompat.setAiming(entity, message.aiming);
        }
    }
}
