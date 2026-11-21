package VLADMIX90.motorcyclePlugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import VLADMIX90.motorcyclePlugin.api.GravibikeDecalApi;

public final class MotorcyclePlugin extends JavaPlugin {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private NamespacedKey bikeKey;
    private NamespacedKey batteryKey;
    private NamespacedKey bikeIdKey;
    private NamespacedKey seatKey;
    private NamespacedKey interactionKey;
    private NamespacedKey visualKey;
    private NamespacedKey headingKey;
    private NamespacedKey batteryFractionKey;
    private NamespacedKey chargeFractionKey;
    private NamespacedKey gravibikeSkinIdKey;
    private NamespacedKey gravibikeItemModelKey;
    private NamespacedKey gravibikeCustomModelDataKey;

    private ZoneManager zoneManager;
    private MotorcycleManager motorcycleManager;
    private GravibikeDecalApi gravibikeDecalApi;

    @Override
    public void onLoad() {
        WorldGuardMotorcycleFlag.registerIfAvailable(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        bikeKey = new NamespacedKey(this, "motorcycle_item");
        batteryKey = new NamespacedKey(this, "battery");
        bikeIdKey = new NamespacedKey(this, "motorcycle_id");
        seatKey = new NamespacedKey(this, "motorcycle_seat");
        interactionKey = new NamespacedKey(this, "motorcycle_interaction");
        visualKey = new NamespacedKey(this, "motorcycle_visual");
        headingKey = new NamespacedKey(this, "heading_yaw");
        batteryFractionKey = new NamespacedKey(this, "battery_fraction");
        chargeFractionKey = new NamespacedKey(this, "charge_fraction");
        gravibikeSkinIdKey = new NamespacedKey(this, "gravibike_skin_id");
        gravibikeItemModelKey = new NamespacedKey(this, "gravibike_item_model");
        gravibikeCustomModelDataKey = new NamespacedKey(this, "gravibike_custom_model_data");

        zoneManager = new ZoneManager(this);
        zoneManager.loadAsync().exceptionally(error -> {
            getLogger().log(java.util.logging.Level.SEVERE, "Unable to load zones", error);
            getServer().getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
            return null;
        });
        motorcycleManager = new MotorcycleManager(this);
        gravibikeDecalApi = new GravibikeDecalService(motorcycleManager);
        getServer().getServicesManager().register(GravibikeDecalApi.class, gravibikeDecalApi, this,
                org.bukkit.plugin.ServicePriority.Normal);
        motorcycleManager.start();

        MotorcycleCommand command = new MotorcycleCommand(this);
        if (getCommand("moto") == null) {
            getLogger().severe("Command 'moto' is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("moto").setExecutor(command);
        getCommand("moto").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new MotorcycleListener(this), this);
        WorldGuardMotorcycleFlag.registerSpawnListenerIfAvailable(this);

        getLogger().info("MotorcyclePlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (gravibikeDecalApi != null) {
            getServer().getServicesManager().unregister(gravibikeDecalApi);
            gravibikeDecalApi = null;
        }
        if (motorcycleManager != null) {
            motorcycleManager.shutdown();
        }
        if (zoneManager != null) {
            zoneManager.shutdown().join();
        }
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    public NamespacedKey bikeKey() {
        return bikeKey;
    }

    public NamespacedKey batteryKey() {
        return batteryKey;
    }

    public NamespacedKey bikeIdKey() {
        return bikeIdKey;
    }

    public NamespacedKey seatKey() {
        return seatKey;
    }

    public NamespacedKey interactionKey() {
        return interactionKey;
    }

    public NamespacedKey visualKey() {
        return visualKey;
    }

    public NamespacedKey headingKey() {
        return headingKey;
    }

    public NamespacedKey batteryFractionKey() {
        return batteryFractionKey;
    }

    public NamespacedKey chargeFractionKey() {
        return chargeFractionKey;
    }

    public NamespacedKey gravibikeSkinIdKey() {
        return gravibikeSkinIdKey;
    }

    public NamespacedKey gravibikeItemModelKey() { return gravibikeItemModelKey; }

    public NamespacedKey gravibikeCustomModelDataKey() { return gravibikeCustomModelDataKey; }

    public ZoneManager zones() {
        return zoneManager;
    }

    public MotorcycleManager motorcycles() {
        return motorcycleManager;
    }

    public String message(String path) {
        return getConfig().getString("messages.prefix", "") + getConfig().getString(path, path);
    }
}
