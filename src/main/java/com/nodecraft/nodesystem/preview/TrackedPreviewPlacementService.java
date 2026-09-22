package com.nodecraft.nodesystem.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Tracks temporary preview blocks placed directly into the world.
 * Clearing a tracked preview restores the previous world state.
 * <p>
 * Compatibility / special preview backend — not the default path.
 * Prefer {@link PreviewBackend#GHOST} via {@link PreviewManager}; durable edits
 * belong in {@code BakePlacementService}.
 * See {@code docs/architecture/preview-world-boundary.md}.
 */
public final class TrackedPreviewPlacementService {

    private static final TrackedPreviewPlacementService INSTANCE = new TrackedPreviewPlacementService();

    /**
     * Maximum number of blocks that can be tracked in a single preview.
     * This prevents performance issues when previewing very large models.
     * Use GHOST preview for models exceeding this limit.
     */
    public static final int MAX_TRACKED_PREVIEW_BLOCKS = 20_000;

    private final Map<World, Map<String, TrackedPreviewState>> trackedPreviews = new IdentityHashMap<>();

    private TrackedPreviewPlacementService() {
    }

    public static TrackedPreviewPlacementService getInstance() {
        return INSTANCE;
    }

    public int updateTrackedPreview(World world,
                                    String nodeId,
                                    List<BlockPos> positions,
                                    BlockState previewState,
                                    PlacementMode placementMode) {
        return updateTrackedPreviewOnWorldThread(world, nodeId, positions, previewState, placementMode, null);
    }

    /**
     * Applies preview block mutations on the server thread.
     * Thread safety is enforced by this service; {@code context} is optional metadata for callers.
     */
    public int updateTrackedPreviewOnWorldThread(World world,
                                                   String nodeId,
                                                   List<BlockPos> positions,
                                                   BlockState previewState,
                                                   PlacementMode placementMode,
                                                   @Nullable ExecutionContext context) {
        if (world == null || nodeId == null || nodeId.isEmpty() || positions == null || positions.isEmpty() || previewState == null) {
            return clearTrackedPreviewOnWorldThread(world, nodeId, context);
        }

        Set<BlockPos> requestedPositions = buildRequestedPositions(positions);
        if (requestedPositions.isEmpty()) {
            return clearTrackedPreviewOnWorldThread(world, nodeId, context);
        }

        enforceTrackedPreviewLimit(requestedPositions, nodeId);

        try {
            return runOnWorldThread(world, () -> applyTrackedPreviewUpdate(
                world,
                nodeId,
                requestedPositions,
                previewState,
                placementMode
            ));
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to update tracked preview on world thread. nodeId={}", nodeId, e);
            return 0;
        }
    }

    public int clearTrackedPreview(World world, String nodeId) {
        return clearTrackedPreviewOnWorldThread(world, nodeId, null);
    }

    /**
     * Restores tracked preview blocks on the server thread.
     * Thread safety is enforced by this service; {@code context} is optional metadata for callers.
     */
    public int clearTrackedPreviewOnWorldThread(World world, String nodeId, @Nullable ExecutionContext context) {
        try {
            return clearTrackedPreviewInternal(world, nodeId);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to clear tracked preview on world thread. nodeId={}", nodeId, e);
            return 0;
        }
    }

    private int clearTrackedPreviewInternal(World world, String nodeId) {
        TrackedPreviewState detached = detachTrackedPreviewState(world, nodeId);
        if (detached == null) {
            NodeCraft.LOGGER.debug("TrackedPreviewPlacementService.clearTrackedPreview nodeId={} had no tracked state", nodeId);
            return 0;
        }

        int restoredCount = restoreDetachedPreviewBlocks(world, detached);
        NodeCraft.LOGGER.debug(
                "TrackedPreviewPlacementService.clearTrackedPreview nodeId={} restored={}",
                nodeId, restoredCount
        );
        return restoredCount;
    }

    public int getTrackedCount(World world, String nodeId) {
        if (world == null || nodeId == null || nodeId.isEmpty()) {
            return 0;
        }

        synchronized (this) {
            Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
            if (byNode == null) {
                return 0;
            }

            TrackedPreviewState trackedState = byNode.get(nodeId);
            return trackedState == null ? 0 : trackedState.previousStates().size();
        }
    }

    public List<String> getTrackedPreviewIds(World world) {
        synchronized (this) {
            Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
            if (byNode == null || byNode.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(byNode.keySet());
        }
    }

    public int clearAllTrackedPreviews(World world) {
        return clearAllTrackedPreviews(world, null);
    }

    public int clearAllTrackedPreviews(World world, @Nullable ExecutionContext context) {
        if (world == null) {
            return 0;
        }

        List<String> previewIds = getTrackedPreviewIds(world);
        int restoredCount = 0;
        for (String previewId : previewIds) {
            restoredCount += clearTrackedPreviewInternal(world, previewId);
        }
        NodeCraft.LOGGER.info(
                "TrackedPreviewPlacementService.clearAllTrackedPreviews clearedPreviews={} restoredBlocks={}",
                previewIds.size(), restoredCount
        );
        return restoredCount;
    }

    public boolean hasAnyTrackedPreviews(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return false;
        }

        synchronized (this) {
            for (Map<String, TrackedPreviewState> byNode : trackedPreviews.values()) {
                if (byNode.containsKey(nodeId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int clearTrackedPreviewAcrossWorlds(String nodeId) {
        return clearTrackedPreviewAcrossWorlds(nodeId, null);
    }

    /**
     * Clears tracked preview across all worlds on the server thread.
     * Thread safety is enforced by this service; {@code context} is optional metadata for callers.
     */
    public int clearTrackedPreviewAcrossWorlds(String nodeId, @Nullable ExecutionContext context) {
        if (nodeId == null || nodeId.isEmpty()) {
            return 0;
        }

        List<World> worlds;
        synchronized (this) {
            worlds = new ArrayList<>(trackedPreviews.keySet());
        }

        int restoredCount = 0;
        for (World world : worlds) {
            restoredCount += clearTrackedPreviewInternal(world, nodeId);
        }
        return restoredCount;
    }

    private int applyTrackedPreviewUpdate(World world,
                                           String nodeId,
                                           Set<BlockPos> requestedPositions,
                                           BlockState previewState,
                                           PlacementMode placementMode) {
        assertOnServerThread(world);

        TrackedPreviewState previousTrackedState = readTrackedPreviewState(world, nodeId);
        Map<BlockPos, BlockState> trackedOriginalStates = previousTrackedState == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previousTrackedState.previousStates());
        BlockState previousPreviewState = previousTrackedState == null ? null : previousTrackedState.previewState();

        int placedCount = 0;
        int skippedCount = 0;
        int restoredCount = 0;
        int unchangedCount = 0;

        List<BlockPos> removedPositions = new ArrayList<>();
        for (BlockPos trackedPos : trackedOriginalStates.keySet()) {
            if (!requestedPositions.contains(trackedPos)) {
                removedPositions.add(trackedPos);
            }
        }

        for (BlockPos removedPos : removedPositions) {
            BlockState originalState = trackedOriginalStates.remove(removedPos);
            if (originalState != null
                    && restoreIfStillPreview(world, removedPos, originalState, previousPreviewState)) {
                restoredCount++;
            }
        }

        boolean previewStateChanged = previousPreviewState != null && !previousPreviewState.equals(previewState);

        for (BlockPos pos : requestedPositions) {
            boolean alreadyTracked = trackedOriginalStates.containsKey(pos);
            if (!alreadyTracked && placementMode == PlacementMode.INCREMENTAL && !world.isAir(pos)) {
                skippedCount++;
                continue;
            }

            if (!alreadyTracked) {
                trackedOriginalStates.put(pos, world.getBlockState(pos));
            }

            if (alreadyTracked && !previewStateChanged) {
                unchangedCount++;
                continue;
            }

            if (world.setBlockState(pos, previewState, Block.NOTIFY_ALL)) {
                placedCount++;
            } else if (alreadyTracked) {
                unchangedCount++;
            }
        }

        TrackedPreviewState nextState = trackedOriginalStates.isEmpty()
                ? null
                : new TrackedPreviewState(trackedOriginalStates, previewState);
        commitTrackedPreviewState(world, nodeId, nextState);

        NodeCraft.LOGGER.debug(
                "TrackedPreviewPlacementService.updateTrackedPreview nodeId={} requested={} placed={} skipped={} restored={} unchanged={} tracked={}",
                nodeId, requestedPositions.size(), placedCount, skippedCount, restoredCount, unchangedCount, trackedOriginalStates.size()
        );

        return trackedOriginalStates.size();
    }

    @Nullable
    private TrackedPreviewState detachTrackedPreviewState(World world, String nodeId) {
        if (world == null || nodeId == null || nodeId.isEmpty()) {
            return null;
        }

        synchronized (this) {
            Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
            if (byNode == null) {
                return null;
            }

            TrackedPreviewState trackedState = byNode.remove(nodeId);
            if (trackedState == null) {
                removeWorldIfEmpty(world);
                return null;
            }

            removeWorldIfEmpty(world);
            return new TrackedPreviewState(
                    new LinkedHashMap<>(trackedState.previousStates()),
                    trackedState.previewState()
            );
        }
    }

    /**
     * Restores original blocks only where the world still holds the preview state.
     * If an external player/command/mod changed the cell after preview placement,
     * leave that external change alone (do not overwrite with the pre-preview original).
     */
    private int restoreDetachedPreviewBlocks(World world, TrackedPreviewState detached) {
        Map<BlockPos, BlockState> statesToRestore = detached.previousStates();
        if (statesToRestore.isEmpty()) {
            return 0;
        }

        BlockState expectedPreview = detached.previewState();
        return runOnWorldThread(world, () -> {
            assertOnServerThread(world);
            int count = 0;
            for (Map.Entry<BlockPos, BlockState> entry : statesToRestore.entrySet()) {
                if (restoreIfStillPreview(world, entry.getKey(), entry.getValue(), expectedPreview)) {
                    count++;
                }
            }
            return count;
        });
    }

    /**
     * @return true if the original state was written back
     */
    private static boolean restoreIfStillPreview(
            World world,
            BlockPos pos,
            BlockState originalState,
            @Nullable BlockState expectedPreviewState
    ) {
        BlockState current = world.getBlockState(pos);
        if (expectedPreviewState != null && !current.equals(expectedPreviewState)) {
            NodeCraft.LOGGER.debug(
                    "Tracked preview restore skipped at {} — external change detected (current != previewState)",
                    pos
            );
            return false;
        }
        return world.setBlockState(pos, originalState, Block.NOTIFY_ALL);
    }

    @Nullable
    private TrackedPreviewState readTrackedPreviewState(World world, String nodeId) {
        synchronized (this) {
            Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
            if (byNode == null) {
                return null;
            }
            TrackedPreviewState state = byNode.get(nodeId);
            if (state == null) {
                return null;
            }
            return new TrackedPreviewState(new LinkedHashMap<>(state.previousStates()), state.previewState());
        }
    }

    private void commitTrackedPreviewState(World world, String nodeId, @Nullable TrackedPreviewState nextState) {
        synchronized (this) {
            if (nextState == null) {
                Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
                if (byNode != null) {
                    byNode.remove(nodeId);
                    removeWorldIfEmpty(world);
                }
                return;
            }

            Map<String, TrackedPreviewState> byNode = trackedPreviews.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
            byNode.put(nodeId, nextState);
        }
    }

    private static Set<BlockPos> buildRequestedPositions(List<BlockPos> positions) {
        Set<BlockPos> requestedPositions = new LinkedHashSet<>();
        for (BlockPos originalPos : positions) {
            if (originalPos != null) {
                requestedPositions.add(originalPos.toImmutable());
            }
        }
        return requestedPositions;
    }

    private static void enforceTrackedPreviewLimit(Set<BlockPos> requestedPositions, String nodeId) {
        if (requestedPositions.size() <= MAX_TRACKED_PREVIEW_BLOCKS) {
            return;
        }

        NodeCraft.LOGGER.warn(
            "TrackedPreviewPlacementService: Requested {} blocks for preview, but limit is {}. " +
            "Consider using GHOST preview for large models. Preview will be limited to {} blocks. nodeId={}",
            requestedPositions.size(), MAX_TRACKED_PREVIEW_BLOCKS, MAX_TRACKED_PREVIEW_BLOCKS, nodeId
        );

        List<BlockPos> positionsList = new ArrayList<>(requestedPositions);
        requestedPositions.clear();

        int step = Math.max(1, positionsList.size() / MAX_TRACKED_PREVIEW_BLOCKS);
        for (int i = 0; i < positionsList.size() && requestedPositions.size() < MAX_TRACKED_PREVIEW_BLOCKS; i += step) {
            requestedPositions.add(positionsList.get(i));
        }
    }

    private void removeWorldIfEmpty(World world) {
        Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
        if (byNode != null && byNode.isEmpty()) {
            trackedPreviews.remove(world);
        }
    }

    private static <T> T runOnWorldThread(World world, Supplier<T> supplier) {
        if (supplier == null) {
            return null;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            throw new IllegalStateException("Preview world mutations require a ServerWorld");
        }

        MinecraftServer server = serverWorld.getServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server unavailable for preview world mutation");
        }

        if (!server.isOnThread()) {
            try {
                return server.submit(supplier).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while scheduling preview work on server thread", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Preview work failed on server thread", e.getCause());
            }
        }

        return supplier.get();
    }

    private static void assertOnServerThread(World world) {
        if (!(world instanceof ServerWorld serverWorld)) {
            throw new IllegalStateException("Preview world mutations require a ServerWorld");
        }
        MinecraftServer server = serverWorld.getServer();
        if (server == null || !server.isOnThread()) {
            throw new IllegalStateException("Preview world mutation invoked off server thread");
        }
    }

    private record TrackedPreviewState(Map<BlockPos, BlockState> previousStates, BlockState previewState) {
    }
}
