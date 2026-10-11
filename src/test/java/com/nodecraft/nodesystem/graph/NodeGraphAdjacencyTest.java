package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGraphAdjacencyTest {

    @Test
    void indexesIncomingAndOutgoingConnections() {
        NodeGraph graph = new NodeGraph("adjacency");
        TestNode source = new TestNode("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);
        graph.connect(source.getId(), "out", target.getId(), "in");

        assertEquals(1, graph.getIncomingConnections(target.getId()).size());
        assertEquals(1, graph.getOutgoingConnections(source.getId()).size());
        assertTrue(graph.getIncomingConnections(source.getId()).isEmpty());
        assertTrue(graph.getOutgoingConnections(target.getId()).isEmpty());
        assertEquals(source.getId(), graph.getConnectedOutputNodeId(target.getId(), "in"));
        assertEquals("out", graph.getConnectedOutputPortId(target.getId(), "in"));
    }

    @Test
    void unindexesConnectionsOnRemoveAndNodeDelete() {
        NodeGraph graph = new NodeGraph("remove");
        TestNode source = new TestNode("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);
        graph.connect(source.getId(), "out", target.getId(), "in");

        NodeGraph.Connection connection = graph.getOutgoingConnections(source.getId()).getFirst();
        graph.removeConnection(connection);

        assertTrue(graph.getIncomingConnections(target.getId()).isEmpty());
        assertTrue(graph.getOutgoingConnections(source.getId()).isEmpty());
        assertNull(graph.getConnectedOutputNodeId(target.getId(), "in"));

        graph.connect(source.getId(), "out", target.getId(), "in");
        graph.removeNode(target.getId());

        assertTrue(graph.getConnections().isEmpty());
        assertTrue(graph.getOutgoingConnections(source.getId()).isEmpty());
    }

    @Test
    void downstreamTraversalUsesOutgoingIndex() {
        NodeGraph graph = new NodeGraph("downstream");
        TestNode a = new TestNode("a");
        TestNode b = new TestNode("b");
        TestNode c = new TestNode("c");
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.connect(a.getId(), "out", b.getId(), "in");
        graph.connect(b.getId(), "out", c.getId(), "in");

        assertEquals(Set.of(b.getId(), c.getId()), graph.getDownstreamNodeIds(a.getId()));
        assertEquals(Set.of(c.getId()), graph.getDownstreamNodeIds(b.getId()));
        assertTrue(graph.getDownstreamNodeIds(c.getId()).isEmpty());
    }

    @Test
    void dirtyImpactUsesIndexedTraversal() {
        NodeGraph graph = new NodeGraph("dirty-impact");
        TestNode a = new TestNode("a");
        TestNode b = new TestNode("b");
        graph.addNode(a);
        graph.addNode(b);
        graph.connect(a.getId(), "out", b.getId(), "in");

        assertEquals(Set.of(a.getId(), b.getId()), graph.getDirtyImpactNodeIds(a.getId()));
    }

    @Test
    void canConnectFalseForExactExistingEdge() {
        NodeGraph graph = new NodeGraph("exact-edge");
        TestNode source = new TestNode("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);
        assertTrue(graph.connect(source.getId(), "out", target.getId(), "in"));

        assertFalse(graph.canConnect(source.getId(), "out", target.getId(), "in"));
    }

    @Test
    void connectIsIdempotentForExactEdge() {
        NodeGraph graph = new NodeGraph("idempotent");
        TestNode source = new TestNode("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);

        assertTrue(graph.connect(source.getId(), "out", target.getId(), "in"));
        assertTrue(graph.connect(source.getId(), "out", target.getId(), "in"));
        assertEquals(1, graph.getConnections().size());
    }

    @Test
    void occupiedSingleInputRejectsSecondSource() {
        NodeGraph graph = new NodeGraph("occupied-input");
        TestNode a = new TestNode("a");
        TestNode b = new TestNode("b");
        TestNode target = new TestNode("target");
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(target);

        assertTrue(graph.connect(a.getId(), "out", target.getId(), "in"));
        assertFalse(graph.canConnect(b.getId(), "out", target.getId(), "in"));
        assertFalse(graph.connect(b.getId(), "out", target.getId(), "in"));
        assertEquals(1, graph.getConnections().size());
    }

    @Test
    void multiIncomingPortAllowsDistinctSources() {
        NodeGraph graph = new NodeGraph("multi-in");
        TestNode a = new TestNode("a");
        TestNode b = new TestNode("b");
        MultiInNode target = new MultiInNode("target");
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(target);

        assertTrue(graph.connect(a.getId(), "out", target.getId(), "in"));
        assertTrue(graph.canConnect(b.getId(), "out", target.getId(), "in"));
        assertTrue(graph.connect(b.getId(), "out", target.getId(), "in"));
        assertEquals(2, graph.getConnections().size());
    }

    @Test
    void incompatibleTypesCannotConnect() {
        NodeGraph graph = new NodeGraph("type-mismatch");
        TypedNode intOut = new TypedNode("int", NodeDataType.INTEGER, NodeDataType.INTEGER);
        TypedNode floatIn = new TypedNode("float", NodeDataType.FLOAT, NodeDataType.FLOAT);
        graph.addNode(intOut);
        graph.addNode(floatIn);

        assertFalse(graph.canConnect(intOut.getId(), "out", floatIn.getId(), "in"));
        assertFalse(graph.connect(intOut.getId(), "out", floatIn.getId(), "in"));
        assertTrue(graph.getConnections().isEmpty());
    }

    @Test
    void dataCycleCanBeWired() {
        NodeGraph graph = new NodeGraph("data-cycle-wire");
        TestNode a = new TestNode("a");
        TestNode b = new TestNode("b");
        graph.addNode(a);
        graph.addNode(b);

        assertTrue(graph.connect(a.getId(), "out", b.getId(), "in"));
        assertTrue(graph.canConnect(b.getId(), "out", a.getId(), "in"));
        assertTrue(graph.connect(b.getId(), "out", a.getId(), "in"));
        assertEquals(2, graph.getConnections().size());
    }

    private static final class TestNode extends BaseNode {
        private TestNode(String suffix) {
            super(UUID.randomUUID(), "test.adjacency." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }

    private static final class MultiInNode extends BaseNode {
        private MultiInNode(String suffix) {
            super(UUID.randomUUID(), "test.adjacency.multi." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this, true));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }

    private static final class TypedNode extends BaseNode {
        private TypedNode(String suffix, NodeDataType inType, NodeDataType outType) {
            super(UUID.randomUUID(), "test.adjacency.typed." + suffix);
            addInputPort(new BasePort("in", "In", "input", inType, this));
            addOutputPort(new BasePort("out", "Out", "output", outType, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }
}
