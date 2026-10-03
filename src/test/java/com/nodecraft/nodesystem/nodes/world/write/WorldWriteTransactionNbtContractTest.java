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

    private static final List<String> CAPTURE_BEFORE_MUTATE_SOURCES = List.of(
        "SetBlockNode.java",
        "SetBlocksNode.java",
        "FillRegionNode.java",
        "ReplaceBlocksNode.java",
        "CloneRegionNode.java",
        "RemoveBlocksNode.java",
        "SetBlockNbtNode.java",
        "WriteSignTextNode.java"
    );

    @Test
    void setBlockCapturesSnapshotBeforeMutation() throws Exception {
        for (String file : CAPTURE_BEFORE_MUTATE_SOURCES) {
            String src = Files.readString(Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/" + file));
            assertTrue(src.contains("WorldWriteUndoJournal.captureCurrent("), file + " must captureCurrent");
            assertTrue(src.contains("tx.recordSuccess(before)"), file + " must recordSuccess(before)");
            assertFalse(src.contains("tx.recordSuccess(context,"), file + " must not re-read NBT after mutate");
            int captureIdx = src.indexOf("WorldWriteUndoJournal.captureCurrent(");
            int mutateIdx = firstMutateIndex(src);
            assertTrue(captureIdx >= 0 && mutateIdx > captureIdx,
                file + " must captureCurrent before setBlockState/breakBlock");
        }
    }

    @Test
    void setBlockRecordsSuccessOnlyAfterFullCellSuccess() throws Exception {
        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/SetBlockNode.java"));
        int nbtApply = src.indexOf("WorldWriteNbtUtils.applyToBlockEntity(");
        int recordSuccess = src.indexOf("tx.recordSuccess(before)");
        int restore = src.indexOf("WorldWriteUndoJournal.restore(context, before)");
        assertTrue(nbtApply >= 0 && recordSuccess > nbtApply,
            "SetBlock must apply NBT before recordSuccess");
        assertTrue(restore >= 0 && restore < recordSuccess,
            "SetBlock must restore on NBT fail before committing undo");
        assertFalse(src.contains("Block placed, but NBT was not applied"));
    }

    @Test
    void setBlocksRestoresCellWhenNbtFails() throws Exception {
        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/SetBlocksNode.java"));
        assertTrue(src.contains("WorldWriteUndoJournal.restore(context, before)"));
        int restore = src.indexOf("WorldWriteUndoJournal.restore(context, before)");
        int recordFailure = src.indexOf("tx.recordFailure()", restore);
        assertTrue(recordFailure > restore, "NBT fail must restore then recordFailure");
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
    void undoRedoCaptureInverseViaExecutionContext() throws Exception {
        String history = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/WorldWriteHistoryService.java"));
        String undoNode = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/UndoLastWorldWriteNode.java"));
        String redoNode = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/RedoLastWorldWriteNode.java"));

        assertTrue(history.contains("undoLast(UUID actorId, ExecutionContext context)"));
        assertTrue(history.contains("redoLast(UUID actorId, ExecutionContext context)"));
        assertTrue(history.contains("WorldWriteUndoJournal.captureCurrent(context, pos)"));
        assertFalse(history.contains("skip NBT capture on inverse"));
        assertTrue(history.contains("applyBlockEntityNbt(restored, targetNbt, context)"));
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

    private static int firstMutateIndex(String src) {
        int set = indexOrMax(src, "setBlockState(");
        int brk = indexOrMax(src, "breakBlock(");
        int apply = indexOrMax(src, "applyBlockEntityNbt(");
        int setText = indexOrMax(src, "setText(");
        return Math.min(Math.min(set, brk), Math.min(apply, setText));
    }

    private static int indexOrMax(String src, String token) {
        int idx = src.indexOf(token);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }
}
