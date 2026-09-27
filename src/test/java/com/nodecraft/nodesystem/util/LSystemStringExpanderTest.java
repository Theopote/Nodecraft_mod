package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemExpandNode;
import com.nodecraft.nodesystem.nodes.pattern.lsystem.LSystemTurtle3DNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        assertTrue(result.ok());
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

        assertTrue(result.ok());
        assertFalse(result.hitLimit());
        assertEquals(4, result.iterationsApplied());
        assertEquals("ABBBB", result.text());
    }

    @Test
    void expandNodeExposesHitLimitOutput() {
        ExpandProbe node = new ExpandProbe();
        node.setNodeState(Map.of("iterations", 16));
        node.setInput("input_axiom", "F");
        node.connectInput("input_rule_0", NodeDataType.L_SYSTEM_RULE);
        node.putRawInput("input_rule_0", new LSystemRule("F", "F[+F]F[-F]F"));
        node.processNode(null);

        assertEquals(true, node.getOutput("output_valid"));
        assertEquals(true, node.getOutput("output_hit_limit"));
        assertTrue(((String) node.getOutput("output_string")).length() <= GenerationLimits.MAX_LSYSTEM_EXPANDED_LENGTH);
    }

    @Test
    void turtleNodeRejectsOversizedCommandString() {
        LSystemTurtle3DNode node = new LSystemTurtle3DNode();
        node.compute(Map.of(
                "input_commands", "F".repeat(GenerationLimits.MAX_LSYSTEM_COMMAND_LENGTH + 1)
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

        assertTrue(result.ok());
        assertTrue(result.hitLimit());
        assertEquals(0, result.iterationsApplied());
        assertEquals("F", result.text());
    }

    @Test
    void turtleInterpreterFailsClosedWhenSegmentCapReached() {
        int cap = 100;
        LSystemTurtle3DInterpreter.TurtleResult result = LSystemTurtle3DInterpreter.interpret(
                "F".repeat(cap + 1),
                new org.joml.Vector3d(),
                1.0d,
                25.0d,
                cap + 10,
                cap,
                GenerationLimits.MAX_LSYSTEM_TURTLE_STACK_DEPTH
        );

        assertFalse(result.valid());
        assertTrue(result.hitLimit());
        assertEquals(0, result.segmentCount());
        assertTrue(result.paths().isEmpty());
    }

    @Test
    void zeroWeightSingleRuleKeepsSymbol() {
        LSystemStringExpander.ExpandResult result = LSystemStringExpander.expand(
                "F",
                List.of(new LSystemRule("F", "FF", 0.0d)),
                1,
                0L
        );
        assertTrue(result.ok());
        assertFalse(result.hitLimit());
        assertEquals("F", result.text());
    }

    @Test
    void zeroIterationsPassthroughWithoutRules() {
        LSystemStringExpander.ExpandResult result = LSystemStringExpander.expand(
                "ABC",
                List.of(),
                0,
                0L
        );
        assertTrue(result.ok());
        assertEquals("ABC", result.text());
        assertEquals(0, result.iterationsApplied());
    }

    private static final class ExpandProbe extends LSystemExpandNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            PortStubNode stub = new PortStubNode(outputType);
            BasePort output = (BasePort) stub.getOutputPorts().getFirst();
            BasePort input = (BasePort) getInputPorts().stream()
                .filter(port -> portId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
            assertTrue(output.connectTo(input));
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
