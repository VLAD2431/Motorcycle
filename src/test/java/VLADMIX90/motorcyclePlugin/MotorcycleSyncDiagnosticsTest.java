package VLADMIX90.motorcyclePlugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MotorcycleSyncDiagnosticsTest {
    @Test
    void formatsSeatRiderDisplayPassengerAndTeleportResultsWithWorldQualifiedLocations() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("overworld");
        UUID bikeId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        String line = MotorcycleSyncDiagnostics.format(42, bikeId,
                new Location(world, 1.25, 64.0, -3.5, 90.0f, 0.0f), new Vector(0.5, -0.25, 1.0), 90.0f,
                List.of("rider:Alex", "entity:1234"),
                new Location(world, 1.25, 65.0, -3.5),
                new Location(world, 1.0, 64.5, -3.0),
                new Location(world, 1.0, 64.5, -3.0), true,
                new Location(world, 2.0, 64.0, -3.5),
                new Location(world, 2.0, 64.0, -3.5), false);

        assertEquals("sync tick=42 bike=123e4567-e89b-12d3-a456-426614174000 "
                + "seat=overworld@1.25,64.00,-3.50 yaw=90.00 velocity=0.50,-0.25,1.00 "
                + "passengers=[rider:Alex,entity:1234] rider=overworld@1.25,65.00,-3.50 "
                + "display=overworld@1.00,64.50,-3.00->overworld@1.00,64.50,-3.00 teleport=true "
                + "passengerSeat=overworld@2.00,64.00,-3.50->overworld@2.00,64.00,-3.50 teleport=false", line);
    }

    @Test
    void formatsMissingRiderAndPassengerSeatExplicitly() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("nether");
        Location location = new Location(world, 0, 50, 0);

        String line = MotorcycleSyncDiagnostics.format(7, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                location, new Vector(), 0, List.of(), null, location, location, true, null, null, null);

        assertTrue(line.contains("rider=absent"));
        assertTrue(line.contains("passengerSeat=absent"));
        assertTrue(line.contains("seat=nether@0.00,50.00,0.00"));
    }
}
