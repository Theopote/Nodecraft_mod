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
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCommandServiceDeleteTest {

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

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

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls, history));

        assertTrue(service.deleteSelected());
        assertEquals(1, history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.REMOVE_NODES, history.getUndoTopActionType());
        assertNull(graph.getNode(drop.getId()));
    }

    @Test
    void deleteSingleConnectedNodeUndoRestoresEdges() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("delete-edge-undo");
        RelayNode source = new RelayNode();
        RelayNode mid = new RelayNode();
        RelayNode sink = new RelayNode();
        graph.addNode(source);
        graph.addNode(mid);
        graph.addNode(sink);
        document.setGraph(graph);
        document.setNodePosition(source.getId(), new NodePosition(0, 0));
        document.setNodePosition(mid.getId(), new NodePosition(80, 0));
        document.setNodePosition(sink.getId(), new NodePosition(160, 0));
        assertTrue(graph.connect(source.getId(), "output_signal", mid.getId(), "input_signal"));
        assertTrue(graph.connect(mid.getId(), "output_signal", sink.getId(), "input_signal"));
        interaction.getSelectedNodeIds().add(mid.getId());

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();
        NodeCommandService service = new NodeCommandService(
                host(document, interaction, new AtomicInteger(), history));

        assertTrue(service.deleteSelected());
        assertEquals(2, graph.getNodes().size());
        assertEquals(0, graph.getConnections().size());
        assertEquals(1, history.undoStackSize());

        assertTrue(history.undo());
        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getConnections().size());
        assertTrue(history.redo());
        assertEquals(2, graph.getNodes().size());
        assertEquals(0, graph.getConnections().size());
    }

    @Test
    void batchDeleteIsSingleUndoStep() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("batch-delete");
        RelayNode a = new RelayNode();
        RelayNode b = new RelayNode();
        RelayNode keep = new RelayNode();
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(keep);
        document.setGraph(graph);
        document.setNodePosition(a.getId(), new NodePosition(0, 0));
        document.setNodePosition(b.getId(), new NodePosition(40, 0));
        document.setNodePosition(keep.getId(), new NodePosition(80, 0));
        assertTrue(graph.connect(a.getId(), "output_signal", b.getId(), "input_signal"));
        interaction.getSelectedNodeIds().add(a.getId());
        interaction.getSelectedNodeIds().add(b.getId());

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();
        NodeCommandService service = new NodeCommandService(
                host(document, interaction, new AtomicInteger(), history));

        assertTrue(service.deleteSelected());
        assertEquals(1, graph.getNodes().size());
        assertEquals(1, history.undoStackSize());
        assertTrue(history.undo());
        assertEquals(3, graph.getNodes().size());
        assertEquals(1, graph.getConnections().size());
    }

    @Test
    void partialDeleteFailureRollsBackWithoutHistory() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("partial-fail");
        RelayNode first = new RelayNode();
        RelayNode second = new RelayNode();
        graph.addNode(first);
        graph.addNode(second);
        document.setGraph(graph);
        document.setNodePosition(first.getId(), new NodePosition(0, 0));
        document.setNodePosition(second.getId(), new NodePosition(40, 0));
        interaction.getSelectedNodeIds().add(first.getId());
        interaction.getSelectedNodeIds().add(second.getId());

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();

        AtomicInteger positionRemovals = new AtomicInteger();
        NodeCommandService.Host failingHost = new NodeCommandService.Host() {
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
                if (positionRemovals.incrementAndGet() >= 2) {
                    throw new IllegalStateException("simulated position cleanup failure");
                }
                document.removeNodePosition(nodeId);
            }

            @Override
            public void removeSelectedNode(UUID nodeId) {
                interaction.removeFromSelection(nodeId);
            }
        };

        NodeCommandService service = new NodeCommandService(failingHost);
        assertFalse(service.deleteSelected());
        assertEquals(0, history.undoStackSize(), "history must not record a failed delete");
        assertEquals(2, graph.getNodes().size(), "partial delete must roll back removed nodes");
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

    /** Canvas wired to the same document so undo can restore nodes/edges. */
    private static final class HistoryCanvasEditor implements ICanvasEditor {
        private final EditorDocumentState document;
        private final ImGuiNodeHistory history;

        private HistoryCanvasEditor(EditorDocumentState document) {
            this.document = document;
            this.history = new ImGuiNodeHistory(this);
        }

        ImGuiNodeHistory history() {
            return history;
        }

        @Override
        public NodeGraph getCurrentGraph() {
            return document.getGraph();
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
            NodeGraph graph = document.getGraph();
            return graph != null && graph.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            NodeGraph graph = document.getGraph();
            return graph != null && graph.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public Map<UUID, NodePosition> getNodePositions() {
            return document.getNodePositions();
        }

        @Override
        public NodePosition getNodePosition(UUID nodeId) {
            return document.getNodePosition(nodeId);
        }

        @Override
        public void setCurrentGraph(NodeGraph graph) {
            document.setGraph(graph);
        }

        @Override
        public void setNodePositions(Map<UUID, NodePosition> positions) {
            document.replaceNodePositions(positions != null ? positions : Map.of());
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
            document.removeNodePosition(nodeId);
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
            return addNodeWithState(nodeTypeId, null, x, y, null);
        }

        @Override
        public INode addNodeWithState(
                String nodeTypeId,
                @Nullable UUID oldNodeId,
                float x,
                float y,
                @Nullable Object nodeState) {
            NodeGraph graph = document.getGraph();
            if (graph == null) {
                return null;
            }
            INode node = NodeRegistry.getInstance().createNodeInstance(nodeTypeId);
            if (node == null) {
                return null;
            }
            if (nodeState != null) {
                node.setNodeState(nodeState);
            }
            graph.addNode(node);
            Map<UUID, NodePosition> positions = new HashMap<>(document.getNodePositions());
            positions.put(node.getId(), new NodePosition(x, y));
            document.replaceNodePositions(positions);
            return node;
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
