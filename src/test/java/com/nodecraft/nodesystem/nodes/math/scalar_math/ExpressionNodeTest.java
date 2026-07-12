package com.nodecraft.nodesystem.nodes.math.scalar_math;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionNodeTest {

    @Test
    void deepParenthesesReturnValidationErrorInsteadOfStackOverflow() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("(".repeat(150) + "1" + ")".repeat(150));

        Map<String, Object> outputs = node.compute(Map.of());

        assertEquals(false, outputs.get("output_valid"));
        assertEquals(
            "Expression nesting is too deep (max 100)",
            outputs.get("output_error")
        );
    }

    @Test
    void deepUnaryOperatorsReturnValidationError() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("-".repeat(150) + "1");

        Map<String, Object> outputs = node.compute(Map.of());

        assertEquals(false, outputs.get("output_valid"));
        assertEquals(
            "Expression nesting is too deep (max 100)",
            outputs.get("output_error")
        );
    }

    @Test
    void setExpressionTruncatesToEditorLimit() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("A".repeat(3000));

        assertEquals(2048, node.getExpression().length());
    }

    @Test
    void normalExpressionStillEvaluates() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("sin(A) + B");

        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", Math.PI / 2.0d,
            "input_b", 2.0d
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(3.0d, (Double) outputs.get("output_result"), 1.0e-9);
        assertEquals("", outputs.get("output_error"));
    }
}
