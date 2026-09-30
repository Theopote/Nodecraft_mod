package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.fields.AttractorFieldBlendNode;
import com.nodecraft.nodesystem.nodes.math.fields.CurveAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.PointAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.VolumeAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.VortexFieldNode;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field Attractor Robustness & Numerical Consistency v2 (Graph V135).
 */
class FieldAttractorRobustnessLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV135() {
        assertEquals(135, GraphFormatVersion.V135);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V135);
    }

    @Test
    void surfacePullSdfOnlyWithoutCenterDoesNotFallBackToWorldOrigin() {
        SignedDistanceFieldData nanSdf = point -> Double.NaN;
        VolumeAttractorFieldNode node = new VolumeAttractorFieldNode();
        node.setNodeState(Map.of("pullMode", VolumeAttractorFieldNode.PullMode.SURFACE_PULL.name()));

        Map<String, Object> built = node.compute(Map.of("input_sdf", nanSdf));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Vector3d dest = new Vector3d();
        field.sampleVector(new Vector3d(3.0d, 4.0d, 5.0d), dest);
        assertTrue(Double.isNaN(dest.x) && Double.isNaN(dest.y) && Double.isNaN(dest.z),
                "Must not invent world-origin attraction when surface fails and center is absent");

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(3.0d, 4.0d, 5.0d)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
        assertNull(sample.get("output_vector"));
    }

    @Test
    void sdfNaNSurfacePullReportsFailureWithoutBogusSuccess() {
        SignedDistanceFieldData nanSdf = point -> Double.NaN;
        VolumeAttractorFieldNode node = new VolumeAttractorFieldNode();
        node.setNodeState(Map.of("pullMode", VolumeAttractorFieldNode.PullMode.SURFACE_PULL.name()));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class,
                node.compute(Map.of("input_sdf", nanSdf)).get("output_field"));

        Vector3d dest = new Vector3d();
        field.sampleVector(new Vector3d(0.5d, 0.0d, 0.0d), dest);
        assertTrue(Double.isNaN(dest.x));
    }

    @Test
    void sdfGradientOverflowSurfacePullFails() {
        SignedDistanceFieldData overflow = point ->
                (Double.MAX_VALUE * 0.5d) * (point.x + point.y + point.z);
        VolumeAttractorFieldNode node = new VolumeAttractorFieldNode();
        node.setNodeState(Map.of(
                "pullMode", VolumeAttractorFieldNode.PullMode.SURFACE_PULL.name(),
                "sdfStep", 1.0d
        ));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class,
                node.compute(Map.of("input_sdf", overflow, "input_sdf_step", 1.0d)).get("output_field"));

        Vector3d dest = new Vector3d();
        field.sampleVector(new Vector3d(0, 0, 0), dest);
        assertTrue(Double.isNaN(dest.x) && Double.isNaN(dest.y) && Double.isNaN(dest.z));
    }

    @Test
    void flatSdfWithoutCenterIsInvalid_withCenterFallsBackToCenterPull() {
        SignedDistanceFieldData flat = point -> 1.0d;
        VolumeAttractorFieldNode node = new VolumeAttractorFieldNode();
        node.setNodeState(Map.of("pullMode", VolumeAttractorFieldNode.PullMode.SURFACE_PULL.name()));

        VectorFieldData noCenter = assertInstanceOf(VectorFieldData.class,
                node.compute(Map.of("input_sdf", flat)).get("output_field"));
        Vector3d invalid = new Vector3d();
        noCenter.sampleVector(new Vector3d(2.0d, 0.0d, 0.0d), invalid);
        assertTrue(Double.isNaN(invalid.x));

        VectorFieldData withCenter = assertInstanceOf(VectorFieldData.class,
                node.compute(Map.of(
                        "input_sdf", flat,
                        "input_center", new Vector3d(0.0d, 0.0d, 0.0d)
                )).get("output_field"));
        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", withCenter,
                "input_point", new PointData(2.0d, 0.0d, 0.0d)
        ));
        assertTrue((Boolean) sample.get("output_valid"));
        Vector3d vector = assertInstanceOf(Vector3d.class, sample.get("output_vector"));
        assertTrue(vector.x < 0.0d, "Center pull should attract toward origin from +X");
    }

    @Test
    void pointAttractorAtCenterYieldsZeroVector() {
        Vector3d center = new Vector3d(1.0d, 2.0d, 3.0d);
        VectorFieldData field = assertInstanceOf(VectorFieldData.class,
                new PointAttractorFieldNode().compute(Map.of("input_center", center)).get("output_field"));

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(1.0d, 2.0d, 3.0d)
        ));
        assertTrue((Boolean) sample.get("output_valid"));
        Vector3d vector = assertInstanceOf(Vector3d.class, sample.get("output_vector"));
        assertEquals(0.0d, vector.x, 0.0d);
        assertEquals(0.0d, vector.y, 0.0d);
        assertEquals(0.0d, vector.z, 0.0d);
    }

    @Test
    void vortexZeroAxisFailsConstruction() {
        Map<String, Object> outputs = new VortexFieldNode().compute(Map.of(
                "input_origin", new Vector3d(0, 0, 0),
                "input_axis", new Vector3d(0, 0, 0)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertNull(outputs.get("output_field"));
    }

    @Test
    void vortexInfiniteAxisFailsConstruction() {
        Map<String, Object> outputs = new VortexFieldNode().compute(Map.of(
                "input_origin", new Vector3d(0, 0, 0),
                "input_axis", new Vector3d(Double.POSITIVE_INFINITY, 0, 0)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertNull(outputs.get("output_field"));
    }

    @Test
    void blendTinyNonZeroWeightStillContributes() {
        VectorFieldData unitX = (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        VectorFieldData unitY = (point, dest) -> dest.set(0.0d, 1.0d, 0.0d);
        Map<String, Object> built = new AttractorFieldBlendNode().compute(Map.of(
                "input_field_a", unitX,
                "input_field_b", unitY,
                "input_weight_a", 5.0e-10d,
                "input_weight_b", 0.0d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));
        Vector3d out = new Vector3d();
        field.sampleVector(new Vector3d(), out);
        assertTrue(out.x > 0.0d);
    }

    @Test
    void blendNormalizeOnTinySumYieldsZeroNotBogusUnit() {
        VectorFieldData tiny = (point, dest) -> dest.set(1.0e-20d, 0.0d, 0.0d);
        AttractorFieldBlendNode blend = new AttractorFieldBlendNode();
        blend.setNodeState(Map.of("normalize", true));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class,
                blend.compute(Map.of(
                        "input_field_a", tiny,
                        "input_weight_a", 1.0d
                )).get("output_field"));

        Vector3d out = new Vector3d();
        field.sampleVector(new Vector3d(), out);
        assertEquals(0.0d, out.x, 0.0d);
        assertEquals(0.0d, out.y, 0.0d);
        assertEquals(0.0d, out.z, 0.0d);
    }

    @Test
    void blendTinyPositiveMaxMagnitudeStillClamps() {
        VectorFieldData unitX = (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        AttractorFieldBlendNode blend = new AttractorFieldBlendNode();
        blend.setNodeState(Map.of("maxMagnitude", 1.0e-12d));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class,
                blend.compute(Map.of(
                        "input_field_a", unitX,
                        "input_weight_a", 1.0d
                )).get("output_field"));

        Vector3d out = new Vector3d();
        field.sampleVector(new Vector3d(), out);
        assertEquals(1.0e-12d, out.x, 1.0e-18d);
        assertEquals(0.0d, out.y, 0.0d);
        assertEquals(0.0d, out.z, 0.0d);
    }

    @Test
    void pathAttractorWithZeroLengthMiddleSegmentStillAttracts() {
        PathData path = PathData.fromPolyline(new PolylineData(List.of(
                new Vec3d(0.0d, 0.0d, 0.0d),
                new Vec3d(5.0d, 0.0d, 0.0d),
                new Vec3d(5.0d, 0.0d, 0.0d),
                new Vec3d(10.0d, 0.0d, 0.0d)
        )));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class,
                new CurveAttractorFieldNode().compute(Map.of("input_path", path)).get("output_field"));

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(5.0d, 2.0d, 0.0d)
        ));
        assertTrue((Boolean) sample.get("output_valid"));
        Vector3d vector = assertInstanceOf(Vector3d.class, sample.get("output_vector"));
        assertTrue(Math.abs(vector.y) > 0.0d || Math.abs(vector.x) > 0.0d);
        assertNotNull(vector);
    }
}
