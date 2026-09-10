package mchorse.blockbuster_pack.client.render;

import java.lang.reflect.Constructor;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.blockbuster.client.render.RenderCustomModel;
import mchorse.blockbuster.client.render.layer.LayerHeldItem;
import mchorse.blockbuster_pack.client.render.layers.LayerActorArmor;
import mchorse.blockbuster_pack.client.render.layers.LayerActorMWFArmor;
import mchorse.blockbuster_pack.client.render.layers.LayerBodyPart;
import mchorse.blockbuster_pack.client.render.layers.LayerCustomHead;
import mchorse.blockbuster_pack.client.render.layers.LayerElytra;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * Overriden {@link RenderCustomModel} to support {@link CustomMorph}'s skin
 * property.
 */
public class RenderCustomActor extends RenderCustomModel
{
    public RenderCustomActor(RenderManager renderManagerIn, ModelBase modelBaseIn, float shadowSizeIn)
    {
        super(renderManagerIn, modelBaseIn, shadowSizeIn);

        this.addLayer(new LayerElytra(this));
        this.addLayer(new LayerBodyPart(this));
        this.addLayer(new LayerActorArmor(this));
        this.addLayer(new LayerCustomHead(this));
        this.addLayer(new LayerHeldItem(this));
        this.addLayer(new LayerActorMWFArmor(this));
        this.addModularWarfareLayers();
    }

    @Override
    public void doRender(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
        boolean restore = this.setOuterLayerVisible(entity, false);

        try
        {
            super.doRender(entity, x, y, z, entityYaw, partialTicks);
        }
        finally
        {
            if (restore)
            {
                this.setOuterLayerVisible(entity, true);
            }
        }
    }

    /**
     * Hide or show the model's second skin layer for this actor.
     *
     * Blockbuster models mark that layer by limb name rather than by a flag -
     * fred/alex/steve use bodywear, left_armwear, right_armwear, left_legwear,
     * right_legwear and outer (the hat). Worn clothing clips through them, so
     * a replay can switch them off.
     *
     * @return true when something was hidden and has to be restored afterwards
     */
    private boolean setOuterLayerVisible(EntityLivingBase entity, boolean visible)
    {
        if (!visible && !this.wantsOuterLayerHidden(entity)) return false;
        if (!(this.mainModel instanceof ModelCustom)) return false;

        for (ModelCustomRenderer limb : ((ModelCustom) this.mainModel).limbs)
        {
            if (limb == null || limb.limb == null) continue;

            String name = limb.limb.name;

            if (name.endsWith("wear") || name.equals("outer"))
            {
                limb.showModel = visible;
            }
        }

        return true;
    }

    /**
     * Letzter bekannter Wert je Actor. Beim Einfrieren am Szenenende wird der
     * RecordPlayer abgeraeumt - ohne dieses Gedaechtnis kam die aeussere
     * Hautschicht in dem Moment zurueck, in dem der Actor stehen blieb.
     */
    private static final java.util.Map<Integer, Boolean> LAST_HIDDEN = new java.util.HashMap<Integer, Boolean>();

    private boolean wantsOuterLayerHidden(EntityLivingBase entity)
    {
        try
        {
            if (entity.isDead)
            {
                LAST_HIDDEN.remove(entity.getEntityId());

                return false;
            }

            RecordPlayer record = EntityUtils.getRecordPlayer(entity);

            if (record != null && record.getReplay() != null)
            {
                boolean hidden = record.getReplay().hideOuterLayer;

                LAST_HIDDEN.put(entity.getEntityId(), Boolean.valueOf(hidden));

                return hidden;
            }

            return Boolean.TRUE.equals(LAST_HIDDEN.get(entity.getEntityId()));
        }
        catch (Throwable ignored) {}

        return false;
    }

    /**
     * Attach ModularWarfare's held weapon layers to the actor renderer.
     *
     * MWF does not draw guns through the vanilla item pipeline, it uses its own
     * render layers, and it only ever attaches them to player renderers (and to
     * CustomNPC renderers, see MWF's CustomNPCListener). An actor is neither, so
     * a recorded weapon stayed invisible while the arm was still posed for it.
     *
     * These three layers are typed on EntityLivingBase / RenderLivingBase and
     * therefore work here unchanged. RenderLayerBody and RenderLayerBackpack are
     * deliberately not attached: they are typed on EntityPlayer / RenderPlayer
     * and read MWF's extra slot capability, which an actor does not have.
     *
     * Reflection is used on purpose so that Blockbuster keeps loading when
     * ModularWarfare is absent.
     */
    @SuppressWarnings("unchecked")
    private void addModularWarfareLayers()
    {
        String[] names = {
            "com.modularwarfare.client.model.layers.RenderLayerHeldGun",
            "com.modularwarfare.client.model.layers.RenderLayerHeldMelee",
            "com.modularwarfare.client.model.layers.RenderLayerHeldGrenade"
        };

        for (String name : names)
        {
            try
            {
                Constructor<?> constructor = Class.forName(name).getConstructor(RenderLivingBase.class);

                this.addLayer((LayerRenderer<EntityLivingBase>) constructor.newInstance(this));
            }
            catch (ClassNotFoundException e)
            {
                /* ModularWarfare is not installed, nothing to attach. */
                return;
            }
            catch (Exception e)
            {
                Blockbuster.LOGGER.warn("Couldn't attach ModularWarfare layer " + name + " to the actor renderer", e);
            }
        }
    }

    /**
     * Get entity's texture
     *
     * The thing which is going on here, is that we're going to check, whether
     * given entity has a morph, and if it does, we're going to use its skin
     */
    @Override
    protected ResourceLocation getEntityTexture(EntityLivingBase entity)
    {
        AbstractMorph morph = this.current;

        if (morph != null && morph instanceof CustomMorph)
        {
            ResourceLocation skin = ((CustomMorph) morph).skin;

            if (skin != null)
            {
                return skin;
            }
        }

        return super.getEntityTexture(entity);
    }

    /**
     * Can the nametag be rendered by this entity
     *
     * This method is also takes in account the config option for making actor
     * nametags visible always.
     */
    @Override
    protected boolean canRenderName(EntityLivingBase entity)
    {
        return entity.hasCustomName() && (Blockbuster.actorAlwaysRenderNames.get() || (Minecraft.isGuiEnabled() && entity == this.renderManager.pointedEntity));
    }
}