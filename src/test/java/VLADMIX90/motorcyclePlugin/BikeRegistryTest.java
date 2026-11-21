package VLADMIX90.motorcyclePlugin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BikeRegistryTest {
    @Test
    void forgetRemovesBikeAndItsLoadedEntities() {
        BikeRegistry registry = new BikeRegistry();
        UUID id = UUID.randomUUID();
        Pig seat = entity(Pig.class);
        ItemDisplay visual = entity(ItemDisplay.class);
        registry.bikes().put(id, motorcycle(id, seat, visual));
        registry.seats().put(id, seat);
        registry.visuals().put(id, visual);

        registry.forget(id);

        assertFalse(registry.bikes().containsKey(id));
        assertFalse(registry.seats().containsKey(id));
        assertFalse(registry.visuals().containsKey(id));
    }

    @Test
    void unloadSeatPreservesVisual() {
        BikeRegistry registry = registryWithEntities();
        UUID id = registry.bikes().keySet().iterator().next();
        Pig seat = registry.seats().get(id);
        UUID visualId = registry.visuals().get(id).getUniqueId();

        registry.unload(id, seat);

        assertFalse(registry.seats().containsKey(id));
        assertEquals(visualId, registry.visuals().get(id).getUniqueId());
    }

    @Test
    void unloadVisualPreservesSeat() {
        BikeRegistry registry = registryWithEntities();
        UUID id = registry.bikes().keySet().iterator().next();
        UUID seatId = registry.seats().get(id).getUniqueId();
        ItemDisplay visual = registry.visuals().get(id);

        registry.unload(id, visual);

        assertEquals(seatId, registry.seats().get(id).getUniqueId());
        assertFalse(registry.visuals().containsKey(id));
    }

    @Test
    void unloadIgnoresEntityFromAnotherBike() {
        BikeRegistry registry = registryWithEntities();
        UUID id = registry.bikes().keySet().iterator().next();
        Entity foreignEntity = entity(Entity.class);

        registry.unload(id, foreignEntity);

        assertTrue(registry.seats().containsKey(id));
        assertTrue(registry.visuals().containsKey(id));
    }

    @Test
    void pruneInvalidRemovesOnlyInvalidLoadedEntities() {
        BikeRegistry registry = new BikeRegistry();
        UUID validId = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();
        Pig validSeat = entity(Pig.class);
        Pig invalidSeat = entity(Pig.class);
        ItemDisplay validVisual = entity(ItemDisplay.class);
        ItemDisplay invalidVisual = entity(ItemDisplay.class);
        when(validSeat.isValid()).thenReturn(true);
        when(invalidSeat.isValid()).thenReturn(false);
        when(validVisual.isValid()).thenReturn(true);
        when(invalidVisual.isValid()).thenReturn(false);
        registry.seats().put(validId, validSeat);
        registry.seats().put(invalidId, invalidSeat);
        registry.visuals().put(validId, validVisual);
        registry.visuals().put(invalidId, invalidVisual);

        registry.pruneInvalid();

        assertTrue(registry.seats().containsKey(validId));
        assertFalse(registry.seats().containsKey(invalidId));
        assertTrue(registry.visuals().containsKey(validId));
        assertFalse(registry.visuals().containsKey(invalidId));
    }

    private static BikeRegistry registryWithEntities() {
        BikeRegistry registry = new BikeRegistry();
        UUID id = UUID.randomUUID();
        Pig seat = entity(Pig.class);
        ItemDisplay visual = entity(ItemDisplay.class);
        registry.bikes().put(id, motorcycle(id, seat, visual));
        registry.seats().put(id, seat);
        registry.visuals().put(id, visual);
        return registry;
    }

    private static Motorcycle motorcycle(UUID id, Pig seat, ItemDisplay visual) {
        return new Motorcycle(id, seat, visual, 100, 0.0f);
    }

    private static <T extends Entity> T entity(Class<T> type) {
        T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        return entity;
    }
}
