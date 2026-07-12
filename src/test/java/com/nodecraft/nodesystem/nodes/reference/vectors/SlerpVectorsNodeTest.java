package com.nodecraft.nodesystem.nodes.reference.vectors;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlerpVectorsNodeTest {

    @Test
    void longPathOppositeVectorsUseDegenerateFallback() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        node.setNodeState(Map.of("shortestPath", false, "preserveMagnitude", false));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", new Vector3d(1.0d, 0.0d, 0.0d),
            "input_b", new Vector3d(-1.0d, 0.0d, 0.0d),
            "input_t", 0.5d
        ));

        assertEquals(true, outputs.get("output_valid"));
        Vector3d result = (Vector3d) outputs.get("output_result");
        assertTrue(Double.isFinite(result.x));
        assertTrue(Double.isFinite(result.y));
        assertTrue(Double.isFinite(result.z));
        assertTrue(result.lengthSquared() > 0.0d);
    }

    @Test
    void longPathNearlyOppositeVectorsAvoidNaN() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        node.setNodeState(Map.of("shortestPath", false, "preserveMagnitude", false));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", new Vector3d(1.0d, 0.0d, 0.0d),
            "input_b", new Vector3d(-0.999999999d, 0.0d, 0.0d),
            "input_t", 0.25d
        ));

        assertEquals(true, outputs.get("output_valid"));
        Vector3d result = (Vector3d) outputs.get("output_result");
        assertTrue(Double.isFinite(result.x));
        assertTrue(Double.isFinite(result.y));
        assertTrue(Double.isFinite(result.z));
    }
}
