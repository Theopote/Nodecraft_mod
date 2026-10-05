package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemExpandNode;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemRuleNode;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemTurtle3DNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.LSystemStringExpander;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Pattern L-System Language v2.
 */
class PatternLSystemLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "pattern.lsystem.expand",
        "pattern.lsystem.rule",
        "pattern.lsystem.turtle_3d"
    );

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.LINE,
        NodeDataType.POLYLINE,
        NodeDataType.CURVE,
        NodeDataType.BLOCK_LIST
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
    void exactlyThreeNodesWithOrdersZeroToTwoPure() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("pattern.lsystem."))
            .sorted()
            .toList();
        assertEquals(3, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("pattern.lsystem", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
            assertTrue(hasPort(node, "output_valid"), id);
            assertTrue(hasPort(node, "output_error"), id);
        }
        for (int i = 0; i < 3; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
        assertEquals(0, registry.getNodeInfo("pattern.lsystem.rule").getOrder());
        assertEquals(1, registry.getNodeInfo("pattern.lsystem.expand").getOrder());
        assertEquals(2, registry.getNodeInfo("pattern.lsystem.turtle_3d").getOrder());
    }

    @Test
    void publicPortsForbidLegacyLooseTypes() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " input " + port.getId() + " has forbidden type " + port.getDataType());
            }
            for (IPort port : node.getOutputPorts()) {
                assertFalse(FORBIDDEN_PORT_TYPES.contains(port.getDataType()),
                    id + " output " + port.getId() + " has forbidden type " + port.getDataType());
            }
        }
    }

    @Test
    void ruleEmptyProductionLegalMissingProductionFails() {
        RuleProbe ok = new RuleProbe();
        ok.setInput("input_symbol", "F");
        ok.setInput("input_production", "");
        ok.processNode(null);
        assertEquals(Boolean.TRUE, ok.getOutput("output_valid"));
        assertEquals("", ok.getOutput("output_error"));

        RuleProbe missing = new RuleProbe();
        missing.setInput("input_symbol", "F");
        missing.processNode(null);
        assertEquals(Boolean.FALSE, missing.getOutput("output_valid"));
        assertTrue(String.valueOf(missing.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("production"));
    }

    @Test
    void ruleWeightConnectedInvalidFails() {
        RuleProbe probe = new RuleProbe();
        probe.setInput("input_symbol", "F");
        probe.setInput("input_production", "FF");
        probe.connectInput("input_weight", NodeDataType.DOUBLE);
        probe.putRawInput("input_weight", "bad");
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("weight"));
    }

    @Test
    void expandIterationsAndSeedConnectedNonExactIntegerFail() {
        ExpandProbe iters = seededExpand();
        iters.connectInput("input_iterations", NodeDataType.INTEGER);
        iters.putRawInput("input_iterations", 3.8d);
        iters.processNode(null);
        assertInvalidExpand(iters);

        ExpandProbe seed = seededExpand();
        seed.connectInput("input_seed", NodeDataType.INTEGER);
        seed.putRawInput("input_seed", 1.5d);
        seed.processNode(null);
        assertInvalidExpand(seed);
    }

    @Test
    void expandDynamicRuleConnectedInvalidFails() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F");
        probe.setNodeState(Map.of("iterations", 1));
        probe.connectInput("input_rule_0", NodeDataType.L_SYSTEM_RULE);
        probe.putRawInput("input_rule_0", "not-a-rule");
        probe.processNode(null);
        assertInvalidExpand(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("rule"));
    }

    @Test
    void expandRulesListConnectedInvalidFails() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F");
        probe.setNodeState(Map.of("iterations", 1));
        probe.connectInput("input_rules", NodeDataType.L_SYSTEM_RULE_LIST);
        probe.putRawInput("input_rules", List.of("bad"));
        probe.processNode(null);
        assertInvalidExpand(probe);
    }

    @Test
    void expandAxiomOverLengthWithZeroIterationsFails() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F".repeat(GenerationLimits.MAX_LSYSTEM_EXPANDED_LENGTH + 1));
        probe.setNodeState(Map.of("iterations", 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, probe.getOutput("output_hit_limit"));
    }

    @Test
    void expandZeroIterationsPassthrough() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "ABC");
        probe.setNodeState(Map.of("iterations", 0));
        probe.processNode(null);
        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals("ABC", probe.getOutput("output_string"));
        assertEquals(0, probe.getOutput("output_iterations_applied"));
        assertEquals(Boolean.FALSE, probe.getOutput("output_hit_limit"));
        assertEquals("", probe.getOutput("output_error"));
    }

    @Test
    void expandRuleCountCapFailsClosed() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F");
        probe.setNodeState(Map.of("iterations", 1));
        List<LSystemRule> rules = new ArrayList<>(GenerationLimits.MAX_LSYSTEM_RULES + 1);
        for (int i = 0; i <= GenerationLimits.MAX_LSYSTEM_RULES; i++) {
            rules.add(new LSystemRule("S" + i, "X"));
        }
        probe.connectInput("input_rules", NodeDataType.L_SYSTEM_RULE_LIST);
        probe.putRawInput("input_rules", rules);
        probe.processNode(null);
        assertInvalidExpand(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("rule"));
    }

    @Test
    void expandRewriteWorkloadFailsClosed() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F".repeat(10_000));
        probe.setNodeState(Map.of("iterations", 16));
        List<LSystemRule> rules = new ArrayList<>();
        rules.add(new LSystemRule("F", "FF"));
        for (int i = 0; i < 200; i++) {
            rules.add(new LSystemRule("Z" + i, "Q"));
        }
        probe.connectInput("input_rules", NodeDataType.L_SYSTEM_RULE_LIST);
        probe.putRawInput("input_rules", rules);
        probe.processNode(null);
        assertInvalidExpand(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("budget")
            || String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("rewrite"));
    }

    @Test
    void expandShrinkingLargeAxiomIsNotRejectedByPreflightEstimate() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "A".repeat(100_000));
        probe.setNodeState(Map.of("iterations", 16));
        List<LSystemRule> rules = new ArrayList<>();
        rules.add(new LSystemRule("A", ""));
        for (int i = 0; i < 99; i++) {
            rules.add(new LSystemRule("Z" + i, "Q"));
        }
        probe.connectInput("input_rules", NodeDataType.L_SYSTEM_RULE_LIST);
        probe.putRawInput("input_rules", rules);
        probe.processNode(null);
        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals("", probe.getOutput("output_string"));
        assertEquals(16, probe.getOutput("output_iterations_applied"));
        assertEquals(Boolean.FALSE, probe.getOutput("output_hit_limit"));
    }

    @Test
    void expandCompetingWeightOverflowFails() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F");
        probe.setNodeState(Map.of("iterations", 1));
        probe.connectRule(0, new LSystemRule("F", "A", Double.MAX_VALUE));
        probe.connectRule(1, new LSystemRule("F", "B", Double.MAX_VALUE));
        probe.processNode(null);
        assertInvalidExpand(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("weight"));
    }

    @Test
    void expandLengthHitKeepsPreviousCompleteRoundValid() {
        LSystemStringExpander.ExpandResult result = LSystemStringExpander.expand(
            "F",
            List.of(new LSystemRule("F", "X".repeat(20_000))),
            1,
            0L,
            10_000
        );
        assertTrue(result.ok());
        assertTrue(result.hitLimit());
        assertEquals("F", result.text());
        assertEquals(0, result.iterationsApplied());
    }

    @Test
    void turtleStepAngleOriginConnectedInvalidFail() {
        TurtleProbe step = new TurtleProbe();
        step.setInput("input_commands", "F");
        step.connectInput("input_step", NodeDataType.DOUBLE);
        step.putRawInput("input_step", "bad");
        step.processNode(null);
        assertInvalidTurtle(step);

        TurtleProbe angle = new TurtleProbe();
        angle.setInput("input_commands", "F");
        angle.connectInput("input_angle", NodeDataType.DOUBLE);
        angle.putRawInput("input_angle", "bad");
        angle.processNode(null);
        assertInvalidTurtle(angle);

        TurtleProbe origin = new TurtleProbe();
        origin.setInput("input_commands", "F");
        origin.connectInput("input_origin", NodeDataType.POINT);
        origin.putRawInput("input_origin", new Vector3d(Double.NaN, 0, 0));
        origin.processNode(null);
        assertInvalidTurtle(origin);
    }

    @Test
    void turtleSegmentLimitFailsClosedNoPartial() {
        TurtleProbe probe = new TurtleProbe();
        int cap = GenerationLimits.maxLSystemTurtleSegments();
        // Use interpreter-level assertion for a small cap; node uses global cap.
        // Node-level: oversized command string.
        probe.setInput("input_commands", "F".repeat(GenerationLimits.MAX_LSYSTEM_COMMAND_LENGTH + 1));
        probe.processNode(null);
        assertInvalidTurtle(probe);
        assertEquals(Boolean.TRUE, probe.getOutput("output_hit_limit"));
        assertEquals(65_536, GenerationLimits.MAX_LSYSTEM_TURTLE_SEGMENTS);
        assertEquals(65_536, cap);
        assertTrue(cap <= GenerationLimits.MAX_LIST_ELEMENTS / 2);
    }

    @Test
    void turtleMissingCommandsFailsClosed() {
        TurtleProbe probe = new TurtleProbe();
        probe.processNode(null);
        assertInvalidTurtle(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("command"));
    }

    @Test
    void turtleWrongTypeCommandsFailsClosed() {
        TurtleProbe probe = new TurtleProbe();
        probe.connectInput("input_commands", NodeDataType.STRING);
        probe.putRawInput("input_commands", 42);
        probe.processNode(null);
        assertInvalidTurtle(probe);
        assertTrue(String.valueOf(probe.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("command"));
    }

    @Test
    void turtleExplicitEmptyCommandsValid() {
        TurtleProbe probe = new TurtleProbe();
        probe.setInput("input_commands", "");
        probe.processNode(null);
        assertEquals(Boolean.TRUE, probe.getOutput("output_valid"));
        assertEquals(0, probe.getOutput("output_segment_count"));
        assertEquals("", probe.getOutput("output_error"));
    }

    @Test
    void turtleBranchingIndependentPathsAndZeroDrawValid() {
        TurtleProbe branch = new TurtleProbe();
        branch.setInput("input_commands", "F[+F]F");
        branch.setNodeState(Map.of("step", 1.0d, "angleDegrees", 90.0d));
        branch.processNode(null);
        assertEquals(Boolean.TRUE, branch.getOutput("output_valid"));
        assertEquals(3, branch.getOutput("output_segment_count"));
        @SuppressWarnings("unchecked")
        List<PathData> paths = assertInstanceOf(List.class, branch.getOutput("output_paths"));
        assertEquals(3, paths.size());

        TurtleProbe zeroDraw = new TurtleProbe();
        zeroDraw.setInput("input_commands", "++++");
        zeroDraw.processNode(null);
        assertEquals(Boolean.TRUE, zeroDraw.getOutput("output_valid"));
        assertEquals(0, zeroDraw.getOutput("output_segment_count"));
        assertEquals("", zeroDraw.getOutput("output_error"));
    }

    @Test
    void turtleFiniteOutputsAndBrackets() {
        TurtleProbe ok = new TurtleProbe();
        ok.setInput("input_commands", "F");
        ok.setNodeState(Map.of("step", 1.0d));
        ok.processNode(null);
        assertEquals(Boolean.TRUE, ok.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PointData> points = assertInstanceOf(List.class, ok.getOutput("output_points"));
        for (PointData point : points) {
            Vector3d p = point.position();
            assertTrue(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z));
        }

        TurtleProbe unmatched = new TurtleProbe();
        unmatched.setInput("input_commands", "F]");
        unmatched.processNode(null);
        assertInvalidTurtle(unmatched);

        TurtleProbe unclosed = new TurtleProbe();
        unclosed.setInput("input_commands", "F[+F");
        unclosed.processNode(null);
        assertInvalidTurtle(unclosed);
    }

    @Test
    void expandDeterministicSameSeed() {
        ExpandProbe first = seededExpand();
        first.connectRule(1, new LSystemRule("F", "B", 3.0d));
        first.setNodeState(Map.of("iterations", 1, "seed", 7));
        first.processNode(null);

        ExpandProbe second = seededExpand();
        second.connectRule(1, new LSystemRule("F", "B", 3.0d));
        second.setNodeState(Map.of("iterations", 1, "seed", 7));
        second.processNode(null);

        assertEquals(Boolean.TRUE, first.getOutput("output_valid"));
        assertEquals(first.getOutput("output_string"), second.getOutput("output_string"));
    }

    private static ExpandProbe seededExpand() {
        ExpandProbe probe = new ExpandProbe();
        probe.setInput("input_axiom", "F");
        probe.connectRule(0, new LSystemRule("F", "A", 1.0d));
        probe.setNodeState(Map.of("iterations", 1));
        return probe;
    }

    private static void assertInvalidExpand(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_iterations_applied"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    private static void assertInvalidTurtle(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_segment_count"));
        @SuppressWarnings("unchecked")
        List<?> paths = assertInstanceOf(List.class, node.getOutput("output_paths"));
        @SuppressWarnings("unchecked")
        List<?> points = assertInstanceOf(List.class, node.getOutput("output_points"));
        assertTrue(paths.isEmpty());
        assertTrue(points.isEmpty());
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getOutputPorts().stream().anyMatch(port -> port.getId().equals(portId))
            || node.getInputPorts().stream().anyMatch(port -> port.getId().equals(portId));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class RuleProbe extends LSystemRuleNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLSystemLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ExpandProbe extends LSystemExpandNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLSystemLanguageV2ContractTest.connectInput(this, portId, outputType);
        }

        void connectRule(int index, LSystemRule rule) {
            String portId = "input_rule_" + index;
            connectInput(portId, NodeDataType.L_SYSTEM_RULE);
            putRawInput(portId, rule);
        }
    }

    private static final class TurtleProbe extends LSystemTurtle3DNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PatternLSystemLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}
