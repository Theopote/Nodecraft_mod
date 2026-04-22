package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Maps canonical registry categories into node-library presentation categories.
 *
 * This mapper intentionally stays simple for v1:
 * - The registry remains the source of truth for canonical categories.
 * - The library UI uses this mapper as a presentation layer.
 * - Only canonical presentation is exposed to users.
 */
public final class NodeCategoryPresentationMapper {

    public record CategoryPresentation(
        NodeCategory sourceCategory,
        String canonicalCategoryId,
        String displayCategoryId,
        String displayName,
        String parentDisplayId,
        String colorKey,
        boolean defaultExpanded
    ) {
    }

    private static final Set<String> DEFAULT_EXPANDED_DISPLAY_IDS = Set.of(
        "geometry", "input", "material", "math", "output", "pattern", "reference", "transform", "world", "utilities",
        "input.values", "input.numeric", "input.context", "input.type_selectors",
        "reference.points", "reference.vectors", "reference.planes", "reference.frames",
        "world.selection", "world.read", "world.query", "world.write",
        "geometry.boolean", "geometry.curves", "geometry.primitives", "geometry.profiles", "geometry.solids", "geometry.architectural_primitives",
        "pattern.linear", "pattern.grid", "pattern.radial", "pattern.surface_volume_distribution",
        "transform.basic_transforms", "transform.deformations", "transform.orientation",
        "math.scalar_math", "math.compare", "math.logic", "math.random", "math.trigonometry", "math.sequence", "math.list",
        "output.preview", "output.execute", "output.export", "output.debug",
        "utilities.organization", "utilities.assist",
        "material.basic_assignment", "material.gradient_mapping", "material.directional_mapping",
        "material.pattern_mapping", "material.block_state", "material.surface_aging"
    );

    /**
     * Builds UI presentation categories from canonical registry categories.
     */
    public List<CategoryPresentation> mapCategories(List<NodeCategory> canonicalCategories) {
        List<CategoryPresentation> mapped = new ArrayList<>();
        if (canonicalCategories == null) {
            return mapped;
        }

        for (NodeCategory category : canonicalCategories) {
            if (category == null || category.getId() == null) {
                continue;
            }

            String canonicalId = category.getId().toLowerCase();
            String displayId = canonicalId;
            String parentDisplayId = canonicalId.contains(".")
                ? canonicalId.substring(0, canonicalId.lastIndexOf('.'))
                : null;

            mapped.add(new CategoryPresentation(
                category,
                canonicalId,
                displayId,
                category.getDisplayName(),
                parentDisplayId,
                canonicalId,
                DEFAULT_EXPANDED_DISPLAY_IDS.contains(displayId)
            ));
        }

        return List.copyOf(mapped);
    }
}
