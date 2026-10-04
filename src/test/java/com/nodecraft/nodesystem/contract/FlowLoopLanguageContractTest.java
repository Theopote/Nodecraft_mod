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
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.flow.loop.ForEachLoopNode;
import com.nodecraft.nodesystem.nodes.flow.loop.WhileLoopNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
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
 * Flow Loop v1 language fence (Graph V66).
 */
class FlowLoopLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "flow.loop.for_each",
            "flow.loop.while"
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
    void currentGraphFormatIsV66() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyTwoFlowLoopNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("flow.loop."))
                .sorted()
                .toList();
        assertEquals(2, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(registry.getAllNodeIds().contains("flow.loop.accumulator"));
    }

    @Test
    void ordersAreZeroThroughOne() {
        assertEquals(0, orderOf("flow.loop.for_each"));
        assertEquals(1, orderOf("flow.loop.while"));
    }

    @Test
    void effectsMatchFlowLoopContract() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(
                registry.createNodeInstance("flow.loop.for_each").getClass(), "flow.loop.for_each"));
        assertEquals(NodeEffect.CONTEXT_WRITE, NodeEffectResolver.resolve(
                registry.createNodeInstance("flow.loop.while").getClass(), "flow.loop.while"));
    }

    @Test
    void forEachEnabledStrictBoolean() {
        ForEachLoopNode node = new ForEachLoopNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("a"),
                "input_enabled", 1
        ));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertFalse(node.shouldFireExecComplete());
        assertEquals(0, node.execLoopIterationCount());
    }

    @Test
    void forEachConnectedNullEnabledFailsClosed() {
        ForEachProbe node = new ForEachProbe();
        node.connectInput("input_enabled", NodeDataType.BOOLEAN);
        node.setInput("input_list", List.of("a"));
        node.setInput("input_enabled", null);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertFalse(node.shouldFireExecComplete());
    }

    @Test
    void forEachListSizeHardCap() {
        ForEachLoopNode node = new ForEachLoopNode();
        List<Object> huge = new ArrayList<>(Collections.nCopies(GenerationLimits.MAX_LOOP_ITERATIONS + 1, "x"));
        Map<String, Object> outputs = node.compute(Map.of("input_list", huge));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertFalse(node.shouldFireExecComplete());
        assertEquals(0, node.execLoopIterationCount());
    }

    @Test
    void forEachEmptyAndDisabledStillComplete() {
        ForEachLoopNode empty = new ForEachLoopNode();
        Map<String, Object> emptyOut = empty.compute(Map.of("input_list", List.of()));
        assertEquals(Boolean.TRUE, emptyOut.get("output_valid"));
        assertTrue(empty.shouldFireExecComplete());
        assertEquals(0, empty.execLoopIterationCount());

        ForEachLoopNode disabled = new ForEachLoopNode();
        Map<String, Object> disabledOut = disabled.compute(Map.of(
                "input_list", List.of("a"),
                "input_enabled", false
        ));
        assertEquals(Boolean.TRUE, disabledOut.get("output_valid"));
        assertTrue(disabled.shouldFireExecComplete());
    }

    @Test
    void forEachListAndItemBindTypeVariableT() {
        INode node = registry.createNodeInstance("flow.loop.for_each");
        IPort list = findPort(node, "input_list");
        IPort item = findPort(node, "output_item");
        assertNotNull(list);
        assertNotNull(item);
        assertEquals("T", list.getListTypeVariable());
        assertTrue(item.isListElementBinding());
        assertEquals("T", item.getListTypeVariable());
        assertFalse(hasPort(node, "output_items"));
        assertFalse(hasPort(node, "output_pairs"));
        assertFalse(hasPort(node, "output_indices"));
    }

    @Test
    void whileConditionStrictBoolean() {
        WhileLoopNode node = new WhileLoopNode();
        Map<String, Object> outputs = node.compute(Map.of("input_condition", 1));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertTrue(node.getActiveExecOutputPortIds().isEmpty());
    }

    @Test
    void whileUnconnectedConditionFailsClosed() {
        WhileLoopNode node = new WhileLoopNode();
        Map<String, Object> outputs = node.compute(Map.of());
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertTrue(String.valueOf(outputs.get("output_error")).contains("Condition"));
        assertTrue(node.getActiveExecOutputPortIds().isEmpty());
    }

    @Test
    void whileMaxIterationsRejectsDoubleAndOutOfRange() {
        WhileLoopNode dbl = new WhileLoopNode();
        assertEquals(Boolean.FALSE, dbl.compute(Map.of(
                "input_condition", true,
                "input_max_iterations", 3.5
        )).get("output_valid"));

        WhileLoopNode zero = new WhileLoopNode();
        assertEquals(Boolean.FALSE, zero.compute(Map.of(
                "input_condition", true,
                "input_max_iterations", 0
        )).get("output_valid"));
    }

    @Test
    void whileSetMaxIterationsIgnoresOutOfRange() {
        WhileLoopNode node = new WhileLoopNode();
        node.setMaxIterations(32);
        assertEquals(32, node.getMaxIterations());
        node.setMaxIterations(0);
        assertEquals(32, node.getMaxIterations());
        node.setMaxIterations(GenerationLimits.MAX_LOOP_ITERATIONS + 1);
        assertEquals(32, node.getMaxIterations());
    }

    @Test
    void whileIterationCounterIsRunLocal() {
        WhileLoopNode node = new WhileLoopNode();
        node.setMaxIterations(2);
        ExecutionContext context = ExecutionContext.createEmpty(null);

        context.setSharedExecutionRunGuard(new ExecutionRunGuard());
        node.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), node.getActiveExecOutputPortIds());
        node.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), node.getActiveExecOutputPortIds());
        node.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_complete"), node.getActiveExecOutputPortIds());
        assertEquals(true, node.getOutput("output_hit_limit"));

        context.setSharedExecutionRunGuard(new ExecutionRunGuard());
        node.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), node.getActiveExecOutputPortIds());
        assertEquals(1, node.getOutput("output_iterations"));
    }

    @Test
    void whileCanRunSecondIndependentSessionWithinSameExecutionRun() {
        WhileLoopNode node = new WhileLoopNode();
        node.setMaxIterations(2);
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSharedExecutionRunGuard(new ExecutionRunGuard());

        node.compute(Map.of("input_condition", true), context);
        node.compute(Map.of("input_condition", true), context);
        node.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_complete"), node.getActiveExecOutputPortIds());
        assertEquals(true, node.getOutput("output_hit_limit"));
        assertEquals(2, node.getOutput("output_iterations"));

        // Same guard: session counter was cleared; a new pulse starts a new session.
        node.compute(Map.of("input_condition", true), context);
        assertEquals(Set.of("exec_body"), node.getActiveExecOutputPortIds());
        assertEquals(1, node.getOutput("output_iterations"));
        assertEquals(false, node.getOutput("output_hit_limit"));
    }

    @Test
    void whileHasNoValuesPorts() {
        INode node = registry.createNodeInstance("flow.loop.while");
        assertFalse(hasPort(node, "input_values"));
        assertFalse(hasPort(node, "output_values"));
        assertFalse(hasPort(node, "input_default_condition"));
        assertPortType(node, "input_condition", NodeDataType.BOOLEAN);
    }

    @Test
    void forEachIterationCountPortIsPresent() {
        INode node = registry.createNodeInstance("flow.loop.for_each");
        IPort count = findPort(node, "output_count");
        assertNotNull(count);
        assertEquals("Iteration Count", count.getDisplayName());
    }

    @Test
    void joinStringsNodeIsRegistered() {
        INode node = registry.createNodeInstance("math.list.join_strings");
        assertNotNull(node);
        assertPortType(node, "input_list", NodeDataType.STRING_LIST);
        assertPortType(node, "output_result", NodeDataType.STRING);
    }


    private static int orderOf(String typeId) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node.getClass().getAnnotation(NodeInfo.class).order();
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertEquals(expected, port.getDataType(), portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
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

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        node.state = new HashMap<>();
        return node;
    }

    private static SavedConnection wire(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNode;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetNode;
        connection.targetPortId = targetPort;
        return connection;
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

    private static final class ForEachProbe extends ForEachLoopNode {
        void connectInput(String portId, NodeDataType outputType) {
            FlowLoopLanguageContractTest.connectInput(this, portId, outputType);
        }
    }
}
