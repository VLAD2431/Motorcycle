package VLADMIX90.motorcyclePlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SurfaceParticlesTest {
    private World world;
    private YamlConfiguration config;
    private SurfaceParticles particles;
    private Location base;
    private Vector velocity;

    @BeforeEach
    void setUp() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        world = mock(World.class);
        base = new Location(world, 10, 5.7, 20);
        velocity = new Vector(0.5, 0, 0);
        particles = new SurfaceParticles(plugin);
    }

    @Test
    void selectsSandAndRedSandAndSuppliesTheirBlockData() {
        BlockData sand = mock(BlockData.class);
        BlockData redSand = mock(BlockData.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.SAND)).thenReturn(sand);
            bukkit.when(() -> Bukkit.createBlockData(Material.RED_SAND)).thenReturn(redSand);
            particles.emit(base, velocity, false, 2, surface(Material.SAND, false));
            particles.emit(base, velocity, false, 2, surface(Material.RED_SAND, false));
            verify(world).spawnParticle(Particle.BLOCK, new Location(world, 9.4, 5.05, 20), 4, 0.15, 0.15, 0.15, 0, sand);
            verify(world).spawnParticle(Particle.BLOCK, new Location(world, 9.4, 5.05, 20), 4, 0.15, 0.15, 0.15, 0, redSand);
        }
    }

    @Test
    void waterTakesPriorityAndSettingsControlEmission() {
        config.set("motorcycle.particles.water.particle", "CLOUD");
        config.set("motorcycle.particles.water.count", 7);
        config.set("motorcycle.particles.water.spread", 0.3);
        config.set("motorcycle.particles.water.interval-ticks", 3);
        config.set("motorcycle.particles.water.min-speed-kmh", 30);
        particles.emit(base, velocity, false, 2, surface(Material.SAND, true));
        particles.emit(base, new Vector(0.1, 0, 0), false, 3, surface(Material.SAND, true));
        particles.emit(base, velocity, false, 3, surface(Material.SAND, true));
        verify(world, times(1)).spawnParticle(Particle.CLOUD, new Location(world, 9.4, 5.05, 20), 7, 0.3, 0.3, 0.3, 0, null);
    }

    @Test
    void rejectsUnsupportedStatesAndParticleData() {
        CollisionProbe.Surface water = surface(Material.WATER, true);
        particles.emit(base, velocity, true, 2, water);
        particles.emit(base, velocity, false, 2, null);
        particles.emit(base, velocity, false, 2, surface(Material.STONE, false));
        particles.emit(new Location(world, 10, 6.3, 20), velocity, false, 2, water);
        particles.emit(base, new Vector(0, 1, 0), false, 2, water);
        config.set("motorcycle.particles.water.enabled", false);
        particles.emit(base, velocity, false, 2, water);
        config.set("motorcycle.particles.water.enabled", true);
        config.set("motorcycle.particles.water.particle", "DUST");
        particles.emit(base, velocity, false, 2, water);
        config.set("motorcycle.particles.water.particle", "not_a_particle");
        particles.emit(base, velocity, false, 2, water);
        verify(world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    private CollisionProbe.Surface surface(Material material, boolean water) {
        return new CollisionProbe.Surface(5.0, material, water);
    }
}
