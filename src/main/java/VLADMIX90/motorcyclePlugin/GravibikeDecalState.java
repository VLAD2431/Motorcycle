package VLADMIX90.motorcyclePlugin;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public final class GravibikeDecalState {
    private GravibikeDecalState() {
    }

    public static String read(PersistentDataContainer data, NamespacedKey key) {
        return data.get(key, PersistentDataType.STRING);
    }

    public static void write(PersistentDataContainer data, NamespacedKey skinKey, NamespacedKey itemModelKey,
            NamespacedKey customModelDataKey, String skinId, String itemModel, String customModelData) {
        write(data, skinKey, skinId);
        write(data, itemModelKey, itemModel);
        write(data, customModelDataKey, customModelData);
    }

    public static void write(PersistentDataContainer data, NamespacedKey key, String skinId) {
        if (skinId == null || skinId.isBlank()) {
            data.remove(key);
        } else {
            data.set(key, PersistentDataType.STRING, skinId);
        }
    }
}
