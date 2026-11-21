package VLADMIX90.motorcyclePlugin;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.entity.Pig;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

public final class WorldGuardMotorcycleSpawnListener implements Listener {
    private final MotorcyclePlugin plugin;
    private final StateFlag motorcycleSpawning;
    private final java.util.function.BiFunction<Location, StateFlag, StateFlag.State> queryState;

    public WorldGuardMotorcycleSpawnListener(MotorcyclePlugin plugin, StateFlag motorcycleSpawning) {
        this(plugin, motorcycleSpawning, (location, flag) -> WorldGuard.getInstance().getPlatform()
                .getRegionContainer().createQuery()
                .queryState(BukkitAdapter.adapt(location), null, flag));
    }

    WorldGuardMotorcycleSpawnListener(MotorcyclePlugin plugin, StateFlag motorcycleSpawning,
                                     java.util.function.BiFunction<Location, StateFlag, StateFlag.State> queryState) {
        this.plugin = plugin;
        this.motorcycleSpawning = motorcycleSpawning;
        this.queryState = queryState;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Pig pig)
                || !pig.getPersistentDataContainer().has(plugin.seatKey(), PersistentDataType.BYTE)) {
            return;
        }

        StateFlag.State state = queryState.apply(event.getLocation(), motorcycleSpawning);
        if (state == StateFlag.State.ALLOW) {
            event.setCancelled(false);
        }
    }
}
