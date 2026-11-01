package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Development aid: materializes and executes every builtin composite preset, reporting
 * per-node validity failures so broken presets can be found without launching the client.
 */
class PresetRuntimeProbeTest {

    private static final Gson GSON = new Gson();

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void probeAllPresets() {
        GraphPresetRules rules = loadRules();
        List<String> report = new ArrayList<>();
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                if (preset == null || !"composite".equalsIgnoreCase(preset.kind)) {
                    continue;
                }
                report.add(probe(preset));
            }
        }
        report.forEach(line -> System.out.println("PROBE|" + line.replace("\n", " | ")));
        System.out.println("PROBE_DONE presets=" + report.size());
    }

    private static String probe(GraphPresetRules.GraphPresetDefinition preset) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(preset.id).append('\n');
        NodeGraph graph = new NodeGraph(preset.id);
        Map<String, INode> byRef = new HashMap<>();
        Map<String, UUID> idByRef = new HashMap<>();
        try {
            for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
                INode node = registry.createNodeInstance(presetNode.typeId);
                if (presetNode.state != null) {
                    node.setNodeState(presetNode.state);
                }
                graph.addNode(node);
                byRef.put(presetNode.ref, node);
                idByRef.put(presetNode.ref, node.getId());
            }
        } catch (Exception e) {
            sb.append("  CREATE_FAILED: ").append(e).append('\n');
            return sb.toString();
        }
        for (INode node : byRef.values()) {
            if ("input.context.player_position".equals(node.getTypeId())) {
                seedPlayer(node);
            }
        }

        List<String> connectErrors = new ArrayList<>();
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            UUID fromId = idByRef.get(connection.fromRef);
            UUID toId = idByRef.get(connection.toRef);
            boolean ok = graph.connect(fromId, connection.fromPort, toId, connection.toPort);
            if (!ok) {
                connectErrors.add(connection.fromRef + "." + connection.fromPort
                        + " -> " + connection.toRef + "." + connection.toPort);
            }
        }
        if (!connectErrors.isEmpty()) {
            sb.append("  CONNECT_FAILED: ").append(connectErrors).append('\n');
            return sb.toString();
        }

        ExecutionContext context = new ExecutionContext(null, null);
        NodeExecutor executor = new NodeExecutor(
                graph,
                context,
                null,
                IncrementalExecutionOptions.defaults(),
                ExecutionRunLimits.defaults(),
                CancellationToken.none(),
                true,
                null,
                true);
        boolean executed;
        try {
            executed = executor.executeSync();
        } catch (Exception e) {
            sb.append("  EXECUTE_THREW: ").append(e).append('\n');
            return sb.toString();
        }
        sb.append("  executed=").append(executed).append('\n');

        for (Map.Entry<String, INode> entry : byRef.entrySet()) {
            INode node = entry.getValue();
            Object valid = node.getOutput("output_valid");
            if (Boolean.FALSE.equals(valid)) {
                Object error = node.getOutput("output_error");
                sb.append("  INVALID node ").append(entry.getKey())
                        .append(" [").append(node.getTypeId()).append("] error=").append(error).append('\n');
            }
        }
        return sb.toString();
    }

    private static void seedPlayer(INode playerNode) {
        if (playerNode == null) {
            return;
        }
        try {
            Method setter = playerNode.getClass().getDeclaredMethod("setCachedPosition", double.class, double.class, double.class);
            setter.setAccessible(true);
            setter.invoke(playerNode, 8.0d, 64.0d, 8.0d);
        } catch (Exception ignored) {
            // player position is optional for the probe
        }
    }

    private static GraphPresetRules loadRules() {
        try (InputStream stream = PresetRuntimeProbeTest.class.getResourceAsStream(
                GraphPresetTestResources.BUILTIN_GRAPH_PRESETS)) {
            if (stream == null) {
                throw new AssertionError("missing preset resource");
            }
            return GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    GraphPresetRules.class);
        } catch (Exception e) {
            throw new AssertionError("failed to load presets", e);
        }
    }
}
