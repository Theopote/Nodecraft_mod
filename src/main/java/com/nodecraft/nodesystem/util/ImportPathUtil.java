package com.nodecraft.nodesystem.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Validates default import read paths for file-input nodes when external paths are disabled.
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
        Path normalized = path.toAbsolutePath().normalize();
        Path gameRoot = gameDir.toAbsolutePath().normalize();
        Path nodecraftConfig = gameRoot.resolve("config").resolve("nodecraft").normalize();

        if (kind == ImportKind.IMAGE) {
            if (startsWithDirectory(normalized, nodecraftConfig.resolve("images"))) {
                return true;
            }
            if (startsWithDirectory(normalized, gameRoot.resolve("screenshots"))) {
                return true;
            }
        } else {
            if (startsWithDirectory(normalized, nodecraftConfig.resolve("assets"))) {
                return true;
            }
            if (startsWithDirectory(normalized, nodecraftConfig.resolve("vox"))) {
                return true;
            }
        }

        return startsWithDirectory(normalized, gameRoot.resolve("saves"));
    }

    private static boolean startsWithDirectory(Path path, Path directory) {
        Path base = directory.toAbsolutePath().normalize();
        return path.startsWith(base);
    }
}
