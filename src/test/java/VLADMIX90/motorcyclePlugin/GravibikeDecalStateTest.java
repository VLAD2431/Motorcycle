package VLADMIX90.motorcyclePlugin;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GravibikeDecalStateTest {
    @Test
    void savesAndReadsSelectedSkinId() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        NamespacedKey key = new NamespacedKey("motorcycleplugin", "gravibike_skin_id");
        when(data.get(key, PersistentDataType.STRING)).thenReturn("neon_fox");

        GravibikeDecalState.write(data, key, "neon_fox");

        verify(data).set(key, PersistentDataType.STRING, "neon_fox");
        assertEquals("neon_fox", GravibikeDecalState.read(data, key));
    }

    @Test
    void clearsSelectedSkinIdWhenBaseModelIsChosen() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        NamespacedKey key = new NamespacedKey("motorcycleplugin", "gravibike_skin_id");

        GravibikeDecalState.write(data, key, null);

        verify(data).remove(key);
        when(data.get(key, PersistentDataType.STRING)).thenReturn(null);
        assertNull(GravibikeDecalState.read(data, key));
    }

    @Test
    void savesAndReadsAllModelFieldsForRestoration() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        NamespacedKey skinKey = new NamespacedKey("motorcycleplugin", "gravibike_skin_id");
        NamespacedKey modelKey = new NamespacedKey("motorcycleplugin", "gravibike_item_model");
        NamespacedKey customDataKey = new NamespacedKey("motorcycleplugin", "gravibike_custom_model_data");
        when(data.get(skinKey, PersistentDataType.STRING)).thenReturn("neon_fox");
        when(data.get(modelKey, PersistentDataType.STRING)).thenReturn("pack:neon_fox");
        when(data.get(customDataKey, PersistentDataType.STRING)).thenReturn("neon_fox");

        GravibikeDecalState.write(data, skinKey, modelKey, customDataKey,
                "neon_fox", "pack:neon_fox", "neon_fox");

        assertEquals("neon_fox", GravibikeDecalState.read(data, skinKey));
        assertEquals("pack:neon_fox", GravibikeDecalState.read(data, modelKey));
        assertEquals("neon_fox", GravibikeDecalState.read(data, customDataKey));
    }
}
