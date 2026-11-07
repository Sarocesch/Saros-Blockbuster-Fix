package mchorse.blockbuster.network.server;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketLoopSceneAction;
import mchorse.blockbuster.network.common.PacketLoopSceneList;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneAutoResume;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;

public class ServerHandlerLoopSceneAction extends ServerMessageHandler<PacketLoopSceneAction>
{
    @Override
    public void run(EntityPlayerMP player, PacketLoopSceneAction message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        World world = player.getServer().getWorld(0);

        if (message.action != PacketLoopSceneAction.LIST)
        {
            Scene scene = CommonProxy.scenes.get(message.id, world);

            if (scene != null)
            {
                this.apply(player, scene, message.action);
            }
        }

        Dispatcher.sendTo(list(world), player);
    }

    private void apply(EntityPlayerMP player, Scene scene, byte action)
    {
        if (action == PacketLoopSceneAction.ON && scene.loops)
        {
            SceneAutoResume.activate(scene);
        }
        else if (action == PacketLoopSceneAction.OFF)
        {
            if (scene.playing)
            {
                scene.stopPlayback(true, true);
            }

            SceneAutoResume.stopped(scene.getId());
        }
        else if (action == PacketLoopSceneAction.SCHEDULE)
        {
            SceneAutoResume.cycleSchedule(scene.getId());
        }
        else if (action == PacketLoopSceneAction.RESTART && scene.loops)
        {
            SceneAutoResume.restart(scene);
        }
        else if (action == PacketLoopSceneAction.TELEPORT)
        {
            List<double[]> anchors = SceneAutoResume.anchors(scene);

            if (anchors.isEmpty())
            {
                return;
            }

            if (player.dimension != 0)
            {
                player.sendStatusMessage(new TextComponentString(TextFormatting.RED + "Loop-Szenen liegen in der Oberwelt."), true);

                return;
            }

            double[] anchor = anchors.get(0);

            player.connection.setPlayerLocation(anchor[0], anchor[2], anchor[1], player.rotationYaw, player.rotationPitch);
        }
    }

    private static PacketLoopSceneList list(World world)
    {
        PacketLoopSceneList packet = new PacketLoopSceneList();

        for (String id : CommonProxy.scenes.sceneFiles())
        {
            try
            {
                Scene scene = CommonProxy.scenes.getScenes().get(id);

                if (scene == null)
                {
                    scene = CommonProxy.scenes.load(id);
                }

                if (scene == null || !scene.loops)
                {
                    continue;
                }

                PacketLoopSceneList.Entry entry = new PacketLoopSceneList.Entry();

                entry.id = id;
                entry.title = scene.title;
                entry.active = SceneAutoResume.isActive(id);
                entry.playing = scene.playing;
                entry.schedule = (byte) SceneAutoResume.getSchedule(id);
                entry.timeOk = SceneAutoResume.scheduleAllows(id, world);

                if (scene.playing)
                {
                    for (RecordPlayer actor : scene.actors.values())
                    {
                        entry.total++;

                        if (actor.actor != null && !actor.actor.isDead)
                        {
                            entry.alive++;
                        }
                    }
                }

                List<double[]> anchors = SceneAutoResume.anchors(scene);

                entry.broken = anchors.size() < SceneAutoResume.enabledReplays(scene);

                if (!anchors.isEmpty())
                {
                    entry.hasPos = true;
                    entry.x = anchors.get(0)[0];
                    entry.z = anchors.get(0)[1];
                    entry.y = anchors.get(0)[2];
                }

                packet.entries.add(entry);
            }
            catch (Throwable t)
            {}
        }

        return packet;
    }
}
