package mchorse.blockbuster.recording.dynamx;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.VehicleBasicsAction;
import mchorse.blockbuster.recording.actions.VehicleControlAction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-tick polling helper for DynamX vehicle recording.
 *
 * DynamX's {@code VehicleEntityEvent.ControllerUpdate} event fires on CLIENT
 * side only, so we cannot directly hook it for server-side recording. Instead,
 * each server tick we check whether any currently-recording player is driving
 * a DynamX vehicle and capture the engine's control bitmask when it changes.
 *
 * This class uses only reflection via {@link DynamXCompat}, so it is safe to
 * load/call even when DynamX is absent (it becomes a no-op).
 *
 * Delta-compression: only emits a {@link VehicleControlAction} when the
 * bitmask actually changes since the last tick.
 */
public class DynamXVehicleHandler
{
    /**
     * Last-known control bitmask per recording player. Used for delta
     * compression so we only emit actions when controls actually change.
     */
    private final Map<UUID, Integer> lastControls = new HashMap<UUID, Integer>();

    /**
     * Last-known BasicsAddon state bitmask per recording player (siren,
     * beacons, head lights, turn signals). Tracked separately from the
     * engine controls because it is a separate synchronized variable.
     */
    private final Map<UUID, Integer> lastBasics = new HashMap<UUID, Integer>();

    /**
     * Called each server tick for each player currently being recorded.
     * If the player is driving a DynamX vehicle and its engine controls have
     * changed since the last check, a {@link VehicleControlAction} is added
     * to the player's recording action list.
     */
    public void tickRecordingPlayer(EntityPlayer player)
    {
        if (player == null || player.world == null || player.world.isRemote) return;
        if (!DynamXCompat.isAvailable()) return;

        Entity ridden = player.getRidingEntity();
        UUID id = player.getUniqueID();

        if (!DynamXCompat.isVehicle(ridden))
        {
            /* Not in a DynamX vehicle anymore — forget the last state. */
            this.lastControls.remove(id);
            this.lastBasics.remove(id);
            return;
        }

        int controls = DynamXCompat.getVehicleControls(ridden);
        if (controls == -1) return;

        Integer previous = this.lastControls.get(id);
        if (previous == null || previous.intValue() != controls)
        {
            List<Action> events = CommonProxy.manager.getActions(player);
            if (events != null)
            {
                events.add(new VehicleControlAction(controls));
            }
            this.lastControls.put(id, controls);
        }

        this.tickBasics(player, ridden, id);
    }

    /**
     * Capture siren / beacons / head lights / turn signals, which live in the
     * BasicsAddon module rather than in the engine's control bitmask.
     */
    private void tickBasics(EntityPlayer player, Entity ridden, UUID id)
    {
        int state = DynamXCompat.getVehicleBasicsState(ridden);

        if (state == -1) return;

        Integer previous = this.lastBasics.get(id);

        if (previous == null || previous.intValue() != state)
        {
            List<Action> events = CommonProxy.manager.getActions(player);

            if (events != null)
            {
                events.add(new VehicleBasicsAction(state));
            }

            this.lastBasics.put(id, state);
        }
    }

    /**
     * Called when a player is removed from recording (logged out, dismounted).
     * Clears any tracked state.
     */
    public void clearPlayer(UUID playerId)
    {
        this.lastControls.remove(playerId);
        this.lastBasics.remove(playerId);
    }

    public void clearAll()
    {
        this.lastControls.clear();
        this.lastBasics.clear();
    }
}
