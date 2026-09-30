package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.scalar_math.ClampNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.ExpressionNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.GraphMapperNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.IntDivideNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.LerpNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.RemapNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.SmoothstepNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scalar Math Strict Inputs & Numeric Boundaries v2 (Graph V120).
 */
class ScalarMathLanguageV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV120() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void intDivideMinValueOverMinusOneFails() {
        IntDivideNode node = new IntDivideNode();
        node.setInput("input_a", Integer.MIN_VALUE);
        node.setInput("input_b", -1);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_quotient"));
        assertEquals(0, node.getOutput("output_remainder"));
    }

    @Test
    void intDivideRejectsNonIntegerInput() {
        IntDivideProbe probe = new IntDivideProbe();
        probe.putInput("input_a", 3.9d);
        probe.putInput("input_b", 1);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void smoothstepExtremeSymmetricSpan() {
        SmoothstepNode node = new SmoothstepNode();
        node.setInput("input_value", 0.0d);
        node.setInput("input_edge0", -1.0e308d);
        node.setInput("input_edge1", 1.0e308d);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(0.5d, (Double) node.getOutput("output_t"), 1.0e-6);
        assertEquals(0.5d, (Double) node.getOutput("output_result"), 1.0e-6);
    }

    @Test
    void smoothstepTMatchesResultPath() {
        SmoothstepNode node = new SmoothstepNode();
        node.setInput("input_value", 0.25d);
        node.setInput("input_edge0", 0.0d);
        node.setInput("input_edge1", 1.0d);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        double t = (Double) node.getOutput("output_t");
        double result = (Double) node.getOutput("output_result");
        assertEquals(t * t * (3.0d - 2.0d * t), result, 1.0e-12);
    }

    @Test
    void expressionUsedConnectedInvalidVarFails() {
        ExpressionProbe probe = new ExpressionProbe();
        probe.setExpression("A + B");
        probe.connectInput("input_a", NodeDataType.DOUBLE);
        probe.putInput("input_a", "not-a-number");
        probe.putInput("input_b", 1.0d);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void expressionUnusedVarIgnoresInvalidPort() {
        ExpressionProbe probe = new ExpressionProbe();
        probe.setExpression("A * 2");
        probe.connectInput("input_b", NodeDataType.DOUBLE);
        probe.putInput("input_a", 3.0d);
        probe.putInput("input_b", "bad");
        probe.processNode(null);
        assertTrue((Boolean) probe.getOutput("output_valid"));
        assertEquals(6.0d, (Double) probe.getOutput("output_result"), 0.0d);
    }

    @Test
    void graphMapperDrivenInvalidDomainFails() {
        GraphMapperProbe probe = new GraphMapperProbe();
        probe.connectInput("input_source", NodeDataType.NUMERIC_RANGE);
        probe.putInput("input_value", 0.5d);
        probe.putInput("input_source", 1.0d);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(Double.isNaN((Double) probe.getOutput("output_result")));
    }

    @Test
    void clampRemapDrivenInvalidDomainFails() {
        ClampProbe clamp = new ClampProbe();
        clamp.connectInput("input_domain", NodeDataType.NUMERIC_RANGE);
        clamp.putInput("input_value", 0.5d);
        clamp.putInput("input_domain", 1.0d);
        clamp.processNode(null);
        assertFalse((Boolean) clamp.getOutput("output_valid"));

        RemapProbe remap = new RemapProbe();
        remap.connectInput("input_source", NodeDataType.NUMERIC_RANGE);
        remap.putInput("input_value", 0.5d);
        remap.putInput("input_source", 1.0d);
        remap.putInput("input_target", new NumericRangeData(0.0d, 1.0d));
        remap.processNode(null);
        assertFalse((Boolean) remap.getOutput("output_valid"));
    }

    @Test
    void validTrueImpliesFiniteNumericOutputs() {
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.scalar_math.")) {
                continue;
            }
            if (nodeId.endsWith(".int_divide")) {
                continue;
            }
            INode created = registry.createNodeInstance(nodeId);
            if (!(created instanceof BaseNode instance)) {
                continue;
            }
            seedMinimalValidInputs(instance, nodeId);
            instance.processNode(null);
            Object validObj = instance.getOutput("output_valid");
            if (!(validObj instanceof Boolean valid) || !valid) {
                continue;
            }
            for (IPort port : instance.getOutputPorts()) {
                if (port.getDataType() != NodeDataType.DOUBLE) {
                    continue;
                }
                Object value = instance.getOutput(port.getId());
                if (value instanceof Double d) {
                    assertTrue(Double.isFinite(d),
                        nodeId + "#" + port.getId() + " must be finite when Valid=true");
                }
            }
        }
    }

    @Test
    void stableLerpLargeOppositeEndpoints() {
        LerpNode node = new LerpNode();
        Map<String, Object> mid = node.compute(Map.of(
            "input_a", 1.0e308d,
            "input_b", -1.0e308d,
            "input_t", 0.5d
        ));
        assertTrue((Boolean) mid.get("output_valid"));
        assertEquals(0.0d, (Double) mid.get("output_result"), 0.0d);

        Map<String, Object> atB = node.compute(Map.of(
            "input_a", 1.0e308d,
            "input_b", -1.0e308d,
            "input_t", 1.0d
        ));
        assertTrue((Boolean) atB.get("output_valid"));
        assertEquals(-1.0e308d, (Double) atB.get("output_result"), 0.0d);
    }

    private static void seedMinimalValidInputs(BaseNode node, String nodeId) {
        if (nodeId.endsWith(".addition") || nodeId.endsWith(".subtraction")
            || nodeId.endsWith(".multiplication") || nodeId.endsWith(".division")
            || nodeId.endsWith(".modulus") || nodeId.endsWith(".min") || nodeId.endsWith(".max")) {
            node.setInput("input_a", 1.0d);
            node.setInput("input_b", 2.0d);
        } else if (nodeId.endsWith(".power")) {
            node.setInput("input_base", 2.0d);
            node.setInput("input_exponent", 1.0d);
        } else if (nodeId.endsWith(".logarithm")) {
            node.setInput("input_number", 2.0d);
            node.setInput("input_base", 2.0d);
        } else if (nodeId.endsWith(".absolute") || nodeId.endsWith(".sqrt")
            || nodeId.endsWith(".floor") || nodeId.endsWith(".ceiling")
            || nodeId.endsWith(".round") || nodeId.endsWith(".frac")
            || nodeId.endsWith(".sign")) {
            node.setInput("input_value", 1.0d);
        } else if (nodeId.endsWith(".lerp")) {
            node.setInput("input_a", 0.0d);
            node.setInput("input_b", 1.0d);
            node.setInput("input_t", 0.5d);
        } else if (nodeId.endsWith(".smoothstep")) {
            node.setInput("input_value", 0.5d);
            node.setInput("input_edge0", 0.0d);
            node.setInput("input_edge1", 1.0d);
        } else if (nodeId.endsWith(".clamp")) {
            node.setInput("input_value", 0.5d);
            node.setInput("input_domain", new NumericRangeData(0.0d, 1.0d));
        } else if (nodeId.endsWith(".remap") || nodeId.endsWith(".graph_mapper")) {
            node.setInput("input_value", 0.5d);
            node.setInput("input_source", new NumericRangeData(0.0d, 1.0d));
            node.setInput("input_target", new NumericRangeData(0.0d, 10.0d));
        } else if (nodeId.endsWith(".expression")) {
            ((ExpressionNode) node).setExpression("1 + 1");
        }
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

    private static final class IntDivideProbe extends IntDivideNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class ExpressionProbe extends ExpressionNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ScalarMathLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GraphMapperProbe extends GraphMapperNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ScalarMathLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ClampProbe extends ClampNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ScalarMathLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RemapProbe extends RemapNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ScalarMathLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
