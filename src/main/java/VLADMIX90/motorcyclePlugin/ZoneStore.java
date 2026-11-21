package VLADMIX90.motorcyclePlugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

final class ZoneStore {
    private final Path dataFolder;
    private final Logger logger;
    private Connection connection;

    ZoneStore(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    synchronized List<Zone> load() {
        try {
            Files.createDirectories(dataFolder);
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.resolve("database.db"));
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("CREATE TABLE IF NOT EXISTS zones (name TEXT PRIMARY KEY COLLATE NOCASE, type TEXT NOT NULL, world TEXT NOT NULL, min_x REAL NOT NULL, min_y REAL NOT NULL, min_z REAL NOT NULL, max_x REAL NOT NULL, max_y REAL NOT NULL, max_z REAL NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            }
            if (!migrationComplete()) importLegacy();
            return readZones();
        } catch (Exception exception) {
            closeQuietly();
            throw new IllegalStateException("Unable to load zones from SQLite", exception);
        }
    }

    synchronized void upsert(Zone zone) {
        String sql = "INSERT INTO zones VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(name) DO UPDATE SET type=excluded.type, world=excluded.world, min_x=excluded.min_x, min_y=excluded.min_y, min_z=excluded.min_z, max_x=excluded.max_x, max_y=excluded.max_y, max_z=excluded.max_z";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, zone);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save zone " + zone.name(), exception);
        }
    }

    synchronized void delete(String name) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM zones WHERE name = ?")) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete zone " + name, exception);
        }
    }

    CompletableFuture<Void> upsert(Zone zone, Executor executor) {
        return CompletableFuture.runAsync(() -> upsert(zone), executor);
    }

    CompletableFuture<Void> delete(String name, Executor executor) {
        return CompletableFuture.runAsync(() -> delete(name), executor);
    }

    private boolean migrationComplete() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM metadata WHERE key='zones_yaml_migrated'");
             ResultSet result = statement.executeQuery()) {
            return result.next();
        }
    }

    private void importLegacy() throws IOException, SQLException {
        Path legacy = dataFolder.resolve("zones.yml");
        if (!Files.exists(legacy)) return;
        Path backup = dataFolder.resolve("zones.yml.bak");
        if (Files.exists(backup)) throw new IOException("Zone backup already exists: " + backup);
        List<Zone> imported = parseLegacy(legacy);
        Files.copy(legacy, backup);
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Zone zone : imported) insert(zone);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO metadata(key, value) VALUES ('zones_yaml_migrated', 'true')")) {
                statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            if (exception instanceof SQLException sqlException) throw sqlException;
            throw new IOException("Unable to import zones.yml", exception);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private List<Zone> parseLegacy(Path legacy) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(legacy.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Invalid zones.yml syntax", exception);
        }
        ConfigurationSection section = yaml.getConfigurationSection("zones");
        if (section == null && yaml.getKeys(false).isEmpty()) return List.of();
        if (section == null) throw new IOException("zones.yml has no zones section");
        List<Zone> parsed = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            String path = "zones." + name;
            if (!name.matches("[A-Za-z0-9_-]{1,32}")) throw new IOException("Invalid zone name: " + name);
            String typeName = yaml.getString(path + ".type");
            ZoneType type;
            try {
                type = ZoneType.valueOf(typeName);
            } catch (RuntimeException exception) {
                throw new IOException("Invalid type for zone " + name, exception);
            }
            String world = yaml.getString(path + ".world");
            if (world == null || world.isBlank()) throw new IOException("Missing world for zone " + name);
            double minX = coordinate(yaml, path + ".min.x", name);
            double minY = coordinate(yaml, path + ".min.y", name);
            double minZ = coordinate(yaml, path + ".min.z", name);
            double maxX = coordinate(yaml, path + ".max.x", name);
            double maxY = coordinate(yaml, path + ".max.y", name);
            double maxZ = coordinate(yaml, path + ".max.z", name);
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IOException("Invalid bounds for zone " + name);
            parsed.add(new Zone(name, type, world, minX, minY, minZ, maxX, maxY, maxZ));
        }
        return parsed;
    }

    private static double coordinate(YamlConfiguration yaml, String path, String name) throws IOException {
        Object value = yaml.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IOException("Invalid coordinate " + path + " for zone " + name);
        }
        return number.doubleValue();
    }

    private void insert(Zone zone) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO zones VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bind(statement, zone);
            statement.executeUpdate();
        }
    }

    private List<Zone> readZones() throws SQLException {
        List<Zone> result = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT * FROM zones ORDER BY name")) {
            while (rows.next()) result.add(new Zone(rows.getString("name"), ZoneType.valueOf(rows.getString("type")),
                    rows.getString("world"), rows.getDouble("min_x"), rows.getDouble("min_y"), rows.getDouble("min_z"),
                    rows.getDouble("max_x"), rows.getDouble("max_y"), rows.getDouble("max_z")));
        }
        return result;
    }

    private static void bind(PreparedStatement statement, Zone zone) throws SQLException {
        statement.setString(1, zone.name()); statement.setString(2, zone.type().name()); statement.setString(3, zone.worldName());
        statement.setDouble(4, zone.minX()); statement.setDouble(5, zone.minY()); statement.setDouble(6, zone.minZ());
        statement.setDouble(7, zone.maxX()); statement.setDouble(8, zone.maxY()); statement.setDouble(9, zone.maxZ());
    }

    private void closeQuietly() {
        try { if (connection != null) connection.close(); }
        catch (SQLException exception) { logger.warning("Failed to close zone database: " + exception.getMessage()); }
        finally { connection = null; }
    }

    synchronized void close() {
        closeQuietly();
    }
}
