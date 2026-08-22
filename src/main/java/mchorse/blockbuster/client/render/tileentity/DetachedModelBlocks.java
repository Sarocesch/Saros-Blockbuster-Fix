package mchorse.blockbuster.client.render.tileentity;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.common.tileentity.TileEntityModelSettings;
import mchorse.mclib.utils.OptifineHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client side cache of model blocks that have a custom render distance.
 *
 * Minecraft drops a tile entity as soon as its chunk is unloaded on the
 * client, which means a model block simply stops existing once the player
 * walks far enough away, no matter how big its render distance is. Long
 * structures (tunnels, walls, bridges) therefore vanish way before the
 * configured distance is reached.
 *
 * This class keeps a detached copy of every model block that has a render
 * distance set (0 = off, so nothing is tracked by default) and renders that
 * copy itself, completely independent of whether the chunk is still around,
 * whether it has a render chunk and whether it survived frustum culling.
 */
@SideOnly(Side.CLIENT)
public class DetachedModelBlocks
{
    /**
     * Safety net so a broken setup can't eat all the memory. Model blocks
     * only end up in here when someone explicitly gave them a render
     * distance, so this should never be reached in practice.
     */
    public static final int LIMIT = 1024;

    /** How often the loaded tile entities are scanned, in client ticks */
    public static final int SCAN_INTERVAL = 40;

    /** Upper bound for the far plane, depth precision gets worse the higher it is */
    public static final float MAX_FAR_PLANE = 4096F;

    private static final Map<BlockPos, TileEntityModel> blocks = new HashMap<BlockPos, TileEntityModel>();

    /** Last known NBT per position, used to skip pointless copying */
    private static final Map<BlockPos, NBTTagCompound> tags = new HashMap<BlockPos, NBTTagCompound>();

    /** Positions that were already drawn by the normal renderer this frame */
    private static final Set<BlockPos> rendered = new HashSet<BlockPos>();

    private static World world;
    private static int scanTimer;
    private static float requiredFarPlane;

    /**
     * The far plane the projection matrix needs so the tracked model blocks
     * don't get clipped away, or 0 when nothing needs one. Minecraft's own
     * far plane is render distance * 16 * sqrt(2), which is why a model
     * block stops showing up at around 128 or 256 blocks no matter what its
     * own render distance says.
     */
    public static float getRequiredFarPlane()
    {
        return requiredFarPlane;
    }

    /**
     * Register or refresh the detached copy of the given model block.
     */
    public static void track(TileEntityModel model)
    {
        if (model == null || model.detached || model.getWorld() == null)
        {
            return;
        }

        BlockPos pos = model.getPos();

        if (model.getSettings().getRenderDistance() <= 0)
        {
            /* Distance was turned off again, drop the copy */
            untrack(pos);

            return;
        }

        checkWorld(model.getWorld());

        if (!blocks.containsKey(pos) && blocks.size() >= LIMIT)
        {
            return;
        }

        NBTTagCompound tag = new NBTTagCompound();

        model.writeToNBT(tag);

        /* Nothing changed since the last copy, morph deserialization is
         * not exactly free so don't do it for nothing */
        if (tag.equals(tags.get(pos)))
        {
            return;
        }

        TileEntityModel copy = blocks.get(pos);

        if (copy == null)
        {
            copy = new TileEntityModel();
            copy.detached = true;

            pos = pos.toImmutable();

            blocks.put(pos, copy);
        }

        copy.setWorld(model.getWorld());
        copy.readFromNBT(tag);
        copy.updateEntity();

        tags.put(pos, tag);
    }

    /**
     * Drop the copy of a model block, used when the block gets destroyed.
     */
    public static void untrack(BlockPos pos)
    {
        blocks.remove(pos);
        tags.remove(pos);
    }

    public static void clear()
    {
        blocks.clear();
        tags.clear();
        rendered.clear();

        requiredFarPlane = 0;
    }

    /**
     * Copies are bound to one world, so throw them away when the player
     * changes dimension or leaves the world.
     */
    private static void checkWorld(World newWorld)
    {
        if (world != newWorld)
        {
            clear();

            world = newWorld;
        }
    }

    /**
     * Look for loaded model blocks that have a custom render distance and
     * make a copy of them. Runs every {@link #SCAN_INTERVAL} ticks and only
     * walks the tile entity list, which is a plain instanceof check per
     * entry - blocks without a custom distance cost nothing beyond that.
     */
    private static void scan(Minecraft mc)
    {
        List<TileEntity> list = mc.world.loadedTileEntityList;

        for (int i = 0, c = list.size(); i < c; i++)
        {
            TileEntity te;

            try
            {
                te = list.get(i);
            }
            catch (IndexOutOfBoundsException e)
            {
                /* The list shrank while we were walking it */
                break;
            }

            if (te instanceof TileEntityModel)
            {
                TileEntityModel model = (TileEntityModel) te;

                if (!model.detached && model.getSettings().getRenderDistance() > 0)
                {
                    track(model);
                }
            }
        }
    }

