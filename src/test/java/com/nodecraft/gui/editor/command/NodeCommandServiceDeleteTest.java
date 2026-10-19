package com.nodecraft.gui.editor.command;

import com.nodecraft.gui.editor.StructureEditRollbackException;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.interaction.EditorInteractionState;
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
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertNull(document.getGraph().getNode(drop.getId()));
        assertNotNull(document.getGraph().getNode(keep.getId()));
        assertNull(document.getNodePosition(drop.getId()));
        assertFalse(interaction.getSelectedNodeIds().contains(drop.getId()));
        // Transaction commit + impact-scoped notify.
        assertEquals(2, dirtyCalls.get());
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
    void deleteSelectedRecordsSingleGraphTransaction() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("delete-history");
        RelayNode drop = new RelayNode();
        graph.addNode(drop);
        document.setGraph(graph);
        document.setNodePosition(drop.getId(), new NodePosition(10, 20));
        interaction.getSelectedNodeIds().add(drop.getId());

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();
        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls, history));

        assertTrue(service.deleteSelected());
        assertEquals(1, history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, history.getUndoTopActionType());
        assertNull(document.getGraph().getNode(drop.getId()));
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

        UUID sourceId = source.getId();
        UUID midId = mid.getId();
        UUID sinkId = sink.getId();

        assertTrue(service.deleteSelected());
        assertEquals(2, document.getGraph().getNodes().size());
        assertEquals(0, document.getGraph().getConnections().size());
        assertEquals(1, history.undoStackSize());

        assertTrue(history.undo());
        NodeGraph undone = document.getGraph();
        assertEquals(3, undone.getNodes().size());
        assertEquals(2, undone.getConnections().size());
        assertNotNull(undone.getNode(sourceId));
        assertNotNull(undone.getNode(midId));
        assertNotNull(undone.getNode(sinkId));
        assertTrue(undone.isConnected(sourceId, "output_signal", midId, "input_signal"));
        assertTrue(undone.isConnected(midId, "output_signal", sinkId, "input_signal"));
        assertTrue(history.redo());
        assertEquals(2, document.getGraph().getNodes().size());
        assertEquals(0, document.getGraph().getConnections().size());
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
        UUID aId = a.getId();
        UUID bId = b.getId();
        UUID keepId = keep.getId();
        interaction.getSelectedNodeIds().add(aId);
        interaction.getSelectedNodeIds().add(bId);

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();
        NodeCommandService service = new NodeCommandService(
                host(document, interaction, new AtomicInteger(), history));

        assertTrue(service.deleteSelected());
        assertEquals(1, document.getGraph().getNodes().size());
        assertEquals(1, history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, history.getUndoTopActionType());
        assertTrue(history.undo());
        NodeGraph undone = document.getGraph();
        assertEquals(3, undone.getNodes().size());
        assertEquals(1, undone.getConnections().size());
        assertNotNull(undone.getNode(aId));
        assertNotNull(undone.getNode(bId));
        assertNotNull(undone.getNode(keepId));
        assertTrue(undone.isConnected(aId, "output_signal", bId, "input_signal"));
    }

    @Test
    void partialDeleteFailureRollsBackNodesEdgesPositionsAndSelection() {
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
        assertTrue(graph.connect(first.getId(), "output_signal", second.getId(), "input_signal"));
        UUID firstId = first.getId();
        UUID secondId = second.getId();
        interaction.getSelectedNodeIds().add(firstId);
        interaction.getSelectedNodeIds().add(secondId);
        interaction.setPrimarySelectedNodeId(firstId);

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();

        Set<UUID> idsBefore = Set.of(firstId, secondId);
        Set<UUID> selectionBefore = new LinkedHashSet<>(interaction.getSelectedNodeIds());

        AtomicInteger positionRemovals = new AtomicInteger();
        NodeCommandService.Host failingHost = failingPositionHost(
                document, interaction, history, canvas, positionRemovals, 2);

        NodeCommandService service = new NodeCommandService(failingHost);
        assertFalse(service.deleteSelected());
        assertEquals(0, history.undoStackSize(), "history must not record a failed delete");

        NodeGraph restored = document.getGraph();
        assertEquals(idsBefore, nodeIds(restored));
        assertEquals(1, restored.getConnections().size());
        assertTrue(restored.isConnected(firstId, "output_signal", secondId, "input_signal"));
        assertNotNull(document.getNodePosition(firstId));
        assertNotNull(document.getNodePosition(secondId));
        assertEquals(0.0f, document.getNodePosition(firstId).x, 0.001f);
        assertEquals(40.0f, document.getNodePosition(secondId).x, 0.001f);
        assertEquals(selectionBefore, new LinkedHashSet<>(interaction.getSelectedNodeIds()));
        assertEquals(firstId, interaction.getPrimarySelectedNodeId());
    }

    @Test
    void partialDeleteFailureWithoutHistoryStillRollsBack() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("partial-fail-no-history");
        RelayNode first = new RelayNode();
        RelayNode second = new RelayNode();
        graph.addNode(first);
        graph.addNode(second);
        document.setGraph(graph);
        document.setNodePosition(first.getId(), new NodePosition(0, 0));
        document.setNodePosition(second.getId(), new NodePosition(40, 0));
        assertTrue(graph.connect(first.getId(), "output_signal", second.getId(), "input_signal"));
        UUID firstId = first.getId();
        UUID secondId = second.getId();
        interaction.getSelectedNodeIds().add(firstId);
        interaction.getSelectedNodeIds().add(secondId);

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        AtomicInteger positionRemovals = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(
                failingPositionHost(document, interaction, null, canvas, positionRemovals, 2));

        assertFalse(service.deleteSelected());
        NodeGraph restored = document.getGraph();
        assertEquals(Set.of(firstId, secondId), nodeIds(restored));
        assertTrue(restored.isConnected(firstId, "output_signal", secondId, "input_signal"));
        assertEquals(Set.of(firstId, secondId), new HashSet<>(interaction.getSelectedNodeIds()));
    }

    @Test
    void removeNodeThrowingMidDeleteRollsBack() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        ThrowingRemoveGraph graph = new ThrowingRemoveGraph("throw-remove");
        RelayNode first = new RelayNode();
        RelayNode second = new RelayNode();
        graph.addNode(first);
        graph.addNode(second);
        document.setGraph(graph);
        document.setNodePosition(first.getId(), new NodePosition(0, 0));
        document.setNodePosition(second.getId(), new NodePosition(40, 0));
        UUID firstId = first.getId();
        UUID secondId = second.getId();
        interaction.getSelectedNodeIds().add(firstId);
        interaction.getSelectedNodeIds().add(secondId);
        graph.failOnNodeId = secondId;

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        NodeCommandService service = new NodeCommandService(
                host(document, interaction, new AtomicInteger(), null, canvas));

        assertFalse(service.deleteSelected());
        assertEquals(Set.of(firstId, secondId), nodeIds(document.getGraph()));
        assertEquals(Set.of(firstId, secondId), new HashSet<>(interaction.getSelectedNodeIds()));
    }

    @Test
    void deleteUndoRedoClearsDeletedNodeGhostGrace() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("delete-ghost");
        RelayNode keep = new RelayNode();
        RelayNode drop = new RelayNode();
        graph.addNode(keep);
        graph.addNode(drop);
        document.setGraph(graph);
        document.setNodePosition(keep.getId(), new NodePosition(0, 0));
        document.setNodePosition(drop.getId(), new NodePosition(40, 0));
        UUID dropId = drop.getId();
        UUID keepId = keep.getId();
        interaction.getSelectedNodeIds().add(dropId);

        PreviewManager.touchNonEmptyForTests(dropId.toString(), "ghost_block");
        PreviewManager.touchNonEmptyForTests(keepId.toString(), "ghost_block");

        HistoryCanvasEditor canvas = new HistoryCanvasEditor(document);
        ImGuiNodeHistory history = canvas.history();
        history.clear();

        NodeCommandService.Host previewHost = new NodeCommandService.Host() {
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
                PreviewManager.hidePreviewsNotOwnedBy(liveOwnerIds(document));
            }

            @Override
            public void notifyStructureDirty(Set<UUID> impactedNodeIds) {
                for (UUID id : impactedNodeIds) {
                    if (id != null) {
                        PreviewManager.hideNodePreviews(id.toString());
                    }
                }
                notifyStructureDirty();
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
                document.removeNodePosition(nodeId);
            }

            @Override
            public void removeSelectedNode(UUID nodeId) {
                interaction.removeFromSelection(nodeId);
            }

            @Override
            public boolean restoreDocument(SavedGraph snapshot) {
                boolean ok = canvas.restoreGraphSnapshot(snapshot);
                if (ok) {
                    PreviewManager.clearAllPreviews();
                    PreviewManager.clearEmptyInputGrace();
                }
                return ok;
            }
        };

        NodeCommandService service = new NodeCommandService(previewHost);
        assertTrue(service.deleteSelected());
        assertFalse(PreviewManager.withinEmptyGraceForTests(dropId.toString(), "ghost_block"));
        assertTrue(PreviewManager.withinEmptyGraceForTests(keepId.toString(), "ghost_block"),
                "unrelated live node grace should survive delete");

        assertTrue(history.undo());
        // Snapshot restore clears all GHOST (document replace semantics).
        assertFalse(PreviewManager.withinEmptyGraceForTests(dropId.toString(), "ghost_block"));
        assertFalse(PreviewManager.withinEmptyGraceForTests(keepId.toString(), "ghost_block"));
        assertNotNull(document.getGraph().getNode(dropId));

        assertTrue(history.redo());
        assertNull(document.getGraph().getNode(dropId));
    }

    private static Set<String> liveOwnerIds(EditorDocumentState document) {
        Set<String> ids = new HashSet<>();
        NodeGraph g = document.getGraph();
        if (g == null) {
            return ids;
        }
        for (INode node : g.getNodes()) {
            ids.add(node.getId().toString());
        }
        return ids;
    }

    @Test
    void rollbackFailureThrowsStructureEditRollbackException() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("rollback-fail");
        RelayNode first = new RelayNode();
        RelayNode second = new RelayNode();
        graph.addNode(first);
        graph.addNode(second);
        document.setGraph(graph);
        document.setNodePosition(first.getId(), new NodePosition(0, 0));
        document.setNodePosition(second.getId(), new NodePosition(40, 0));
        interaction.getSelectedNodeIds().add(first.getId());
        interaction.getSelectedNodeIds().add(second.getId());

        AtomicInteger positionRemovals = new AtomicInteger();
        NodeCommandService.Host host = new NodeCommandService.Host() {
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
                return null;
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

            @Override
            public boolean restoreDocument(SavedGraph snapshot) {
                return false;
            }
        };

        NodeCommandService service = new NodeCommandService(host);
        assertThrows(StructureEditRollbackException.class, service::deleteSelected);
    }

    private static Set<UUID> nodeIds(NodeGraph graph) {
        Set<UUID> ids = new HashSet<>();
        for (INode node : graph.getNodes()) {
            ids.add(node.getId());
        }
        return ids;
    }

    private static NodeCommandService.Host host(
            EditorDocumentState document,
            EditorInteractionState interaction,
            AtomicInteger dirtyCalls
    ) {
        return host(document, interaction, dirtyCalls, null, null);
    }

    private static NodeCommandService.Host host(
            EditorDocumentState document,
            EditorInteractionState interaction,
            AtomicInteger dirtyCalls,
            @Nullable ImGuiNodeHistory history
    ) {
        return host(document, interaction, dirtyCalls, history, null);
    }

    private static NodeCommandService.Host host(
            EditorDocumentState document,
            EditorInteractionState interaction,
            AtomicInteger dirtyCalls,
            @Nullable ImGuiNodeHistory history,
            @Nullable HistoryCanvasEditor canvas
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

            @Override
            public boolean restoreDocument(SavedGraph snapshot) {
                if (canvas != null) {
                    return canvas.restoreGraphSnapshot(snapshot);
                }
                return restoreDocumentInto(document, snapshot);
            }
        };
    }

    private static NodeCommandService.Host failingPositionHost(
            EditorDocumentState document,
            EditorInteractionState interaction,
            @Nullable ImGuiNodeHistory history,
            HistoryCanvasEditor canvas,
            AtomicInteger positionRemovals,
            int failAtOrAfter
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
                if (positionRemovals.incrementAndGet() >= failAtOrAfter) {
                    throw new IllegalStateException("simulated position cleanup failure");
                }
                document.removeNodePosition(nodeId);
            }

            @Override
            public void removeSelectedNode(UUID nodeId) {
                interaction.removeFromSelection(nodeId);
            }

            @Override
            public boolean restoreDocument(SavedGraph snapshot) {
                return canvas.restoreGraphSnapshot(snapshot);
            }
        };
    }

    private static boolean restoreDocumentInto(EditorDocumentState document, SavedGraph snapshot) {
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
            boolean ok = restoreDocumentInto(document, snapshot);
            if (ok) {
                // Match ImGuiNodeEditor.restoreGraphSnapshot → notifyGraphReplaced semantics.
                PreviewManager.clearAllPreviews();
                PreviewManager.clearEmptyInputGrace();
            }
            return ok;
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

    /** Removes the first node successfully, then throws on a configured id. */
    private static final class ThrowingRemoveGraph extends NodeGraph {
        @Nullable
        UUID failOnNodeId;

        ThrowingRemoveGraph(String name) {
            super(name);
        }

        @Override
        public boolean removeNode(UUID nodeId) {
            if (failOnNodeId != null && failOnNodeId.equals(nodeId)) {
                throw new IllegalStateException("simulated graph.removeNode failure");
            }
            return super.removeNode(nodeId);
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
