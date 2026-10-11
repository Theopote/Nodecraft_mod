package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphExecutionPlannerTest {

    @Test
    void buildsParallelLevelsForDiamondGraph() {
        NodeGraph graph = new NodeGraph("diamond");
        TestNode source = new TestNode("source");
        TestNode left = new TestNode("left");
        TestNode right = new TestNode("right");
        TestNode sink = new TestNode("sink");
        graph.addNode(source);
        graph.addNode(left);
        graph.addNode(right);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", left.getId(), "in");
        graph.connect(source.getId(), "out", right.getId(), "in");
        graph.connect(left.getId(), "out", sink.getId(), "in");
        graph.connect(right.getId(), "out", sink.getId(), "in");

        GraphExecutionPlanner.ExecutionPlan plan = GraphExecutionPlanner.plan(graph);

        assertFalse(plan.hasCycle());
        assertEquals(4, plan.topologicalOrder().size());
        assertEquals(3, plan.levels().size());
        assertEquals(2, plan.maxParallelWidth());
        assertEquals(source.getId(), plan.levels().get(0).getFirst().getId());
        assertEquals(sink.getId(), plan.levels().get(2).getFirst().getId());
        assertEquals(
            Set.of(left.getId(), right.getId()),
            Set.of(
                plan.levels().get(1).get(0).getId(),
                plan.levels().get(1).get(1).getId()
            )
        );
    }

    @Test
    void detectsCycles() {
        NodeGraph graph = new NodeGraph("cycle");
        TestNode nodeA = new TestNode("a");
        TestNode nodeB = new TestNode("b");
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        // Connect-time allows data cycles; planner rejects them at execute/plan.
        assertTrue(graph.connect(nodeA.getId(), "out", nodeB.getId(), "in"));
        assertTrue(graph.connect(nodeB.getId(), "out", nodeA.getId(), "in"));
        assertEquals(2, graph.getConnections().size());

        GraphExecutionPlanner.ExecutionPlan plan = GraphExecutionPlanner.plan(graph);

        assertTrue(plan.hasCycle());
        assertTrue(plan.topologicalOrder().isEmpty());
        assertTrue(plan.levels().isEmpty());
    }

    @Test
    void execCycleDoesNotCountAsDataCycle() {
        NodeGraph graph = new NodeGraph("exec-cycle-plan");
        ExecNode nodeA = new ExecNode("a");
        ExecNode nodeB = new ExecNode("b");
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        assertTrue(graph.connect(nodeA.getId(), "exec_out", nodeB.getId(), "exec_in"));
        assertTrue(graph.connect(nodeB.getId(), "exec_out", nodeA.getId(), "exec_in"));

        GraphExecutionPlanner.ExecutionPlan plan = GraphExecutionPlanner.plan(graph);

        assertFalse(plan.hasCycle(), "exec back-edges are ignored by data topo");
        assertEquals(2, plan.topologicalOrder().size());
    }

    private static final class TestNode extends BaseNode {
        private TestNode(String suffix) {
            super(UUID.randomUUID(), "test.planner." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }

    private static final class ExecNode extends BaseNode {
        private ExecNode(String suffix) {
            super(UUID.randomUUID(), "test.planner.exec." + suffix);
            addInputPort(new BasePort("exec_in", "Exec In", "input", NodeDataType.EXEC, this, false, false));
            addOutputPort(new BasePort("exec_out", "Exec Out", "output", NodeDataType.EXEC, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("exec_out", Boolean.TRUE);
        }
    }
}
