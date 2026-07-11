package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemExpandStringNode;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemTurtle3DNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LSystemStringExpanderTest {

    @Test
    void expandStopsBeforeExponentialGrowthExceedsCap() {
        LSystemRule rule = new LSystemRule("F", "F[+F]F[-F]F");
        LSystemStringExpander.ExpandResult result = LSystemStringExpander.expand(
                "F",
                List.of(rule),
                16,
                0L,
                10_000
        );

        assertTrue(result.hitLimit());
        assertTrue(result.iterationsApplied() < 16);
        assertTrue(result.text().length() <= 10_000);
        assertTrue(result.text().length() > 1);
    }

    @Test
    void expandReportsNoLimitForSmallGrowth() {
        LSystemRule rule = new LSystemRule("A", "AB");
        LSystemStringExpander.ExpandResult result = LSystemStringExpander.expand(
                "A",
                List.of(rule),
                4,
                0L,
                1_000
        );

        assertFalse(result.hitLimit());
        assertEquals(4, result.iterationsApplied());
        assertEquals("ABBBB", result.text());
    }

    @Test
    void expandStringNodeExposesHitLimitOutput() {
        LSystemExpandStringNode node = new LSystemExpandStringNode();
        node.compute(Map.of(
                "input_axiom", "F",
                "input_rules", List.of(new LSystemRule("F", "F[+F]F[-F]F")),
                "input_iterations", 16
        ));

        assertEquals(true, node.getOutput("output_valid"));
        assertEquals(true, node.getOutput("output_hit_limit"));
        assertTrue(((String) node.getOutput("output_string")).length() <= LSystemStringExpander.DEFAULT_MAX_EXPANDED_LENGTH);
    }

    @Test
    void turtleNodeRejectsOversizedCommandString() {
        LSystemTurtle3DNode node = new LSystemTurtle3DNode();
        node.compute(Map.of(
                "input_commands", "F".repeat(LSystemTurtle3DNode.MAX_COMMAND_LENGTH + 1)
        ));

        assertEquals(false, node.getOutput("output_valid"));
        assertEquals(true, node.getOutput("output_hit_limit"));
    }

    @Test
    void expandStopsDuringSingleIterationWhenProductionWouldExceedCap() {
        LSystemRule rule = new LSystemRule("F", "X".repeat(20_000));
        LSystemStringExpander.ExpandResult result = LSystemStringExpander.expand(
                "F",
                List.of(rule),
                1,
                0L,
                10_000
        );

        assertTrue(result.hitLimit());
        assertEquals(0, result.iterationsApplied());
        assertEquals("F", result.text());
    }

    @Test
    void turtleNodeStopsWhenPolylinePointCapReached() {
        LSystemTurtle3DNode node = new LSystemTurtle3DNode();
        int cap = LSystemTurtle3DNode.MAX_POLYLINE_POINTS;
        node.compute(Map.of(
                "input_commands", "F".repeat(cap)
        ));

        assertEquals(true, node.getOutput("output_hit_limit"));
        assertEquals(cap, ((List<?>) node.getOutput("output_points")).size());
    }
}
