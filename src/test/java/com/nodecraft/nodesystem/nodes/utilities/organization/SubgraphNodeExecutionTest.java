package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubgraphNodeExecutionTest {

    private final NodeRegistry registry = NodeRegistry.getInstance();

    @BeforeEach
    void registerNodes() {
        registry.clear();
        registry.registerNode(new NodeInfo("test.pass", "Pass", "pass-through test node", "test", 0, PassNode.class));
        registry.registerNode(new NodeInfo("test.constant", "Constant", "constant source", "test", 0, ConstantSourceNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.graph_input", "Graph Input", "graph input", "utilities.organization", 0, GraphInputNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.graph_output", "Graph Output", "graph output", "utilities.organization", 0, GraphOutputNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.subgraph", "Subgraph", "subgraph", "utilities.organization", 0, SubgraphNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.subgraph_register", "Subgraph Register", "subgraph register", "utilities.organization", 0, SubgraphRegisterNode.class));
    }

    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void embeddedSubgraphExecutesThroughGraphInputAndOutput() {
        NodeGraph inner = passthroughSubgraph();
        SubgraphNode subgraph = configuredSubgraph("embedded-test", GraphSerializer.toJson(inner));

        NodeGraph outer = new NodeGraph("outer");
        ConstantSourceNode source = new ConstantSourceNode(21);
        outer.addNode(source);
        outer.addNode(subgraph);
        outer.connect(source.getId(), "out", subgraph.getId(), "input_value");

        ExecutionContext context = ExecutionContext.createEmpty(null);

        assertTrue(new NodeExecutor(outer, context).executeSync());
        assertEquals(true, subgraph.getOutput("output_valid"));
        assertEquals(21, subgraph.getOutput("output_value"));
    }

    @Test
    void registryBackedSubgraphExecutesWithMappedOutputs() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        NodeGraph inner = passthroughSubgraph();

        SubgraphRegisterNode register = new SubgraphRegisterNode();
        Map<String, Object> registered = register.compute(Map.of(
            "input_subgraph_ref", "helper",
            "input_subgraph_graph", inner,
            "input_register", true
        ), context);
        assertEquals(true, registered.get("output_success"));

        SubgraphNode subgraph = configuredSubgraph("helper", "");
        Map<String, Object> outputs = subgraph.compute(Map.of("input_value", 42), context);

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(42, outputs.get("output_value"));
    }

    @Test
    void recursiveSubgraphCallIsBlocked() {
        NodeGraph inner = passthroughSubgraph();
        SubgraphNode nested = configuredSubgraph("loop", GraphSerializer.toJson(inner));

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY, new java.util.ArrayList<>(java.util.List.of("loop")));

        Map<String, Object> outputs = nested.compute(Map.of("input_value", 1), context);
        assertEquals(false, outputs.get("output_valid"));
        assertEquals("recursive_call_blocked", ((Map<?, ?>) outputs.get("output_metadata")).get("mode"));
    }

    @Test
    void executorRunsRegisterBeforeSubgraphCall() {
        NodeGraph graph = new NodeGraph("register-then-call");
        ConstantSourceNode source = new ConstantSourceNode(99);
        SubgraphRegisterNode register = new SubgraphRegisterNode();
        register.setNodeState(Map.of("defaultRef", "helper"));
        ConstantSourceNode graphPayload = new ConstantSourceNode(passthroughSubgraph());
        SubgraphNode subgraph = configuredSubgraph("helper", "");

        graph.addNode(source);
        graph.addNode(graphPayload);
        graph.addNode(register);
        graph.addNode(subgraph);
        graph.connect(source.getId(), "out", subgraph.getId(), "input_value");
        graph.connect(graphPayload.getId(), "out", register.getId(), "input_subgraph_graph");
        graph.connect(register.getId(), "output_ref", subgraph.getId(), "input_subgraph_ref");

        ExecutionContext context = ExecutionContext.createEmpty(null);
        assertTrue(new NodeExecutor(graph, context).executeSync());

        assertEquals(true, register.getOutput("output_success"));
        assertEquals(true, subgraph.getOutput("output_valid"));
        assertEquals(99, subgraph.getOutput("output_value"));
    }

    private static NodeGraph passthroughSubgraph() {
        GraphInputNode input = new GraphInputNode();
        input.setNodeState(Map.of("inputName", "in"));
        GraphOutputNode output = new GraphOutputNode();
        output.setNodeState(Map.of("outputName", "out"));

        NodeGraph inner = new NodeGraph("inner");
        inner.addNode(input);
        inner.addNode(output);
        inner.connect(input.getId(), "output_value", output.getId(), "input_value");
        return inner;
    }

    private static SubgraphNode configuredSubgraph(String ref, String embeddedGraphJson) {
        SubgraphNode subgraph = new SubgraphNode();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("subgraphRef", ref);
        state.put("inputKey", "in");
        state.put("outputKey", "out");
        state.put("embeddedGraphJson", embeddedGraphJson);
        subgraph.setNodeState(state);
        return subgraph;
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

    public static final class ConstantSourceNode extends BaseNode {
        private final Object value;

        public ConstantSourceNode(Object value) {
            super(UUID.randomUUID(), "test.constant");
            this.value = value;
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            outputValues.put("out", value);
        }
    }
}
