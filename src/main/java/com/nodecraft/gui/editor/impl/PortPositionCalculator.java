package com.nodecraft.gui.editor.impl;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nodecraft.gui.editor.session.EditorSession;
import com.nodecraft.gui.editor.session.NodeDisplayMode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * 端口位置计算器
 * 负责计算和管理节点端口的屏幕位置
 */
public class PortPositionCalculator {

    private final ICanvasEditor editor;
    private final PortPositionManager portPositionManager;
    private final FlatPortPositionManager flatPortManager;
    private boolean useFlatPortManager = true; // 默认使用新的扁平化管理器

    public PortPositionCalculator(ICanvasEditor editor) {
        this.editor = editor;
        this.portPositionManager = new PortPositionManager();
        this.flatPortManager = new FlatPortPositionManager();
    }

    /**
     * 计算所有节点的端口位置
     */
    public void calculatePortPositions(ImVec2 canvasPos, NodeGraph graph,
                                       Map<UUID, NodePosition> nodePositions,
                                       Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        if (graph == null) return;

        if (useFlatPortManager) {
            flatPortManager.nextFrame();
        } else {
            portPositionManager.nextFrame();
        }

        float canvasZoom = editor.getCanvasZoom();
        float canvasOffsetX = editor.getCanvasOffsetX();
        float canvasOffsetY = editor.getCanvasOffsetY();

        float baseTextLineHeight = ImGui.getTextLineHeight();
        float baseItemSpacingY = ImGui.getStyle().getItemSpacingY();
        NodeDisplayMode displayMode = resolveDisplayMode();

        for (INode node : graph.getNodes()) {
            updateNodeDimensionsInPosition(node, nodePositions, displayMode, baseTextLineHeight, baseItemSpacingY);
        }

        for (INode node : graph.getNodes()) {
            UUID nodeId = node.getId();
            NodePosition pos = nodePositions.get(nodeId);

            if (pos == null) continue;

            float nodeScreenX = canvasPos.x + (pos.x * canvasZoom + canvasOffsetX);
            float nodeScreenY = canvasPos.y + (pos.y * canvasZoom + canvasOffsetY);
            float finalNodeWidthScaled = pos.width * canvasZoom;

            if (!portScreenPositions.containsKey(nodeId)) {
                portScreenPositions.put(nodeId, new HashMap<>());
            }

            float scaledTextLineHeight = baseTextLineHeight * canvasZoom;
            float scaledPortVerticalSpacing = baseItemSpacingY * canvasZoom * 0.8f;

            List<IPort> visibleInputPorts = getVisibleInputPorts(node);
            List<IPort> visibleOutputPorts = getVisibleOutputPorts(node);
            NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                    node, displayMode, visibleInputPorts, visibleOutputPorts,
                    this::getCachedTextWidth, baseTextLineHeight, baseItemSpacingY);

            float portYOffset;
            if (layout.compactReroute()) {
                portYOffset = nodeScreenY + (pos.height * canvasZoom - scaledTextLineHeight) / 2.0f;
            } else {
                portYOffset = nodeScreenY + layout.portRegionTop() * canvasZoom;
            }

            boolean hasInputPorts = !visibleInputPorts.isEmpty();
            boolean hasOutputPorts = !visibleOutputPorts.isEmpty();

            if (useFlatPortManager) {
                if (hasInputPorts) {
                    int numInputPorts = visibleInputPorts.size();
                    for (int i = 0; i < numInputPorts; i++) {
                        IPort port = visibleInputPorts.get(i);
                        float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;
                        flatPortManager.setPortPosition(nodeId, port.getId(), nodeScreenX, currentPortY);
                        portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(nodeScreenX, currentPortY));
                    }
                }

                if (hasOutputPorts) {
                    float outputPortCircleX = nodeScreenX + finalNodeWidthScaled;
                    int numOutputPorts = visibleOutputPorts.size();
                    for (int i = 0; i < numOutputPorts; i++) {
                        IPort port = visibleOutputPorts.get(i);
                        float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;
                        flatPortManager.setPortPosition(nodeId, port.getId(), outputPortCircleX, currentPortY);
                        portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(outputPortCircleX, currentPortY));
                    }
                }
            } else {
                PortPositionManager.PortPositionCalculator calculator = (nodeIdParam, nodeParam, positions) -> {
                    float localNodeScreenX = canvasPos.x + (pos.x * canvasZoom + canvasOffsetX);
                    float localFinalNodeWidthScaled = pos.width * canvasZoom;

                    if (hasInputPorts) {
                        int numInputPorts = visibleInputPorts.size();
                        for (int i = 0; i < numInputPorts; i++) {
                            IPort port = visibleInputPorts.get(i);
                            float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;
                            positions.setPosition(port.getId(), localNodeScreenX, currentPortY);
                            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(localNodeScreenX, currentPortY));
                        }
                    }

                    if (hasOutputPorts) {
                        float outputPortCircleX = localNodeScreenX + localFinalNodeWidthScaled;
                        int numOutputPorts = visibleOutputPorts.size();
                        for (int i = 0; i < numOutputPorts; i++) {
                            IPort port = visibleOutputPorts.get(i);
                            float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;
                            positions.setPosition(port.getId(), outputPortCircleX, currentPortY);
                            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(outputPortCircleX, currentPortY));
                        }
                    }
                };

                portPositionManager.updateNodePortPositions(nodeId, pos, canvasZoom, canvasOffsetX, canvasOffsetY, node, calculator);
            }
        }
    }

    /**
     * 获取端口屏幕位置
     */
    public ImVec2 getPortScreenPosition(UUID nodeId, String portId, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        if (useFlatPortManager) {
            return flatPortManager.getPortPosition(nodeId, portId);
        }

        Map<String, ImVec2> ports = portScreenPositions.get(nodeId);
        return (ports != null) ? ports.get(portId) : null;
    }

    /**
     * 更新节点尺寸在位置信息中
     */
    private void updateNodeDimensionsInPosition(INode node, Map<UUID, NodePosition> nodePositions,
                                                NodeDisplayMode displayMode,
                                                float baseTextLineHeight, float baseItemSpacingY) {
        UUID nodeId = node.getId();
        NodePosition pos = nodePositions.computeIfAbsent(nodeId, id -> new NodePosition(100, 100));

        NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                node, displayMode, getVisibleInputPorts(node), getVisibleOutputPorts(node),
                this::getCachedTextWidth, baseTextLineHeight, baseItemSpacingY);
        pos.setSize(layout.width(), layout.height());
    }

    private NodeDisplayMode resolveDisplayMode() {
        EditorSession session = editor.getEditorSession();
        return session != null ? session.getNodeDisplayMode() : NodeDisplayMode.TEXT_ONLY;
    }

    private float getCachedTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;
        return ImGui.calcTextSize(text).x;
    }

    /**
     * 预计算端口文本大小
     */
    public void precalculatePortTextSizes(INode node) {
        NodeDisplayMode mode = resolveDisplayMode();
        List<IPort> visibleInputPorts = getVisibleInputPorts(node);
        List<IPort> visibleOutputPorts = getVisibleOutputPorts(node);
        if (visibleInputPorts != null) {
            for (IPort port : visibleInputPorts) {
                getCachedTextWidth(NodePortLabelUtil.portLabel(mode, port.getDisplayName()));
            }
        }
        if (visibleOutputPorts != null) {
            for (IPort port : visibleOutputPorts) {
                getCachedTextWidth(NodePortLabelUtil.portLabel(mode, port.getDisplayName()));
            }
        }
    }

    private List<IPort> getVisibleInputPorts(INode node) {
        return filterViewerPorts(node.getInputPorts(), node);
    }

    private List<IPort> getVisibleOutputPorts(INode node) {
        return filterViewerPorts(node.getOutputPorts(), node);
    }

    private List<IPort> filterViewerPorts(List<IPort> ports, INode node) {
        if (ports == null || ports.isEmpty()) {
            return ports;
        }

        List<IPort> visiblePorts = new ArrayList<>();
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
                        "input_hemisphere_geometry".equals(portId) ||
                        "input_prism_geometry".equals(portId) ||
                        "input_tetrahedron_geometry".equals(portId) ||
                        "input_octahedron_geometry".equals(portId) ||
                        "input_icosahedron_geometry".equals(portId) ||
                        "input_dodecahedron_geometry".equals(portId);
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
