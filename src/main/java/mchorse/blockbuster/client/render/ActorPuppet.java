package mchorse.blockbuster.client.render;

import com.mojang.authlib.GameProfile;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.mwf.MWFCompat;
import mchorse.blockbuster_pack.client.render.layers.LayerActorMWFArmor;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/**
 * Unsichtbarer Stellvertreter-Spieler, mit dem ein Actor als echtes Spielermodell
 * gezeichnet wird (Replay-Schalter "Skin des Aufnehmenden").
 *
 * <p>CustomPlayerModels greift nur bei RenderPlayer und sucht das Modell ueber das
 * GameProfile. Blockbusters ModelCustom sieht CPM nie. Dieser Spieler kommt nie in
 * die Welt; er uebernimmt jedes Bild Pose und Ausruestung des Actors.</p>
 *
 * <p>getRidingEntity und getUniqueID zeigen auf den Actor: DynamX zeichnet Mitfahrer
 * selbst und blendet sie im normalen Durchgang per Referenzvergleich der UUID aus -
 * so verhaelt sich der Stellvertreter dabei genau wie der Actor.</p>
 */
@SideOnly(Side.CLIENT)
public class ActorPuppet extends EntityOtherPlayerMP
{
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");

    public final EntityActor actor;
    public final String hash;
    public ResourceLocation skin;
    public boolean slim;
    public boolean hideOuter;

    private ItemStack lastVest = ItemStack.EMPTY;

    public ActorPuppet(EntityActor actor, String hash, String name)
    {
        super(actor.world, new GameProfile(UUID.nameUUIDFromBytes(("bb-appearance:" + hash).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));

        this.actor = actor;
        this.hash = hash;
    }

    @Override
    public ResourceLocation getLocationSkin()
    {
        return this.skin != null ? this.skin : (this.slim ? ALEX : STEVE);
    }

    @Override
    public String getSkinType()
    {
        return this.slim ? "slim" : "default";
    }

    @Override
    public boolean hasPlayerInfo()
    {
        return true;
    }

    @Override
    public ResourceLocation getLocationCape()
    {
        return null;
    }

    @Override
    public ResourceLocation getLocationElytra()
    {
        return null;
    }

    @Override
    public boolean isSpectator()
    {
        return false;
    }

    @Override
    public boolean isWearing(EnumPlayerModelParts part)
    {
        if (part == EnumPlayerModelParts.CAPE)
        {
            return false;
        }

        /* Die Hutschicht traegt bei vielen Skins die Haare - die bleibt. Kleidung
         * schaut durch Jacke, Aermel und Hose, nicht durch den Kopf. */
        return part == EnumPlayerModelParts.HAT || !this.hideOuter;
    }

    @Override
    public Entity getRidingEntity()
    {
        return this.actor == null ? super.getRidingEntity() : this.actor.getRidingEntity();
    }

    @Override
    public UUID getUniqueID()
    {
        /* Waehrend des Konstruktors (Capabilities) ist actor noch null. */
        return this.actor == null ? super.getUniqueID() : this.actor.getUniqueID();
    }

    /**
     * Pose, Bewegung und Ausruestung vom Actor uebernehmen.
     */
    public void mirror()
    {
        EntityActor target = this.actor;

        this.posX = target.posX;
        this.posY = target.posY;
        this.posZ = target.posZ;
        this.prevPosX = target.prevPosX;
        this.prevPosY = target.prevPosY;
        this.prevPosZ = target.prevPosZ;
        this.lastTickPosX = target.lastTickPosX;
        this.lastTickPosY = target.lastTickPosY;
        this.lastTickPosZ = target.lastTickPosZ;

        this.rotationYaw = target.rotationYaw;
        this.rotationPitch = target.rotationPitch;
        this.rotationYawHead = target.rotationYawHead;
        this.renderYawOffset = target.renderYawOffset;
        this.prevRotationYaw = target.prevRotationYaw;
        this.prevRotationPitch = target.prevRotationPitch;
        this.prevRotationYawHead = target.prevRotationYawHead;
        this.prevRenderYawOffset = target.prevRenderYawOffset;

        this.motionX = target.motionX;
        this.motionY = target.motionY;
        this.motionZ = target.motionZ;

        this.limbSwing = target.limbSwing;
        this.limbSwingAmount = target.limbSwingAmount;
        this.prevLimbSwingAmount = target.prevLimbSwingAmount;
        this.isSwingInProgress = target.isSwingInProgress;
        this.swingProgress = target.swingProgress;
        this.prevSwingProgress = target.prevSwingProgress;
        this.swingingHand = target.swingingHand;

        this.ticksExisted = target.ticksExisted;
        this.hurtTime = target.hurtTime;
        this.deathTime = target.deathTime;
        this.onGround = target.onGround;

        this.rotateElytraX = target.rotateElytraX;
        this.rotateElytraY = target.rotateElytraY;
        this.rotateElytraZ = target.rotateElytraZ;

        if (this.isSneaking() != target.isSneaking()) this.setSneaking(target.isSneaking());
        if (this.isSprinting() != target.isSprinting()) this.setSprinting(target.isSprinting());
        if (this.isElytraFlying() != target.isElytraFlying()) this.setFlag(7, target.isElytraFlying());

        this.mirrorEquipment(target);
        this.mirrorActiveHand(target);

        ItemStack vest = LayerActorMWFArmor.getVest(target.getEntityId());

        if (vest != this.lastVest)
        {
            this.lastVest = vest;
            MWFCompat.setExtraSlotStack(this, MWFCompat.SLOT_VEST, vest);
        }

        MWFCompat.mirrorMovementStateClient(target, this);
    }

    /**
     * Direkt ins Inventar, nur bei Aenderung - setItemStackToSlot spielt bei jedem
     * Aufruf den Anlege-Sound.
     */
    private void mirrorEquipment(EntityActor target)
    {
        ItemStack main = target.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);

        if (this.inventory.mainInventory.get(this.inventory.currentItem) != main)
        {
            this.inventory.mainInventory.set(this.inventory.currentItem, main);
        }

        ItemStack off = target.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);

        if (this.inventory.offHandInventory.get(0) != off)
        {
            this.inventory.offHandInventory.set(0, off);
        }

        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values())
        {
            if (slot.getSlotType() != EntityEquipmentSlot.Type.ARMOR)
            {
                continue;
            }

            ItemStack stack = target.getItemStackFromSlot(slot);

            if (this.inventory.armorInventory.get(slot.getIndex()) != stack)
            {
                this.inventory.armorInventory.set(slot.getIndex(), stack);
            }
        }
    }

    /**
     * Bogen spannen, essen, blocken: das liest RenderPlayer ueber die aktive Hand.
     */
    private void mirrorActiveHand(EntityActor target)
    {
        Byte flags = target.getDataManager().get(HAND_STATES);

        if (!flags.equals(this.dataManager.get(HAND_STATES)))
        {
            this.dataManager.set(HAND_STATES, flags);
        }

        this.activeItemStack = target.getActiveItemStack();
        this.activeItemStackUseCount = target.getItemInUseCount();
    }
}
