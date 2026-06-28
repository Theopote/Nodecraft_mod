package com.nodecraft.nodesystem.preset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry for all available presets.
 *
 * <p>Manages loading, caching, and querying of preset definitions.
 * This is a singleton that should be initialized at mod startup.</p>
 */
public class PresetRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetRegistry.class);
    private static final PresetRegistry INSTANCE = new PresetRegistry();

    private final Map<String, PresetDefinition> presets = new HashMap<>();
    private final Map<String, List<PresetDefinition>> presetsByCategory = new HashMap<>();
    private boolean initialized = false;

    private PresetRegistry() {
        // Private constructor for singleton
    }

    public static PresetRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Loads all presets from a directory.
     *
     * <p>Scans for preset.json files recursively and loads them into the registry.</p>
     *
     * @param presetDirectory the root preset directory (e.g., config/nodecraft/presets)
     */
    public void loadPresets(Path presetDirectory) {
        if (!Files.exists(presetDirectory)) {
            LOGGER.warn("Preset directory does not exist: {}", presetDirectory);
            try {
                Files.createDirectories(presetDirectory);
                LOGGER.info("Created preset directory: {}", presetDirectory);
            } catch (IOException e) {
                LOGGER.error("Failed to create preset directory", e);
            }
            return;
        }

        LOGGER.info("Loading presets from: {}", presetDirectory);

        try (Stream<Path> paths = Files.walk(presetDirectory)) {
            List<Path> presetFiles = paths
                .filter(p -> p.getFileName().toString().equals("preset.json"))
                .collect(Collectors.toList());

            int loadedCount = 0;
            int failedCount = 0;

            for (Path presetFile : presetFiles) {
                try {
                    PresetDefinition preset = PresetLoader.load(presetFile);
                    registerPreset(preset);
                    loadedCount++;
                } catch (IOException e) {
                    LOGGER.error("Failed to load preset from: {}", presetFile, e);
                    failedCount++;
                }
            }

            LOGGER.info("Loaded {} presets ({} failed)", loadedCount, failedCount);
            initialized = true;

        } catch (IOException e) {
            LOGGER.error("Failed to scan preset directory", e);
        }
    }

    /**
     * Registers a preset in the registry.
     *
     * @param preset the preset definition to register
     */
    public void registerPreset(PresetDefinition preset) {
        presets.put(preset.getPresetId(), preset);

        // Index by category
        String category = preset.getMetadata().getCategory();
        presetsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(preset);

        LOGGER.debug("Registered preset: {}", preset.getPresetId());
    }

    /**
     * Gets a preset by its ID.
     *
     * @param presetId the preset identifier
     * @return the preset definition, or null if not found
     */
    public PresetDefinition getPreset(String presetId) {
        return presets.get(presetId);
    }

    /**
     * Gets all registered presets.
     *
     * @return list of all presets
     */
    public List<PresetDefinition> getAllPresets() {
        return new ArrayList<>(presets.values());
    }

    /**
     * Gets all presets in a specific category.
     *
     * @param category the category name
     * @return list of presets in that category
     */
    public List<PresetDefinition> getPresetsByCategory(String category) {
        return presetsByCategory.getOrDefault(category, List.of());
    }

    /**
     * Gets all available categories.
     *
     * @return set of category names
     */
    public Set<String> getCategories() {
        return presetsByCategory.keySet();
    }

    /**
     * Searches presets by query string and optional filters.
     *
     * @param query search query (matches name, description, tags)
     * @param tags required tags (all must be present)
     * @param difficulty required difficulty level (null for any)
     * @param category required category (null for any)
     * @return list of matching presets
     */
    public List<PresetDefinition> search(String query, List<String> tags, PresetDifficulty difficulty, String category) {
        return presets.values().stream()
            .filter(preset -> {
                // Match query
                if (!preset.getMetadata().matchesQuery(query)) {
                    return false;
                }

                // Match tags
                if (tags != null && !tags.isEmpty() && !preset.getMetadata().hasTags(tags)) {
                    return false;
                }

                // Match difficulty
                if (difficulty != null && preset.getMetadata().getDifficulty() != difficulty) {
                    return false;
                }

                // Match category
                if (category != null && !preset.getMetadata().getCategory().equals(category)) {
                    return false;
                }

                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * Simple search by query only.
     *
     * @param query search query
     * @return list of matching presets
     */
    public List<PresetDefinition> search(String query) {
        return search(query, null, null, null);
    }

    /**
     * Gets the total number of registered presets.
     *
     * @return preset count
     */
    public int getPresetCount() {
        return presets.size();
    }

    /**
     * Checks if the registry has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Clears all registered presets.
     * Useful for testing or reloading.
     */
    public void clear() {
        presets.clear();
        presetsByCategory.clear();
        initialized = false;
        LOGGER.info("Cleared preset registry");
    }

    /**
     * Reloads all presets from the directory.
     *
     * @param presetDirectory the preset directory
     */
    public void reload(Path presetDirectory) {
        clear();
        loadPresets(presetDirectory);
    }
}
