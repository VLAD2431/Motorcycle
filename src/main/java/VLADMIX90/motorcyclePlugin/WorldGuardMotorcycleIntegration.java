package VLADMIX90.motorcyclePlugin;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldGuardMotorcycleIntegration {
    private static StateFlag motorcycleSpawning;

    private WorldGuardMotorcycleIntegration() {
    }

    public static Flag<?> registerFlag(JavaPlugin plugin) {
        StateFlag flag = new StateFlag(WorldGuardMotorcycleFlag.FLAG_NAME, false);
        try {
            WorldGuard.getInstance().getFlagRegistry().register(flag);
            plugin.getLogger().info("Registered WorldGuard flag '" + WorldGuardMotorcycleFlag.FLAG_NAME + "'.");
            motorcycleSpawning = flag;
            return flag;
        } catch (FlagConflictException exception) {
            Flag<?> existing = WorldGuard.getInstance().getFlagRegistry().get(WorldGuardMotorcycleFlag.FLAG_NAME);
            if (existing instanceof StateFlag) {
                plugin.getLogger().info("Reusing existing WorldGuard flag '" + WorldGuardMotorcycleFlag.FLAG_NAME + "'.");
                motorcycleSpawning = (StateFlag) existing;
                return existing;
            }
            plugin.getLogger().warning("WorldGuard flag '" + WorldGuardMotorcycleFlag.FLAG_NAME
                    + "' is already registered with an incompatible type; motorcycle spawn exception disabled.");
            return null;
        }
    }

    public static void registerSpawnListener(MotorcyclePlugin plugin, Object flag) {
        if (motorcycleSpawning != flag || !(flag instanceof StateFlag stateFlag)) {
            return;
        }
        Listener listener = new WorldGuardMotorcycleSpawnListener(plugin, stateFlag);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
