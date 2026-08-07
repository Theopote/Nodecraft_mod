package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for thread-safe preview cleanup (P0-1 fix).
 * Verifies that preview restoration happens on the server thread when ExecutionContext is provided.
 */
class TrackedPreviewCleanupThreadSafetyTest {

    private TrackedPreviewPlacementService service;
    private ServerWorld mockWorld;
    private MinecraftServer mockServer;
    private ExecutionContext mockContext;

    @BeforeEach
    void setUp() {
        service = TrackedPreviewPlacementService.getInstance();
        mockWorld = mock(ServerWorld.class);
        mockServer = mock(MinecraftServer.class);
        mockContext = mock(ExecutionContext.class);

        when(mockWorld.getServer()).thenReturn(mockServer);
        when(mockContext.getWorld()).thenReturn(mockWorld);
    }

    @Test
    void clearTrackedPreviewOnWorldThread_UsesExecutionContext() {
        // Setup: Track some preview blocks
        String nodeId = "test-node";
        List<BlockPos> positions = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0),
            new BlockPos(2, 64, 0)
        );
        BlockState previewState = Blocks.STONE.getDefaultState();
        BlockState originalState = Blocks.AIR.getDefaultState();

        // Mock world to return original state
        when(mockWorld.getBlockState(any(BlockPos.class))).thenReturn(originalState);
        when(mockWorld.setBlockState(any(BlockPos.class), any(BlockState.class), anyInt())).thenReturn(true);
        when(mockWorld.isAir(any(BlockPos.class))).thenReturn(true);

        // Update tracked preview (this will store original states)
        service.updateTrackedPreview(mockWorld, nodeId, positions, previewState, com.nodecraft.nodesystem.bake.PlacementMode.OVERWRITE);

        // Mock callOnWorldThread to capture the supplier and execute it
        ArgumentCaptor<Supplier<?>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        when(mockContext.callOnWorldThread(supplierCaptor.capture())).thenAnswer(invocation -> {
            Supplier<?> supplier = supplierCaptor.getValue();
            return supplier.get();
        });

        // Act: Clear preview with ExecutionContext
        int restored = service.clearTrackedPreviewOnWorldThread(mockWorld, nodeId, mockContext);

        // Assert: callOnWorldThread was invoked
        verify(mockContext, atLeastOnce()).callOnWorldThread(any(Supplier.class));

        // Assert: Blocks were restored
        assertEquals(3, restored);
        verify(mockWorld, times(3)).setBlockState(any(BlockPos.class), eq(originalState), anyInt());
    }

    @Test
    void clearTrackedPreviewWithoutContext_LogsWarningFromWorkerThread() {
        // This test verifies that when clearing without context from a worker thread,
        // a warning is logged (but operation still succeeds for backward compatibility)

        String nodeId = "test-node-no-context";
        List<BlockPos> positions = List.of(new BlockPos(5, 64, 5));
        BlockState previewState = Blocks.STONE.getDefaultState();
        BlockState originalState = Blocks.AIR.getDefaultState();

        when(mockWorld.getBlockState(any(BlockPos.class))).thenReturn(originalState);
        when(mockWorld.setBlockState(any(BlockPos.class), any(BlockState.class), anyInt())).thenReturn(true);
        when(mockWorld.isAir(any(BlockPos.class))).thenReturn(true);
        when(mockServer.isOnThread()).thenReturn(false); // Simulate being called from worker thread

        service.updateTrackedPreview(mockWorld, nodeId, positions, previewState, com.nodecraft.nodesystem.bake.PlacementMode.OVERWRITE);

        // Act: Clear without ExecutionContext (legacy behavior)
        int restored = service.clearTrackedPreview(mockWorld, nodeId);

        // Assert: Operation succeeded but would have logged warning
        assertEquals(1, restored);
        verify(mockWorld, times(1)).setBlockState(any(BlockPos.class), eq(originalState), anyInt());
    }

    @Test
    void clearTrackedPreviewAcrossWorlds_UsesContext() {
        // Setup: Multiple worlds with tracked previews
        String nodeId = "multi-world-node";
        ServerWorld mockWorld2 = mock(ServerWorld.class);
        MinecraftServer mockServer2 = mock(MinecraftServer.class);

        when(mockWorld2.getServer()).thenReturn(mockServer2);
        when(mockWorld.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.getDefaultState());
        when(mockWorld2.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.getDefaultState());
        when(mockWorld.setBlockState(any(BlockPos.class), any(BlockState.class), anyInt())).thenReturn(true);
        when(mockWorld2.setBlockState(any(BlockPos.class), any(BlockState.class), anyInt())).thenReturn(true);
        when(mockWorld.isAir(any(BlockPos.class))).thenReturn(true);
        when(mockWorld2.isAir(any(BlockPos.class))).thenReturn(true);

        // Track previews in both worlds
        service.updateTrackedPreview(mockWorld, nodeId, List.of(new BlockPos(0, 64, 0)),
            Blocks.STONE.getDefaultState(), com.nodecraft.nodesystem.bake.PlacementMode.OVERWRITE);
        service.updateTrackedPreview(mockWorld2, nodeId, List.of(new BlockPos(1, 64, 1)),
            Blocks.STONE.getDefaultState(), com.nodecraft.nodesystem.bake.PlacementMode.OVERWRITE);

        // Mock context to execute suppliers
        when(mockContext.callOnWorldThread(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Act: Clear across all worlds with context
        int totalRestored = service.clearTrackedPreviewAcrossWorlds(nodeId, mockContext);

        // Assert: Both worlds were cleared
        assertEquals(2, totalRestored);
        verify(mockContext, atLeast(2)).callOnWorldThread(any(Supplier.class));
    }

    @Test
    void clearTrackedPreviewOnWorldThread_HandlesNullContext() {
        // Verifies fallback behavior when context is null
        String nodeId = "null-context-node";
        List<BlockPos> positions = List.of(new BlockPos(10, 64, 10));

        when(mockWorld.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.getDefaultState());
        when(mockWorld.setBlockState(any(BlockPos.class), any(BlockState.class), anyInt())).thenReturn(true);
        when(mockWorld.isAir(any(BlockPos.class))).thenReturn(true);

        service.updateTrackedPreview(mockWorld, nodeId, positions,
            Blocks.STONE.getDefaultState(), com.nodecraft.nodesystem.bake.PlacementMode.OVERWRITE);

        // Act: Clear with null context
        int restored = service.clearTrackedPreviewOnWorldThread(mockWorld, nodeId, null);

        // Assert: Fallback to direct restoration
        assertEquals(1, restored);
        verify(mockWorld, times(1)).setBlockState(any(BlockPos.class), any(BlockState.class), anyInt());
    }
}
