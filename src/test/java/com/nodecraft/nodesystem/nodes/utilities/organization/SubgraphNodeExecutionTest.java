package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
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
        registry.registerNode(new NodeInfo("test.constant", "Constant", "constant source", "test", 0, ConstantSourceNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.graph_input", "Graph Input", "graph input", "utilities.organization", 0, GraphInputNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.graph_output", "Graph Output", "graph output", "utilities.organization", 0, GraphOutputNode.class));
        registry.registerNode(new NodeInfo("utilities.organization.subgraph", "Subgraph", "subgraph", "utilities.organization", 0, SubgraphNode.class));
    }

    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void definitionBackedSubgraphExecutesThroughGraphInputAndOutput() {
        SavedGraph definition = passthroughSubgraphDefinition();
        SubgraphNode subgraph = configuredSubgraph("embedded-test", definition);

        NodeGraph outer = new NodeGraph("outer");
        ConstantSourceNode source = new ConstantSourceNode(21);
        outer.addNode(source);
        outer.addNode(subgraph);
        outer.connect(
                source.getId(),
                "out",
                subgraph.getId(),
                SubgraphPortIds.dynamicInputPortId("in")
        );

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSubgraphDefinitions(Map.of("embedded-test", definition));

        assertTrue(new NodeExecutor(outer, context).executeSync());
        assertEquals(true, subgraph.getOutput("output_valid"));
        assertEquals(
                21,
                subgraph.getOutput(SubgraphPortIds.dynamicOutputPortId("out"))
        );
    }

    @Test
    void recursiveSubgraphCallIsBlocked() {
        SavedGraph definition = passthroughSubgraphDefinition();
        SubgraphNode nested = configuredSubgraph("loop", definition);

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSubgraphDefinitions(Map.of("loop", definition));
        context.setVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY, new java.util.ArrayList<>(java.util.List.of("loop")));

        Map<String, Object> outputs = nested.compute(Map.of(), context);
        assertEquals(false, outputs.get("output_valid"));
        assertTrue(String.valueOf(outputs.get("output_error")).contains("recursive"));
    }

    private static SavedGraph passthroughSubgraphDefinition() {
        return GraphSerializer.toSavedGraph(passthroughSubgraph());
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

    private static SubgraphNode configuredSubgraph(String ref, SavedGraph definition) {
        SubgraphNode subgraph = new SubgraphNode();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("subgraphRef", ref);
        subgraph.setNodeState(state);
        subgraph.syncPortsFromDefinition(definition);
        return subgraph;
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
