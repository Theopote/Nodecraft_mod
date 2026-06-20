package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.organization.GraphInputNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.GraphOutputNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiNodeEditorSubgraphTest {

    private final NodeRegistry registry = NodeRegistry.getInstance();

    @BeforeEach
    void registerNodes() {
        registry.clear();
        registry.registerNode(new NodeInfo("test.pass", "Pass", "test pass node", "test", 0, PassNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.subgraph", "Subgraph", "subgraph node", "utilities.organization", 0, SubgraphNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.graph_input", "Graph Input", "graph input", "utilities.organization", 0, GraphInputNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.graph_output", "Graph Output", "graph output", "utilities.organization", 0, GraphOutputNode.class));
    }

    @AfterEach
    void clearRegistry() {
        ImGuiNodeEditor.getInstance().getHistory().clear();
        registry.clear();
    }

    @Test
    void createsAndDissolvesEmbeddedSubgraphFromSelection() {
        NodeGraph graph = new NodeGraph("round trip");
        PassNode source = new PassNode();
        PassNode selectedA = new PassNode();
        PassNode selectedB = new PassNode();
        PassNode sink = new PassNode();
        graph.addNode(source);
        graph.addNode(selectedA);
        graph.addNode(selectedB);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", selectedA.getId(), "in");
        graph.connect(selectedA.getId(), "out", selectedB.getId(), "in");
        graph.connect(selectedB.getId(), "out", sink.getId(), "in");

        Map<UUID, NodePosition> positions = new HashMap<>();
        positions.put(source.getId(), new NodePosition(0, 0));
        positions.put(selectedA.getId(), new NodePosition(100, 0));
        positions.put(selectedB.getId(), new NodePosition(220, 0));
        positions.put(sink.getId(), new NodePosition(340, 0));

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.setNodePositions(positions);
        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().add(selectedA.getId());
        editor.getSelectedNodeIds().add(selectedB.getId());
        editor.setSelectedNodeId(selectedA.getId());

        assertTrue(editor.createSubgraphFromSelection());
        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getConnections().size());

        UUID wrapperId = editor.getSelectedNodeId();
        assertTrue(graph.getNode(wrapperId) instanceof SubgraphNode);

        assertTrue(editor.dissolveSelectedSubgraph());
        assertEquals(4, graph.getNodes().size());
        assertEquals(3, graph.getConnections().size());
        assertFalse(graph.getNodes().stream().anyMatch(SubgraphNode.class::isInstance));
        assertTrue(hasConnectionFrom(graph, source.getId()));
        assertTrue(hasConnectionTo(graph, sink.getId()));
    }

    @Test
    void createAndDissolveAreSingleUndoRedoTransactions() {
        ImGuiNodeEditor editor = prepareLinearSelectionGraph();
        editor.getHistory().clear();

        assertTrue(editor.createSubgraphFromSelection());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, editor.getHistory().getUndoTopActionType());
        assertTrue(editor.undo());
        assertEquals(4, editor.getCurrentGraph().getNodes().size());
        assertEquals(3, editor.getCurrentGraph().getConnections().size());
        assertFalse(editor.getCurrentGraph().getNodes().stream().anyMatch(SubgraphNode.class::isInstance));

        assertTrue(editor.redo());
        assertEquals(3, editor.getCurrentGraph().getNodes().size());
        SubgraphNode wrapper = findSubgraphNode(editor.getCurrentGraph());
        assertNotNull(wrapper);

        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().add(wrapper.getId());
        editor.setSelectedNodeId(wrapper.getId());
        assertTrue(editor.dissolveSelectedSubgraph());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, editor.getHistory().getUndoTopActionType());
        assertTrue(editor.undo());
        assertNotNull(findSubgraphNode(editor.getCurrentGraph()));
        assertTrue(editor.redo());
        assertFalse(editor.getCurrentGraph().getNodes().stream().anyMatch(SubgraphNode.class::isInstance));
    }

    @Test
    void opensAndClosesEmbeddedSubgraphEditor() {
        NodeGraph graph = new NodeGraph("open close");
        PassNode source = new PassNode();
        PassNode selected = new PassNode();
        PassNode sink = new PassNode();
        graph.addNode(source);
        graph.addNode(selected);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", selected.getId(), "in");
        graph.connect(selected.getId(), "out", sink.getId(), "in");

        Map<UUID, NodePosition> positions = new HashMap<>();
        positions.put(source.getId(), new NodePosition(0, 0));
        positions.put(selected.getId(), new NodePosition(120, 0));
        positions.put(sink.getId(), new NodePosition(240, 0));

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.setNodePositions(positions);
        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().add(selected.getId());
        editor.setSelectedNodeId(selected.getId());

        assertTrue(editor.createSubgraphFromSelection());
        UUID wrapperId = editor.getSelectedNodeId();
        assertTrue(editor.openSelectedSubgraph());
        assertTrue(editor.isEditingSubgraph());
        assertEquals(3, editor.getCurrentGraph().getNodes().size());

        assertTrue(editor.closeCurrentSubgraph());
        assertFalse(editor.isEditingSubgraph());
        assertEquals(graph, editor.getCurrentGraph());
        assertEquals(wrapperId, editor.getSelectedNodeId());
        assertTrue(editor.getCurrentGraph().getNode(wrapperId) instanceof SubgraphNode);
    }

    @Test
    void closingSubgraphCommitsInnerEditsAsOneParentTransaction() {
        ImGuiNodeEditor editor = prepareLinearSelectionGraph();
        editor.getHistory().clear();
        assertTrue(editor.createSubgraphFromSelection());

        SubgraphNode wrapper = findSubgraphNode(editor.getCurrentGraph());
        assertNotNull(wrapper);
        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().add(wrapper.getId());
        editor.setSelectedNodeId(wrapper.getId());
        assertTrue(editor.openSelectedSubgraph());
        assertNotNull(editor.addNode("test.pass", 400, 100));
        assertTrue(editor.closeCurrentSubgraph());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, editor.getHistory().getUndoTopActionType());
        assertEquals(5, embeddedNodeCount(findSubgraphNode(editor.getCurrentGraph())));

        assertTrue(editor.undo());
        assertEquals(4, embeddedNodeCount(findSubgraphNode(editor.getCurrentGraph())));
        assertTrue(editor.redo());
        assertEquals(5, embeddedNodeCount(findSubgraphNode(editor.getCurrentGraph())));
    }

    @Test
    void renamesSubgraphAsSingleUndoRedoTransaction() {
        ImGuiNodeEditor editor = prepareLinearSelectionGraph();
        assertTrue(editor.createSubgraphFromSelection());
        editor.getHistory().clear();

        SubgraphNode wrapper = findSubgraphNode(editor.getCurrentGraph());
        assertNotNull(wrapper);
        String originalName = wrapper.getDisplayName();

        assertTrue(editor.renameSubgraphNode(wrapper.getId(), "Castle Wall"));
        assertEquals("Castle Wall", findSubgraphNode(editor.getCurrentGraph()).getDisplayName());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, editor.getHistory().getUndoTopActionType());

        assertTrue(editor.undo());
        assertEquals(originalName, findSubgraphNode(editor.getCurrentGraph()).getDisplayName());
        assertTrue(editor.redo());
        assertEquals("Castle Wall", findSubgraphNode(editor.getCurrentGraph()).getDisplayName());
    }

    private static ImGuiNodeEditor prepareLinearSelectionGraph() {
        NodeGraph graph = new NodeGraph("transaction");
        PassNode source = new PassNode();
        PassNode selectedA = new PassNode();
        PassNode selectedB = new PassNode();
        PassNode sink = new PassNode();
        graph.addNode(source);
        graph.addNode(selectedA);
        graph.addNode(selectedB);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", selectedA.getId(), "in");
        graph.connect(selectedA.getId(), "out", selectedB.getId(), "in");
        graph.connect(selectedB.getId(), "out", sink.getId(), "in");

        Map<UUID, NodePosition> positions = new HashMap<>();
        positions.put(source.getId(), new NodePosition(0, 0));
        positions.put(selectedA.getId(), new NodePosition(100, 0));
        positions.put(selectedB.getId(), new NodePosition(220, 0));
        positions.put(sink.getId(), new NodePosition(340, 0));

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.setNodePositions(positions);
        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().add(selectedA.getId());
        editor.getSelectedNodeIds().add(selectedB.getId());
        editor.setSelectedNodeId(selectedA.getId());
        return editor;
    }

    private static SubgraphNode findSubgraphNode(NodeGraph graph) {
        return graph.getNodes().stream()
            .filter(SubgraphNode.class::isInstance)
            .map(SubgraphNode.class::cast)
            .findFirst()
            .orElse(null);
    }

    private static int embeddedNodeCount(SubgraphNode node) {
        assertNotNull(node);
        Object state = node.getNodeState();
        assertTrue(state instanceof Map<?, ?>);
        Object json = ((Map<?, ?>) state).get("embeddedGraphJson");
        assertTrue(json instanceof String);
        return GraphSerializer.fromJson((String) json).nodes.size();
    }

    private static boolean hasConnectionFrom(NodeGraph graph, UUID sourceNodeId) {
        return graph.getConnections().stream()
            .anyMatch(connection -> connection.sourceNode.getId().equals(sourceNodeId));
    }

    private static boolean hasConnectionTo(NodeGraph graph, UUID targetNodeId) {
        return graph.getConnections().stream()
            .anyMatch(connection -> connection.targetNode.getId().equals(targetNodeId));
    }

    public static final class PassNode extends BaseNode {
        public PassNode() {
            super(UUID.randomUUID(), "test.pass");
            addInputPort(new BasePort("in", "In", "input", NodeDataType.STRING, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.STRING, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }
}
