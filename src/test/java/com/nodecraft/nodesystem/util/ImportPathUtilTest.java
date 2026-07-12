package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportPathUtilTest {

    @TempDir
    Path gameDir;

    @Test
    void allowsNodecraftImageDirectory() {
        Path image = gameDir.resolve("config").resolve("nodecraft").resolve("images").resolve("logo.png");

        assertTrue(ImportPathUtil.isAllowedDefaultPath(image, gameDir, ImportPathUtil.ImportKind.IMAGE));
    }

    @Test
    void allowsScreenshotsDirectory() {
        Path screenshot = gameDir.resolve("screenshots").resolve("2026-01-01.png");

        assertTrue(ImportPathUtil.isAllowedDefaultPath(screenshot, gameDir, ImportPathUtil.ImportKind.IMAGE));
    }

    @Test
    void allowsNodecraftVoxDirectories() {
        Path asset = gameDir.resolve("config").resolve("nodecraft").resolve("assets").resolve("model.vox");
        Path vox = gameDir.resolve("config").resolve("nodecraft").resolve("vox").resolve("model.vox");

        assertTrue(ImportPathUtil.isAllowedDefaultPath(asset, gameDir, ImportPathUtil.ImportKind.VOX));
        assertTrue(ImportPathUtil.isAllowedDefaultPath(vox, gameDir, ImportPathUtil.ImportKind.VOX));
    }

    @Test
    void allowsWorldSaveDirectoryViaPathPrefix() {
        Path saveAsset = gameDir.resolve("saves").resolve("Creative").resolve("nodecraft").resolve("ref.png");

        assertTrue(ImportPathUtil.isAllowedDefaultPath(saveAsset, gameDir, ImportPathUtil.ImportKind.IMAGE));
        assertTrue(ImportPathUtil.isAllowedDefaultPath(saveAsset, gameDir, ImportPathUtil.ImportKind.VOX));
    }

    @Test
    void rejectsGameRootFilesWithoutExternalPathsEnabled() {
        Path serverProperties = gameDir.resolve("server.properties");
        Path modConfig = gameDir.resolve("config").resolve("some-mod").resolve("secrets.json");

        assertFalse(ImportPathUtil.isAllowedDefaultPath(serverProperties, gameDir, ImportPathUtil.ImportKind.IMAGE));
        assertFalse(ImportPathUtil.isAllowedDefaultPath(modConfig, gameDir, ImportPathUtil.ImportKind.VOX));
    }

    @Test
    void rejectsFakeSavesSubstringBypass() {
        Path fakePath = gameDir.resolve("tmp")
            .resolve("evil.minecraft")
            .resolve("saves")
            .resolve("secret.png");

        assertFalse(ImportPathUtil.isAllowedDefaultPath(fakePath, gameDir, ImportPathUtil.ImportKind.IMAGE));
    }
}
