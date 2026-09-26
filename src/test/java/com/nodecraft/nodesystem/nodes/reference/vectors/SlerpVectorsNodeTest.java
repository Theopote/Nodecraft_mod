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
    void antiparallelIntermediateTrajectoryIsContinuousSemicircle() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        node.setNodeState(Map.of("preserveMagnitude", false));

        Vector3d a = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d b = new Vector3d(-1.0d, 0.0d, 0.0d);

        Vector3d at025 = requireVector(node.compute(Map.of(
            "input_a", a, "input_b", b, "input_t", 0.25d
        )).get("output_result"));
        Vector3d at05 = requireVector(node.compute(Map.of(
            "input_a", a, "input_b", b, "input_t", 0.5d
        )).get("output_result"));
        Vector3d at075 = requireVector(node.compute(Map.of(
            "input_a", a, "input_b", b, "input_t", 0.75d
        )).get("output_result"));

        assertEquals(1.0d, at025.length(), 1.0e-6d);
        assertEquals(1.0d, at05.length(), 1.0e-6d);
        assertEquals(1.0d, at075.length(), 1.0e-6d);

        assertTrue(Math.abs(at025.dot(a)) < 1.0d - 1.0e-3d);
        assertTrue(Math.abs(at025.dot(b)) < 1.0d - 1.0e-3d);
        assertEquals(0.0d, at05.dot(a), 1.0e-6d);
        assertTrue(Math.abs(at075.dot(a)) < 1.0d - 1.0e-3d);
        assertTrue(Math.abs(at075.dot(b)) < 1.0d - 1.0e-3d);

        assertTrue(at025.dot(at05) > 0.0d);
        assertTrue(at05.dot(at075) > 0.0d);
    }

    @Test
    void nearlyOppositeVectorsAvoidNaNAndLeaveStart() {
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
        assertEquals(1.0d, result.length(), 1.0e-6d);
        assertTrue(Math.abs(result.dot(new Vector3d(1, 0, 0))) < 1.0d - 1.0e-3d);
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
