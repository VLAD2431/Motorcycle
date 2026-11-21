package VLADMIX90.motorcyclePlugin;

import VLADMIX90.motorcyclePlugin.api.GravibikeDecalApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class GravibikeDecalService implements GravibikeDecalApi {
    private final MotorcycleManager motorcycles;

    GravibikeDecalService(MotorcycleManager motorcycles) {
        this.motorcycles = motorcycles;
    }

    @Override
    public boolean isGravibikeItem(ItemStack item) {
        return motorcycles.isMotorcycleItem(item);
    }

    @Override
    public boolean applyToItem(ItemStack item, String skinId, String itemModel, String customModelData) {
        return motorcycles.applyDecalToItem(item, skinId, itemModel, customModelData);
    }

    @Override
    public boolean applyToNearest(Player player, double radius, String skinId, String itemModel,
            String customModelData) {
        return motorcycles.applyDecalToNearest(player, radius, skinId, itemModel, customModelData);
    }
}
