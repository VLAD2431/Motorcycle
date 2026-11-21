package VLADMIX90.motorcyclePlugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ZoneManager {

    private final MotorcyclePlugin plugin;
    private final Map<String, Zone> zones = new ConcurrentHashMap<>();
    private final Map<String, EnumMap<ZoneType, ZoneNode>> zonesByWorld = new HashMap<>();
    private boolean indexDirty = true;
    private final Map<UUID, Location> pos1 = new java.util.HashMap<>();
    private final Map<UUID, Location> pos2 = new java.util.HashMap<>();
    private final ZoneStore store;
    private final Executor publication;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "motorcycle-zone-storage");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile CompletableFuture<Void> loadFuture;
    private boolean stopping;
    private CompletableFuture<Void> shutdownFuture;

    public ZoneManager(MotorcyclePlugin plugin) {
        this(plugin, new ZoneStore(plugin.getDataFolder().toPath(), plugin.getLogger()),
                task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    ZoneManager(MotorcyclePlugin plugin, ZoneStore store, Executor publication) {
        this.plugin = plugin;
        this.store = store;
        this.publication = publication;
    }

    public synchronized CompletableFuture<Void> loadAsync() {
        if (stopping) return CompletableFuture.failedFuture(new IllegalStateException("Zone storage is stopping"));
        if (loadFuture != null) return loadFuture;
        synchronized (this) {
            if (loadFuture == null) {
                loadFuture = CompletableFuture.supplyAsync(store::load, io)
                        .thenAcceptAsync(loaded -> {
                            synchronized (this) {
                                if (stopping) throw new IllegalStateException("Zone storage is stopping");
                                zones.clear();
                                loaded.forEach(zone -> zones.put(key(zone.name()), zone));
                                indexDirty = true;
                                ready.set(true);
                            }
                        }, publication);
            }
            return loadFuture;
        }
    }

    public boolean isReady() {
        return ready.get();
    }

    public void setPos1(UUID player, Location location) {
        pos1.put(player, location.clone());
    }

    public void setPos2(UUID player, Location location) {
        pos2.put(player, location.clone());
    }

    public Location getPos1(UUID player) {
        return pos1.get(player);
    }

    public Location getPos2(UUID player) {
        return pos2.get(player);
    }

    public synchronized Zone create(String name, ZoneType type, Location a, Location b) {
        if (!isReady()) throw new IllegalStateException("Zones are still loading");
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Zone names must match [A-Za-z0-9_-]{1,32}");
        }
        if (a.getWorld() == null || b.getWorld() == null) {
            throw new IllegalArgumentException("Locations must have a world");
        }
        if (!a.getWorld().getUID().equals(b.getWorld().getUID())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }

        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX());
        double maxY = Math.max(a.getY(), b.getY());
        double maxZ = Math.max(a.getZ(), b.getZ());

        Zone zone = new Zone(name, type, a.getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ);
        zones.put(key(name), zone);
        indexDirty = true;
        store.upsert(zone, io).exceptionally(error -> {
            plugin.getLogger().severe("Failed to persist zone " + name + ": " + error.getMessage());
            return null;
        });
        return zone;
    }

    public synchronized boolean delete(String name) {
        if (!isReady()) throw new IllegalStateException("Zones are still loading");
        Zone removed = zones.remove(key(name));
        if (removed != null) {
            indexDirty = true;
            store.delete(name, io).exceptionally(error -> {
                plugin.getLogger().severe("Failed to delete zone " + name + ": " + error.getMessage());
                return null;
            });
            return true;
        }
        return false;
    }

    public Zone get(String name) {
        return zones.get(key(name));
    }

    public List<Zone> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(zones.values()));
    }

    public boolean isNoRide(Location location) {
        return contains(location, ZoneType.NO_RIDE);
    }

    public boolean isCharge(Location location) {
        return contains(location, ZoneType.CHARGE);
    }

    private boolean contains(Location location, ZoneType type) {
        World world = location.getWorld();
        if (world == null) return false;
        if (indexDirty) rebuildIndex();
        EnumMap<ZoneType, ZoneNode> types = zonesByWorld.get(world.getName());
        return types != null && contains(types.get(type), location);
    }

    private void rebuildIndex() {
        Map<String, EnumMap<ZoneType, List<Zone>>> groups = new HashMap<>();
        for (Zone zone : zones.values()) {
            if (zone.type() == null || !(zone.minX() <= zone.maxX())) continue;
            groups.computeIfAbsent(zone.worldName(), key -> new EnumMap<>(ZoneType.class))
                    .computeIfAbsent(zone.type(), key -> new ArrayList<>()).add(zone);
        }
        zonesByWorld.clear();
        groups.forEach((world, types) -> {
            EnumMap<ZoneType, ZoneNode> trees = new EnumMap<>(ZoneType.class);
            types.forEach((type, list) -> {
                list.sort(Comparator.comparingDouble(Zone::minX));
                trees.put(type, buildTree(list, 0, list.size()));
            });
            zonesByWorld.put(world, trees);
        });
        indexDirty = false;
    }

    private static ZoneNode buildTree(List<Zone> zones, int start, int end) {
        if (start == end) return null;
        int middle = (start + end) >>> 1;
        return new ZoneNode(zones.get(middle), buildTree(zones, start, middle),
                buildTree(zones, middle + 1, end));
    }

    private static boolean contains(ZoneNode node, Location location) {
        double x = location.getX();
        if (node == null || !(x <= node.maxX)) return false;
        if (contains(node.left, location)) return true;
        if (x < node.zone.minX()) return false;
        return node.zone.contains(location) || contains(node.right, location);
    }

    private static final class ZoneNode {
        private final Zone zone;
        private final ZoneNode left;
        private final ZoneNode right;
        private final double maxX;

        private ZoneNode(Zone zone, ZoneNode left, ZoneNode right) {
            this.zone = zone;
            this.left = left;
            this.right = right;
            this.maxX = Math.max(zone.maxX(), Math.max(left == null ? Double.NEGATIVE_INFINITY : left.maxX,
                    right == null ? Double.NEGATIVE_INFINITY : right.maxX));
        }
    }

    public synchronized CompletableFuture<Void> shutdown() {
        if (shutdownFuture != null) return shutdownFuture;
        stopping = true;
        ready.set(false);
        shutdownFuture = CompletableFuture.runAsync(store::close, io).whenComplete((ignored, error) -> io.shutdown());
        return shutdownFuture;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
