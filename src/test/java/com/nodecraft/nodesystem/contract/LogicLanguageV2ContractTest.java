package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.logic.AndNode;
import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.math.logic.NotNode;
import com.nodecraft.nodesystem.nodes.math.logic.SelectItemNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logic Strict Boolean & Value Selection Contract v2 (Graph V122).
 */
class LogicLanguageV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV122() {
        assertEquals(122, GraphFormatVersion.V122);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V122);
    }

    @Test
    void allLogicNodesExposeOutputValid() {
        List<String> logicIds = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.logic."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(6, logicIds.size());
        for (String nodeId : logicIds) {
            INode node = registry.createNodeInstance(nodeId);
            assertNotNull(node.getOutputPorts().stream()
                    .filter(port -> "output_valid".equals(port.getId()))
                    .findFirst()
                    .orElse(null), nodeId + " missing output_valid");
        }
    }

    @Test
    void ifNodeExposesOutputError() {
        IfNode node = new IfNode();
        assertNotNull(findPort(node, "output_error"));
        assertEquals(NodeDataType.STRING, findPort(node, "output_error").getDataType());
    }

    @Test
    void notInvalidInputIsNotTrue() {
        NotNode node = new NotNode();
        node.setInput("input_value", null);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));

        node.setInput("input_value", "invalid");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));
    }

    @Test
    void ifInvalidConditionDoesNotSelectFalseBranch() {
        IfNode node = new IfNode();
        node.setInput("input_condition", 1);
        node.setInput("input_true_value", "true-branch");
        node.setInput("input_false_value", "false-branch");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_result"));
    }

    @Test
    void ifFalseConditionSelectsFalseBranch() {
        IfNode node = new IfNode();
        node.setInput("input_condition", false);
        node.setInput("input_true_value", "true-branch");
        node.setInput("input_false_value", "false-branch");
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals("false-branch", node.getOutput("output_result"));
        assertEquals("", node.getOutput("output_error"));
    }

    @Test
    void switchOutOfRangeUsesDefaultWithValidTrue() {
        SelectItemNode node = new SelectItemNode();
        node.setInput("input_index", 5);
        node.setInput("input_item_0", "item0");
        node.setInput("input_item_1", "item1");
        node.setInput("input_default", "default");
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals("default", node.getOutput("output_result"));
    }

    @Test
    void switchInvalidIndexFailsClosed() {
        SelectItemNode node = new SelectItemNode();
        node.setInput("input_index", "1");
        node.setInput("input_item_0", "item0");
        node.setInput("input_default", "default");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_result"));
    }

    @Test
    void switchConnectedInvalidIndexFailsClosed() {
        SwitchProbe probe = new SwitchProbe();
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.putInput("input_index", 1.9d);
        probe.putInput("input_item_0", "item0");
        probe.putInput("input_default", "default");
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertNull(probe.getOutput("output_result"));
    }

    @Test
    void ifPassesThroughOpaqueAnyWithoutConversion() {
        PointData point = new PointData(1, 2, 3);
        IfNode node = new IfNode();
        node.setInput("input_condition", true);
        node.setInput("input_true_value", point);
        node.setInput("input_false_value", "string-branch");
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(point, node.getOutput("output_result"));
    }

    @Test
    void andRejectsNonBooleanDrivenInput() {
        AndNode node = new AndNode();
        node.setInput("input_a", 1);
        node.setInput("input_b", true);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));
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
        throw new AssertionError("missing port " + portId + " on " + node.getTypeId());
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
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }

    private static final class SwitchProbe extends SelectItemNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            LogicLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
