package com.nodecraft.nodesystem.preset;

import com.nodecraft.nodesystem.graph.NodeGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the preset system.
 */
public class PresetSystemTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setup() throws IOException {
        // Create a simple test preset
        String presetJson = """
        {
          "preset_id": "test.simple_box",
          "version": "1.0.0",
          "schema_version": "1.0",
          "metadata": {
            "name": "Test Box",
            "description": "A simple test box",
            "author": "Test",
            "tags": ["test"],
            "category": "test",
            "difficulty": "beginner",
            "estimated_build_time": "1 min",
            "estimated_node_count": 2
          },
          "thumbnails": {
            "main": "thumb.png",
            "previews": []
          },
          "parameters": [
            {
              "id": "size",
              "name": "Size",
              "type": "integer",
              "default": 5,
              "min": 1,
              "max": 10
            }
          ],
          "graph": {
            "nodes": [
              {
                "id": "node_1",
                "type": "geometry.primitives.box",
                "position": {"x": 100, "y": 100},
                "parameters": {
                  "size": {"param": "size"}
                }
              }
            ],
            "connections": []
          },
          "documentation": {
            "learning_notes": "Test preset",
            "tips": [],
            "related_presets": []
          }
        }
        """;

        Path presetFile = tempDir.resolve("preset.json");
        Files.writeString(presetFile, presetJson);
    }

    @Test
    public void testPresetLoading() throws IOException {
        Path presetFile = tempDir.resolve("preset.json");
        PresetDefinition preset = PresetLoader.load(presetFile);

        assertNotNull(preset);
        assertEquals("test.simple_box", preset.getPresetId());
        assertEquals("1.0.0", preset.getVersion());
        assertEquals("Test Box", preset.getMetadata().getName());
        assertEquals(1, preset.getParameters().size());
    }

    @Test
    public void testPresetRegistry() {
        PresetRegistry registry = new PresetRegistry();
        registry.loadPresets(tempDir.getParent());

        assertTrue(registry.getPresetCount() >= 0);
    }

    @Test
    public void testParameterValidation() {
        PresetParameter param = new PresetParameter(
            "test",
            "Test",
            ParameterType.INTEGER,
            5,
            1,
            10,
            1,
            "Test parameter",
            "Test",
            null
        );

        assertEquals(5, param.validateValue(5));
        assertEquals(1, param.validateValue(0)); // Clamped to min
        assertEquals(10, param.validateValue(15)); // Clamped to max
    }

    @Test
    public void testPresetSearch() throws IOException {
        PresetRegistry registry = new PresetRegistry();
        Path presetFile = tempDir.resolve("preset.json");
        PresetDefinition preset = PresetLoader.load(presetFile);
        registry.registerPreset(preset);

        var results = registry.search("box");
        assertEquals(1, results.size());

        results = registry.search("nonexistent");
        assertEquals(0, results.size());
    }

    @Test
    public void testMetadataI18n() throws IOException {
        Path presetFile = tempDir.resolve("preset.json");
        PresetDefinition preset = PresetLoader.load(presetFile);

        assertEquals("Test Box", preset.getMetadata().getName());
        assertEquals("Test Box", preset.getMetadata().getName("en_US"));
    }
}
