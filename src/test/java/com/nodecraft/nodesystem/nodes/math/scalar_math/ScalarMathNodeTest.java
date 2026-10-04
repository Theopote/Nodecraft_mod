package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.datatypes.NumericRangeData;
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
    void divisionAcceptsTinyNonZeroDivisor() {
        DivisionNode node = new DivisionNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1.0d,
            "input_b", 1.0e-11d
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0e11d, (Double) outputs.get("output_quotient"), 1.0e-3);
    }

    @Test
    void subtractionOverflowProducesNan() {
        SubtractionNode node = new SubtractionNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", Double.MAX_VALUE,
            "input_b", -Double.MAX_VALUE
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_difference")));
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
            "input_domain", new NumericRangeData(0.0d, 1.0d)
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void additionRejectsNonFiniteInputs() {
        AdditionNode node = new AdditionNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", Double.POSITIVE_INFINITY,
            "input_b", 1.0d
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_sum")));
    }

    @Test
    void remapAcceptsFiniteInput() {
        RemapNode node = new RemapNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", 0.5d,
            "input_source", new NumericRangeData(0.0d, 1.0d),
            "input_target", new NumericRangeData(0.0d, 10.0d)
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(5.0d, (Double) outputs.get("output_result"), 1.0e-12);
    }

    @Test
    void remapRejectsDegenerateInputRange() {
        RemapNode node = new RemapNode();

        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", 0.5d,
            "input_source", new NumericRangeData(1.0d, 1.0d),
            "input_target", new NumericRangeData(0.0d, 10.0d)
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_result")));
        assertEquals("degenerate_domain", outputs.get("output_error"));
    }

    @Test
    void remapClampInvalidFailsClosed() {
        RemapNode node = new RemapNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", 0.5d,
            "input_source", new NumericRangeData(0.0d, 1.0d),
            "input_target", new NumericRangeData(0.0d, 10.0d),
            "input_clamp", "true"
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals("invalid_input", outputs.get("output_error"));
    }

    @Test
    void remapExtremeSourceIntervalMatchesSmoothstepT() {
        NumericRangeData extreme = new NumericRangeData(-1.0e308d, 1.0e308d);
        Map<String, Object> remap = new RemapNode().compute(Map.of(
            "input_value", 0.0d,
            "input_source", extreme,
            "input_target", new NumericRangeData(0.0d, 1.0d),
            "input_clamp", false
        ));
        assertTrue((Boolean) remap.get("output_valid"));
        assertEquals(0.5d, (Double) remap.get("output_result"), 1.0e-6);

        SmoothstepNode smooth = new SmoothstepNode();
        Map<String, Object> t = smooth.compute(Map.of(
            "input_value", 0.0d,
            "input_edge0", -1.0e308d,
            "input_edge1", 1.0e308d
        ));
        assertTrue((Boolean) t.get("output_valid"));
        assertEquals((Double) t.get("output_t"), (Double) remap.get("output_result"), 1.0e-12);
    }

    @Test
    void additionRejectsIntegerCoercion() {
        AdditionNode node = new AdditionNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1,
            "input_b", 2.0d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_sum")));
    }
}
