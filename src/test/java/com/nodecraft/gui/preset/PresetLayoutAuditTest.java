package com.nodecraft.gui.preset;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures authored preset node positions in graph_presets.json do not visually overlap
 * when loaded as quickstart examples (uses JSON x/y, not runtime auto-layout).
 */
class PresetLayoutAuditTest {

    private static final double MIN_SEPARATION = 40.0;

    @BeforeAll
    static void loadCatalog() {
        GraphPresetCatalog.getInstance().reload();
    }

    @Test
    void compositePresetsHaveNonOverlappingNodePositions() {
        List<String> failures = new ArrayList<>();
        for (GraphPresetCatalog.CategoryView categoryView : GraphPresetCatalog.getInstance().getCategories()) {
            if (categoryView.category() == null || categoryView.category().presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : categoryView.category().presets) {
                if (preset == null || !"composite".equalsIgnoreCase(preset.kind)) {
                    continue;
                }
                if (preset.nodes == null || preset.nodes.size() < 2) {
                    continue;
                }
                failures.addAll(findOverlaps(preset));
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static List<String> findOverlaps(GraphPresetRules.GraphPresetDefinition preset) {
        List<String> errors = new ArrayList<>();
        List<GraphPresetRules.PresetNode> nodes = preset.nodes.stream()
                .filter(n -> n != null && n.ref != null)
                .toList();
        for (int i = 0; i < nodes.size(); i++) {
            GraphPresetRules.PresetNode a = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                GraphPresetRules.PresetNode b = nodes.get(j);
                if (Math.abs(a.x - b.x) < MIN_SEPARATION && Math.abs(a.y - b.y) < MIN_SEPARATION) {
                    errors.add(preset.id + ": nodes '" + a.ref + "' and '" + b.ref
                            + "' overlap at (" + a.x + "," + a.y + ") vs (" + b.x + "," + b.y + ")");
                }
            }
        }
        return errors;
    }
}
