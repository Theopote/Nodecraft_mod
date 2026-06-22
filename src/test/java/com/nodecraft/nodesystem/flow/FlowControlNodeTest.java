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
    void branchClearsBothOutputsWhenSignalIsMissing() {
        BranchNode branch = new BranchNode();
        Map<String, Object> outputs = branch.compute(Map.of("input_condition", true));

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
    void forEachExpandsListIntoItemsIndicesAndPairs() {
        ForEachLoopNode forEach = new ForEachLoopNode();
        Map<String, Object> outputs = forEach.compute(Map.of(
            "input_list", List.of("a", "b", "c")
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(3, outputs.get("output_count"));
        assertEquals(List.of("a", "b", "c"), outputs.get("output_items"));
        assertEquals(List.of(0, 1, 2), outputs.get("output_indices"));
        assertEquals("a", outputs.get("output_first_item"));
        assertEquals("c", outputs.get("output_last_item"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pairs = (List<Map<String, Object>>) outputs.get("output_pairs");
        assertEquals(3, pairs.size());
        assertEquals(1, pairs.get(1).get("index"));
        assertEquals("b", pairs.get(1).get("item"));
    }

    @Test
    void forEachReturnsEmptyOutputsWhenDisabled() {
        ForEachLoopNode forEach = new ForEachLoopNode();
        Map<String, Object> outputs = forEach.compute(Map.of(
            "input_list", List.of("a", "b"),
            "input_enabled", false
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_count"));
        assertEquals(List.of(), outputs.get("output_items"));
    }

    @Test
    void doOnceBlocksSecondPassWithinSameExecutionContext() {
        DoOnceNode gate = new DoOnceNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);

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
        gate.compute(Map.of("input_signal", "once"));

        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());
        assertEquals(Boolean.TRUE, gate.getOutput("exec_out"));

        gate.compute(Map.of("input_signal", "once"));
        assertEquals(Set.of("exec_blocked"), gate.getActiveExecOutputPortIds());
        assertEquals(Boolean.TRUE, gate.getOutput("exec_blocked"));
    }

    @Test
    void whileRoutesExecOutputsByCondition() {
        WhileLoopNode whileLoop = new WhileLoopNode();
        whileLoop.compute(Map.of("input_condition", true));
        assertEquals(Set.of("exec_body"), whileLoop.getActiveExecOutputPortIds());

        whileLoop.compute(Map.of("input_condition", false));
        assertEquals(Set.of("exec_complete"), whileLoop.getActiveExecOutputPortIds());
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

        graph.connect(condition.getId(), "out", branch.getId(), "input_condition");
        graph.connect(signal.getId(), "out", branch.getId(), "input_signal");
        graph.connect(branch.getId(), "output_true", trueSink.getId(), "in");
        graph.connect(branch.getId(), "output_false", falseSink.getId(), "in");

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
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            outputValues.put("out", payload);
        }
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
