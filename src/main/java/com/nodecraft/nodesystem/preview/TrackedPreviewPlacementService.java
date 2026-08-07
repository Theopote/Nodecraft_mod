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

    public int updateTrackedPreview(World world,
                                    String nodeId,
                                    List<BlockPos> positions,
                                    BlockState previewState,
                                    PlacementMode placementMode) {
        return updateTrackedPreviewOnWorldThread(world, nodeId, positions, previewState, placementMode, null);
    }

    /**
     * Thread-safe version that applies preview block mutations on the server thread.
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

        try {
            return runOnWorldThread(world, context, () ->
                updateTrackedPreviewDirect(world, nodeId, positions, previewState, placementMode, context)
            );
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to update tracked preview on world thread. nodeId={}", nodeId, e);
            return 0;
        }
    }

    private synchronized int updateTrackedPreviewDirect(World world,
                                                        String nodeId,
                                                        List<BlockPos> positions,
                                                        BlockState previewState,
                                                        PlacementMode placementMode,
                                                        @Nullable ExecutionContext context) {
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
            return clearTrackedPreviewInternal(world, nodeId, context);
        }

        // Check and enforce maximum tracked preview size
        if (requestedPositions.size() > MAX_TRACKED_PREVIEW_BLOCKS) {
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

        removeWorldIfEmpty(world);

        NodeCraft.LOGGER.debug(
                "TrackedPreviewPlacementService.updateTrackedPreview nodeId={} requested={} placed={} skipped={} restored={} unchanged={} tracked={}",
                nodeId, requestedPositions.size(), placedCount, skippedCount, restoredCount, unchangedCount, trackedOriginalStates.size()
        );

        return trackedOriginalStates.size();
    }

    public int clearTrackedPreview(World world, String nodeId) {
        return clearTrackedPreviewOnWorldThread(world, nodeId, null);
    }

    /**
     * Thread-safe version that ensures world restoration happens on the server thread.
     */
    public int clearTrackedPreviewOnWorldThread(World world, String nodeId, @Nullable ExecutionContext context) {
        try {
            return clearTrackedPreviewInternal(world, nodeId, context);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to clear tracked preview on world thread. nodeId={}", nodeId, e);
            return 0;
        }
    }

    private synchronized int clearTrackedPreviewInternal(World world, String nodeId, @Nullable ExecutionContext context) {
        if (world == null || nodeId == null || nodeId.isEmpty()) {
            return 0;
        }

        Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
        if (byNode == null) {
            return 0;
        }

        TrackedPreviewState trackedState = byNode.remove(nodeId);
        if (trackedState == null) {
            removeWorldIfEmpty(world);
            NodeCraft.LOGGER.debug("TrackedPreviewPlacementService.clearTrackedPreview nodeId={} had no tracked state", nodeId);
            return 0;
        }

        final Map<BlockPos, BlockState> statesToRestore = new LinkedHashMap<>(trackedState.previousStates());
        int restoredCount = runOnWorldThread(world, context, () -> {
            int count = 0;
            for (Map.Entry<BlockPos, BlockState> entry : statesToRestore.entrySet()) {
                if (world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL)) {
                    count++;
                }
            }
            return count;
        });

        removeWorldIfEmpty(world);

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
            restoredCount += clearTrackedPreviewInternal(world, previewId, context);
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

    public int clearTrackedPreviewAcrossWorlds(String nodeId) {
        return clearTrackedPreviewAcrossWorlds(nodeId, null);
    }

    /**
     * Clears tracked preview across all worlds, marshaling world mutations onto the server thread.
     */
    public int clearTrackedPreviewAcrossWorlds(String nodeId, @Nullable ExecutionContext context) {
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

    private void removeWorldIfEmpty(World world) {
        Map<String, TrackedPreviewState> byNode = trackedPreviews.get(world);
        if (byNode != null && byNode.isEmpty()) {
            trackedPreviews.remove(world);
        }
    }

    private static <T> T runOnWorldThread(World world, @Nullable ExecutionContext context, Supplier<T> supplier) {
        if (supplier == null) {
            return null;
        }
        if (context != null) {
            return context.callOnWorldThread(supplier);
        }
        if (world instanceof ServerWorld serverWorld) {
            MinecraftServer server = serverWorld.getServer();
            if (server != null && !server.isOnThread()) {
                try {
                    return server.submit(supplier).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Failed to run preview work on the Minecraft server thread", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Preview work failed on the Minecraft server thread", e.getCause());
                }
            }
        }
        return supplier.get();
    }

    private record TrackedPreviewState(Map<BlockPos, BlockState> previousStates, BlockState previewState) {
    }
}
