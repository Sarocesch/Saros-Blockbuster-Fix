package mchorse.blockbuster.network.client.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketFramesChunk;
import mchorse.blockbuster.network.common.recording.PacketPlayerRecording;
import mchorse.blockbuster.recording.RecordRecorder;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client hanlder player recording
 *
 * This client handler is responsible for updating recording overlay status and
 * starting or stopping the recording based on the state given from packet.
 */
public class ClientHandlerPlayerRecording extends ClientMessageHandler<PacketPlayerRecording>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketPlayerRecording message)
    {
        ClientProxy.recordingOverlay.setVisible(message.recording);
        ClientProxy.recordingOverlay.setCaption(message.filename, true);

        if (message.recording)
        {
            ClientProxy.manager.record(message.filename, player, Mode.FRAMES, false, false, message.offset, null);
        }
        else
        {
            if (!message.canceled)
            {
                this.sendFrames(ClientProxy.manager.recorders.get(player));
            }

            ClientProxy.manager.halt(player, false, false, message.canceled);
        }
    }

    /**
     * Maximum size of a single frames packet.
     *
     * {@link net.minecraft.network.play.client.CPacketCustomPayload} refuses
     * anything above 32767 bytes and disconnects the player. The gap to that
     * hard limit covers FML's channel discriminator and netty's framing.
     */
    private static final int MAX_PAYLOAD = 32000;

    /**
     * Send frames to the server
     *
     * Send chunked frames to the server
     */
    @SideOnly(Side.CLIENT)
    private void sendFrames(RecordRecorder recorder)
    {
        Record record = recorder.record;
        int offset = recorder.offset;
        List<List<Frame>> chunks = this.splitFrames(record, offset);
        int count = chunks.size();

        for (int i = 0; i < count; i++)
        {
            Dispatcher.sendToServer(new PacketFramesChunk(i, count, offset, record.filename, chunks.get(i)));
        }
    }

    /**
     * Split the recorded frames into chunks that each stay below
     * {@link #MAX_PAYLOAD}.
     *
     * This used to split by frame count (400 per packet), which ignores that a
     * frame is not a fixed size: a mounted frame writes mountYaw/mountPitch and
     * a frame with body yaw writes bodyYaw on top of the 76 byte base. Driving a
     * vehicle therefore produced 88 byte frames, and 400 of those overflow the
     * packet limit and kick the player while the recording is being saved.
     *
     * The server reassembles by chunk index ({@link mchorse.blockbuster.recording.data.FrameChunk#add}),
     * not by position, so chunks of differing lengths are fine.
     */
    @SideOnly(Side.CLIENT)
    private List<List<Frame>> splitFrames(Record record, int offset)
    {
        List<List<Frame>> chunks = new ArrayList<List<Frame>>();
        ByteBuf scratch = Unpooled.buffer();

        try
        {
            /* Measure the packet header exactly rather than estimating it, so
             * the budget stays correct for long record names too. */
            new PacketFramesChunk(0, 1, offset, record.filename, Collections.<Frame>emptyList()).toBytes(scratch);

            int budget = MAX_PAYLOAD - scratch.readableBytes();
            List<Frame> current = new ArrayList<Frame>();
            int size = 0;

            for (Frame frame : record.frames)
            {
                scratch.clear();
                frame.toBytes(scratch);

                int frameSize = scratch.readableBytes();

                if (!current.isEmpty() && size + frameSize > budget)
                {
                    chunks.add(current);

                    current = new ArrayList<Frame>();
                    size = 0;
                }

                current.add(frame);
                size += frameSize;
            }

            /* Always send at least one packet, otherwise the server never
             * finalises and saves the record. */
            chunks.add(current);
        }
        finally
        {
            scratch.release();
        }

        return chunks;
    }
}