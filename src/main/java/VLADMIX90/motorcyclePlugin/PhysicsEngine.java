package VLADMIX90.motorcyclePlugin;

import org.bukkit.util.Vector;

/** Horizontal controls and jump initiation, independent from entity lifecycle. */
public final class PhysicsEngine {
    private final MotorcyclePlugin plugin;

    public PhysicsEngine(MotorcyclePlugin plugin) { this.plugin = plugin; }

    public void updateSpeed(Motorcycle bike, boolean forward, boolean backward) {
        double max = plugin.getConfig().getDouble("motorcycle.physics.max-speed-kmh", 90) / 72.0;
        double reverse = plugin.getConfig().getDouble("motorcycle.physics.reverse-speed-kmh", 12) / 72.0;
        double accel = plugin.getConfig().getDouble("motorcycle.physics.acceleration-kmh-per-second", 22) / 1440.0;
        if (forward && bike.battery() > 0) bike.setSpeed(Math.min(max, bike.speed() + accel));
        else if (backward) {
            if (bike.speed() > 0.001) bike.setSpeed(Math.max(0, bike.speed() - brakingPerTick()));
            else if (bike.battery() > 0) bike.setSpeed(Math.max(-reverse, bike.speed() - accel * 0.5));
        } else bike.setSpeed(moveTowardZero(bike.speed(), rollingPerTick()));
        if (bike.battery() <= 0) bike.setSpeed(moveTowardZero(bike.speed(), brakingPerTick() * 1.6));
    }

    public Vector horizontalVelocity(Motorcycle bike) {
        double yaw = Math.toRadians(bike.headingYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw)).multiply(bike.speed());
    }

    public void handleJump(Motorcycle bike, boolean pressed) {
        if (pressed && !bike.jumpHeld() && !bike.jumping() && bike.battery() > 0) {
            double height = Math.max(0.1, plugin.getConfig().getDouble("motorcycle.physics.jump-height", 2.5));
            double gravity = Math.max(0.001, plugin.getConfig().getDouble("motorcycle.physics.jump-gravity", 0.08));
            bike.setJumpVelocity(Math.sqrt(2 * gravity * height));
            bike.setJumping(true);
        }
        bike.setJumpHeld(pressed);
    }

    public double rollingPerTick() { return plugin.getConfig().getDouble("motorcycle.physics.rolling-resistance-kmh-per-second", 7) / 1440.0; }
    public double brakingPerTick() { return plugin.getConfig().getDouble("motorcycle.physics.braking-kmh-per-second", 42) / 1440.0; }
    public static double moveTowardZero(double value, double amount) {
        return value > 0 ? Math.max(0, value - amount) : value < 0 ? Math.min(0, value + amount) : 0;
    }
}
