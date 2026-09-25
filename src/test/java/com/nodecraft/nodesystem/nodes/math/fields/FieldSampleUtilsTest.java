package com.nodecraft.nodesystem.nodes.math.fields;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

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
}
