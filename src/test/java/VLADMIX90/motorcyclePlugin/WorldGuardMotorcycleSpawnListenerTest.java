package VLADMIX90.motorcyclePlugin;

import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Pig;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorldGuardMotorcycleSpawnListenerTest {
    private MotorcyclePlugin plugin;
    private StateFlag flag;
    private CreatureSpawnEvent event;
    private Pig pig;
    private PersistentDataContainer pdc;
    private java.util.function.BiFunction<org.bukkit.Location, StateFlag, StateFlag.State> queryState;

    @BeforeEach
    void setUp() {
        plugin = mock(MotorcyclePlugin.class);
        when(plugin.seatKey()).thenReturn(NamespacedKey.minecraft("motorcycle_seat"));
        flag = new StateFlag("motorcycle-spawning-test", false);
        event = mock(CreatureSpawnEvent.class);
        pig = mock(Pig.class);
        pdc = mock(PersistentDataContainer.class);
        when(event.getEntity()).thenReturn(pig);
        when(pig.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(plugin.seatKey(), PersistentDataType.BYTE)).thenReturn(true);

        queryState = (location, queriedFlag) -> StateFlag.State.ALLOW;
    }

    @Test
    void clearsCancellationForTaggedPigInAllowedRegion() {
        when(event.isCancelled()).thenReturn(true);

        new WorldGuardMotorcycleSpawnListener(plugin, flag, queryState).onCreatureSpawn(event);

        verify(event).setCancelled(false);
    }

    @Test
    void leavesUntaggedPigCancellationUnchanged() {
        when(pdc.has(plugin.seatKey(), PersistentDataType.BYTE)).thenReturn(false);
        when(event.isCancelled()).thenReturn(true);

        new WorldGuardMotorcycleSpawnListener(plugin, flag, queryState).onCreatureSpawn(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void leavesTaggedPigCancelledWhenFlagDoesNotAllow() {
        when(event.isCancelled()).thenReturn(true);
        queryState = (location, queriedFlag) -> StateFlag.State.DENY;

        new WorldGuardMotorcycleSpawnListener(plugin, flag, queryState).onCreatureSpawn(event);

        verify(event, never()).setCancelled(anyBoolean());
    }
}
