package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.fields.FieldSampleUtils;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldBinaryOpNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldFromSdfGradientNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointNode;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field Vector Numerical Stability & Strict Sampling v2 (Graph V134).
 */
class FieldVectorNumericalLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV134() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void sdfSampleNaNYieldsInvalidGradientSample() {
        SignedDistanceFieldData nanSdf = point -> Double.NaN;
        Map<String, Object> built = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", nanSdf,
                "input_step", 0.25d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(0, 0, 0)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
        assertNull(sample.get("output_vector"));
    }

    @Test
    void sdfDifferenceInfinityOverflowYieldsInvalidSample() {
        SignedDistanceFieldData overflowSdf = point ->
                point.x >= 0.0d ? Double.MAX_VALUE : -Double.MAX_VALUE;
        Map<String, Object> built = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", overflowSdf,
                "input_step", 0.25d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(0, 0, 0)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
        assertNull(sample.get("output_vector"));
    }

    @Test
    void finiteHugeGradientComponentsDoNotYieldBogusUnitVector() {
        // Central difference → finite (MAX,MAX,MAX); chained hypot overflows → NaN dest.
        SignedDistanceFieldData hugeGrad = point ->
                (Double.MAX_VALUE * 0.5d) * (point.x + point.y + point.z);
        Map<String, Object> built = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", hugeGrad,
                "input_step", 1.0d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Vector3d dest = new Vector3d();
        field.sampleVector(new Vector3d(0, 0, 0), dest);
        assertTrue(Double.isNaN(dest.x) && Double.isNaN(dest.y) && Double.isNaN(dest.z),
                "Overflowing normalize must not invent a unit vector");

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(0, 0, 0)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
    }

    @Test
    void hugeCoordsWithTinyStepAreInvalidNotZero() {
        SignedDistanceFieldData plane = point -> point.x;
        Map<String, Object> built = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", plane,
                "input_step", 1.0e-6d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Vector3d dest = new Vector3d();
        field.sampleVector(new Vector3d(1.0e20d, 0.0d, 0.0d), dest);
        assertTrue(Double.isNaN(dest.x) && Double.isNaN(dest.y) && Double.isNaN(dest.z),
                "Unresolvable perturbation must fail, not look like flat zero");

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(1.0e20d, 0.0d, 0.0d)
        ));
        assertFalse((Boolean) sample.get("output_valid"));
    }

    @Test
    void trueConstantSdfYieldsZeroVectorAndValidSample() {
        SignedDistanceFieldData flat = point -> 1.0d;
        Map<String, Object> built = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", flat,
                "input_step", 0.25d
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(0.5d, -1.0d, 2.0d)
        ));
        assertTrue((Boolean) sample.get("output_valid"));
        Vector3d vector = assertInstanceOf(Vector3d.class, sample.get("output_vector"));
        assertEquals(0.0d, vector.x, 0.0d);
        assertEquals(0.0d, vector.y, 0.0d);
        assertEquals(0.0d, vector.z, 0.0d);
    }

    @Test
    void samplePointNaNCoordinateRejectsEvenConstantField() {
        ScalarFieldData scalar = assertInstanceOf(ScalarFieldData.class,
                new ScalarFieldConstantNode().compute(Map.of("input_value", 3.0d)).get("output_field"));
        Map<String, Object> scalarSample = new ScalarFieldSamplePointNode().compute(Map.of(
                "input_field", scalar,
                "input_point", new PointData(Double.NaN, 0.0d, 0.0d)
        ));
        assertFalse((Boolean) scalarSample.get("output_valid"));
        assertTrue(Double.isNaN((Double) scalarSample.get("output_value")));

        VectorFieldData vector = assertInstanceOf(VectorFieldData.class,
                new VectorFieldConstantNode().compute(Map.of(
                        "input_x", 1.0d,
                        "input_y", 0.0d,
                        "input_z", 0.0d
                )).get("output_field"));
        Map<String, Object> vectorSample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", vector,
                "input_point", new PointData(0.0d, Double.NaN, 0.0d)
        ));
        assertFalse((Boolean) vectorSample.get("output_valid"));
        assertNull(vectorSample.get("output_vector"));
    }

    @Test
    void vectorConstantDrivenNaNComponentFailsConstruction() {
        Map<String, Object> outputs = new VectorFieldConstantNode().compute(Map.of(
                "input_x", 1.0d,
                "input_y", Double.NaN,
                "input_z", 0.0d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_INPUT, outputs.get("output_error"));
        assertNull(outputs.get("output_field"));
    }

    @Test
    void combineCrossProductRightHandRule() {
        VectorFieldData unitX = (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        VectorFieldData unitY = (point, dest) -> dest.set(0.0d, 1.0d, 0.0d);
        VectorFieldBinaryOpNode combine = new VectorFieldBinaryOpNode();
        combine.setNodeState(Map.of("operation", VectorFieldBinaryOpNode.VectorBinaryOp.CROSS.name()));
        Map<String, Object> built = combine.compute(Map.of(
                "input_a", unitX,
                "input_b", unitY
        ));
        assertTrue((Boolean) built.get("output_valid"));
        VectorFieldData field = assertInstanceOf(VectorFieldData.class, built.get("output_field"));

        Map<String, Object> sample = new VectorFieldSamplePointNode().compute(Map.of(
                "input_field", field,
                "input_point", new PointData(0, 0, 0)
        ));
        assertTrue((Boolean) sample.get("output_valid"));
        Vector3d vector = assertInstanceOf(Vector3d.class, sample.get("output_vector"));
        assertEquals(0.0d, vector.x, 1.0e-12d);
        assertEquals(0.0d, vector.y, 1.0e-12d);
        assertEquals(1.0d, vector.z, 1.0e-12d);
    }

    @Test
    void combineMissingBFailsConstruction() {
        VectorFieldData unitX = (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        Map<String, Object> outputs = new VectorFieldBinaryOpNode().compute(Map.of(
                "input_a", unitX
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_INVALID_FIELD, outputs.get("output_error"));
        assertNull(outputs.get("output_field"));
    }

    @Test
    void tinyDrivenStepIsHonoredNotReplacedByProperty() {
        SignedDistanceFieldData wavy = point -> Math.sin(20.0d * point.x);
        Vector3d sampleAt = new Vector3d(0.15d, 0.07d, 0.0d);

        Map<String, Object> tinyBuilt = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", wavy,
                "input_step", 1.0e-6d
        ));
        assertTrue((Boolean) tinyBuilt.get("output_valid"));
        VectorFieldData tinyField = assertInstanceOf(VectorFieldData.class, tinyBuilt.get("output_field"));
        Vector3d tiny = new Vector3d();
        tinyField.sampleVector(sampleAt, tiny);
        assertTrue(Double.isFinite(tiny.x) && Double.isFinite(tiny.y) && Double.isFinite(tiny.z));

        Map<String, Object> defaultBuilt = new VectorFieldFromSdfGradientNode().compute(Map.of(
                "input_sdf", wavy
        ));
        assertTrue((Boolean) defaultBuilt.get("output_valid"));
        VectorFieldData defaultField = assertInstanceOf(VectorFieldData.class, defaultBuilt.get("output_field"));
        Vector3d propertyDefault = new Vector3d();
        defaultField.sampleVector(sampleAt, propertyDefault);

        assertFalse(Math.abs(tiny.x - propertyDefault.x) <= 1.0e-9d
                        && Math.abs(tiny.y - propertyDefault.y) <= 1.0e-9d
                        && Math.abs(tiny.z - propertyDefault.z) <= 1.0e-9d,
                "Tiny port step must be used instead of property default 0.25");
        assertNotNull(tinyField);
    }
}
