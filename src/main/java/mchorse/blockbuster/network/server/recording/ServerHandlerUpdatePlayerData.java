package mchorse.blockbuster.network.server.recording;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.recording.PacketUpdatePlayerData;
import mchorse.blockbuster.recording.MPMHelper;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

public class ServerHandlerUpdatePlayerData extends ServerMessageHandler<PacketUpdatePlayerData>
{
    @Override
    public void run(EntityPlayerMP player, PacketUpdatePlayerData message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Record record = null;

        try
        {
            record = CommonProxy.manager.get(message.record);
        }
        catch (Exception e)
        {}

        if (record == null)
        {
            Blockbuster.l10n.error(player, "record.not_exist", message.record);

            return;
        }

        NBTTagCompound tag = new NBTTagCompound();

        player.writeEntityToNBT(tag);
        record.playerData = tag;

        if (MPMHelper.isLoaded())
        {
            tag = MPMHelper.getMPMData(player);

            if (tag != null)
            {
                record.playerData.setTag("MPMData", tag);
            }
        }

        /* Damit bekommen auch aeltere Aufnahmen /skin und CPM-Modell, ohne neu aufzunehmen. */
        mchorse.blockbuster.recording.appearance.ActorAppearance look = mchorse.blockbuster.recording.appearance.AppearanceCapture.capture(player);

        if (look != null)
        {
            record.appearance = look;
            player.sendMessage(new net.minecraft.util.text.TextComponentString(TextFormatting.GRAY + "Aussehen fuer " + message.record + " uebernommen: " + TextFormatting.WHITE + look.describe()));
        }
        else
        {
            player.sendMessage(new net.minecraft.util.text.TextComponentString(TextFormatting.GRAY + "Kein /skin oder CPM-Modell gefunden - Aussehen von " + message.record + " bleibt unveraendert"));
        }

        record.dirty = true;
        record.resetUnload();
    }
}