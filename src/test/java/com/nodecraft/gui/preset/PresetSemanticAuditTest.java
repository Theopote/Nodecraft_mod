package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preset Library v2 semantic contracts for canonical teaching presets.
 *
 * <p>Static port/type checks live in {@link GraphPresetResourceTest}. This class
 * catches dead material branches, missing preview sinks, and unused voxelize
 * outputs that those structural tests still accept.</p>
 */
class PresetSemanticAuditTest {

    private static final Gson GSON = new Gson();
    private static final List<String> RESOURCE_PATHS = List.of(
            "/nodecraft/graph_presets.json",
            "/nodecraft/graph_presets_updated.json");

    /** P0 Quickstart + Composites — must pass the full v2 teaching contract. */
    private static final Set<String> P0_CANONICAL_IDS = Set.of(
            "composite.textured_box",
            "composite.array_transform_deform",
            "composite.boolean_cut_bake",
            "quickstart.basic_box",
            "quickstart.basic_sphere",
            "quickstart.garden_wall",
            "quickstart.simple_tower");

    /** P1 architectural workflow + building elements promoted to v2 block chain. */
    private static final Set<String> P1_CANONICAL_IDS = Set.of(
            "architectural.residential.mini_building_v1",
            "building_elements.roofs.gable_roof",
            "building_elements.stairs.straight_staircase");

    /** P2 building element components — explicit params + v2 preview/material chain. */
    private static final Set<String> P2_CANONICAL_IDS = Set.of(
            "building_elements.columns.classical_column",
            "building_elements.doors.simple_door",
            "building_elements.windows.modern_window",
            "building_elements.windows.arched_window",
            "building_elements.stairs.spiral_staircase");

    /** P3 showcase / style presets — architectural chains + dual-material merge where needed. */
    private static final Set<String> P3_CANONICAL_IDS = Set.of(
            "architectural.residential.medieval_cottage",
            "architectural.residential.simple_house",
            "architectural.infrastructure.stone_bridge",
            "architectural.infrastructure.watchtower",
            "decorative.fountain_circular",
            "decorative.gazebo",
            "styles.fantasy.wizard_tower",
            "styles.medieval.castle_keep",
            "styles.modern.glass_box_building");

    private static final Set<String> CANONICAL_IDS;
    static {
        Set<String> all = new LinkedHashSet<>(P0_CANONICAL_IDS);
        all.addAll(P1_CANONICAL_IDS);
        all.addAll(P2_CANONICAL_IDS);
        all.addAll(P3_CANONICAL_IDS);
        CANONICAL_IDS = Set.copyOf(all);
    }

    private static final Set<String> BUILD_WITH_APPLY_IDS = Set.of(
            "architectural.residential.mini_building_v1");

    private static final String APPLY_CHANGES = "output.execute.apply_changes";

    private static final Set<String> SINK_TYPE_IDS = Set.of(
            "output.preview.preview_blocks",
            "output.preview.preview_geometry",
            "output.preview.geometry_viewer",
            "output.execute.apply_changes");

    private static final Set<String> MATERIAL_TYPE_IDS = Set.of(
            "material.basic_assignment.assign_block_type");

    private static final String VOXELIZE_TYPE = "geometry.voxel.voxelize_geometry";
    private static final String PREVIEW_BLOCKS = "output.preview.preview_blocks";
    private static final String PREVIEW_GEOMETRY = "output.preview.preview_geometry";
    private static final String MATERIAL_PLACEMENTS_PORT = "output_placements";

    @BeforeAll
    static void initializeRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void p0CanonicalPresetsPassSemanticContract() {
        auditCanonicalPresetIds(P0_CANONICAL_IDS);
    }

    @Test
    void p1CanonicalPresetsPassSemanticContract() {
        auditCanonicalPresetIds(P1_CANONICAL_IDS);
    }

    @Test
    void p2CanonicalPresetsPassSemanticContract() {
        auditCanonicalPresetIds(P2_CANONICAL_IDS);
    }

