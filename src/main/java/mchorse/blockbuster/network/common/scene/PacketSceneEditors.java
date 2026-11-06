package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Server an Client: wer diese Szene ausser dir gerade offen hat, und an welchem Actor.
 */
public class PacketSceneEditors implements IMessage
{
    public String scene = "";
    public List<String> names = new ArrayList<String>();
    public List<String> replays = new ArrayList<String>();

    public PacketSceneEditors()
    {}

    public PacketSceneEditors(String scene, List<String> names, List<String> replays)
    {
        this.scene = scene;
        this.names = names;
        this.replays = replays;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.scene = ByteBufUtils.readUTF8String(buf);

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            this.names.add(ByteBufUtils.readUTF8String(buf));
            this.replays.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, this.scene);
        buf.writeInt(this.names.size());

        for (int i = 0; i < this.names.size(); i++)
        {
            ByteBufUtils.writeUTF8String(buf, this.names.get(i));
            ByteBufUtils.writeUTF8String(buf, this.replays.get(i));
        }
    }
}
