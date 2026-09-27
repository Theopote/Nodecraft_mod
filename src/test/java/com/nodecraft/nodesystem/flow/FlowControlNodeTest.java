package com.nodecraft.nodesystem.flow;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.flow.control.BranchNode;
import com.nodecraft.nodesystem.nodes.flow.control.DoOnceNode;
import com.nodecraft.nodesystem.nodes.flow.control.SequenceNode;
import com.nodecraft.nodesystem.nodes.flow.loop.ForEachLoopNode;
import com.nodecraft.nodesystem.nodes.flow.loop.WhileLoopNode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowControlNodeTest {

    @Test
    void branchRoutesSignalToTrueOutputWhenConditionIsTrue() {
        BranchNode branch = new BranchNode();
        Map<String, Object> outputs = branch.compute(Map.of(
            "input_condition", true,
            "input_signal", "payload"
        ));

        assertEquals("payload", outputs.get("output_true"));
        assertNull(outputs.get("output_false"));
    }

    @Test
    void branchRoutesSignalToFalseOutputWhenConditionIsFalse() {
        BranchNode branch = new BranchNode();
        Map<String, Object> outputs = branch.compute(Map.of(
            "input_condition", false,
            "input_signal", 99
        ));

        assertNull(outputs.get("output_true"));
        assertEquals(99, outputs.get("output_false"));
    }

    @Test
    void branchRoutesExecWhenSignalIsMissing() {
        BranchNode branch = new BranchNode();
        Map<String, Object> outputs = branch.compute(Map.of("input_condition", true));

        assertEquals(Boolean.TRUE, outputs.get("output_valid"));
        assertEquals(Set.of("exec_true"), branch.getActiveExecOutputPortIds());
        assertNull(outputs.get("output_true"));
        assertNull(outputs.get("output_false"));
    }

    @Test
    void sequenceReplicatesSignalToActiveStepsInOnePass() {
        SequenceNode sequence = new SequenceNode();
        Map<String, Object> outputs = sequence.compute(Map.of(
            "input_signal", "go",
            "input_step_count", 3
        ));

        assertEquals("go", outputs.get("output_step_1"));
        assertEquals("go", outputs.get("output_step_2"));
        assertEquals("go", outputs.get("output_step_3"));
        assertNull(outputs.get("output_step_4"));
        assertEquals(3, outputs.get("output_active_step_count"));
        assertEquals(List.of(1, 2, 3), outputs.get("output_active_steps"));
    }

    @Test
    void forEachReportsCountAndValidForList() {
        ForEachLoopNode forEach = new ForEachLoopNode();
        Map<String, Object> outputs = forEach.compute(Map.of(
            "input_list", List.of("a", "b", "c")
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(3, outputs.get("output_count"));
        assertEquals(3, forEach.execLoopIterationCount());
        assertTrue(forEach.shouldFireExecComplete());
    }

    @Test
    void forEachReturnsEmptyWhenDisabled() {
        ForEachLoopNode forEach = new ForEachLoopNode();
        Map<String, Object> outputs = forEach.compute(Map.of(
            "input_list", List.of("a", "b"),
            "input_enabled", false
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_count"));
        assertEquals(0, forEach.execLoopIterationCount());
        assertTrue(forEach.shouldFireExecComplete());
    }

    @Test
    void doOnceBlocksSecondPassWithinSameExecutionRun() {
        DoOnceNode gate = new DoOnceNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new com.nodecraft.nodesystem.execution.ExecutionRunGuard());

        Map<String, Object> first = gate.compute(Map.of("input_signal", "once"), context);
        assertEquals("once", first.get("output_first_pass"));
        assertEquals(true, first.get("output_did_execute"));

        Map<String, Object> second = gate.compute(Map.of("input_signal", "once"), context);
        assertNull(second.get("output_first_pass"));
        assertEquals("once", second.get("output_blocked"));
        assertEquals(false, second.get("output_did_execute"));
        assertEquals(true, second.get("output_has_executed"));
    }

    @Test
    void sequenceFiresExecStepsInOrder() {
        SequenceNode sequence = new SequenceNode();
        sequence.compute(Map.of(
                "input_signal", "go",
                "input_step_count", 3
        ));

        assertEquals(List.of("exec_step_1", "exec_step_2", "exec_step_3"), List.copyOf(sequence.getActiveExecOutputPortIds()));
        assertEquals(Boolean.TRUE, sequence.getOutput(SequenceNode.execStepPortId(1)));
        assertNull(sequence.getOutput(SequenceNode.execStepPortId(4)));
    }

    @Test
    void doOnceRoutesExecOutputsByGateState() {
        DoOnceNode gate = new DoOnceNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new com.nodecraft.nodesystem.execution.ExecutionRunGuard());

        gate.compute(Map.of("input_signal", "once"), context);

        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());
        assertEquals(Boolean.TRUE, gate.getOutput("exec_out"));

        gate.compute(Map.of("input_signal", "once"), context);
        assertEquals(Set.of("exec_blocked"), gate.getActiveExecOutputPortIds());
        assertEquals(Boolean.TRUE, gate.getOutput("exec_blocked"));
    }

    @Test
    void whileRoutesExecOutputsByCondition() {
        WhileLoopNode whileLoop = new WhileLoopNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new com.nodecraft.nodesystem.execution.ExecutionRunGuard());

        whileLoop.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), whileLoop.getActiveExecOutputPortIds());

        whileLoop.compute(Map.of("input_condition", false), context);
        assertEquals(Set.of("exec_complete"), whileLoop.getActiveExecOutputPortIds());
    }

    @Test
    void whileStopsExecBodyAfterMaxIterations() {
        WhileLoopNode whileLoop = new WhileLoopNode();
        whileLoop.setNodeState(Map.of("maxIterations", 2));
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new com.nodecraft.nodesystem.execution.ExecutionRunGuard());

        whileLoop.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), whileLoop.getActiveExecOutputPortIds());
        assertEquals(1, whileLoop.getOutput("output_iterations"));

        whileLoop.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), whileLoop.getActiveExecOutputPortIds());
        assertEquals(2, whileLoop.getOutput("output_iterations"));

        whileLoop.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_complete"), whileLoop.getActiveExecOutputPortIds());
        assertEquals(true, whileLoop.getOutput("output_hit_limit"));
        assertEquals(2, whileLoop.getOutput("output_iterations"));

        // New run guard resets the counter.
        context.setSharedExecutionRunGuard(new com.nodecraft.nodesystem.execution.ExecutionRunGuard());
        whileLoop.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), whileLoop.getActiveExecOutputPortIds());
        assertEquals(1, whileLoop.getOutput("output_iterations"));
    }

    @Test
    void forEachExposesPerItemOutputsDuringExecLoop() {
        ForEachLoopNode forEach = new ForEachLoopNode();
        forEach.compute(Map.of("input_list", List.of("a", "b")));

        forEach.prepareExecLoopIteration(0);
        assertEquals("a", forEach.getOutput("output_item"));
        assertEquals(0, forEach.getOutput("output_index"));

        forEach.prepareExecLoopIteration(1);
        assertEquals("b", forEach.getOutput("output_item"));
        assertEquals(1, forEach.getOutput("output_index"));
    }

    @Test
    void branchRoutesExecOutputsByCondition() {
        BranchNode branch = new BranchNode();
        branch.compute(Map.of(
                "input_condition", true,
                "input_signal", "payload"
        ));

        assertEquals(Set.of("exec_true"), branch.getActiveExecOutputPortIds());
        assertEquals(Boolean.TRUE, branch.getOutput("exec_true"));
        assertNull(branch.getOutput("exec_false"));
    }

    @Test
    void branchDoesNotSkipEitherDownstreamNodeInExecutor() {
        NodeGraph graph = new NodeGraph("branch-dataflow");
        PassThroughNode condition = new PassThroughNode("condition", Boolean.TRUE);
        PassThroughNode signal = new PassThroughNode("signal", "payload");
        BranchNode branch = new BranchNode();
        CaptureNode trueSink = new CaptureNode("true_sink");
        CaptureNode falseSink = new CaptureNode("false_sink");

        graph.addNode(condition);
        graph.addNode(signal);
        graph.addNode(branch);
        graph.addNode(trueSink);
        graph.addNode(falseSink);

        assertTrue(graph.connect(condition.getId(), "out", branch.getId(), "input_condition"));
        assertTrue(graph.connect(signal.getId(), "out", branch.getId(), "input_signal"));
        assertTrue(graph.connect(branch.getId(), "output_true", trueSink.getId(), "in"));
        assertTrue(graph.connect(branch.getId(), "output_false", falseSink.getId(), "in"));

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals("payload", trueSink.getOutput("out"));
        assertNull(falseSink.getOutput("out"));
        assertTrue(trueSink.wasExecuted());
        assertTrue(falseSink.wasExecuted());
    }

    private static final class PassThroughNode extends BaseNode {
        private final Object payload;

        private PassThroughNode(String suffix, Object payload) {
            super(java.util.UUID.randomUUID(), "test.pass." + suffix);
            this.payload = payload;
            addOutputPort(new BasePort("out", "Out", "output", inferOutputType(payload), this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            outputValues.put("out", payload);
        }
    }

    private static NodeDataType inferOutputType(@Nullable Object payload) {
        if (payload instanceof Boolean) {
            return NodeDataType.BOOLEAN;
        }
        if (payload instanceof Integer) {
            return NodeDataType.INTEGER;
        }
        if (payload instanceof Number) {
            return NodeDataType.DOUBLE;
        }
        if (payload instanceof String) {
            return NodeDataType.STRING;
        }
        if (payload instanceof java.util.List<?>) {
            return NodeDataType.LIST;
        }
        return NodeDataType.ANY;
    }

    private static final class CaptureNode extends BaseNode {
        private boolean executed;

        private CaptureNode(String suffix) {
            super(java.util.UUID.randomUUID(), "test.capture." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executed = true;
            outputValues.put("out", inputValues.get("in"));
        }

        boolean wasExecuted() {
            return executed;
        }
    }
}