    /**
     * Tick the detached copies so their morphs keep animating, no matter
     * whether the real tile entity is still around. Only copies that are
     * within their own render distance are ticked.
     */
    public static void tick()
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null || mc.player == null)
        {
            clear();

            world = null;

            return;
        }

        checkWorld(mc.world);

        if (mc.isGamePaused())
        {
            return;
        }

        if (--scanTimer <= 0)
        {
            scanTimer = SCAN_INTERVAL;

            scan(mc);
        }

        if (blocks.isEmpty())
        {
            requiredFarPlane = 0;

            return;
        }

        double x = mc.player.posX;
        double y = mc.player.posY;
        double z = mc.player.posZ;
        float far = 0;

        Iterator<Map.Entry<BlockPos, TileEntityModel>> it = blocks.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<BlockPos, TileEntityModel> entry = it.next();
            BlockPos pos = entry.getKey();
            TileEntityModel copy = entry.getValue();
            float distance = copy.getSettings().getRenderDistance();

            if (distance <= 0)
            {
                it.remove();
                tags.remove(pos);

                continue;
            }

            /* Chunk is loaded but the model block is gone, so it got broken
             * or replaced - drop the copy */
            if (mc.world.isBlockLoaded(pos, false) && !(mc.world.getTileEntity(pos) instanceof TileEntityModel))
            {
                it.remove();
                tags.remove(pos);

                continue;
            }

            if (distance > far)
            {
                far = distance;
            }

            /*
             * Tick regardless of the real tile entity. A loaded chunk far
             * outside the render distance has no render chunk, so the copy
             * is the one being drawn and it has to keep animating.
             */
            if (copy.getDistanceSq(x, y, z) <= copy.getMaxRenderDistanceSquared())
            {
                copy.update();
            }
        }

        /* Leave room for the model's own geometry, which can stick out well
         * beyond the block it belongs to */
        requiredFarPlane = far <= 0 ? 0 : Math.min(far * 1.25F + 64F, MAX_FAR_PLANE);
    }

    /**
     * Remember that the normal rendering path already drew this model block
     * this frame, so {@link #render(float)} doesn't draw it a second time.
     */
    public static void markRendered(BlockPos pos)
    {
        if (!blocks.isEmpty())
        {
            rendered.add(pos);
        }
    }

    public static void resetFrame()
    {
        rendered.clear();
    }

    /**
     * Render every tracked model block that wasn't drawn by the normal path
     * this frame. Called at the very end of RenderGlobal#renderEntities, so
     * at this point every tile entity of this frame has been rendered.
     *
     * Nothing in here looks at chunks, render chunks or the frustum - that
     * is the whole point, those are exactly the things that make a model
     * block disappear early.
     */
    public static void render(float partialTicks)
    {
        if (blocks.isEmpty())
        {
            rendered.clear();

            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null || ClientProxy.modelRenderer == null)
        {
            rendered.clear();

            return;
        }

        checkWorld(mc.world);

        double px = TileEntityRendererDispatcher.staticPlayerX;
        double py = TileEntityRendererDispatcher.staticPlayerY;
        double pz = TileEntityRendererDispatcher.staticPlayerZ;

        /*
         * Remember the state this method was entered with.
         *
         * This runs from renderLastEntities(), which is ASM-called at the end of
         * RenderGlobal.renderEntities - so it sits in the middle of somebody
         * else's draw sequence, not at a clean boundary. Whatever is set here and
         * left behind is inherited by the rest of the frame: the remaining world
         * passes, the first person hand, the GUI, and on the next frame the
         * vehicles again.
         *
         * That is not a theoretical concern. Setting standard item lighting and
         * pinning the lightmap without ever putting them back is exactly what
         * made DynamX vehicles render with wrong glass and unreadable license
         * plates, and the first person hand flash bright for a frame, whenever a
         * model block with a custom render distance was in the scene. Removing
         * every Blockbuster block from the set made it go away (2026-08-21).
         *
         * The pinned lightmap is the nastier half: the value below comes from the
         * MODEL BLOCK, not from whatever is drawn afterwards, and for an unloaded
         * chunk it is deliberately forced to full brightness. So the leak makes
         * the rest of the frame either too dark or far too bright, depending on
         * where the block happens to stand.
         */
        boolean hadLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        float lastBrightnessX = OpenGlHelper.lastBrightnessX;
        float lastBrightnessY = OpenGlHelper.lastBrightnessY;
        boolean lit = false;

        try
        {
        for (Map.Entry<BlockPos, TileEntityModel> entry : blocks.entrySet())
        {
            BlockPos pos = entry.getKey();

            if (rendered.contains(pos))
            {
                continue;
            }

            TileEntityModel copy = entry.getValue();
            TileEntityModelSettings settings = copy.getSettings();

            if (!settings.isEnabled() || copy.morph.isEmpty())
            {
                continue;
            }

            double dx = pos.getX() - px;
            double dy = pos.getY() - py;
            double dz = pos.getZ() - pz;
            double distance = settings.getRenderDistance();

            if (dx * dx + dy * dy + dz * dz > distance * distance)
            {
                continue;
            }

            /*
             * An unloaded chunk has no light data, so fall back to full
             * brightness instead of rendering a black silhouette.
             */
            int light = mc.world.isBlockLoaded(pos, false)
                ? mc.world.getCombinedLight(pos, 0)
                : (15 << 20 | 15 << 4);

            /* Only once, no matter how many blocks are drawn */
            if (!lit)
            {
                RenderHelper.enableStandardItemLighting();
                lit = true;
            }

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (light % 65536), (float) (light / 65536));
            GlStateManager.color(1F, 1F, 1F, 1F);

            OptifineHelper.nextBlockEntity(copy);
            ClientProxy.modelRenderer.render(copy, dx, dy, dz, partialTicks, -1, 1F);
        }
        }
        finally
        {
            /*
             * Put back exactly what was found, and do it in a finally so a
             * throwing model renderer cannot poison the rest of the frame either.
             *
             * disableStandardItemLighting() is the counterpart of the call above;
             * it also switches GL_LIGHTING off, so whether it has to come back on
             * is decided by what was measured on entry rather than assumed.
             */
            if (lit)
            {
                RenderHelper.disableStandardItemLighting();

                if (hadLighting)
                {
                    GlStateManager.enableLighting();
                }

                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);
                GlStateManager.color(1F, 1F, 1F, 1F);
            }

            rendered.clear();
        }
    }
}
