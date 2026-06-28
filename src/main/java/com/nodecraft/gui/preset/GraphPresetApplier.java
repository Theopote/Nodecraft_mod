package com.nodecraft.gui.preset;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraphPresetApplier {

    private static final Map<String, List<String>> OUTPUT_PORT_ALIASES = Map.ofEntries(
            Map.entry("output_result", List.of("output_geometry", "output_profile", "output_polyline", "output_blocks", "output_placements", "output_positions")),
            Map.entry("output_geometry", List.of("output_result")),
            Map.entry("output_instances", List.of("output_geometry", "output_placements", "output_positions", "output_blocks")),
            Map.entry("output_curve", List.of("output_polyline", "output_profile", "output_geometry")),
            Map.entry("output_profile", List.of("output_polyline", "output_geometry")),
            Map.entry("output_blocks", List.of("output_placements", "output_positions")),
            Map.entry("output_positions", List.of("output_placements", "output_blocks")),
            Map.entry("output_coordinates", List.of("output_positions", "output_blocks"))
    );

    private static final Map<String, List<String>> INPUT_PORT_ALIASES = Map.ofEntries(
            Map.entry("input_a", List.of("input_base", "input_left", "input_geometry_0", "input_geometry")),
            Map.entry("input_b", List.of("input_cutter", "input_right", "input_geometry_1", "input_geometry")),
            Map.entry("input_base", List.of("input_geometry_0", "input_start", "input_center", "input_geometry")),
            Map.entry("input_cutter", List.of("input_geometry_1", "input_geometry")),
            Map.entry("input_geometries", List.of("input_geometry_0", "input_geometry")),
            Map.entry("input_geometry", List.of("input_base", "input_geometry_0")),
            Map.entry("input_profile", List.of("input_points", "input_polyline", "input_curve")),
            Map.entry("input_curve", List.of("input_path", "input_polyline", "input_points")),
            Map.entry("input_path", List.of("input_curve", "input_polyline", "input_points")),
            Map.entry("input_points", List.of("input_origins", "input_coordinates", "input_positions")),
            Map.entry("input_origins", List.of("input_points", "input_coordinates", "input_positions")),
            Map.entry("input_blocks", List.of("input_placements", "input_coordinates", "input_geometry"))
    );

    public record ApplyResult(boolean success, String message, List<UUID> createdNodeIds) {
        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message, List.of());
        }

        public static ApplyResult success(String message, List<UUID> createdNodeIds) {
            return new ApplyResult(true, message, List.copyOf(createdNodeIds));
        }
    }

    private GraphPresetApplier() {
    }

    public static ApplyResult apply(GraphPresetRules.GraphPresetDefinition preset, float originX, float originY) {
        if (preset == null) {
            return ApplyResult.failure("Preset is missing");
        }
        if ("placeholder".equalsIgnoreCase(preset.kind)) {
            return ApplyResult.failure("该预设仍在筹备中");
        }
        if (!"composite".equalsIgnoreCase(preset.kind)) {
            return ApplyResult.failure("Unsupported preset kind: " + preset.kind);
        }
        if (preset.nodes == null || preset.nodes.isEmpty()) {
            return ApplyResult.failure("Preset has no nodes");
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        if (editor == null || editor.getCurrentGraph() == null) {
            return ApplyResult.failure("Editor is not ready");
        }

        Map<String, UUID> refToNodeId = new HashMap<>();
        List<UUID> createdNodeIds = new ArrayList<>();

        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            if (presetNode == null || presetNode.ref == null || presetNode.typeId == null) {
                continue;
            }
            INode created = editor.addNode(
                    presetNode.typeId,
                    originX + presetNode.x,
                    originY + presetNode.y);
            if (created == null) {
                rollback(editor, createdNodeIds);
                return ApplyResult.failure("Failed to create node: " + presetNode.typeId);
            }
            refToNodeId.put(presetNode.ref, created.getId());
            createdNodeIds.add(created.getId());
        }

        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null) {
                    continue;
                }
                UUID sourceNodeId = refToNodeId.get(connection.fromRef);
                UUID targetNodeId = refToNodeId.get(connection.toRef);
                if (sourceNodeId == null || targetNodeId == null) {
                    NodeCraft.LOGGER.warn(
                            "Skipping preset connection with unknown node reference in {}: {} -> {}",
                            preset.id,
                            connection.fromRef,
                            connection.toRef);
                    continue;
                }

                INode sourceNode = editor.getCurrentGraph().getNode(sourceNodeId);
                INode targetNode = editor.getCurrentGraph().getNode(targetNodeId);
                ResolvedConnection resolvedConnection = resolveConnection(
                        editor,
                        sourceNodeId,
                        sourceNode,
                        connection.fromPort,
                        targetNodeId,
                        targetNode,
                        connection.toPort);
                if (resolvedConnection == null) {
                    NodeCraft.LOGGER.warn(
                            "Skipping invalid preset connection in {}: {}.{} -> {}.{}",
                            preset.id,
                            connection.fromRef,
                            connection.fromPort,
                            connection.toRef,
                            connection.toPort);
                    continue;
                }

                boolean connected = editor.connectPorts(
                        sourceNodeId,
                        resolvedConnection.fromPortId,
                        targetNodeId,
                        resolvedConnection.toPortId);
                if (!connected) {
                    NodeCraft.LOGGER.warn(
                            "Skipping invalid preset connection in {}: {}.{} -> {}.{}",
                            preset.id,
                            connection.fromRef,
                            resolvedConnection.fromPortId,
                            connection.toRef,
                            resolvedConnection.toPortId);
                } else if (!resolvedConnection.matches(connection.fromPort, connection.toPort)) {
                    NodeCraft.LOGGER.info(
                            "Resolved preset connection ports in {}: {}.{} -> {}.{} became {}.{} -> {}.{}",
                            preset.id,
                            connection.fromRef,
                            connection.fromPort,
                            connection.toRef,
                            connection.toPort,
                            connection.fromRef,
                            resolvedConnection.fromPortId,
                            connection.toRef,
                            resolvedConnection.toPortId);
                }
            }
        }

        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().addAll(createdNodeIds);
        if (!createdNodeIds.isEmpty()) {
            editor.setSelectedNodeId(createdNodeIds.get(0));
        }

        NodeCraft.LOGGER.info("Applied graph preset {} ({} nodes)", preset.displayName, createdNodeIds.size());
        return ApplyResult.success("已添加预设: " + preset.displayName, createdNodeIds);
    }

    private static void rollback(ImGuiNodeEditor editor, List<UUID> createdNodeIds) {
        Set<UUID> ids = new HashSet<>(createdNodeIds);
        editor.getSelectedNodeIds().clear();
        editor.getSelectedNodeIds().addAll(ids);
        if (!ids.isEmpty()) {
            editor.setSelectedNodeId(ids.iterator().next());
            editor.deleteSelectedNodes();
        }
    }

    private static ResolvedConnection resolveConnection(
            ImGuiNodeEditor editor,
            UUID sourceNodeId,
            INode sourceNode,
            String requestedOutputPortId,
            UUID targetNodeId,
            INode targetNode,
            String requestedInputPortId) {
        if (editor == null || editor.getCurrentGraph() == null || sourceNode == null || targetNode == null) {
            return null;
        }

        for (String outputPortId : candidatePortIds(sourceNode.getOutputPorts(), requestedOutputPortId, OUTPUT_PORT_ALIASES)) {
            IPort outputPort = findPort(sourceNode.getOutputPorts(), outputPortId);
            if (outputPort == null) {
                continue;
            }
            for (String inputPortId : candidatePortIds(targetNode.getInputPorts(), requestedInputPortId, INPUT_PORT_ALIASES)) {
                IPort inputPort = findPort(targetNode.getInputPorts(), inputPortId);
                if (isConnectable(outputPort, inputPort)
                        && editor.getCurrentGraph().canConnect(sourceNodeId, outputPortId, targetNodeId, inputPortId)) {
                    return new ResolvedConnection(outputPortId, inputPortId);
                }
            }
        }

        for (IPort outputPort : sourceNode.getOutputPorts()) {
            for (IPort inputPort : targetNode.getInputPorts()) {
                if (isConnectable(outputPort, inputPort)
                        && editor.getCurrentGraph().canConnect(sourceNodeId, outputPort.getId(), targetNodeId, inputPort.getId())) {
                    return new ResolvedConnection(outputPort.getId(), inputPort.getId());
                }
            }
        }

        return null;
    }

    private static List<String> candidatePortIds(
            List<IPort> ports,
            String requestedPortId,
            Map<String, List<String>> aliases) {
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        if (requestedPortId != null && !requestedPortId.isBlank()) {
            candidateIds.add(requestedPortId);
            List<String> aliasIds = aliases.get(requestedPortId);
            if (aliasIds != null) {
                candidateIds.addAll(aliasIds);
            }
        }
        for (String candidateId : new ArrayList<>(candidateIds)) {
            List<String> aliasIds = aliases.get(candidateId);
            if (aliasIds != null) {
                candidateIds.addAll(aliasIds);
            }
        }
        if (isGeometryInputAlias(requestedPortId)) {
            for (IPort port : ports) {
                if (port.getId().matches("input_geometry_\\d+")) {
                    candidateIds.add(port.getId());
                }
            }
        }
        candidateIds.removeIf(candidateId -> findPort(ports, candidateId) == null);
        return new ArrayList<>(candidateIds);
    }

    private static boolean isGeometryInputAlias(String portId) {
        return "input_geometries".equals(portId)
                || "input_a".equals(portId)
                || "input_b".equals(portId)
                || "input_base".equals(portId)
                || "input_cutter".equals(portId);
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

    private static boolean isConnectable(IPort outputPort, IPort inputPort) {
        return outputPort != null
                && inputPort != null
                && NodeDataType.isConnectableTo(outputPort.getDataType(), inputPort.getDataType());
    }

    private record ResolvedConnection(String fromPortId, String toPortId) {
        boolean matches(String requestedFromPortId, String requestedToPortId) {
            return fromPortId.equals(requestedFromPortId) && toPortId.equals(requestedToPortId);
        }
    }
}
