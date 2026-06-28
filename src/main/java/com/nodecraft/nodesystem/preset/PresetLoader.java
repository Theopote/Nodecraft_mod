package com.nodecraft.nodesystem.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads preset definitions from JSON files.
 *
 * <p>Handles parsing of preset.json files and converts them into {@link PresetDefinition} objects.</p>
 */
public class PresetLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads a preset definition from a JSON file.
     *
     * @param presetJsonPath path to the preset.json file
     * @return the loaded preset definition
     * @throws IOException if the file cannot be read or parsed
     */
    public static PresetDefinition load(Path presetJsonPath) throws IOException {
        LOGGER.debug("Loading preset from: {}", presetJsonPath);

        try (Reader reader = Files.newBufferedReader(presetJsonPath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            String presetId = root.get("preset_id").getAsString();
            String version = root.get("version").getAsString();
            String schemaVersion = root.has("schema_version")
                ? root.get("schema_version").getAsString()
                : "1.0";

            PresetMetadata metadata = parseMetadata(root.getAsJsonObject("metadata"));
            PresetThumbnails thumbnails = parseThumbnails(root.getAsJsonObject("thumbnails"));
            List<PresetParameter> parameters = parseParameters(root.getAsJsonArray("parameters"));
            PresetGraph graph = parseGraph(root.getAsJsonObject("graph"));
            PresetDocumentation documentation = parseDocumentation(
                root.has("documentation") ? root.getAsJsonObject("documentation") : null
            );

            PresetDefinition preset = new PresetDefinition(
                presetId,
                version,
                schemaVersion,
                metadata,
                parameters,
                graph,
                documentation,
                thumbnails
            );

            LOGGER.info("Loaded preset: {} v{}", presetId, version);
            return preset;

        } catch (Exception e) {
            LOGGER.error("Failed to load preset from: {}", presetJsonPath, e);
            throw new IOException("Failed to load preset: " + e.getMessage(), e);
        }
    }

    private static PresetMetadata parseMetadata(JsonObject metadataObj) {
        String name = metadataObj.get("name").getAsString();
        Map<String, String> nameI18n = parseI18nMap(metadataObj, "name_i18n");

        String description = metadataObj.get("description").getAsString();
        Map<String, String> descriptionI18n = parseI18nMap(metadataObj, "description_i18n");

        String author = metadataObj.get("author").getAsString();

        List<String> tags = new ArrayList<>();
        if (metadataObj.has("tags")) {
            metadataObj.getAsJsonArray("tags").forEach(e -> tags.add(e.getAsString()));
        }

        String category = metadataObj.get("category").getAsString();
        PresetDifficulty difficulty = PresetDifficulty.fromString(
            metadataObj.get("difficulty").getAsString()
        );

        String estimatedBuildTime = metadataObj.has("estimated_build_time")
            ? metadataObj.get("estimated_build_time").getAsString()
            : "Unknown";

        int estimatedNodeCount = metadataObj.has("estimated_node_count")
            ? metadataObj.get("estimated_node_count").getAsInt()
            : 0;

        return new PresetMetadata(
            name,
            nameI18n,
            description,
            descriptionI18n,
            author,
            tags,
            category,
            difficulty,
            estimatedBuildTime,
            estimatedNodeCount
        );
    }

    private static Map<String, String> parseI18nMap(JsonObject parent, String key) {
        Map<String, String> map = new HashMap<>();
        if (parent.has(key)) {
            JsonObject i18nObj = parent.getAsJsonObject(key);
            i18nObj.entrySet().forEach(entry -> {
                map.put(entry.getKey(), entry.getValue().getAsString());
            });
        }
        return map;
    }

    private static PresetThumbnails parseThumbnails(JsonObject thumbnailsObj) {
        String main = thumbnailsObj.get("main").getAsString();
        List<String> previews = new ArrayList<>();
        if (thumbnailsObj.has("previews")) {
            thumbnailsObj.getAsJsonArray("previews").forEach(e -> previews.add(e.getAsString()));
        }
        return new PresetThumbnails(main, previews);
    }

    private static List<PresetParameter> parseParameters(com.google.gson.JsonArray parametersArray) {
        List<PresetParameter> parameters = new ArrayList<>();

        if (parametersArray != null) {
            parametersArray.forEach(element -> {
                JsonObject paramObj = element.getAsJsonObject();

                String id = paramObj.get("id").getAsString();
                String name = paramObj.get("name").getAsString();
                ParameterType type = ParameterType.fromString(paramObj.get("type").getAsString());
                Object defaultValue = parseParameterValue(paramObj.get("default"), type);

                Object minValue = paramObj.has("min") ? parseParameterValue(paramObj.get("min"), type) : null;
                Object maxValue = paramObj.has("max") ? parseParameterValue(paramObj.get("max"), type) : null;
                Object step = paramObj.has("step") ? parseParameterValue(paramObj.get("step"), type) : null;

                String description = paramObj.has("description") ? paramObj.get("description").getAsString() : "";
                String group = paramObj.has("group") ? paramObj.get("group").getAsString() : "General";

                List<PresetParameter.ParameterOption> options = null;
                if (paramObj.has("options")) {
                    options = new ArrayList<>();
                    for (var optionElement : paramObj.getAsJsonArray("options")) {
                        JsonObject optionObj = optionElement.getAsJsonObject();
                        String value = optionObj.get("value").getAsString();
                        String label = optionObj.get("label").getAsString();
                        options.add(new PresetParameter.ParameterOption(value, label));
                    }
                }

                parameters.add(new PresetParameter(
                    id, name, type, defaultValue, minValue, maxValue, step, description, group, options
                ));
            });
        }

        return parameters;
    }

    private static Object parseParameterValue(com.google.gson.JsonElement element, ParameterType type) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        switch (type) {
            case INTEGER:
                return element.getAsInt();
            case FLOAT:
            case ANGLE:
                return element.getAsDouble();
            case BOOLEAN:
                return element.getAsBoolean();
            case STRING:
            case DROPDOWN:
            case BLOCK_SELECTOR:
            case COLOR:
                return element.getAsString();
            case VECTOR3:
                if (element.isJsonObject()) {
                    JsonObject vec = element.getAsJsonObject();
                    Map<String, Object> map = new HashMap<>();
                    map.put("x", vec.get("x").getAsDouble());
                    map.put("y", vec.get("y").getAsDouble());
                    map.put("z", vec.get("z").getAsDouble());
                    return map;
                }
                return null;
            default:
                return element.getAsString();
        }
    }

    private static PresetGraph parseGraph(JsonObject graphObj) {
        List<PresetGraph.PresetNodeDefinition> nodes = new ArrayList<>();
        List<PresetGraph.PresetConnectionDefinition> connections = new ArrayList<>();

        // Parse nodes
        if (graphObj.has("nodes")) {
            graphObj.getAsJsonArray("nodes").forEach(element -> {
                JsonObject nodeObj = element.getAsJsonObject();

                String id = nodeObj.get("id").getAsString();
                String type = nodeObj.get("type").getAsString();

                Map<String, Double> position = new HashMap<>();
                if (nodeObj.has("position")) {
                    JsonObject posObj = nodeObj.getAsJsonObject("position");
                    position.put("x", posObj.get("x").getAsDouble());
                    position.put("y", posObj.get("y").getAsDouble());
                }

                Map<String, Object> parameters = new HashMap<>();
                if (nodeObj.has("parameters")) {
                    JsonObject paramsObj = nodeObj.getAsJsonObject("parameters");
                    paramsObj.entrySet().forEach(entry -> {
                        parameters.put(entry.getKey(), parseNodeParameterValue(entry.getValue()));
                    });
                }

                nodes.add(new PresetGraph.PresetNodeDefinition(id, type, position, parameters));
            });
        }

        // Parse connections
        if (graphObj.has("connections")) {
            graphObj.getAsJsonArray("connections").forEach(element -> {
                JsonObject connObj = element.getAsJsonObject();

                JsonObject fromObj = connObj.getAsJsonObject("from");
                PresetGraph.PresetConnectionDefinition.ConnectionEndpoint from =
                    new PresetGraph.PresetConnectionDefinition.ConnectionEndpoint(
                        fromObj.get("node").getAsString(),
                        fromObj.get("port").getAsString()
                    );

                JsonObject toObj = connObj.getAsJsonObject("to");
                PresetGraph.PresetConnectionDefinition.ConnectionEndpoint to =
                    new PresetGraph.PresetConnectionDefinition.ConnectionEndpoint(
                        toObj.get("node").getAsString(),
                        toObj.get("port").getAsString()
                    );

                connections.add(new PresetGraph.PresetConnectionDefinition(from, to));
            });
        }

        return new PresetGraph(nodes, connections);
    }

    @SuppressWarnings("unchecked")
    private static Object parseNodeParameterValue(com.google.gson.JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                // Try to preserve integer vs double
                double val = primitive.getAsDouble();
                if (val == Math.floor(val)) {
                    return primitive.getAsInt();
                }
                return val;
            } else if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else {
                return primitive.getAsString();
            }
        } else if (element.isJsonObject()) {
            // Could be a parameter reference like {"param": "width"} or a nested object
            JsonObject obj = element.getAsJsonObject();
            Map<String, Object> map = new HashMap<>();
            obj.entrySet().forEach(entry -> {
                map.put(entry.getKey(), parseNodeParameterValue(entry.getValue()));
            });
            return map;
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            element.getAsJsonArray().forEach(e -> list.add(parseNodeParameterValue(e)));
            return list;
        }
        return null;
    }

    private static PresetDocumentation parseDocumentation(JsonObject docObj) {
        if (docObj == null) {
            return new PresetDocumentation("", List.of(), List.of());
        }

        String learningNotes = docObj.has("learning_notes")
            ? docObj.get("learning_notes").getAsString()
            : "";

        List<String> tips = new ArrayList<>();
        if (docObj.has("tips")) {
            docObj.getAsJsonArray("tips").forEach(e -> tips.add(e.getAsString()));
        }

        List<String> relatedPresets = new ArrayList<>();
        if (docObj.has("related_presets")) {
            docObj.getAsJsonArray("related_presets").forEach(e -> relatedPresets.add(e.getAsString()));
        }

        return new PresetDocumentation(learningNotes, tips, relatedPresets);
    }
}
