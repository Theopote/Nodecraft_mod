package com.nodecraft.gui.preset;

import com.nodecraft.nodesystem.preset.PresetDefinition;
import com.nodecraft.nodesystem.preset.PresetLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Folder-format exports under {@code presets/} must parse and load without errors.
 */
class PresetFolderLoadTest {

    @Test
    void allFolderPresetsLoadViaPresetLoader() throws Exception {
        Optional<Path> presetsRoot = resolveRepoPresetsRoot();
        if (presetsRoot.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<>();
        try (var paths = Files.walk(presetsRoot.get())) {
            for (Path presetFile : paths
                    .filter(path -> path.getFileName().toString().equals("preset.json"))
                    .sorted()
                    .toList()) {
                try {
                    PresetDefinition preset = PresetLoader.load(presetFile);
                    assertNotNull(preset.presetId());
                    assertFalse(preset.graph().nodes().isEmpty(), preset.presetId() + " has no nodes");
                } catch (Exception e) {
                    errors.add(presetsRoot.get().relativize(presetFile) + ": " + e.getMessage());
                }
            }
        }

        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
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
