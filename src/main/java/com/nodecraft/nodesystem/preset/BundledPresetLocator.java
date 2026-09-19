package com.nodecraft.nodesystem.preset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves shipped quickstart preset directories for dev (repo) and runtime (classpath) layouts.
 */
public final class BundledPresetLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundledPresetLocator.class);
    private static final String CLASSPATH_QUICKSTART = "/nodecraft/bundled_presets/quickstart";

    private BundledPresetLocator() {
    }

    /**
     * Root {@code presets/} directory when running from a checkout ({@code presets/quickstart/...}).
     */
    public static Optional<Path> resolveRepoPresetsRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path quickstart = dir.resolve("presets").resolve("quickstart");
            if (Files.isDirectory(quickstart)) {
                return Optional.of(dir.resolve("presets"));
            }
        }
        return Optional.empty();
    }

    /**
     * Quickstart presets extracted into resources at build time.
     */
    public static Optional<Path> resolveClasspathQuickstartRoot() {
        URL marker = BundledPresetLocator.class.getResource(CLASSPATH_QUICKSTART + "/basic-box/preset.json");
        if (marker == null) {
            return Optional.empty();
        }
        try {
            URI uri = marker.toURI();
            if (!"file".equals(uri.getScheme())) {
                return Optional.empty();
            }
            Path presetFile = Path.of(uri);
            Path quickstart = presetFile.getParent().getParent();
            if (Files.isDirectory(quickstart)) {
                return Optional.of(quickstart);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not resolve classpath quickstart presets: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public static void registerBundledQuickstartPresetsIfAbsent(PresetRegistry registry) {
        if (registry == null) {
            return;
        }
        resolveClasspathQuickstartRoot().ifPresent(root -> {
            LOGGER.debug("Loading bundled quickstart presets from classpath: {}", root);
            registry.loadPresetsIfAbsent(root);
        });
        resolveRepoPresetsRoot()
                .map(root -> root.resolve("quickstart"))
                .ifPresent(root -> {
                    LOGGER.debug("Loading repo quickstart presets: {}", root);
                    registry.loadPresetsIfAbsent(root);
                });
    }
}
