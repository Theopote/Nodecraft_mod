package com.nodecraft.nodesystem.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates default import read paths for file-input nodes when external paths are disabled.
 * Existing files and allowlist roots are compared via {@link Path#toRealPath()} when present
 * so symlink escapes outside the allowlist are rejected.
 */
public final class ImportPathUtil {

    public enum ImportKind {
        IMAGE,
        VOX
    }

    private ImportPathUtil() {
    }

    public static boolean isAllowedDefaultPath(Path path, ImportKind kind) {
        return isAllowedDefaultPath(path, FabricLoader.getInstance().getGameDir(), kind);
    }

    static boolean isAllowedDefaultPath(Path path, Path gameDir, ImportKind kind) {
        Path resolvedPath = resolveForCompare(path.toAbsolutePath().normalize());
        Path gameRoot = resolveForCompare(gameDir.toAbsolutePath().normalize());
        Path nodecraftConfig = resolveForCompare(gameRoot.resolve("config").resolve("nodecraft"));

        if (kind == ImportKind.IMAGE) {
            if (startsWithDirectory(resolvedPath, resolveForCompare(nodecraftConfig.resolve("images")))) {
                return true;
            }
            if (startsWithDirectory(resolvedPath, resolveForCompare(gameRoot.resolve("screenshots")))) {
                return true;
            }
        } else {
            if (startsWithDirectory(resolvedPath, resolveForCompare(nodecraftConfig.resolve("assets")))) {
                return true;
            }
            if (startsWithDirectory(resolvedPath, resolveForCompare(nodecraftConfig.resolve("vox")))) {
                return true;
            }
        }

        return startsWithDirectory(resolvedPath, resolveForCompare(gameRoot.resolve("saves")));
    }

    /**
     * Lexical absolute normalize; when the path exists, prefer the real path (symlink-resolved).
     */
    private static Path resolveForCompare(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            return normalized;
        }
        try {
            return normalized.toRealPath();
        } catch (IOException ignored) {
            return normalized;
        }
    }

    private static boolean startsWithDirectory(Path path, Path directory) {
        return path.startsWith(directory);
    }
}
