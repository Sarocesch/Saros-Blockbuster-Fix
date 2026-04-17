package mchorse.blockbuster.recording.mwf;

import com.modularwarfare.api.WeaponFireEvent;
import com.modularwarfare.common.guns.GunType;
import com.modularwarfare.common.guns.manager.FireManager;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.MWFFireAction;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * MWF event listener for Blockbuster recording.
 *
 * Only registered on the Forge event bus when MWF is installed (checked at
 * mod init via ActionHandler). Safe to hard-import MWF classes here because
 * the class is never loaded without MWF present.
 *
 * Listens on {@link WeaponFireEvent.Pre} at LOWEST priority so we capture
 * the weapon info before other listeners can modify/cancel it. We never
 * cancel or modify the event ourselves — recording is passive.
 */
public class MWFCompatHandler
{
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onWeaponFire(WeaponFireEvent.Pre event)
    {
        EntityLivingBase shooter = event.shooter;
        if (!(shooter instanceof EntityPlayer)) return;
        if (shooter.world.isRemote) return;

        EntityPlayer player = (EntityPlayer) shooter;
        List<Action> actions = CommonProxy.manager.getActions(player);
        if (actions == null) return;

        FireManager.FireData fireData = event.fireData;
        if (fireData == null) return;

        GunType gunType = fireData.gunType;
        if (gunType == null || gunType.internalName == null) return;

        actions.add(new MWFFireAction(gunType.internalName, gunType.fireTickDelay));
    }
}
