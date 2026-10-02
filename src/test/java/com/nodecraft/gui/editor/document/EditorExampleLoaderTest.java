package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.preset.GraphPresetCatalog;
import com.nodecraft.gui.preset.GraphPresetRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorExampleLoaderTest {

    @BeforeAll
    static void loadCatalog() {
        GraphPresetCatalog.getInstance().reload();
    }

    @Test
    void listsQuickstartExamplesFromGraphCatalog() {
        var examples = EditorExampleLoader.listQuickstartExamples();
        assertFalse(examples.isEmpty(), "quickstart examples should be available");
        assertTrue(examples.size() >= 4, "expected at least four quickstart graph presets");
    }

    @Test
    void findsKnownQuickstartExampleById() {
        EditorExampleEntry entry = EditorExampleLoader.findQuickstartEntry("quickstart.basic_box");
        assertNotNull(entry);
        assertEquals("quickstart.basic_box", entry.id());
    }

    @Test
    void quickstartExampleMatchesGraphCatalogDefinition() {
        GraphPresetRules.GraphPresetDefinition catalogPreset =
                GraphPresetCatalog.getInstance().getPresetDefinition("quickstart.basic_box");
        assertNotNull(catalogPreset);

        EditorExampleEntry entry = EditorExampleLoader.findQuickstartEntry("quickstart.basic_box");
        assertNotNull(entry);
        assertEquals(catalogPreset.displayName, entry.displayName());
        assertEquals(catalogPreset.description, entry.description());
    }
}
