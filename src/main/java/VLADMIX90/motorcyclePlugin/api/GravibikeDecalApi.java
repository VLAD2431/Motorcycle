package VLADMIX90.motorcyclePlugin.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface GravibikeDecalApi {
    boolean isGravibikeItem(ItemStack item);

    boolean applyToItem(ItemStack item, String skinId, String itemModel, String customModelData);

    boolean applyToNearest(Player player, double radius, String skinId, String itemModel, String customModelData);
}
