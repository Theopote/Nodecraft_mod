package com.nodecraft.gui.editor.command;

import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.interaction.EditorInteractionState;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCommandServiceDeleteTest {

    @Test
    void deleteSelectedRemovesNodesPositionsAndSelection() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("delete");
        StubNode keep = new StubNode("keep");
        StubNode drop = new StubNode("drop");
        graph.addNode(keep);
        graph.addNode(drop);
        document.setGraph(graph);
        document.getNodePositions().put(keep.getId(), new NodePosition(0, 0));
        document.getNodePositions().put(drop.getId(), new NodePosition(40, 0));
        interaction.getSelectedNodeIds().add(drop.getId());

        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls));

        assertTrue(service.deleteSelected());
        assertNull(graph.getNode(drop.getId()));
        assertEquals(keep, graph.getNode(keep.getId()));
        assertNull(document.getNodePosition(drop.getId()));
        assertFalse(interaction.getSelectedNodeIds().contains(drop.getId()));
        assertEquals(1, dirtyCalls.get());
    }

    @Test
    void deleteSelectedReturnsFalseWhenNothingSelected() {
        EditorDocumentState document = new EditorDocumentState();
        document.setGraph(new NodeGraph("empty-selection"));
        EditorInteractionState interaction = new EditorInteractionState();
        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls, null));

        assertFalse(service.deleteSelected());
        assertEquals(0, dirtyCalls.get());
    }

    @Test
    void deleteSelectedRecordsRemoveNodesHistory() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("delete-history");
        StubNode drop = new StubNode("drop");
        graph.addNode(drop);
        document.setGraph(graph);
        document.getNodePositions().put(drop.getId(), new NodePosition(10, 20));
        interaction.getSelectedNodeIds().add(drop.getId());

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(graph);
        ImGuiNodeHistory history = canvas.history();
        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls, history));

        assertTrue(service.deleteSelected());
        assertEquals(1, history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.REMOVE_NODES, history.getUndoTopActionType());
        assertNull(graph.getNode(drop.getId()));
    }

    private static NodeCommandService.Host host(
            EditorDocumentState document,
            EditorInteractionState interaction,
            AtomicInteger dirtyCalls
    ) {
        return host(document, interaction, dirtyCalls, null);
    }

    private static NodeCommandService.Host host(
            EditorDocumentState document,
            EditorInteractionState interaction,
            AtomicInteger dirtyCalls,
            @Nullable ImGuiNodeHistory history
    ) {
        return new NodeCommandService.Host() {
            @Override
            public EditorDocumentState document() {
                return document;
            }

            @Override
            public EditorInteractionState interaction() {
                return interaction;
            }

            @Override
            public ImGuiNodeHistory history() {
                return history;
            }

            @Override
            public void notifyStructureDirty() {
                dirtyCalls.incrementAndGet();
            }

            @Override
            public void notifyNodeAdded(Map<String, Object> eventData) {
            }

            @Override
            public void clearSelectedNodes() {
                interaction.clearSelection();
            }

            @Override
            public void setSelectedNodeId(UUID nodeId) {
                interaction.setPrimarySelectedNodeId(nodeId);
            }

            @Override
            public void removeNodePosition(UUID nodeId) {
                document.getNodePositions().remove(nodeId);
            }

            @Override
            public void removeSelectedNode(UUID nodeId) {
                interaction.removeFromSelection(nodeId);
            }
        };
    }

    /** Minimal canvas so {@link ImGuiNodeHistory#captureRemovedNodeSnapshot} can read the graph. */
    private static final class HistoryCanvasEditor implements ICanvasEditor {
        private final NodeGraph graph;
        private final ImGuiNodeHistory history;

        private HistoryCanvasEditor(NodeGraph graph) {
            this.graph = graph;
            this.history = new ImGuiNodeHistory(this);
        }

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
        public INode addNode(String nodeTypeId, float x, float y) {
            return null;
        }

        @Override
        public INode addNodeWithState(
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

    private static final class StubNode extends BaseNode {
        StubNode(String name) {
            super(UUID.randomUUID(), "test.stub." + name);
            addOutputPort(new BasePort("out", "Out", "", NodeDataType.ANY, this));
        }

        @Override
        public String getDisplayName() {
            return "Stub";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }
}
