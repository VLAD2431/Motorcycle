package VLADMIX90.motorcyclePlugin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loaded entity pairs only. Unregistering never destroys persisted entities. */
public final class BikeRegistry {
    private final Map<UUID, Motorcycle> bikes = new HashMap<>();
    private final Map<UUID, Pig> seats = new HashMap<>();
    private final Map<UUID, ItemDisplay> visuals = new HashMap<>();
    private final Map<UUID, ItemDisplay> lightVisuals = new HashMap<>();

    public Map<UUID, Motorcycle> bikes() { return bikes; }
    public Map<UUID, Pig> seats() { return seats; }
    public Map<UUID, ItemDisplay> visuals() { return visuals; }
    public Map<UUID, ItemDisplay> lightVisuals() { return lightVisuals; }

    public void forget(UUID id) {
        bikes.remove(id);
        seats.remove(id);
        visuals.remove(id);
        lightVisuals.remove(id);
    }

    public void unload(UUID id, Entity entity) {
        Pig seat = seats.get(id);
        if (seat != null && seat.getUniqueId().equals(entity.getUniqueId())) {
            seats.remove(id);
            bikes.remove(id);
        }
        ItemDisplay visual = visuals.get(id);
        if (visual != null && visual.getUniqueId().equals(entity.getUniqueId())) {
            visuals.remove(id);
            bikes.remove(id);
        }
        ItemDisplay lightVisual = lightVisuals.get(id);
        if (lightVisual != null && lightVisual.getUniqueId().equals(entity.getUniqueId())) {
            lightVisuals.remove(id);
            bikes.remove(id);
        }
    }

    public void pruneInvalid() {
        seats.values().removeIf(seat -> !seat.isValid());
        visuals.values().removeIf(visual -> !visual.isValid());
        lightVisuals.values().removeIf(visual -> !visual.isValid());
        bikes.entrySet().removeIf(entry -> !seats.containsKey(entry.getKey()) || !visuals.containsKey(entry.getKey()));
    }

    public void clear() { bikes.clear(); seats.clear(); visuals.clear(); lightVisuals.clear(); }
}
