package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.bake.BakeTaskState;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enqueue + await helper so world.write block nodes share BakePlacementService with Apply Changes.
 */
final class WorldWriteBakeBridge {

    record Outcome(
        boolean complete,
        int successCount,
        int failureCount,
        int nbtSuccessCount,
        int totalCount,
        String error
    ) {
        static Outcome empty() {
            return new Outcome(true, 0, 0, 0, 0, "");
        }

        static Outcome rejected(int totalCount, int failureCount, String error) {
            return new Outcome(false, 0, failureCount, 0, totalCount, error == null ? "" : error);
        }
    }

    private WorldWriteBakeBridge() {
    }

    static BakeTask.Placement placement(BlockPos pos, BlockState state) {
        return new BakeTask.Placement(pos, state);
    }

    static BakeTask.Placement placement(BlockPos pos, BlockState state, @Nullable NbtCompound nbt, boolean mergeNbt) {
        return new BakeTask.Placement(pos, state, nbt, mergeNbt);
    }

    static Outcome enqueueAndAwait(
        ExecutionContext context,
        List<BakeTask.Placement> placements,
        boolean recordUndo,
        int skippedUnloaded
    ) {
        int total = placements.size() + skippedUnloaded;
        if (placements.isEmpty()) {
            if (skippedUnloaded > 0) {
                return Outcome.rejected(total, skippedUnloaded, "Partial write: " + skippedUnloaded + " failure(s)");
            }
            return Outcome.empty();
        }
        if (placements.size() > GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS) {
            return Outcome.rejected(total, total,
                "Placement count " + placements.size() + " exceeds Max Blocks "
                    + GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS + ".");
        }

        BakePlacementService service = BakePlacementService.getInstance();
        UUID taskId = service.enqueuePlacements(
            context.getWorld(),
            new ArrayList<>(placements),
            PlacementMode.OVERWRITE,
            recordUndo,
            BakePlacementService.DEFAULT_BLOCKS_PER_TICK,
            BakePlacementService.DEFAULT_TICK_BUDGET_NANOS,
            BakePlacementService.resolveActorId(context.getPlayer()),
            null
        );
        if (taskId == null) {
            return Outcome.rejected(total, placements.size() + skippedUnloaded, "World rejected bake enqueue");
        }

        long deadline = System.currentTimeMillis()
            + (long) GenerationLimits.MAX_EXECUTION_TIMEOUT_SECONDS * 1000L;
        BakePlacementService.AwaitResult await = context.callOnWorldThread(() ->
            service.awaitTaskTerminalState(taskId, deadline)
        );
        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
        int placed = snapshot != null ? snapshot.placedCount() : 0;
        int bakeFailures = snapshot != null
            ? Math.max(0, snapshot.totalCount() - snapshot.placedCount() - snapshot.skippedCount())
            : placements.size();
        int failureCount = skippedUnloaded + bakeFailures;
        boolean completed = await != null && await.completed() && failureCount == 0;
        String error = "";
        if (await != null && await.deadlineExceeded()) {
            error = "Bake timed out waiting for a terminal state";
        } else if (snapshot != null && snapshot.state() != BakeTaskState.COMPLETED) {
            error = "Bake " + snapshot.resolveState().toLowerCase();
        } else if (failureCount > 0) {
            error = "Partial write: " + failureCount + " failure(s)";
        }
        return new Outcome(completed, placed, failureCount, 0, total, error);
    }
}
