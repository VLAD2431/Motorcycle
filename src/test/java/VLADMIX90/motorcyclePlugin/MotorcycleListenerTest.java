package VLADMIX90.motorcyclePlugin;

import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MotorcycleListenerTest {
    @Test
    void protectsBothOccupiedSeatsFromFallDamage() {
        for (boolean passenger : new boolean[] { false, true }) {
            MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
            MotorcycleManager manager = mock(MotorcycleManager.class);
            Motorcycle bike = mock(Motorcycle.class);
            Pig driverSeat = mock(Pig.class);
            Pig passengerSeat = mock(Pig.class);
            Player player = mock(Player.class);
            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(plugin.motorcycles()).thenReturn(manager);
            when(manager.getByPlayer(player)).thenReturn(bike);
            when(bike.seat()).thenReturn(driverSeat);
            when(bike.passengerSeat()).thenReturn(passengerSeat);
            when(player.getVehicle()).thenReturn(passenger ? passengerSeat : driverSeat);
            when(event.getEntity()).thenReturn(player);
            when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

            new MotorcycleListener(plugin).onDamage(event);

            verify(event).setCancelled(true);
            verify(player).setFallDistance(0);
        }
    }

    @Test
    void doesNotProtectOtherDamageOrPlayersNoLongerMounted() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        MotorcycleManager manager = mock(MotorcycleManager.class);
        Motorcycle bike = mock(Motorcycle.class);
        Player player = mock(Player.class);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(plugin.motorcycles()).thenReturn(manager);
        when(manager.getByPlayer(player)).thenReturn(bike);
        when(bike.seat()).thenReturn(mock(Pig.class));
        when(bike.passengerSeat()).thenReturn(mock(Pig.class));
        when(event.getEntity()).thenReturn(player);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

        new MotorcycleListener(plugin).onDamage(event);
        verify(event, never()).setCancelled(true);
        verify(player, never()).setFallDistance(anyFloat());

        Pig driverSeat = bike.seat();
        when(player.getVehicle()).thenReturn(driverSeat);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE);
        new MotorcycleListener(plugin).onDamage(event);
        verify(event, never()).setCancelled(true);
        verify(player, never()).setFallDistance(anyFloat());
    }
}