    @Test
    void p3CanonicalPresetsPassSemanticContract() {
        auditCanonicalPresetIds(P3_CANONICAL_IDS);
    }

    @Test
    void allCompositePresetsRejectDeadMaterialOutputs() {
        for (String resourcePath : RESOURCE_PATHS) {
            GraphPresetRules rules = loadRules(resourcePath);
            List<String> errors = new ArrayList<>();

            for (GraphPresetRules.PresetCategory category : rules.categories) {
                if (category == null || category.presets == null) {
                    continue;
                }
                for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                    if (preset == null || !"composite".equalsIgnoreCase(preset.kind)) {
                        continue;
                    }
                    if (!CANONICAL_IDS.contains(preset.id)) {
                        continue;
                    }
                    errors.addAll(findDeadMaterialBranches(preset));
                }
            }

            assertTrue(
                    errors.isEmpty(),
                    resourcePath + System.lineSeparator() + String.join(System.lineSeparator(), errors));
        }
    }

    @Test
    void graphPresetsJsonMatchesUpdatedCopyForCanonicalPresets() {
        GraphPresetRules primary = loadRules("/nodecraft/graph_presets.json");
        GraphPresetRules updated = loadRules("/nodecraft/graph_presets_updated.json");
        for (String presetId : CANONICAL_IDS) {
            GraphPresetRules.GraphPresetDefinition a = findPreset(primary, presetId);
            GraphPresetRules.GraphPresetDefinition b = findPreset(updated, presetId);
            assertNotNull(a, "primary missing " + presetId);
            assertNotNull(b, "updated missing " + presetId);
            assertTrue(
                    GSON.toJson(a).equals(GSON.toJson(b)),
                    "Canonical preset diverged between graph_presets.json and graph_presets_updated.json: "
                            + presetId);
        }
    }

    private static void auditCanonicalPresetIds(Set<String> presetIds) {
        for (String resourcePath : RESOURCE_PATHS) {
            GraphPresetRules rules = loadRules(resourcePath);
            List<String> errors = new ArrayList<>();
            Set<String> found = new HashSet<>();

            for (GraphPresetRules.PresetCategory category : rules.categories) {
                if (category == null || category.presets == null) {
                    continue;
                }
                for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                    if (preset == null || !presetIds.contains(preset.id)) {
                        continue;
                    }
                    found.add(preset.id);
                    errors.addAll(auditCanonicalPreset(preset));
                }
            }

            for (String requiredId : presetIds) {
                if (!found.contains(requiredId)) {
                    errors.add(resourcePath + " missing canonical preset " + requiredId);
                }
            }

            assertTrue(
                    errors.isEmpty(),
                    resourcePath + System.lineSeparator() + String.join(System.lineSeparator(), errors));
        }
    }

    private static List<String> auditCanonicalPreset(GraphPresetRules.GraphPresetDefinition preset) {
        List<String> errors = new ArrayList<>();
        Map<String, String> typeByRef = typeByRef(preset);
        Set<String> types = new LinkedHashSet<>(typeByRef.values());

        if (!types.contains(PREVIEW_GEOMETRY)) {
            errors.add(preset.id + ": missing Preview Geometry sink");
        }
        if (!types.contains(PREVIEW_BLOCKS)) {
            errors.add(preset.id + ": missing Preview Blocks sink");
        }
        if (BUILD_WITH_APPLY_IDS.contains(preset.id) && !types.contains(APPLY_CHANGES)) {
            errors.add(preset.id + ": missing Apply Changes sink for build workflow");
        }
        if (BUILD_WITH_APPLY_IDS.contains(preset.id)) {
            errors.addAll(requireApplyChangesConsumesPlacements(preset, typeByRef));
        }

        boolean hasMaterial = types.stream().anyMatch(MATERIAL_TYPE_IDS::contains);
        boolean hasVoxelize = types.contains(VOXELIZE_TYPE);
        if (hasMaterial && !hasVoxelize) {
            // Material may accept Geometry directly; still require placement sink.
            errors.addAll(requireMaterialPlacementsConsumed(preset, typeByRef));
        }
        if (hasMaterial && hasVoxelize) {
            errors.addAll(requireMaterialPlacementsConsumed(preset, typeByRef));
            errors.addAll(requireVoxelizeFeedsMaterialOrPreview(preset, typeByRef));
        }
        if (hasVoxelize && !hasMaterial) {
            errors.addAll(requireVoxelizeConsumed(preset, typeByRef));
        }

        errors.addAll(findDeadMaterialBranches(preset));
        errors.addAll(findUnconsumedNonSinkNodes(preset, typeByRef));
        errors.addAll(requireExplicitStateOnRepeatedPrimitives(preset, typeByRef));

        // Structural connectability still owned by GraphPresetResourceTest; re-check ports
        // here so semantic failures surface with the same resource load.
        errors.addAll(validatePortsExist(preset, typeByRef));
        return errors;
    }

    private static List<String> requireApplyChangesConsumesPlacements(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (!APPLY_CHANGES.equals(entry.getValue())) {
                continue;
            }
            boolean placementsUsed = hasIncomingConnection(
                    preset,
                    entry.getKey(),
                    "input_block_placements",
                    "input_block_placements_tree");
            if (!placementsUsed) {
                errors.add(preset.id + ": Apply Changes node '" + entry.getKey()
                        + "' is not fed block placements from the material chain");
            }
        }
        return errors;
    }

    private static boolean hasIncomingConnection(
            GraphPresetRules.GraphPresetDefinition preset,
            String targetRef,
            String... targetPorts) {
        if (preset.connections == null) {
            return false;
        }
        Set<String> allowedPorts = Set.of(targetPorts);
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null) {
                continue;
            }
            if (targetRef.equals(connection.toRef) && allowedPorts.contains(connection.toPort)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> findDeadMaterialBranches(GraphPresetRules.GraphPresetDefinition preset) {
        List<String> errors = new ArrayList<>();
        Map<String, String> typeByRef = typeByRef(preset);
        Set<String> consumedOutputs = consumedOutputKeys(preset);

        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (!MATERIAL_TYPE_IDS.contains(entry.getValue())) {
                continue;
            }
            String placementKey = entry.getKey() + "." + MATERIAL_PLACEMENTS_PORT;
            if (!consumedOutputs.contains(placementKey)) {
                errors.add(preset.id + ": material node '" + entry.getKey()
                        + "' contributes no output_placements to any preview/build sink");
            }
        }
        return errors;
    }

    private static List<String> requireMaterialPlacementsConsumed(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        return findDeadMaterialBranches(preset);
    }

    private static List<String> requireVoxelizeFeedsMaterialOrPreview(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        Set<String> consumed = consumedOutputKeys(preset);
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (!VOXELIZE_TYPE.equals(entry.getValue())) {
                continue;
            }
            boolean blocksUsed = consumed.contains(entry.getKey() + ".output_blocks")
                    || consumed.contains(entry.getKey() + ".output_blocks_tree");
            if (!blocksUsed) {
                errors.add(preset.id + ": voxelize '" + entry.getKey()
                        + "' output_blocks is not consumed");
            }
        }
        return errors;
    }

    private static List<String> requireVoxelizeConsumed(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        return requireVoxelizeFeedsMaterialOrPreview(preset, typeByRef);
    }

    private static List<String> findUnconsumedNonSinkNodes(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        Set<String> refsWithOutgoing = new HashSet<>();
        Set<String> refsWithIncoming = new HashSet<>();
        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null) {
                    continue;
                }
                refsWithOutgoing.add(connection.fromRef);
                refsWithIncoming.add(connection.toRef);
            }
        }

        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            String ref = entry.getKey();
            String typeId = entry.getValue();
            if (SINK_TYPE_IDS.contains(typeId)) {
                continue;
            }
            // Input-only context nodes are allowed as pure sources.
            if (typeId.startsWith("input.")) {
                continue;
            }
            boolean hasIncoming = refsWithIncoming.contains(ref);
            boolean hasOutgoing = refsWithOutgoing.contains(ref);
            if (hasIncoming && !hasOutgoing) {
                errors.add(preset.id + ": dead branch — node '" + ref
                        + "' (" + typeId + ") has inputs but no consumed outputs");
            }
            if (!hasIncoming && !hasOutgoing && typeByRef.size() > 1) {
                errors.add(preset.id + ": orphan node '" + ref + "' (" + typeId + ")");
            }
        }
        return errors;
    }

    private static List<String> requireExplicitStateOnRepeatedPrimitives(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        Map<String, List<GraphPresetRules.PresetNode>> byType = new HashMap<>();
        if (preset.nodes == null) {
            return errors;
        }
        for (GraphPresetRules.PresetNode node : preset.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (!isPrimitiveGeometryType(node.typeId)) {
                continue;
            }
            byType.computeIfAbsent(node.typeId, ignored -> new ArrayList<>()).add(node);
        }
        for (Map.Entry<String, List<GraphPresetRules.PresetNode>> entry : byType.entrySet()) {
            List<GraphPresetRules.PresetNode> nodes = entry.getValue();
            if (nodes.size() < 2) {
                continue;
            }
            long withoutState = nodes.stream()
                    .filter(n -> n.state == null || n.state.isEmpty())
                    .count();
            if (withoutState == nodes.size()) {
                errors.add(preset.id + ": " + nodes.size() + " repeated "
                        + entry.getKey() + " nodes use identical default state=null "
                        + "(possible overlapping geometry)");
            }
        }
        return errors;
    }

    private static boolean isPrimitiveGeometryType(String typeId) {
        return typeId != null && typeId.startsWith("geometry.primitives.");
    }

    private static List<String> validatePortsExist(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        NodeRegistry registry = NodeRegistry.getInstance();
        Map<String, INode> cache = new LinkedHashMap<>();
        if (preset.connections == null) {
            return errors;
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = typeByRef.get(connection.fromRef);
            String targetType = typeByRef.get(connection.toRef);
            if (sourceType == null || targetType == null) {
                errors.add(preset.id + ": unknown connection refs "
                        + connection.fromRef + " -> " + connection.toRef);
                continue;
            }
            INode source = cache.computeIfAbsent(sourceType, registry::createNodeInstance);
            INode target = cache.computeIfAbsent(targetType, registry::createNodeInstance);
            assertNotNull(source, "missing node type " + sourceType);
            assertNotNull(target, "missing node type " + targetType);
            IPort out = findPort(source.getOutputPorts(), connection.fromPort);
            IPort in = findPort(target.getInputPorts(), connection.toPort);
            if (out == null) {
                errors.add(preset.id + ": missing output " + sourceType + "." + connection.fromPort);
                continue;
            }
            if (in == null) {
                errors.add(preset.id + ": missing input " + targetType + "." + connection.toPort);
                continue;
            }
            if (!NodeDataType.isConnectableTo(out.getDataType(), in.getDataType())) {
                errors.add(preset.id + ": incompatible "
                        + sourceType + "." + connection.fromPort + " -> "
                        + targetType + "." + connection.toPort);
            }
        }
        return errors;
    }

    private static Set<String> consumedOutputKeys(GraphPresetRules.GraphPresetDefinition preset) {
        Set<String> keys = new HashSet<>();
        if (preset.connections == null) {
            return keys;
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null || connection.fromRef == null || connection.fromPort == null) {
                continue;
            }
            keys.add(connection.fromRef + "." + connection.fromPort);
        }
        return keys;
    }

    private static Map<String, String> typeByRef(GraphPresetRules.GraphPresetDefinition preset) {
        Map<String, String> map = new LinkedHashMap<>();
        if (preset.nodes == null) {
            return map;
        }
        for (GraphPresetRules.PresetNode node : preset.nodes) {
            if (node != null && node.ref != null && node.typeId != null) {
                map.put(node.ref, node.typeId);
            }
        }
        return map;
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
        try (InputStream stream = PresetSemanticAuditTest.class.getResourceAsStream(resourcePath)) {
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

    private static IPort findPort(List<IPort> ports, String portId) {
        if (portId == null) {
            return null;
        }
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}
