package com.nodecraft.gui.preset;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Layer A static coordinate-space heuristics for built-in graph presets.
 *
 * <p>Detects topology-level risks that {@link PresetSemanticAuditTest} cannot see:
 * double translation (world placement + final move) and split preview/material geometry chains.</p>
 */
final class PresetCoordinateSpaceAuditor {

    enum ViolationKind {
        DOUBLE_TRANSLATION,
        SPLIT_PREVIEW_CHAIN,
        WORLD_SPACE_WITHOUT_MOVE
    }

    record Violation(ViolationKind kind, String detail) {
        String code() {
            return kind.name();
        }
    }

    private static final String PLAYER_POSITION_TYPE = "input.context.player_position";
    private static final String MOVE_GEOMETRY_TYPE = "transform.basic_transforms.move_geometry";
    private static final String PREVIEW_GEOMETRY_TYPE = "output.preview.preview_geometry";
    private static final String VOXELIZE_TYPE = "geometry.voxel.voxelize_geometry";
    private static final String CREATE_LIST_TYPE = "math.list.create_list";

    private static final String PLAYER_OUTPUT_PORT = "output_position";
    private static final String MOVE_TRANSLATION_PORT = "input_translation";

    private static final Set<String> WORLD_PLACEMENT_PORTS = Set.of(
            "input_base",
            "input_start",
            "input_end",
            "input_center",
            "input_point",
            "input_origin");

    private static final Pattern LIST_INPUT_PORT = Pattern.compile("input_\\d+");

    private PresetCoordinateSpaceAuditor() {
    }

    static List<Violation> audit(GraphPresetRules.GraphPresetDefinition preset) {
        List<Violation> violations = new ArrayList<>();
        if (preset == null) {
            return violations;
        }
        Map<String, String> typeByRef = typeByRef(preset);
        Map<String, List<GraphPresetRules.PresetConnection>> incoming = incomingByTarget(preset);

        violations.addAll(findDoubleTranslation(preset, typeByRef));
        violations.addAll(findSplitPreviewChain(preset, typeByRef, incoming));
        return violations;
    }

    static Set<String> violationCodes(GraphPresetRules.GraphPresetDefinition preset) {
        Set<String> codes = new LinkedHashSet<>();
        for (Violation violation : audit(preset)) {
            codes.add(violation.code());
        }
        return codes;
    }

    private static List<Violation> findDoubleTranslation(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<Violation> violations = new ArrayList<>();
        Set<String> playerRefs = refsOfType(typeByRef, PLAYER_POSITION_TYPE);
        if (playerRefs.isEmpty()) {
            return violations;
        }

        boolean usesFinalMove = false;
        List<String> placementLinks = new ArrayList<>();

        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null || connection.fromRef == null || connection.toRef == null) {
                    continue;
                }
                if (!playerRefs.contains(connection.fromRef)) {
                    continue;
                }
                if (!PLAYER_OUTPUT_PORT.equals(connection.fromPort)) {
                    continue;
                }

                String targetType = typeByRef.get(connection.toRef);
                if (MOVE_GEOMETRY_TYPE.equals(targetType)
                        && MOVE_TRANSLATION_PORT.equals(connection.toPort)) {
                    usesFinalMove = true;
                    continue;
                }

