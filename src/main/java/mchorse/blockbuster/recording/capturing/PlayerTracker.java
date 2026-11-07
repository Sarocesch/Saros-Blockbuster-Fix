package mchorse.blockbuster.recording.capturing;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.recording.RecordRecorder;
import mchorse.blockbuster.recording.actions.AttackAction;
import mchorse.blockbuster.recording.actions.CloseContainerAction;
import mchorse.blockbuster.recording.actions.EquipAction;
import mchorse.blockbuster.recording.actions.HotbarChangeAction;
import mchorse.blockbuster.recording.actions.SwipeAction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Player tracker class
 *
 * This class tracks player's properties such as arm swing and player equipped
 * inventory. That's it.
 */
public class PlayerTracker
{
    /**
     * Record recorder to which tracked stuff are going to be added
     */
    public RecordRecorder recorder;

    /* Items to track */
    private ItemStack[] items = new ItemStack[6];
    /* MW-Waffenlicht/Laser werden am selben ItemStack per NBT umgeschaltet - die Referenz bleibt gleich */
    private int[] lightFlags = new int[6];
    private ItemStack[] hotbar = new ItemStack[9];
    /**
     * Runtime variable to track the complete hotbar for the fist frame
     */
    private boolean trackedHotbar = false;
    
    private Container container;

    public PlayerTracker(RecordRecorder recorder)
    {
        this.recorder = recorder;

        Arrays.fill(hotbar, ItemStack.EMPTY);
    }

    /**
     * Track player's properties like armor, hand held items and hand swing
     */
    public void track(EntityPlayer player)
    {
        this.trackSwing(player);
        this.trackHeldItem(player);
        this.trackHotBar(player);
        this.trackArmor(player);
        this.trackContainerClose(player);
    }

    /**
     * Track armor inventory
     */
    private void trackArmor(EntityPlayer player)
    {
        for (int i = 1; i < 5; i++)
        {
            this.trackItemToSlot(player.inventory.armorInventory.get(i - 1), i);
        }
    }

    /**
     * Track held items
     */
    private void trackHeldItem(EntityPlayer player)
    {
        ItemStack mainhand = player.getHeldItemMainhand();
        ItemStack offhand = player.getHeldItemOffhand();

        this.trackItemToSlot(mainhand, 0, player.inventory.currentItem);
        this.trackItemToSlot(offhand, 5);
    }

    private void trackHotBar(EntityPlayer player)
    {
        List<ItemStack> playerHotbar = player.inventory.mainInventory;

        for (int i = 0; i < playerHotbar.size() && i < this.hotbar.length; i++)
        {
            if (!ItemStack.areItemStacksEqual(this.hotbar[i], playerHotbar.get(i)) || !this.trackedHotbar)
            {
                this.recorder.actions.add(new HotbarChangeAction(i, playerHotbar.get(i).copy()));

                this.hotbar[i] = playerHotbar.get(i).copy();
            }
        }

        this.trackedHotbar = true;
    }

    /**
     * Track item to slot.
     *
     * This is a simple utility method that reduces number of lines for both
     * hands.
     */
    private boolean trackItemToSlot(ItemStack item, int slot)
    {
        return this.trackItemToSlot(item, slot, -1);
    }

    private boolean trackItemToSlot(ItemStack item, int slot, int hotbarslot)
    {
        if (!item.isEmpty())
        {
            int flags = lightFlags(item);

            if (item != this.items[slot] || flags != this.lightFlags[slot])
            {
                this.items[slot] = item;
                this.lightFlags[slot] = flags;
                /* Kopie: writeToNBT teilt sonst das NBT mit der echten Waffe, gespeichert wuerde der Zustand vom Aufnahme-Ende */
                ItemStack snapshot = item.copy();
                this.recorder.actions.add((hotbarslot != -1) ? new EquipAction((byte) slot,(byte) hotbarslot, snapshot) : new EquipAction((byte) slot, snapshot));

                return true;
            }
        }
        else if (this.items[slot] != null)
        {
            this.items[slot] = null;
            this.recorder.actions.add((hotbarslot != -1) ? new EquipAction((byte) slot,(byte) hotbarslot, null) : new EquipAction((byte) slot, null));

            return true;
        }

        return false;
    }

    private static int lightFlags(ItemStack item)
    {
        if (!item.hasTagCompound())
        {
            return 0;
        }

        /* 4: Light-Addon-Taschenlampe (NBT "On"), wird ebenfalls am selben Stack umgeschaltet */
        return (item.getTagCompound().getBoolean("flashlight_enabled") ? 1 : 0)
            | (item.getTagCompound().getBoolean("laser_enabled") ? 2 : 0)
            | (item.getTagCompound().getBoolean("On") ? 4 : 0);
    }

    /**
     * Track the hand swing (like when you do the tap-tap with left-click)
     */
    private void trackSwing(EntityPlayer player)
    {
        /**
         * player.isPlayerSleeping() is necessary since for some reason when 
         * player is falling asleep, the isSwingInProgress is true, while 
         * player.swingProgress is equals to 0 which makes player swipe every 
         * tick in bed.
         * 
         * So gotta check that so it wouldn't look like Steve is beating his 
         * meat.
         */
        if (player.isSwingInProgress && player.swingProgress == 0 && !player.isPlayerSleeping())
        {
            this.recorder.actions.add(new SwipeAction());
            this.trackLittleTilesSwing(player);

            if (Blockbuster.recordAttackOnSwipe.get())
            {
                this.recorder.actions.add(new AttackAction());
            }
        }
    }

    /** Welt-Tick des letzten aufgenommenen Block-Klicks. */
    private long lastClick = -100;

    /**
     * Rechtsklick auf eine offene LittleTiles-Tuer (Animation): LittleTiles
     * faengt ihn so frueh ab, dass weder Server noch Client ein Klick-Ereignis
     * sehen - uebrig bleibt nur der Armschwung. Trifft der Blick dabei eine
     * Tuer-Animation, wird daraus der Tuer-Klick. Kurz nach einem anderen
     * Klick nicht, sonst schloesse der Schwung die gerade geoeffnete Tuer.
     */
    private void trackLittleTilesSwing(EntityPlayer player)
    {
        long now = player.world.getTotalWorldTime();

        for (mchorse.blockbuster.recording.actions.Action action : this.recorder.actions)
        {
            if (action.getClass() == mchorse.blockbuster.recording.actions.InteractBlockAction.class)
            {
                this.lastClick = now;

                return;
            }
        }

        if (now - this.lastClick < 10)
        {
            return;
        }

        net.minecraft.util.math.BlockPos pos = mchorse.blockbuster.recording.LTHelper.animationHit(player);

        if (mchorse.blockbuster.recording.LTHelper.lastChecked > 0)
        {
            System.out.println("[Blockbuster] LittleTiles-Schwung von " + player.getName() + ": "
                + mchorse.blockbuster.recording.LTHelper.lastChecked + " Tuer-Animationen geprueft, Treffer " + pos);
        }

        if (pos != null)
        {
            this.lastClick = now;
            ActionHandler.addLittleTilesClick(this.recorder.actions, pos);
        }
    }

    /**
     * Track whether player has closed a container
     */
    private void trackContainerClose(EntityPlayer player)
    {
        if (container != null && player.openContainer == player.inventoryContainer && container != player.openContainer)
        {
            this.recorder.actions.add(new CloseContainerAction());
        }
        this.container = player.openContainer;
    }
}