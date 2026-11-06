package mchorse.blockbuster.network.common.scene;

import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Server an Client: neue Fassung einer Szene, die du gerade offen hast.
 * Eigenes Paket statt PacketSceneCast, damit eine spaet ankommende Fassung
 * nie das Menue oeffnet oder auf eine andere Szene umschaltet.
 */
public class PacketSceneLive extends PacketScene
{
    public PacketSceneLive()
    {}

    public PacketSceneLive(SceneLocation location)
    {
        super(location);
    }
}
