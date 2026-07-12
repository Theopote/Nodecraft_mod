package com.nodecraft.nodesystem.nodes.math.scalar_math;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalarMathNodeTest {

    @Test
    void divisionRejectsZeroDivisor() {
        DivisionNode node = new DivisionNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 10.0d,
            "input_b", 0.0d
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_quotient")));
    }

    @Test
    void powerRejectsNonFiniteResult() {
        PowerNode node = new PowerNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_base", -1.0d,
            "input_exponent", 0.5d
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void clampRejectsNonFiniteInput() {
        ClampNode node = new ClampNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", Double.POSITIVE_INFINITY,
            "input_min", 0.0d,
            "input_max", 1.0d
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void remapAcceptsFiniteInput() {
        RemapNode node = new RemapNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", 0.5d,
            "input_in_min", 0.0d,
            "input_in_max", 1.0d,
            "input_out_min", 0.0d,
            "input_out_max", 10.0d
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(5.0d, (Double) outputs.get("output_result"), 1.0e-12);
    }
}
