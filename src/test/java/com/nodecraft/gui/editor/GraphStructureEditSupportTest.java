package com.nodecraft.gui.editor;

import com.nodecraft.gui.components.node.NodeActionGraphSupport;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.subgraph.SubgraphEditService;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphStructureEditSupportTest {

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void inputCountShrinkRestoresConnectionsOnUndo() {
        Fixture fixture = Fixture.create();
        CreateListNode list = new CreateListNode();
        RelayNode source = new RelayNode();
        fixture.document.getGraph().addNode(list);
        fixture.document.getGraph().addNode(source);
        fixture.document.setNodePosition(list.getId(), new NodePosition(0, 0));
        fixture.document.setNodePosition(source.getId(), new NodePosition(-100, 0));

        UUID listId = list.getId();
        UUID sourceId = source.getId();
        assertEquals(3, list.getInputCount());
        assertTrue(fixture.document.getGraph().connect(sourceId, "output_signal", listId, "input_2"));
        fixture.history.clear();

        assertEquals(
                GraphStructureEditSupport.Outcome.SUCCESS,
                GraphStructureEditSupport.run(
                        fixture.structureHost(),
                        "Change inputCount",
                        () -> {
                            list.setInputCount(2);
                            // Drop edges whose ports vanished (same as editor cleanupDangling).
                            NodeGraph graph = fixture.document.getGraph();
                            for (NodeGraph.Connection connection : List.copyOf(graph.getConnections())) {
                                boolean sourceOk = connection.sourceNode().getOutputPorts().stream()
                                        .anyMatch(p -> p.getId().equals(connection.sourcePort().getId()));
                                boolean targetOk = connection.targetNode().getInputPorts().stream()
                                        .anyMatch(p -> p.getId().equals(connection.targetPort().getId()));
                                if (!sourceOk || !targetOk) {
                                    graph.removeConnection(connection);
                                }
                            }
                            return true;
                        }
                ));

        assertEquals(1, fixture.history.undoStackSize());
        assertEquals(2, list.getInputCount());
        assertTrue(fixture.document.getGraph().getConnections().isEmpty());

        assertTrue(fixture.history.undo());
        // Snapshot restore remaps UUIDs — locate restored CreateList by type.
        CreateListNode restoredList = fixture.document.getGraph().getNodes().stream()
                .filter(CreateListNode.class::isInstance)
                .map(CreateListNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(3, restoredList.getInputCount());
        assertEquals(1, fixture.document.getGraph().getConnections().size());
        NodeGraph.Connection edge = fixture.document.getGraph().getConnections().getFirst();
        assertEquals("output_signal", edge.sourcePort().getId());
        assertEquals("input_2", edge.targetPort().getId());
    }

    @Test
    void forkBranchRemovalRestoresPortAndEdgeOnUndo() {
        Fixture fixture = Fixture.create();
        SignalForkNode fork = new SignalForkNode();
        RelayNode sink = new RelayNode();
        fixture.document.getGraph().addNode(fork);
        fixture.document.getGraph().addNode(sink);
        fixture.document.setNodePosition(fork.getId(), new NodePosition(0, 0));
        fixture.document.setNodePosition(sink.getId(), new NodePosition(200, 0));

        UUID forkId = fork.getId();
        UUID sinkId = sink.getId();
        // Default fork already has output_a / output_b.
        assertTrue(fixture.document.getGraph().connect(forkId, "output_b", sinkId, "input_signal"));
        fixture.history.clear();

        assertEquals(
                GraphStructureEditSupport.Outcome.SUCCESS,
                GraphStructureEditSupport.run(
                        fixture.structureHost(),
                        "Remove Fork Branch",
                        () -> {
                            String removed = fork.removeLastOutputBranch();
                            if (removed != null) {
                                NodeActionGraphSupport.removeConnectionsForPort(
                                        () -> fixture.document.getGraph(), forkId, removed, false);
                            }
                            return true;
                        }
                ));

        assertEquals(1, fixture.history.undoStackSize());
        assertTrue(fixture.document.getGraph().getConnections().isEmpty());

        assertTrue(fixture.history.undo());
        SignalForkNode restoredFork = fixture.document.getGraph().getNodes().stream()
                .filter(SignalForkNode.class::isInstance)
                .map(SignalForkNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(2, restoredFork.getOutputBranchCount());
        assertEquals(1, fixture.document.getGraph().getConnections().size());
        NodeGraph.Connection edge = fixture.document.getGraph().getConnections().getFirst();
        assertEquals("output_b", edge.sourcePort().getId());
        assertEquals("input_signal", edge.targetPort().getId());
    }

    private static final class Fixture {
        final EditorDocumentState document;
        final ImGuiNodeHistory history;
        final DocumentEditor editor;

        private Fixture(EditorDocumentState document, ImGuiNodeHistory history, DocumentEditor editor) {
            this.document = document;
            this.history = history;
            this.editor = editor;
        }

        static Fixture create() {
            EditorDocumentState document = new EditorDocumentState();
            document.setGraph(new NodeGraph("structure-edit"));
            DocumentEditor editor = new DocumentEditor(document);
            return new Fixture(document, editor.history(), editor);
        }

        GraphStructureEditSupport.Host structureHost() {
            return new GraphStructureEditSupport.Host() {
                @Override
                public EditorDocumentState document() {
                    return document;
                }

                @Override
                public ImGuiNodeHistory history() {
                    return history;
                }

                @Override
                public boolean restoreDocument(SavedGraph snapshot) {
                    return editor.restoreGraphSnapshot(snapshot);
                }

                @Override
                public void notifyStructureDirty() {
                }
            };
        }
    }

    private static final class DocumentEditor implements ICanvasEditor {
        private final EditorDocumentState document;
        private final ImGuiNodeHistory history;

        private DocumentEditor(EditorDocumentState document) {
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
        public boolean restoreGraphSnapshot(SavedGraph snapshot) {
            if (snapshot == null) {
                return false;
            }
            GraphLoadResult loadResult = GraphSerializer.loadFromSavedGraph(snapshot);
            if (!loadResult.hasLoadedNodes() && !GraphSerializer.isEmptyNodeList(snapshot)) {
                return false;
            }
            SubgraphEditService.LoadedGraph loaded = SubgraphEditService.toLoadedGraph(snapshot, loadResult);
            document.setGraph(loaded.graph());
            document.replaceNodePositions(loaded.positions());
            document.applySavedGraphMetadata(snapshot);
            return true;
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
        public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return document.getGraph().connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return document.getGraph().disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
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
            document.replaceNodePositions(positions);
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
            document.clearNodePositions();
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
