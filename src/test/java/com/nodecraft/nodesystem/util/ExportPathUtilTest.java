package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportPathUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativePathUnderRoot() {
        Path root = tempDir.resolve("nodecraft_exports");
        Path resolved = ExportPathUtil.resolveUnderRoot(root, "schematics/out.litematic", "nodecraft_export.litematic", ".litematic");

        assertEquals(root.resolve("schematics").resolve("out.litematic").normalize(), resolved);
        assertTrue(resolved.startsWith(root.normalize()));
    }

    @Test
    void appendsMissingExtension() {
        Path root = tempDir.resolve("nodecraft_exports");
        Path resolved = ExportPathUtil.resolveUnderRoot(root, "exports/data", "nodecraft_export.csv", ".csv");

        assertEquals(root.resolve("exports").resolve("data.csv").normalize(), resolved);
    }

    @Test
    void rejectsPathTraversal() {
        Path root = tempDir.resolve("nodecraft_exports");

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ExportPathUtil.resolveUnderRoot(root, "../../../escape.litematic", "nodecraft_export.litematic", ".litematic")
        );
        assertTrue(error.getMessage().contains("Export path must stay inside"));
    }

    @Test
    void rejectsAbsolutePathOutsideRoot() {
        Path root = tempDir.resolve("nodecraft_exports");
        Path outside = tempDir.resolve("outside").resolve("file.nbt");

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ExportPathUtil.resolveUnderRoot(root, outside.toString(), "nodecraft_export.nbt", ".nbt")
        );
        assertTrue(error.getMessage().contains("Export path must stay inside"));
    }

    @Test
    void allowsAbsolutePathInsideRoot() {
        Path root = tempDir.resolve("nodecraft_exports");
        Path inside = root.resolve("nested").resolve("file.schem");

        Path resolved = ExportPathUtil.resolveUnderRoot(root, inside.toString(), "nodecraft_export.schem", ".schem");

        assertEquals(inside.normalize(), resolved);
    }
}
