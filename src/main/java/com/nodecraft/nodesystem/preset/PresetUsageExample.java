package com.nodecraft.nodesystem.preset;

import com.nodecraft.nodesystem.graph.NodeGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Example usage of the preset system.
 *
 * <p>This class demonstrates how to use presets in your code.</p>
 */
public class PresetUsageExample {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetUsageExample.class);

    /**
     * Example: Load and instantiate a preset with default parameters.
     */
    public static void example1_BasicUsage() {
        // Get the preset registry
        PresetRegistry registry = PresetRegistry.getInstance();

        // Get a specific preset
        PresetDefinition preset = registry.getPreset("quickstart.basic_box");

        if (preset == null) {
            LOGGER.error("Preset not found");
            return;
        }

        try {
            // Instantiate with default parameters
            NodeGraph graph = PresetInstantiator.instantiate(preset);

            LOGGER.info("Created graph with {} nodes", graph.getNodes().size());

            // Use the graph...
            // editor.setGraph(graph);
            // executor.execute(graph);

        } catch (PresetInstantiator.PresetInstantiationException e) {
            LOGGER.error("Failed to instantiate preset", e);
        }
    }

    /**
     * Example: Instantiate a preset with custom parameters.
     */
    public static void example2_CustomParameters() {
        PresetRegistry registry = PresetRegistry.getInstance();
        PresetDefinition preset = registry.getPreset("quickstart.basic_box");

        if (preset == null) {
            LOGGER.error("Preset not found");
            return;
        }

        try {
            // Specify custom parameter values
            Map<String, Object> params = Map.of(
                "width", 10,
                "height", 15,
                "depth", 10,
                "material", "minecraft:oak_planks"
            );

            NodeGraph graph = PresetInstantiator.instantiate(preset, params);

            LOGGER.info("Created custom box: {}x{}x{}", 10, 15, 10);

        } catch (PresetInstantiator.PresetInstantiationException e) {
            LOGGER.error("Failed to instantiate preset", e);
        }
    }

    /**
     * Example: Search for presets.
     */
    public static void example3_SearchPresets() {
        PresetRegistry registry = PresetRegistry.getInstance();

        // Search by query
        var results = registry.search("tower");
        LOGGER.info("Found {} presets matching 'tower'", results.size());

        // Search with filters
        var beginnerPresets = registry.search(
            "",  // empty query = match all
            null,  // no tag filter
            PresetDifficulty.BEGINNER,  // only beginner presets
            null   // any category
        );
        LOGGER.info("Found {} beginner presets", beginnerPresets.size());

        // Get presets by category
        var quickstartPresets = registry.getPresetsByCategory("quickstart");
        LOGGER.info("Found {} quickstart presets", quickstartPresets.size());
    }

    /**
     * Example: Inspect preset metadata before instantiation.
     */
    public static void example4_InspectPreset() {
        PresetRegistry registry = PresetRegistry.getInstance();
        PresetDefinition preset = registry.getPreset("quickstart.simple_tower");

        if (preset == null) {
            LOGGER.error("Preset not found");
            return;
        }

        PresetMetadata metadata = preset.getMetadata();

        LOGGER.info("Preset: {}", metadata.getName());
        LOGGER.info("Description: {}", metadata.getDescription());
        LOGGER.info("Author: {}", metadata.getAuthor());
        LOGGER.info("Difficulty: {}", metadata.getDifficulty());
        LOGGER.info("Tags: {}", metadata.getTags());
        LOGGER.info("Estimated nodes: {}", metadata.getEstimatedNodeCount());

        // List parameters
        LOGGER.info("Parameters:");
        for (PresetParameter param : preset.getParameters()) {
            LOGGER.info("  - {} ({}): default={}, range=[{}, {}]",
                param.getName(),
                param.getType(),
                param.getDefaultValue(),
                param.getMinValue(),
                param.getMaxValue()
            );
        }
    }

    /**
     * Example: Get all available presets.
     */
    public static void example5_ListAllPresets() {
        PresetRegistry registry = PresetRegistry.getInstance();

        LOGGER.info("Total presets: {}", registry.getPresetCount());
        LOGGER.info("Categories: {}", registry.getCategories());

        for (PresetDefinition preset : registry.getAllPresets()) {
            LOGGER.info("  - {} ({}): {}",
                preset.getPresetId(),
                preset.getMetadata().getCategory(),
                preset.getMetadata().getName()
            );
        }
    }

    public static void main(String[] args) {
        // Note: In actual usage, the registry is initialized by NodeCraft.onInitialize()
        // This is just for demonstration

        LOGGER.info("=== Preset System Examples ===");

        example1_BasicUsage();
        example2_CustomParameters();
        example3_SearchPresets();
        example4_InspectPreset();
        example5_ListAllPresets();
    }
}
