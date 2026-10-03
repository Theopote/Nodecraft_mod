package com.nodecraft.nodesystem.world;

import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Loaded-chunk-only world reads. Never force-loads or generates chunks.
 */
public final class WorldQueryAccess {

    public enum Status {
        OK,
        UNLOADED,
        BUDGET
    }

    public record BlockRead(@Nullable BlockState state, Status status) {
        public static BlockRead ok(BlockState state) {
            return new BlockRead(state, Status.OK);
        }

        public static BlockRead unloaded() {
            return new BlockRead(null, Status.UNLOADED);
        }

        public static BlockRead budget() {
            return new BlockRead(null, Status.BUDGET);
        }
    }

    public record FluidRead(@Nullable FluidState state, Status status) {
        public static FluidRead ok(FluidState state) {
            return new FluidRead(state, Status.OK);
        }

        public static FluidRead unloaded() {
            return new FluidRead(null, Status.UNLOADED);
        }

        public static FluidRead budget() {
            return new FluidRead(null, Status.BUDGET);
        }
    }

    public record LightRead(int combined, int sky, int block, boolean skyVisible, Status status) {
        public static LightRead unloaded() {
            return new LightRead(0, 0, 0, false, Status.UNLOADED);
        }

        public static LightRead budget() {
            return new LightRead(0, 0, 0, false, Status.BUDGET);
        }
    }

    public record BlockEntityRead(@Nullable BlockEntity entity, Status status) {
        public static BlockEntityRead ok(@Nullable BlockEntity entity) {
            return new BlockEntityRead(entity, Status.OK);
        }

        public static BlockEntityRead unloaded() {
            return new BlockEntityRead(null, Status.UNLOADED);
        }

        public static BlockEntityRead budget() {
            return new BlockEntityRead(null, Status.BUDGET);
        }
    }

    public record BiomeRead(@Nullable RegistryEntry<Biome> biome, Status status) {
        public static BiomeRead ok(RegistryEntry<Biome> biome) {
            return new BiomeRead(biome, Status.OK);
        }

        public static BiomeRead unloaded() {
            return new BiomeRead(null, Status.UNLOADED);
        }

        public static BiomeRead budget() {
            return new BiomeRead(null, Status.BUDGET);
        }
    }

    public record TopYRead(int topY, Status status) {
        public static TopYRead ok(int topY) {
            return new TopYRead(topY, Status.OK);
        }

        public static TopYRead unloaded() {
            return new TopYRead(0, Status.UNLOADED);
        }

        public static TopYRead budget() {
            return new TopYRead(0, Status.BUDGET);
        }
    }

    private final World world;
    private long reads;

    public WorldQueryAccess(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    public long readCount() {
        return reads;
    }

    public boolean isLoaded(BlockPos pos) {
        return pos != null && world.isChunkLoaded(pos);
    }

    public boolean isBoxFullyLoaded(Box box) {
        if (box == null) {
            return false;
        }
        int minCx = (int) Math.floor(box.minX) >> 4;
        int maxCx = (int) Math.floor(box.maxX) >> 4;
        int minCz = (int) Math.floor(box.minZ) >> 4;
        int maxCz = (int) Math.floor(box.maxZ) >> 4;
        int y = (int) Math.floor((box.minY + box.maxY) * 0.5d);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!isLoaded(new BlockPos(cx << 4, y, cz << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * True when every block cell along {@code start → end} is in a loaded chunk.
     */
    public boolean isSegmentLoaded(Vec3d start, Vec3d end) {
        if (start == null || end == null) {
            return false;
        }
        BlockPos startPos = BlockPos.ofFloored(start);
        BlockPos endPos = BlockPos.ofFloored(end);
        if (!isLoaded(startPos) || !isLoaded(endPos)) {
            return false;
        }
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(length));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos pos = BlockPos.ofFloored(
                    start.x + dx * t,
                    start.y + dy * t,
                    start.z + dz * t
            );
            if (!isLoaded(pos)) {
                return false;
            }
        }
        return true;
    }

    public BlockRead getBlockState(BlockPos pos) {
        if (!isLoaded(pos)) {
            return BlockRead.unloaded();
        }
        if (!charge()) {
            return BlockRead.budget();
        }
        return BlockRead.ok(world.getBlockState(pos));
    }

    public FluidRead getFluidState(BlockPos pos) {
        if (!isLoaded(pos)) {
            return FluidRead.unloaded();
        }
        if (!charge()) {
            return FluidRead.budget();
        }
        return FluidRead.ok(world.getFluidState(pos));
    }

    public LightRead getLight(BlockPos pos) {
        if (!isLoaded(pos)) {
            return LightRead.unloaded();
        }
        if (!charge()) {
            return LightRead.budget();
        }
        return new LightRead(
                world.getLightLevel(pos),
                world.getLightLevel(LightType.SKY, pos),
                world.getLightLevel(LightType.BLOCK, pos),
                world.isSkyVisible(pos),
                Status.OK
        );
    }

    public BlockEntityRead getBlockEntity(BlockPos pos) {
        if (!isLoaded(pos)) {
            return BlockEntityRead.unloaded();
        }
        if (!charge()) {
            return BlockEntityRead.budget();
        }
        return BlockEntityRead.ok(world.getBlockEntity(pos));
    }

    public BiomeRead getBiome(BlockPos pos) {
        if (!isLoaded(pos)) {
            return BiomeRead.unloaded();
        }
        if (!charge()) {
            return BiomeRead.budget();
        }
        return BiomeRead.ok(world.getBiome(pos));
    }

    public TopYRead getTopY(Heightmap.Type type, int x, int z) {
        BlockPos probe = new BlockPos(x, world.getBottomY(), z);
        if (!isLoaded(probe)) {
            return TopYRead.unloaded();
        }
        if (!charge()) {
            return TopYRead.budget();
        }
        return TopYRead.ok(world.getTopY(type, x, z));
    }

    private boolean charge() {
        if (reads >= GenerationLimits.MAX_WORLD_BLOCK_READS_PER_NODE) {
            return false;
        }
        reads++;
        return true;
    }
}
