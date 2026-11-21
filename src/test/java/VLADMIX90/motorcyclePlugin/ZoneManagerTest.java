package VLADMIX90.motorcyclePlugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZoneManagerTest {
    @TempDir
    Path dataFolder;

    private ZoneManager manager;
    private World overworld;
    private World nether;
    private World emptyWorld;

    @BeforeEach
    void setUp() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        manager = new ZoneManager(plugin, new ZoneStore(dataFolder, java.util.logging.Logger.getAnonymousLogger()), Runnable::run);
        manager.loadAsync().join();
        overworld = world("world");
        nether = world("nether");
        emptyWorld = world("empty");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        manager.shutdown().join();
    }

    @Test
    void indexMatchesLinearSearchAcrossZonesCoordinatesAndWorlds() {
        Random random = new Random(93741);
        World[] worlds = {overworld, nether};
        ZoneType[] types = ZoneType.values();
        for (int i = 0; i < 100; i++) {
            World world = worlds[i % worlds.length];
            double x = random.nextInt(401) - 200;
            double y = random.nextInt(101) - 50;
            double z = random.nextInt(401) - 200;
            manager.create("zone_" + i, types[(i / 2) % types.length],
                    new Location(world, x, y, z),
                    new Location(world, x + random.nextInt(80), y + random.nextInt(40), z + random.nextInt(80)));
        }

        for (World world : new World[]{overworld, nether, emptyWorld}) {
            for (int i = 0; i < 3000; i++) {
                assertMatchesLinear(new Location(world, random.nextInt(601) - 300,
                        random.nextInt(181) - 90, random.nextInt(601) - 300));
            }
        }
        for (Zone zone : manager.getAll()) {
            World world = zone.worldName().equals("world") ? overworld : nether;
            assertMatchesLinear(new Location(world, zone.minX(), zone.minY(), zone.minZ()));
            assertMatchesLinear(new Location(world, zone.maxX(), zone.maxY(), zone.maxZ()));
        }
        assertMatchesLinear(new Location(null, 0, 0, 0));
    }

    @Test
    void largeZoneAndNegativeBoundariesAreInclusive() {
        manager.create("large", ZoneType.NO_RIDE,
                new Location(overworld, 30000000, 320, 30000000),
                new Location(overworld, -30000000, -64, -30000000));
        manager.create("negative", ZoneType.CHARGE,
                new Location(overworld, -17, -10, -33), new Location(overworld, -16, -5, -32));

        assertTrue(manager.isNoRide(new Location(overworld, -30000000, -64, -30000000)));
        assertTrue(manager.isNoRide(new Location(overworld, 30000000, 320, 30000000)));
        assertFalse(manager.isNoRide(new Location(overworld, Math.nextDown(-30000000.0), 0, 0)));
        assertFalse(manager.isNoRide(new Location(overworld, 0, Math.nextUp(320.0), 0)));
        assertFalse(manager.isNoRide(new Location(nether, 0, 0, 0)));
        assertTrue(manager.isCharge(new Location(overworld, -17, -10, -33)));
        assertTrue(manager.isCharge(new Location(overworld, -16, -5, -32)));
        assertFalse(manager.isCharge(new Location(overworld, Math.nextDown(-17.0), -10, -33)));
        assertFalse(manager.isCharge(new Location(overworld, -16, -5, Math.nextUp(-32.0))));
    }

    @Test
    void createReplacementAndDeleteInvalidateIndex() {
        Location oldPoint = new Location(overworld, -20, 5, -20);
        Location newPoint = new Location(nether, 20, 5, 20);
        assertFalse(manager.isNoRide(oldPoint));
        manager.create("Home", ZoneType.NO_RIDE, oldPoint, oldPoint);
        assertTrue(manager.isNoRide(oldPoint));
        assertMatchesLinear(oldPoint);

        manager.create("HOME", ZoneType.CHARGE, newPoint, newPoint);
        assertEquals(1, manager.getAll().size());
        assertFalse(manager.isNoRide(oldPoint));
        assertFalse(manager.isCharge(oldPoint));
        assertTrue(manager.isCharge(newPoint));
        assertMatchesLinear(oldPoint);
        assertMatchesLinear(newPoint);

        assertFalse(manager.delete("missing"));
        assertTrue(manager.delete("home"));
        assertFalse(manager.isCharge(newPoint));
        assertMatchesLinear(newPoint);
        assertFalse(manager.delete("HOME"));
    }

    @Test
    void zonesPersistAcrossManagerRestartAndDelete() {
        manager.create("persistent", ZoneType.CHARGE,
                new Location(overworld, 1, 2, 3), new Location(overworld, 4, 5, 6));
        manager.shutdown().join();

        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        ZoneManager restarted = new ZoneManager(plugin, new ZoneStore(dataFolder, java.util.logging.Logger.getAnonymousLogger()), Runnable::run);
        restarted.loadAsync().join();
        assertEquals(ZoneType.CHARGE, restarted.get("PERSISTENT").type());
        restarted.create("persistent", ZoneType.NO_RIDE,
                new Location(overworld, 7, 8, 9), new Location(overworld, 10, 11, 12));
        restarted.shutdown().join();
        restarted = new ZoneManager(plugin, new ZoneStore(dataFolder, java.util.logging.Logger.getAnonymousLogger()), Runnable::run);
        restarted.loadAsync().join();
        assertEquals(7, restarted.get("persistent").minX());
        assertEquals(ZoneType.NO_RIDE, restarted.get("persistent").type());
        assertTrue(restarted.delete("persistent"));
        restarted.shutdown().join();
        restarted = new ZoneManager(plugin, new ZoneStore(dataFolder, java.util.logging.Logger.getAnonymousLogger()), Runnable::run);
        restarted.loadAsync().join();
        assertTrue(restarted.getAll().isEmpty());
        restarted.shutdown().join();
    }

    @Test
    void shutdownRejectsMutationsBeforeChangingCache() {
        manager.create("kept", ZoneType.CHARGE, new Location(overworld, 1, 2, 3), new Location(overworld, 1, 2, 3));
        manager.shutdown().join();
        assertFalse(manager.isReady());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> manager.delete("kept"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> manager.create("new", ZoneType.CHARGE,
                new Location(overworld, 1, 2, 3), new Location(overworld, 1, 2, 3)));
        assertEquals(1, manager.getAll().size());
    }

    @Test
    void shutdownBeforePublicationDoesNotPublishLoadedZones() throws Exception {
        var publications = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        ZoneManager pending = new ZoneManager(mock(MotorcyclePlugin.class),
                new ZoneStore(dataFolder.resolve("pending"), java.util.logging.Logger.getAnonymousLogger()), publications::add);
        var loaded = pending.loadAsync();
        Runnable publish = publications.poll(5, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.jupiter.api.Assertions.assertNotNull(publish);
        pending.shutdown().join();
        publish.run();
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class, loaded::join);
        assertFalse(pending.isReady());
    }

    @Test
    void mutationBeforeLoadCannotOverwriteDatabase() {
        ZoneStore store = new ZoneStore(dataFolder, java.util.logging.Logger.getAnonymousLogger());
        ZoneManager notLoaded = new ZoneManager(mock(MotorcyclePlugin.class), store, Runnable::run);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> notLoaded.delete("anything"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> notLoaded.create("anything", ZoneType.CHARGE,
                new Location(overworld, 1, 2, 3), new Location(overworld, 1, 2, 3)));
        assertFalse(notLoaded.isReady());
        notLoaded.shutdown().join();
    }

    private void assertMatchesLinear(Location location) {
        for (ZoneType type : ZoneType.values()) {
            boolean expected = manager.getAll().stream()
                    .anyMatch(zone -> zone.type() == type && zone.contains(location));
            boolean actual = type == ZoneType.NO_RIDE ? manager.isNoRide(location) : manager.isCharge(location);
            assertEquals(expected, actual, () -> type + " at " + location);
        }
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        return world;
    }
}
