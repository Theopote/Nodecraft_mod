package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class GraphPresetLoader {

    private static final String RESOURCE_PATH = "/nodecraft/graph_presets.json";
    private static final Gson GSON = new Gson();

    private GraphPresetLoader() {
    }

    public static GraphPresetRules load() {
        try (InputStream stream = openStream()) {
            if (stream == null) {
                NodeCraft.LOGGER.warn("Graph presets not found at {}", RESOURCE_PATH);
                return new GraphPresetRules();
            }
            GraphPresetRules rules = GSON.fromJson(
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),
                    GraphPresetRules.class);
            if (rules == null) {
                return new GraphPresetRules();
            }
            validateCompositeNodeIds(rules);
            return rules;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to load graph presets: {}", e.getMessage(), e);
            return new GraphPresetRules();
        }
    }

    private static InputStream openStream() throws Exception {
        InputStream stream = GraphPresetLoader.class.getResourceAsStream(RESOURCE_PATH);
        if (stream != null) {
            return stream;
        }
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH.substring(1));
        if (stream != null) {
            return stream;
        }
        Path devPath = Path.of("src/main/resources/nodecraft/graph_presets.json");
        if (Files.isRegularFile(devPath)) {
            return Files.newInputStream(devPath);
        }
        return null;
    }

    private static void validateCompositeNodeIds(GraphPresetRules rules) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry.getNodeCount() == 0 || rules.categories == null) {
            return;
        }
        Set<String> knownIds = Set.copyOf(registry.getAllNodeIds());
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category.presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                if (!"composite".equalsIgnoreCase(preset.kind) || preset.nodes == null) {
                    continue;
                }
                for (GraphPresetRules.PresetNode node : preset.nodes) {
                    if (node == null || node.typeId == null) {
                        continue;
                    }
                    if (!knownIds.contains(node.typeId.toLowerCase())) {
                        NodeCraft.LOGGER.warn(
                                "Graph preset {} references unknown node id: {}",
                                preset.id,
                                node.typeId);
                    }
                }
            }
        }
    }
}
