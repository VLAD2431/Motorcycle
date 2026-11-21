package VLADMIX90.motorcyclePlugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneStoreTest {
    @TempDir Path dataFolder;
    private ZoneStore store;

    @org.junit.jupiter.api.AfterEach
    void closeStore() {
        if (store != null) store.close();
    }

    @Test
    void importsLegacyZonesAndPreservesBackupAcrossRestart() throws Exception {
        Files.writeString(dataFolder.resolve("zones.yml"), """
                zones:
                  safe:
                    type: NO_RIDE
                    world: world
                    min:
                      x: -1
                      y: 0
                      z: -2
                    max:
                      x: 1
                      y: 10
                      z: 2
                """);
        store = new ZoneStore(dataFolder, Logger.getAnonymousLogger());
        assertEquals("safe", store.load().getFirst().name());
        assertTrue(Files.exists(dataFolder.resolve("zones.yml.bak")));
        store.close();
        store = new ZoneStore(dataFolder, Logger.getAnonymousLogger());
        ZoneStore restarted = new ZoneStore(dataFolder, Logger.getAnonymousLogger());
        assertEquals(1, restarted.load().size());
        restarted.close();
        store.close();
        store = null;
    }

    @Test
    void malformedLegacyZoneFailsWithoutCreatingBackup() throws Exception {
        Files.writeString(dataFolder.resolve("zones.yml"), "zones:\n  broken:\n    type: UNKNOWN\n    world: world\n");
        store = new ZoneStore(dataFolder, Logger.getAnonymousLogger());
        assertThrows(IllegalStateException.class, store::load);
        assertTrue(Files.notExists(dataFolder.resolve("zones.yml.bak")));
    }

    @Test
    void existingBackupIsNeverOverwritten() throws Exception {
        Files.writeString(dataFolder.resolve("zones.yml"), "zones: {}\n");
        Files.writeString(dataFolder.resolve("zones.yml.bak"), "original backup");
        store = new ZoneStore(dataFolder, Logger.getAnonymousLogger());
        assertThrows(IllegalStateException.class, store::load);
        assertEquals("original backup", Files.readString(dataFolder.resolve("zones.yml.bak")));
    }

    @Test
    void failedTransactionRollsBackRowsAndMarkerAndRetainsBackup() throws Exception {
        String zone = "    type: NO_RIDE\n    world: world\n    min: {x: 0, y: 0, z: 0}\n    max: {x: 1, y: 1, z: 1}\n";
        String legacy = "zones:\n  home:\n" + zone + "  HOME:\n" + zone;
        Files.writeString(dataFolder.resolve("zones.yml"), legacy);
        store = new ZoneStore(dataFolder, Logger.getAnonymousLogger());
        assertThrows(IllegalStateException.class, store::load);
        assertEquals(legacy, Files.readString(dataFolder.resolve("zones.yml.bak")));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dataFolder.resolve("database.db"));
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM zones")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM metadata")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rows.next());
                assertEquals("wal", rows.getString(1));
            }
        }
    }
}
