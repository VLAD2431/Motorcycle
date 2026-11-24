package VLADMIX90.motorcyclePlugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MotorcycleSpawnTest {
    private MotorcycleManager manager;
    private World world;
    private Pig seat;
    private ItemDisplay visual;
    private Location location;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        manager = new MotorcycleManager(plugin);
        world = mock(World.class);
        location = new Location(world, 100, 65, 100);
        seat = mock(Pig.class);
        visual = mock(ItemDisplay.class);
        when(seat.getLocation()).thenAnswer(invocation -> location.clone());
        when(visual.teleport(any(Location.class))).thenReturn(true);
        when(seat.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(world.spawn(any(Location.class), eq(Pig.class), org.mockito.ArgumentMatchers.<Consumer<Pig>>any()))
                .thenReturn(seat);
        when(world.spawn(any(Location.class), eq(ItemDisplay.class), org.mockito.ArgumentMatchers.<Consumer<ItemDisplay>>any()))
                .thenReturn(visual);
    }

    @Test
    void barrierCustomModelDataCreatesLightWithoutAnItemModelOverride() {
        config.set("motorcycle.visual.light-model", "");
        config.set("motorcycle.visual.light-custom-model-data.strings", java.util.List.of("bike_light"));
        when(seat.isValid()).thenReturn(true);
        when(visual.isValid()).thenReturn(true);

        manager.spawn(location, 100);

        verify(world, times(2)).spawn(any(Location.class), eq(ItemDisplay.class),
                org.mockito.ArgumentMatchers.<Consumer<ItemDisplay>>any());
    }

    @Test
    void cancelledSeatSpawnDoesNotCreateAnOrphanModel() {
        when(seat.isValid()).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> manager.spawn(location, 100));

        verify(world, never()).spawn(any(Location.class), eq(ItemDisplay.class),
                org.mockito.ArgumentMatchers.<Consumer<ItemDisplay>>any());
    }

    @Test
    void cancelledModelSpawnRemovesTheSeat() {
        when(seat.isValid()).thenReturn(true);
        when(visual.isValid()).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> manager.spawn(location, 100));

        verify(seat).remove();
        verify(visual).remove();
    }

    @Test
    void modelSpawnExceptionRemovesTheSeat() {
        when(seat.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class), eq(ItemDisplay.class), org.mockito.ArgumentMatchers.<Consumer<ItemDisplay>>any()))
                .thenThrow(new IllegalArgumentException("Spawn denied"));

        assertThrows(IllegalArgumentException.class, () -> manager.spawn(location, 100));

        verify(seat).remove();
    }

}
