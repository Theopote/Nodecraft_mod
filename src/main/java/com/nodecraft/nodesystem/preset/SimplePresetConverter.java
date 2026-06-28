package com.nodecraft.nodesystem.preset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple preset converter entry point.
 */
public class SimplePresetConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimplePresetConverter.class);

    public static void main(String[] args) {
        LOGGER.info("NodeCraft Preset Converter (Simple Version)");

        try {
            Path presetDir = Paths.get("presets");
            Path existingJson = Paths.get("src/main/resources/nodecraft/graph_presets.json");
            Path outputJson = Paths.get("src/main/resources/nodecraft/graph_presets_updated.json");

            LOGGER.info("Preset directory: {}", presetDir.toAbsolutePath());
            LOGGER.info("Existing JSON: {}", existingJson.toAbsolutePath());
            LOGGER.info("Output JSON: {}", outputJson.toAbsolutePath());

            if (!Files.exists(presetDir)) {
                LOGGER.error("Preset directory does not exist: {}", presetDir.toAbsolutePath());
                System.exit(1);
            }

            PresetFormatAdapter.generateGraphPresetsJson(
                presetDir,
                outputJson,
                existingJson
            );

            LOGGER.info("Conversion complete.");
            LOGGER.info("Output file: {}", outputJson.toAbsolutePath());
            LOGGER.info("File size: {} bytes", Files.size(outputJson));
            LOGGER.info("Next steps: review the output file, then replace {} if it looks correct.", existingJson);
        } catch (Exception e) {
            LOGGER.error("Conversion failed", e);
            System.exit(1);
        }
    }
}
