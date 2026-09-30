package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.math.SequenceOps;
import com.nodecraft.nodesystem.math.SequenceResult;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DataSeriesNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MathRangeNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.RepeatNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sequence Strict Inputs & Explicit Termination Contract v2 (Graph V125).
 */
class SequenceLanguageV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV125() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void allSequenceNodesExposeValidAndError() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.sequence."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(3, ids.size());
        for (String nodeId : ids) {
            INode node = registry.createNodeInstance(nodeId);
            assertNotNull(findPort(node, "output_valid"), nodeId);
            assertNotNull(findPort(node, "output_error"), nodeId);
        }
    }

    @Test
    void connectedNullStepDoesNotUseDefault() {
        MathRangeProbe probe = new MathRangeProbe();
        probe.putInput("input_start", 0.0d);
        probe.putInput("input_end", 4.0d);
        probe.putInput("input_step", null);
        probe.connectInput("input_step", NodeDataType.DOUBLE);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_numbers")).isEmpty());
    }

    @Test
    void undrivenStepUsesDefault() {
        MathRangeNode node = new MathRangeNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_start", 0.0d,
                "input_end", 2.0d
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of(0.0d, 1.0d, 2.0d), outputs.get("output_numbers"));
    }

    @Test
    void connectedDoubleCountOnSeriesFailsClosed() {
        DataSeriesProbe probe = new DataSeriesProbe();
        probe.putInput("input_start", 0.0d);
        probe.putInput("input_step", 1.0d);
        probe.putInput("input_count", 1.9d);
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_series")).isEmpty());
    }

    @Test
    void seriesOverflowFailsClosed() {
        DataSeriesNode node = new DataSeriesNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_start", 1.0e308d,
                "input_step", 1.0e308d,
                "input_count", 10
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(((List<?>) outputs.get("output_series")).isEmpty());
        assertEquals(SequenceOps.ERROR_NON_FINITE_VALUE, outputs.get("output_error"));
    }

    @Test
    void rangeFloatStallFailsClosed() {
        MathRangeNode node = new MathRangeNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_start", 1.0e308d,
                "input_end", Double.MAX_VALUE,
                "input_step", 1.0d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(((List<?>) outputs.get("output_numbers")).isEmpty());
        assertEquals(SequenceOps.ERROR_FLOAT_PRECISION_STALL, outputs.get("output_error"));
    }

    @Test
    void rangeDirectionMismatchIsValidEmpty() {
        MathRangeNode node = new MathRangeNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_start", 0.0d,
                "input_end", 4.0d,
                "input_step", -1.0d
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertTrue(((List<?>) outputs.get("output_numbers")).isEmpty());
    }

    @Test
    void repeatConnectedInvalidCountFailsClosed() {
        RepeatProbe probe = new RepeatProbe();
        probe.putInput("input_data", "A");
        probe.putInput("input_count", 1.9d);
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_result")).isEmpty());
        assertEquals(0, probe.getOutput("output_length"));
    }

    @Test
    void repeatPreservesObjectReference() {
        Vector3d vector = new Vector3d(1.0d, 2.0d, 3.0d);
        RepeatNode node = new RepeatNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_data", vector,
                "input_count", 3
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) outputs.get("output_result");
        assertEquals(3, result.size());
        assertSame(vector, result.get(0));
        assertSame(vector, result.get(1));
        assertSame(vector, result.get(2));
    }

    @Test
    void nodeMatchesSequenceOpsForRange() {
        MathRangeNode node = new MathRangeNode();
        SequenceResult expected = SequenceOps.range(0.0d, 1.0d, 0.25d);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_start", 0.0d,
                "input_end", 1.0d,
                "input_step", 0.25d
        ));
        assertEquals(expected.valid(), outputs.get("output_valid"));
        assertEquals(expected.values(), outputs.get("output_numbers"));
        assertEquals(expected.error(), outputs.get("output_error"));
    }

    @Test
    void nodeMatchesSequenceOpsForSeries() {
        DataSeriesNode node = new DataSeriesNode();
        SequenceResult expected = SequenceOps.series(0.0d, 2.0d, 4);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_start", 0.0d,
                "input_step", 2.0d,
                "input_count", 4
        ));
        assertEquals(expected.valid(), outputs.get("output_valid"));
        assertEquals(expected.values(), outputs.get("output_series"));
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
        throw new AssertionError("missing port " + portId);
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

    private static final class MathRangeProbe extends MathRangeNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            SequenceLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class DataSeriesProbe extends DataSeriesNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            SequenceLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RepeatProbe extends RepeatNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            SequenceLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
