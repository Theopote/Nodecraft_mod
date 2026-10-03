package com.nodecraft.nodesystem.world;

import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
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

    private boolean charge() {
        if (reads >= GenerationLimits.MAX_WORLD_BLOCK_READS_PER_NODE) {
            return false;
        }
        reads++;
        return true;
    }
}