                if (isWorldPlacementPort(connection.toPort, targetType)) {
                    placementLinks.add(connection.toRef + "." + connection.toPort);
                }
            }
        }

        if (usesFinalMove && !placementLinks.isEmpty()) {
            violations.add(new Violation(
                    ViolationKind.DOUBLE_TRANSLATION,
                    "Player Position anchors geometry at "
                            + String.join(", ", placementLinks)
                            + " and also drives Move Geometry.input_translation"));
        }
        return violations;
    }

    private static List<Violation> findSplitPreviewChain(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef,
            Map<String, List<GraphPresetRules.PresetConnection>> incoming) {
        List<Violation> violations = new ArrayList<>();
        List<String> previewRefs = refsOfTypeInOrder(typeByRef, PREVIEW_GEOMETRY_TYPE);
        List<String> voxelRefs = refsOfTypeInOrder(typeByRef, VOXELIZE_TYPE);
        if (previewRefs.isEmpty() || voxelRefs.isEmpty()) {
            return violations;
        }

        Set<String> playerAnchoredMoves = playerAnchoredMoveRefs(preset, typeByRef);

        Set<String> previewMoveAnchors = new LinkedHashSet<>();
        for (String previewRef : previewRefs) {
            previewMoveAnchors.addAll(moveAnchorsInUpstream(previewRef, incoming, typeByRef));
        }

        Set<String> voxelMoveAnchors = new LinkedHashSet<>();
        for (String voxelRef : voxelRefs) {
            voxelMoveAnchors.addAll(moveAnchorsInUpstream(voxelRef, incoming, typeByRef));
        }

        boolean previewUsesPlayerMove = usesPlayerAnchoredMove(previewMoveAnchors, playerAnchoredMoves);
        boolean allVoxelsUsePlayerMove = voxelRefs.stream().allMatch(voxelRef -> {
            Set<String> moves = moveAnchorsInUpstream(voxelRef, incoming, typeByRef);
            return usesPlayerAnchoredMove(moves, playerAnchoredMoves);
        });

        if (previewUsesPlayerMove == allVoxelsUsePlayerMove) {
            return violations;
        }

        violations.add(new Violation(
                ViolationKind.SPLIT_PREVIEW_CHAIN,
                "Preview Geometry player-anchored move="
                        + previewUsesPlayerMove
                        + " ("
                        + formatMoveAnchors(previewMoveAnchors, typeByRef)
                        + ") but Voxelize player-anchored move="
                        + allVoxelsUsePlayerMove
                        + " ("
                        + formatMoveAnchors(voxelMoveAnchors, typeByRef)
                        + ") — geometry preview and block preview may not coincide"));
        return violations;
    }

    private static Set<String> playerAnchoredMoveRefs(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        Set<String> playerRefs = refsOfType(typeByRef, PLAYER_POSITION_TYPE);
        Set<String> moves = new LinkedHashSet<>();
        if (preset.connections == null || playerRefs.isEmpty()) {
            return moves;
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null || connection.fromRef == null || connection.toRef == null) {
                continue;
            }
            if (!playerRefs.contains(connection.fromRef)) {
                continue;
            }
            if (!PLAYER_OUTPUT_PORT.equals(connection.fromPort)) {
                continue;
            }
            if (!MOVE_TRANSLATION_PORT.equals(connection.toPort)) {
                continue;
            }
            if (MOVE_GEOMETRY_TYPE.equals(typeByRef.get(connection.toRef))) {
                moves.add(connection.toRef);
            }
        }
        return moves;
    }

    private static boolean usesPlayerAnchoredMove(Set<String> moveAnchors, Set<String> playerAnchoredMoves) {
        if (playerAnchoredMoves.isEmpty()) {
            return moveAnchors.isEmpty();
        }
        for (String moveRef : moveAnchors) {
            if (playerAnchoredMoves.contains(moveRef)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> moveAnchorsInUpstream(
            String startRef,
            Map<String, List<GraphPresetRules.PresetConnection>> incoming,
            Map<String, String> typeByRef) {
        Set<String> upstream = upstreamRefs(startRef, incoming);
        Set<String> moveAnchors = new LinkedHashSet<>();
        for (String ref : upstream) {
            if (MOVE_GEOMETRY_TYPE.equals(typeByRef.get(ref))) {
                moveAnchors.add(ref);
            }
        }
        return moveAnchors;
    }

    private static Set<String> upstreamRefs(
            String startRef,
            Map<String, List<GraphPresetRules.PresetConnection>> incoming) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startRef);
        while (!queue.isEmpty()) {
            String ref = queue.poll();
            if (!visited.add(ref)) {
                continue;
            }
            for (GraphPresetRules.PresetConnection connection : incoming.getOrDefault(ref, List.of())) {
                if (connection.fromRef != null) {
                    queue.add(connection.fromRef);
                }
            }
        }
        return visited;
    }

    private static boolean isWorldPlacementPort(String toPort, String targetTypeId) {
        if (toPort == null || targetTypeId == null) {
            return false;
        }
        if (WORLD_PLACEMENT_PORTS.contains(toPort)) {
            return true;
        }
        return CREATE_LIST_TYPE.equals(targetTypeId) && LIST_INPUT_PORT.matcher(toPort).matches();
    }

    private static String formatMoveAnchors(Set<String> moveAnchors, Map<String, String> typeByRef) {
        if (moveAnchors.isEmpty()) {
            return "(none/local origin)";
        }
        List<String> labels = new ArrayList<>();
        for (String ref : moveAnchors) {
            labels.add(ref + " [" + typeByRef.getOrDefault(ref, "?") + "]");
        }
        return String.join(", ", labels);
    }

    private static Set<String> refsOfType(Map<String, String> typeByRef, String typeId) {
        Set<String> refs = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (typeId.equals(entry.getValue())) {
                refs.add(entry.getKey());
            }
        }
        return refs;
    }

    private static List<String> refsOfTypeInOrder(Map<String, String> typeByRef, String typeId) {
        List<String> refs = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeByRef.entrySet()) {
            if (typeId.equals(entry.getValue())) {
                refs.add(entry.getKey());
            }
        }
        return refs;
    }

    private static Map<String, List<GraphPresetRules.PresetConnection>> incomingByTarget(
            GraphPresetRules.GraphPresetDefinition preset) {
        Map<String, List<GraphPresetRules.PresetConnection>> incoming = new HashMap<>();
        if (preset.connections == null) {
            return incoming;
        }
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection == null || connection.toRef == null) {
                continue;
            }
            incoming.computeIfAbsent(connection.toRef, ignored -> new ArrayList<>()).add(connection);
        }
        return incoming;
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

    /**
     * Presets allowed to anchor geometry directly at Player Position without a final Move Geometry.
     */
    static boolean isWorldSpaceAnchorAllowlisted(String presetId) {
        return WORLD_SPACE_ANCHOR_ALLOWLIST.contains(presetId);
    }

    static List<Violation> findWorldSpaceWithoutMove(
            GraphPresetRules.GraphPresetDefinition preset,
            Map<String, String> typeByRef) {
        List<Violation> violations = new ArrayList<>();
        if (isWorldSpaceAnchorAllowlisted(preset.id)) {
            return violations;
        }

        Set<String> playerRefs = refsOfType(typeByRef, PLAYER_POSITION_TYPE);
        if (playerRefs.isEmpty()) {
            return violations;
        }

        boolean usesFinalMove = false;
        List<String> placementLinks = new ArrayList<>();
        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null || connection.fromRef == null || connection.toRef == null) {
                    continue;
                }
                if (!playerRefs.contains(connection.fromRef)) {
                    continue;
                }
                if (!PLAYER_OUTPUT_PORT.equals(connection.fromPort)) {
                    continue;
                }
                String targetType = typeByRef.get(connection.toRef);
                if (MOVE_GEOMETRY_TYPE.equals(targetType)
                        && MOVE_TRANSLATION_PORT.equals(connection.toPort)) {
                    usesFinalMove = true;
                    continue;
                }
                if (isWorldPlacementPort(connection.toPort, targetType)) {
                    placementLinks.add(connection.toRef + "." + connection.toPort);
                }
            }
        }

        if (!usesFinalMove && !placementLinks.isEmpty()) {
            violations.add(new Violation(
                    ViolationKind.WORLD_SPACE_WITHOUT_MOVE,
                    "Player Position anchors geometry at "
                            + String.join(", ", placementLinks)
                            + " without a final Move Geometry (intentional only for teaching allowlist presets)"));
        }
        return violations;
    }

    private static final Set<String> WORLD_SPACE_ANCHOR_ALLOWLIST = Set.of(
            "quickstart.basic_box",
            "quickstart.basic_sphere",
            "building_elements.stairs.straight_staircase");

    static List<Violation> auditIncludingWorldSpacePolicy(GraphPresetRules.GraphPresetDefinition preset) {
        List<Violation> violations = new ArrayList<>(audit(preset));
        violations.addAll(findWorldSpaceWithoutMove(preset, typeByRef(preset)));
        return violations;
    }

    static Set<String> violationCodesIncludingWorldSpacePolicy(GraphPresetRules.GraphPresetDefinition preset) {
        Set<String> codes = new LinkedHashSet<>();
        for (Violation violation : auditIncludingWorldSpacePolicy(preset)) {
            codes.add(violation.code());
        }
        return codes;
    }
}
