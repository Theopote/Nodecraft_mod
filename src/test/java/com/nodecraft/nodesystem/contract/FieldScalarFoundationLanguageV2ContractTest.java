package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.fields.FieldSampleUtils;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldBinaryOpNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldNoiseNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointsNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointsNode;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field Scalar Foundation & Strict Sampling v2 (Graph V133).
 */
class FieldScalarFoundationLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV133() {
        assertEquals(133, GraphFormatVersion.V133);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V133);
    }

    @Test
    void scalarSamplePointsRejectsMixedInvalidElement() {
        ScalarFieldData field = point -> 1.0d;
        List<Object> points = new ArrayList<>();
        points.add(new PointData(0, 0, 0));
        points.add(new PointData(1, 0, 0));
        points.add("not-a-point");
        points.add(new PointData(3, 0, 0));

        Map<String, Object> outputs = new ScalarFieldSamplePointsNode().compute(Map.of(
                "input_field", field,
                "input_points", points
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_POINTS, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_count"));
        assertTrue(((List<?>) outputs.get("output_values")).isEmpty());
    }

    @Test
    void vectorSamplePointsRejectsMixedInvalidElement() {
        VectorFieldData field = (point, dest) -> dest.set(1, 0, 0);
        List<Object> points = new ArrayList<>();
        points.add(new PointData(0, 0, 0));
        points.add("bad");

        Map<String, Object> outputs = new VectorFieldSamplePointsNode().compute(Map.of(
                "input_field", field,
                "input_points", points
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_POINTS, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_count"));
    }

    @Test
    void scalarSamplePointsRejectsNaNCoordinate() {
        ScalarFieldData field = point -> 1.0d;
        Map<String, Object> outputs = new ScalarFieldSamplePointsNode().compute(Map.of(
                "input_field", field,
                "input_points", List.of(new PointData(0, 0, 0), new PointData(Double.NaN, 0, 0))
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_POINTS, outputs.get("output_error"));
    }

    @Test
    void scalarSamplePointsPreservesCountAndOrder() {
        ScalarFieldData field = point -> point.x;
        List<PointData> points = List.of(
                new PointData(1, 0, 0),
                new PointData(2, 0, 0),
                new PointData(3, 0, 0)
        );
        Map<String, Object> outputs = new ScalarFieldSamplePointsNode().compute(Map.of(
                "input_field", field,
                "input_points", points
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(3, outputs.get("output_count"));
        assertEquals(List.of(1.0d, 2.0d, 3.0d), outputs.get("output_values"));
    }

    @Test
    void scalarSamplePointsEmptyListIsValid() {
        ScalarFieldData field = point -> 1.0d;
        Map<String, Object> outputs = new ScalarFieldSamplePointsNode().compute(Map.of(
                "input_field", field,
                "input_points", List.of()
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(0, outputs.get("output_count"));
        assertEquals(List.of(), outputs.get("output_values"));
    }

    @Test
    void noiseConnectedNaNScaleFailsClosed() {
        NoiseProbe probe = new NoiseProbe();
        probe.putInput("input_seed", 1);
        probe.putInput("input_scale", Double.NaN);
        probe.connectInput("input_scale", NodeDataType.DOUBLE);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertNull(probe.getOutput("output_field"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_INPUT, probe.getOutput("output_error"));
    }

    @Test
    void noiseUndrivenScaleUsesDefault() {
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        Map<String, Object> outputs = node.compute(Map.of("input_seed", 7));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertNotNull(outputs.get("output_field"));
        ScalarFieldData field = assertInstanceOf(ScalarFieldData.class, outputs.get("output_field"));
        assertTrue(Double.isFinite(field.sampleScalar(new Vector3d(0.5d, 0.25d, 0.125d))));
    }

    @Test
    void noiseExtremeOverflowCoordsYieldInvalidSample() {
        ScalarFieldNoiseNode noise = new ScalarFieldNoiseNode();
        Map<String, Object> built = noise.compute(Map.of(
                "input_seed", 1,
                "input_scale", 1.0e300d,
                "input_amplitude", 1.0d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        ScalarFieldData field = assertInstanceOf(ScalarFieldData.class, built.get("output_field"));

        Map<String, Object> sample = new ScalarFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(1.0e300d, 1.0e300d, 1.0e300d)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
        assertTrue(Double.isNaN((Double) sample.get("output_value")));
    }

    @Test
    void noiseDeterminismUnchanged() {
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        Map<String, Object> inputs = Map.of(
                "input_seed", 99,
                "input_scale", 1.5d,
                "input_offset_x", 0.1d,
                "input_offset_y", 0.2d,
                "input_offset_z", 0.3d,
                "input_amplitude", 2.0d
        );
        ScalarFieldData a = assertInstanceOf(ScalarFieldData.class, node.compute(inputs).get("output_field"));
        ScalarFieldData b = assertInstanceOf(ScalarFieldData.class, node.compute(inputs).get("output_field"));
        Vector3d p = new Vector3d(1.25d, -0.5d, 3.0d);
        assertEquals(a.sampleScalar(p), b.sampleScalar(p), 0.0d);
    }

    @Test
    void constantInfinityFailsWithError() {
        Map<String, Object> outputs = new ScalarFieldConstantNode().compute(Map.of(
                "input_value", Double.POSITIVE_INFINITY
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_INPUT, outputs.get("output_error"));
        assertNull(outputs.get("output_field"));
    }

    @Test
    void combineDivByZeroSampleIsInvalid() {
        ScalarFieldData one = point -> 1.0d;
        ScalarFieldData zero = point -> 0.0d;
        ScalarFieldBinaryOpNode combine = new ScalarFieldBinaryOpNode();
        combine.setNodeState(Map.of("operation", ScalarFieldBinaryOpNode.ScalarBinaryOp.DIV.name()));
        Map<String, Object> built = combine.compute(Map.of(
                "input_a", one,
                "input_b", zero
        ));
        assertTrue((Boolean) built.get("output_valid"));
        ScalarFieldData field = assertInstanceOf(ScalarFieldData.class, built.get("output_field"));

        Map<String, Object> sample = new ScalarFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(0, 0, 0)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
        assertTrue(Double.isNaN((Double) sample.get("output_value")));
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

    private static final class NoiseProbe extends ScalarFieldNoiseNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            FieldScalarFoundationLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
