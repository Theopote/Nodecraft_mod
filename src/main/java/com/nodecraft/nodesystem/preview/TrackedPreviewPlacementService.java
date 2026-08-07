package com.nodecraft.nodesystem.preview;

import com.nodecraft.core.NodeCraft;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nodecraft.nodesystem.bake.PlacementMode;

/**
 * Tracks temporary preview blocks placed directly into the world.
 * Clearing a tracked preview restores the previous world state.
 * This service only manages preview lifecycle and never commits builds.
 * It is a controlled backend and should not be used as the default preview path.
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

    public synchronized int updateTrackedPreview(World world,
                                                 String nodeId,
                                                 List<BlockPos> positions,
                                                 BlockState previewState,
                                                 PlacementMode placementMode) {
        if (world == null || nodeId == null || nodeId.isEmpty() || positions == null || positions.isEmpty() || previewState == null) {
            clearTrackedPreview(world, nodeId);
            return 0;
        }

        Map<String, TrackedPreviewState> byNode = trackedPreviews.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        TrackedPreviewState previousTrackedState = byNode.get(nodeId);

        Map<BlockPos, BlockState> trackedOriginalStates = previousTrackedState == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previousTrackedState.previousStates());
        BlockState previousPreviewState = previousTrackedState == null ? null : previousTrackedState.previewState();

        Set<BlockPos> requestedPositions = new LinkedHashSet<>();
        for (BlockPos originalPos : positions) {
            if (originalPos != null) {
                requestedPositions.add(originalPos.toImmutable());
            }
        }

        if (requestedPositions.isEmpty()) {
            clearTrackedPreview(world, nodeId);
            return 0;
        }

        // Check and enforce maximum tracked preview size
        if (requestedPositions.size() > MAX_TRACKED_PREVIEW_BLOCKS) {
            NodeCraft.LOGGER.warn(
                "TrackedPreviewPlacementService: Requested {} blocks for preview, but limit is {}. " +
                "Consider using GHOST preview for large models. Preview will be limited to {} blocks. nodeId={}",
                requestedPositions.size(), MAX_TRACKED_PREVIEW_BLOCKS, MAX_TRACKED_PREVIEW_BLOCKS, nodeId
            );

            // Sample down to the limit
            List<BlockPos> positionsList = new ArrayList<>(requestedPositions);
            requestedPositions.clear();

            // Use simple sampling: take every Nth position
            int step = Math.max(1, positionsList.size() / MAX_TRACKED_PREVIEW_BLOCKS);
            for (int i = 0; i < positionsList.size() && requestedPositions.size() < MAX_TRACKED_PREVIEW_BLOCKS; i += step) {
                requestedPositions.add(positionsList.get(i));
            }
        }

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
            if (originalState != null && world.setBlockState(removedPos, originalState, Block.NOTIFY_ALL)) {
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

        if (!trackedOriginalStates.isEmpty()) {
            byNode.put(nodeId, new TrackedPreviewState(trackedOriginalStates, previewState));
        } else {
            byNode.remove(nodeId);
        }

        if (byNode.isEmpty()) {
            trackedPreviews.remove(world);
        }

        NodeCraft.LOGGER.debug(
                "TrackedPreviewPlacementService.updateTrackedPreview nodeId={} requested={} placed={} skipped={} restored={} unchanged={} tracked={}",
                nodeId, requestedPositions.size(), placedCount, skippedCount, restoredCount, unchangedCount, trackedOriginalStates.size()
        );

        return trackedOriginalStates.size();
    }

    public synchronized int clearTrackedPreview(World world, String nodeId) {
        return clearTrackedPreviewInternal(world, nodeId, null);
    }

    /**
     * Thread-safe version that ensures world restoration happens on the server thread.
     * Use this when called from worker threads (e.g., during node execution cleanup).
     */
    public synchronized int clearTrackedPreviewOnWorldThread(World world, String nodeId,
                                                              com.nodecraft.nodesystem.execution.ExecutionContext context) {
        return clearTrackedPreviewInternal(world, nodeId, context);
    }

    private synchronized int clearTrackedPreviewInternal(World world, String nodeId,
                                                          com.nodecraft.nodesystem.execution.ExecutionContext context) {
        if (world == null || nodeId == null || nodeId.isEmpty()) {
            return 0;
        }

        Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
        if (byNode == null) {
            return 0;
        }

        TrackedPreviewState trackedState = byNode.remove(nodeId);
        if (trackedState == null) {
            if (byNode.isEmpty()) {
                trackedPreviews.remove(world);
            }
            NodeCraft.LOGGER.debug("TrackedPreviewPlacementService.clearTrackedPreview nodeId={} had no tracked state", nodeId);
            return 0;
        }

        // Copy the states to restore before potentially switching threads
        final Map<BlockPos, BlockState> statesToRestore = new java.util.LinkedHashMap<>(trackedState.previousStates());

        // Perform restoration on the world thread if context is available
        if (context != null) {
            try {
                return context.callOnWorldThread(() -> {
                    int count = 0;
                    for (Map.Entry<BlockPos, BlockState> entry : statesToRestore.entrySet()) {
                        if (world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL)) {
                            count++;
                        }
                    }
                    NodeCraft.LOGGER.debug(
                            "TrackedPreviewPlacementService.clearTrackedPreview nodeId={} restored={} (on world thread)",
                            nodeId, count
                    );
                    return count;
                });
            } catch (Exception e) {
                NodeCraft.LOGGER.error("Failed to clear tracked preview on world thread, falling back to direct restoration", e);
            }
        }

        // Fallback: direct restoration (legacy behavior, but log warning if not on server thread)
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            if (!serverWorld.getServer().isOnThread()) {
                NodeCraft.LOGGER.warn(
                    "TrackedPreviewPlacementService clearing preview from non-server thread without ExecutionContext. " +
                    "This may cause thread safety issues. nodeId={}", nodeId
                );
            }
        }

        int restoredCount = 0;
        for (Map.Entry<BlockPos, BlockState> entry : statesToRestore.entrySet()) {
            if (world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL)) {
                restoredCount++;
            }
        }

        if (byNode.isEmpty()) {
            trackedPreviews.remove(world);
        }

        NodeCraft.LOGGER.debug(
                "TrackedPreviewPlacementService.clearTrackedPreview nodeId={} restored={}",
                nodeId, restoredCount
        );

        return restoredCount;
    }

    public synchronized int getTrackedCount(World world, String nodeId) {
        if (world == null || nodeId == null || nodeId.isEmpty()) {
            return 0;
        }

        Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
        if (byNode == null) {
            return 0;
        }

        TrackedPreviewState trackedState = byNode.get(nodeId);
        return trackedState == null ? 0 : trackedState.previousStates().size();
    }

    public synchronized List<String> getTrackedPreviewIds(World world) {
        Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
        if (byNode == null || byNode.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(byNode.keySet());
    }

    public synchronized int clearAllTrackedPreviews(World world) {
        if (world == null) {
            return 0;
        }

        List<String> previewIds = getTrackedPreviewIds(world);
        int restoredCount = 0;
        for (String previewId : previewIds) {
            restoredCount += clearTrackedPreview(world, previewId);
        }
        NodeCraft.LOGGER.info(
                "TrackedPreviewPlacementService.clearAllTrackedPreviews clearedPreviews={} restoredBlocks={}",
                previewIds.size(), restoredCount
        );
        return restoredCount;
    }

    public synchronized boolean hasAnyTrackedPreviews(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return false;
        }
        for (Map<String, TrackedPreviewState> byNode : trackedPreviews.values()) {
            if (byNode.containsKey(nodeId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int clearTrackedPreviewAcrossWorlds(String nodeId) {
        return clearTrackedPreviewAcrossWorlds(nodeId, null);
    }

    /**
     * Thread-safe version that clears tracked preview across all worlds using ExecutionContext.
     */
    public synchronized int clearTrackedPreviewAcrossWorlds(String nodeId,
                                                             com.nodecraft.nodesystem.execution.ExecutionContext context) {
        if (nodeId == null || nodeId.isEmpty()) {
            return 0;
        }

        int restoredCount = 0;
        List<World> worlds = new ArrayList<>(trackedPreviews.keySet());
        for (World world : worlds) {
            restoredCount += clearTrackedPreviewInternal(world, nodeId, context);
        }
        return restoredCount;
    }

    private record TrackedPreviewState(Map<BlockPos, BlockState> previousStates, BlockState previewState) {
    }
}
