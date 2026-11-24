package VLADMIX90.motorcyclePlugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MotorcycleVisualSyncTest {
    @Test
    void lightKeepsFollowingWhenOnlyPositionChangesAfterDismount() throws Exception {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        MotorcycleManager manager = new MotorcycleManager(plugin);
        Pig seat = mock(Pig.class);
        ItemDisplay visual = mock(ItemDisplay.class);
        ItemDisplay light = mock(ItemDisplay.class);
        when(light.isValid()).thenReturn(true);
        when(visual.teleport(any(Location.class))).thenReturn(true);
        World world = mock(World.class);
        Location seatLocation = new Location(world, 0, 64, 0);
        when(seat.getLocation()).thenAnswer(invocation -> seatLocation.clone());
        Motorcycle bike = new Motorcycle(UUID.randomUUID(), seat, visual, 100, 0);
        bike.setLightVisual(light);
        Method sync = MotorcycleManager.class.getDeclaredMethod("syncVisual", Motorcycle.class);
        sync.setAccessible(true);
        sync.invoke(manager, bike);
        clearInvocations(visual, light);

        seatLocation.setZ(10);
        sync.invoke(manager, bike);

        Location target = new Location(world, 0, 64.55, 10.55);
        verify(visual).teleport(target);
        verify(light).teleport(target);
    }
}
