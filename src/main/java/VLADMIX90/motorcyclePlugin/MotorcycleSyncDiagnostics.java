package VLADMIX90.motorcyclePlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Vector;

final class MotorcycleSyncDiagnostics {
    private MotorcycleSyncDiagnostics() {
    }

    static String format(long tick, UUID bikeId, Location seat, Vector velocity, float yaw,
            List<String> passengers, Location rider, Location displayActual, Location displayTarget,
            Boolean displayTeleportSucceeded, Location passengerSeatActual, Location passengerSeatTarget,
            Boolean passengerSeatTeleportSucceeded) {
        return "sync tick=" + tick + " bike=" + bikeId
                + " seat=" + location(seat)
                + " yaw=" + number(yaw)
                + " velocity=" + vector(velocity)
                + " passengers=[" + String.join(",", passengers) + "]"
                + " rider=" + location(rider)
                + " display=" + pair(displayActual, displayTarget)
                + " teleport=" + result(displayTeleportSucceeded)
                + " passengerSeat=" + pair(passengerSeatActual, passengerSeatTarget)
                + " teleport=" + result(passengerSeatTeleportSucceeded);
    }

    private static String pair(Location actual, Location target) {
        if (actual == null && target == null)
            return "absent";
        return location(actual) + "->" + location(target);
    }

    private static String result(Boolean value) {
        return value == null ? "n/a" : value.toString();
    }

    private static String vector(Vector vector) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", vector.getX(), vector.getY(), vector.getZ());
    }

    private static String location(Location location) {
        if (location == null)
            return "absent";
        String world = location.getWorld() == null ? "unknown-world" : location.getWorld().getName();
        return world + "@" + String.format(Locale.ROOT, "%.2f,%.2f,%.2f",
                location.getX(), location.getY(), location.getZ());
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
