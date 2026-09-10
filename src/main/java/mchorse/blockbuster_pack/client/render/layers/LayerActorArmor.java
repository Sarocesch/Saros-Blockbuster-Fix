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

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
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

    private static final float DEG = 180F / (float) Math.PI;

    /** Reused per doRenderLayer call to avoid per-frame allocation. Cleared before use. */
    private final Set<EntityEquipmentSlot> renderedDynamXSlots = new HashSet<EntityEquipmentSlot>();

    /** Armor roles already drawn this frame, so two limbs sharing a role draw one piece. */
    private final Set<ArmorSlot> drawnDynamXParts = new HashSet<ArmorSlot>();

    /** Which custom limb carries which armor role. First limb in the model wins. */
    private final Map<ArmorSlot, ModelCustomRenderer> anchors = new EnumMap<ArmorSlot, ModelCustomRenderer>(ArmorSlot.class);

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale)
    {
        ModelBase base = this.renderer.getMainModel();

        if (base instanceof ModelCustom)
        {
            ModelCustom model = (ModelCustom) base;

            this.renderedDynamXSlots.clear();
            this.drawnDynamXParts.clear();
            this.anchors.clear();

            for (ModelCustomRenderer limb : model.armor)
            {
                if (!this.anchors.containsKey(limb.limb.slot))
                {
                    this.anchors.put(limb.limb.slot, limb);
                }
            }

            for (ModelCustomRenderer limb : model.armor)
            {
                ItemStack stack = entity.getItemStackFromSlot(limb.limb.slot.slot);

                if (stack != null && stack.getItem() instanceof ItemArmor)
                {
                    ItemArmor item = (ItemArmor) stack.getItem();

                    if (item.getEquipmentSlot() == limb.limb.slot.slot)
                    {
                        /* Resolve armor model once — avoids calling getArmorModelHook twice
                         * (previously called here AND again inside renderArmorSlot). */
                        ModelBiped defaultModel = this.getModelFromSlot(limb.limb.slot.slot);
                        ModelBiped armorModel = this.getArmorModelHook(entity, stack, limb.limb.slot.slot, defaultModel);

                        if (armorModel != null && armorModel.getClass().getName().equals(DYNAMX_ARMOR_CLASS))
                        {
                            /* Draw each DynamX piece inside the custom limb that carries its
                             * role. Only when every piece of this equipment slot has a limb to
                             * hang on, otherwise pieces would silently go missing and the
                             * whole-model path is the better guess. */
                            if (this.canAnchorEveryPart(armorModel, limb.limb.slot.slot))
                            {
                                if (this.drawnDynamXParts.add(limb.limb.slot))
                                {
                                    this.renderDynamXPartInLimb(model, armorModel, limb, scale);
                                }
                            }
                            else if (this.renderedDynamXSlots.add(limb.limb.slot.slot))
                            {
                                this.renderDynamXArmorSlot(entity, armorModel, limb.limb.slot.slot, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
                            }
                        }
                        else
                        {
                            /* Standard (vanilla) armor rendering — pass pre-resolved model. */
                            this.renderArmorSlot(entity, stack, item, limb, limb.limb.slot.slot, partialTicks, scale, armorModel);
                        }
                    }
                }
            }
        }
    }

    /* --- DynamX armor, drawn limb by limb ---------------------------------
     *
     * ModelObjArmor is written for a vanilla player: it reads the ModelBiped
     * bones every frame, and while sneaking ArmorNode adds a fixed
     * translate(0, 0.2, 0). A custom model poses its own limbs and leaves the
     * biped bones untouched, so that path can only guess - hence the torso
     * sitting too low and the wrong rotation while sneaking.
     *
     * ArmorRenderer.render is a pivot: translate by rotationPoint, flip 180
     * degrees around X into the OBJ's space, rotate, then translate by
     * (-offsetX, +offsetY, -offsetZ), offset being the REST position of the
     * bone the piece belongs to. Placing the matrix on the limb already covers
     * position and rotation, so rotationPoint and the angles go to zero and
     * only offset is set - to the limb's rest pivot, taken from the standing
     * pose. Its current pivot would cancel out and the piece would never move.
     */

    private static boolean dynamxFieldsResolved;
    private static Field fieldHead;
    private static Field fieldBody;
    private static Field fieldArms;
    private static Field fieldLegs;
    private static Field fieldFoot;

    private static boolean resolveDynamXFields(Class<?> clazz)
    {
        if (dynamxFieldsResolved)
        {
            return fieldBody != null;
        }

        dynamxFieldsResolved = true;

        try
        {
            fieldHead = clazz.getDeclaredField("head");
            fieldBody = clazz.getDeclaredField("body");
            fieldArms = clazz.getDeclaredField("arms");
            fieldLegs = clazz.getDeclaredField("legs");
            fieldFoot = clazz.getDeclaredField("foot");

            fieldHead.setAccessible(true);
            fieldBody.setAccessible(true);
            fieldArms.setAccessible(true);
            fieldLegs.setAccessible(true);
            fieldFoot.setAccessible(true);
        }
        catch (Throwable t)
        {
            fieldHead = fieldBody = fieldArms = fieldLegs = fieldFoot = null;
            System.out.println("[Blockbuster] DynamX armor parts unreachable, falling back to the whole-model path: " + t);
        }

        return fieldBody != null;
    }

    /**
     * @return true when every piece this armor has for the slot has a limb to hang on
     */
    private boolean canAnchorEveryPart(ModelBiped armorModel, EntityEquipmentSlot slot)
    {
        if (!resolveDynamXFields(armorModel.getClass()))
        {
            return false;
        }

        try
        {
            switch (slot)
            {
                case HEAD:
                    return fieldHead.get(armorModel) == null || this.anchors.containsKey(ArmorSlot.HEAD);

                case CHEST:
                    if (fieldBody.get(armorModel) != null && !this.anchors.containsKey(ArmorSlot.CHEST))
                    {
                        return false;
                    }

                    return fieldArms.get(armorModel) == null
                        || (this.anchors.containsKey(ArmorSlot.LEFT_SHOULDER) && this.anchors.containsKey(ArmorSlot.RIGHT_SHOULDER));

                case LEGS:
                    return fieldLegs.get(armorModel) == null
                        || (this.anchors.containsKey(ArmorSlot.LEFT_LEG) && this.anchors.containsKey(ArmorSlot.RIGHT_LEG));

                case FEET:
                    return fieldFoot.get(armorModel) == null
                        || (this.anchors.containsKey(ArmorSlot.LEFT_FOOT) && this.anchors.containsKey(ArmorSlot.RIGHT_FOOT));

                default:
                    return false;
            }
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private void renderDynamXPartInLimb(ModelCustom model, ModelBiped armorModel, ModelCustomRenderer limb, float scale)
    {
        ModelRenderer part = this.getDynamXPart(armorModel, limb.limb.slot);

        if (part == null)
        {
            return;
        }

        float[] rest = restPivot(model, limb);

        /* ModelObjArmor belongs to the armor TYPE and is shared by everyone
         * wearing it. Every field written here has to go back, or the next
         * wearer - a real player - inherits the actor's values. */
        float pointX = part.rotationPointX;
        float pointY = part.rotationPointY;
        float pointZ = part.rotationPointZ;
        float angleX = part.rotateAngleX;
        float angleY = part.rotateAngleY;
        float angleZ = part.rotateAngleZ;
        float offsetX = part.offsetX;
        float offsetY = part.offsetY;
        float offsetZ = part.offsetZ;

        GlStateManager.pushMatrix();

        try
        {
            part.rotationPointX = 0F;
            part.rotationPointY = 0F;
            part.rotationPointZ = 0F;
            part.rotateAngleX = 0F;
            part.rotateAngleY = 0F;
            part.rotateAngleZ = 0F;

            part.offsetX = rest[0] / 16F;
            part.offsetY = rest[1] / 16F;
            part.offsetZ = -rest[2] / 16F;

            applyLimbChain(limb, scale);
            part.render(scale);
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] DynamX armor piece " + limb.limb.slot + " failed: " + t);
        }
        finally
        {
            part.rotationPointX = pointX;
            part.rotationPointY = pointY;
            part.rotationPointZ = pointZ;
            part.rotateAngleX = angleX;
            part.rotateAngleY = angleY;
            part.rotateAngleZ = angleZ;
            part.offsetX = offsetX;
            part.offsetY = offsetY;
            part.offsetZ = offsetZ;

            GlStateManager.popMatrix();
        }
    }

    /**
     * Same transform ModelCustomRenderer.postRender applies, root limb first,
     * but without its isHidden/showModel check: the limb carrying an armor
     * role may well be a hidden one (the outer skin layer), and skipping the
     * transform would drop the piece at the model's origin.
     */
    private static void applyLimbChain(ModelCustomRenderer limb, float scale)
    {
        if (limb.parent != null)
        {
            applyLimbChain(limb.parent, scale);
        }

        if (limb.rotateAngleX == 0F && limb.rotateAngleY == 0F && limb.rotateAngleZ == 0F)
        {
            if (limb.rotationPointX != 0F || limb.rotationPointY != 0F || limb.rotationPointZ != 0F)
            {
                GlStateManager.translate(limb.rotationPointX * scale, limb.rotationPointY * scale, limb.rotationPointZ * scale);
            }
        }
        else
        {
            GlStateManager.translate(limb.rotationPointX * scale, limb.rotationPointY * scale, limb.rotationPointZ * scale);

            if (limb.rotateAngleZ != 0F) GlStateManager.rotate(limb.rotateAngleZ * DEG, 0F, 0F, 1F);
            if (limb.rotateAngleY != 0F) GlStateManager.rotate(limb.rotateAngleY * DEG, 0F, 1F, 0F);
            if (limb.rotateAngleX != 0F) GlStateManager.rotate(limb.rotateAngleX * DEG, 1F, 0F, 0F);
        }

        GlStateManager.scale(limb.scaleX, limb.scaleY, limb.scaleZ);
    }

    /**
     * The limb's pivot in the standing pose, accumulated over its parents, in
     * the same units and signs ModelCustomRenderer.applyTransform produces.
     */
    private static float[] restPivot(ModelCustom model, ModelCustomRenderer limb)
    {
        float[] result = new float[3];
        ModelPose standing = model == null || model.model == null ? null : model.model.poses.get("standing");

        for (ModelCustomRenderer current = limb; current != null; current = current.parent)
        {
            ModelTransform transform = standing == null || current.limb == null ? null : standing.limbs.get(current.limb.name);

            if (transform == null)
            {
                /* No standing entry - the live pivot is the best reference left. */
                result[0] += current.rotationPointX;
                result[1] += current.rotationPointY;
                result[2] += current.rotationPointZ;

                continue;
            }

            result[0] += transform.translate[0];
            result[1] += current.limb.parent.isEmpty() ? (-transform.translate[1] + 24F) : -transform.translate[1];
            result[2] += -transform.translate[2];
        }

        return result;
    }

    private ModelRenderer getDynamXPart(ModelBiped armorModel, ArmorSlot slot)
    {
        try
        {
            switch (slot)
            {
                case HEAD:           return (ModelRenderer) fieldHead.get(armorModel);
                case CHEST:          return (ModelRenderer) fieldBody.get(armorModel);
                case LEFT_SHOULDER:  return dynamxPart(fieldArms, armorModel, 0);
                case RIGHT_SHOULDER: return dynamxPart(fieldArms, armorModel, 1);
                case LEFT_LEG:       return dynamxPart(fieldLegs, armorModel, 0);
                case RIGHT_LEG:      return dynamxPart(fieldLegs, armorModel, 1);
                case LEFT_FOOT:      return dynamxPart(fieldFoot, armorModel, 0);
                case RIGHT_FOOT:     return dynamxPart(fieldFoot, armorModel, 1);
                /* LEGGINGS carries no piece of its own - DynamX splits the
                 * trousers into the two leg parts, drawn by LEFT/RIGHT_LEG. */
                default:             return null;
            }
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static ModelRenderer dynamxPart(Field field, ModelBiped armorModel, int index) throws Exception
    {
        Object value = field.get(armorModel);

        if (!(value instanceof Object[]))
        {
            return null;
        }

        Object[] parts = (Object[]) value;

        return index < parts.length && parts[index] instanceof ModelRenderer ? (ModelRenderer) parts[index] : null;
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


    private void renderArmorSlot(EntityLivingBase entity, ItemStack stack, ItemArmor item, ModelCustomRenderer limb, EntityEquipmentSlot slot, float partialTicks, float scale, ModelBiped model)
    {
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