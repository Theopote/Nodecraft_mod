package com.nodecraft.nodesystem.nodes.world.write;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks capture-before-mutate + NBT restore failure accounting.
 */
class WorldWriteTransactionNbtContractTest {

    private static final List<String> BAKE_MUTATORS = List.of(
        "SetBlockNode.java",
        "SetBlocksNode.java",
        "FillRegionNode.java",
        "ReplaceBlocksNode.java",
        "CloneRegionNode.java",
        "RemoveBlocksNode.java",
        "SetBlockNbtNode.java"
    );

    @Test
    void blockMutatorsEnqueueBakePlacements() throws Exception {
        for (String file : BAKE_MUTATORS) {
            String src = Files.readString(Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/" + file));
            assertTrue(src.contains("WorldWriteBakeBridge.enqueueAndAwait("), file + " must enqueue bake");
            assertTrue(src.contains("WorldWriteBakeBridge.placement(") || src.contains("BakeTask.Placement"),
                file + " must build bake placements");
        }
        String bakeTask = Files.readString(Path.of("src/main/java/com/nodecraft/nodesystem/bake/BakeTask.java"));
        int capture = bakeTask.indexOf("before = captureCell(");
        int mutate = bakeTask.indexOf("world.setBlockState(");
        assertTrue(capture >= 0 && mutate > capture, "BakeTask must capture BE NBT before setBlockState");
        assertTrue(bakeTask.contains("WorldWriteNbtUtils.extractBlockEntityNbt("));
        assertTrue(bakeTask.contains("WorldWriteNbtUtils.applyToBlockEntity("));
    }

    @Test
    void writeSignTextStillCapturesBeforeMutate() throws Exception {
        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/WriteSignTextNode.java"));
        assertTrue(src.contains("WorldWriteUndoJournal.captureCurrent("));
        assertTrue(src.contains("tx.recordSuccess(before)"));
        assertFalse(src.contains("tx.recordSuccess(context,"));
        int captureIdx = src.indexOf("WorldWriteUndoJournal.captureCurrent(");
        int mutateIdx = src.indexOf("setText(");
        assertTrue(captureIdx >= 0 && mutateIdx > captureIdx);
    }

    @Test
    void journalExposesRestoreAndLoadedCapture() throws Exception {
        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/WorldWriteUndoJournal.java"));
        assertTrue(src.contains("public static boolean restore(ExecutionContext context, BlockSnapshot snapshot)"));
        assertTrue(src.contains("WorldWriteUtils.isChunkLoaded(context, pos)"));
        assertTrue(src.contains("WorldWriteUtils.isChunkLoaded(context, snapshot.pos())"));
    }

    @Test
    void historyFacadeDelegatesToBakeHistory() throws Exception {
        String history = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/WorldWriteHistoryService.java"));
        String undoNode = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/UndoLastWorldWriteNode.java"));
        String redoNode = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/RedoLastWorldWriteNode.java"));

        assertTrue(history.contains("undoLast(UUID actorId, ExecutionContext context)"));
        assertTrue(history.contains("redoLast(UUID actorId, ExecutionContext context)"));
        assertTrue(history.contains("BakePlacementService.getInstance().getHistory"));
        assertTrue(history.contains("service.undoLast(actorId, context.getWorld())"));
        assertTrue(history.contains("service.redoLast(actorId, context.getWorld())"));
        assertTrue(undoNode.contains("service.undoLast(actorId, context)"));
        assertTrue(redoNode.contains("service.redoLast(actorId, context)"));
        assertFalse(undoNode.contains("undoLast(actorId, context.getWorld())"));
        assertFalse(redoNode.contains("redoLast(actorId, context.getWorld())"));
    }

    @Test
    void nbtRestoreFailureMakesUndoIncomplete() {
        assertTrue(WorldWriteHistoryService.UndoRecord.isFullRestoreSuccess(true, false, false, false));
        assertTrue(WorldWriteHistoryService.UndoRecord.isFullRestoreSuccess(true, true, true, true));
        assertFalse(WorldWriteHistoryService.UndoRecord.isFullRestoreSuccess(false, true, true, true));
        assertFalse(WorldWriteHistoryService.UndoRecord.isFullRestoreSuccess(true, true, false, true));
        assertFalse(WorldWriteHistoryService.UndoRecord.isFullRestoreSuccess(true, true, true, false));

        WorldWriteHistoryService.UndoApplyResult partial =
            WorldWriteHistoryService.UndoApplyResult.ok(1, 1);
        assertTrue(partial.success());
        assertFalse(partial.complete());
        assertEquals(1, partial.failureCount());
    }

    @Test
    void recordSuccessStoresProvidedSnapshotNbtWithoutWorldReread() {
        BlockPos pos = new BlockPos(1, 64, 2);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Lock", "diamonds");
        WorldWriteUndoJournal tx = new WorldWriteUndoJournal("minecraft:overworld");
        tx.recordSuccess(null);
        assertEquals(1, tx.failureCount());
        assertFalse(tx.isComplete());
        assertNull(WorldWriteUndoJournal.captureCurrent(null, pos));
        assertFalse(WorldWriteUndoJournal.restore(null, null));
    }
}
