package com.nodecraft.gui.editor.connection;

import com.nodecraft.gui.editor.GraphStructureEditSupport;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.subgraph.SubgraphEditService;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionEditServiceRerouteTest {

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void insertRerouteSuccessIsSingleUndoStep() {
        Fixture fixture = Fixture.create(true);
        RelayNode source = new RelayNode();
        RelayNode target = new RelayNode();
        fixture.document.getGraph().addNode(source);
        fixture.document.getGraph().addNode(target);
        fixture.document.setNodePosition(source.getId(), new NodePosition(0, 0));
        fixture.document.setNodePosition(target.getId(), new NodePosition(200, 0));
        assertTrue(fixture.service.connect(
                source.getId(), "output_signal", target.getId(), "input_signal"));
        fixture.history.clear();

        UUID sourceId = source.getId();
        UUID targetId = target.getId();
        fixture.service.insertReroute(100f, 0f, sourceId, "output_signal", targetId, "input_signal");

        assertEquals(1, fixture.history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, fixture.history.getUndoTopActionType());
        assertEquals(3, fixture.document.getGraph().getNodes().size());
        assertEquals(2, fixture.document.getGraph().getConnections().size());
        assertFalse(fixture.document.getGraph().isConnected(sourceId, "output_signal", targetId, "input_signal"));

        assertTrue(fixture.history.undo());
        // Snapshot restore remaps node UUIDs — assert structure, not original ids.
        assertEquals(2, fixture.document.getGraph().getNodes().size());
        assertEquals(1, fixture.document.getGraph().getConnections().size());
        assertDirectRelayConnection(fixture.document.getGraph());

        assertTrue(fixture.history.redo());
        assertEquals(3, fixture.document.getGraph().getNodes().size());
        assertEquals(2, fixture.document.getGraph().getConnections().size());
    }

    @Test
    void insertRerouteSecondConnectFailureRestoresWithoutHistory() {
        Fixture fixture = Fixture.create(false);
        RelayNode source = new RelayNode();
        RelayNode target = new RelayNode();
        fixture.document.getGraph().addNode(source);
        fixture.document.getGraph().addNode(target);
        fixture.document.setNodePosition(source.getId(), new NodePosition(0, 0));
        fixture.document.setNodePosition(target.getId(), new NodePosition(200, 0));
        assertTrue(fixture.service.connect(
                source.getId(), "output_signal", target.getId(), "input_signal"));
        fixture.history.clear();

        UUID sourceId = source.getId();
        UUID targetId = target.getId();
        int nodesBefore = fixture.document.getGraph().getNodes().size();
        fixture.service.insertReroute(100f, 0f, sourceId, "output_signal", targetId, "input_signal");

        assertEquals(0, fixture.history.undoStackSize());
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
        assertEquals(1, fixture.document.getGraph().getConnections().size());
        assertDirectRelayConnection(fixture.document.getGraph());
    }

    private static void assertDirectRelayConnection(NodeGraph graph) {
        assertEquals(1, graph.getConnections().size());
        NodeGraph.Connection edge = graph.getConnections().getFirst();
        assertEquals("output_signal", edge.sourcePort().getId());
        assertEquals("input_signal", edge.targetPort().getId());
        assertEquals("utilities.assist.relay", edge.sourceNode().getTypeId());
        assertEquals("utilities.assist.relay", edge.targetNode().getTypeId());
    }

    private static final class Fixture {
        final EditorDocumentState document;
        final ImGuiNodeHistory history;
        final ConnectionEditService service;

        private Fixture(EditorDocumentState document, ImGuiNodeHistory history, ConnectionEditService service) {
            this.document = document;
            this.history = history;
            this.service = service;
        }

        static Fixture create(boolean healthyRelay) {
            NodeGraph graph = new NodeGraph("reroute");
            EditorDocumentState document = new EditorDocumentState();
            document.setGraph(graph);

            DocumentEditor editor = new DocumentEditor(document);
            ImGuiNodeHistory history = editor.history();

            AtomicInteger selected = new AtomicInteger();
            ConnectionEditService.Host host = new ConnectionEditService.Host() {
                @Override
                public EditorDocumentState document() {
                    return document;
                }

                @Override
                public ImGuiNodeHistory history() {
                    return history;
                }

                @Override
                public void notifyStructureDirty() {
                }

                @Override
                public void notifyConnectionAdded(Map<String, Object> eventData) {
                }

                @Override
                public INode addNode(String nodeTypeId, float x, float y) {
                    INode node = healthyRelay ? new RelayNode() : new BrokenRelayNode();
                    document.getGraph().addNode(node);
                    document.setNodePosition(node.getId(), new NodePosition(x, y));
                    return node;
                }

                @Override
                public void removeNodePosition(UUID nodeId) {
                    document.removeNodePosition(nodeId);
                }

                @Override
                public void clearSelectedNodes() {
                }

                @Override
                public void setSelectedNodeId(UUID nodeId) {
                    selected.incrementAndGet();
                }

                @Override
                public SavedGraph snapshotDocument() {
                    return GraphStructureEditSupport.snapshotDocument(document);
                }

                @Override
                public boolean restoreDocument(SavedGraph snapshot) {
                    return editor.restoreGraphSnapshot(snapshot);
                }
            };

            return new Fixture(document, history, new ConnectionEditService(host));
        }
    }

    /** Relay missing output port so the second reconnect fails. */
    private static final class BrokenRelayNode extends BaseNode {
        BrokenRelayNode() {
            super(UUID.randomUUID(), "utilities.assist.relay");
            addInputPort(new BasePort("input_signal", "Input", "", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
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
