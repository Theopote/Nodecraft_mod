package com.nodecraft.nodesystem.semantic;

import com.nodecraft.nodesystem.api.NodeEffect;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic capability / domain derivation from typeId, category, and effect.
 *
 * <p>Priority: exact known families → category-family → {@link NodeEffect} → conservative fallback.
 * Prefer missing a capability over a false positive.</p>
 */
public final class NodeSemanticDeriver {

    private NodeSemanticDeriver() {
    }

    public static Set<NodeCapability> deriveCapabilities(String typeId, String category, NodeEffect effect) {
        Set<NodeCapability> caps = EnumSet.noneOf(NodeCapability.class);
        if (typeId == null || typeId.isBlank()) {
            applyEffectCaps(caps, effect);
            return caps;
        }
        String id = typeId.toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.toLowerCase(Locale.ROOT);

        // --- Exact / family rules (conservative) ---
        if (id.contains("wall_slab") || id.contains("wall_along") || id.contains("wall_with")
                || (cat.contains("architectural") && id.contains("wall"))) {
            caps.add(NodeCapability.WALL);
        }

        if (id.contains("window_array")) {
            caps.add(NodeCapability.WINDOW);
            caps.add(NodeCapability.OPENING);
            caps.add(NodeCapability.ARRAY);
        } else if (id.contains("window")) {
            caps.add(NodeCapability.WINDOW);
            // Do not auto-tag ARRAY / OPENING for every window* id (e.g. window_frame).
        }

        if (id.contains("door_array")) {
            caps.add(NodeCapability.OPENING);
            caps.add(NodeCapability.ARRAY);
        } else if (id.contains(".door") || id.endsWith("_door") || id.contains("door_")) {
            caps.add(NodeCapability.OPENING);
        }

        if (id.contains("roof") || id.contains("gable") || id.contains("hip_")) {
            caps.add(NodeCapability.ROOF);
        }

        // BOOLEAN_CUT only for subtractive difference — not union / intersection / generic boolean.
        if (id.contains("difference") || id.contains("subtract")) {
            caps.add(NodeCapability.BOOLEAN_CUT);
        }

        if (id.contains("linear_array") || id.contains("polar_array")
                || id.endsWith("_array") || id.contains(".array.")) {
            caps.add(NodeCapability.ARRAY);
        } else if (id.contains("array") && !id.contains("window") && !id.contains("door")) {
            caps.add(NodeCapability.ARRAY);
        }

        if (id.startsWith("material.") || id.contains("assign_block") || id.contains("palette")) {
            caps.add(NodeCapability.MATERIAL);
        }

        if (id.startsWith("output.preview.")) {
            caps.add(NodeCapability.PREVIEW);
        }

        if (id.contains("sphere")) {
            caps.add(NodeCapability.SPHERE);
        }
        if (id.contains(".box") || id.endsWith("box") || id.contains("box_from")) {
            caps.add(NodeCapability.BOX);
        }
        if (id.contains("curve") || id.contains("path") || id.contains("spline")) {
            caps.add(NodeCapability.CURVE);
        }
        if (id.contains("terrain") || id.contains("heightmap")) {
            caps.add(NodeCapability.TERRAIN);
        }
        if (id.contains("sdf")) {
            caps.add(NodeCapability.SDF);
        }
        if (id.contains("field") || id.contains("scalar_from") || id.contains("vector_from")) {
            caps.add(NodeCapability.FIELD);
        }
        if (id.startsWith("geometry.voxel.") || id.contains("voxelize") || id.contains("surface_strip_to_blocks")) {
            caps.add(NodeCapability.VOXELIZE);
        }
        if (id.contains("sweep")) {
            caps.add(NodeCapability.SWEEP);
        }
        if (id.contains("extrude")) {
            caps.add(NodeCapability.EXTRUDE);
        }

        // WORLD_APPLY: effect-first. Namespace world.write only when effect is unspecified.
        applyEffectCaps(caps, effect);
        if ((effect == null || effect == NodeEffect.UNSPECIFIED) && id.startsWith("world.write.")) {
            caps.add(NodeCapability.WORLD_APPLY);
            caps.add(NodeCapability.APPLY);
        }

        return caps;
    }

    private static void applyEffectCaps(Set<NodeCapability> caps, NodeEffect effect) {
        if (effect == NodeEffect.WORLD_WRITE) {
            caps.add(NodeCapability.WORLD_APPLY);
            caps.add(NodeCapability.APPLY);
        }
        if (effect == NodeEffect.PREVIEW_WRITE) {
            caps.add(NodeCapability.PREVIEW);
        }
    }

    public static Set<NodeDomain> deriveDomains(String typeId, String category) {
        Set<NodeDomain> domains = EnumSet.noneOf(NodeDomain.class);
        String id = typeId == null ? "" : typeId.toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.toLowerCase(Locale.ROOT);

        if (cat.startsWith("geometry.architectural") || id.contains("architectural")
                || id.contains("wall") || id.contains("window") || id.contains("door")
                || id.contains("roof") || id.contains("stair") || id.contains("railing")) {
            domains.add(NodeDomain.ARCHITECTURE);
        }
        if (cat.contains("curve") || id.contains("curve") || id.contains("path") || id.contains("spline")) {
            domains.add(NodeDomain.CURVE);
        }
        if (cat.contains("profile") || id.contains("profile")) {
            domains.add(NodeDomain.PROFILE);
        }
        if (cat.contains("terrain") || id.contains("terrain") || id.contains("heightmap")) {
            domains.add(NodeDomain.TERRAIN);
        }
        if (cat.startsWith("material.") || id.startsWith("material.")) {
            domains.add(NodeDomain.MATERIAL);
        }
        if (id.contains("sdf") || cat.contains("sdf")) {
            domains.add(NodeDomain.SDF);
        }
        if (cat.contains("field") || id.contains("field") || id.contains("scalar_field")
                || id.contains("vector_field")) {
            domains.add(NodeDomain.FIELD);
        }
        if (cat.contains("array") || id.contains("array")) {
            domains.add(NodeDomain.ARRAY);
        }
        if (cat.startsWith("world.") || id.startsWith("world.") || cat.startsWith("output.execute.")) {
            domains.add(NodeDomain.WORLD);
        }
        if (cat.startsWith("math.") || id.startsWith("math.")) {
            domains.add(NodeDomain.MATH);
        }
        if (cat.contains("data_tree") || id.contains("data_tree")) {
            domains.add(NodeDomain.DATA_TREE);
        }
        if (cat.startsWith("geometry.") || id.startsWith("geometry.")) {
            domains.add(NodeDomain.GEOMETRY);
        }
        return domains;
    }
}
