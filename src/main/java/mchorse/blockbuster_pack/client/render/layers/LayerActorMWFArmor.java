package mchorse.blockbuster_pack.client.render.layers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import mchorse.blockbuster.recording.mwf.MWFCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

/**
 * Draws ModularWarfare's custom vests on an actor.
 *
 * MWF's own RenderLayerBody cannot be reused here: it is typed on EntityPlayer
 * and RenderPlayer and reads the vest out of MWF's extra slot capability, which
 * an actor does not have. So the vest is pushed to the client by
 * {@link mchorse.blockbuster.recording.actions.MWFExtraSlotAction}, kept here
 * per entity ID, and drawn with the same sequence MWF uses - anchored to the
 * body bone, not to the entity, so Blockbuster poses carry it along.
 *
 * Everything is resolved reflectively; without MWF the layer draws nothing.
 */
@SideOnly(Side.CLIENT)
public class LayerActorMWFArmor implements LayerRenderer<EntityLivingBase>
{
    /** Vests of currently replaying actors, by entity ID. */
    private static final Map<Integer, ItemStack> VESTS = new HashMap<Integer, ItemStack>();

    /** Entities already reported, so the diagnosis does not spam every frame. */
    private static final java.util.Set<Integer> REPORTED = new java.util.HashSet<Integer>();

    private static boolean resolved;
    private static boolean available;
    private static Class<?> classItemSpecialArmor;
    private static Class<?> classItemMWArmor;
    private static Field fieldMWArmorType;   /* ItemMWArmor.type */
    private static Field fieldArmorType;      /* ItemSpecialArmor.type */
    private static Method methodHasModel;     /* BaseType.hasModel() */
    private static Field fieldBipedModel;     /* BaseType.bipedModel */
    private static Field fieldModelSkins;     /* BaseType.modelSkins */
    private static Method methodGetSkin;      /* SkinType.getSkin() */
    private static Method methodRenderArmor;  /* ModelCustomArmor.render(String, ModelRenderer, float, float) */
    private static Method methodPostRender;   /* WoundPartFrame.postRender(ModelRenderer, float) */

    private final RenderLivingBase<?> renderer;

    public LayerActorMWFArmor(RenderLivingBase<?> renderer)
    {
        this.renderer = renderer;
    }

    /**
     * Called from the packet handler when an actor's extra slot changes.
     */
    public static void put(int entityId, int slot, ItemStack stack)
    {
        if (slot != MWFCompat.SLOT_VEST) return;

        if (stack == null || stack.isEmpty())
        {
            VESTS.remove(entityId);
        }
        else
        {
            VESTS.put(entityId, stack);
        }
    }

    public static void clear()
    {
        VESTS.clear();
        REPORTED.clear();
    }

    private static boolean resolve()
    {
        if (resolved) return available;
        resolved = true;

        try
        {
            classItemSpecialArmor = Class.forName("com.modularwarfare.common.armor.ItemSpecialArmor");
            fieldArmorType = classItemSpecialArmor.getField("type");

            /* The custom vests are worn in a normal armor slot as ItemMWArmor,
             * not in MWF's extra slots. Both expose a public "type" field. */
            classItemMWArmor = Class.forName("com.modularwarfare.common.armor.ItemMWArmor");
            fieldMWArmorType = classItemMWArmor.getField("type");

            Class<?> classBaseType = Class.forName("com.modularwarfare.common.type.BaseType");
            methodHasModel = classBaseType.getMethod("hasModel");
            fieldBipedModel = classBaseType.getField("bipedModel");
            fieldModelSkins = classBaseType.getField("modelSkins");

            Class<?> classSkinType = Class.forName("com.modularwarfare.common.guns.SkinType");
            methodGetSkin = classSkinType.getMethod("getSkin");

            Class<?> classModelCustomArmor = Class.forName("com.modularwarfare.client.model.ModelCustomArmor");
            methodRenderArmor = classModelCustomArmor.getMethod("render", String.class, ModelRenderer.class, float.class, float.class);

            Class<?> classWoundPartFrame = Class.forName("com.modularwarfare.client.wounds.WoundPartFrame");
            methodPostRender = classWoundPartFrame.getMethod("postRender", ModelRenderer.class, float.class);

            available = true;
        }
        catch (Throwable t)
        {
            available = false;
        }

        return available;
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale)
    {
        if (entity == null) return;

        boolean report = REPORTED.add(entity.getEntityId());

        if (!resolve())
        {
            if (report) System.out.println("[Blockbuster] MWF armor: MWF classes not resolved");

            return;
        }

        /* Two sources, because MWF has two armor item classes: ItemMWArmor sits
         * in a normal armor slot (that is where the custom vests actually are)
         * and ItemSpecialArmor sits in MWF's extra slots. */
        ItemStack stack = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        Field typeField = fieldMWArmorType;

        if (report)
        {
            System.out.println("[Blockbuster] MWF armor: entity=" + entity.getEntityId()
                    + " chest=" + (stack.isEmpty() ? "<empty>" : stack.getItem().getClass().getName())
                    + " extraSlot=" + (VESTS.get(entity.getEntityId()) == null ? "<none>" : "present"));
        }

        if (stack.isEmpty() || !classItemMWArmor.isInstance(stack.getItem()))
        {
            stack = VESTS.get(entity.getEntityId());
            typeField = fieldArmorType;

            if (stack == null || stack.isEmpty() || !classItemSpecialArmor.isInstance(stack.getItem()))
            {
                if (report) System.out.println("[Blockbuster] MWF armor: no MWF armor on this entity");

                return;
            }
        }

        ModelBiped model = this.renderer.getMainModel() instanceof ModelBiped ? (ModelBiped) this.renderer.getMainModel() : null;

        if (model == null || model.bipedBody == null) return;

        Object armorModel;
        Object skin;

        /* Resolve everything BEFORE touching the GL stack, so a reflection
         * failure can never leave a pushed matrix behind. */
        try
        {
            Object armorType = typeField.get(stack.getItem());

            if (armorType == null || !Boolean.TRUE.equals(methodHasModel.invoke(armorType)))
            {
                if (report) System.out.println("[Blockbuster] MWF armor: armorType has no model");

                return;
            }

            armorModel = fieldBipedModel.get(armorType);

            Object[] skins = (Object[]) fieldModelSkins.get(armorType);

            if (armorModel == null || skins == null || skins.length == 0) return;

            skin = methodGetSkin.invoke(skins[0]);

            if (skin == null) return;
        }
        catch (Throwable t)
        {
            if (report) System.out.println("[Blockbuster] MWF armor: resolving failed: " + t);

            return;
        }

        GlStateManager.pushMatrix();

        try
        {
            if (entity.isSneaking())
            {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }

            /* Anchor on the body bone the same way MWF does, so every pose the
             * actor is in carries the vest instead of leaving it in the air. */
            methodPostRender.invoke(null, model.bipedBody, 0.0625F);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableRescaleNormal();

            Minecraft.getMinecraft().getRenderManager().renderEngine.bindTexture(
                    new ResourceLocation("modularwarfare", "skins/armor/" + skin + ".png"));

            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            methodRenderArmor.invoke(armorModel, "armorModel", model.bipedBody, 0.0625F, 1.0F);
        }
        catch (Throwable ignored)
        {
            /* Never let a cosmetic layer take the whole render pass down. */
        }
        finally
        {
            /* Restore the state this layer changed, in every path. */
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.disableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures()
    {
        return true;
    }
}
