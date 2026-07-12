package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.nodes.math.scalar_math.LogarithmNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRestrictedMathNodeTest {

    @Test
    void arcCosRejectsOutOfDomainInput() {
        ArcCosNode node = new ArcCosNode();

        Map<String, Object> outputs = node.compute(Map.of("input_value", 1.0000000002d));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_angle_rad")));
    }

    @Test
    void arcSinAcceptsInDomainInput() {
        ArcSinNode node = new ArcSinNode();

        Map<String, Object> outputs = node.compute(Map.of("input_value", 0.5d));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(Math.asin(0.5d), (Double) outputs.get("output_angle_rad"), 1.0e-12);
    }

    @Test
    void logarithmRejectsNonPositiveNumber() {
        LogarithmNode node = new LogarithmNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_number", 0.0d,
            "input_base", 10.0d
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_logarithm")));
    }

    @Test
    void logarithmAcceptsValidInputs() {
        LogarithmNode node = new LogarithmNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_number", 100.0d,
            "input_base", 10.0d
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(2.0d, (Double) outputs.get("output_logarithm"), 1.0e-12);
    }
}
