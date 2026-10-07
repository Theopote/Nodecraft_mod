package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
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
        errors.addAll(requirePreviewBlocksAndApplyShareSource(preset, typeByRef));
        errors.addAll(requireMiniBuildingFlagshipPath(preset, typeByRef));

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
        GraphPresetRules.PresetConnection connection = incomingConnection(preset, targetRef, targetPort);
        return connection == null ? null : connection.fromRef;
    }

    private static String incomingFromPort(
            GraphPresetRules.GraphPresetDefinition preset,
            String targetRef,
            String targetPort) {
        GraphPresetRules.PresetConnection connection = incomingConnection(preset, targetRef, targetPort);
        return connection == null ? null : connection.fromPort;
    }

    private static GraphPresetRules.PresetConnection incomingConnection(
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
                return connection;
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

    private static Boolean booleanStateValue(Map<String, Object> state, String key) {
        if (state == null) {
            return null;
        }
        Object value = state.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Every canonical preset that both previews and applies must share
     * {@code material.output_placements}, keep a single WORLD_WRITE Apply, and
     * preview design geometry after the Move that feeds Voxelize.
     */
    private static List<String> requirePreviewBlocksAndApplyShareSource(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        List<String> previewRefs = refsOfType(typeByRef, PREVIEW_BLOCKS);
        List<String> applyRefs = refsOfType(typeByRef, APPLY_CHANGES);
        if (previewRefs.isEmpty() || applyRefs.isEmpty()) {
            return errors;
        }
        if (applyRefs.size() != 1) {
            errors.add(preset.id + ": expected exactly one Apply Changes node, found " + applyRefs.size());
        }
        List<String> worldWrites = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (NodeEffectResolver.inferFromTypeId(entry.getValue()) == NodeEffect.WORLD_WRITE) {
                worldWrites.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
        }
        if (worldWrites.size() != 1) {
            errors.add(preset.id + ": expected exactly one WORLD_WRITE sink, found " + worldWrites);
        }

        String previewRef = previewRefs.getFirst();
        String applyRef = applyRefs.getFirst();
        String previewFrom = incomingFromRef(preset, previewRef, "input_block_placements");
        String previewPort = incomingFromPort(preset, previewRef, "input_block_placements");
        String applyFrom = incomingFromRef(preset, applyRef, "input_block_placements");
        String applyPort = incomingFromPort(preset, applyRef, "input_block_placements");
        if (previewFrom == null
                || applyFrom == null
                || !previewFrom.equals(applyFrom)
                || !MATERIAL_PLACEMENTS_PORT.equals(previewPort)
                || !MATERIAL_PLACEMENTS_PORT.equals(applyPort)) {
            errors.add(preset.id + ": Preview Blocks and Apply Changes must share "
                    + MATERIAL_PLACEMENTS_PORT + " from the same node");
        } else if (!MATERIAL_TYPE_IDS.contains(typeByRef.get(previewFrom))) {
            errors.add(preset.id + ": shared placements must come from Assign Block Type "
                    + MATERIAL_PLACEMENTS_PORT);
        }
        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null || !applyRef.equals(connection.toRef)) {
                    continue;
                }
                if ("input_trigger".equals(connection.toPort)
                        || "input_block_placements".equals(connection.toPort)) {
                    continue;
                }
                errors.add(preset.id + ": Apply Changes must only consume input_block_placements "
                        + "(plus optional EXEC input_trigger); extra wire to " + connection.toPort);
            }
        }
        GraphPresetRules.PresetNode applyNode = nodeByRefMap(preset).get(applyRef);
        if (!Boolean.TRUE.equals(booleanStateValue(applyNode == null ? null : applyNode.state, "recordUndo"))) {
            errors.add(preset.id + ": Apply Changes must freeze recordUndo=true");
        }

        String voxelizeRef = firstRefOfType(typeByRef, VOXELIZE_TYPE);
        String previewGeometryRef = firstRefOfType(typeByRef, PREVIEW_GEOMETRY);
        if (voxelizeRef != null && previewGeometryRef != null) {
            String moveRef = incomingFromRef(preset, voxelizeRef, "input_geometry");
            if (moveRef == null
                    || !"transform.basic_transforms.move_geometry".equals(typeByRef.get(moveRef))) {
                errors.add(preset.id + ": Voxelize geometry must come from the final Move");
            } else if (!moveRef.equals(incomingFromRef(preset, previewGeometryRef, "input_geometry"))) {
                errors.add(preset.id + ": Preview Geometry must use geometry after the final Move");
            }
        }
        return errors;
    }

    private static List<String> requireMiniBuildingFlagshipPath(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<String> errors = new ArrayList<>();
        if (!"architectural.residential.mini_building_v1".equals(preset.id)) {
            return errors;
        }
        if (!"flow.control.manual_trigger".equals(typeByRef.get("apply_trigger"))) {
            errors.add(preset.id + ": missing apply_trigger Manual Trigger node");
        }
        if (!"apply_trigger".equals(incomingFromRef(preset, "apply_changes", "input_trigger"))
                || !"output_exec".equals(incomingFromPort(preset, "apply_changes", "input_trigger"))) {
            errors.add(preset.id + ": apply_trigger.output_exec must drive apply_changes.input_trigger");
        }
        if (refsOfType(typeByRef, "geometry.combine.geometry").size() != 1
                || refsOfType(typeByRef, "transform.basic_transforms.move_geometry").size() != 1
                || refsOfType(typeByRef, VOXELIZE_TYPE).size() != 1) {
            errors.add(preset.id + ": Combine → Move → Voxelize must be the unique construction path");
        }
        if (!"combine".equals(incomingFromRef(preset, "move_to_pos", "input_geometry"))
                || !"move_to_pos".equals(incomingFromRef(preset, "voxelize", "input_geometry"))) {
            errors.add(preset.id + ": construction path must be combine → move_to_pos → voxelize");
        }
        return errors;
    }

    private static List<String> refsOfType(Map<String, String> typeByRef, String typeId) {
        List<String> refs = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (typeId.equals(entry.getValue())) {
                refs.add(entry.getKey());
            }
        }
        return refs;
    }

    private static String firstRefOfType(Map<String, String> typeByRef, String typeId) {
        List<String> refs = refsOfType(typeByRef, typeId);
        return refs.isEmpty() ? null : refs.getFirst();
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
        // Two-POINT → PATH via Sphere Diameter Path (create_list is LIST, not POINT_LIST).
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
                if (typeByRef.containsValue("geometry.architectural_primitives.roof_base")) {
                    errors.add(preset.id + ": shed/gable roof_base conflicts with flat battlement parapet");
                }
                if (typeByRef.containsValue("geometry.architectural_primitives.railing")) {
                    errors.add(preset.id + ": eave railing duplicates battlement parapet role");
                }
                if (!typeByRef.containsKey("tower_cut")) {
                    errors.add(preset.id + ": ground door requires tower_cut difference (hollow − arch opening)");
                }
                if (!typeByRef.containsKey("top_deck")) {
                    errors.add(preset.id + ": flat parapet watchtower requires top_deck slab");
                }
                Double cornerY = box == null ? null : numericStateValue(box.state, "cornerY");
                if (cornerY == null || cornerY < 14.0d) {
                    errors.add(preset.id + ": battlement_box.cornerY must sit on top deck (~14)");
                }
                if (!"battlement_count".equals(incomingFromRef(preset, "battlement_array", "input_count"))) {
                    errors.add(preset.id + ": battlement_count must drive polar_array input_count");
                }
            }
            case "decorative.fountain_circular" -> {
                GraphPresetRules.PresetNode outer = nodeByRef.get("outer_basin");
                GraphPresetRules.PresetNode inner = nodeByRef.get("inner_hollow");
                GraphPresetRules.PresetNode tier = nodeByRef.get("inner_tier");
                GraphPresetRules.PresetNode spout = nodeByRef.get("center_spout");
                Double outerY = outer == null ? null : numericStateValue(outer.state, "startY");
                Double innerY = inner == null ? null : numericStateValue(inner.state, "startY");
                Double tierY = tier == null ? null : numericStateValue(tier.state, "startY");
                Double spoutY = spout == null ? null : numericStateValue(spout.state, "startY");
                if (outerY == null || innerY == null || !(innerY > outerY)) {
                    errors.add(preset.id + ": inner_hollow.startY must be > outer_basin.startY"
                            + " (retain basin floor)");
                }
                if (innerY == null || tierY == null || spoutY == null
                        || Math.abs(tierY - innerY) > 1.0e-6d
                        || Math.abs(spoutY - innerY) > 1.0e-6d) {
                    errors.add(preset.id + ": inner_tier and center_spout must start at"
                            + " inner_hollow.startY (seat on basin floor)");
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
                if (!"pavilion_height".equals(incomingFromRef(preset, "volume", "input_size_y"))) {
                    errors.add(preset.id + ": pavilion_height must drive volume.input_size_y");
                }
                if (!"pavilion_height".equals(incomingFromRef(preset, "column_end", "input_y"))) {
                    errors.add(preset.id + ": pavilion_height must drive column_end.input_y");
                }
                GraphPresetRules.PresetNode roofType = nodeByRef.get("roof_type");
                if (roofType == null || !"input.values.dropdown".equals(roofType.typeId)) {
                    errors.add(preset.id + ": roof_type must be input.values.dropdown");
                }
                if (!"roof_type".equals(incomingFromRef(preset, "roof", "input_roof_type"))) {
                    errors.add(preset.id + ": roof_type must drive roof.input_roof_type");
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
            case "architectural.residential.medieval_cottage" -> {
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
                if (!"opening_depth".equals(incomingFromRef(preset, "windows", "input_depth"))) {
                    errors.add(preset.id + ": opening_depth must drive windows.input_depth");
                }
                if (!"opening_depth".equals(incomingFromRef(preset, "doors", "input_depth"))) {
                    errors.add(preset.id + ": opening_depth must drive doors.input_depth");
                }
                if (wallsRef != null && !"wall_thickness".equals(incomingFromRef(preset, wallsRef, "input_thickness"))) {
                    errors.add(preset.id + ": wall_thickness must drive walls.input_thickness");
                }
                GraphPresetRules.PresetNode openingDepth = nodeByRef.get("opening_depth");
                GraphPresetRules.PresetNode wallThickness = nodeByRef.get("wall_thickness");
                Double depth = openingDepth == null ? null : numericStateValue(openingDepth.state, "value");
                Double thickness = wallThickness == null ? null : numericStateValue(wallThickness.state, "value");
                if (depth == null || thickness == null || depth < thickness) {
                    errors.add(preset.id + ": opening_depth must be >= wall_thickness for through-wall cutters");
                }
                if (incomingFromRef(preset, "roof", "input_overhang") == null) {
                    errors.add(preset.id + ": gable roof must expose input_overhang for cottage eaves");
                }
            }
            case "architectural.residential.simple_house" -> {
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
            case "building_elements.doors.simple_door" -> {
                GraphPresetRules.PresetNode outer = nodeByRef.get("outer_frame");
                GraphPresetRules.PresetNode inner = nodeByRef.get("inner_opening");
                if (outer == null || inner == null) {
                    errors.add(preset.id + ": missing outer_frame / inner_opening for open-bottom door");
                    break;
                }
                Double outerY = numericStateValue(outer.state, "cornerY");
                Double outerH = numericStateValue(outer.state, "sizeY");
                Double innerY = numericStateValue(inner.state, "cornerY");
                Double innerH = numericStateValue(inner.state, "sizeY");
                if (outerY == null || outerH == null || innerY == null || innerH == null) {
                    errors.add(preset.id + ": outer/inner must declare cornerY and sizeY");
                    break;
                }
                if (!(innerY < outerY)) {
                    errors.add(preset.id + ": inner.cornerY must be below outer.cornerY"
                            + " (Y-through cutter opens the sill)");
                }
                if (!(innerY + innerH < outerY + outerH)) {
                    errors.add(preset.id + ": inner top must stay below outer top"
                            + " (head band / lintel must remain)");
                }
            }
            case "building_elements.roofs.gable_roof" -> {
                GraphPresetRules.PresetNode volume = nodeByRef.get("volume");
                Double sizeX = volume == null ? null : numericStateValue(volume.state, "sizeX");
                Double sizeY = volume == null ? null : numericStateValue(volume.state, "sizeY");
                Double sizeZ = volume == null ? null : numericStateValue(volume.state, "sizeZ");
                if (sizeX == null || Math.abs(sizeX - 10.0d) > 0.05d
                        || sizeY == null || Math.abs(sizeY - 4.0d) > 0.05d
                        || sizeZ == null || Math.abs(sizeZ - 8.0d) > 0.05d) {
                    errors.add(preset.id + ": volume.state must be sizeX=10, sizeY=4, sizeZ=8"
                            + " (building footprint; roof sits on wall top ~playerY+4)");
                }
                if (!"roof_type".equals(incomingFromRef(preset, "roof", "input_roof_type"))) {
                    errors.add(preset.id + ": roof_type must drive roof.input_roof_type");
                }
                if (!"ridge_direction".equals(incomingFromRef(preset, "roof", "input_ridge_direction"))) {
                    errors.add(preset.id + ": ridge_direction must drive roof.input_ridge_direction");
                }
                if (!"overhang".equals(incomingFromRef(preset, "roof", "input_overhang"))) {
                    errors.add(preset.id + ": overhang must drive roof.input_overhang");
                }
                if (!"roof_height".equals(incomingFromRef(preset, "roof", "input_height"))) {
                    errors.add(preset.id + ": roof_height must drive roof.input_height");
                }
            }
            case "building_elements.columns.classical_column" -> {
                if (!"local_origin".equals(incomingFromRef(preset, "base", "input_frame"))) {
                    errors.add(preset.id + ": local_origin.output_frame must drive base.input_frame");
                }
                if (!"base_shape".equals(incomingFromRef(preset, "base", "input_shape"))
                        || !"shaft_shape".equals(incomingFromRef(preset, "shaft", "input_shape"))
                        || !"capital_shape".equals(incomingFromRef(preset, "capital", "input_shape"))) {
                    errors.add(preset.id + ": shape dropdowns must drive Column input_shape ports");
                }
                for (String shapeRef : List.of("base_shape", "shaft_shape", "capital_shape")) {
                    GraphPresetRules.PresetNode shape = nodeByRef.get(shapeRef);
                    if (shape == null || !"input.values.dropdown".equals(shape.typeId)) {
                        errors.add(preset.id + ": " + shapeRef + " must be input.values.dropdown");
                    }
                }
            }
            case "building_elements.stairs.straight_staircase" -> {
                GraphPresetRules.PresetNode runVector = nodeByRef.get("run_vector");
                Double runY = runVector == null ? null : numericStateValue(runVector.state, "y");
                if (runY == null || Math.abs(runY) > 1.0e-6d) {
                    errors.add(preset.id + ": run_vector.y must be 0 (horizontal plan path;"
                            + " Step Rise owns elevation)");
                }
            }
            case "building_elements.stairs.spiral_staircase" -> {
                if (!"spiral_height".equals(incomingFromRef(preset, "staircase", "input_spiral_height"))
                        || !"spiral_height".equals(incomingFromRef(preset, "post_end", "input_distance"))) {
                    errors.add(preset.id + ": spiral_height must drive staircase and center-post end");
                }
                if (!"spiral_core_radius".equals(incomingFromRef(preset, "staircase", "input_spiral_core_radius"))
                        || !"spiral_core_radius".equals(incomingFromRef(preset, "center_post", "input_radius"))) {
                    errors.add(preset.id + ": spiral_core_radius must drive staircase and center_post.radius");
                }
                if (!"post_end".equals(incomingFromRef(preset, "center_post", "input_end"))) {
                    errors.add(preset.id + ": center_post.input_end must come from post_end");
                }
            }
            case "building_elements.windows.arched_window" -> {
                GraphPresetRules.PresetNode plane = nodeByRef.get("facade_plane");
                Object presetName = plane == null || plane.state == null ? null : plane.state.get("planePreset");
                if (!"XY".equals(presetName)) {
                    errors.add(preset.id + ": facade_plane must be world_plane XY");
                }
                if (!"facade_plane".equals(incomingFromRef(preset, "rect_profile", "input_plane"))
                        || !"facade_plane".equals(incomingFromRef(preset, "arc_profile", "input_plane"))) {
                    errors.add(preset.id + ": rect/arc profiles must use facade_plane");
                }
                GraphPresetRules.PresetNode rectCy = nodeByRef.get("rect_cy");
                GraphPresetRules.PresetNode arcCy = nodeByRef.get("arc_cy");
                Double rectY = rectCy == null ? null : numericStateValue(rectCy.state, "value");
                Double arcY = arcCy == null ? null : numericStateValue(arcCy.state, "value");
                if (rectY == null || arcY == null || !(arcY > rectY)) {
                    errors.add(preset.id + ": arc center Y must be above rect center Y (arch on rect top)");
                }
                if (!typeByRef.containsKey("material_frame") || !typeByRef.containsKey("material_glass")
                        || !typeByRef.containsKey("merge_placements")) {
                    errors.add(preset.id + ": dual frame/glass materials must merge into Preview Blocks");
                }
            }
            case "building_elements.windows.modern_window" -> {
                if (!"geometry.architectural_primitives.wall_slab".equals(typeByRef.get("wall"))) {
                    errors.add(preset.id + ": host must be wall_slab (not wall_with_openings)");
                }
                if (!"opening_depth".equals(incomingFromRef(preset, "windows", "input_depth"))) {
                    errors.add(preset.id + ": opening_depth must drive windows.input_depth");
                }
                GraphPresetRules.PresetNode depthFactor = nodeByRef.get("depth_factor");
                Double factor = depthFactor == null ? null : numericStateValue(depthFactor.state, "value");
                if (factor == null || factor + 1.0e-6d < 2.0d) {
                    errors.add(preset.id + ": opening_depth must be wall_thickness × ≥2 for through cutters");
                }
                if (!typeByRef.containsKey("glass_pane") || !typeByRef.containsKey("place_glass")) {
                    errors.add(preset.id + ": must place a glass_pane on window frames");
                }
                if (!typeByRef.containsKey("material_wall") || !typeByRef.containsKey("material_frame")
                        || !typeByRef.containsKey("material_glass")
                        || !typeByRef.containsKey("merge_placements")) {
                    errors.add(preset.id + ": wall/frame/glass materials must merge into Preview Blocks");
                }
                if (!"merge_placements".equals(incomingFromRef(preset, "preview_blocks", "input_block_placements"))) {
                    errors.add(preset.id + ": Preview Blocks must consume merge_placements");
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
