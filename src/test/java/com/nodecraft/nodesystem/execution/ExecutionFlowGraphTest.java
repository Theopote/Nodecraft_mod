package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionFlowGraphTest {

    @Test
    void analyzeDetectsNoExecEdgesOnDataOnlyGraph() {
        NodeGraph graph = new NodeGraph("data-only");
        StubNode a = new StubNode("a");
        StubNode b = new StubNode("b");
        graph.addNode(a);
        graph.addNode(b);
        graph.connect(a.getId(), "out", b.getId(), "in");

        ExecutionFlowGraph flow = ExecutionFlowGraph.analyze(graph);

        assertFalse(flow.hasExecEdges());
        assertTrue(flow.entryNodeIds().isEmpty());
    }

    @Test
    void analyzeFindsEntryNodesAndSuccessors() {
        NodeGraph graph = new NodeGraph("exec-chain");
        ExecNode entry = new ExecNode("entry");
        ExecNode middle = new ExecNode("middle");
        ExecNode tail = new ExecNode("tail");
        graph.addNode(entry);
        graph.addNode(middle);
        graph.addNode(tail);
        graph.connect(entry.getId(), "exec_out", middle.getId(), "exec_in");
        graph.connect(middle.getId(), "exec_out", tail.getId(), "exec_in");

        ExecutionFlowGraph flow = ExecutionFlowGraph.analyze(graph);

        assertTrue(flow.hasExecEdges());
        assertEquals(1, flow.entryNodeIds().size());
        assertTrue(flow.entryNodeIds().contains(entry.getId()));
        assertTrue(flow.execSuccessors(entry.getId()).contains(middle.getId()));
        assertTrue(flow.execSuccessors(entry.getId(), "exec_out").contains(middle.getId()));
        assertEquals(3, flow.reachableExecNodeIds().size());
    }

    @Test
    void execSuccessorsAreScopedToSourcePort() {
        NodeGraph graph = new NodeGraph("exec-port-scope");
        ExecForkNode fork = new ExecForkNode("fork");
        ExecNode trueSink = new ExecNode("true-sink");
        ExecNode falseSink = new ExecNode("false-sink");
        graph.addNode(fork);
        graph.addNode(trueSink);
        graph.addNode(falseSink);
        graph.connect(fork.getId(), "exec_true", trueSink.getId(), "exec_in");
        graph.connect(fork.getId(), "exec_false", falseSink.getId(), "exec_in");

        ExecutionFlowGraph flow = ExecutionFlowGraph.analyze(graph);

        assertEquals(Set.of(trueSink.getId()), flow.execSuccessors(fork.getId(), "exec_true"));
        assertEquals(Set.of(falseSink.getId()), flow.execSuccessors(fork.getId(), "exec_false"));
    }

    private static final class ExecForkNode extends BaseNode {
        private ExecForkNode(String suffix) {
            super(UUID.randomUUID(), "test.exec.fork." + suffix);
            addOutputPort(new BasePort("exec_true", "Exec True", "output", NodeDataType.EXEC, this));
            addOutputPort(new BasePort("exec_false", "Exec False", "output", NodeDataType.EXEC, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("exec_true", Boolean.TRUE);
        }
    }

    private static final class StubNode extends BaseNode {
        private StubNode(String suffix) {
            super(UUID.randomUUID(), "test.stub." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", "value");
        }
    }

    private static final class ExecNode extends BaseNode {
        private ExecNode(String suffix) {
            super(UUID.randomUUID(), "test.exec." + suffix);
            addInputPort(new BasePort("exec_in", "Exec In", "input", NodeDataType.EXEC, this, false, false));
            addOutputPort(new BasePort("exec_out", "Exec Out", "output", NodeDataType.EXEC, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("exec_out", Boolean.TRUE);
        }
    }
}
