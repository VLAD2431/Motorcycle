package VLADMIX90.motorcyclePlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.Locale;

public final class SurfaceParticles {
    private final MotorcyclePlugin plugin;

    public SurfaceParticles(MotorcyclePlugin plugin) {
        this.plugin = plugin;
    }

    public void emit(Location base, Vector velocity, boolean jumping, long tick, CollisionProbe.Surface surface) {
        if (jumping || base == null || velocity == null || surface == null) return;
        World world = base.getWorld();
        double speed = Math.hypot(velocity.getX(), velocity.getZ());
        if (world == null || !Double.isFinite(speed) || speed <= 0 || !Double.isFinite(surface.height())
                || !Double.isFinite(base.getY())) return;

        FileConfiguration config = plugin.getConfig();
        double hover = config.getDouble("motorcycle.physics.hover-height", 0.70);
        if (!Double.isFinite(hover)) hover = 0.70;
        double distance = base.getY() - surface.height();
        if (distance < 0 || distance > Math.max(0, hover) + 0.5) return;

        String type;
        if (surface.water()) type = "water";
        else if (surface.material() == Material.SAND) type = "sand";
        else if (surface.material() == Material.RED_SAND) type = "red-sand";
        else return;
        String path = "motorcycle.particles." + type + ".";
        if (!config.getBoolean(path + "enabled", true)) return;
        int interval = Math.max(1, config.getInt(path + "interval-ticks", 2));
        if (Math.floorMod(tick, interval) != 0) return;
        double minimum = config.getDouble(path + "min-speed-kmh", 5.0);
        if (!Double.isFinite(minimum)) minimum = 5.0;
        if (speed * 72.0 < Math.max(0, minimum)) return;
        int count = config.getInt(path + "count", 4);
        if (count <= 0) return;
        double spread = config.getDouble(path + "spread", 0.15);
        if (!Double.isFinite(spread)) spread = 0.15;
        spread = Math.max(0, spread);

        String name = config.getString(path + "particle", type.equals("water") ? "SPLASH" : "BLOCK");
        if (name == null) return;
        Particle particle;
        try {
            particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return;
        }
        Object data = null;
        if (particle.getDataType() == BlockData.class) {
            Material material = surface.material();
            if (material == null) return;
            data = Bukkit.createBlockData(material);
        } else if (particle.getDataType() != Void.class) {
            return;
        }
        Location position = new Location(world, base.getX() - velocity.getX() / speed * 0.6,
                surface.height() + 0.05, base.getZ() - velocity.getZ() / speed * 0.6);
        world.spawnParticle(particle, position, count, spread, spread, spread, 0, data);
    }
}
