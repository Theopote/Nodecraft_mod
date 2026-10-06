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
 * catches dead material branches, missing preview sinks, unused voxelize
 * outputs, Point Along Vector spans encoded in Direction magnitude
 * (Direction is always normalized; Distance is the actual move), and
 * selected geometric-scale contracts for showcase presets.</p>
 */
class PresetSemanticAuditTest {

    private static final Gson GSON = new Gson();
    private static final String RESOURCE_PATH = GraphPresetTestResources.BUILTIN_GRAPH_PRESETS;

    /** P0 Quickstart + Composites — must pass the full v2 teaching contract. */
    private static final Set<String> P0_CANONICAL_IDS = Set.of(
            "composite.textured_box",
            "composite.array_transform",
            "composite.boolean_cut",
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
        GraphPresetRules rules = loadRules(RESOURCE_PATH);
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
                RESOURCE_PATH + System.lineSeparator() + String.join(System.lineSeparator(), errors));
    }

    private static void auditCanonicalPresetIds(Set<String> presetIds) {
        GraphPresetRules rules = loadRules(RESOURCE_PATH);
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
                errors.add(RESOURCE_PATH + " missing canonical preset " + requiredId);
            }
        }

        assertTrue(
                errors.isEmpty(),
                RESOURCE_PATH + System.lineSeparator() + String.join(System.lineSeparator(), errors));
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
        errors.addAll(requireMaterialBlockTypeConnected(preset, typeByRef));
        errors.addAll(requirePointAlongVectorUsesDistanceAsSpan(preset, typeByRef));
        errors.addAll(requireStraightStaircasePathCoversRun(preset, typeByRef));
        errors.addAll(requireCanonicalGeometricScaleContracts(preset, typeByRef));
        errors.addAll(findUnconsumedNonSinkNodes(preset, typeByRef));
        errors.addAll(requireExplicitStateOnRepeatedPrimitives(preset, typeByRef));

        // Structural connectability still owned by GraphPresetResourceTest; re-check ports
        // here so semantic failures surface with the same resource load.
        errors.addAll(validatePortsExist(preset, typeByRef));
        return errors;
    }

    /**
     * Assign Block Type requires {@code input_block_type} at runtime; a connected
     * placements output alone does not make the material branch executable.
     */
    private static List<String> requireMaterialBlockTypeConnected(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (!MATERIAL_TYPE_IDS.contains(entry.getValue())) {
                continue;
            }
            if (!hasIncomingConnection(preset, entry.getKey(), "input_block_type")) {
                errors.add(preset.id + ": material node '" + entry.getKey()
                        + "' has no connection to required input_block_type");
            }
        }
        return errors;
    }

    /**
     * Point Along Vector always normalizes Direction, then moves by Distance.
     * Presets that encode the span in the Vector magnitude with Distance=1
     * collapse to a 1-unit path at runtime.
     */
    private static List<String> requirePointAlongVectorUsesDistanceAsSpan(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        if (preset.connections == null || preset.nodes == null) {
            return errors;
        }
        Map<String, GraphPresetRules.PresetNode> nodeByRef = new HashMap<>();
        for (GraphPresetRules.PresetNode node : preset.nodes) {
            if (node != null && node.ref != null) {
                nodeByRef.put(node.ref, node);
            }
        }
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (!"reference.points.point_along_vector".equals(entry.getValue())) {
                continue;
            }
            String moveRef = entry.getKey();
            String vectorRef = incomingFromRef(preset, moveRef, "input_vector");
            String distanceRef = incomingFromRef(preset, moveRef, "input_distance");
            if (vectorRef == null || distanceRef == null) {
                continue;
            }
            GraphPresetRules.PresetNode vectorNode = nodeByRef.get(vectorRef);
            GraphPresetRules.PresetNode distanceNode = nodeByRef.get(distanceRef);
            if (vectorNode == null || distanceNode == null) {
                continue;
            }
            if (!"reference.vectors.vector".equals(vectorNode.typeId)) {
                continue;
            }
            double magnitude = vectorStateMagnitude(vectorNode.state);
            Double distance = numericStateValue(distanceNode.state, "value");
            if (magnitude <= 0.0d || distance == null) {
                continue;
            }
            if (Math.abs(distance) <= 1.05d && magnitude > 1.25d) {
                errors.add(preset.id + ": Point Along Vector '" + moveRef
                        + "' uses Distance=" + distance
                        + " but Direction magnitude=" + magnitude
                        + " — direction is normalized, so the path span is Distance, not |vector|");
            }
        }
        return errors;
    }

    private static String incomingFromRef(
            GraphPresetRules.GraphPresetDefinition preset,
            String targetRef,
            String targetPort) {
        if (preset.connections == null) {
            return null;
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null) {
                continue;
            }
            if (targetRef.equals(connection.toRef) && targetPort.equals(connection.toPort)) {
                return connection.fromRef;
            }
        }
        return null;
    }

    private static double vectorStateMagnitude(Map<String, Object> state) {
        if (state == null) {
            return 0.0d;
        }
        double x = numericStateValue(state, "x") == null ? 0.0d : numericStateValue(state, "x");
        double y = numericStateValue(state, "y") == null ? 0.0d : numericStateValue(state, "y");
        double z = numericStateValue(state, "z") == null ? 0.0d : numericStateValue(state, "z");
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static Double numericStateValue(Map<String, Object> state, String key) {
        if (state == null) {
            return null;
        }
        Object value = state.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Map<String, GraphPresetRules.PresetNode> nodeByRefMap(
            GraphPresetRules.GraphPresetDefinition preset) {
        Map<String, GraphPresetRules.PresetNode> nodeByRef = new HashMap<>();
        if (preset.nodes == null) {
            return nodeByRef;
        }
        for (GraphPresetRules.PresetNode node : preset.nodes) {
            if (node != null && node.ref != null) {
                nodeByRef.put(node.ref, node);
            }
        }
        return nodeByRef;
    }

    /**
     * Straight staircase fails closed when path.length &lt; stepCount × stepRun + landing.
     * With Point Along Vector, path length equals Distance (direction is normalized).
     */
    private static List<String> requireStraightStaircasePathCoversRun(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        if (!"building_elements.stairs.straight_staircase".equals(preset.id)) {
            return errors;
        }
        Map<String, GraphPresetRules.PresetNode> nodeByRef = nodeByRefMap(preset);
        String stairRef = null;
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if ("geometry.architectural_primitives.staircase".equals(entry.getValue())) {
                stairRef = entry.getKey();
                break;
            }
        }
        if (stairRef == null) {
            return errors;
        }
        String stepCountRef = incomingFromRef(preset, stairRef, "input_step_count");
        String stepRunRef = incomingFromRef(preset, stairRef, "input_step_run");
        String pathRef = incomingFromRef(preset, stairRef, "input_path");
        if (stepCountRef == null || stepRunRef == null || pathRef == null) {
            return errors;
        }
        GraphPresetRules.PresetNode stepCountNode = nodeByRef.get(stepCountRef);
        GraphPresetRules.PresetNode stepRunNode = nodeByRef.get(stepRunRef);
        if (stepCountNode == null || stepRunNode == null) {
            return errors;
        }
        Double stepCount = numericStateValue(stepCountNode.state, "value");
        Double stepRun = numericStateValue(stepRunNode.state, "value");
        if (stepCount == null || stepRun == null) {
            return errors;
        }
        double required = stepCount * stepRun;
        // Sphere-by-diameter path: start → Point Along Vector end; length = Distance.
        String pathEndRef = null;
        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null) {
                    continue;
                }
                if (pathRef.equals(connection.toRef) && "input_end".equals(connection.toPort)) {
                    pathEndRef = connection.fromRef;
                    break;
                }
            }
        }
        if (pathEndRef == null || !"reference.points.point_along_vector".equals(typeByRef.get(pathEndRef))) {
            return errors;
        }
        String distanceRef = incomingFromRef(preset, pathEndRef, "input_distance");
        GraphPresetRules.PresetNode distanceNode = distanceRef == null ? null : nodeByRef.get(distanceRef);
        if (distanceNode == null) {
            return errors;
        }
        Double distance = numericStateValue(distanceNode.state, "value");
        if (distance == null) {
            return errors;
        }
        if (distance + 1.0e-6d < required) {
            errors.add(preset.id + ": Straight Staircase path Distance=" + distance
                    + " is shorter than required run stepCount×stepRun=" + required);
        }
        return errors;
    }

    /**
     * Showcase presets that previously collapsed / misaligned at runtime.
     * These are structural scale contracts, not a full bounding-box simulator.
     */
    private static List<String> requireCanonicalGeometricScaleContracts(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        Map<String, GraphPresetRules.PresetNode> nodeByRef = nodeByRefMap(preset);
        switch (preset.id) {
            case "architectural.infrastructure.stone_bridge" -> {
                GraphPresetRules.PresetNode elevation = nodeByRef.get("deck_elevation");
                GraphPresetRules.PresetNode bridgeBody = nodeByRef.get("bridge_body");
                if (elevation == null || bridgeBody == null) {
                    errors.add(preset.id + ": missing deck_elevation / bridge_body for deck-at-body-top scale");
                    break;
                }
                Double deckY = numericStateValue(elevation.state, "value");
                Double bodyH = numericStateValue(bridgeBody.state, "sizeY");
                if (deckY == null || bodyH == null || Math.abs(deckY - bodyH) > 0.05d) {
                    errors.add(preset.id + ": deck_elevation must match bridge_body sizeY (deck sits on body top)");
                }
                if (!"deck_elevation".equals(incomingFromRef(preset, "span_start", "input_y"))) {
                    errors.add(preset.id + ": span_start.input_y must come from deck_elevation");
                }
            }
            case "architectural.infrastructure.watchtower" -> {
                GraphPresetRules.PresetNode array = nodeByRef.get("battlement_array");
                GraphPresetRules.PresetNode box = nodeByRef.get("battlement_box");
                if (array == null || !"pattern.radial.polar_array".equals(array.typeId)) {
                    errors.add(preset.id + ": battlements must use polar_array on the roof rim"
                            + " (linear_array at ground origin misplaces merlons)");
                }
                Double cornerY = box == null ? null : numericStateValue(box.state, "cornerY");
                if (cornerY == null || cornerY < 10.0d) {
                    errors.add(preset.id + ": battlement_box.cornerY must sit near roof height (~14)");
                }
            }
            case "decorative.gazebo" -> {
                GraphPresetRules.PresetNode volume = nodeByRef.get("volume");
                Double cornerX = volume == null ? null : numericStateValue(volume.state, "cornerX");
                Double cornerZ = volume == null ? null : numericStateValue(volume.state, "cornerZ");
                if (cornerX == null || cornerZ == null || cornerX >= -0.1d || cornerZ >= -0.1d) {
                    errors.add(preset.id + ": volume must be centered (negative cornerX/Z)"
                            + " so polar columns at r=4 stay on the floor slab");
                }
            }
            case "styles.medieval.castle_keep" -> {
                GraphPresetRules.PresetNode keep = nodeByRef.get("keep_body");
                Double cornerX = keep == null ? null : numericStateValue(keep.state, "cornerX");
                Double cornerZ = keep == null ? null : numericStateValue(keep.state, "cornerZ");
                if (cornerX == null || cornerZ == null || cornerX < 0.5d || cornerZ < 0.5d) {
                    errors.add(preset.id + ": keep_body must be inset from footprint corner"
                            + " so Column Grid towers remain at outer corners");
                }
            }
            case "architectural.residential.simple_house",
                 "architectural.residential.medieval_cottage" -> {
                if (!typeByRef.containsValue("geometry.architectural_primitives.wall_along_path")) {
                    break;
                }
                GraphPresetRules.PresetNode wallHeight = nodeByRef.get("wall_height");
                GraphPresetRules.PresetNode volume = nodeByRef.get("volume");
                Double height = wallHeight == null ? null : numericStateValue(wallHeight.state, "value");
                Double sizeY = volume == null ? null : numericStateValue(volume.state, "sizeY");
                if (height == null || sizeY == null || Math.abs(height - sizeY) > 0.05d) {
                    errors.add(preset.id + ": wall_height must match volume.sizeY"
                            + " (Wall Along Path defaults to 3, shorter than the host volume)");
                }
                String wallsRef = null;
                for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
                    if ("geometry.architectural_primitives.wall_along_path".equals(entry.getValue())) {
                        wallsRef = entry.getKey();
                        break;
                    }
                }
                if (wallsRef != null && incomingFromRef(preset, wallsRef, "input_height") == null) {
                    errors.add(preset.id + ": Wall Along Path has no input_height connection");
                }
            }
            default -> {
            }
        }
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
