package VLADMIX90.motorcyclePlugin;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Pig;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MotorcycleSoundsTest {
    @Test
    void playsWithoutRiderAndRepeatsAtConfiguredInterval() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        Pig seat = mock(Pig.class);
        World world = mock(World.class);
        Location location = new Location(world, 0, 64, 0);
        when(seat.getVelocity()).thenReturn(new Vector());
        when(seat.getWorld()).thenReturn(world);
        when(seat.getLocation()).thenReturn(location);
        Motorcycle bike = new Motorcycle(UUID.randomUUID(), seat, null, 100, 0);
        MotorcycleSounds sounds = new MotorcycleSounds(plugin);

        sounds.update(bike, false, 1);
        sounds.update(bike, false, 2);
        sounds.update(bike, false, 41);
        when(seat.getVelocity()).thenReturn(new Vector(0.5, 0, 0));
        sounds.update(bike, true, 42);
        sounds.update(bike, false, 43);
        config.set("motorcycle.sounds.enabled", false);
        sounds.update(bike, true, 44);
        verify(world, times(3)).playSound(location, "quack_sound:s0", SoundCategory.NEUTRAL, 1.0f, 1.0f);
        verify(world).playSound(location, "quack_sound:s2", SoundCategory.NEUTRAL, 1.0f, 1.0f);
        verifyNoMoreInteractions(world);
    }

    @Test
    void bundledSoundDefaultsMatchRuntimeFallbacks() {
        var resource = getClass().getResourceAsStream("/config.yml");
        assertNotNull(resource);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        String[] states = {"idle", "low", "medium", "high"};
        for (int i = 0; i < states.length; i++) {
            String expected = "quack_sound:s" + i;
            assertEquals(expected, config.getString("motorcycle.sounds." + states[i] + ".sound"));
            assertEquals(expected, MotorcycleSounds.defaultSound(states[i]));
        }
    }
}
