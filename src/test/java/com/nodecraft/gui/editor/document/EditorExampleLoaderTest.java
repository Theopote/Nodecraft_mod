package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.preset.GraphPresetCatalog;
import com.nodecraft.nodesystem.preset.BundledPresetLocator;
import com.nodecraft.nodesystem.preset.PresetDefinition;
import com.nodecraft.nodesystem.preset.PresetInstantiator;
import com.nodecraft.nodesystem.preset.PresetRegistry;
import com.nodecraft.nodesystem.registry.NodeRegistry;
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
        PresetRegistry.getInstance().clear();
        BundledPresetLocator.registerBundledQuickstartPresetsIfAbsent(PresetRegistry.getInstance());

        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void listsQuickstartExamplesFromBundledFullGraphPresets() {
        var examples = EditorExampleLoader.listQuickstartExamples();
        assertFalse(examples.isEmpty(), "quickstart examples should be available");
        assertTrue(examples.size() >= 4, "expected bundled quickstart full-graph presets");
    }

    @Test
    void findsKnownQuickstartExampleById() {
        EditorExampleEntry entry = EditorExampleLoader.findQuickstartEntry("quickstart.basic_box");
        assertNotNull(entry);
        assertEquals("quickstart.basic_box", entry.id());
    }

    @Test
    void instantiatesBundledQuickstartPresetGraph() throws Exception {
        PresetDefinition preset = PresetRegistry.getInstance().getPreset("quickstart.basic_box");
        assertNotNull(preset);

        PresetInstantiator.InstantiateResult result = PresetInstantiator.instantiateWithLayout(preset);
        assertFalse(result.graph().getNodes().isEmpty());
        assertFalse(result.nodePositions().isEmpty());
    }
}
