package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.compare.EqualsNode;
import com.nodecraft.nodesystem.nodes.math.compare.GreaterThanNode;
import com.nodecraft.nodesystem.nodes.math.compare.LessThanNode;
import com.nodecraft.nodesystem.nodes.math.compare.NotEqualsNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compare Exact Numeric & Invalid Input Contract v2 (Graph V121).
 */
class CompareLanguageV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV121() {
        assertEquals(121, GraphFormatVersion.V121);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V121);
    }

    @Test
    void allCompareNodesExposeOutputValid() {
        List<String> compareIds = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.compare."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(6, compareIds.size());
        for (String nodeId : compareIds) {
            INode node = registry.createNodeInstance(nodeId);
            assertNotNull(node.getOutputPorts().stream()
                    .filter(port -> "output_valid".equals(port.getId()))
                    .findFirst()
                    .orElse(null), nodeId + " missing output_valid");
        }
    }

    @Test
    void notEqualsBothNaNIsInvalid() {
        NotEqualsNode node = new NotEqualsNode();
        node.setInput("input_a", Double.NaN);
        node.setInput("input_b", Double.NaN);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));
    }

    @Test
    void bothUndrivenEqualsIsInvalid() {
        EqualsNode node = new EqualsNode();
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));
    }

    @Test
    void explicitInjectedNullEqualityRemainsTrue() {
        EqualsNode node = new EqualsNode();
        node.setInput("input_a", null);
        node.setInput("input_b", null);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertTrue((Boolean) node.getOutput("output_result"));
    }

    @Test
    void largeLongIntegersAreNotEqual() {
        EqualsNode node = new EqualsNode();
        node.setInput("input_a", 9007199254740992L);
        node.setInput("input_b", 9007199254740993L);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));
    }

    @Test
    void orderingRejectsIntegerOnDoublePort() {
        LessThanProbe probe = new LessThanProbe();
        probe.putInput("input_a", 1);
        probe.putInput("input_b", 2.0d);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertFalse((Boolean) probe.getOutput("output_result"));
    }

    @Test
    void lessThanUsesExactNumericComparison() {
        LessThanNode node = new LessThanNode();
        node.setInput("input_a", 0.0d);
        node.setInput("input_b", 5.0e-11d);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertTrue((Boolean) node.getOutput("output_result"));
    }

    @Test
    void equalsCrossTypeIntegerDoubleStillTrue() {
        EqualsNode node = new EqualsNode();
        node.setInput("input_a", 1);
        node.setInput("input_b", 1.0d);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertTrue((Boolean) node.getOutput("output_result"));
    }

    @Test
    void connectedInvalidDoubleOrderingFailsClosed() {
        LessThanProbe probe = new LessThanProbe();
        probe.connectInput("input_a", NodeDataType.DOUBLE);
        probe.putInput("input_a", "not-a-number");
        probe.putInput("input_b", 1.0d);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertFalse((Boolean) probe.getOutput("output_result"));
    }

    @Test
    void validTrueImpliesFalseWhenInvalid() {
        GreaterThanNode node = new GreaterThanNode();
        node.setInput("input_a", Double.NaN);
        node.setInput("input_b", 1.0d);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertFalse((Boolean) node.getOutput("output_result"));
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

    private static final class LessThanProbe extends LessThanNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            CompareLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
