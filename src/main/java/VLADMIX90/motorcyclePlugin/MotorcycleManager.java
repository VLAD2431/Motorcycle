package VLADMIX90.motorcyclePlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;

import net.kyori.adventure.text.Component;

/**
 * Полностью переработанный ховербайк.
 *
 * Несущая сущность — невидимая свинья (Pig): у неё настоящий passenger-слот,
 * нормальная камера Minecraft и нормальная передача движения пассажиру.
 * Сам игрок больше не телепортируется и не меняет свою камеру плагином.
 */
public final class MotorcycleManager {
    private final MotorcyclePlugin plugin;

    private final BikeRegistry registry = new BikeRegistry();
    private final Map<UUID, Motorcycle> motorcycles = registry.bikes();
    private final BatterySystem batteries;
    private final PhysicsEngine physics;
    private final CollisionProbe collision;
    private final MotorcycleSounds sounds;
    private final SurfaceParticles particles;
    private final Map<UUID, UUID> playerToBike = new HashMap<>();
    private final Map<UUID, Integer> pendingStorageTasks = new HashMap<>();
    private final Map<UUID, Integer> pendingInstallTasks = new HashMap<>();
    private final Map<UUID, Long> installCooldowns = new HashMap<>();
    private final Map<UUID, Motorcycle> storageBikes = new HashMap<>();
    private final Map<UUID, VisualState> visualStates = new HashMap<>();

    private final Map<UUID, Pig> loadedSeats = registry.seats();
    private final Map<UUID, ItemDisplay> loadedVisuals = registry.visuals();
    private final Map<UUID, ItemDisplay> loadedLightVisuals = registry.lightVisuals();

    private int tickTask = -1;
    private long ticksElapsed;

    public MotorcycleManager(MotorcyclePlugin plugin) {
        this.plugin = plugin;
        batteries = new BatterySystem(plugin);
        physics = new PhysicsEngine(plugin);
        collision = new CollisionProbe(plugin);
        sounds = new MotorcycleSounds(plugin);
        particles = new SurfaceParticles(plugin);
    }

    public void start() {
        loadExistingLoadedEntities();
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
    }

