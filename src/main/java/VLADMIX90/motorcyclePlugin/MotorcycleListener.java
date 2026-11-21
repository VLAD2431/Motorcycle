package VLADMIX90.motorcyclePlugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class MotorcycleListener implements Listener {
    private final MotorcyclePlugin plugin;

    public MotorcycleListener(MotorcyclePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (var entity : event.getChunk().getEntities()) {
            plugin.motorcycles().registerLoadedEntity(entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Motorcycle bike = plugin.motorcycles().getByEntity(event.getRightClicked());
        if (bike == null) return;
        event.setCancelled(true);
        plugin.motorcycles().mount(event.getPlayer(), bike);
    }

    // PlayerInteractAtEntityEvent is a subclass of PlayerInteractEntityEvent;
    // one handler prevents the interaction from being processed twice.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (plugin.motorcycles().getByPlayer(event.getPlayer()) != null) return;
        if (!plugin.motorcycles().isMotorcycleItem(event.getPlayer().getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        plugin.motorcycles().beginInstall(event.getPlayer());
    }

    /** F — уборка мотоцикла рядом. Q плагином не используется. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.motorcycles().getByPlayer(event.getPlayer()) != null) return;
        if (plugin.motorcycles().beginStorageNearby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        plugin.motorcycles().onDismount(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.motorcycles().onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.motorcycles().unloadEntities(event.getChunk().getEntities());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.motorcycles().intersectsPlacedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Motorcycle bike = plugin.motorcycles().getByEntity(event.getEntity());
        if (bike != null) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                Motorcycle riddenBike = plugin.motorcycles().getByPlayer(player);
                var vehicle = player.getVehicle();
                if (riddenBike != null && vehicle != null
                        && (vehicle == riddenBike.seat() || vehicle == riddenBike.passengerSeat())) {
                    player.setFallDistance(0);
                    event.setCancelled(true);
                    return;
                }
            }
            // Storage runs while standing next to the bike, so the pending task
            // must be consulted instead of the rider mapping.
            if (plugin.motorcycles().isStoragePending(player)
                    && plugin.getConfig().getBoolean("motorcycle.storage.cancel-on-damage", true)) {
                plugin.motorcycles().cancelStorageForPlayer(player, "messages.storage-cancel");
            }
        }
    }
}
