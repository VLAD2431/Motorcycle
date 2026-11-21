package VLADMIX90.motorcyclePlugin;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldGuardMotorcycleFlagTest {
    @Test
    void integrationIsUnavailableWhenWorldGuardIsNotEnabled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PluginManagerStub pluginManager = new PluginManagerStub();
        when(plugin.getServer()).thenReturn(pluginManager.server());

        assertNull(WorldGuardMotorcycleFlag.registerIfAvailable(plugin));
    }

    private static final class PluginManagerStub {
        private final org.bukkit.Server server = mock(org.bukkit.Server.class);

        PluginManagerStub() {
            when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            when(server.getPluginManager().getPlugin("WorldGuard")).thenReturn(null);
        }

        org.bukkit.Server server() {
            return server;
        }
    }
}
