package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldSampleUtilsTest {

    @Test
    void sampleScalarRejectsNonFinite() {
        var sample = FieldSampleUtils.sampleScalar(point -> Double.NaN, new Vector3d());
        assertFalse(sample.valid());
        assertTrue(Double.isNaN(sample.value()));
    }

    @Test
    void sampleVectorRejectsNonFiniteComponent() {
        var sample = FieldSampleUtils.sampleVector((point, dest) -> dest.set(Double.NaN, 1.0d, 2.0d), new Vector3d());
        assertFalse(sample.valid());
        assertNull(sample.vector());
    }

    @Test
    void allFiniteScalarsChecksEveryElement() {
        assertTrue(FieldSampleUtils.allFiniteScalars(java.util.List.of(1.0d, 2.0d)));
        assertFalse(FieldSampleUtils.allFiniteScalars(java.util.List.of(1.0d, Double.NaN)));
    }

    @Test
    void resolvePointListStrictRejectsSkippedElements() {
        List<Object> mixed = List.of(new PointData(0, 0, 0), "bad");
        FieldSampleUtils.PointListResult result = FieldSampleUtils.resolvePointListStrict(mixed);
        assertFalse(result.valid());
        assertEquals(FieldSampleUtils.ERROR_INVALID_POINTS, result.error());
    }

    @Test
    void scalarSamplePointsExceedsBudgetFailsClosed() {
        ScalarFieldData field = point -> 1.0d;
        ScalarFieldSamplePointsNode node = new ScalarFieldSamplePointsNode();
        node.samplePointLimit = 2;
        Map<String, Object> outputs = node.compute(Map.of(
                "input_field", field,
                "input_points", List.of(
                        new PointData(0, 0, 0),
                        new PointData(1, 0, 0),
                        new PointData(2, 0, 0)
                )
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FieldSampleUtils.ERROR_OUTPUT_BUDGET_EXCEEDED, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_count"));
    }
}
