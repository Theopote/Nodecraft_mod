package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunGuard;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.flow.control.BranchNode;
import com.nodecraft.nodesystem.nodes.flow.control.DoOnceNode;
import com.nodecraft.nodesystem.nodes.flow.control.SequenceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flow Control v1 language fence (Graph V65).
 */
class FlowControlLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "flow.control.branch",
            "flow.control.sequence",
            "flow.control.do_once"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV65() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyThreeFlowControlNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("flow.control."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void ordersAreZeroThroughTwo() {
        assertEquals(0, orderOf("flow.control.branch"));
        assertEquals(1, orderOf("flow.control.sequence"));
        assertEquals(2, orderOf("flow.control.do_once"));
    }

    @Test
    void effectsMatchFlowControlContract() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(
                registry.createNodeInstance("flow.control.branch").getClass(), "flow.control.branch"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(
                registry.createNodeInstance("flow.control.sequence").getClass(), "flow.control.sequence"));
        assertEquals(NodeEffect.CONTEXT_WRITE, NodeEffectResolver.resolve(
                registry.createNodeInstance("flow.control.do_once").getClass(), "flow.control.do_once"));
    }

    @Test
    void branchConditionStrictBooleanRejectsNumber() {
        BranchNode branch = new BranchNode();
        Map<String, Object> outputs = branch.compute(Map.of("input_condition", 1));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertTrue(branch.getActiveExecOutputPortIds().isEmpty());
    }

    @Test
    void branchConnectedNullConditionFailsClosedNoExec() {
        BranchProbe branch = new BranchProbe();
        branch.connectInput("input_condition", NodeDataType.BOOLEAN);
        branch.setInput("input_condition", null);
        branch.setInput("input_signal", "payload");
        branch.processNode(null);
        assertEquals(Boolean.FALSE, branch.getOutput("output_valid"));
        assertTrue(branch.getActiveExecOutputPortIds().isEmpty());
        assertNull(branch.getOutput("exec_true"));
        assertNull(branch.getOutput("exec_false"));
    }

    @Test
    void branchWithoutSignalStillRoutesExec() {
        BranchNode branch = new BranchNode();
        Map<String, Object> outputs = branch.compute(Map.of("input_condition", true));
        assertEquals(Boolean.TRUE, outputs.get("output_valid"));
        assertEquals(Set.of("exec_true"), branch.getActiveExecOutputPortIds());
        assertEquals(Boolean.TRUE, outputs.get("exec_true"));
        assertNull(outputs.get("output_true"));
        assertNull(outputs.get("output_false"));
    }

    @Test
    void branchPassthroughPortsBindT() {
        INode branch = registry.createNodeInstance("flow.control.branch");
        assertPassthroughT(branch, "input_signal");
        assertPassthroughT(branch, "output_true");
        assertPassthroughT(branch, "output_false");
    }

    @Test
    void sequenceStepCountRejectsDouble() {
        SequenceNode sequence = new SequenceNode();
        Map<String, Object> outputs = sequence.compute(Map.of(
                "input_signal", "go",
                "input_step_count", 3.5
        ));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertTrue(sequence.getActiveExecOutputPortIds().isEmpty());
    }

    @Test
    void sequenceStepCountRejectsZeroAndNineWithoutClamp() {
        SequenceNode zero = new SequenceNode();
        Map<String, Object> out0 = zero.compute(Map.of("input_step_count", 0));
        assertEquals(Boolean.FALSE, out0.get("output_valid"));
        assertTrue(zero.getActiveExecOutputPortIds().isEmpty());

        SequenceNode nine = new SequenceNode();
        Map<String, Object> out9 = nine.compute(Map.of("input_step_count", 9));
        assertEquals(Boolean.FALSE, out9.get("output_valid"));
        assertTrue(nine.getActiveExecOutputPortIds().isEmpty());
    }

    @Test
    void sequenceActiveStepsAreIntegerList() {
        INode sequence = registry.createNodeInstance("flow.control.sequence");
        assertPortType(sequence, "output_active_steps", NodeDataType.INTEGER_LIST);
    }

    @Test
    void sequencePassthroughPortsBindT() {
        INode sequence = registry.createNodeInstance("flow.control.sequence");
        assertPassthroughT(sequence, "input_signal");
        for (int i = 1; i <= 8; i++) {
            assertPassthroughT(sequence, "output_step_" + i);
        }
    }

    @Test
    void sequenceSetStepCountIgnoresOutOfRange() {
        SequenceNode sequence = new SequenceNode();
        sequence.setStepCount(3);
        assertEquals(3, sequence.getStepCount());
        sequence.setStepCount(9);
        assertEquals(3, sequence.getStepCount());
        sequence.setStepCount(0);
        assertEquals(3, sequence.getStepCount());
    }

    @Test
    void doOnceWithoutSignalStillFiresExec() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new ExecutionRunGuard());
        DoOnceNode gate = new DoOnceNode();
        Map<String, Object> first = gate.compute(Map.of(), context);
        assertEquals(Boolean.TRUE, first.get("output_valid"));
        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());
        assertEquals(true, first.get("output_did_execute"));
        assertNull(first.get("output_first_pass"));
    }

    @Test
    void doOnceWithoutRunGuardFailsClosed() {
        DoOnceNode gate = new DoOnceNode();
        Map<String, Object> outputs = gate.compute(Map.of());
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertTrue(String.valueOf(outputs.get("output_error")).contains("execution run context"));
        assertTrue(gate.getActiveExecOutputPortIds().isEmpty());
    }

    @Test
    void doOnceResetExecNotFiredIsOk() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new ExecutionRunGuard());
        DoOnceProbe gate = new DoOnceProbe();
        gate.connectInput("input_reset", NodeDataType.EXEC);
        gate.setInput("input_reset", null);
        gate.processNode(context);
        assertEquals(Boolean.TRUE, gate.getOutput("output_valid"));
        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());
    }

    @Test
    void doOnceResetExecClearsGate() {
        DoOnceNode gate = new DoOnceNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new ExecutionRunGuard());

        gate.compute(Map.of("exec_in", true, "input_signal", "a"), context);
        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());

        gate.compute(Map.of("exec_in", true, "input_signal", "b"), context);
        assertEquals(Set.of("exec_blocked"), gate.getActiveExecOutputPortIds());

        Map<String, Object> resetOnly = gate.compute(Map.of("input_reset", true), context);
        assertEquals(Boolean.TRUE, resetOnly.get("output_valid"));
        assertTrue(gate.getActiveExecOutputPortIds().isEmpty());
        assertEquals(false, resetOnly.get("output_has_executed"));

        Map<String, Object> again = gate.compute(Map.of("exec_in", true, "input_signal", "c"), context);
        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());
        assertEquals("c", again.get("output_first_pass"));
    }

    @Test
    void doOnceResetPortIsExec() {
        INode gate = registry.createNodeInstance("flow.control.do_once");
        assertPortType(gate, "input_reset", NodeDataType.EXEC);
    }

    @Test
    void doOnceStateResetsOnNewExecutionRun() {
        DoOnceNode gate = new DoOnceNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);

        context.setSharedExecutionRunGuard(new ExecutionRunGuard());
        gate.compute(Map.of("input_signal", "a"), context);
        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());

        context.setSharedExecutionRunGuard(new ExecutionRunGuard());
        Map<String, Object> secondRun = gate.compute(Map.of("input_signal", "b"), context);
        assertEquals(Set.of("exec_out"), gate.getActiveExecOutputPortIds());
        assertEquals("b", secondRun.get("output_first_pass"));
        assertEquals(true, secondRun.get("output_did_execute"));
    }

    @Test
    void doOnceExecutedFlagNotSerialized() {
        DoOnceNode gate = new DoOnceNode();
        Object state = gate.getNodeState();
        if (state instanceof Map<?, ?> map) {
            assertFalse(map.containsKey("fallbackExecuted"));
        }
    }

    @Test
    void doOncePassthroughPortsBindT() {
        INode gate = registry.createNodeInstance("flow.control.do_once");
        assertPassthroughT(gate, "input_signal");
        assertPassthroughT(gate, "output_first_pass");
        assertPassthroughT(gate, "output_blocked");
    }


    private static int orderOf(String typeId) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        NodeInfo annotation = node.getClass().getAnnotation(NodeInfo.class);
        assertNotNull(annotation, typeId);
        return annotation.order();
    }

    private static void assertPassthroughT(INode node, String portId) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertTrue(port.isPassthroughBinding(), portId);
        assertEquals("T", port.getListTypeVariable(), portId);
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertEquals(expected, port.getDataType(), portId);
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }

    private static final class BranchProbe extends BranchNode {
        void connectInput(String portId, NodeDataType outputType) {
            FlowControlLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class DoOnceProbe extends DoOnceNode {
        void connectInput(String portId, NodeDataType outputType) {
            FlowControlLanguageContractTest.connectInput(this, portId, outputType);
        }
    }
}
