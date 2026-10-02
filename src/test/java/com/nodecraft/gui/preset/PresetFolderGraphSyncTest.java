package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When folder-format preset exports exist under {@code presets/}, their node-type topology
 * must match {@code graph_presets.json} for the same preset id.
 */
class PresetFolderGraphSyncTest {

    private static final Gson GSON = new Gson();

    @Test
    void folderExportsMatchGraphPresetNodeTypes() throws Exception {
        Optional<Path> presetsRoot = resolveRepoPresetsRoot();
        if (presetsRoot.isEmpty()) {
            return;
        }

        Map<String, GraphPresetRules.GraphPresetDefinition> graphById = loadGraphPresetsById();
        List<String> errors = new ArrayList<>();

        try (var paths = Files.walk(presetsRoot.get())) {
            List<Path> presetFiles = paths
                    .filter(path -> path.getFileName().toString().equals("preset.json"))
                    .sorted()
                    .toList();

            for (Path presetFile : presetFiles) {
                JsonObject folder = GSON.fromJson(Files.readString(presetFile), JsonObject.class);
                if (folder == null || !folder.has("preset_id")) {
                    continue;
                }
                String presetId = folder.get("preset_id").getAsString();
                GraphPresetRules.GraphPresetDefinition graphPreset = graphById.get(presetId);
                if (graphPreset == null) {
                    continue;
                }

                Set<String> folderTypes = extractFolderNodeTypes(folder);
                Set<String> graphTypes = extractGraphNodeTypes(graphPreset);
                if (!folderTypes.equals(graphTypes)) {
                    errors.add(presetId
                            + " node types differ — folder="
                            + folderTypes
                            + " graph="
                            + graphTypes
                            + " (" + presetsRoot.get().relativize(presetFile) + ")");
                }

                Set<String> folderEdges = extractFolderConnectionSignature(folder);
                Set<String> graphEdges = extractGraphConnectionSignature(graphPreset);
                if (!folderEdges.equals(graphEdges)) {
                    errors.add(presetId
                            + " connections differ — folder="
                            + folderEdges.size()
                            + " graph="
                            + graphEdges.size()
                            + " (" + presetsRoot.get().relativize(presetFile) + ")");
                }
            }
        }

        assertTrue(
                errors.isEmpty(),
                "Folder preset exports drifted from graph_presets.json:"
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), errors));
    }

    private static Set<String> extractFolderNodeTypes(JsonObject folder) {
        Set<String> types = new TreeSet<>();
        if (!folder.has("graph")) {
            return types;
        }
        JsonObject graph = folder.getAsJsonObject("graph");
        if (!graph.has("nodes")) {
            return types;
        }
        JsonArray nodes = graph.getAsJsonArray("nodes");
        nodes.forEach(element -> {
            JsonObject node = element.getAsJsonObject();
            if (node.has("type")) {
                types.add(node.get("type").getAsString());
            }
        });
        return types;
    }

    private static Set<String> extractGraphNodeTypes(GraphPresetRules.GraphPresetDefinition preset) {
        if (preset.nodes == null) {
            return Set.of();
        }
        return preset.nodes.stream()
                .filter(node -> node != null && node.typeId != null)
                .map(node -> node.typeId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> extractFolderConnectionSignature(JsonObject folder) {
        Set<String> edges = new TreeSet<>();
        if (!folder.has("graph")) {
            return edges;
        }
        JsonObject graph = folder.getAsJsonObject("graph");
        Map<String, String> idToType = new HashMap<>();
        if (graph.has("nodes")) {
            graph.getAsJsonArray("nodes").forEach(element -> {
                JsonObject node = element.getAsJsonObject();
                idToType.put(node.get("id").getAsString(), node.get("type").getAsString());
            });
        }
        if (!graph.has("connections")) {
            return edges;
        }
        graph.getAsJsonArray("connections").forEach(element -> {
            JsonObject conn = element.getAsJsonObject();
            JsonObject from = conn.getAsJsonObject("from");
            JsonObject to = conn.getAsJsonObject("to");
            String fromType = idToType.get(from.get("node").getAsString());
            String toType = idToType.get(to.get("node").getAsString());
            edges.add(fromType + "." + from.get("port").getAsString()
                    + "->" + toType + "." + to.get("port").getAsString());
        });
        return edges;
    }

    private static Set<String> extractGraphConnectionSignature(GraphPresetRules.GraphPresetDefinition preset) {
        Set<String> edges = new TreeSet<>();
        if (preset.nodes == null || preset.connections == null) {
            return edges;
        }
        Map<String, String> refToType = new HashMap<>();
        for (GraphPresetRules.PresetNode node : preset.nodes) {
            if (node != null && node.ref != null && node.typeId != null) {
                refToType.put(node.ref, node.typeId);
            }
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null) {
                continue;
            }
            String fromType = refToType.get(connection.fromRef);
            String toType = refToType.get(connection.toRef);
            edges.add(fromType + "." + connection.fromPort + "->" + toType + "." + connection.toPort);
        }
        return edges;
    }

    private static Map<String, GraphPresetRules.GraphPresetDefinition> loadGraphPresetsById() throws Exception {
        Map<String, GraphPresetRules.GraphPresetDefinition> byId = new HashMap<>();
        try (InputStream stream = PresetFolderGraphSyncTest.class.getResourceAsStream(
                GraphPresetTestResources.BUILTIN_GRAPH_PRESETS)) {
            if (stream == null) {
                return byId;
            }
            GraphPresetRules rules = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    GraphPresetRules.class);
            if (rules == null || rules.categories == null) {
                return byId;
            }
            for (GraphPresetRules.PresetCategory category : rules.categories) {
                if (category == null || category.presets == null) {
                    continue;
                }
                for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                    if (preset != null && preset.id != null) {
                        byId.put(preset.id, preset);
                    }
                }
            }
        }
        return byId;
    }

    private static Optional<Path> resolveRepoPresetsRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path presets = dir.resolve("presets");
            if (Files.isDirectory(presets)) {
                return Optional.of(presets);
            }
        }
        return Optional.empty();
    }
}
