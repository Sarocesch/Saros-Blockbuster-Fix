package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.scene.SceneLocation;

public class PacketSceneCast extends PacketScene
{
    public boolean open = true;

    /**
     * Nur Client an Server: was der Spieler gegenueber dem Stand beim Oeffnen
     * geaendert hat. Null = ganze Szene uebernehmen (altes Verhalten).
     */
    public mchorse.blockbuster.recording.scene.SceneMerge merge;

    /**
     * Nur Client an Server: Zwischenspeichern waehrend das Menue offen ist. Dann
     * nur die Datei schreiben, eine laufende Szene nicht neu laden - das passiert
     * einmal beim Schliessen.
     */
    public boolean live;

    public PacketSceneCast()
    {}

    public PacketSceneCast(SceneLocation location)
    {
        super(location);
    }

    public PacketSceneCast open(boolean open)
    {
        this.open = open;

        return this;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.open = buf.readBoolean();

        if (buf.isReadable() && buf.readBoolean())
        {
            this.merge = mchorse.blockbuster.recording.scene.SceneMerge.fromBuf(buf);
            this.live = buf.isReadable() && buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeBoolean(this.open);

        if (this.merge != null)
        {
            buf.writeBoolean(true);
            this.merge.toBuf(buf);
            buf.writeBoolean(this.live);
        }
    }
}