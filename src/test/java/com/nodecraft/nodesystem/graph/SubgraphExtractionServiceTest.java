package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedNode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubgraphExtractionServiceTest {

    @Test
    void extractsSelectedNodesWithGraphInputAndOutputBoundaries() {
        NodeGraph graph = new NodeGraph("castle wall");
        TestNode source = new TestNode("source");
        TestNode selectedA = new TestNode("selected_a");
        TestNode selectedB = new TestNode("selected_b");
        TestNode sink = new TestNode("sink");

        graph.addNode(source);
        graph.addNode(selectedA);
        graph.addNode(selectedB);
        graph.addNode(sink);

        graph.connect(source.getId(), "out", selectedA.getId(), "in");
        graph.connect(selectedA.getId(), "out", selectedB.getId(), "in");
        graph.connect(selectedB.getId(), "out", sink.getId(), "in");

        SubgraphExtractionService.ExtractionResult result = SubgraphExtractionService.extract(
                graph,
                Set.of(selectedA.getId(), selectedB.getId()),
                "Wall Segment"
        );

        assertEquals("Wall Segment", result.savedGraph().graphName);
        assertEquals(4, result.savedGraph().nodes.size());
        assertEquals(3, result.savedGraph().connections.size());
        assertEquals(1, result.inputBindings().size());
        assertEquals(1, result.outputBindings().size());
        assertEquals(1, result.inputKeys().size());
        assertEquals(1, result.outputKeys().size());

        Set<String> typeIds = result.savedGraph().nodes.stream().map(node -> node.typeId).collect(Collectors.toSet());
        assertTrue(typeIds.contains("test.selected_a"));
        assertTrue(typeIds.contains("test.selected_b"));
        assertTrue(typeIds.contains(SubgraphExtractionService.GRAPH_INPUT_TYPE_ID));
        assertTrue(typeIds.contains(SubgraphExtractionService.GRAPH_OUTPUT_TYPE_ID));

        SavedNode graphInput = findNodeByType(result, SubgraphExtractionService.GRAPH_INPUT_TYPE_ID);
        SavedNode graphOutput = findNodeByType(result, SubgraphExtractionService.GRAPH_OUTPUT_TYPE_ID);
        assertNotNull(graphInput);
        assertNotNull(graphOutput);
        assertEquals(result.inputKeys().getFirst(), ((Map<?, ?>) graphInput.state).get("inputName"));
        assertEquals(result.outputKeys().getFirst(), ((Map<?, ?>) graphOutput.state).get("outputName"));

        assertConnection(result, graphInput.nodeId, "output_value", selectedA.getId().toString(), "in");
        assertConnection(result, selectedA.getId().toString(), "out", selectedB.getId().toString(), "in");
        assertConnection(result, selectedB.getId().toString(), "out", graphOutput.nodeId, "input_value");

        SubgraphExtractionService.InputBinding input = result.inputBindings().getFirst();
        assertEquals(source.getId(), input.externalSourceNodeId());
        assertEquals(selectedA.getId(), input.internalTargetNodeId());
        assertEquals("string", input.externalType());
        assertEquals("string", input.internalType());

        SubgraphExtractionService.OutputBinding output = result.outputBindings().getFirst();
        assertEquals(selectedB.getId(), output.internalSourceNodeId());
        assertEquals(sink.getId(), output.externalTargetNodeId());
        assertEquals("string", output.internalType());
        assertEquals("string", output.externalType());
    }

    @Test
    void rejectsEmptySelection() {
        NodeGraph graph = new NodeGraph("empty");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SubgraphExtractionService.extract(graph, Set.of(), "Nope"));

        assertTrue(error.getMessage().contains("empty"));
    }

    @Test
    void ignoresIdsThatAreNotInGraphButRequiresAtLeastOneSelectedNode() {
        NodeGraph graph = new NodeGraph("empty");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SubgraphExtractionService.extract(graph, Set.of(UUID.randomUUID()), "Nope"));

        assertTrue(error.getMessage().contains("does not contain"));
    }

    private static SavedNode findNodeByType(SubgraphExtractionService.ExtractionResult result, String typeId) {
        return result.savedGraph().nodes.stream()
                .filter(node -> typeId.equals(node.typeId))
                .findFirst()
                .orElse(null);
    }

    private static void assertConnection(SubgraphExtractionService.ExtractionResult result,
                                         String sourceNodeId,
                                         String sourcePortId,
                                         String targetNodeId,
                                         String targetPortId) {
        boolean found = false;
        for (SavedConnection connection : result.savedGraph().connections) {
            if (sourceNodeId.equals(connection.sourceNodeId)
                    && sourcePortId.equals(connection.sourcePortId)
                    && targetNodeId.equals(connection.targetNodeId)
                    && targetPortId.equals(connection.targetPortId)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected connection " + sourceNodeId + ":" + sourcePortId + " -> " + targetNodeId + ":" + targetPortId);
    }

    private static final class TestNode extends BaseNode {
        private TestNode(String id) {
            super(UUID.randomUUID(), "test." + id);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.STRING, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.STRING, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }
}
