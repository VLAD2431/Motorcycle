package VLADMIX90.motorcyclePlugin;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MotorcycleSounds {
    private final MotorcyclePlugin plugin;
    private final Map<UUID, Playback> playback = new HashMap<>();

    MotorcycleSounds(MotorcyclePlugin plugin) {
        this.plugin = plugin;
    }

    void update(Motorcycle bike, boolean hasRider, long tick) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("motorcycle.sounds.enabled", true)) {
            playback.remove(bike.id());
            return;
        }

        double speed = hasRider ? Math.hypot(bike.seat().getVelocity().getX(), bike.seat().getVelocity().getZ()) * 72.0 : 0.0;
        double low = positive(config.getDouble("motorcycle.sounds.low.max-speed", 30.0), 30.0);
        double medium = Math.max(low, positive(config.getDouble("motorcycle.sounds.medium.max-speed", 60.0), 60.0));
        String state = speed < 0.1 ? "idle" : speed < low ? "low" : speed < medium ? "medium" : "high";
        String path = "motorcycle.sounds." + state + ".";
        Playback previous = playback.get(bike.id());
        int interval = Math.max(1, config.getInt(path + "interval-ticks", 40));
        if (previous != null && previous.state().equals(state) && tick - previous.tick() < interval)
            return;

        String sound = config.getString(path + "sound", defaultSound(state));
        double volume = config.getDouble(path + "volume", 1.0);
        double pitch = config.getDouble(path + "pitch", 1.0);
        if (sound != null && !sound.isBlank() && Double.isFinite(volume) && Double.isFinite(pitch)) {
            bike.seat().getWorld().playSound(bike.seat().getLocation(), sound, SoundCategory.NEUTRAL,
                    (float) Math.max(0, Math.min(volume, Float.MAX_VALUE)), (float) Math.max(0.5, Math.min(2.0, pitch)));
        }
        playback.put(bike.id(), new Playback(state, tick));
    }

    void retain(Set<UUID> ids) {
        playback.keySet().retainAll(ids);
    }

    void clear() {
        playback.clear();
    }

    static String defaultSound(String state) {
        return switch (state) {
            case "idle" -> "quack_sound:s0";
            case "low" -> "quack_sound:s1";
            case "medium" -> "quack_sound:s2";
            case "high" -> "quack_sound:s3";
            default -> throw new IllegalArgumentException("Unknown sound state: " + state);
        };
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private record Playback(String state, long tick) { }
}
