package com.nodecraft.gui.editor.impl;

import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiNodeHistoryScopeTest {

    @Test
    void singleScopePreservesParentHistoryOnExit() {
        StubEditor editor = new StubEditor();
        ImGuiNodeHistory history = editor.history();

        history.recordPropertyChange(UUID.randomUUID(), "size", 1.0d, 2.0d);
        assertEquals(1, history.undoStackSize());

        history.enterScope();
        assertEquals(1, history.scopeDepth());
        assertEquals(0, history.undoStackSize());
        assertTrue(history.isRecording());

        history.recordPropertyChange(UUID.randomUUID(), "size", 2.0d, 3.0d);
        assertEquals(1, history.undoStackSize());

        assertTrue(history.exitScope());
        assertEquals(0, history.scopeDepth());
        assertEquals(1, history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.PROPERTY_CHANGE, history.getUndoTopActionType());
        assertTrue(history.isRecording());
    }

    @Test
    void nestedScopesRestoreParentAndGrandparentHistory() {
        StubEditor editor = new StubEditor();
        ImGuiNodeHistory history = editor.history();

        history.recordPropertyChange(UUID.randomUUID(), "a", 0.0d, 1.0d);
        history.enterScope();
        history.recordPropertyChange(UUID.randomUUID(), "b", 1.0d, 2.0d);
        history.enterScope();
        assertEquals(2, history.scopeDepth());
        history.recordPropertyChange(UUID.randomUUID(), "c", 2.0d, 3.0d);
        assertEquals(1, history.undoStackSize());

        assertTrue(history.exitScope());
        assertEquals(1, history.scopeDepth());
        assertEquals(1, history.undoStackSize());

        assertTrue(history.exitScope());
        assertEquals(0, history.scopeDepth());
        assertEquals(1, history.undoStackSize());
        assertFalse(history.exitScope());
    }

    @Test
    void enterScopeRestoresPausedRecordingOnExit() {
        StubEditor editor = new StubEditor();
        ImGuiNodeHistory history = editor.history();
        history.pauseRecording();
        assertFalse(history.isRecording());

        history.enterScope();
        assertTrue(history.isRecording());
        history.recordPropertyChange(UUID.randomUUID(), "size", 1.0d, 2.0d);
        assertEquals(1, history.undoStackSize());

        assertTrue(history.exitScope());
        assertFalse(history.isRecording());
        assertEquals(0, history.undoStackSize());
    }

    @Test
    void childScopeEntriesAreDiscardedAndDoNotPolluteParent() {
        StubEditor editor = new StubEditor();
        ImGuiNodeHistory history = editor.history();

        history.recordPropertyChange(UUID.randomUUID(), "parent", 0.0d, 1.0d);
        history.enterScope();
        history.recordPropertyChange(UUID.randomUUID(), "child-a", 1.0d, 2.0d);
        history.recordPropertyChange(UUID.randomUUID(), "child-b", 2.0d, 3.0d, 9L);
        assertEquals(2, history.undoStackSize());

        assertTrue(history.exitScope());
        assertEquals(1, history.undoStackSize());
        assertEquals(0, history.redoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.PROPERTY_CHANGE, history.getUndoTopActionType());
    }

    private static final class StubEditor implements ICanvasEditor {
        private final NodeGraph graph = new NodeGraph("scope-history");
        private final ImGuiNodeHistory history = new ImGuiNodeHistory(this);

        ImGuiNodeHistory history() {
            return history;
        }

        @Override
        public NodeGraph getCurrentGraph() {
            return graph;
        }

        @Override
        public boolean undo() {
            return history.undo();
        }

        @Override
        public boolean redo() {
            return history.redo();
        }

        @Override
        public float getCanvasZoom() {
            return 1.0f;
        }

        @Override
        public float getCanvasOffsetX() {
            return 0.0f;
        }

        @Override
        public float getCanvasOffsetY() {
            return 0.0f;
        }

        @Override
        public UUID getSelectedNodeId() {
            return null;
        }

        @Override
        public void setSelectedNodeId(UUID nodeId) {
        }

        @Override
        public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return false;
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return false;
        }

        @Override
        public Map<UUID, NodePosition> getNodePositions() {
            return Map.of();
        }

        @Override
        public NodePosition getNodePosition(UUID nodeId) {
            return null;
        }

        @Override
        public void setCurrentGraph(NodeGraph graph) {
        }

        @Override
        public void setNodePositions(Map<UUID, NodePosition> positions) {
        }

        @Override
        public boolean isShowGrid() {
            return false;
        }

        @Override
        public void setShowGrid(boolean showGrid) {
        }

        @Override
        public void setCanvasZoom(float zoom) {
        }

        @Override
        public void setCanvasOffset(float x, float y) {
        }

        @Override
        public void clearNodePositions() {
        }

        @Override
        public void clearSelectedNodes() {
        }

        @Override
        public void removeSelectedNode(UUID nodeId) {
        }

        @Override
        public void removeNodePosition(UUID nodeId) {
        }

        @Override
        public UUID getNodeIdUnderMouse(float mouseX, float mouseY) {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public com.nodecraft.nodesystem.api.INode addNode(String nodeTypeId, float x, float y) {
            return null;
        }

        @Override
        public com.nodecraft.nodesystem.api.INode addNodeWithState(
                String nodeTypeId,
                @Nullable UUID oldNodeId,
                float x,
                float y,
                @Nullable Object nodeState) {
            return null;
        }

        @Override
        public Set<UUID> getSelectedNodeIds() {
            return Set.of();
        }

        @Override
        public void setCanvasView(float zoom, float offsetX, float offsetY) {
        }

        @Override
        public void pasteNodesAtPosition(float x, float y) {
        }

        @Override
        public ImGuiNodeInteraction getInteraction() {
            return null;
        }

        @Override
        public Map<UUID, Map<String, imgui.ImVec2>> getPortScreenPositions() {
            return Map.of();
        }

        @Override
        public ImGuiNodeIO getNodeIO() {
            return null;
        }

        @Override
        public ImGuiNodeHistory getHistory() {
            return history;
        }

        @Override
        public ImGuiNodeClipboard getClipboard() {
            return null;
        }

        @Override
        public boolean copySelectedNodes() {
            return false;
        }

        @Override
        public boolean cutSelectedNodes() {
            return false;
        }

        @Override
        public boolean pasteNodesAt(float x, float y) {
            return false;
        }

        @Override
        public boolean deleteSelectedNodes() {
            return false;
        }

        @Override
        public void selectAllNodes() {
        }

        @Override
        public boolean createSubgraphFromSelection() {
            return false;
        }

        @Override
        public boolean openSelectedSubgraph() {
            return false;
        }

        @Override
        public boolean dissolveSelectedSubgraph() {
            return false;
        }

        @Override
        public boolean restoreGraphSnapshot(SavedGraph snapshot) {
            return false;
        }

        @Override
        public boolean hasUnsavedChanges() {
            return false;
        }

        @Override
        public boolean duplicateSelectedNode() {
            return false;
        }

        @Override
        public boolean alignNodes(Set<UUID> nodeIds, NodeAlignmentAction action) {
            return false;
        }

        @Override
        public void setNodeCustomColor(UUID nodeId, int color) {
        }

        @Override
        public Integer getNodeCustomColor(UUID nodeId) {
            return null;
        }

        @Override
        public void removeNodeCustomColor(UUID nodeId) {
        }

        @Override
        public boolean hasNodeCustomColor(UUID nodeId) {
            return false;
        }

        @Override
        public boolean toggleNodeDisabled(UUID nodeId) {
            return false;
        }

        @Override
        public void setNodeDisabled(UUID nodeId, boolean disabled) {
        }

        @Override
        public boolean isNodeDisabled(UUID nodeId) {
            return false;
        }

        @Override
        public boolean toggleNodeVisible(UUID nodeId) {
            return false;
        }

        @Override
        public void setNodeVisible(UUID nodeId, boolean visible) {
        }

        @Override
        public boolean isNodeVisible(UUID nodeId) {
            return true;
        }
    }
}
