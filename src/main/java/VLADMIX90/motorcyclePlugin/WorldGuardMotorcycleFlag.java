package VLADMIX90.motorcyclePlugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;

public final class WorldGuardMotorcycleFlag {
    public static final String FLAG_NAME = "motorcycle-spawning";
    public static Object MOTORCYCLE_SPAWNING;

    private WorldGuardMotorcycleFlag() {
    }

    public static Object registerIfAvailable(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return null;
        }
        try {
            Class<?> integration = Class.forName(
                    "VLADMIX90.motorcyclePlugin.WorldGuardMotorcycleIntegration",
                    true,
                    plugin.getClass().getClassLoader());
            MOTORCYCLE_SPAWNING = integration.getMethod("registerFlag", JavaPlugin.class).invoke(null, plugin);
            return MOTORCYCLE_SPAWNING;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            plugin.getLogger().severe("WorldGuard is present, but motorcycle flag integration could not be loaded: " + e);
            return null;
        } catch (InvocationTargetException e) {
            plugin.getLogger().severe("Unable to register WorldGuard motorcycle flag: " + e.getCause());
            return null;
        }
    }

    public static void registerSpawnListenerIfAvailable(MotorcyclePlugin plugin) {
        if (MOTORCYCLE_SPAWNING == null) {
            return;
        }
        try {
            Class<?> integration = Class.forName(
                    "VLADMIX90.motorcyclePlugin.WorldGuardMotorcycleIntegration",
                    true,
                    plugin.getClass().getClassLoader());
            integration.getMethod("registerSpawnListener", MotorcyclePlugin.class, Object.class)
                    .invoke(null, plugin, MOTORCYCLE_SPAWNING);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            plugin.getLogger().severe("Unable to register WorldGuard motorcycle spawn listener: " + e);
        } catch (InvocationTargetException e) {
            plugin.getLogger().severe("Unable to register WorldGuard motorcycle spawn listener: " + e.getCause());
        }
    }
}
