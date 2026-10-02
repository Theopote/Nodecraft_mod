package com.nodecraft.gui.preset;

import com.nodecraft.gui.editor.document.EditorExampleEntry;
import com.nodecraft.gui.editor.document.EditorExampleLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures menu quickstart examples and the preset library panel share one graph source.
 */
class PresetLibrarySourceConsistencyTest {

    @BeforeAll
    static void loadCatalog() {
        GraphPresetCatalog.getInstance().reload();
    }

    @Test
    void quickstartExamplesComeFromGraphCatalog() {
        var examples = EditorExampleLoader.listQuickstartExamples();
        assertFalse(examples.isEmpty(), "quickstart examples should be available");
        assertTrue(examples.size() >= 4, "expected at least four quickstart examples");

        for (EditorExampleEntry example : examples) {
            GraphPresetCatalog.PresetView view = GraphPresetCatalog.getInstance().findPreset(example.id());
            assertNotNull(view, "missing catalog entry for " + example.id());
            assertEquals(EditorExampleLoader.QUICKSTART_CATEGORY_ID, view.categoryId());
            assertEquals(example.displayName(), view.preset().displayName);
            assertTrue(view.isApplicable(), example.id() + " should be a composite preset");
        }
    }

    @Test
    void basicBoxQuickstartIncludesPreviewGeometryNode() {
        GraphPresetRules.GraphPresetDefinition preset =
                GraphPresetCatalog.getInstance().getPresetDefinition("quickstart.basic_box");
        assertNotNull(preset);

        Set<String> typeIds = new HashSet<>();
        for (GraphPresetRules.PresetNode node : preset.nodes) {
            if (node != null && node.typeId != null) {
                typeIds.add(node.typeId);
            }
        }
        assertTrue(
                typeIds.contains("output.preview.preview_geometry"),
                "basic_box must include Preview Geometry (canonical v2 chain)");
        assertTrue(
                typeIds.contains("output.preview.preview_blocks"),
                "basic_box must include Preview Blocks");
    }
}
