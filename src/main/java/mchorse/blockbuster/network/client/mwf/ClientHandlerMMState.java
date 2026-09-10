package mchorse.blockbuster.network.client.mwf;

import mchorse.blockbuster.network.common.mwf.PacketMMState;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Hands an actor's ModularMovements pose to MWF's own state map so the model
 * gets posed the same way a player's would be.
 */
public class ClientHandlerMMState extends ClientMessageHandler<PacketMMState>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketMMState message)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null) return;

        Entity entity = mc.world.getEntityByID(message.entityId);

        if (entity != null)
        {
            MWFCompat.setMovementState(entity, message.code);
        }
    }
}
