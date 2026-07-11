package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableScopeNodeTest {

    private final NodeRegistry registry = NodeRegistry.getInstance();

    @BeforeEach
    void registerNodes() {
        registry.clear();
        registry.registerNode(new NodeInfo("test.pass", "Pass", "pass-through test node", "test", 0, PassNode.class));
        registry.registerNode(new NodeInfo("variable.set", "Set Variable", "set variable", "variable", 0, SetVariableNode.class));
        registry.registerNode(new NodeInfo("variable.get", "Get Variable", "get variable", "variable", 0, GetVariableNode.class));
        registry.registerNode(new NodeInfo("variable.list", "Variable List", "variable list", "variable", 0, VariableListNode.class));
    }

    @AfterEach
    void clearRegistry() {
        registry.clear();
        VariableScopeBridge.clearFallbackScope("graph-a");
        VariableScopeBridge.clearFallbackScope("graph-b");
    }

    @Test
    void fallbackScopesAreIsolatedPerGraphId() {
        SetVariableNode set = new SetVariableNode();
        set.setNodeState(Map.of("defaultName", "count"));
        GetVariableNode get = new GetVariableNode();
        get.setNodeState(Map.of("defaultName", "count"));

        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-a")) {
            set.compute(Map.of("input_value", 1));
        }
        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-b")) {
            set.compute(Map.of("input_value", 2));
        }

        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-a")) {
            Map<String, Object> outputs = get.compute(Map.of());
            assertEquals(1, outputs.get("output_value"));
        }
        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-b")) {
            Map<String, Object> outputs = get.compute(Map.of());
            assertEquals(2, outputs.get("output_value"));
        }
    }

    @Test
    void frameLocalFallbackScopesAreIsolatedPerGraphId() {
        FrameLocalVariableNode writer = new FrameLocalVariableNode();
        FrameLocalVariableNode reader = new FrameLocalVariableNode();

        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-a")) {
            writer.compute(Map.of(
                "input_frame", "session",
                "input_name", "counter",
                "input_value", 7,
                "input_write", true
            ));
        }
        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-b")) {
            writer.compute(Map.of(
                "input_frame", "session",
                "input_name", "counter",
                "input_value", 9,
                "input_write", true
            ));
        }

        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-a")) {
            Map<String, Object> outputs = reader.compute(Map.of(
                "input_frame", "session",
                "input_name", "counter",
                "input_write", false,
                "input_default", 0
            ));
            assertEquals(7, outputs.get("output_value"));
        }
    }

    @Test
    void frameLocalWriteAndReadWithinSameFrame() {
        FrameLocalVariableNode writer = new FrameLocalVariableNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);

        writer.compute(Map.of(
            "input_frame", "session",
            "input_name", "counter",
            "input_value", 5,
            "input_write", true
        ), context);

        FrameLocalVariableNode reader = new FrameLocalVariableNode();
        Map<String, Object> outputs = reader.compute(Map.of(
            "input_frame", "session",
            "input_name", "counter",
            "input_write", false,
            "input_default", 0
        ), context);

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(true, outputs.get("output_exists"));
        assertEquals(5, outputs.get("output_value"));
        assertEquals(1, outputs.get("output_size"));
    }

    @Test
    void frameLocalClearFrameRemovesExistingEntriesBeforeWrite() {
        FrameLocalVariableNode seed = new FrameLocalVariableNode();
        ExecutionContext context = ExecutionContext.createEmpty(null);

        seed.compute(Map.of(
            "input_frame", "batch",
            "input_name", "old",
            "input_value", "stale",
            "input_write", true
        ), context);

        FrameLocalVariableNode resetWrite = new FrameLocalVariableNode();
        resetWrite.compute(Map.of(
            "input_frame", "batch",
            "input_name", "new",
            "input_value", "fresh",
            "input_write", true,
            "input_clear_frame", true
        ), context);

        FrameLocalVariableNode reader = new FrameLocalVariableNode();
        Map<String, Object> oldValue = reader.compute(Map.of(
            "input_frame", "batch",
            "input_name", "old",
            "input_write", false,
            "input_default", "missing"
        ), context);
        Map<String, Object> newValue = reader.compute(Map.of(
            "input_frame", "batch",
            "input_name", "new",
            "input_write", false
        ), context);

        assertEquals("missing", oldValue.get("output_value"));
        assertEquals(false, oldValue.get("output_exists"));
        assertEquals("fresh", newValue.get("output_value"));
        assertEquals(1, newValue.get("output_size"));
    }

    @Test
    void variableListFiltersByPrefixAndHidesInternalVariables() {
        SetVariableNode setAlpha = new SetVariableNode();
        setAlpha.setNodeState(Map.of("defaultName", "user.alpha"));
        SetVariableNode setBeta = new SetVariableNode();
        setBeta.setNodeState(Map.of("defaultName", "other.beta"));

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable("__nodecraft.hidden", "internal");

        setAlpha.compute(Map.of("input_value", "A"), context);
        setBeta.compute(Map.of("input_value", "B"), context);

        VariableListNode list = new VariableListNode();
        list.compute(Map.of("input_prefix", "user."), context);

        assertEquals(1, list.getOutput("output_count"));
        assertEquals(List.of("user.alpha"), list.getOutput("output_names"));
        assertEquals(List.of("A"), list.getOutput("output_values"));
    }

    public static final class PassNode extends BaseNode {
        public PassNode() {
            super(UUID.randomUUID(), "test.pass");
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }
}
