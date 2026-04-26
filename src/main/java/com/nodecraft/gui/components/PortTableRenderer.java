package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

final class PortTableRenderer {

    private PortTableRenderer() {
    }

    static void renderInputPorts(INode selectedNode, NodeGraph graph) {
        if (selectedNode == null) {
            return;
        }

        java.util.List<IPort> inputPorts = filterViewerPorts(selectedNode.getInputPorts(), selectedNode);
        if (inputPorts.isEmpty()) {
            ImGui.textDisabled("No input ports");
            return;
        }

        if (ImGui.beginTable("inputPortsTable", 2,
                ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersOuter)) {
            ImGui.tableSetupColumn("Port", ImGuiTableColumnFlags.WidthFixed, ImGui.getContentRegionAvailX() * 0.4f);
            ImGui.tableSetupColumn("Value / Connection", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableHeadersRow();

            for (IPort port : inputPorts) {
                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text(port.getDisplayName());
                if (ImGui.isItemHovered()) {
                    StringBuilder tooltip = new StringBuilder();
                    tooltip.append("Type: ").append(port.getDataType().name());
                    if (port.getDescription() != null && !port.getDescription().isEmpty()) {
                        tooltip.append("\n").append(port.getDescription());
                    }
                    ImGui.setTooltip(tooltip.toString());
                }

                ImGui.tableSetColumnIndex(1);
                if (port.isConnected() && graph != null) {
                    UUID sourceNodeId = graph.getConnectedOutputNodeId(selectedNode.getId(), port.getId());
                    String sourcePortId = graph.getConnectedOutputPortId(selectedNode.getId(), port.getId());
                    if (sourceNodeId != null) {
                        INode sourceNode = graph.getNode(sourceNodeId);
                        if (sourceNode != null) {
                            ImGui.textDisabled("Connected from: " + sourceNode.getDisplayName());
                            for (IPort sourcePort : sourceNode.getOutputPorts()) {
                                if (sourcePort.getId().equals(sourcePortId)) {
                                    Object value = NodeOutputResolver.resolveNodeOutput(graph, sourceNode, sourcePortId);
                                    ImGui.textWrapped(PropertyValueFormatter.formatValuePreview(value));
                                    if (ImGui.isItemHovered()) {
                                        ImGui.setTooltip(PropertyValueFormatter.formatValueDetails(value, "Input Data"));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    Object value = port.getValue();
                    ImGui.textWrapped(PropertyValueFormatter.formatValuePreview(value));
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(PropertyValueFormatter.formatValueDetails(value, "Default Value"));
                    }
                }
            }
            ImGui.endTable();
        }
    }

    static void renderOutputPorts(INode selectedNode, NodeGraph graph) {
        if (selectedNode == null) {
            return;
        }

        java.util.List<IPort> outputPorts = filterViewerPorts(selectedNode.getOutputPorts(), selectedNode);
        if (outputPorts.isEmpty()) {
            ImGui.textDisabled("No output ports");
            return;
        }

        if (ImGui.beginTable("outputPortsTable", 2,
                ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersOuter)) {
            ImGui.tableSetupColumn("Port", ImGuiTableColumnFlags.WidthFixed, ImGui.getContentRegionAvailX() * 0.4f);
            ImGui.tableSetupColumn("Value / Connection", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableHeadersRow();

            for (IPort port : outputPorts) {
                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text(port.getDisplayName());
                if (ImGui.isItemHovered()) {
                    StringBuilder tooltip = new StringBuilder();
                    tooltip.append("Type: ").append(port.getDataType().name());
                    if (port.getDescription() != null && !port.getDescription().isEmpty()) {
                        tooltip.append("\n").append(port.getDescription());
                    }
                    ImGui.setTooltip(tooltip.toString());
                }

                ImGui.tableSetColumnIndex(1);
                Object value = graph != null
                        ? NodeOutputResolver.resolveNodeOutput(graph, selectedNode, port.getId())
                        : selectedNode.getOutput(port.getId());

                if (value != null) {
                    ImGui.textWrapped(PropertyValueFormatter.formatValuePreview(value));
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(PropertyValueFormatter.formatValueDetails(value, "Output Data"));
                    }
                } else {
                    value = port.getValue();
                    ImGui.textWrapped(PropertyValueFormatter.formatValuePreview(value));
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(PropertyValueFormatter.formatValueDetails(value, "Current Value"));
                    }
                }

                if (graph != null && port.isConnected()) {
                    Map<UUID, String> connectedInputs = graph.getConnectedInputs(selectedNode.getId(), port.getId());
                    if (!connectedInputs.isEmpty()) {
                        ImGui.separator();
                        ImGui.textDisabled("Connected to:");
                        for (Map.Entry<UUID, String> entry : connectedInputs.entrySet()) {
                            INode targetNode = graph.getNode(entry.getKey());
                            if (targetNode != null) {
                                String targetPortName = entry.getValue();
                                for (IPort targetPort : targetNode.getInputPorts()) {
                                    if (targetPort.getId().equals(entry.getValue())) {
                                        targetPortName = targetPort.getDisplayName();
                                        break;
                                    }
                                }
                                ImGui.text(" -> " + targetNode.getDisplayName() + "." + targetPortName);
                            }
                        }
                    }
                }
            }
            ImGui.endTable();
        }
    }

    private static java.util.List<IPort> filterViewerPorts(java.util.List<IPort> ports, INode node) {
        if (ports == null || ports.isEmpty()) {
            return ports;
        }

        java.util.List<IPort> visiblePorts = new ArrayList<>();
        for (IPort port : ports) {
            if (port == null) {
                continue;
            }

            String portId = port.getId();
            boolean isLegacyInput;

            if (node instanceof GeometryViewerNode) {
                isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                        "input_cylinder_geometry".equals(portId) ||
                        "input_sphere_geometry".equals(portId) ||
                        "input_torus_geometry".equals(portId) ||
                        "input_color".equals(portId) ||
                        "input_transparency".equals(portId);
            } else if (node instanceof PreviewGeometryNode) {
                isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                        "input_cylinder_geometry".equals(portId) ||
                        "input_sphere_geometry".equals(portId) ||
                        "input_torus_geometry".equals(portId) ||
                        "input_cone_geometry".equals(portId) ||
                        "input_frustum_cone_geometry".equals(portId) ||
                        "input_ellipsoid_geometry".equals(portId) ||
                        "input_prism_geometry".equals(portId) ||
                        "input_tetrahedron_geometry".equals(portId) ||
                        "input_octahedron_geometry".equals(portId);
            } else if (node instanceof ApplyChangesNode) {
                isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                        "input_cylinder_geometry".equals(portId) ||
                        "input_sphere_geometry".equals(portId) ||
                        "input_torus_geometry".equals(portId) ||
                        "input_preview_ids".equals(portId) ||
                        "input_notify".equals(portId);
            } else {
                return ports;
            }

            if (isLegacyInput && !port.isConnected()) {
                continue;
            }

            visiblePorts.add(port);
        }
        return visiblePorts;
    }
}
