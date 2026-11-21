package VLADMIX90.motorcyclePlugin;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Keeps sub-percent changes in memory; serializes only at lifecycle boundaries. */
public final class BatterySystem {
    private final MotorcyclePlugin plugin;

    public BatterySystem(MotorcyclePlugin plugin) { this.plugin = plugin; }

    public int capacity() { return Math.max(1, plugin.getConfig().getInt("motorcycle.battery.capacity", 100)); }

    public void restore(Motorcycle bike) {
        var data = bike.seat().getPersistentDataContainer();
        bike.setBatteryFraction(data.getOrDefault(plugin.batteryFractionKey(), PersistentDataType.DOUBLE, 0.0));
        bike.setChargeFraction(data.getOrDefault(plugin.chargeFractionKey(), PersistentDataType.DOUBLE, 0.0));
    }

    public void persist(Motorcycle bike) {
        // An entity whose chunk is unloaded cannot accept writes; the server
        // would either drop them or reject them.
        if (!bike.seat().isValid()) return;
        var data = bike.seat().getPersistentDataContainer();
        data.set(plugin.batteryKey(), PersistentDataType.INTEGER, bike.battery());
        data.set(plugin.batteryFractionKey(), PersistentDataType.DOUBLE, bike.batteryFraction());
        data.set(plugin.chargeFractionKey(), PersistentDataType.DOUBLE, bike.chargeFraction());
    }

    public void consume(Motorcycle bike, double speed) {
        double max = plugin.getConfig().getDouble("motorcycle.physics.max-speed-kmh", 90) / 72.0;
        if (max <= 0 || speed <= 0.0001 || bike.battery() <= 0) return;
        double rate = Math.max(0, plugin.getConfig().getDouble("motorcycle.battery.consumption-at-max-speed-percent-per-minute", 0.65));
        double fraction = bike.batteryFraction() + rate / 1200.0 * Math.min(1, speed / max);
        int spent = Math.min(bike.battery(), (int) Math.floor(fraction));
        bike.setBatteryFraction(fraction - spent);
        setBattery(bike, bike.battery() - spent);
    }

    public void charge(Motorcycle bike, boolean inChargeZone) {
        if (!inChargeZone || Math.abs(bike.speed()) > 0.001 || bike.battery() >= capacity()) return;
        double fraction = bike.chargeFraction() + Math.max(0, plugin.getConfig().getDouble("motorcycle.battery.charge-per-second", 3.0)) / 20.0;
        int gained = (int) Math.floor(fraction);
        bike.setChargeFraction(fraction - gained);
        setBattery(bike, bike.battery() + gained);
    }

    public void setBattery(Motorcycle bike, int value) {
        int clamped = Math.max(0, Math.min(capacity(), value));
        if (clamped == bike.battery()) return;
        bike.setBattery(clamped);
        persist(bike);
        ItemStack item = bike.visual().getItemStack();
        if (plugin.motorcycles().isMotorcycleItem(item))
            bike.visual().setItemStack(plugin.motorcycles().updateItemBattery(item, clamped));
    }
}
