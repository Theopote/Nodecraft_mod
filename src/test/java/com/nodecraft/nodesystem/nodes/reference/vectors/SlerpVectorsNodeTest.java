package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.util.VectorUtils;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlerpVectorsNodeTest {

    @Test
    void endpointsPreserveDirectionsWhenDotIsNegative() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        node.setNodeState(Map.of("preserveMagnitude", false));

        Vector3d a = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d b = new Vector3d(-0.8d, 0.6d, 0.0d);
        Vector3d expectedB = new Vector3d(b).normalize();

        Map<String, Object> atZero = node.compute(Map.of(
            "input_a", a,
            "input_b", b,
            "input_t", 0.0d
        ));
        assertEquals(Boolean.TRUE, atZero.get("output_valid"));
        assertVectorClose(a, requireVector(atZero.get("output_result")), 1.0e-6d);

        Map<String, Object> atOne = node.compute(Map.of(
            "input_a", a,
            "input_b", b,
            "input_t", 1.0d
        ));
        assertEquals(Boolean.TRUE, atOne.get("output_valid"));
        assertVectorClose(expectedB, requireVector(atOne.get("output_result")), 1.0e-6d);
    }

    @Test
    void antiparallelVectorsPreserveDeterministicEndpoints() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        node.setNodeState(Map.of("preserveMagnitude", false));

        Vector3d a = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d b = new Vector3d(-1.0d, 0.0d, 0.0d);

        Map<String, Object> atZero = node.compute(Map.of(
            "input_a", a,
            "input_b", b,
            "input_t", 0.0d
        ));
        assertEquals(Boolean.TRUE, atZero.get("output_valid"));
        assertVectorClose(a, requireVector(atZero.get("output_result")), 1.0e-6d);

        Map<String, Object> atOne = node.compute(Map.of(
            "input_a", a,
            "input_b", b,
            "input_t", 1.0d
        ));
        assertEquals(Boolean.TRUE, atOne.get("output_valid"));
        assertVectorClose(b, requireVector(atOne.get("output_result")), 1.0e-6d);
    }

    @Test
    void nearlyOppositeVectorsAvoidNaN() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        node.setNodeState(Map.of("preserveMagnitude", false));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", new Vector3d(1.0d, 0.0d, 0.0d),
            "input_b", new Vector3d(-0.999999999d, 0.0d, 0.0d),
            "input_t", 0.25d
        ));

        assertEquals(Boolean.TRUE, outputs.get("output_valid"));
        Vector3d result = requireVector(outputs.get("output_result"));
        assertNotNull(result);
        assertTrue(Double.isFinite(result.x));
        assertTrue(Double.isFinite(result.y));
        assertTrue(Double.isFinite(result.z));
    }

    private static Vector3d requireVector(Object value) {
        Vector3d vector = VectorUtils.toVector(value);
        assertNotNull(vector);
        return vector;
    }

    private static void assertVectorClose(Vector3d expected, Vector3d actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }
}
