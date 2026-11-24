package VLADMIX90.motorcyclePlugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightCustomModelDataTest {
    @Test
    void buildsStringVariantForLightModel() {
        var config = new YamlConfiguration();
        config.set("motorcycle.visual.light-custom-model-data.strings", java.util.List.of("emissive_bike_light"));
        var section = config.getConfigurationSection("motorcycle.visual.light-custom-model-data");
        assertEquals(java.util.List.of("emissive_bike_light"),
                section.getStringList("strings").stream().filter(value -> !value.isBlank()).toList());
    }

    @Test
    void clampsLightBrightnessToMinecraftRange() {
        assertEquals(0, MotorcycleManager.clampLightBrightness(-1));
        assertEquals(0, MotorcycleManager.clampLightBrightness(0));
        assertEquals(8, MotorcycleManager.clampLightBrightness(8));
        assertEquals(15, MotorcycleManager.clampLightBrightness(15));
        assertEquals(15, MotorcycleManager.clampLightBrightness(16));
    }
}