    public void registerLoadedEntity(Entity entity) {
        String idString = entity.getPersistentDataContainer().get(plugin.bikeIdKey(), PersistentDataType.STRING);
        if (idString == null)
            return;

        UUID id;
        try {
            id = UUID.fromString(idString);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        if (entity.getPersistentDataContainer().has(plugin.interactionKey(), PersistentDataType.BYTE)) {
            entity.remove();
            return;
        }

        if (entity instanceof Pig pig
                && pig.getPersistentDataContainer().has(plugin.seatKey(), PersistentDataType.BYTE)) {
            configurePig(pig);
            loadedSeats.put(id, pig);
            tryRegisterLoaded(id);
            return;
        }

        // Удаляем старые сиденья из версий на ArmorStand, чтобы старые сущности
        // не оставались невидимыми и не перехватывали взаимодействия.
        if (entity.getPersistentDataContainer().has(plugin.seatKey(), PersistentDataType.BYTE)) {
            entity.remove();
            return;
        }

        if (entity instanceof ItemDisplay visual
                && visual.getPersistentDataContainer().has(plugin.visualKey(), PersistentDataType.BYTE)) {
            loadedVisuals.put(id, visual);
            tryRegisterLoaded(id);
            return;
        }

        if (entity instanceof ItemDisplay visual
                && visual.getPersistentDataContainer().has(plugin.lightVisualKey(), PersistentDataType.BYTE)) {
            loadedLightVisuals.put(id, visual);
            Motorcycle bike = motorcycles.get(id);
            if (bike != null) bike.setLightVisual(visual);
        }
    }

    private void tryRegisterLoaded(UUID id) {
        if (motorcycles.containsKey(id))
            return;
        Pig seat = loadedSeats.get(id);
        ItemDisplay visual = loadedVisuals.get(id);
        if (seat == null || visual == null || !seat.isValid() || !visual.isValid())
            return;

        int capacity = capacity();
        int battery = seat.getPersistentDataContainer()
                .getOrDefault(plugin.batteryKey(), PersistentDataType.INTEGER, capacity);
        float heading = seat.getPersistentDataContainer()
                .getOrDefault(plugin.headingKey(), PersistentDataType.FLOAT, seat.getYaw());

        Motorcycle bike = new Motorcycle(id, seat, visual,
                Math.min(capacity, Math.max(0, battery)), heading);
        ItemDisplay lightVisual = loadedLightVisuals.get(id);
        if (lightVisual != null && lightVisual.isValid()) bike.setLightVisual(lightVisual);
        bike.setGravibikeDecal(
                GravibikeDecalState.read(visual.getPersistentDataContainer(), plugin.gravibikeSkinIdKey()),
                GravibikeDecalState.read(visual.getPersistentDataContainer(), plugin.gravibikeItemModelKey()),
                GravibikeDecalState.read(visual.getPersistentDataContainer(), plugin.gravibikeCustomModelDataKey()));
        visual.setItemStack(createItem(bike.battery(), bike.gravibikeSkinId(), bike.gravibikeItemModel(),
                bike.gravibikeCustomModelData()));
        bike.setPersistedHeading(heading);
        batteries.restore(bike);
        motorcycles.put(id, bike);
        syncVisual(bike);
    }

    private void loadExistingLoadedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                registerLoadedEntity(entity);
            }
        }
    }

    static CustomModelData lightCustomModelData(String value) {
        return CustomModelData.customModelData().addString(value).build();
    }

    static int clampLightBrightness(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private ItemStack applyLightCustomModelData(ItemStack item, ConfigurationSection section) {
        if (section == null) return item;
        List<Float> floats = section.getFloatList("floats");
        List<Boolean> flags = section.getBooleanList("flags");
        List<String> strings = section.getStringList("strings");
        List<Color> colors = configColors(section);
        if (floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty()) return item;
        var builder = CustomModelData.customModelData();
        floats.forEach(builder::addFloat);
        flags.forEach(builder::addFlag);
        strings.stream().filter(value -> !value.isBlank()).forEach(builder::addString);
        colors.forEach(builder::addColor);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder);
        return item;
    }

    public void shutdown() {
        if (tickTask != -1)
            Bukkit.getScheduler().cancelTask(tickTask);
        pendingStorageTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingInstallTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        pendingStorageTasks.clear();
        pendingInstallTasks.clear();
        storageBikes.values().forEach(bike -> bike.setStorageInProgress(false));
        storageBikes.clear();
        motorcycles.values().forEach(bike -> {
            batteries.persist(bike);
            persistHeading(bike);
        });

        for (UUID playerId : new HashSet<>(playerToBike.keySet())) {
            var player = Bukkit.getPlayer(playerId);
            if (player != null)
                player.leaveVehicle();
        }
        playerToBike.clear();
        motorcycles.values().forEach(this::removePassengerSeat);
        registry.clear();
        visualStates.clear();
        sounds.clear();
    }

    /**
     * Called after {@code /moto reload}. Visual state is deduplicated against
     * the last packet, so without dropping the cache a new model offset would
     * only reach a bike once it happened to move.
     */
    public void onConfigReloaded() {
        visualStates.clear();
        sounds.clear();
    }

    public ItemStack createItem(int battery) {
        return createItem(battery, null, null, null);
    }

    private ItemStack createItem(int battery, String skinId, String itemModel, String customModelData) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("motorcycle.item.material", "BARRIER"));
        if (material == null || !material.isItem())
            material = Material.BARRIER;

        int clampedBattery = Math.max(0, Math.min(capacity(), battery));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.getPersistentDataContainer().set(plugin.bikeKey(), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(plugin.batteryKey(), PersistentDataType.INTEGER, clampedBattery);
        meta.getPersistentDataContainer().set(plugin.bikeIdKey(), PersistentDataType.STRING,
                UUID.randomUUID().toString());
        GravibikeDecalState.write(meta.getPersistentDataContainer(), plugin.gravibikeSkinIdKey(),
                plugin.gravibikeItemModelKey(), plugin.gravibikeCustomModelDataKey(), skinId, itemModel,
                customModelData);
        meta.setMaxStackSize(Math.min(99, Math.max(1, plugin.getConfig().getInt("motorcycle.item.max-stack-size", 1))));
        meta.displayName(plugin.miniMessage().deserialize(replace(
                plugin.getConfig().getString("motorcycle.item.name", "<white>Гравибайк"), clampedBattery, 0, 0)));

        List<Component> lore = plugin.getConfig().getStringList("motorcycle.item.lore").stream()
                .map(line -> plugin.miniMessage().deserialize(replace(line, clampedBattery, 0, 0)))
                .toList();
        meta.lore(lore);

        String model = itemModel == null || itemModel.isBlank()
                ? plugin.getConfig().getString("motorcycle.item.model", "") : itemModel;
        if (model != null && !model.isBlank() && model.contains(":")) {
            var key = org.bukkit.NamespacedKey.fromString(model);
            if (key != null)
                meta.setItemModel(key);
        }

        item.setItemMeta(meta);
        if (customModelData != null && !customModelData.isBlank()) {
            var builder = CustomModelData.customModelData().addString(customModelData);
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder);
        } else if (skinId == null || skinId.isBlank()) {
            item = applyCustomModelData(item);
        } else {
            item.resetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        }
        return applyCustomData(item);
    }

    public boolean applyDecalToItem(ItemStack item, String skinId, String itemModel, String customModelData) {
        if (!isMotorcycleItem(item) || skinId == null || skinId.isBlank()
                || itemModel == null || itemModel.isBlank()) return false;
        ItemMeta meta = item.getItemMeta();
        var model = org.bukkit.NamespacedKey.fromString(itemModel);
        if (model == null) return false;
        GravibikeDecalState.write(meta.getPersistentDataContainer(), plugin.gravibikeSkinIdKey(),
                plugin.gravibikeItemModelKey(), plugin.gravibikeCustomModelDataKey(), skinId, itemModel,
                customModelData);
        meta.setItemModel(model);
        item.setItemMeta(meta);
        if (customModelData != null && !customModelData.isBlank()) {
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(customModelData));
        } else {
            item.resetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        }
        return true;
    }

    public boolean hasDecalData(ItemStack item) {
        return isMotorcycleItem(item) && GravibikeDecalState.read(
                item.getItemMeta().getPersistentDataContainer(), plugin.gravibikeSkinIdKey()) != null;
    }

    public boolean applyDecalToNearest(org.bukkit.entity.Player player, double radius, String skinId,
            String itemModel, String customModelData) {
        if (player == null || radius < 0 || skinId == null || skinId.isBlank()
                || itemModel == null || itemModel.isBlank()) return false;
        Motorcycle nearest = null;
        double nearestDistance = radius * radius;
        for (Motorcycle bike : motorcycles.values()) {
            if (!bike.seat().isValid() || !bike.visual().isValid()
                    || !bike.seat().getWorld().equals(player.getWorld())) continue;
            double distance = bike.seat().getLocation().distanceSquared(player.getLocation());
            if (distance <= nearestDistance) {
                nearest = bike;
                nearestDistance = distance;
            }
        }
        if (nearest == null) return false;
        nearest.setGravibikeDecal(skinId, itemModel, customModelData);
        nearest.visual().setItemStack(createItem(nearest.battery(), skinId, itemModel, customModelData));
        GravibikeDecalState.write(nearest.visual().getPersistentDataContainer(), plugin.gravibikeSkinIdKey(),
                plugin.gravibikeItemModelKey(), plugin.gravibikeCustomModelDataKey(), skinId, itemModel,
                customModelData);
        return true;
    }

    /**
     * Записывает компонент {@code minecraft:custom_model_data} — с 1.21.4 именно
     * по нему текстур-пак выбирает модель предмета
     * ({@code assets/<ns>/items/<id>.json}, свойство {@code minecraft:custom_model_data}).
     * <p>
     * Набор значений берётся из {@code motorcycle.item.custom-model-data}: строки
     * попадают в {@code strings} (пак ищет их в блоке when модели select), числа —
     * в {@code floats} ({@code range_dispatch}), флаги — в {@code flags}
     * ({@code condition}), цвета — в {@code colors} (tints). Пустые списки не
     * задаются, чтобы не затирать значения, уже записанные в предмет.
     */
    private ItemStack applyCustomModelData(ItemStack item) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("motorcycle.item.custom-model-data");
        if (section == null)
            return item;

        List<Float> floats = section.getFloatList("floats");
        List<Boolean> flags = section.getBooleanList("flags");
        List<String> strings = section.getStringList("strings");
        List<Color> colors = configColors(section);
        if (floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty())
            return item;

        var builder = CustomModelData.customModelData();
        floats.forEach(builder::addFloat);
        flags.forEach(builder::addFlag);
        strings.forEach(builder::addString);
        colors.forEach(builder::addColor);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder);
        return item;
    }

    /** Цвета из конфига: число (0xRRGGBB), строка-шестизначный hex или #RRGGBB. */
    private static List<Color> configColors(ConfigurationSection section) {
        List<Color> colors = new ArrayList<>();
        for (Object raw : section.getList("colors", Collections.emptyList())) {
            Color color = parseColor(raw);
            if (color != null)
                colors.add(color);
        }
        return colors;
    }

    private static Color parseColor(Object raw) {
        if (raw instanceof Color color)
            return color;
        if (raw instanceof Number number)
            return Color.fromRGB(number.intValue());
        if (raw instanceof String text) {
            String value = text.trim();
            try {
                return Color.fromRGB(value.startsWith("#")
                        ? Integer.parseInt(value.substring(1), 16)
                        : Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Записывает компонент {@code minecraft:custom_data} прямо в NBT предмета.
     * <p>
     * В API Paper этого компонента нет: {@link ItemMeta#getPersistentDataContainer()}
     * пишет ключи с префиксом пространства имён ({@code minecraft:base_bike}), а
     * предикат {@code minecraft:custom_data} в текстур-паке ищет «голый» ключ
     * ({@code base_bike}). Поэтому предмет собирается в JSON, в блок
     * {@code components} дописывается нужный NBT и предмет собирается обратно.
     * Уже существующие данные (в том числе PDC самого плагина) сохраняются.
     * <p>
     * Типы значений из конфига сохраняются (int остаётся int, а не 1.0), иначе
     * предикат в паке перестаёт совпадать.
     *
     * @return предмет с custom_data; если запись не удалась — исходный предмет
     */
    private ItemStack applyCustomData(ItemStack item) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("motorcycle.item.custom-data");
        if (section == null)
            return item;

        JsonObject json;
        JsonObject components;
        try {
            json = Bukkit.getUnsafe().serializeItemAsJson(item);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Не удалось записать minecraft:custom_data в предмет мотоцикла: "
                    + ex.getMessage());
            return item;
        }
        components = json.has("components") && json.get("components").isJsonObject()
                ? json.getAsJsonObject("components")
                : new JsonObject();
        json.add("components", components);

        // Свои ключи перезаписывают уже записанные, чужие (в том числе PDC плагина) — сохраняются.
        Set<String> overridden = new HashSet<>(section.getKeys(false));
        StringBuilder body = new StringBuilder();
        JsonObject merged = new JsonObject();
        JsonElement existing = components.get("minecraft:custom_data");
        boolean objectForm = existing != null && existing.isJsonObject();
        if (existing != null) {
            if (objectForm) {
                for (Map.Entry<String, JsonElement> entry : existing.getAsJsonObject().entrySet()) {
                    if (overridden.contains(entry.getKey()))
                        continue;
                    body.append(snbtKey(entry.getKey())).append(':').append(snbtJson(entry.getValue())).append(',');
                    merged.add(entry.getKey(), entry.getValue());
                }
            } else if (existing.isJsonPrimitive() && existing.getAsJsonPrimitive().isString()) {
                // NBT-компаунд сервер отдаёт строкой SNBT. Повторяющиеся ключи
                // не трогаем: NBT-компаунд отдаёт последнее значение.
                String snbt = existing.getAsString().trim();
                if (snbt.startsWith("{") && snbt.endsWith("}"))
                    snbt = snbt.substring(1, snbt.length() - 1).trim();
                body.append(snbt);
            }
        }

        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            appendSnbtPair(body, key, value instanceof ConfigurationSection child
                    ? snbtSection(child)
                    : snbtScalar(value));
            merged.add(key, jsonValue(value));
        }

        if (body.isEmpty())
            return item;

        // Форму компонента, которую отдал сервер, придерживаемся первой:
        // иначе предмет не соберётся обратно. Не вышло — пробуем противоположную.
        for (boolean stringForm : new boolean[] { !objectForm, objectForm }) {
            try {
                JsonObject copy = json.deepCopy();
                JsonObject copyComponents = copy.getAsJsonObject("components");
                if (stringForm)
                    copyComponents.addProperty("minecraft:custom_data", "{" + body + "}");
                else
                    copyComponents.add("minecraft:custom_data", merged);
                return Bukkit.getUnsafe().deserializeItemFromJson(copy);
            } catch (RuntimeException ignored) {
                // форма не подошла — пробуем следующую
            }
        }

        plugin.getLogger().warning("Не удалось записать minecraft:custom_data в предмет мотоцикла: "
                + "сервер принял предмет только без этого компонента");
        return item;
    }

    /** Значение из конфига в виде JSON — для формы, где компонент пишется объектом. */
    private static JsonElement jsonValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            JsonObject nested = new JsonObject();
            for (String key : section.getKeys(false))
                nested.add(key, jsonValue(section.get(key)));
            return nested;
        }
        if (value instanceof Number number)
            return new JsonPrimitive(number);
        if (value instanceof Boolean bool)
            return new JsonPrimitive(bool);
        if (value == null)
            return new JsonPrimitive("0b");
        return new JsonPrimitive(String.valueOf(value));
    }

    /** Дописывает пару {@code key:value} в тело NBT-компаунда с запятой-разделителем. */
    private static void appendSnbtPair(StringBuilder body, String key, String value) {
        if (value == null || value.isEmpty())
            return;
        int length = body.length();
        if (length > 0) {
            char last = body.charAt(length - 1);
            if (last != ',' && last != '{')
                body.append(',');
        }
        body.append(snbtKey(key)).append(':').append(value);
    }

    /** Значение из конфига в виде NBT: типы сохраняются, иначе предикат пака не совпадёт. */
    private static String snbtScalar(Object value) {
        if (value == null)
            return "0b";
        if (value instanceof Boolean bool)
            return bool ? "1b" : "0b";
        if (value instanceof Byte || value instanceof Short || value instanceof Integer)
            return value.toString();
        if (value instanceof Long || value instanceof Float || value instanceof Double)
            return value.toString() + (value instanceof Long ? "L" : value instanceof Float ? "f" : "d");
        if (value instanceof Number number)
            return number.toString();
        if (value instanceof List<?> || value instanceof Map<?, ?>)
            return "\"" + value + "\""; // список/карта из YAML: строковое представление
        return snbtString(value.toString());
    }

    /** Вложенный раздел конфига — вложенный NBT-компаунд. */
    private static String snbtSection(ConfigurationSection section) {
        StringBuilder nested = new StringBuilder("{");
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            appendSnbtPair(nested, key, value instanceof ConfigurationSection child
                    ? snbtSection(child)
                    : snbtScalar(value));
        }
        return nested.append('}').toString();
    }

    /** Значение компонента, уже пришедшее в JSON, в виде NBT. */
    private static String snbtJson(JsonElement element) {
        if (element == null || element.isJsonNull())
            return "0b";
        if (element.isJsonObject()) {
            StringBuilder nested = new StringBuilder("{");
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
                appendSnbtPair(nested, entry.getKey(), snbtJson(entry.getValue()));
            return nested.append('}').toString();
        }
        if (element.isJsonArray())
            return element.getAsJsonArray().toString();
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean())
                return primitive.getAsBoolean() ? "1b" : "0b";
            if (primitive.isNumber()) {
                String literal = primitive.getAsString();
                return literal.indexOf('.') >= 0 || literal.indexOf('e') >= 0 || literal.indexOf('E') >= 0
                        ? literal + "d"
                        : literal;
            }
            return snbtString(primitive.getAsString());
        }
        return "0b";
    }

    /** Ключ NBT: простые имена не берём в кавычки, остальные экранируем по правилам SNBT. */
    private static String snbtKey(String key) {
        return key.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '+')
                ? key
                : snbtString(key);
    }

    private static String snbtString(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"')
                quoted.append('\\');
            quoted.append(c);
        }
        return quoted.append('"').toString();
    }

    public boolean isMotorcycleItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(plugin.bikeKey(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public int getBattery(ItemStack item) {
        if (!isMotorcycleItem(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(plugin.batteryKey(), PersistentDataType.INTEGER, capacity());
    }

    public ItemStack updateItemBattery(ItemStack item, int battery) {
        if (!isMotorcycleItem(item))
            return item;
        ItemMeta meta = item.getItemMeta();
        int value = Math.max(0, Math.min(capacity(), battery));
        meta.getPersistentDataContainer().set(plugin.batteryKey(), PersistentDataType.INTEGER, value);
        meta.displayName(plugin.miniMessage().deserialize(replace(
                plugin.getConfig().getString("motorcycle.item.name", "<white>Гравибайк"), value, 0, 0)));
        meta.lore(plugin.getConfig().getStringList("motorcycle.item.lore").stream()
                .map(line -> plugin.miniMessage().deserialize(replace(line, value, 0, 0)))
                .toList());
        item.setItemMeta(meta);
        // custom_model_data и custom_data живут мимо ItemMeta, поэтому после
        // обновления имени/заряда подтверждаем, что модель из текстур-пака
        // осталась на месте.
        String decalId = GravibikeDecalState.read(meta.getPersistentDataContainer(), plugin.gravibikeSkinIdKey());
        String decalModel = GravibikeDecalState.read(meta.getPersistentDataContainer(), plugin.gravibikeItemModelKey());
        String decalCustomModelData = GravibikeDecalState.read(meta.getPersistentDataContainer(),
                plugin.gravibikeCustomModelDataKey());
        if (decalId != null && decalModel != null) {
            meta.setItemModel(org.bukkit.NamespacedKey.fromString(decalModel));
            item.setItemMeta(meta);
            if (decalCustomModelData == null || decalCustomModelData.isBlank()) {
                item.resetData(DataComponentTypes.CUSTOM_MODEL_DATA);
            } else {
                item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                        CustomModelData.customModelData().addString(decalCustomModelData));
            }
            return applyCustomData(item);
        }
        return applyCustomData(applyCustomModelData(item));
    }

    public Motorcycle spawn(Location vehicleBaseLocation, int battery) {
        return spawn(vehicleBaseLocation, battery, null, null, null);
    }

    private Motorcycle spawn(Location vehicleBaseLocation, int battery, String skinId, String itemModel,
            String customModelData) {
        UUID id = UUID.randomUUID();
        float initialYaw = installationYaw();

        Location base = vehicleBaseLocation.clone();
        base.setYaw(initialYaw);
        base.setPitch(0);
        base = snapBaseToHover(base);
        if (!collision.volumeClear(base, collision.supportYOrDefault(base))) {
            throw new IllegalArgumentException("Deployment space is obstructed");
        }
        Location seatLocation = toSeatLocation(base);

        int clampedBattery = Math.max(0, Math.min(capacity(), battery));

        Pig pig = base.getWorld().spawn(seatLocation, Pig.class, spawnedPig -> {
            spawnedPig.getPersistentDataContainer().set(plugin.seatKey(), PersistentDataType.BYTE, (byte) 1);
            configureNewPig(spawnedPig);
        });
        // A cancelled CreatureSpawnEvent can return a Pig that was never added to the world.
        if (!pig.isValid()) {
            pig.remove();
            throw new SpawnRejectedException("Driver seat spawn was cancelled");
        }
        pig.getPersistentDataContainer().set(plugin.bikeIdKey(), PersistentDataType.STRING, id.toString());
        pig.getPersistentDataContainer().set(plugin.batteryKey(), PersistentDataType.INTEGER, clampedBattery);
        pig.getPersistentDataContainer().set(plugin.headingKey(), PersistentDataType.FLOAT, initialYaw);

        ItemDisplay visual;
        try {
            visual = base.getWorld().spawn(base, ItemDisplay.class, display -> {
                display.setItemStack(createItem(clampedBattery, skinId, itemModel, customModelData));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setPersistent(true);
                display.setTeleportDuration(3);
                display.setInterpolationDuration(3);
                display.getPersistentDataContainer().set(plugin.visualKey(), PersistentDataType.BYTE, (byte) 1);
                display.getPersistentDataContainer().set(plugin.bikeIdKey(), PersistentDataType.STRING, id.toString());
                GravibikeDecalState.write(display.getPersistentDataContainer(), plugin.gravibikeSkinIdKey(),
                        plugin.gravibikeItemModelKey(), plugin.gravibikeCustomModelDataKey(), skinId, itemModel,
                        customModelData);
            });
        } catch (RuntimeException ex) {
            pig.remove();
            throw ex;
        }
        if (!visual.isValid() || !pig.isValid()) {
            visual.remove();
            pig.remove();
            throw new SpawnRejectedException("Motorcycle entity spawn was cancelled");
        }

        Motorcycle bike = new Motorcycle(id, pig, visual, clampedBattery, initialYaw);
        bike.setGravibikeDecal(skinId, itemModel, customModelData);
        try {
            String lightModel = plugin.getConfig().getString("motorcycle.visual.light-model", "");
            var lightModelKey = lightModel == null || lightModel.isBlank() ? null
                    : org.bukkit.NamespacedKey.fromString(lightModel);
            ConfigurationSection lightModelData = plugin.getConfig()
                    .getConfigurationSection("motorcycle.visual.light-custom-model-data");
            if (lightModelKey != null || lightModelData != null) {
                ItemDisplay lightVisual = base.getWorld().spawn(base, ItemDisplay.class, display -> {
                    ItemStack lightItem = new ItemStack(Material.BARRIER);
                    ItemMeta lightMeta = lightItem.getItemMeta();
                    if (lightModelKey != null) lightMeta.setItemModel(lightModelKey);
                    lightItem.setItemMeta(lightMeta);
                    lightItem = applyLightCustomModelData(lightItem, lightModelData);
                    display.setItemStack(lightItem);
                    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    display.setInvulnerable(true);
                    display.setGravity(false);
                    display.setPersistent(true);
                    display.setTeleportDuration(3);
                    display.setInterpolationDuration(3);
                    int blockBrightness = clampLightBrightness(plugin.getConfig()
                            .getInt("motorcycle.visual.light-brightness.block", 15));
                    int skyBrightness = clampLightBrightness(plugin.getConfig()
                            .getInt("motorcycle.visual.light-brightness.sky", 15));
                    display.setBrightness(new org.bukkit.entity.Display.Brightness(blockBrightness, skyBrightness));
                    display.getPersistentDataContainer().set(plugin.lightVisualKey(), PersistentDataType.BYTE, (byte) 1);
                    display.getPersistentDataContainer().set(plugin.bikeIdKey(), PersistentDataType.STRING, id.toString());
                });
                if (!lightVisual.isValid()) throw new SpawnRejectedException("Light model spawn was cancelled");
                bike.setLightVisual(lightVisual);
                loadedLightVisuals.put(id, lightVisual);
            }
        } catch (RuntimeException ex) {
            visual.remove();
            pig.remove();
            throw ex;
        }
        motorcycles.put(id, bike);
        loadedSeats.put(id, pig);
        loadedVisuals.put(id, visual);
        syncVisual(bike);
        return bike;
    }

    private void configureNewPig(Pig pig) {
        pig.setAdult();
        pig.setInvisible(true);
        // AI оставляем включённым: Paper указывает, что mob без AI
        // вообще не может нормально двигаться. Вместо этого отключаем
        // самостоятельное поведение через setAware(false). Внешний setVelocity()
        // при этом продолжает двигать сущность.
        pig.setAI(true);
        pig.setAware(false);
        pig.setGravity(false);
        pig.setInvulnerable(true);
        pig.setSilent(true);
        pig.setPersistent(true);
        pig.setCollidable(false);
        pig.setCanPickupItems(false);
        pig.setSaddle(false);
        var stepHeight = pig.getAttribute(Attribute.STEP_HEIGHT);
        if (stepHeight != null) {
            stepHeight.setBaseValue(0.0);
        }
    }

    private void configurePig(Pig pig) {
        configureNewPig(pig);
    }

    public void mount(org.bukkit.entity.Player player, Motorcycle motorcycle) {
        mount(player, motorcycle, false);
    }

    private void mount(org.bukkit.entity.Player player, Motorcycle motorcycle, boolean preserveHeading) {
        if (motorcycle.storageInProgress()) {
            send(player, "messages.storage-busy");
            return;
        }
        if (getByPlayer(player) != null || player.isInsideVehicle()) {
            send(player, "messages.already-riding");
            return;
        }
        if (plugin.zones().isNoRide(vehicleBaseLocation(motorcycle))) {
            send(player, "messages.no-ride");
            return;
        }
        if (!motorcycle.seat().isValid() || !motorcycle.visual().isValid())
            return;
        boolean playerAlreadyAboard = motorcycle.seat().getPassengers().stream()
                .anyMatch(passenger -> passenger instanceof org.bukkit.entity.Player);
        if (playerAlreadyAboard) {
            mountPassenger(player, motorcycle);
            return;
        }

        if (motorcycle.visual().getVehicle() != null) {
            motorcycle.visual().leaveVehicle();
        }

        boolean mounted = motorcycle.seat().addPassenger(player);
        if (!mounted) {
            send(player, "messages.mount-failed");
            return;
        }

        playerToBike.put(player.getUniqueId(), motorcycle.id());
        player.setFallDistance(0);
        motorcycle.setSpeed(0);
        if (!preserveHeading)
            alignHeadingToRider(motorcycle, player.getYaw());
        motorcycle.seat().setRotation(motorcycle.headingYaw(), 0);
        motorcycle.seat().setVelocity(new Vector());
    }

    private void mountPassenger(org.bukkit.entity.Player player, Motorcycle bike) {
        Pig seat = bike.passengerSeat();
        if (seat != null && seat.isValid() && !seat.getPassengers().isEmpty()) {
            send(player, "messages.already-riding");
            return;
        }
        if (seat == null || !seat.isValid()) {
            seat = bike.seat().getWorld().spawn(bike.seat().getLocation(), Pig.class, pig -> {
                configureNewPig(pig);
                pig.setPersistent(false);
                pig.getPersistentDataContainer().set(plugin.interactionKey(), PersistentDataType.BYTE, (byte) 1);
                pig.getPersistentDataContainer().set(plugin.bikeIdKey(), PersistentDataType.STRING, bike.id().toString());
            });
            bike.setPassengerSeat(seat);
        }
        updatePassengerSeat(bike);
        if (!seat.addPassenger(player)) {
            send(player, "messages.mount-failed");
            return;
        }
        playerToBike.put(player.getUniqueId(), bike.id());
        player.setFallDistance(0);
    }

    private boolean updatePassengerSeat(Motorcycle bike) {
        Pig seat = bike.passengerSeat();
        if (seat == null || !seat.isValid())
            return true;
        Location location = bike.seat().getLocation();
        double yaw = Math.toRadians(bike.headingYaw());
        location.add(Math.sin(yaw) * 0.8, 0, -Math.cos(yaw) * 0.8);
        location.setYaw(bike.headingYaw());
        return seat.teleport(location, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    public void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player player))
            return;
        Motorcycle bike = getByEntity(event.getDismounted());
        if (bike == null)
            return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getVehicle() == event.getDismounted())
                return;
            if (player.getVehicle() != bike.seat() && player.getVehicle() != bike.passengerSeat())
                playerToBike.remove(player.getUniqueId(), bike.id());
        });
    }

    public void removeRider(org.bukkit.entity.Player player) {
        Motorcycle bike = getByPlayer(player);
        if (bike == null) {
            playerToBike.remove(player.getUniqueId());
            return;
        }
        if (player.leaveVehicle()) {
            playerToBike.remove(player.getUniqueId(), bike.id());
        }
    }

    public Motorcycle getByPlayer(org.bukkit.entity.Player player) {
        UUID id = playerToBike.get(player.getUniqueId());
        return id == null ? null : motorcycles.get(id);
    }

    public Motorcycle getByEntity(Entity entity) {
        if (entity == null)
            return null;
        String raw = entity.getPersistentDataContainer().get(plugin.bikeIdKey(), PersistentDataType.STRING);
        if (raw == null)
            return null;
        try {
            return motorcycles.get(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ==================== Хранение / установка ====================

    public boolean beginStorageNearby(org.bukkit.entity.Player player) {
        if (player == null || getByPlayer(player) != null || pendingStorageTasks.containsKey(player.getUniqueId()))
            return false;

        double radius = Math.max(0.5, plugin.getConfig().getDouble("motorcycle.storage.nearby-radius", 3.0));
        Motorcycle nearest = findNearestMotorcycle(player.getLocation(), radius);
        if (nearest == null || nearest.storageInProgress())
            return false;

        startNearbyStorage(player, nearest);
        return true;
    }

    private Motorcycle findNearestMotorcycle(Location location, double radius) {
        Motorcycle nearest = null;
        double best = radius * radius;
        for (Motorcycle bike : motorcyclesInArea(location.getWorld(), location.getX() - radius,
                location.getZ() - radius, location.getX() + radius, location.getZ() + radius)) {
            boolean hasPlayerAboard = bike.seat().getPassengers().stream()
                    .anyMatch(passenger -> passenger instanceof org.bukkit.entity.Player)
                    || (bike.passengerSeat() != null && !bike.passengerSeat().getPassengers().isEmpty());
            if (!bike.seat().isValid() || bike.storageInProgress() || hasPlayerAboard)
                continue;
            if (!location.getWorld().equals(bike.seat().getWorld()))
                continue;
            double d = location.distanceSquared(vehicleBaseLocation(bike));
            if (d <= best) {
                best = d;
                nearest = bike;
            }
        }
        return nearest;
    }

    private void startNearbyStorage(org.bukkit.entity.Player player, Motorcycle bike) {
        int duration = Math.max(1, plugin.getConfig().getInt("motorcycle.storage.duration-seconds", 30));
        Location start = player.getLocation().clone();
        bike.setStorageInProgress(true);
        storageBikes.put(player.getUniqueId(), bike);
        applyOperationSlowness(player);
        send(player, "messages.storage-start");

        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int left = duration;

            @Override
            public void run() {
                if (!player.isOnline() || !bike.seat().isValid()
                        || getByPlayer(player) != null
                        || bike.seat().getPassengers().stream()
                                .anyMatch(passenger -> passenger instanceof org.bukkit.entity.Player)
                        || (bike.passengerSeat() != null && !bike.passengerSeat().getPassengers().isEmpty())
                        || !player.getWorld().equals(start.getWorld())
                        || player.getLocation().distanceSquared(start) > 0.16) {
                    cancelStorage(player, bike, "messages.storage-cancel");
                    return;
                }
                if (left <= 0) {
                    completeStorage(player, bike);
                    return;
                }
                applyOperationSlowness(player);
                sendActionBar(player, parseActionbar("motorcycle.actionbar.storage", bike, left));
                left--;
            }
        }, 0L, 20L);
        pendingStorageTasks.put(player.getUniqueId(), task);
    }

    private void completeStorage(org.bukkit.entity.Player player, Motorcycle bike) {
        cancelTask(pendingStorageTasks.remove(player.getUniqueId()));
        storageBikes.remove(player.getUniqueId());
        bike.setStorageInProgress(false);
        clearOperationSlowness(player);

        ItemStack item = createItem(bike.battery(), bike.gravibikeSkinId(), bike.gravibikeItemModel(),
                bike.gravibikeCustomModelData());
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        removeMotorcycle(bike);
        sendActionBar(player, Component.empty());
        send(player, "messages.storage-complete");
    }

    public void beginInstall(org.bukkit.entity.Player player) {
        long now = System.nanoTime();
        long cooldownNanos = Math.max(0L,
                plugin.getConfig().getLong("motorcycle.install-cooldown-seconds", 1L)) * 1_000_000_000L;
        Long lastInstall = installCooldowns.get(player.getUniqueId());
        if (lastInstall != null && now - lastInstall < cooldownNanos)
            return;
        if (getByPlayer(player) != null) {
            send(player, "messages.already-riding");
            return;
        }
        if (pendingInstallTasks.containsKey(player.getUniqueId()))
            return;
        if (plugin.zones().isNoRide(player.getLocation())) {
            send(player, "messages.no-ride-deploy");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isMotorcycleItem(held)) {
            send(player, "messages.not-motorcycle-item");
            return;
        }

        int battery = getBattery(held);
        installCooldowns.put(player.getUniqueId(), now);
        String itemId = held.getItemMeta().getPersistentDataContainer().get(plugin.bikeIdKey(),
                PersistentDataType.STRING);
        Location start = player.getLocation().clone();
        int duration = Math.max(1, plugin.getConfig().getInt("motorcycle.storage.duration-seconds", 30));
        send(player, "messages.install-start");
        applyOperationSlowness(player);

        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int left = duration;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelInstall(player, "messages.install-cancel");
                    return;
                }
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!isMotorcycleItem(current) || getBattery(current) != battery
                        || !java.util.Objects.equals(itemId, current.getItemMeta().getPersistentDataContainer()
                                .get(plugin.bikeIdKey(), PersistentDataType.STRING))) {
                    cancelInstall(player, "messages.install-cancel");
                    return;
                }
                if (!player.getWorld().equals(start.getWorld()) || plugin.zones().isNoRide(player.getLocation())
                        || player.getLocation().distanceSquared(start) > 0.16) {
                    cancelInstall(player, "messages.install-cancel");
                    return;
                }
                if (left <= 0) {
                    completeInstall(player, battery);
                    return;
                }
                applyOperationSlowness(player);
                sendActionBar(player, parseActionbar("motorcycle.actionbar.installing", null, left));
                left--;
            }
        }, 0L, 20L);
        pendingInstallTasks.put(player.getUniqueId(), task);
    }

    private void completeInstall(org.bukkit.entity.Player player, int battery) {
        cancelTask(pendingInstallTasks.remove(player.getUniqueId()));
        clearOperationSlowness(player);
        ItemStack current = player.getInventory().getItemInMainHand();
        if (!isMotorcycleItem(current))
            return;
        Motorcycle bike;
        try {
            ItemMeta meta = current.getItemMeta();
            bike = spawn(player.getLocation(), battery,
                    GravibikeDecalState.read(meta.getPersistentDataContainer(), plugin.gravibikeSkinIdKey()),
                    GravibikeDecalState.read(meta.getPersistentDataContainer(), plugin.gravibikeItemModelKey()),
                    GravibikeDecalState.read(meta.getPersistentDataContainer(),
                            plugin.gravibikeCustomModelDataKey()));
        } catch (SpawnRejectedException ex) {
            sendActionBar(player, Component.empty());
            player.sendMessage(plugin.miniMessage().deserialize(plugin.getConfig().getString(
                    "messages.install-spawn-denied",
                    "<red>Сервер запретил создание гравибайка в этой области. Предмет сохранён.")));
            plugin.getLogger().warning("Motorcycle installation rejected at " + player.getLocation()
                    + ": " + ex.getMessage() + ". Check region and mob-spawn restrictions.");
            return;
        } catch (IllegalArgumentException ex) {
            sendActionBar(player, Component.empty());
            send(player, "messages.install-obstructed");
            return;
        }
        if (current.getAmount() <= 1)
            player.getInventory().setItemInMainHand(null);
        else {
            current.setAmount(current.getAmount() - 1);
            player.getInventory().setItemInMainHand(current);
        }

        send(player, "messages.install-complete");
        mount(player, bike, true);
    }

    private void cancelInstall(org.bukkit.entity.Player player, String messagePath) {
        cancelTask(pendingInstallTasks.remove(player.getUniqueId()));
        clearOperationSlowness(player);
        sendActionBar(player, Component.empty());
        send(player, messagePath);
    }

    public void cancelStorageForPlayer(org.bukkit.entity.Player player, String messagePath) {
        Motorcycle bike = storageBikes.get(player.getUniqueId());
        if (bike != null)
            cancelStorage(player, bike, messagePath);
    }

    public boolean isStoragePending(org.bukkit.entity.Player player) {
        return storageBikes.containsKey(player.getUniqueId());
    }

    private void cancelStorage(org.bukkit.entity.Player player, Motorcycle bike, String path) {
        bike.setStorageInProgress(false);
        clearOperationSlowness(player);
        cancelTask(pendingStorageTasks.remove(player.getUniqueId()));
        storageBikes.remove(player.getUniqueId());
        sendActionBar(player, Component.empty());
        send(player, path);
    }

    private void applyOperationSlowness(org.bukkit.entity.Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false, false));
    }

    private void clearOperationSlowness(org.bukkit.entity.Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private void cancelTask(Integer task) {
        if (task != null)
            Bukkit.getScheduler().cancelTask(task);
    }

    private void persistHeading(Motorcycle bike) {
        if (!bike.seat().isValid())
            return;
        bike.seat().getPersistentDataContainer().set(plugin.headingKey(), PersistentDataType.FLOAT, bike.headingYaw());
        bike.setPersistedHeading(bike.headingYaw());
    }

    // ==================== Основной цикл ====================

    private void tick() {
        ticksElapsed++;
        for (Motorcycle bike : motorcycles.values().toArray(Motorcycle[]::new)) {
            if (!bike.seat().isValid() || !bike.visual().isValid()) {
                unregister(bike);
                continue;
            }

            for (Entity passenger : bike.seat().getPassengers()) {
                if (passenger instanceof org.bukkit.entity.Player player)
                    player.setFallDistance(0);
            }
            if (bike.passengerSeat() != null) {
                for (Entity passenger : bike.passengerSeat().getPassengers()) {
                    if (passenger instanceof org.bukkit.entity.Player player)
                        player.setFallDistance(0);
                }
            }

            org.bukkit.entity.Player rider = getRider(bike);
            if (rider != null && rider.isOnline() && !rider.isDead()) {
                playerToBike.put(rider.getUniqueId(), bike.id());
                drive(bike, rider);
            } else {
                driveWithoutRider(bike);
                chargeIfNeeded(bike);
                updateVisualLean(bike, false, false, false, 0);
            }
            sounds.update(bike, getRider(bike) != null && rider != null && rider.isOnline() && !rider.isDead(), ticksElapsed);
            boolean diagnosticsEnabled = plugin.getConfig().getBoolean("motorcycle.diagnostics.sync.enabled", false);
            Location displayTarget = diagnosticsEnabled ? visualTargetLocation(bike) : null;
            boolean displayTeleported = syncVisual(bike);
            Location passengerSeatTarget = diagnosticsEnabled ? passengerSeatTargetLocation(bike) : null;
            boolean passengerSeatTeleported = updatePassengerSeat(bike);
            if (diagnosticsEnabled) {
                Pig passengerSeat = bike.passengerSeat();
                plugin.getLogger().info(MotorcycleSyncDiagnostics.format(ticksElapsed, bike.id(),
                        bike.seat().getLocation(), bike.seat().getVelocity(), bike.seat().getYaw(),
                        bike.seat().getPassengers().stream().map(MotorcycleManager::passengerLabel).toList(),
                        rider == null ? null : rider.getLocation(), bike.visual().getLocation(), displayTarget,
                        displayTeleported,
                        passengerSeat == null || !passengerSeat.isValid() ? null : passengerSeat.getLocation(),
                        passengerSeatTarget,
                        passengerSeat == null || !passengerSeat.isValid() ? null : passengerSeatTeleported));
            }
        }
        if (ticksElapsed % 20 == 0)
            registry.pruneInvalid();
        sounds.retain(motorcycles.keySet());
    }

    private org.bukkit.entity.Player getRider(Motorcycle bike) {
        for (Entity passenger : bike.seat().getPassengers()) {
            if (passenger instanceof org.bukkit.entity.Player player)
                return player;
        }
        return null;
    }

    private void drive(Motorcycle bike, org.bukkit.entity.Player player) {
        if (bike.storageInProgress()) {
            bike.seat().setVelocity(new Vector());
            return;
        }
        if (player.isSneaking()) {
            dismount(player);
            return;
        }

        Input input = player.getCurrentInput();
        boolean forward = input != null && input.isForward();
        boolean backward = input != null && input.isBackward();
        boolean left = input != null && input.isLeft();
        boolean right = input != null && input.isRight();
        boolean jump = input != null && input.isJump();

        Location base = vehicleBaseLocation(bike);
        if (plugin.zones().isNoRide(base)) {
            bike.setSpeed(PhysicsEngine.moveTowardZero(bike.speed(), physics.brakingPerTick() * 1.6));
            bike.setJumping(false);
            bike.seat().setVelocity(new Vector(0, hoverCorrection(bike, base), 0));
            updateVisualLean(bike, false, false, false, 0);
            if (actionbarTick())
                sendActionBar(player,
                        plugin.miniMessage().deserialize(plugin.getConfig().getString("messages.no-ride", "")));
            return;
        }

        updateHeading(bike, left, right);
        handleJumpState(bike, jump);
        updateSpeed(bike, forward, backward);

        Vector horizontal = calculateHorizontalVelocity(bike);
        Vector velocity;
        if (bike.jumping()) {
            velocity = calculateJumpVelocity(bike, horizontal);
        } else {
            velocity = resolveGroundMovement(bike, horizontal);
        }

        // Двигаем только транспорт. Игрок остаётся настоящим пассажиром Pig,
        // поэтому Minecraft сам перемещает его вместе с транспортом.
        // Нельзя одновременно задавать velocity и Pig, и игроку — это вызывает
        // рассинхронизацию пассажира и рывки камеры.
        bike.seat().setVelocity(velocity);
        if (!bike.jumping() && Math.hypot(velocity.getX(), velocity.getZ()) > 0) {
            CollisionProbe.Surface surface = null;
            for (Location point : footprintPoints(base, footprintHalfExtent())) {
                CollisionProbe.Surface candidate = collision.surfaceAt(base.getWorld(), point.getX(), point.getZ(),
                        footprintReferenceY(base));
                if (candidate != null && (surface == null || candidate.height() > surface.height()))
                    surface = candidate;
            }
            particles.emit(base, velocity, false, ticksElapsed, surface);
        }
        player.setFallDistance(0);

        consumeBattery(bike, Math.hypot(velocity.getX(), velocity.getZ()));
        chargeIfNeeded(bike);
        updateVisualLean(bike, left, right, forward, backward ? -1 : (forward ? 1 : 0));
        if (actionbarTick())
            sendActionBar(player, parseActionbar("motorcycle.actionbar.driving", bike, 0));
    }

    private void driveWithoutRider(Motorcycle bike) {
        if (bike.storageInProgress()) {
            bike.seat().setVelocity(new Vector());
            return;
        }

        Location base = vehicleBaseLocation(bike);
        if (plugin.zones().isNoRide(base)) {
            bike.setSpeed(PhysicsEngine.moveTowardZero(bike.speed(), physics.brakingPerTick() * 1.6));
            bike.setJumping(false);
            bike.seat().setVelocity(new Vector(0, hoverCorrection(bike, base), 0));
            bike.setJumpHeld(false);
            return;
        }

        bike.setSpeed(PhysicsEngine.moveTowardZero(bike.speed(), physics.rollingPerTick()));
        Vector horizontal = calculateHorizontalVelocity(bike);
        Vector velocity = bike.jumping()
                ? calculateJumpVelocity(bike, horizontal)
                : resolveGroundMovement(bike, horizontal);
        bike.seat().setVelocity(velocity);
        bike.setJumpHeld(false);
    }

    private void dismount(org.bukkit.entity.Player player) {
        Motorcycle bike = getByPlayer(player);
        if (bike == null)
            return;
        removeRider(player);
    }

    // ==================== Управление ====================

    private void updateHeading(Motorcycle bike, boolean left, boolean right) {
        double maxSteerPerTick = physics.steeringDegreesPerTick(bike.speed());
        if (left == right)
            return;

        float previous = bike.headingYaw();
        if (left)
            bike.setHeadingYaw(previous - (float) maxSteerPerTick);
        if (right)
            bike.setHeadingYaw(previous + (float) maxSteerPerTick);
        bike.seat().setRotation(bike.headingYaw(), 0);
        // Persist only after a meaningful turn, so the chunk is not dirtied every tick.
        if (Float.isNaN(bike.persistedHeading()) || Math.abs(bike.headingYaw() - bike.persistedHeading()) > 5.0f) {
            persistHeading(bike);
        }
    }

    private void updateSpeed(Motorcycle bike, boolean forward, boolean backward) {
        physics.updateSpeed(bike, forward, backward);
    }

    private Vector calculateHorizontalVelocity(Motorcycle bike) {
        return physics.horizontalVelocity(bike);
    }

    // ==================== Рельеф / ступени ====================

    private Vector resolveGroundMovement(Motorcycle bike, Vector desiredHorizontal) {
        Location currentBase = vehicleBaseLocation(bike);
        double distance = Math.hypot(desiredHorizontal.getX(), desiredHorizontal.getZ());

        double currentGround = footprintGroundY(currentBase);
        boolean airborne = Double.isNaN(currentGround);
        if (airborne)
            currentGround = groundYAt(currentBase.getWorld(), currentBase.getX(), currentBase.getZ(),
                    footprintReferenceY(currentBase));

        if (distance <= 1.0e-6) {
            bike.setBlockedTicks(0);
            return new Vector(0, hoverCorrection(bike, currentGround), 0);
        }

        double maxStep = plugin.getConfig().getDouble("motorcycle.physics.step-up-max-height", 1.0);
        double nextGround = lookAheadGroundY(currentBase, desiredHorizontal, distance);
        if (Double.isNaN(nextGround))
            nextGround = currentGround;

        double rise = (!Double.isNaN(currentGround) && !Double.isNaN(nextGround))
                ? nextGround - currentGround
                : 0.0;

        if (rise > maxStep + 0.01) {
            // A wall the bike cannot climb. Bleed the speed off instead of zeroing
            // it: a hard zero plus a vertical-only velocity was a dead end — the
            // player held W and the same branch fired again on the next tick.
            // Now the bike keeps grinding against the wall and re-evaluates.
            block(bike);
            return new Vector(0, hoverCorrection(bike, currentGround), 0);
        }

        // Climbing must look ahead, descending must look down. Taking the max of
        // both for the flight target kept a ledge alive for as long as the probe
        // still reached it, so the bike hovered over the void instead of rolling
        // off. The support level stays the max either way — otherwise the ledge
        // we are leaving would be re-classified as an obstacle.
        double followGround = rise > 0.0 || Double.isNaN(currentGround) ? nextGround : currentGround;
        double supportY = maxGround(currentGround, nextGround);
        // Nothing solid within reach: a ravine deeper than ground-search-depth, or
        // a footprint point in an unloaded chunk. Without this guard NaN would
        // reach the velocity vector and the seat would stop responding entirely.
        // While the bike still rests on something (travelGround only went blind)
        // the height is held; once nothing at all is under the footprint it sinks
        // slowly, otherwise it would drift away from the terrain for ever.
        if (Double.isNaN(followGround) || Double.isNaN(supportY))
            return new Vector(desiredHorizontal.getX(),
                    airborne ? -noGroundFallPerTick() : 0.0, desiredHorizontal.getZ());
        double targetBaseY = hoverBaseY(followGround, bike.hoverPhase());
        double targetSeatY = toSeatLocationY(targetBaseY);
        double stepPerTick = plugin.getConfig().getDouble("motorcycle.physics.step-up-per-tick", 1.05);
        double maxRise = rise > 0.05
                ? Math.max(stepPerTick, rise + 0.20)
                : plugin.getConfig().getDouble("motorcycle.physics.hover-rise-per-tick", 0.22);
        double maxDrop = rise < -0.05
                ? Math.max(stepPerTick, -rise + 0.20)
                : plugin.getConfig().getDouble("motorcycle.physics.hover-drop-per-tick", 0.28);
        double dy = clamp(targetSeatY - bike.seat().getY(), -maxDrop, maxRise);

        Location candidateBase = currentBase.clone().add(desiredHorizontal);
        candidateBase.setY(targetBaseY);
        if (!vehicleVolumeClear(bike, candidateBase, supportY)) {
            Location liftOnly = currentBase.clone();
            liftOnly.setY(targetBaseY);
            // Symmetric fallback: as well as climbing in place, allow sinking in
            // place. Without the descending branch a bike wedged between a ledge
            // and its own hit box deadlocked — it needed to move to fall and to
            // fall in order to move.
            boolean verticalMove = Math.abs(rise) > 0.05 || dy < -0.05;
            if (verticalMove && vehicleVolumeClear(bike, liftOnly, supportY)) {
                bike.setBlockedTicks(0);
                return new Vector(0, dy, 0);
            }
            // The collision test can still disagree with the step test on partial
            // shapes (stairs, slabs) or on a footprint the probe did not sample.
            // Climbing out is the last resort after a few blocked ticks, so that
            // misclassification cannot wedge the bike into a permanent stop. Only
            // while the lift still moves the bike — otherwise this would park it
            // on the spot and the escape would repeat for ever.
            if (Math.abs(dy) > 1.0e-6 && bike.blockedTicks() >= stuckEscapeTicks()
                    && vehicleVolumeClear(bike, liftOnly, supportY)) {
                return new Vector(0, dy, 0);
            }
            // Scrape along the obstacle instead of dying: moving on one axis only
            // keeps the bike responsive and re-tests the step from a new spot.
            Location slideOnly = currentBase.clone();
            slideOnly.setY(targetBaseY);
            slideOnly.setX(currentBase.getX() + desiredHorizontal.getX());
            if (vehicleVolumeClear(bike, slideOnly, supportY)) {
                bike.setBlockedTicks(0);
                return new Vector(desiredHorizontal.getX(), dy, 0);
            }
            slideOnly.setX(currentBase.getX());
            slideOnly.setZ(currentBase.getZ() + desiredHorizontal.getZ());
            if (vehicleVolumeClear(bike, slideOnly, supportY)) {
                bike.setBlockedTicks(0);
                return new Vector(0, dy, desiredHorizontal.getZ());
            }
            block(bike);
            return new Vector(0, hoverCorrection(bike, currentGround), 0);
        }

        bike.setBlockedTicks(0);
        return new Vector(desiredHorizontal.getX(), dy, desiredHorizontal.getZ());
    }

    /** Speed bleed plus stuck bookkeeping for a movement the bike cannot take. */
    private void block(Motorcycle bike) {
        double retention = clamp(plugin.getConfig().getDouble("motorcycle.physics.blocked-speed-retention", 0.25), 0.0, 1.0);
        bike.setSpeed(bike.speed() * retention);
        bike.setBlockedTicks(Math.min(bike.blockedTicks() + 1, 1000));
    }

    private int stuckEscapeTicks() {
        return Math.max(1, plugin.getConfig().getInt("motorcycle.physics.stuck-escape-ticks", 6));
    }

    private double noGroundFallPerTick() {
        return clamp(plugin.getConfig().getDouble("motorcycle.physics.no-ground-fall-per-tick", 0.5), 0.0, 1.0);
    }

    /**
     * Ground level the vehicle is about to climb onto.
     *
     * <p>The footprint square already reaches back towards the bike by its own
     * half extent, so probing at {@code step-probe-distance} covers a continuous
     * strip from the nose of the hit box to well past it. Probing a single point
     * used to leave a blind gap where a step was invisible to both this probe
     * and {@code currentGround}, and the bike stalled against it.
     *
     * <p>The probe is therefore never shorter than the distance the bike covers
     * this tick. At the maximum speed it moves further in one tick than the
     * configured probe reaches, and everything in that dead strip was classified
     * as flat ground: the step test saw no rise, the volume test then found the
     * very same block as an obstacle and the bike stopped dead in front of a
     * step it was supposed to climb — the "stuck on blocks" symptom.
     */
    private double lookAheadGroundY(Location currentBase, Vector desiredHorizontal, double distance) {
        Vector dir = desiredHorizontal.clone().setY(0);
        if (dir.lengthSquared() <= 1.0e-12)
            return Double.NaN;
        dir.normalize();

        double configured = plugin.getConfig().getDouble("motorcycle.physics.step-probe-distance", 0.90);
        double probe = Math.max(Math.max(0.35, configured), distance);
        Location ahead = currentBase.clone().add(dir.clone().multiply(probe));
        return footprintGroundY(ahead);
    }

    private double maxGround(double a, double b) {
        if (Double.isNaN(a))
            return b;
        if (Double.isNaN(b))
            return a;
        return Math.max(a, b);
    }

    private Vector calculateJumpVelocity(Motorcycle bike, Vector horizontal) {
        // Airborne: whatever blocked the ground movement is over.
        bike.setBlockedTicks(0);
        double gravity = Math.max(0.001, plugin.getConfig().getDouble("motorcycle.physics.jump-gravity", 0.08));
        double vy = bike.jumpVelocity();
        Location current = bike.seat().getLocation().clone();
        Location next = current.clone().add(horizontal).add(0, vy, 0);

        Location nextBase = next.clone().subtract(0, seatOffsetY(), 0);
        double ground = footprintGroundY(nextBase);
        if (Double.isNaN(ground))
            ground = groundYAt(nextBase.getWorld(), nextBase.getX(), nextBase.getZ(),
                    footprintReferenceY(nextBase));
        if (!vehicleVolumeClear(bike, nextBase, ground)) {
            horizontal.zero();
            next = current.clone().add(0, vy, 0);
            Location stationaryBase = next.clone().subtract(0, seatOffsetY(), 0);
            double stationaryGround = groundYAt(stationaryBase.getWorld(), stationaryBase.getX(),
                    stationaryBase.getZ(), footprintReferenceY(stationaryBase));
            if (!vehicleVolumeClear(bike, stationaryBase, stationaryGround))
                vy = 0;
        }
        double landingSeatY = Double.isNaN(ground) ? Double.NaN
                : toSeatLocationY(hoverBaseY(ground, bike.hoverPhase()));

        if (vy <= 0 && !Double.isNaN(landingSeatY) && next.getY() <= landingSeatY) {
            bike.setJumping(false);
            bike.setJumpVelocity(0);
            return new Vector(horizontal.getX(), clamp(landingSeatY - current.getY(), -0.5, 0.5), horizontal.getZ());
        }

        bike.setJumpVelocity(vy - gravity);
        return new Vector(horizontal.getX(), vy, horizontal.getZ());
    }

    private void handleJumpState(Motorcycle bike, boolean pressed) {
        physics.handleJump(bike, pressed);
    }

    // ==================== Парение ====================

    private Location snapBaseToHover(Location base) {
        double ground = groundYAt(base.getWorld(), base.getX(), base.getZ(), base.getY());
        if (!Double.isNaN(ground))
            base.setY(hoverBaseY(ground, 0));
        return base;
    }

    private double hoverCorrection(Motorcycle bike, Location base) {
        double ground = footprintGroundY(base);
        if (Double.isNaN(ground))
            ground = groundYAt(base.getWorld(), base.getX(), base.getZ(), footprintReferenceY(base));
        return hoverCorrection(bike, ground);
    }

    private double hoverCorrection(Motorcycle bike, double ground) {
        if (Double.isNaN(ground))
            return 0;
        double targetSeatY = toSeatLocationY(hoverBaseY(ground, bike.hoverPhase()));
        return clamp(targetSeatY - bike.seat().getY(),
                -plugin.getConfig().getDouble("motorcycle.physics.hover-drop-per-tick", 0.28),
                plugin.getConfig().getDouble("motorcycle.physics.hover-rise-per-tick", 0.22));
    }

    private double hoverBaseY(double groundY, double phase) {
        double hoverHeight = plugin.getConfig().getDouble("motorcycle.physics.hover-height", 0.70);
        double amplitude = plugin.getConfig().getDouble("motorcycle.physics.hover-bob-amplitude", 0.02);
        double frequency = plugin.getConfig().getDouble("motorcycle.physics.hover-bob-frequency", 1.2);
        double bob = amplitude * Math.sin((ticksElapsed / 20.0) * Math.PI * 2.0 * frequency + phase);
        return groundY + hoverHeight + bob;
    }

    private Location vehicleBaseLocation(Motorcycle bike) {
        Location base = bike.seat().getLocation().clone();
        base.setY(base.getY() - seatOffsetY());
        base.setYaw(bike.headingYaw());
        base.setPitch(0);
        return base;
    }

    private Location toSeatLocation(Location base) {
        Location seat = base.clone();
        seat.setY(base.getY() + seatOffsetY());
        seat.setYaw(base.getYaw());
        seat.setPitch(0);
        return seat;
    }

    private double toSeatLocationY(double baseY) {
        return baseY + seatOffsetY();
    }

    private double seatOffsetY() {
        return plugin.getConfig().getDouble("motorcycle.visual.seat-offset-y", -0.55);
    }

    // ==================== Коллизии ====================

    private boolean vehicleVolumeClear(Motorcycle bike, Location baseLocation, double supportY) {
        return collision.volumeClear(baseLocation, supportY);
    }

    /**
     * Ground reference for a footprint probe: the top of the hit box. A step
     * that is still inside the vehicle's own box is a step it can climb, so it
     * has to be discoverable; anything above that is overhead and must not be.
     */
    private double footprintReferenceY(Location base) {
        return base.getY() + collision.vehicleHeight() / 2;
    }

    /**
     * Highest ground level under the vehicle's collision footprint.
     *
     * <p>The sampled area matches {@link CollisionProbe#vehicleBox} exactly: an
     * axis aligned square of the same half extent, probed at the centre and the
     * four corners. That match matters in both directions.
     *
     * <p>It is what lets a bike that is halfway over a ledge keep the ledge as
     * its support level. Sampling only the centre reported the low side instead,
     * {@code supportY} dropped below the ledge top, {@link CollisionProbe#volumeClear}
     * then classified the ledge block as an obstacle, and the bike locked in
     * place instead of rolling off — the exact "catches on something at the
     * edge" symptom.
     *
     * <p>Because the sampled columns are exactly the columns the hit box
     * overlaps, the probe can never mistake a wall the bike is standing next to
     * for ground and lift the vehicle onto it.
     */
    private double footprintGroundY(Location base) {
        World world = base.getWorld();
        if (world == null)
            return Double.NaN;

        double half = footprintHalfExtent();
        double referenceY = footprintReferenceY(base);
        double max = Double.NEGATIVE_INFINITY;
        for (Location point : footprintPoints(base, half)) {
            double y = groundYAt(world, point.getX(), point.getZ(), referenceY);
            if (!Double.isNaN(y))
                max = Math.max(max, y);
        }
        return max == Double.NEGATIVE_INFINITY ? Double.NaN : max;
    }

    /**
     * Half extent of the footprint square, one millimetre inside the hit box so
     * the sampled columns are always a subset of the columns
     * {@link CollisionProbe#volumeClear} tests.
     */
    private double footprintHalfExtent() {
        double width = Math.max(0.4, plugin.getConfig().getDouble("motorcycle.collision.width", 1.20));
        double extra = Math.max(0, plugin.getConfig().getDouble("motorcycle.collision.extra-radius", 0.10));
        return Math.max(0.05, width / 2.0 + extra - 0.001);
    }

    private List<Location> footprintPoints(Location base, double half) {
        List<Location> points = new ArrayList<>(5);
        points.add(base.clone());
        points.add(base.clone().add(half, 0, half));
        points.add(base.clone().add(-half, 0, half));
        points.add(base.clone().add(half, 0, -half));
        points.add(base.clone().add(-half, 0, -half));
        return points;
    }

    private double groundYAt(World world, double x, double z, double referenceY) {
        return collision.surfaceYAt(world, x, z, referenceY);
    }

    static float snapToCardinalYaw(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0f), 4) * 90.0f;
    }

    static float installationYaw() {
        return 0.0f;
    }

    static void alignHeadingToRider(Motorcycle bike, float riderYaw) {
        bike.setHeadingYaw(snapToCardinalYaw(riderYaw));
    }

    static float initialModelYaw(float heading, double modelYawOffset, boolean initialTransform) {
        return heading + (initialTransform ? 0.0f : (float) modelYawOffset);
    }

    // ==================== Визуал ====================

    private void updateVisualLean(Motorcycle bike, boolean left, boolean right, boolean forward, int accelDirection) {
        double speedKmh = Math.abs(blocksPerTickToKmh(bike.speed()));
        double maxSpeed = Math.max(1.0, plugin.getConfig().getDouble("motorcycle.physics.max-speed-kmh", 90.0));
        double bank = plugin.getConfig().getDouble("motorcycle.physics.max-bank-degrees", 28.0)
                * Math.min(1.0, speedKmh / maxSpeed);

        // ВАЖНО: знак исправлен под визуальную ось Minecraft ItemDisplay:
        // A = левый бок опускается, D = правый бок опускается.
        double targetRoll = left && !right ? -bank : (right && !left ? bank : 0.0);

        double targetPitch = 0.0;
        if (accelDirection > 0 && bike.speed() > 0) {
            targetPitch = -plugin.getConfig().getDouble("motorcycle.physics.acceleration-lean-degrees", 6.0)
                    * Math.min(1.0, speedKmh / maxSpeed);
        } else if (accelDirection < 0 && bike.speed() != 0) {
            targetPitch = plugin.getConfig().getDouble("motorcycle.physics.braking-lean-degrees", 8.0)
                    * Math.min(1.0, speedKmh / maxSpeed);
        }

        double smoothing = clamp(plugin.getConfig().getDouble("motorcycle.physics.visual-smoothing", 0.18), 0.01, 1.0);
        bike.setVisualRoll((float) lerp(bike.visualRoll(), targetRoll, smoothing));
        bike.setVisualPitch((float) lerp(bike.visualPitch(), targetPitch, smoothing));
    }

    private boolean syncVisual(Motorcycle bike) {
        ItemDisplay lightVisual = bike.lightVisual();
        if (bike.visual().getVehicle() != null) {
            bike.visual().leaveVehicle();
        }

        Location visualLoc = visualTargetLocation(bike);

        // Живые сущности (игрок на свинье) клиент интерполирует примерно 3 тика.
        // У Display duration=1 модель приезжает раньше — персонаж визуально отстаёт.
        int teleportTicks = Math.min(59,
                Math.max(1, plugin.getConfig().getInt("motorcycle.visual.teleport-duration-ticks", 3)));
        float scale = (float) plugin.getConfig().getDouble("motorcycle.visual.scale", 1.0);
        // Item models come from the client resource pack, so the plugin has no
        // way of knowing in which axis the pack author modelled the bike. These
        // three offsets are the calibration knobs for that: they are applied in
        // model space, after the heading rotation, so they stay fixed no matter
        // which way the vehicle is facing.
        double modelYaw = plugin.getConfig().getDouble("motorcycle.visual.model-yaw-offset-degrees", 0.0);
        double modelPitch = plugin.getConfig().getDouble("motorcycle.visual.model-pitch-offset-degrees", 0.0);
        double modelRoll = plugin.getConfig().getDouble("motorcycle.visual.model-roll-offset-degrees", 0.0);
        VisualState previous = visualStates.get(bike.id());
        VisualState next = new VisualState(visualLoc.clone(), bike.headingYaw(), bike.visualRoll(), bike.visualPitch(),
                scale, modelYaw, modelPitch, modelRoll, teleportTicks);
        boolean positionChanged = previous == null || !previous.samePosition(next);
        boolean transformChanged = previous == null || !previous.sameTransformation(next);
        boolean durationChanged = previous == null || previous.duration() != teleportTicks;
        if (!positionChanged && !transformChanged && !durationChanged
                && (lightVisual == null || !lightVisual.isValid()))
            return true;
        if (durationChanged)
            bike.visual().setTeleportDuration(teleportTicks);
        boolean teleported = !positionChanged || bike.visual().teleport(visualLoc);
        if (teleported) {
            visualStates.put(bike.id(), transformChanged || durationChanged ? next
                    : new VisualState(next.location(), previous.heading(), previous.roll(), previous.pitch(),
                            previous.scale(), previous.modelYaw(), previous.modelPitch(), previous.modelRoll(), next.duration()));
        } else {
            visualStates.remove(bike.id());
            if (ticksElapsed % 100 == 0) {
                plugin.getLogger().warning("Motorcycle " + bike.id() + " visual teleport failed: target="
                        + visualLoc + ", actual=" + bike.visual().getLocation() + ", valid=" + bike.visual().isValid()
                        + ", vehicle=" + bike.visual().getVehicle());
            }
        }
        if (lightVisual != null && lightVisual.isValid()) {
            lightVisual.setTeleportDuration(teleportTicks);
            lightVisual.teleport(visualLoc);
        }
        if (!transformChanged && !durationChanged)
            return teleported;

        float yawRad = (float) Math.toRadians(initialModelYaw(bike.headingYaw(), modelYaw, previous == null));
        float rollRad = (float) Math.toRadians(bike.visualRoll() + modelRoll);
        float pitchRad = (float) Math.toRadians(bike.visualPitch() + modelPitch);

        Quaternionf rotation = new Quaternionf()
                .rotateY(-yawRad)
                .rotateZ(rollRad)
                .rotateX(pitchRad);

        bike.visual().setRotation(0, 0);
        bike.visual().setTransformation(new Transformation(
                new Vector3f(), rotation,
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
        bike.visual().setInterpolationDelay(0);
        bike.visual().setInterpolationDuration(previous == null ? 0 : teleportTicks);
        if (lightVisual != null && lightVisual.isValid()) {
            lightVisual.setRotation(bike.visual().getYaw(), bike.visual().getPitch());
            lightVisual.setTransformation(bike.visual().getTransformation());
            lightVisual.setInterpolationDelay(0);
            lightVisual.setInterpolationDuration(previous == null ? 0 : teleportTicks);
        } else {
            return teleported;
        }
        return teleported;
    }

    private Location visualTargetLocation(Motorcycle bike) {
        Location location = vehicleBaseLocation(bike);
        double headingRad = Math.toRadians(bike.headingYaw());
        double forwardOffset = plugin.getConfig().getDouble("motorcycle.visual.forward-offset", 0.55);
        double heightOffset = plugin.getConfig().getDouble("motorcycle.visual.height-offset", 0.0);
        location.add(-Math.sin(headingRad) * forwardOffset, heightOffset,
                Math.cos(headingRad) * forwardOffset);
        location.setYaw(0);
        location.setPitch(0);
        return location;
    }

    private Location passengerSeatTargetLocation(Motorcycle bike) {
        Pig seat = bike.passengerSeat();
        if (seat == null || !seat.isValid())
            return null;
        Location location = bike.seat().getLocation();
        double yaw = Math.toRadians(bike.headingYaw());
        location.add(Math.sin(yaw) * 0.8, 0, -Math.cos(yaw) * 0.8);
        location.setYaw(bike.headingYaw());
        return location;
    }

    private static String passengerLabel(Entity passenger) {
        return passenger instanceof org.bukkit.entity.Player player
                ? "player:" + player.getName()
                : passenger.getType().name() + ":" + passenger.getUniqueId();
    }

    // ==================== Батарея ====================

    private void consumeBattery(Motorcycle bike, double horizontalSpeed) {
        batteries.consume(bike, horizontalSpeed);
    }

    private void chargeIfNeeded(Motorcycle bike) {
        batteries.charge(bike, plugin.zones().isCharge(vehicleBaseLocation(bike)));
    }

    private void setBattery(Motorcycle bike, int battery) {
        batteries.setBattery(bike, battery);
    }

    // ==================== Остальное ====================

    /*
     * Значения "*-kmh-per-second" в конфиге — это скорость ИЗМЕНЕНИЯ скорости
     * за одну секунду. kmhToBlocksPerTick() переводит км/ч в блоки/тик, но
     * сам по себе не учитывает, что "per-second" нужно ещё разделить на
     * количество тиков в секунде (20), чтобы получить прирост ЗА ОДИН ТИК.
     * Раньше этого деления не было, и разгон/торможение/качение происходили
     * в 20 раз быстрее, чем задано в конфиге (например, разгон до 90 км/ч
     * занимал ~0.2 сек вместо ~4 сек).
     */
    public int capacity() {
        return batteries.capacity();
    }

    private double kmhToBlocksPerTick(double kmh) {
        return kmh / 3.6 / 20.0;
    }

    private double blocksPerTickToKmh(double value) {
        return value * 20.0 * 3.6;
    }

    private double lerp(double current, double target, double amount) {
        return current + (target - current) * amount;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Component parseActionbar(String path, Motorcycle bike, int seconds) {
        int battery = bike == null ? 0 : bike.battery();
        int speed = bike == null ? 0 : (int) Math.round(Math.abs(blocksPerTickToKmh(bike.speed())));
        String raw = plugin.getConfig().getString(path, "");
        raw = replace(raw, battery, speed, seconds);
        return plugin.miniMessage().deserialize(raw);
    }

    private String replace(String text, int battery, int speed, int seconds) {
        return text == null ? ""
                : text.replace("{charge}", Integer.toString(battery))
                        .replace("{speed}", Integer.toString(speed))
                        .replace("{seconds}", Integer.toString(seconds));
    }

    private void sendActionBar(org.bukkit.entity.Player player, Component message) {
        player.sendActionBar(message);
    }

    private void send(org.bukkit.entity.Player player, String path) {
        if (!player.hasPermission("motorcycle.admin"))
            return;
        player.sendMessage(plugin.miniMessage().deserialize(plugin.message(path)));
    }

    public Collection<Motorcycle> getAll() {
        return Collections.unmodifiableCollection(motorcycles.values());
    }

    private Collection<Motorcycle> motorcyclesInArea(World world, double minX, double minZ, double maxX, double maxZ) {
        if (motorcycles.size() <= 16 || world == null || !Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ))
            return motorcycles.values();
        double minChunkX = Math.floor(minX / 16.0), maxChunkX = Math.floor(maxX / 16.0);
        double minChunkZ = Math.floor(minZ / 16.0), maxChunkZ = Math.floor(maxZ / 16.0);
        double count = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (minChunkX < Integer.MIN_VALUE || minChunkZ < Integer.MIN_VALUE
                || maxChunkX > Integer.MAX_VALUE || maxChunkZ > Integer.MAX_VALUE || count > 256)
            return motorcycles.values();
        List<Motorcycle> result = new ArrayList<>();
        for (long x = (long) minChunkX; x <= (long) maxChunkX; x++) {
            for (long z = (long) minChunkZ; z <= (long) maxChunkZ; z++) {
                if (!world.isChunkLoaded((int) x, (int) z))
                    continue;
                for (Entity entity : world.getChunkAt((int) x, (int) z).getEntities()) {
                    if (!(entity instanceof Pig))
                        continue;
                    Motorcycle bike = getByEntity(entity);
                    if (bike != null && bike.seat().getUniqueId().equals(entity.getUniqueId()))
                        result.add(bike);
                }
            }
        }
        return result;
    }

    public boolean intersectsPlacedBlock(Block block) {
        if (block == null)
            return false;
        double half = Math.max(0.4, plugin.getConfig().getDouble("motorcycle.collision.width", 1.20)) / 2
                + Math.max(0, plugin.getConfig().getDouble("motorcycle.collision.extra-radius", 0.10));
        for (Motorcycle bike : motorcyclesInArea(block.getWorld(), block.getX() - half, block.getZ() - half,
                block.getX() + 1.0 + half, block.getZ() + 1.0 + half)) {
            if (intersectsPlacedBlock(bike, block))
                return true;
        }
        return false;
    }

    public boolean intersectsPlacedBlock(Motorcycle bike, Block block) {
        if (bike == null || block == null || !bike.seat().getWorld().equals(block.getWorld()))
            return false;
        Location c = vehicleBaseLocation(bike);
        BoundingBox bikeBox = collision.vehicleBox(c);
        BoundingBox placed = new BoundingBox(block.getX(), block.getY(), block.getZ(),
                block.getX() + 1.0, block.getY() + 1.0, block.getZ() + 1.0);
        return bikeBox.overlaps(placed);
    }

    public void removeMotorcycle(Motorcycle bike) {
        if (bike == null)
            return;
        unregister(bike);
        registry.forget(bike.id());

        for (Entity passenger : new ArrayList<>(bike.seat().getPassengers())) {
            if (passenger instanceof org.bukkit.entity.Player player) {
                playerToBike.remove(player.getUniqueId());
                player.leaveVehicle();
            }
        }
        if (bike.seat().isValid())
            bike.seat().remove();
        if (bike.visual().isValid())
            bike.visual().remove();
        if (bike.lightVisual() != null && bike.lightVisual().isValid())
            bike.lightVisual().remove();
    }

    private boolean actionbarTick() {
        return ticksElapsed
                % Math.max(1, plugin.getConfig().getInt("motorcycle.actionbar.update-interval-ticks", 4)) == 0;
    }

    public void unloadEntities(Entity[] entities) {
        for (Entity entity : entities) {
            Motorcycle bike = getByEntity(entity);
            if (bike != null && entity == bike.passengerSeat()) {
                removePassengerSeat(bike);
            } else if (bike != null) {
                unregister(bike);
            }
            String raw = entity.getPersistentDataContainer().get(plugin.bikeIdKey(), PersistentDataType.STRING);
            if (raw != null) {
                try {
                    UUID id = UUID.fromString(raw);
                    boolean wasBikePart = bike != null || entity.getPersistentDataContainer()
                            .has(plugin.lightVisualKey(), PersistentDataType.BYTE);
                    registry.unload(id, entity);
                    if (wasBikePart) {
                        Motorcycle loaded = motorcycles.get(id);
                        if (loaded != null) unregister(loaded);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void removePassengerSeat(Motorcycle bike) {
        Pig seat = bike.passengerSeat();
        bike.setPassengerSeat(null);
        if (seat != null && seat.isValid()) {
            seat.eject();
            seat.remove();
        }
    }

    private void unregister(Motorcycle bike) {
        removePassengerSeat(bike);
        if (bike.seat().isValid())
            bike.seat().eject();
        // persist() skips unloaded seats, so this is safe during chunk unloads.
        batteries.persist(bike);
        persistHeading(bike);
        for (UUID playerId : new ArrayList<>(storageBikes.keySet())) {
            if (storageBikes.get(playerId) == bike) {
                cancelTask(pendingStorageTasks.remove(playerId));
                storageBikes.remove(playerId);
                var player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    sendActionBar(player, Component.empty());
                    send(player, "messages.storage-cancel");
                }
            }
        }
        bike.setStorageInProgress(false);
        motorcycles.remove(bike.id());
        // Keep a still-loaded half so either load order can reunite the pair.
        if (!bike.seat().isValid())
            loadedSeats.remove(bike.id());
        if (!bike.visual().isValid())
            loadedVisuals.remove(bike.id());
        if (bike.lightVisual() != null && !bike.lightVisual().isValid())
            loadedLightVisuals.remove(bike.id());
        playerToBike.values().removeIf(id -> id.equals(bike.id()));
        visualStates.remove(bike.id());
    }

    public void onPlayerQuit(org.bukkit.entity.Player player) {
        installCooldowns.remove(player.getUniqueId());
        cancelStorageForPlayer(player, "messages.storage-cancel");
        if (pendingInstallTasks.containsKey(player.getUniqueId()))
            cancelInstall(player, "messages.install-cancel");
        removeRider(player);
    }

    private static final class SpawnRejectedException extends IllegalArgumentException {
        private SpawnRejectedException(String message) {
            super(message);
        }
    }

    private record VisualState(Location location, float heading, float roll, float pitch, float scale, double modelYaw,
            double modelPitch, double modelRoll, int duration) {
        boolean samePosition(VisualState other) {
            return location.getWorld().equals(other.location.getWorld())
                    && location.distanceSquared(other.location) == 0.0;
        }

        boolean sameTransformation(VisualState other) {
            return Math.abs(heading - other.heading) < 0.05
                    && Math.abs(roll - other.roll) < 0.05 && Math.abs(pitch - other.pitch) < 0.05
                    && scale == other.scale && modelYaw == other.modelYaw
                    && modelPitch == other.modelPitch && modelRoll == other.modelRoll;
        }
    }
}
