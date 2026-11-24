package VLADMIX90.motorcyclePlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhysicsEngineTest {
    @Test
    void steeringIsMoreResponsiveAtLowSpeedsAndClampsAtBothEnds() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getDouble("motorcycle.physics.steering-max-degrees-per-second", 120.0)).thenReturn(120.0);
        when(config.getDouble("motorcycle.physics.steering-min-degrees-per-second", 35.0)).thenReturn(35.0);
        when(config.getDouble("motorcycle.physics.steering-low-speed-kmh", 8.0)).thenReturn(8.0);
        when(config.getDouble("motorcycle.physics.steering-high-speed-kmh", 80.0)).thenReturn(80.0);

        PhysicsEngine physics = new PhysicsEngine(plugin);

        assertEquals(6.0, physics.steeringDegreesPerTick(0), 0.0001);
        assertEquals(1.75, physics.steeringDegreesPerTick(100.0 / 72.0), 0.0001);
        assertEquals(1.75, physics.steeringDegreesPerTick(2), 0.0001);
        assertEquals(6.0, physics.steeringDegreesPerTick(-0.1), 0.0001);
    }
}
