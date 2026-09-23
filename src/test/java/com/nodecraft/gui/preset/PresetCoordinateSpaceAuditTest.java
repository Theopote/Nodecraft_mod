package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer A coordinate-space static audit for Preset Library v2 canonical presets.
 *
 * <p>Complements {@link PresetSemanticAuditTest} topology checks with heuristics for:</p>
 * <ul>
 *   <li>Double translation — Player Position anchors primitives/components <em>and</em> drives Move Geometry</li>
 *   <li>Split preview chain — Preview Geometry and Voxelize diverge through different Move Geometry anchors</li>
 *   <li>World-space without move — Player Position placement without final move (non-allowlisted presets)</li>
 * </ul>
 */
class PresetCoordinateSpaceAuditTest {

    private static final Gson GSON = new Gson();
    private static final List<String> RESOURCE_PATHS = List.of(
            "/nodecraft/graph_presets.json",
            "/nodecraft/graph_presets_updated.json");

    /** Mirrors {@link PresetSemanticAuditTest} canonical coverage (P0–P3). */
    private static final Set<String> CANONICAL_IDS = Set.of(
            "composite.textured_box",
            "composite.array_transform_deform",
            "composite.boolean_cut_bake",
            "quickstart.basic_box",
            "quickstart.basic_sphere",
            "quickstart.garden_wall",
            "quickstart.simple_tower",
            "architectural.residential.mini_building_v1",
            "building_elements.roofs.gable_roof",
            "building_elements.stairs.straight_staircase",
            "building_elements.columns.classical_column",
            "building_elements.doors.simple_door",
            "building_elements.windows.modern_window",
            "building_elements.windows.arched_window",
            "building_elements.stairs.spiral_staircase",
            "architectural.residential.medieval_cottage",
            "architectural.residential.simple_house",
            "architectural.infrastructure.stone_bridge",
            "architectural.infrastructure.watchtower",
            "decorative.fountain_circular",
            "decorative.gazebo",
            "styles.fantasy.wizard_tower",
            "styles.medieval.castle_keep",
            "styles.modern.glass_box_building");

    /**
     * Known coordinate-space violations tracked until presets are normalized to the local-space contract.
     * Remove entries as presets are fixed; the test fails if new violations appear or expected ones linger.
     */
    private static final Map<String, Set<String>> EXPECTED_VIOLATIONS = expectedViolations();

    private static Map<String, Set<String>> expectedViolations() {
        return Map.of();
    }

    @Test
    void canonicalPresetsMatchExpectedCoordinateSpaceViolations() {
        for (String resourcePath : RESOURCE_PATHS) {
            GraphPresetRules rules = loadRules(resourcePath);
            List<String> errors = new ArrayList<>();

            for (GraphPresetRules.PresetCategory category : rules.categories) {
                if (category == null || category.presets == null) {
                    continue;
                }
                for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                    if (preset == null || !CANONICAL_IDS.contains(preset.id)) {
                        continue;
                    }
                    Set<String> actual = PresetCoordinateSpaceAuditor.violationCodesIncludingWorldSpacePolicy(preset);
                    Set<String> expected = EXPECTED_VIOLATIONS.getOrDefault(preset.id, Set.of());
                    if (!expected.equals(actual)) {
                        errors.add(describeMismatch(resourcePath, preset, expected, actual));
                    }
                }
            }

            assertTrue(
                    errors.isEmpty(),
                    resourcePath + System.lineSeparator() + String.join(System.lineSeparator(), errors));
        }
    }

    private static final Set<String> WORLD_SPACE_ANCHOR_ALLOWLIST = Set.of(
            "quickstart.basic_box",
            "quickstart.basic_sphere",
            "building_elements.stairs.straight_staircase");

    @Test
    void worldSpaceAnchorAllowlistPresetsStayClean() {
        for (String presetId : WORLD_SPACE_ANCHOR_ALLOWLIST) {
            GraphPresetRules.GraphPresetDefinition preset = findPreset(loadRules("/nodecraft/graph_presets.json"), presetId);
            assertNotNull(preset, presetId);
            Set<String> codes = PresetCoordinateSpaceAuditor.violationCodesIncludingWorldSpacePolicy(preset);
            assertEquals(Set.of(), codes, presetId + " should remain on the intentional world/path anchor allowlist");
        }
    }

    private static String describeMismatch(
            String resourcePath,
            GraphPresetRules.GraphPresetDefinition preset,
            Set<String> expected,
            Set<String> actual) {
        StringBuilder message = new StringBuilder();
        message.append(resourcePath).append(" ").append(preset.id).append(':').append(System.lineSeparator());
        message.append("  expected violations: ").append(expected.isEmpty() ? "(none)" : expected)
                .append(System.lineSeparator());
        message.append("  actual violations:   ").append(actual.isEmpty() ? "(none)" : actual)
                .append(System.lineSeparator());
        for (PresetCoordinateSpaceAuditor.Violation violation
                : PresetCoordinateSpaceAuditor.auditIncludingWorldSpacePolicy(preset)) {
            message.append("    - ").append(violation.code()).append(": ").append(violation.detail())
                    .append(System.lineSeparator());
        }
        if (!expected.isEmpty() && actual.isEmpty()) {
            message.append("  (remove this preset from EXPECTED_VIOLATIONS — violations were fixed)");
        }
        if (expected.isEmpty() && !actual.isEmpty()) {
            message.append("  (new violation — fix preset or add to EXPECTED_VIOLATIONS while tracking)");
        }
        return message.toString().stripTrailing();
    }

    private static GraphPresetRules.GraphPresetDefinition findPreset(GraphPresetRules rules, String id) {
        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                if (preset != null && id.equals(preset.id)) {
                    return preset;
                }
            }
        }
        return null;
    }

    private static GraphPresetRules loadRules(String resourcePath) {
        try (InputStream stream = PresetCoordinateSpaceAuditTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing " + resourcePath);
            GraphPresetRules rules = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    GraphPresetRules.class);
            assertNotNull(rules, "Failed to parse " + resourcePath);
            assertFalse(rules.categories == null || rules.categories.isEmpty());
            return rules;
        } catch (Exception e) {
            throw new AssertionError("Failed to load " + resourcePath, e);
        }
    }
}
