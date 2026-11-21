package VLADMIX90.motorcyclePlugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

/** Block geometry queries shared by deployment and movement. */
public final class CollisionProbe {
    private final MotorcyclePlugin plugin;

    public CollisionProbe(MotorcyclePlugin plugin) { this.plugin = plugin; }

    public BoundingBox vehicleBox(Location base) {
        double width = Math.max(0.4, plugin.getConfig().getDouble("motorcycle.collision.width", 1.20));
        double height = vehicleHeight();
        double half = width / 2 + Math.max(0, plugin.getConfig().getDouble("motorcycle.collision.extra-radius", 0.10));
        return new BoundingBox(base.getX() - half, base.getY() - height / 2 + 0.05, base.getZ() - half,
                base.getX() + half, base.getY() + height / 2 - 0.05, base.getZ() + half);
    }

    public double vehicleHeight() {
        return Math.max(0.6, plugin.getConfig().getDouble("motorcycle.collision.height", 1.20));
    }

    public boolean volumeClear(Location base, double supportY) {
        World world = base.getWorld();
        if (world == null) return false;
        BoundingBox box = vehicleBox(base);
        if (Double.isNaN(supportY)) supportY = Double.NEGATIVE_INFINITY;
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                // Do not synchronously load neighboring chunks during physics checks.
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isPassable()) continue;
                    for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                        if (y + shape.getMaxY() <= supportY + 0.01) continue;
                        if (box.overlaps(shape.clone().shift(x, y, z))) return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Highest walkable surface at or below {@code referenceY}.
     *
     * <p>This deliberately does <em>not</em> start from
     * {@code getHighestBlockYAt}. Scanning down from the top of the column
     * reported the underside of anything overhead — a bridge, a roof, tree
     * canopy — as the ground, so a bike riding underneath one aimed its hover
     * plane at the underside and jumped onto the structure. Searching downward
     * from the vehicle's own height cannot see anything that is overhead.
     *
     * <p>The search is also bounded: it never walks more than
     * {@code motorcycle.physics.ground-search-depth} blocks below the reference,
     * so flying over a ravine costs a fixed handful of block lookups instead of
     * scanning to the bottom of the world.
     *
     * @param referenceY height to search below, normally the vehicle's base plus
     *        half its hit box so a step that is still inside that box is found
     * @return the surface height, or NaN when nothing solid is close enough below
     */
    public double groundYAt(World world, double x, double z, double referenceY) {
        if (world == null) return Double.NaN;
        if (Double.isNaN(referenceY)) return Double.NaN;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        // Never look above the reference, and never above the column's own top.
        int start = Math.min((int) Math.floor(referenceY), world.getHighestBlockYAt(bx, bz));
        start = Math.min(maxHeight - 1, Math.max(minHeight, start));
        int depth = Math.max(1, plugin.getConfig().getInt("motorcycle.physics.ground-search-depth", 12));
        int end = Math.max(minHeight, start - depth);

        for (int y = start; y >= end; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (block.isEmpty() || block.isPassable()) continue;
            double surface = Double.NaN;
            for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                double localX = x - bx, localZ = z - bz;
                double top = y + shape.getMaxY();
                if (localX >= shape.getMinX() && localX <= shape.getMaxX()
                        && localZ >= shape.getMinZ() && localZ <= shape.getMaxZ()
                        && (Double.isNaN(surface) || top > surface)) surface = top;
            }
            if (!Double.isNaN(surface)) return surface;
        }
        return Double.NaN;
    }

    public record Surface(double height, org.bukkit.Material material, boolean water) {}

    /** Highest solid or water support at an exact point, using one column scan. */
    public double surfaceYAt(World world, double x, double z, double referenceY) {
        Surface surface = surfaceAt(world, x, z, referenceY);
        return surface == null ? Double.NaN : surface.height();
    }

    public Surface surfaceAt(World world, double x, double z, double referenceY) {
        if (world == null || Double.isNaN(referenceY)) return null;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return null;

        int minHeight = world.getMinHeight();
        int start = Math.min((int) Math.floor(referenceY), world.getHighestBlockYAt(bx, bz));
        start = Math.min(world.getMaxHeight() - 1, Math.max(minHeight, start));
        int depth = Math.max(1, plugin.getConfig().getInt("motorcycle.physics.ground-search-depth", 12));
        int end = Math.max(minHeight, start - depth);
        boolean waterEnabled = plugin.getConfig().getBoolean("motorcycle.water.enabled", true);
        double ground = Double.NaN, water = Double.NaN;
        org.bukkit.Material groundMaterial = null;
        double localX = x - bx, localZ = z - bz;
        for (int y = start; y >= end; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (waterEnabled && Double.isNaN(water) && isWater(block)) water = y + 1.0;
            if (Double.isNaN(ground) && !block.isEmpty() && !block.isPassable()) {
                for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                    double top = y + shape.getMaxY();
                    if (localX >= shape.getMinX() && localX <= shape.getMaxX()
                            && localZ >= shape.getMinZ() && localZ <= shape.getMaxZ()
                            && (Double.isNaN(ground) || top > ground)) {
                        ground = top;
                        groundMaterial = block.getType();
                    }
                }
            }
            if (!Double.isNaN(ground) && (!waterEnabled || !Double.isNaN(water))) break;
        }
        if (!Double.isNaN(water) && (Double.isNaN(ground) || water >= ground))
            return new Surface(water, org.bukkit.Material.WATER, true);
        return Double.isNaN(ground) ? null : new Surface(ground, groundMaterial, false);
    }

    /** Highest water surface at or below the reference height. Lava is never support. */
    public double waterSurfaceYAt(World world, double x, double z, double referenceY) {
        if (!plugin.getConfig().getBoolean("motorcycle.water.enabled", true) || world == null || Double.isNaN(referenceY))
            return Double.NaN;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;

        int start = Math.min((int) Math.floor(referenceY), world.getHighestBlockYAt(bx, bz));
        start = Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight(), start));
        int depth = Math.max(1, plugin.getConfig().getInt("motorcycle.physics.ground-search-depth", 12));
        int end = Math.max(world.getMinHeight(), start - depth);
        for (int y = start; y >= end; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (isWater(block)) return y + 1.0;
        }
        return Double.NaN;
    }

    private boolean isWater(Block block) {
        return block.getType() == org.bukkit.Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    /**
     * Support level for a deployment test. Falls back to just below the hover
     * plane so an unresolvable floor is not mistaken for an obstacle, while
     * walls and ceilings are still checked.
     */
    public double supportYOrDefault(Location base) {
        double support = surfaceYAt(base.getWorld(), base.getX(), base.getZ(), base.getY());
        return Double.isNaN(support) ? base.getY() - 0.55 : support;
    }
}
