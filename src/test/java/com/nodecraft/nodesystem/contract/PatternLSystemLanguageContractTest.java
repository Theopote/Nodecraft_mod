package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.LSystemStringExpander;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern L-System v1 language fence (Graph V46).
 */
class PatternLSystemLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "pattern.lsystem.rule",
            "pattern.lsystem.expand",
            "pattern.lsystem.turtle_3d"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV46() {
        assertEquals(46, GraphFormatVersion.V46);
        assertEquals(GraphFormatVersion.V46, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyThreeCanonicalLSystemNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("pattern.lsystem."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), "Expected 3 pattern.lsystem nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(ids.contains("pattern.lsystem.expand_string"));
    }

    @Test
    void expandUsesTypedRuleListPort() {
        assertPortType("pattern.lsystem.expand", "input_rules", true, NodeDataType.L_SYSTEM_RULE_LIST);
        assertPortType("pattern.lsystem.rule", "output_rule", false, NodeDataType.L_SYSTEM_RULE);
        assertPortType("pattern.lsystem.expand", "output_iterations_applied", false, NodeDataType.INTEGER);
        assertPortType("pattern.lsystem.turtle_3d", "output_paths", false, NodeDataType.PATH_LIST);
        assertPortType("pattern.lsystem.turtle_3d", "output_segment_count", false, NodeDataType.INTEGER);
    }

    @Test
    void ruleEmptySymbolFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.rule");
        node.setInput("input_symbol", " ");
        node.setInput("input_production", "FF");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void ruleEmptyProductionIsAllowed() {
        BaseNode node = createNode("pattern.lsystem.rule");
        node.setInput("input_symbol", "F");
        node.setInput("input_production", "");
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertInstanceOf(LSystemRule.class, node.getOutput("output_rule"));
    }

    @Test
    void ruleNaNWeightFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.rule");
        node.setInput("input_symbol", "F");
        node.setInput("input_weight", Double.NaN);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void expandZeroIterationsPassthroughWithoutRules() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_iterations", 0);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("F", node.getOutput("output_string"));
        assertEquals(0, node.getOutput("output_iterations_applied"));
    }

    @Test
    void expandStrictIntegerIterationsUsesPropertyFallback() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rule_0", new LSystemRule("F", "FF"));
        node.setInput("input_iterations", 3.8d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("FFFF", node.getOutput("output_string"));
    }

    @Test
    void expandNegativeIterationsFailClosed() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rule_0", new LSystemRule("F", "FF"));
        node.setInput("input_iterations", -1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void expandOverMaxIterationsFailClosed() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rule_0", new LSystemRule("F", "FF"));
        node.setInput("input_iterations", GenerationLimits.MAX_LSYSTEM_ITERATIONS + 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void expandZeroWeightSoloRuleKeepsSymbol() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rule_0", new LSystemRule("F", "FF", 0.0d));
        node.setInput("input_iterations", 1);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("F", node.getOutput("output_string"));
    }

    @Test
    void expandDeterministicWeightedChoice() {
        BaseNode first = createNode("pattern.lsystem.expand");
        first.setInput("input_axiom", "F");
        first.setInput("input_rule_0", new LSystemRule("F", "A", 1.0d));
        first.setInput("input_rule_1", new LSystemRule("F", "B", 3.0d));
        first.setInput("input_iterations", 1);
        first.setInput("input_seed", 42);
        first.processNode(null);

        BaseNode second = createNode("pattern.lsystem.expand");
        second.setInput("input_axiom", "F");
        second.setInput("input_rule_0", new LSystemRule("F", "A", 1.0d));
        second.setInput("input_rule_1", new LSystemRule("F", "B", 3.0d));
        second.setInput("input_iterations", 1);
        second.setInput("input_seed", 42);
        second.processNode(null);

        assertEquals(first.getOutput("output_string"), second.getOutput("output_string"));
    }

    @Test
    void expandLengthCapReturnsPreviousCompleteString() {
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
    void turtleBranchingProducesIndependentSegments() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F[+F]F");
        node.setInput("input_step", 1.0d);
        node.setInput("input_angle", 90.0d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_segment_count"));
        @SuppressWarnings("unchecked")
        List<PathData> paths = assertInstanceOf(List.class, node.getOutput("output_paths"));
        assertEquals(3, paths.size());
    }

    @Test
    void turtlePenUpMoveDoesNotConnectSegments() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F f F");
        node.setInput("input_step", 1.0d);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_segment_count"));
    }

    @Test
    void turtleUnmatchedClosingBracketFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F]");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void turtleBracketErrorClearsAllOutputs() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F]");
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_segment_count"));
        @SuppressWarnings("unchecked")
        List<PathData> paths = assertInstanceOf(List.class, node.getOutput("output_paths"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, node.getOutput("output_points"));
        assertTrue(paths.isEmpty());
        assertTrue(points.isEmpty());
    }

    @Test
    void turtleUnclosedBracketFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F[+F");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void turtleNonPositiveStepFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F");
        node.setInput("input_step", -1.0d);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void turtleNaNAngleFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "F");
        node.setInput("input_angle", Double.NaN);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void turtleStackDepthLimitFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.turtle_3d");
        node.setInput("input_commands", "[".repeat(GenerationLimits.MAX_LSYSTEM_TURTLE_STACK_DEPTH + 1) + "F");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, node.getOutput("output_hit_limit"));
    }

    @Test
    void endToEndRuleExpandTurtleProducesPaths() {
        BaseNode rule = createNode("pattern.lsystem.rule");
        rule.setInput("input_symbol", "F");
        rule.setInput("input_production", "F[+F]F");
        rule.processNode(null);
        assertEquals(Boolean.TRUE, rule.getOutput("output_valid"));

        BaseNode expand = createNode("pattern.lsystem.expand");
        expand.setInput("input_axiom", "F");
        expand.setInput("input_rule_0", rule.getOutput("output_rule"));
        expand.setInput("input_iterations", 1);
        expand.processNode(null);
        assertEquals(Boolean.TRUE, expand.getOutput("output_valid"));

        BaseNode turtle = createNode("pattern.lsystem.turtle_3d");
        turtle.setInput("input_commands", expand.getOutput("output_string"));
        turtle.setInput("input_angle", 90.0d);
        turtle.processNode(null);

        assertEquals(Boolean.TRUE, turtle.getOutput("output_valid"));
        assertTrue((Integer) turtle.getOutput("output_segment_count") >= 3);
    }

    @Test
    void expandInvalidRuleListEntryFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rules", List.of("not-a-rule"));
        node.setInput("input_iterations", 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void expandMalformedRuleObjectFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rule_0", new LSystemRule("F", "FF", Double.NaN));
        node.setInput("input_iterations", 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void expandMalformedRuleListEntryFailsClosed() {
        BaseNode node = createNode("pattern.lsystem.expand");
        node.setInput("input_axiom", "F");
        node.setInput("input_rules", List.of(new LSystemRule("F", "FF", Double.NaN)));
        node.setInput("input_iterations", 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void expandWeightedChoiceCanVaryWithSeed() {
        String firstPick = null;
        String secondPick = null;
        for (int seed = 0; seed < 32 && (firstPick == null || firstPick.equals(secondPick)); seed++) {
            BaseNode node = createNode("pattern.lsystem.expand");
            node.setInput("input_axiom", "F");
            node.setInput("input_rule_0", new LSystemRule("F", "A", 1.0d));
            node.setInput("input_rule_1", new LSystemRule("F", "B", 1.0d));
            node.setInput("input_iterations", 1);
            node.setInput("input_seed", seed);
            node.processNode(null);
            String out = (String) node.getOutput("output_string");
            if (firstPick == null) {
                firstPick = out;
            } else if (!firstPick.equals(out)) {
                secondPick = out;
            }
        }
        assertNotEquals(firstPick, secondPick);
    }

    private static BaseNode createNode(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }
}
