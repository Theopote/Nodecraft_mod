package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.scalar_math.ExpressionNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.ArcCosNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.Atan2Node;
import com.nodecraft.nodesystem.nodes.math.trigonometry.CosineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.CoshNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.TangentNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.TanhNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trigonometry Strict Inputs & Numerical Boundaries Contract v2 (Graph V124).
 */
class TrigonometryLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV124() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void integerInputRejectedAtRuntime() {
        SineProbe probe = new SineProbe();
        probe.putInput("input_angle", 90);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(Double.isNaN((Double) probe.getOutput("output_sine")));
    }

    @Test
    void longBeyondDoubleExactRangeRejectedAtRuntime() {
        SineProbe probe = new SineProbe();
        probe.putInput("input_angle", 9007199254740993L);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void tanNegative270IsInvalid() {
        TangentNode node = new TangentNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", -270.0d));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_tangent")));
    }

    @Test
    void tan450IsInvalid() {
        TangentNode node = new TangentNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", 450.0d));
        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void sinNegative90() {
        SineNode node = new SineNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", -90.0d));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(-1.0d, (Double) outputs.get("output_sine"), 1.0e-12);
    }

    @Test
    void cos360() {
        CosineNode node = new CosineNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", 360.0d));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0d, (Double) outputs.get("output_cosine"), 1.0e-12);
    }

    @Test
    void sinLargeAngleWithNormalization() {
        double angle = 360.0d * 1_000_000.0d + 90.0d;
        SineNode node = new SineNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", angle));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0d, (Double) outputs.get("output_sine"), 1.0e-12);
    }

    @Test
    void arcCosRejectsSlightlyOutOfDomain() {
        ArcCosNode node = new ArcCosNode();
        Map<String, Object> outputs = node.compute(Map.of("input_value", 1.0000000001d));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_angle")));
    }

    @Test
    void atan2PositiveZeroPositiveZero() {
        Atan2Node node = new Atan2Node();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_y", 0.0d,
                "input_x", 0.0d
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(0.0d, (Double) outputs.get("output_angle"), 1.0e-12);
    }

    @Test
    void atan2NegativeZeroNegativeZero() {
        Atan2Node node = new Atan2Node();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_y", -0.0d,
                "input_x", -0.0d
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(Math.toDegrees(Math.atan2(-0.0d, -0.0d)), (Double) outputs.get("output_angle"), 1.0e-12);
    }

    @Test
    void coshOverflowIsInvalid() {
        CoshNode node = new CoshNode();
        Map<String, Object> outputs = node.compute(Map.of("input_value", 1000.0d));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_result")));
    }

    @Test
    void tanhLargeInputsStayValid() {
        TanhNode node = new TanhNode();
        Map<String, Object> pos = node.compute(Map.of("input_value", 1000.0d));
        assertTrue((Boolean) pos.get("output_valid"));
        assertEquals(1.0d, (Double) pos.get("output_result"), 1.0e-6);

        Map<String, Object> neg = node.compute(Map.of("input_value", -1000.0d));
        assertTrue((Boolean) neg.get("output_valid"));
        assertEquals(-1.0d, (Double) neg.get("output_result"), 1.0e-6);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeExpressionParityCases")
    void nodeMatchesExpression(String label, String expression, Map<String, Object> nodeInputs,
                               String nodeOutputKey, double expected, double epsilon) {
        ExpressionNode expr = new ExpressionNode();
        expr.setExpression(expression);
        Map<String, Object> exprOut = expr.compute(Map.of());
        assertTrue((Boolean) exprOut.get("output_valid"), label + " expression valid");
        assertEquals(expected, (Double) exprOut.get("output_result"), epsilon, label + " expression value");

        Map<String, Object> nodeOut = switch (label) {
            case "sin90" -> new SineNode().compute(nodeInputs);
            case "cos0" -> new CosineNode().compute(nodeInputs);
            case "tan45" -> new TangentNode().compute(nodeInputs);
            case "atan2_1_0" -> new Atan2Node().compute(nodeInputs);
            default -> throw new AssertionError("unknown label " + label);
        };
        assertEquals(exprOut.get("output_valid"), nodeOut.get("output_valid"), label + " valid parity");
        assertEquals((Double) exprOut.get("output_result"), (Double) nodeOut.get(nodeOutputKey), epsilon,
                label + " value parity");
    }

    private static Stream<Arguments> nodeExpressionParityCases() {
        return Stream.of(
                Arguments.of("sin90", "sin(90)", Map.of("input_angle", 90.0d), "output_sine", 1.0d, 1.0e-12),
                Arguments.of("cos0", "cos(0)", Map.of("input_angle", 0.0d), "output_cosine", 1.0d, 1.0e-12),
                Arguments.of("tan45", "tan(45)", Map.of("input_angle", 45.0d), "output_tangent", 1.0d, 1.0e-12),
                Arguments.of("atan2_1_0", "atan2(1, 0)", Map.of("input_y", 1.0d, "input_x", 0.0d),
                        "output_angle", 90.0d, 1.0e-12)
        );
    }

    private static final class SineProbe extends SineNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}
