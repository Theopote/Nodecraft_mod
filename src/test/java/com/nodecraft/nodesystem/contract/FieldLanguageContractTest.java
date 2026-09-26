package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.math.FieldMath;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.nodes.math.fields.AttractorFieldBlendNode;
import com.nodecraft.nodesystem.nodes.math.fields.CurveAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.PointAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.RepulsorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldBinaryOpNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldNoiseNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointsNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldFromSdfGradientNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointsNode;
import com.nodecraft.nodesystem.nodes.math.fields.VolumeAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.VortexFieldNode;
import com.nodecraft.nodesystem.nodes.math.random.NoiseNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field v1 language fence: inherited Scalar/Random semantics, finite Valid sampling, typed lists.
 */
class FieldLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlySeventeenFieldNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.fields."))
                .sorted()
                .toList();
        assertEquals(17, ids.size(), "Expected 17 field nodes: " + ids);
    }

    @Test
    void scalarSamplePointsOutputIsDoubleList() {
        ScalarFieldSamplePointsNode node = new ScalarFieldSamplePointsNode();
        assertEquals(NodeDataType.DOUBLE_LIST,
                node.getOutputPorts().stream()
                        .filter(p -> "output_values".equals(p.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getDataType());
    }

    @Test
    void scalarFieldNoiseMatchesRandomNoiseKernel() {
        ScalarFieldNoiseNode fieldNode = new ScalarFieldNoiseNode();
        NoiseNode noiseNode = new NoiseNode();

        Object fieldObj = fieldNode.compute(Map.of(
                "input_seed", 0,
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        )).get("output_field");
        assertInstanceOf(ScalarFieldData.class, fieldObj);
        ScalarFieldData field = (ScalarFieldData) fieldObj;

        double fromField = field.sampleScalar(new Vector3d(1.0d, 2.0d, 3.0d));
        double fromNoise = (Double) noiseNode.compute(Map.of(
                "input_x", 1.0d,
                "input_y", 2.0d,
                "input_z", 3.0d,
                "input_seed", 0
        )).get("output_noise");

        assertEquals(fromNoise, fromField, 0.0d);
    }

    @Test
    void scalarFieldNoiseIgnoresNonIntegerSeed() {
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        Map<String, Object> withString = Map.of(
                "input_seed", "1",
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        );
        Map<String, Object> withZero = Map.of(
                "input_seed", 0,
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        );
        ScalarFieldData a = (ScalarFieldData) node.compute(withString).get("output_field");
        ScalarFieldData b = (ScalarFieldData) node.compute(withZero).get("output_field");
        assertEquals(a.sampleScalar(new Vector3d()), b.sampleScalar(new Vector3d()), 0.0d);
    }

    @Test
    void scalarFieldNoiseIgnoresNonIntegerDoubleSeed() {
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        Map<String, Object> withDouble = Map.of(
                "input_seed", 1.9d,
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        );
        Map<String, Object> withZero = Map.of(
                "input_seed", 0,
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        );
        ScalarFieldData a = (ScalarFieldData) node.compute(withDouble).get("output_field");
        ScalarFieldData b = (ScalarFieldData) node.compute(withZero).get("output_field");
        assertEquals(a.sampleScalar(new Vector3d()), b.sampleScalar(new Vector3d()), 0.0d);
    }

    @Test
    void scalarFieldNoiseAcceptsNegativeFiniteScale() {
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        ScalarFieldData neg = (ScalarFieldData) node.compute(Map.of(
                "input_seed", 42,
                "input_scale", -2.0d,
                "input_amplitude", 1.0d
        )).get("output_field");
        ScalarFieldData pos = (ScalarFieldData) node.compute(Map.of(
                "input_seed", 42,
                "input_scale", 2.0d,
                "input_amplitude", 1.0d
        )).get("output_field");
        assertTrue(Double.isFinite(neg.sampleScalar(new Vector3d(1.0d, 2.0d, 3.0d))));
        assertFalse(Double.isNaN(neg.sampleScalar(new Vector3d(1.0d, 2.0d, 3.0d))));
        assertEquals(
                neg.sampleScalar(new Vector3d(1.0d, 2.0d, 3.0d)),
                pos.sampleScalar(new Vector3d(-1.0d, -2.0d, -3.0d)),
                0.0d);
    }

    @Test
    void samplingPortsFollowScalarVectorSymmetry() {
        assertEquals(NodeDataType.POINT,
                new ScalarFieldSamplePointNode().getInputPorts().stream()
                        .filter(p -> "input_point".equals(p.getId())).findFirst().orElseThrow().getDataType());
        assertEquals(NodeDataType.DOUBLE,
                new ScalarFieldSamplePointNode().getOutputPorts().stream()
                        .filter(p -> "output_value".equals(p.getId())).findFirst().orElseThrow().getDataType());

        assertEquals(NodeDataType.POINT,
                new VectorFieldSamplePointNode().getInputPorts().stream()
                        .filter(p -> "input_point".equals(p.getId())).findFirst().orElseThrow().getDataType());
        assertEquals(NodeDataType.VECTOR,
                new VectorFieldSamplePointNode().getOutputPorts().stream()
                        .filter(p -> "output_vector".equals(p.getId())).findFirst().orElseThrow().getDataType());

        assertEquals(NodeDataType.POINT_LIST,
                new VectorFieldSamplePointsNode().getInputPorts().stream()
                        .filter(p -> "input_points".equals(p.getId())).findFirst().orElseThrow().getDataType());
        assertEquals(NodeDataType.VECTOR_LIST,
                new VectorFieldSamplePointsNode().getOutputPorts().stream()
                        .filter(p -> "output_vectors".equals(p.getId())).findFirst().orElseThrow().getDataType());
    }

    @Test
    void scalarSamplePointAcceptsFiniteFieldValue() {
        ScalarFieldData field = point -> 3.5d;
        Map<String, Object> outputs = new ScalarFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new Vector3d(1.0d, 0.0d, 0.0d)
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(3.5d, (Double) outputs.get("output_value"), 0.0d);
    }

    @Test
    void scalarSamplePointRejectsNonFiniteFieldValue() {
        ScalarFieldData divField = point -> FieldMath.combineScalars(1.0d, 0.0d, FieldMath.ScalarCombineOp.DIV);
        ScalarFieldSamplePointNode node = new ScalarFieldSamplePointNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_field", divField,
                "input_point", new Vector3d(0.0d, 0.0d, 0.0d)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_value")));
    }

    @Test
    void vectorSamplePointRejectsNonFiniteComponents() {
        VectorFieldConstantNode constant = new VectorFieldConstantNode();
        Object field = constant.compute(Map.of(
                "input_x", Double.NaN,
                "input_y", 1.0d,
                "input_z", 2.0d
        )).get("output_field");
        assertNull(field);

        VectorFieldSamplePointNode sample = new VectorFieldSamplePointNode();
        Map<String, Object> badField = sample.compute(Map.of(
                "input_field", (com.nodecraft.nodesystem.datatypes.VectorFieldData) (point, dest) -> dest.set(Double.NaN, 1.0d, 2.0d),
                "input_point", new Vector3d()
        ));
        assertFalse((Boolean) badField.get("output_valid"));
        assertNull(badField.get("output_vector"));
    }

    @Test
    void vectorSamplePointsFailClosedOnSingleNaN() {
        com.nodecraft.nodesystem.datatypes.VectorFieldData field =
                (point, dest) -> dest.set(point.x > 0.0d ? 1.0d : Double.NaN, 0.0d, 0.0d);
        Map<String, Object> outputs = new VectorFieldSamplePointsNode().compute(Map.of(
                "input_field", field,
                "input_points", List.of(new Vector3d(1.0d, 0.0d, 0.0d), new Vector3d(-1.0d, 0.0d, 0.0d))
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(((List<?>) outputs.get("output_vectors")).isEmpty());
        assertEquals(0, outputs.get("output_count"));
    }

    @Test
    void vectorSamplePointAcceptsFiniteComponents() {
        com.nodecraft.nodesystem.datatypes.VectorFieldData field =
                (point, dest) -> dest.set(1.0d, 2.0d, 3.0d);
        Map<String, Object> outputs = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new Vector3d()
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        Vector3d out = (Vector3d) outputs.get("output_vector");
        assertEquals(1.0d, out.x, 0.0d);
        assertEquals(2.0d, out.y, 0.0d);
        assertEquals(3.0d, out.z, 0.0d);
    }

    @Test
    void scalarSamplePointsFailClosedOnSingleNaN() {
        ScalarFieldData field = point -> point.x > 0.0d ? 1.0d : Double.NaN;
        ScalarFieldSamplePointsNode node = new ScalarFieldSamplePointsNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_field", field,
                "input_points", List.of(new Vector3d(1.0d, 0.0d, 0.0d), new Vector3d(-1.0d, 0.0d, 0.0d))
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(((List<?>) outputs.get("output_values")).isEmpty());
        assertEquals(0, outputs.get("output_count"));
    }

    @Test
    void constantFieldsRejectNonFiniteInputs() {
        assertNull(new ScalarFieldConstantNode().compute(Map.of("input_value", Double.POSITIVE_INFINITY)).get("output_field"));
        assertNull(new VectorFieldConstantNode().compute(Map.of(
                "input_x", 1.0d,
                "input_y", Double.NaN,
                "input_z", 0.0d
        )).get("output_field"));
    }

    @Test
    void blendIgnoresExactZeroWeight() {
        com.nodecraft.nodesystem.datatypes.VectorFieldData unitX =
                (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        com.nodecraft.nodesystem.datatypes.VectorFieldData unitY =
                (point, dest) -> dest.set(0.0d, 1.0d, 0.0d);
        AttractorFieldBlendNode blend = new AttractorFieldBlendNode();
        Object fieldObj = blend.compute(Map.of(
                "input_field_a", unitX,
                "input_weight_a", 1.0d,
                "input_field_b", unitY,
                "input_weight_b", 0.0d
        )).get("output_field");
        assertInstanceOf(VectorFieldData.class, fieldObj);
        com.nodecraft.nodesystem.datatypes.VectorFieldData field =
                (com.nodecraft.nodesystem.datatypes.VectorFieldData) fieldObj;
        Vector3d out = new Vector3d();
        field.sampleVector(new Vector3d(), out);
        assertEquals(1.0d, out.x, 0.0d);
        assertEquals(0.0d, out.y, 0.0d);
    }

    @Test
    void blendHonorsTinyNonZeroWeight() {
        com.nodecraft.nodesystem.datatypes.VectorFieldData unitX =
                (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        AttractorFieldBlendNode blend = new AttractorFieldBlendNode();
        Object fieldObj = blend.compute(Map.of(
                "input_field_a", unitX,
                "input_weight_a", 5.0e-10d
        )).get("output_field");
        assertInstanceOf(VectorFieldData.class, fieldObj);
        com.nodecraft.nodesystem.datatypes.VectorFieldData field =
                (com.nodecraft.nodesystem.datatypes.VectorFieldData) fieldObj;
        Vector3d out = new Vector3d();
        field.sampleVector(new Vector3d(), out);
        assertTrue(out.x > 0.0d);
    }

    @Test
    void scalarFieldBinaryOpDivByZeroProducesNaNNotInfinity() {
        ScalarFieldBinaryOpNode op = new ScalarFieldBinaryOpNode();
        op.setNodeState(Map.of("operation", ScalarFieldBinaryOpNode.ScalarBinaryOp.DIV.name()));
        Object one = new ScalarFieldConstantNode().compute(Map.of("input_value", 1.0d)).get("output_field");
        Object zero = new ScalarFieldConstantNode().compute(Map.of("input_value", 0.0d)).get("output_field");
        Object fieldObj = op.compute(Map.of("input_a", one, "input_b", zero)).get("output_field");
        assertInstanceOf(ScalarFieldData.class, fieldObj);
        ScalarFieldData field = (ScalarFieldData) fieldObj;
        assertTrue(Double.isNaN(field.sampleScalar(new Vector3d())));
    }

    @Test
    void randomOpsValueNoiseMatchesFieldNoiseAtScaleZero() {
        double atOrigin = RandomOps.valueNoise3(0.0d, 0.0d, 0.0d, 0);
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        ScalarFieldData field = (ScalarFieldData) node.compute(Map.of(
                "input_seed", 0,
                "input_scale", 0.0d,
                "input_amplitude", 1.0d
        )).get("output_field");
        assertEquals(atOrigin, field.sampleScalar(new Vector3d(5.0d, 5.0d, 5.0d)), 0.0d);
    }

    @Test
    void sdfGradientInvalidStepFallsBackToPropertyDefault() {
        SignedDistanceFieldData plane = point -> point.x;
        Vector3d sampleAt = new Vector3d(0.5d, 0.0d, 0.0d);

        VectorFieldFromSdfGradientNode node = new VectorFieldFromSdfGradientNode();
        node.setNodeState(Map.of("step", 0.5d));

        Vector3d explicit = sampleVectorField(node.compute(Map.of(
                "input_sdf", plane,
                "input_step", 0.5d
        )).get("output_field"), sampleAt);
        Vector3d fallback = sampleVectorField(node.compute(Map.of(
                "input_sdf", plane,
                "input_step", 0.0d
        )).get("output_field"), sampleAt);
        Vector3d fromNaN = sampleVectorField(node.compute(Map.of(
                "input_sdf", plane,
                "input_step", Double.NaN
        )).get("output_field"), sampleAt);

        assertEquals(explicit.x, fallback.x, 1.0e-9d);
        assertEquals(explicit.y, fallback.y, 1.0e-9d);
        assertEquals(explicit.z, fallback.z, 1.0e-9d);
        assertEquals(explicit.x, fromNaN.x, 1.0e-9d);
    }

    @Test
    void sdfGradientTinyStepIsHonoredNotReplacedByPropertyDefault() {
        SignedDistanceFieldData wavy = point -> Math.sin(20.0d * point.x);
        Vector3d sampleAt = new Vector3d(0.15d, 0.07d, 0.0d);

        Vector3d tiny = sampleSdfGradientField(wavy, 1.0e-6d, sampleAt);
        Vector3d propertyDefault = sampleVectorField(new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", wavy
        )).get("output_field"), sampleAt);

        assertTrue(Double.isFinite(tiny.x) && Double.isFinite(tiny.y) && Double.isFinite(tiny.z));
        assertFalse(vectorsEqual(tiny, propertyDefault),
                "Tiny port step must be used instead of property default 0.25");
    }

    @Test
    void pointAttractorInvalidRadiusFallsBackToPropertyDefault() {
        Vector3d center = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d sampleAt = new Vector3d(4.0d, 0.0d, 0.0d);

        Vector3d baseline = samplePointAttractor(Map.of("input_center", center), sampleAt);
        Vector3d withZeroRadius = samplePointAttractor(Map.of(
                "input_center", center,
                "input_radius", 0.0d
        ), sampleAt);
        Vector3d withCustomRadius = samplePointAttractor(Map.of(
                "input_center", center,
                "input_radius", 4.0d
        ), sampleAt);

        assertEquals(baseline.x, withZeroRadius.x, 1.0e-9d);
        assertNotEquals(baseline.x, withCustomRadius.x, 1.0e-9d);
    }

    @Test
    void pointAttractorInvalidStrengthFallsBackToPropertyDefault() {
        Vector3d center = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d sampleAt = new Vector3d(4.0d, 0.0d, 0.0d);

        Vector3d baseline = samplePointAttractor(Map.of("input_center", center), sampleAt);
        Vector3d withNaNStrength = samplePointAttractor(Map.of(
                "input_center", center,
                "input_strength", Double.NaN
        ), sampleAt);
        Vector3d withDoubleStrength = samplePointAttractor(Map.of(
                "input_center", center,
                "input_strength", 2.0d
        ), sampleAt);

        assertEquals(baseline.x, withNaNStrength.x, 1.0e-9d);
        assertEquals(baseline.x * 2.0d, withDoubleStrength.x, 1.0e-9d);
    }

    @Test
    void pointAttractorInvalidExponentFallsBackToPropertyDefault() {
        Vector3d center = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d sampleAt = new Vector3d(4.0d, 0.0d, 0.0d);

        Vector3d baseline = samplePointAttractor(Map.of("input_center", center), sampleAt);
        Vector3d withInvalidExponent = samplePointAttractor(Map.of(
                "input_center", center,
                "input_exponent", -1.0d
        ), sampleAt);
        Vector3d withCustomExponent = samplePointAttractor(Map.of(
                "input_center", center,
                "input_exponent", 1.0d
        ), sampleAt);

        assertEquals(baseline.x, withInvalidExponent.x, 1.0e-9d);
        assertNotEquals(baseline.x, withCustomExponent.x, 1.0e-9d);
    }

    @Test
    void vortexInvalidRadiusFallsBackToPropertyDefault() {
        Vector3d origin = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d axis = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d sampleAt = new Vector3d(2.0d, 0.0d, 0.0d);

        Vector3d baseline = sampleVortexField(Map.of(
                "input_origin", origin,
                "input_axis", axis
        ), sampleAt);
        Vector3d withZeroRadius = sampleVortexField(Map.of(
                "input_origin", origin,
                "input_axis", axis,
                "input_radius", 0.0d
        ), sampleAt);
        Vector3d withCustomRadius = sampleVortexField(Map.of(
                "input_origin", origin,
                "input_axis", axis,
                "input_radius", 2.0d
        ), sampleAt);

        assertEquals(baseline.z, withZeroRadius.z, 1.0e-9d);
        assertNotEquals(baseline.z, withCustomRadius.z, 1.0e-9d);
    }

    @Test
    void repulsorInvalidStrengthFallsBackToPropertyDefault() {
        VectorFieldData source = (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        Vector3d sampleAt = new Vector3d();

        Vector3d baseline = sampleRepulsorField(Map.of("input_field", source), sampleAt);
        Vector3d withNaNStrength = sampleRepulsorField(Map.of(
                "input_field", source,
                "input_strength", Double.NaN
        ), sampleAt);
        Vector3d withDoubleStrength = sampleRepulsorField(Map.of(
                "input_field", source,
                "input_strength", 2.0d
        ), sampleAt);

        assertEquals(baseline.x, withNaNStrength.x, 1.0e-9d);
        assertEquals(baseline.x * 2.0d, withDoubleStrength.x, 1.0e-9d);
    }

    @Test
    void curveAttractorInvalidRadiusFallsBackToPropertyDefault() {
        PathData path = PathData.fromPolyline(new PolylineData(List.of(
                new Vec3d(0.0d, 0.0d, 0.0d),
                new Vec3d(10.0d, 0.0d, 0.0d)
        )));
        Vector3d sampleAt = new Vector3d(5.0d, 2.0d, 0.0d);

        Vector3d baseline = sampleCurveAttractor(Map.of("input_path", path), sampleAt);
        Vector3d withZeroRadius = sampleCurveAttractor(Map.of(
                "input_path", path,
                "input_radius", 0.0d
        ), sampleAt);
        Vector3d withCustomRadius = sampleCurveAttractor(Map.of(
                "input_path", path,
                "input_radius", 2.0d
        ), sampleAt);

        assertEquals(baseline.y, withZeroRadius.y, 1.0e-9d);
        assertNotEquals(baseline.y, withCustomRadius.y, 1.0e-9d);
    }

    @Test
    void volumeAttractorInvalidStrengthAndRadiusFallBackToPropertyDefaults() {
        VolumeAttractorFieldNode node = new VolumeAttractorFieldNode();
        node.setNodeState(Map.of("pullMode", VolumeAttractorFieldNode.PullMode.CENTER_PULL.name()));

        Vector3d center = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d sampleAt = new Vector3d(4.0d, 0.0d, 0.0d);

        Vector3d baseline = sampleVolumeAttractor(node, Map.of("input_center", center), sampleAt);
        Vector3d withInvalid = sampleVolumeAttractor(node, Map.of(
                "input_center", center,
                "input_strength", Double.NaN,
                "input_radius", 0.0d
        ), sampleAt);
        Vector3d withOverrides = sampleVolumeAttractor(node, Map.of(
                "input_center", center,
                "input_strength", 2.0d,
                "input_radius", 4.0d
        ), sampleAt);

        assertEquals(baseline.x, withInvalid.x, 1.0e-9d);
        assertNotEquals(baseline.x, withOverrides.x, 1.0e-9d);
    }

    @Test
    void volumeAttractorInvalidSdfStepFallsBackToPropertyDefault() {
        SignedDistanceFieldData wavy = point -> Math.sin(20.0d * point.x);
        Vector3d sampleAt = new Vector3d(0.15d, 0.0d, 0.05d);

        VolumeAttractorFieldNode node = new VolumeAttractorFieldNode();
        node.setNodeState(Map.of(
                "pullMode", VolumeAttractorFieldNode.PullMode.SURFACE_PULL.name(),
                "sdfStep", 0.5d
        ));

        Vector3d explicit = sampleVolumeAttractor(node, Map.of(
                "input_sdf", wavy,
                "input_sdf_step", 0.5d
        ), sampleAt);
        Vector3d fallback = sampleVolumeAttractor(node, Map.of(
                "input_sdf", wavy,
                "input_sdf_step", 0.0d
        ), sampleAt);
        Vector3d tiny = sampleVolumeAttractor(node, Map.of(
                "input_sdf", wavy,
                "input_sdf_step", 1.0e-6d
        ), sampleAt);

        assertEquals(explicit.x, fallback.x, 1.0e-9d);
        assertEquals(explicit.y, fallback.y, 1.0e-9d);
        assertFalse(vectorsEqual(explicit, tiny));
    }

    @Test
    void currentGraphFormatIsV34() {
        assertEquals(33, GraphFormatVersion.V33);
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V53, GraphFormatVersion.CURRENT);
    }

    @Test
    void v29ToV30DropsIncompatibleScalarSamplePointsWires() {
        SavedGraph v29 = new SavedGraph();
        v29.formatVersion = GraphFormatVersion.V29;

        SavedNode sampler = savedNode("ssp", "math.fields.scalar_sample_points");
        SavedNode sortText = savedNode("sort", "math.list.sort_text");
        SavedNode createList = savedNode("clist", "math.list.create_list");
        SavedNode field = savedNode("field", "math.fields.scalar_constant");

        v29.nodes = new ArrayList<>(List.of(sampler, sortText, createList, field));
        v29.connections = new ArrayList<>(List.of(
                wire("ssp", "output_values", "sort", "input_list"),
                wire("ssp", "output_values", "clist", "input_0"),
                wire("field", "output_field", "ssp", "input_field")
        ));
        v29.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v29);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertFalse(hasWire(migrated, "ssp", "output_values", "sort", "input_list"));
        assertTrue(hasWire(migrated, "ssp", "output_values", "clist", "input_0"));
        assertTrue(hasWire(migrated, "field", "output_field", "ssp", "input_field"));
    }

    private static Vector3d sampleSdfGradientField(SignedDistanceFieldData sdf, double step, Vector3d point) {
        VectorFieldFromSdfGradientNode node = new VectorFieldFromSdfGradientNode();
        Object fieldObj = node.compute(Map.of(
                "input_sdf", sdf,
                "input_step", step
        )).get("output_field");
        return sampleVectorField(fieldObj, point);
    }

    private static Vector3d samplePointAttractor(Map<String, Object> inputs, Vector3d point) {
        return sampleNodeField(new PointAttractorFieldNode(), inputs, point);
    }

    private static Vector3d sampleVortexField(Map<String, Object> inputs, Vector3d point) {
        return sampleNodeField(new VortexFieldNode(), inputs, point);
    }

    private static Vector3d sampleRepulsorField(Map<String, Object> inputs, Vector3d point) {
        return sampleNodeField(new RepulsorFieldNode(), inputs, point);
    }

    private static Vector3d sampleCurveAttractor(Map<String, Object> inputs, Vector3d point) {
        return sampleNodeField(new CurveAttractorFieldNode(), inputs, point);
    }

    private static Vector3d sampleVolumeAttractor(VolumeAttractorFieldNode node, Map<String, Object> inputs,
                                                   Vector3d point) {
        return sampleNodeField(node, inputs, point);
    }

    private static Vector3d sampleNodeField(BaseNode node, Map<String, Object> inputs, Vector3d point) {
        Object fieldObj = node.compute(inputs).get("output_field");
        assertNotNull(fieldObj, node.getTypeId() + " should emit output_field");
        return sampleVectorField(fieldObj, point);
    }

    private static Vector3d sampleVectorField(Object fieldObj, Vector3d point) {
        assertInstanceOf(VectorFieldData.class, fieldObj);
        Vector3d out = new Vector3d();
        ((VectorFieldData) fieldObj).sampleVector(point, out);
        return out;
    }

    private static boolean vectorsEqual(Vector3d a, Vector3d b) {
        return Math.abs(a.x - b.x) <= 1.0e-9d
                && Math.abs(a.y - b.y) <= 1.0e-9d
                && Math.abs(a.z - b.z) <= 1.0e-9d;
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
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

    private static boolean hasWire(SavedGraph graph, String sourceNode, String sourcePort,
                                   String targetNode, String targetPort) {
        return graph.connections.stream().anyMatch(c ->
                sourceNode.equals(c.sourceNodeId)
                        && sourcePort.equalsIgnoreCase(c.sourcePortId)
                        && targetNode.equals(c.targetNodeId)
                        && targetPort.equalsIgnoreCase(c.targetPortId));
    }
}
