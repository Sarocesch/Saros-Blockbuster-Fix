package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.network.common.scene.PacketSceneBackup;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Schreibt die vom Server geschickte Sicherung einer Szene nach
 * .minecraft/blockbuster-sicherungen/&lt;szene_datum&gt;/ (scenes/ und records/).
 */
public class ClientHandlerSceneBackup extends ClientMessageHandler<PacketSceneBackup>
{
    private static final Map<String, ByteArrayOutputStream> parts = new HashMap<String, ByteArrayOutputStream>();

    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketSceneBackup message)
    {
        /* Der Server legt die Namen fest - trotzdem nie aus dem Sicherungsordner heraus schreiben. */
        if (message.folder.isEmpty() || message.folder.contains("..") || message.path.contains("..")
            || message.folder.contains("/") || message.folder.contains("\\") || message.path.contains(":"))
        {
            return;
        }

        String key = message.folder + "/" + message.path;
        ByteArrayOutputStream stream = parts.get(key);

        if (message.chunk == 0 || stream == null)
        {
            stream = new ByteArrayOutputStream();
            parts.put(key, stream);
        }

        stream.write(message.data, 0, message.data.length);

        if (message.chunk < message.chunks - 1)
        {
            return;
        }

        parts.remove(key);

        File root = new File(Minecraft.getMinecraft().mcDataDir, "blockbuster-sicherungen/" + message.folder);
        File file = new File(root, message.path);

        try
        {
            file.getParentFile().mkdirs();

            try (FileOutputStream out = new FileOutputStream(file))
            {
                stream.writeTo(out);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            player.sendMessage(new TextComponentString(TextFormatting.RED + "[Blockbuster] Sicherung: " + message.path + " konnte nicht geschrieben werden: " + e.getMessage()));
        }

        if (message.file == message.files - 1)
        {
            TextComponentString text = new TextComponentString(TextFormatting.GREEN + "[Blockbuster] Lokale Sicherung fertig (" + message.files + " Dateien): "
                + TextFormatting.WHITE + TextFormatting.UNDERLINE + root.getAbsolutePath());

            text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, root.getAbsolutePath()));
            player.sendMessage(text);
        }
    }
}
