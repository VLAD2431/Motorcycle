package VLADMIX90.motorcyclePlugin;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class CollisionProbeTest {
    private static final double X = 2.25, Z = 3.25, REFERENCE_Y = 10.0;
    private World world;
    private FileConfiguration config;
    private CollisionProbe probe;

    @BeforeEach
    void setUp() {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getInt("motorcycle.physics.ground-search-depth", 12)).thenReturn(4);
        when(config.getBoolean("motorcycle.water.enabled", true)).thenReturn(true);
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.getHighestBlockYAt(2, 3)).thenReturn(10);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isEmpty()).thenReturn(true);
        when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        probe = new CollisionProbe(plugin);
    }

    @Test
    void solidGroundMatchesOldQueries() {
        blockAt(8, solid(1.0));
        assertMatchesOldQueries(9.0);
    }

    @Test
    void waterAboveGroundMatchesOldQueries() {
        blockAt(9, water());
        blockAt(7, solid(1.0));
        assertMatchesOldQueries(10.0);
    }

    @Test
    void waterloggedPartialBlockUsesWaterSurface() {
        Block slab = solid(0.5);
        Waterlogged data = mock(Waterlogged.class);
        when(data.isWaterlogged()).thenReturn(true);
        when(slab.getBlockData()).thenReturn(data);
        blockAt(8, slab);
        assertMatchesOldQueries(9.0);
    }

    @Test
    void emptyColumnMatchesOldQueries() {
        assertMatchesOldQueries(Double.NaN);
    }

    @Test
    void disabledWaterUsesOnlyGround() {
        when(config.getBoolean("motorcycle.water.enabled", true)).thenReturn(false);
        blockAt(9, water());
        blockAt(7, solid(0.5));
        assertMatchesOldQueries(7.5);
    }

    @Test
    void unloadedChunkMatchesOldQueriesWithoutReadingBlocks() {
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        assertMatchesOldQueries(Double.NaN);
        assertEquals(0, blockReads());
    }

    @Test
    void surfaceIncludesSandMaterialAndPartialHeight() {
        Block sand = solid(0.5);
        when(sand.getType()).thenReturn(Material.RED_SAND);
        blockAt(8, sand);
        assertEquals(new CollisionProbe.Surface(8.5, Material.RED_SAND, false),
                probe.surfaceAt(world, X, Z, REFERENCE_Y));
        when(sand.getType()).thenReturn(Material.SAND);
        assertEquals(new CollisionProbe.Surface(8.5, Material.SAND, false),
                probe.surfaceAt(world, X, Z, REFERENCE_Y));
    }

    @Test
    void surfaceIdentifiesWaterloggedAndMissingSupport() {
        assertEquals(null, probe.surfaceAt(world, X, Z, REFERENCE_Y));
        Block slab = solid(0.5);
        Waterlogged data = mock(Waterlogged.class);
        when(data.isWaterlogged()).thenReturn(true);
        when(slab.getBlockData()).thenReturn(data);
        blockAt(8, slab);
        assertEquals(new CollisionProbe.Surface(9.0, Material.WATER, true),
                probe.surfaceAt(world, X, Z, REFERENCE_Y));
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        clearInvocations(world);
        assertEquals(null, probe.surfaceAt(world, X, Z, REFERENCE_Y));
        assertEquals(0, blockReads());
    }

    @Test
    void combinedScanReadsFewerBlocksWhenWaterAndGroundExist() {
        blockAt(9, water());
        blockAt(7, solid(1.0));
        clearInvocations(world);
        double oldResult = oldSurface();
        long oldReads = blockReads();
        clearInvocations(world);
        double result = probe.surfaceYAt(world, X, Z, REFERENCE_Y);
        long combinedReads = blockReads();
        assertEquals(oldResult, result);
        assertTrue(combinedReads < oldReads,
                () -> "combined=" + combinedReads + ", old=" + oldReads);
    }

    private void assertMatchesOldQueries(double expected) {
        assertEquals(expected, oldSurface());
        assertEquals(expected, probe.surfaceYAt(world, X, Z, REFERENCE_Y));
    }

    private double oldSurface() {
        double ground = probe.groundYAt(world, X, Z, REFERENCE_Y);
        double water = probe.waterSurfaceYAt(world, X, Z, REFERENCE_Y);
        return Double.isNaN(ground) ? water : Double.isNaN(water) ? ground : Math.max(ground, water);
    }

    private long blockReads() {
        return mockingDetails(world).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("getBlockAt"))
                .count();
    }

    private void blockAt(int y, Block block) {
        when(world.getBlockAt(2, y, 3)).thenReturn(block);
    }

    private Block water() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.WATER);
        when(block.isPassable()).thenReturn(true);
        return block;
    }

    private Block solid(double height) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, height, 1)));
        when(block.getCollisionShape()).thenReturn(shape);
        when(block.getType()).thenReturn(Material.STONE);
        return block;
    }
}
