package mchorse.blockbuster.network.client.mwf;

import mchorse.blockbuster.network.common.mwf.PacketMWFExtraSlot;
import mchorse.blockbuster_pack.client.render.layers.LayerActorMWFArmor;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Stores an actor's ModularWarfare extra slot content so the actor render layer
 * can draw it. Keyed by entity ID because EntityActor has no player UUID.
 */
public class ClientHandlerMWFExtraSlot extends ClientMessageHandler<PacketMWFExtraSlot>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketMWFExtraSlot message)
    {
        LayerActorMWFArmor.put(message.entityId, message.slot, message.stack);
    }
}
