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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        registry.registerNode(new NodeInfo("variable.remove", "Remove Variable", "remove variable", "variable", 0, RemoveVariableNode.class));
        registry.registerNode(new NodeInfo("variable.clear", "Clear Variables", "clear variables", "variable", 0, ClearVariablesNode.class));
        registry.registerNode(new NodeInfo("variable.frame_local", "Frame Local Variable", "frame local", "variable", 0, FrameLocalVariableNode.class));
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
        configureFrameLocal(writer, "session", "counter");
        connectInput(writer, "input_write", NodeDataType.BOOLEAN);

        FrameLocalVariableNode reader = new FrameLocalVariableNode();
        configureFrameLocal(reader, "session", "counter");

        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-a")) {
            writer.compute(Map.of("input_write", true, "input_value", 7));
        }
        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-b")) {
            writer.compute(Map.of("input_write", true, "input_value", 9));
        }

        try (VariableScopeBridge.ScopeBinding ignored = VariableScopeBridge.bindFallbackScope("graph-a")) {
            Map<String, Object> outputs = reader.compute(Map.of("input_default", 0));
            assertEquals(7, outputs.get("output_value"));
        }
    }

    @Test
    void frameLocalWriteAndReadWithinSameFrame() {
        FrameLocalVariableNode writer = new FrameLocalVariableNode();
        configureFrameLocal(writer, "session", "counter");
        connectInput(writer, "input_write", NodeDataType.BOOLEAN);
        ExecutionContext context = ExecutionContext.createEmpty(null);

        writer.compute(Map.of("input_write", true, "input_value", 5), context);

        FrameLocalVariableNode reader = new FrameLocalVariableNode();
        configureFrameLocal(reader, "session", "counter");
        Map<String, Object> outputs = reader.compute(Map.of("input_default", 0), context);

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(true, outputs.get("output_exists"));
        assertEquals(5, outputs.get("output_value"));
        assertEquals(1, outputs.get("output_size"));
    }

    @Test
    void frameLocalClearFrameRemovesExistingEntriesBeforeWrite() {
        FrameLocalVariableNode seed = new FrameLocalVariableNode();
        configureFrameLocal(seed, "batch", "old");
        connectInput(seed, "input_write", NodeDataType.BOOLEAN);
        ExecutionContext context = ExecutionContext.createEmpty(null);

        seed.compute(Map.of("input_write", true, "input_value", "stale"), context);

        FrameLocalVariableNode resetWrite = new FrameLocalVariableNode();
        configureFrameLocal(resetWrite, "batch", "new");
        connectInput(resetWrite, "input_write", NodeDataType.BOOLEAN);
        connectInput(resetWrite, "input_clear_frame", NodeDataType.BOOLEAN);
        resetWrite.compute(Map.of(
            "input_write", true,
            "input_clear_frame", true,
            "input_value", "fresh"
        ), context);

        FrameLocalVariableNode reader = new FrameLocalVariableNode();
        configureFrameLocal(reader, "batch", "old");
        Map<String, Object> oldValue = reader.compute(Map.of("input_default", "missing"), context);
        configureFrameLocal(reader, "batch", "new");
        Map<String, Object> newValue = reader.compute(Map.of(), context);

        assertEquals("missing", oldValue.get("output_value"));
        assertEquals(false, oldValue.get("output_exists"));
        assertEquals("fresh", newValue.get("output_value"));
        assertEquals(1, newValue.get("output_size"));
    }

    @Test
    void setVariableWritesOutputValidOnSuccess() {
        SetVariableNode set = new SetVariableNode();
        set.setNodeState(Map.of("defaultName", "count"));
        ExecutionContext context = ExecutionContext.createEmpty(null);

        set.compute(Map.of("input_value", 3), context);

        assertTrue((Boolean) set.getOutput("output_valid"));
        assertEquals(3, set.getOutput("output_value"));
    }

    @Test
    void removeVariableRemovesExistingKey() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable("temp", "gone");

        RemoveVariableNode remove = new RemoveVariableNode();
        remove.setNodeState(Map.of("defaultName", "temp"));
        remove.compute(Map.of(), context);

        assertTrue((Boolean) remove.getOutput("output_valid"));
        assertTrue((Boolean) remove.getOutput("output_removed"));
        assertEquals("gone", remove.getOutput("output_previous"));
        assertFalse(context.getAllVariables().containsKey("temp"));
    }

    @Test
    void clearVariablesPreservesInternalKeys() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable("user.one", 1);
        context.setVariable("__nodecraft.hidden", "keep");

        ClearVariablesNode clear = new ClearVariablesNode();
        connectInput(clear, "input_clear", NodeDataType.BOOLEAN);
        clear.compute(Map.of("input_clear", true), context);

        assertTrue((Boolean) clear.getOutput("output_valid"));
        assertEquals(1, clear.getOutput("output_cleared_count"));
        assertNull(context.getVariable("user.one"));
        assertEquals("keep", context.getVariable("__nodecraft.hidden"));
    }

    @Test
    void frameLocalConnectedNullWriteFailsClosed() {
        FrameLocalVariableNode frameLocal = new FrameLocalVariableNode();
        configureFrameLocal(frameLocal, "session", "counter");
        connectInput(frameLocal, "input_write", NodeDataType.BOOLEAN);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input_write", null);
        frameLocal.compute(inputs);

        assertFalse((Boolean) frameLocal.getOutput("output_valid"));
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

    private static void configureFrameLocal(FrameLocalVariableNode node, String frame, String name) {
        node.setNodeState(Map.of("defaultFrame", frame, "defaultName", name));
    }

    private static void connectInput(BaseNode target, String portId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> portId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), portId + " connect failed");
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

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }
}
