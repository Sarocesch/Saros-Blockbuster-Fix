package mchorse.blockbuster_pack.client.render.layers;

import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.ArmorSlot;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

/**
 * Actor's armor layer
 *
 * This is a temporary fix for the armor. In next
 */
public class LayerActorArmor extends LayerArmorBase<ModelBiped>
{
    private RenderLivingBase<EntityLivingBase> renderer;

    public LayerActorArmor(RenderLivingBase<EntityLivingBase> renderer)
    {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    protected void initArmor()
    {
        this.modelArmor = new ModelBiped(1);
        this.modelLeggings = new ModelBiped(0.5F);
    }

    private static final String DYNAMX_ARMOR_CLASS = "fr.dynamx.client.renders.model.ModelObjArmor";

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale)
    {
        ModelBase base = this.renderer.getMainModel();

        if (base instanceof ModelCustom)
        {
            ModelCustom model = (ModelCustom) base;

            /* Track which equipment slots have already been rendered via the DynamX
             * scene-graph path so we don't render the same slot multiple times
             * (multiple limbs can map to the same equipment slot). */
            Set<EntityEquipmentSlot> renderedDynamXSlots = null;

            for (ModelCustomRenderer limb : model.armor)
            {
                ItemStack stack = entity.getItemStackFromSlot(limb.limb.slot.slot);

                if (stack != null && stack.getItem() instanceof ItemArmor)
                {
                    ItemArmor item = (ItemArmor) stack.getItem();

                    if (item.getEquipmentSlot() == limb.limb.slot.slot)
                    {
                        /* Check if Forge returns a custom armor model (e.g. DynamX ModelObjArmor) */
                        ModelBiped defaultModel = this.getModelFromSlot(limb.limb.slot.slot);
                        ModelBiped armorModel = this.getArmorModelHook(entity, stack, limb.limb.slot.slot, defaultModel);

                        if (armorModel != null && armorModel.getClass().getName().equals(DYNAMX_ARMOR_CLASS))
                        {
                            /* DynamX OBJ armor: render via the scene-graph path (model.render)
                             * which manages its own textures. Only render once per slot. */
                            if (renderedDynamXSlots == null)
                            {
                                renderedDynamXSlots = new HashSet<EntityEquipmentSlot>();
                            }

                            if (!renderedDynamXSlots.contains(limb.limb.slot.slot))
                            {
                                renderedDynamXSlots.add(limb.limb.slot.slot);
                                this.renderDynamXArmorSlot(entity, armorModel, limb.limb.slot.slot, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
                            }
                        }
                        else
                        {
                            /* Standard (vanilla) armor rendering */
                            this.renderArmorSlot(entity, stack, item, limb, limb.limb.slot.slot, partialTicks, scale);
                        }
                    }
                }
            }
        }
    }

    /**
     * Render a DynamX OBJ armor model via its scene-graph path.
     * DynamX manages its own texture binding internally, so we must NOT
     * call bindTexture/getArmorResource here — that would overwrite the
     * correct OBJ texture with a non-existent vanilla path (pink texture).
     */
    private void renderDynamXArmorSlot(EntityLivingBase entity, ModelBiped armorModel, EntityEquipmentSlot slot, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale)
    {
        GlStateManager.pushMatrix();

        ModelBase mainModel = this.renderer.getMainModel();

        if (mainModel instanceof ModelCustom)
        {
            ModelCustom customModel = (ModelCustom) mainModel;

            /* Apply the root (anchor) limb transform. Poses like lying/sleeping
             * rotate the anchor to flip the entire model — DynamX doesn't know
             * about Blockbuster's parent chain, so we apply it as a GL transform. */
            this.applyParentTransforms(customModel, scale);

            /* Sync rotation angles from custom limbs to standard biped fields
             * so DynamX's setModelAttributes() picks up arm/leg/head animation. */
            this.syncCustomModelToBiped(customModel);
        }

        armorModel.setModelAttributes(mainModel);

        /* Let ModelObjArmor.render() go through the DynamX scene graph which
         * calls ArmorNode → renderPart → ArmorRenderer.render → renderGroup
         * with the correct OBJ textures. */
        armorModel.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        GlStateManager.popMatrix();
    }

    /**
     * Apply GL transforms from root/anchor and body limbs so that DynamX armor
     * follows Blockbuster poses (lying, sleeping, sitting, etc.).
     * These parent limbs have no armor slot but their rotation affects all children.
     */
    private void applyParentTransforms(ModelCustom model, float scale)
    {
        float deg = 180F / (float) Math.PI;

        for (ModelCustomRenderer limb : model.limbs)
        {
            /* Find the root anchor (no parent) */
            if (limb.limb.parent.isEmpty())
            {
                /* Apply anchor's position offset relative to default standing.
                 * Default standing anchor Y = -16 + 24 = 8. If a pose changes
                 * the anchor Y, we need to translate by the difference. */
                float defaultY = 8.0F;
                float dy = (limb.rotationPointY - defaultY) * scale;
                if (dy != 0.0F)
                {
                    GlStateManager.translate(0.0F, dy, 0.0F);
                }

                /* Apply anchor rotation (e.g. 90° X for lying) */
                if (limb.rotateAngleZ != 0.0F)
                {
                    GlStateManager.rotate(limb.rotateAngleZ * deg, 0.0F, 0.0F, 1.0F);
                }
                if (limb.rotateAngleY != 0.0F)
                {
                    GlStateManager.rotate(limb.rotateAngleY * deg, 0.0F, 1.0F, 0.0F);
                }
                if (limb.rotateAngleX != 0.0F)
                {
                    GlStateManager.rotate(limb.rotateAngleX * deg, 1.0F, 0.0F, 0.0F);
                }

                break;
            }
        }
    }

    /**
     * Copies rotation angles from ModelCustom's animated custom limbs to the
     * standard ModelBiped fields (bipedHead, bipedBody, bipedLeftArm, etc.)
     * based on each limb's armor slot assignment.
     *
     * IMPORTANT: Only the FIRST limb per biped part is synced. Child/overlay
     * limbs (bodywear, legwear, armwear) often have zero rotation because they
     * inherit their parent's rotation via the GL matrix stack. If we let them
     * overwrite, they'd zero out the correct rotation from the main limb.
     */
    private void syncCustomModelToBiped(ModelCustom model)
    {
        boolean headSet = false;
        boolean bodySet = false;
        boolean leftArmSet = false;
        boolean rightArmSet = false;
        boolean leftLegSet = false;
        boolean rightLegSet = false;

        for (ModelCustomRenderer limb : model.armor)
        {
            switch (limb.limb.slot)
            {
                case HEAD:
                    if (!headSet) { copyAngles(limb, model.bipedHead); headSet = true; }
                    break;
                case CHEST:
                case LEGGINGS:
                    if (!bodySet) { copyAngles(limb, model.bipedBody); bodySet = true; }
                    break;
                case LEFT_SHOULDER:
                    if (!leftArmSet) { copyAngles(limb, model.bipedLeftArm); leftArmSet = true; }
                    break;
                case RIGHT_SHOULDER:
                    if (!rightArmSet) { copyAngles(limb, model.bipedRightArm); rightArmSet = true; }
                    break;
                case LEFT_LEG:
                    if (!leftLegSet) { copyAngles(limb, model.bipedLeftLeg); leftLegSet = true; }
                    break;
                case RIGHT_LEG:
                    if (!rightLegSet) { copyAngles(limb, model.bipedRightLeg); rightLegSet = true; }
                    break;
                case LEFT_FOOT:
                    if (!leftLegSet) { copyAngles(limb, model.bipedLeftLeg); leftLegSet = true; }
                    break;
                case RIGHT_FOOT:
                    if (!rightLegSet) { copyAngles(limb, model.bipedRightLeg); rightLegSet = true; }
                    break;
                default:
                    break;
            }
        }
    }

    private static void copyAngles(ModelRenderer source, ModelRenderer target)
    {
        target.rotateAngleX = source.rotateAngleX;
        target.rotateAngleY = source.rotateAngleY;
        target.rotateAngleZ = source.rotateAngleZ;
    }


    private void renderArmorSlot(EntityLivingBase entity, ItemStack stack, ItemArmor item, ModelCustomRenderer limb, EntityEquipmentSlot slot, float partialTicks, float scale)
    {
        ModelBiped model = this.getModelFromSlot(slot);
        model = this.getArmorModelHook(entity, stack, slot, model);

        if (model == null)
        {
            return;
        }

        GlStateManager.pushMatrix();

        model.setModelAttributes(this.renderer.getMainModel());
        this.renderer.bindTexture(this.getArmorResource(entity, stack, slot, null));
        limb.postRender(scale);

        ModelRenderer renderer = this.setModelSlotVisible(model, limb.limb, limb.limb.slot);

        if (renderer != null)
        {
            GlStateManager.enableRescaleNormal();

            if (item.hasOverlay(stack))
            {
                int i = item.getColor(stack);
                float r = (float) (i >> 16 & 255) / 255F;
                float g = (float) (i >> 8 & 255) / 255F;
                float b = (float) (i & 255) / 255F;

                GlStateManager.color(r, g, b, 1);
                renderer.render(scale);
                this.renderer.bindTexture(this.getArmorResource(entity, stack, slot, "overlay"));
            }

            GlStateManager.color(1, 1, 1, 1);
            renderer.render(scale);

            GlStateManager.disableRescaleNormal();

            if (stack.hasEffect())
            {
                this.renderMyEnchantedGlint(this.renderer, entity, renderer, partialTicks, scale);
            }
        }

        GlStateManager.popMatrix();
    }

    private void renderMyEnchantedGlint(RenderLivingBase<?> layer, EntityLivingBase entity, ModelRenderer renderer, float partialTicks, float p_188364_9_)
    {
        float timer = entity.ticksExisted + partialTicks;

        layer.bindTexture(ENCHANTED_ITEM_GLINT_RES);
        Minecraft.getMinecraft().entityRenderer.setupFogColor(true);

        GlStateManager.enableBlend();
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.color(0.5F, 0.5F, 0.5F, 1.0F);

        for (int iter = 0; iter < 2; ++iter)
        {
            GlStateManager.disableLighting();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_COLOR, GlStateManager.DestFactor.ONE);
            GlStateManager.color(0.38F, 0.19F, 0.608F, 1.0F);
            GlStateManager.matrixMode(5890);
            GlStateManager.loadIdentity();
            GlStateManager.scale(0.33333334F, 0.33333334F, 0.33333334F);
            GlStateManager.rotate(30.0F - iter * 60.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.translate(0.0F, timer * (0.001F + iter * 0.003F) * 20.0F, 0.0F);
            GlStateManager.matrixMode(5888);
            renderer.render(p_188364_9_);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        }

        GlStateManager.matrixMode(5890);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(5888);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableBlend();

        Minecraft.getMinecraft().entityRenderer.setupFogColor(false);
    }

    protected ModelRenderer setModelSlotVisible(ModelBiped model, ModelLimb limb, ArmorSlot slot)
    {
        model.bipedBody.setRotationPoint(0, 0, 0);
        model.bipedHead.setRotationPoint(0, 0, 0);
        model.bipedHeadwear.setRotationPoint(0, 0, 0);
        model.bipedLeftArm.setRotationPoint(-0.1F, 0, 0);
        model.bipedRightArm.setRotationPoint(0.1F, 0, 0);
        model.bipedLeftLeg.setRotationPoint(0, 0, 0);
        model.bipedRightLeg.setRotationPoint(0, 0, 0);

        model.setVisible(false);

        int w = limb.size[0];
        int h = limb.size[1];
        int d = limb.size[2];

        float ww = w / 8F;
        float hh = h / 8F;
        float dd = d / 8F;

        float offsetX = limb.anchor[0] * ww / 2;
        float offsetY = limb.anchor[1] * hh / 2;
        float offsetZ = limb.anchor[2] * dd / 2;

        GlStateManager.translate(-ww / 4 + offsetX, hh / 4 - offsetY, dd / 4 - offsetZ);

        if (slot == ArmorSlot.HEAD)
        {
            GlStateManager.scale(w / 8F, h / 8F, d / 8F);
            model.bipedHead.showModel = true;
            model.bipedHead.setRotationPoint(0, 4, 0);

            return model.bipedHead;
        }
        else if (slot == ArmorSlot.CHEST)
        {
            GlStateManager.scale(w / 8F, h / 12F, d / 4F);
            model.bipedBody.showModel = true;
            model.bipedBody.setRotationPoint(0, -6, 0);

            return model.bipedBody;
        }
        else if (slot == ArmorSlot.LEFT_SHOULDER)
        {
            GlStateManager.scale(w / 4F, h / 12F, d / 4F);
            model.bipedLeftArm.showModel = true;
            model.bipedLeftArm.setRotationPoint(-1, -4, 0);

            return model.bipedLeftArm;
        }
        else if (slot == ArmorSlot.RIGHT_SHOULDER)
        {
            GlStateManager.scale(w / 4F, h / 12F, d / 4F);
            model.bipedRightArm.showModel = true;
            model.bipedRightArm.setRotationPoint(1, -4, 0);

            return model.bipedRightArm;
        }
        else if (slot == ArmorSlot.LEGGINGS)
        {
            GlStateManager.scale(w / 8F, h / 12F, d / 4F);
            model.bipedBody.showModel = true;
            model.bipedBody.setRotationPoint(0, -6, 0);

            return model.bipedBody;
        }
        else if (slot == ArmorSlot.LEFT_LEG)
        {
            GlStateManager.scale(w / 4F, h / 12F, d / 4F);
            model.bipedLeftLeg.showModel = true;
            model.bipedLeftLeg.setRotationPoint(0, -6, 0);

            return model.bipedLeftLeg;
        }
        else if (slot == ArmorSlot.RIGHT_LEG)
        {
            GlStateManager.scale(w / 4F, h / 12F, d / 4F);
            model.bipedRightLeg.showModel = true;
            model.bipedRightLeg.setRotationPoint(0, -6, 0);

            return model.bipedRightLeg;
        }
        else if (slot == ArmorSlot.LEFT_FOOT)
        {
            GlStateManager.scale(w / 4F, h / 12F, d / 4F);
            model.bipedLeftLeg.showModel = true;
            model.bipedLeftLeg.setRotationPoint(0, -6, 0);

            return model.bipedLeftLeg;
        }
        else if (slot == ArmorSlot.RIGHT_FOOT)
        {
            GlStateManager.scale(w / 4F, h / 12F, d / 4F);
            model.bipedRightLeg.showModel = true;
            model.bipedRightLeg.setRotationPoint(0, -6, 0);

            return model.bipedRightLeg;
        }

        return null;
    }

    @Override
    protected void setModelSlotVisible(ModelBiped p_188359_1_, EntityEquipmentSlot slotIn)
    {}

    @Override
    protected ModelBiped getArmorModelHook(net.minecraft.entity.EntityLivingBase entity, net.minecraft.item.ItemStack itemStack, EntityEquipmentSlot slot, ModelBiped model)
    {
        return net.minecraftforge.client.ForgeHooksClient.getArmorModel(entity, itemStack, slot, model);
    }
}