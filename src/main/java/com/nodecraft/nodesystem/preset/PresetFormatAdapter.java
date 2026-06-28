package com.nodecraft.nodesystem.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.nodecraft.gui.preset.GraphPresetRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Converts new format presets (preset.json) to old format (GraphPresetRules).
 *
 * <p>This adapter allows the new parametric preset system to work with the existing UI.</p>
 */
public class PresetFormatAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetFormatAdapter.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> NODE_ID_ALIASES = Map.ofEntries(
            Map.entry("geometry.primitives.box_by_center_and_size", "geometry.primitives.box"),
            Map.entry("geometry.primitives.box_by_corner_and_size", "geometry.primitives.box_from_corner_size"),
            Map.entry("geometry.primitives.cylinder_by_axis_and_radius", "geometry.primitives.cylinder"),
            Map.entry("geometry.primitives.sphere_by_center_and_radius", "geometry.primitives.sphere"),
            Map.entry("geometry.profiles.triangle", "geometry.profiles.polygon_profile"),
            Map.entry("geometry.profiles.rectangle", "geometry.profiles.rectangle_profile"),
            Map.entry("geometry.profiles.circle", "geometry.profiles.circle_profile"),
            Map.entry("geometry.profiles.arc", "geometry.profiles.sector_profile"),
            Map.entry("geometry.profiles.union_profiles", "geometry.profiles.polygon_profile"),
            Map.entry("geometry.solids.extrude", "geometry.solids.extrude_profile"),
            Map.entry("geometry.boolean.union_multiple", "geometry.boolean.union"),
            Map.entry("geometry.curves.line", "geometry.curves.curve_from_points"),
            Map.entry("geometry.curves.circle", "geometry.profiles.circle_profile"),
            Map.entry("geometry.curves.divide_curve", "geometry.curves.divide_curve_to_points"),
            Map.entry("transform.basic.move", "transform.basic_transforms.move_geometry"),
            Map.entry("transform.basic.rotate", "transform.basic_transforms.rotate_geometry_axis"),
            Map.entry("transform.basic.scale", "transform.basic_transforms.scale_geometry_point"),
            Map.entry("transform.basic_transforms.translate", "transform.basic_transforms.move_geometry"),
            Map.entry("transform.basic_transforms.rotate", "transform.basic_transforms.rotate_geometry_axis"),
            Map.entry("material.gradient_mapping.height_gradient", "material.gradient_mapping.height_gradient_map"),
            Map.entry("output.bake.geometry_to_blocks", "output.execute.bake_geometry_to_blocks"),
            Map.entry("output.preview.preview_blocks", "output.preview.preview_blocks"),
            Map.entry("output.preview.block_preview", "output.preview.preview_blocks"),
            Map.entry("output.preview.geometry_viewer", "output.preview.geometry_viewer"),
            Map.entry("pattern.instances.place_instances_at_points", "pattern.linear.instance_on_points"),
            Map.entry("pattern.instances.orient_instances_to_frames", "pattern.linear.instance_on_points"),
            Map.entry("patterns.instances.instance_on_points", "pattern.linear.instance_on_points"),
            Map.entry("patterns.array.linear", "pattern.linear.linear_array"),
            Map.entry("patterns.instances.instance_geometry_to_points", "pattern.linear.instance_on_points")
    );

    /**
     * Converts a new format preset to old format GraphPresetDefinition.
     *
     * @param newPreset the new preset definition
     * @return the old format preset definition
     */
    public static GraphPresetRules.GraphPresetDefinition convertToOldFormat(PresetDefinition newPreset) {
        GraphPresetRules.GraphPresetDefinition oldPreset = new GraphPresetRules.GraphPresetDefinition();

        // Basic metadata
        oldPreset.id = newPreset.getPresetId();
        oldPreset.displayName = newPreset.getMetadata().getName();
        oldPreset.description = newPreset.getMetadata().getDescription();
        oldPreset.kind = "composite"; // Mark as composite so it's usable

        // Convert nodes
        List<GraphPresetRules.PresetNode> oldNodes = new ArrayList<>();
        for (PresetGraph.PresetNodeDefinition nodeDef : newPreset.getGraph().getNodes()) {
            GraphPresetRules.PresetNode oldNode = new GraphPresetRules.PresetNode();
            oldNode.ref = nodeDef.getId();
            oldNode.typeId = mapNodeTypeId(nodeDef.getType());

            // Get position
            Map<String, Double> pos = nodeDef.getPosition();
            if (pos != null) {
                oldNode.x = pos.getOrDefault("x", 0.0).floatValue();
                oldNode.y = pos.getOrDefault("y", 0.0).floatValue();
            }

            oldNodes.add(oldNode);
        }
        oldPreset.nodes = oldNodes;

        // Convert connections
        List<GraphPresetRules.PresetConnection> oldConnections = new ArrayList<>();
        for (PresetGraph.PresetConnectionDefinition connDef : newPreset.getGraph().getConnections()) {
            GraphPresetRules.PresetConnection oldConn = new GraphPresetRules.PresetConnection();
            oldConn.fromRef = connDef.getFrom().getNode();
            oldConn.fromPort = mapPortName(connDef.getFrom().getPort(), true);  // output
            oldConn.toRef = connDef.getTo().getNode();
            oldConn.toPort = mapPortName(connDef.getTo().getPort(), false);     // input
            oldConnections.add(oldConn);
        }
        oldPreset.connections = oldConnections;

        return oldPreset;
    }

    /**
     * Scans a preset directory and converts all presets to old format.
     *
     * @param presetDirectory the directory containing preset.json files
     * @return GraphPresetRules with all converted presets
     */
    public static GraphPresetRules convertDirectory(Path presetDirectory) {
        GraphPresetRules rules = new GraphPresetRules();
        rules.version = 1;

        Map<String, List<GraphPresetRules.GraphPresetDefinition>> categoryMap = new HashMap<>();

        try (Stream<Path> paths = Files.walk(presetDirectory)) {
            List<Path> presetFiles = paths
                .filter(p -> p.getFileName().toString().equals("preset.json"))
                .toList();

            for (Path presetFile : presetFiles) {
                try {
                    PresetDefinition newPreset = PresetLoader.load(presetFile);
                    GraphPresetRules.GraphPresetDefinition oldPreset = convertToOldFormat(newPreset);

                    String category = newPreset.getMetadata().getCategory();
                    categoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(oldPreset);

                    LOGGER.debug("Converted preset: {}", newPreset.getPresetId());
                } catch (IOException e) {
                    LOGGER.error("Failed to convert preset: {}", presetFile, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan preset directory", e);
        }

        // Create categories
        List<GraphPresetRules.PresetCategory> categories = new ArrayList<>();
        for (Map.Entry<String, List<GraphPresetRules.GraphPresetDefinition>> entry : categoryMap.entrySet()) {
            GraphPresetRules.PresetCategory category = new GraphPresetRules.PresetCategory();
            category.id = entry.getKey();
            category.displayName = getCategoryDisplayName(entry.getKey());
            category.presets = entry.getValue();
            categories.add(category);
        }

        rules.categories = categories;
        return rules;
    }

    /**
     * Maps port names from new format to old format.
     *
     * <p>Old format expects ports prefixed with "output_" or "input_".</p>
     *
     * @param portName the port name from new format
     * @param isOutput true if this is an output port, false for input
     * @return mapped port name
     */
    private static String mapPortName(String portName, boolean isOutput) {
        if (portName == null || portName.isEmpty()) {
            return portName;
        }

        // If already prefixed, return as-is
        if (portName.startsWith("output_") || portName.startsWith("input_")) {
            return portName;
        }

        // Add appropriate prefix
        String prefix = isOutput ? "output_" : "input_";
        return prefix + portName;
    }

    private static String mapNodeTypeId(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return typeId;
        }
        return NODE_ID_ALIASES.getOrDefault(typeId, typeId);
    }

    /**
     * Gets a human-readable display name for a category ID.
     */
    private static String getCategoryDisplayName(String categoryId) {
        return switch (categoryId) {
            case "quickstart" -> "Quickstart";
            case "building_elements" -> "Building Elements";
            case "architectural" -> "Architecture";
            case "decorative" -> "Decorative";
            case "styles" -> "Styles";
            default -> categoryId;
        };
    }

    /**
     * Merges new presets with existing graph_presets.json.
     *
     * @param existingRules existing rules from graph_presets.json
     * @param newPresetDirectory directory with new presets
     * @return merged rules
     */
    public static GraphPresetRules mergePresets(GraphPresetRules existingRules, Path newPresetDirectory) {
        GraphPresetRules newRules = convertDirectory(newPresetDirectory);

        // Create a map of existing categories
        Map<String, GraphPresetRules.PresetCategory> existingCategories = new HashMap<>();
        for (GraphPresetRules.PresetCategory cat : existingRules.categories) {
            existingCategories.put(cat.id, cat);
        }

        // Merge or add new categories
        for (GraphPresetRules.PresetCategory newCat : newRules.categories) {
            GraphPresetRules.PresetCategory existingCat = existingCategories.get(newCat.id);
            if (existingCat != null) {
                List<GraphPresetRules.GraphPresetDefinition> merged = new ArrayList<>(existingCat.presets);
                for (GraphPresetRules.GraphPresetDefinition newPreset : newCat.presets) {
                    merged.removeIf(existingPreset ->
                            existingPreset != null
                                    && newPreset != null
                                    && newPreset.id != null
                                    && newPreset.id.equals(existingPreset.id));
                }
                merged.addAll(newCat.presets);
                existingCat.presets = merged;
            } else {
                // Add new category
                existingCategories.put(newCat.id, newCat);
            }
        }

        GraphPresetRules result = new GraphPresetRules();
        result.version = existingRules.version;
        result.categories = new ArrayList<>(existingCategories.values());

        return result;
    }

    /**
     * Generates a new graph_presets.json file with converted presets.
     *
     * @param presetDirectory directory containing new format presets
     * @param outputPath path to write the merged graph_presets.json
     * @param existingJsonPath path to existing graph_presets.json (optional)
     */
    public static void generateGraphPresetsJson(Path presetDirectory, Path outputPath, Path existingJsonPath)
            throws IOException {

        GraphPresetRules rules;

        if (existingJsonPath != null && Files.exists(existingJsonPath)) {
            GraphPresetRules existingRules = loadExistingRules(existingJsonPath);
            rules = mergePresets(existingRules, presetDirectory);
            LOGGER.info("Merged new presets with existing graph_presets.json");
        } else {
            // Convert all new presets
            rules = convertDirectory(presetDirectory);
            LOGGER.info("Generated graph_presets.json from scratch");
        }

        // Write output
        String json = GSON.toJson(rules);
        Files.writeString(outputPath, json);

        LOGGER.info("Wrote graph_presets.json to: {}", outputPath);
        LOGGER.info("Total categories: {}", rules.categories.size());
        int totalPresets = rules.categories.stream()
            .mapToInt(c -> c.presets.size())
            .sum();
        LOGGER.info("Total presets: {}", totalPresets);
    }

    private static GraphPresetRules loadExistingRules(Path existingJsonPath) throws IOException {
        try {
            String existingJson = Files.readString(existingJsonPath);
            GraphPresetRules existingRules = GSON.fromJson(existingJson, GraphPresetRules.class);
            if (existingRules != null && existingRules.categories != null) {
                return existingRules;
            }
        } catch (JsonSyntaxException e) {
            LOGGER.warn(
                    "Existing graph_presets.json is invalid; rebuilding from default composites and converted presets: {}",
                    e.getMessage());
        }
        return defaultCompositeRules();
    }

    private static GraphPresetRules defaultCompositeRules() {
        GraphPresetRules rules = new GraphPresetRules();
        rules.version = 1;

        GraphPresetRules.PresetCategory category = new GraphPresetRules.PresetCategory();
        category.id = "composites";
        category.displayName = "Node Combinations";
        category.presets = List.of(
                compositePreset(
                        "composite.textured_box",
                        "Textured Box",
                        "Box -> Assign Block Type -> Geometry Viewer",
                        List.of(
                                node("box", "geometry.primitives.box", 0.0f, 0.0f),
                                node("material", "material.basic_assignment.assign_block_type", 300.0f, 0.0f),
                                node("viewer", "output.preview.geometry_viewer", 600.0f, 0.0f)
                        ),
                        List.of(
                                connection("box", "output_geometry", "material", "input_geometry"),
                                connection("material", "output_positions", "viewer", "input_blocks")
                        )
                ),
                compositePreset(
                        "composite.array_transform_deform",
                        "Array Transform",
                        "Box -> Linear Array -> Transform Geometry",
                        List.of(
                                node("box", "geometry.primitives.box", 0.0f, 0.0f),
                                node("array", "pattern.linear.linear_array_geometry", 300.0f, 0.0f),
                                node("transform", "transform.basic_transforms.transform_geometry", 600.0f, 0.0f)
                        ),
                        List.of(
                                connection("box", "output_geometry", "array", "input_geometry"),
                                connection("array", "output_geometry", "transform", "input_geometry")
                        )
                ),
                compositePreset(
                        "composite.boolean_cut_bake",
                        "Boolean Cut Bake",
                        "Box A + Box B -> Difference -> Bake Geometry To Blocks",
                        List.of(
                                node("base", "geometry.primitives.box", 0.0f, 0.0f),
                                node("cutter", "geometry.primitives.box", 0.0f, 180.0f),
                                node("difference", "geometry.boolean.difference", 320.0f, 80.0f),
                                node("bake", "output.execute.bake_geometry_to_blocks", 620.0f, 80.0f)
                        ),
                        List.of(
                                connection("base", "output_geometry", "difference", "input_base"),
                                connection("cutter", "output_geometry", "difference", "input_cutter"),
                                connection("difference", "output_geometry", "bake", "input_geometry")
                        )
                )
        );
        rules.categories = List.of(category);
        return rules;
    }

    private static GraphPresetRules.GraphPresetDefinition compositePreset(
            String id,
            String displayName,
            String description,
            List<GraphPresetRules.PresetNode> nodes,
            List<GraphPresetRules.PresetConnection> connections) {
        GraphPresetRules.GraphPresetDefinition preset = new GraphPresetRules.GraphPresetDefinition();
        preset.id = id;
        preset.displayName = displayName;
        preset.description = description;
        preset.kind = "composite";
        preset.nodes = nodes;
        preset.connections = connections;
        return preset;
    }

    private static GraphPresetRules.PresetNode node(String ref, String typeId, float x, float y) {
        GraphPresetRules.PresetNode node = new GraphPresetRules.PresetNode();
        node.ref = ref;
        node.typeId = typeId;
        node.x = x;
        node.y = y;
        return node;
    }

    private static GraphPresetRules.PresetConnection connection(
            String fromRef,
            String fromPort,
            String toRef,
            String toPort) {
        GraphPresetRules.PresetConnection connection = new GraphPresetRules.PresetConnection();
        connection.fromRef = fromRef;
        connection.fromPort = fromPort;
        connection.toRef = toRef;
        connection.toPort = toPort;
        return connection;
    }
}
