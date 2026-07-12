package com.nodecraft.nodesystem.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves and validates export output paths under a fixed sandbox directory.
 */
public final class ExportPathUtil {

    public static final String EXPORT_ROOT_DIR_NAME = "nodecraft_exports";

    private ExportPathUtil() {
    }

    public static Path exportRoot() {
        return FabricLoader.getInstance().getGameDir()
            .resolve(EXPORT_ROOT_DIR_NAME)
            .toAbsolutePath()
            .normalize();
    }

    /**
     * Resolves a user-provided export path under the NodeCraft export root directory.
     *
     * @throws IllegalArgumentException when the path escapes the export root
     */
    public static Path resolve(String rawPath, String defaultFileName, String extension) {
        return resolveUnderRoot(exportRoot(), rawPath, defaultFileName, extension);
    }

    static Path resolveUnderRoot(Path exportRoot, String rawPath, String defaultFileName, String extension) {
        String candidate = (rawPath == null || rawPath.isBlank()) ? defaultFileName : rawPath.trim();
        String normalizedExt = extension.startsWith(".") ? extension : "." + extension;
        if (!candidate.toLowerCase(Locale.ROOT).endsWith(normalizedExt.toLowerCase(Locale.ROOT))) {
            candidate = candidate + normalizedExt;
        }

        Path root = exportRoot.toAbsolutePath().normalize();
        Path input = Path.of(candidate);
        Path resolved = input.isAbsolute()
            ? input.toAbsolutePath().normalize()
            : root.resolve(input).toAbsolutePath().normalize();

        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Export path must stay inside " + root);
        }
        return resolved;
    }
}
