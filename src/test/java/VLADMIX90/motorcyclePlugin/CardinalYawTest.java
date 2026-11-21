package VLADMIX90.motorcyclePlugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardinalYawTest {
    @Test
    void snapsPlacementYawToNearestCardinalDirection() {
        float[][] cases = {
                {0, 0}, {90, 90}, {180, 180}, {270, 270},
                {44.9f, 0}, {45, 90}, {135, 180}, {225, 270}, {315, 0},
                {-45.1f, 270}, {-45, 0}, {-135, 270}, {405, 90}, {720, 0}
        };
        for (float[] entry : cases) {
            assertEquals(entry[1], MotorcycleManager.snapToCardinalYaw(entry[0]), 0.0f,
                    "yaw=" + entry[0]);
        }
    }

    @Test
    void alignsBikeHeadingToSnappedRiderYaw() {
        Motorcycle bike = new Motorcycle(java.util.UUID.randomUUID(), null, null, 100, 0);

        MotorcycleManager.alignHeadingToRider(bike, 134.0f);

        assertEquals(90.0f, bike.headingYaw(), 0.0f);
    }

    @Test
    void installationHeadingAlwaysPointsSouth() {
        assertEquals(0.0f, MotorcycleManager.installationYaw(), 0.0f);
    }

    @Test
    void snappedHeadingMatchesMinecraftCardinalMovement() {
        PhysicsEngine physics = new PhysicsEngine(null);
        float[][] cases = {{0, 0, 1}, {90, -1, 0}, {180, 0, -1}, {270, 1, 0}};
        for (float[] entry : cases) {
            Motorcycle bike = new Motorcycle(java.util.UUID.randomUUID(), null, null, 100,
                    MotorcycleManager.snapToCardinalYaw(entry[0]));
            bike.setSpeed(1);
            var velocity = physics.horizontalVelocity(bike);
            assertEquals(entry[1], velocity.getX(), 1e-6);
            assertEquals(entry[2], velocity.getZ(), 1e-6);
        }
    }

    @Test
    void initialModelYawIsCardinalEvenWithNonCardinalCalibrationOffset() {
        assertEquals(90.0f, MotorcycleManager.initialModelYaw(90.0f, 37.5, true), 0.0f);
    }

    @Test
    void laterModelYawKeepsCalibrationOffset() {
        assertEquals(127.5f, MotorcycleManager.initialModelYaw(90.0f, 37.5, false), 0.0f);
    }
}
