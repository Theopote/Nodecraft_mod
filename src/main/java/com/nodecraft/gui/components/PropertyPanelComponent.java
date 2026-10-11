package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft; // For logging
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlantStructure;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.util.Vec3; // 确保 Vec3 可用
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.gui.components.ai.AiAssistantComponent;
import com.nodecraft.gui.components.ai.AiAssistantPanel;
import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.gui.components.node.NodeActionPanel;
import com.nodecraft.gui.components.node.NodeConstants;
import com.nodecraft.gui.components.node.NodeGraphAccess;
import com.nodecraft.gui.components.node.NodeStatusPresenter;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyEditSession;
import com.nodecraft.gui.components.property.core.PropertyApplyException;
import com.nodecraft.gui.components.property.core.PropertyValueSnapshots;
import com.nodecraft.gui.components.property.core.SnapshotIsolationException;
import com.nodecraft.gui.components.property.core.PropertyEditorRegistry;
import com.nodecraft.gui.components.property.core.PropertyInspector;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyRendererRegistry;
import com.nodecraft.gui.components.property.core.PropertySectionOrganizer;
import com.nodecraft.gui.components.property.layout.PropertyGroupRenderer;
import com.nodecraft.gui.components.property.renderers.*;
import com.nodecraft.gui.components.property.support.PropertyVisibilitySupport;
import com.nodecraft.gui.components.port.PortDataRenderer;
import com.nodecraft.gui.components.port.PortHighlightSupport;
import com.nodecraft.gui.components.port.PortTableRenderer;
import com.nodecraft.gui.editor.GraphStructureEditSupport;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.ResettableNode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public class PropertyPanelComponent implements EditorComponent {

    private static final String COMPONENT_ID = "property_panel";
    private static final Set<String> HIDDEN_NODE_PROPERTIES = Set.of(
            "cachedHeight",
            "cachedMinWidth",
            "customUIHeight",
            "description",
            "displayName",
            "id",
            "inputPorts",
            "minRequiredUIWidth",
            "nodeState",
            "outputPorts",
            "positionX",
            "positionY",
            "typeId"
    );

    private boolean visible = true;
    private volatile INode selectedNode = null;
    private final Object selectionLock = new Object();
    private final AtomicReference<UUID> selectedNodeIdSnapshot = new AtomicReference<>(null);
    private final PropertyInspector propertyInspector = new PropertyInspector();
    private final PropertyEditSession editSession = new PropertyEditSession();
    private final PortDataRenderer portDataRenderer;

    private final NodeGraphAccess nodeGraphAccess;
    private final AiAssistantComponent aiAssistantComponent = new AiAssistantComponent();
    private final AiAssistantPanel aiAssistantPanel;
    private final NodeActionPanel.ResetFeedback resetFeedback = new NodeActionPanel.ResetFeedback();

    public PropertyPanelComponent() {
        this.nodeGraphAccess = new NodeGraphAccess(() -> {
            try {
                return ImGuiNodeEditor.getInstance().getCurrentGraph();
            } catch (Exception e) {
                NodeCraft.LOGGER.error("获取节点图失败", e);
                return null;
            }
        });
        this.portDataRenderer = new PortDataRenderer(new PortDataRenderer.Actions() {
            @Override
            public void copyToClipboard(String text) {
                PropertyPanelComponent.this.copyToClipboard(text);
            }

            @Override
            public void highlightPoint(Vec3 point) {
                PropertyPanelComponent.this.highlightPoint(point);
            }

            @Override
            public void highlightPoints(List<?> points) {
                PropertyPanelComponent.this.highlightPoints(points);
            }

            @Override
            public void highlightRegion(Object region) {
                PropertyPanelComponent.this.highlightRegion(region);
            }
        });
        this.aiAssistantPanel = new AiAssistantPanel(
            aiAssistantComponent,
            this::getNodeGraph,
            this::copyToClipboard
        );
    }

    /**
     * Applies a property write, marks the node dirty, and records undo history when recording is enabled.
     * Snapshots are taken before the setter; isolation failure refuses the write.
     * If history recording fails after a successful setter due to isolation, the setter is rolled back.
     * <p>
     * Port-structure properties ({@code inputCount}) use a full-document graph transaction so undo
     * restores ports and connections together; dangling edges are cleaned inside that transaction.
     */
    public void applyPropertyValue(INode node, PropertyDescriptor prop, Object value) throws Throwable {
        if (prop.setter == null) {
            return;
        }
        Object oldValue = prop.getter.invoke(node);
        if (propertyValuesEqual(oldValue, value)) {
            return;
        }

        if ("inputCount".equals(prop.name)) {
            applyPortStructureProperty(node, prop, value);
            return;
        }

        final Object beforeSnap;
        final Object afterSnap;
        try {
            beforeSnap = PropertyValueSnapshots.copy(oldValue);
            afterSnap = PropertyValueSnapshots.copy(value);
        } catch (SnapshotIsolationException e) {
            throw new PropertyApplyException(
                    "Refusing property write: cannot isolate undo snapshot for '" + prop.name + "'", e);
        }

        prop.setter.invoke(node, value);
        markNodeDirtyAfterPropertyChange(node);

        try {
            long editSessionId = editSession.getEditSessionId(node, prop.name);
            ImGuiNodeEditor.getInstance().getHistory()
                    .recordPropertyChange(node.getId(), prop.name, beforeSnap, afterSnap, editSessionId);
        } catch (SnapshotIsolationException e) {
            try {
                prop.setter.invoke(node, beforeSnap);
                markNodeDirtyAfterPropertyChange(node);
            } catch (Throwable rollbackError) {
                throw new PropertyApplyException(
                        "Property write succeeded but undo snapshot failed and rollback failed for '"
                                + prop.name + "'",
                        rollbackError);
            }
            throw new PropertyApplyException(
                    "Property write rolled back: cannot isolate undo snapshot for '" + prop.name + "'", e);
        } catch (Exception e) {
            // Editor unavailable / not recording path — do not pretend undo exists.
            NodeCraft.LOGGER.debug("Property history not recorded for {}.{}: {}",
                    node.getId(), prop.name, e.getMessage());
        }
    }

    private void applyPortStructureProperty(INode node, PropertyDescriptor prop, Object value) throws Throwable {
        ImGuiNodeEditor editor;
        try {
            editor = ImGuiNodeEditor.getInstance();
        } catch (Exception e) {
            prop.setter.invoke(node, value);
            markNodeDirtyAfterPropertyChange(node);
            NodeCraft.LOGGER.debug("Port-structure property applied without graph transaction: {}", e.getMessage());
            return;
        }

        final Throwable[] setterError = new Throwable[1];
        boolean ok = GraphStructureEditSupport.run(
                editor.structureEditHost(),
                "Change inputCount",
                () -> {
                    try {
                        prop.setter.invoke(node, value);
                        markNodeDirtyAfterPropertyChange(node);
                        editor.cleanupDanglingConnections();
                        return true;
                    } catch (Throwable t) {
                        setterError[0] = t;
                        return false;
                    }
                }
        );
        if (setterError[0] != null) {
            throw setterError[0];
        }
        if (!ok) {
            NodeCraft.LOGGER.warn("Port-structure property '{}' transaction aborted for node {}",
                    prop.name, node.getId());
        }
    }

    private static boolean propertyValuesEqual(Object oldValue, Object newValue) {
        if (oldValue instanceof Number oldNumber && newValue instanceof Number newNumber) {
            return Double.compare(oldNumber.doubleValue(), newNumber.doubleValue()) == 0;
        }
        return Objects.equals(oldValue, newValue);
    }

    public void markNodeDirtyAfterPropertyChange(INode node) {
        if (node instanceof BaseNode baseNode) {
            baseNode.markDirty();
        }
    }

    public void performResetWithHistory(ResettableNode resettable) {
        if (!(resettable instanceof BaseNode baseNode)) {
            throw new IllegalArgumentException("Resettable node must be a BaseNode");
        }
        Object beforeState;
        try {
            beforeState = PropertyValueSnapshots.copyNodeState(baseNode.getNodeState());
        } catch (SnapshotIsolationException e) {
            throw new NodeActionPanel.ResetAbortedException(
                    "Reset cancelled. Could not safely snapshot node state.", e);
        }
        try {
            resettable.resetProperties();
            Object afterState = baseNode.getNodeState();
            markNodeDirtyAfterPropertyChange(baseNode);
            ImGuiNodeEditor.getInstance().getHistory()
                    .recordNodeStateChange(baseNode.getId(), beforeState, afterState, "Reset Properties");
        } catch (Exception e) {
            try {
                baseNode.setNodeState(beforeState);
            } catch (Exception rollbackError) {
                throw new NodeActionPanel.ResetInconsistentException(
                        "Reset failed and rollback failed",
                        rollbackError);
            }
            throw new NodeActionPanel.ResetRollbackException("Reset failed", e);
        }
    }

    // Primitive editors live in PropertyEditorRegistry / *PropertyRenderer.

    private static final PropertyRenderer VEC3_RENDERER = Vec3PropertyRenderer.RENDERER;

    private static final PropertyRenderer PLANE_RENDERER = PlanePropertyRenderer.RENDERER;

    private static final PropertyRenderer L_SYSTEM_RULE_RENDERER = LSystemRulePropertyRenderer.RENDERER;

    private static final PropertyRenderer POLYLINE_RENDERER = PolylinePropertyRenderer.RENDERER;

    private static final PropertyRenderer REGION_RENDERER = RegionPropertyRenderer.RENDERER;

    private static final PropertyRenderer PLANT_STRUCTURE_RENDERER = PlantStructurePropertyRenderer.RENDERER;

    private static final PropertyRenderer BOX_GEOMETRY_RENDERER = BoxGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer BOX_FACE_RENDERER = BoxFacePropertyRenderer.RENDERER;

    private static final PropertyRenderer POLYGON_PROFILE_RENDERER = PolygonProfilePropertyRenderer.RENDERER;

    private static final PropertyRenderer SURFACE_STRIP_RENDERER = SurfaceStripPropertyRenderer.RENDERER;

    private static final PropertyRenderer PRISM_GEOMETRY_RENDERER = PrismGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer SQUARE_PYRAMID_RENDERER = SquarePyramidGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer TETRAHEDRON_RENDERER = TetrahedronGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer CONE_GEOMETRY_RENDERER = ConeGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer FRUSTUM_CONE_GEOMETRY_RENDERER = FrustumConeGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer CYLINDER_GEOMETRY_RENDERER = CylinderGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer HEMISPHERE_GEOMETRY_RENDERER = HemisphereGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer ICOSAHEDRON_GEOMETRY_RENDERER = IcosahedronGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer DODECAHEDRON_GEOMETRY_RENDERER = DodecahedronGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer ELLIPSOID_GEOMETRY_RENDERER = EllipsoidGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer OCTAHEDRON_RENDERER = OctahedronGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer TORUS_GEOMETRY_RENDERER = TorusGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer VECTOR3D_RENDERER = Vector3dPropertyRenderer.RENDERER;

    private static final PropertyRenderer BLOCK_POS_RENDERER = BlockPosPropertyRenderer.RENDERER;

    private static final PropertyRenderer COLOR_RENDERER = ColorPropertyRenderer.COLOR_DATA_RENDERER;

    private static final PropertyRenderer NODE_COLOR_RENDERER = ColorPropertyRenderer.NODE_COLOR_RENDERER;

    private static final PropertyRenderer POINT_RENDERER = PointPropertyRenderer.RENDERER;

    private static final PropertyRenderer BOUNDING_BOX_RENDERER = BoundingBoxPropertyRenderer.RENDERER;

    private static final PropertyRenderer SPHERE_RENDERER = SpherePropertyRenderer.RENDERER;

    private static final PropertyRenderer LINE_RENDERER = LinePropertyRenderer.RENDERER;

    private static final PropertyRenderer GEOMETRY_RENDERER = GeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer CURVE_RENDERER = CurvePropertyRenderer.RENDERER;

    private static final PropertyRenderer BLOCK_POS_LIST_RENDERER = BlockPosListPropertyRenderer.RENDERER;

    private static final PropertyRenderer PLANT_BLOCK_RENDERER = PlantBlockPropertyRenderer.RENDERER;

    private static final PropertyRenderer LIST_RENDERER = ListPropertyRenderer.RENDERER;

    public void handlePropertyError(INode node, PropertyDescriptor prop, Throwable e) {
        String errorType;
        String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;

        if (cause instanceof IllegalAccessException) {
            errorType = "Access denied";
        } else if (cause instanceof IllegalArgumentException || cause instanceof ClassCastException) {
            errorType = "Invalid value or type";
        } else if (cause instanceof NullPointerException) {
            errorType = "Missing required data";
        } else {
            errorType = "Unexpected error";
        }

        int errorCount = editSession.recordPropertyError(node, prop.name);

        if (errorCount >= NodeConstants.ERROR_THRESHOLD) {
            NodeCraft.LOGGER.error(
                    "Property '{}' on node {} disabled after {} errors ({}): {}",
                    prop.name, node.getId(), errorCount, errorType, errorMessage);
        } else if (errorCount == 1) {
            NodeCraft.LOGGER.warn("Property '{}' on node {} failed ({}): {}",
                    prop.name, node.getId(), errorType, errorMessage, e);
        } else {
            NodeCraft.LOGGER.debug("Property '{}' on node {} failed again ({}): {}",
                    prop.name, node.getId(), errorType, errorMessage);
        }

        ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "Cannot edit this parameter");

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Property '" + prop.displayName + "' could not be edited.\nReason: " + errorType + ".");
        }
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    @Override
    public void init() {
        NodeCraft.LOGGER.debug("PropertyPanelComponent initialized");
    }

    @Override
    public void cleanup() {
        NodeCraft.LOGGER.debug("PropertyPanelComponent cleaned up");
        clearAllTempValues();
        propertyInspector.clearCache();
        aiAssistantPanel.cleanup();
        // 移除未保存更改相关的清理
        selectedNode = null;
    }

    @Override
    public void render(float x, float y, float width, float height, float windowPaddingX, float windowPaddingY) {
        if (!visible) return;

        float baseScrollbarSize = ImGui.getStyle().getScrollbarSize();
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ScrollbarSize, baseScrollbarSize * 0.5f);
        try {
            checkAndCleanExpiredEditLocks();
            aiAssistantPanel.flushSessionStateIfDue();

            if (ImGui.beginTabBar("rightPanelTabs")) {
                if (ImGui.beginTabItem("Properties")) {
                    renderPropertiesTabContent();
                    ImGui.endTabItem();
                }

                if (ImGui.beginTabItem("AI Assistant")) {
                    renderAiAssistantTabContent();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }

        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to render property panel", e);
            ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "Render error: " + e.getMessage());
        } finally {
            ImGui.popStyleVar();
        }
    }

    private void renderPropertiesTabContent() {
        try (ImGuiChildScope scope = new ImGuiChildScope(
                "rightPanelPropertiesContent", 0, 0, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            if (!scope.isOpen()) {
                return;
            }
            if (selectedNode != null) {
                renderCompactNodeHeader();
                renderHeaderActions();
                ImGui.separator();
                renderNodeProperties();

                if (ImGui.collapsingHeader("Input Ports")) {
                    renderInputPorts();
                }

                if (ImGui.collapsingHeader("Output Ports")) {
                    renderOutputPorts();
                }

                renderDangerActions();
            } else {
                ImGui.text("No node selected");
            }
        }
    }

    private void renderAiAssistantTabContent() {
        try (ImGuiChildScope scope = new ImGuiChildScope(
                "rightPanelAiContent", 0, 0, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            if (scope.isOpen()) {
                aiAssistantPanel.render();
            }
        }
    }

    private void renderCompactNodeHeader() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.94f, 0.98f, 1.0f);
        ImGui.text(selectedNode.getDisplayName());
        ImGui.popStyleColor();

        String nodeStatus = NodeStatusPresenter.getNodeStatus(selectedNode);
        ImVec4 statusColor = NodeStatusPresenter.getStatusColor(nodeStatus);
        ImGui.sameLine();
        ImGui.textColored(statusColor.x, statusColor.y, statusColor.z, statusColor.w, nodeStatus);

        if (nodeStatus.equals("Error") || nodeStatus.equals("Warning")) {
            ImGui.sameLine();
            ImGui.textDisabled("(?)");
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 22.0f);
                ImGui.textUnformatted(NodeStatusPresenter.getNodeStatusMessage(nodeStatus));
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
        }

        String categoryName = NodeStatusPresenter.getCategoryNameForNode(selectedNode.getTypeId());
        ImGui.textDisabled(categoryName);

        String description = selectedNode.getDescription();
        if (description != null && !description.isEmpty()) {
            if (ImGui.collapsingHeader("Help")) {
                ImGui.pushTextWrapPos(0.0f);
                ImGui.textWrapped(description);
                ImGui.popTextWrapPos();
            }
        }
    }

    private void renderHeaderActions() {
        resetFeedback.clear();
        NodeActionPanel.renderQuickActions(
                selectedNode,
                this::clearCurrentNodeTempValues,
                this::performResetWithHistory,
                resetFeedback
        );
        NodeActionPanel.renderProviderActions(selectedNode, this::getNodeGraph);
    }

    private void renderDangerActions() {
        NodeActionPanel.renderDangerActions(selectedNode, this::setSelectedNode, this::deleteSelectedNodeWithHistory);
    }

    /**
     * Deletes the property-panel node via the canvas editor command path so Ctrl+Z can restore it.
     */
    boolean deleteSelectedNodeWithHistory() {
        if (selectedNode == null) {
            return false;
        }
        try {
            ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
            UUID nodeId = selectedNode.getId();
            editor.clearSelectedNodes();
            editor.setSelectedNodeId(nodeId);
            return editor.deleteSelectedNodes();
        } catch (Exception e) {
            NodeCraft.LOGGER.error(
                    "History-aware delete failed for {}: {}",
                    selectedNode.getDisplayName(),
                    e.getMessage(),
                    e);
            return false;
        }
    }
    private void renderNodeProperties() {
        if (selectedNode == null) return;

        NodeActionPanel.renderAssistNodeControls(selectedNode, this::getNodeGraph);

        List<PropertyDescriptor> properties = getPropertiesForNode(selectedNode.getClass()).stream()
                .filter(prop -> !HIDDEN_NODE_PROPERTIES.contains(prop.name))
                .filter(prop -> PropertyVisibilitySupport.shouldDisplayProperty(selectedNode, prop))
                .toList();
        if (properties.isEmpty()) {
            ImGui.textDisabled("No editable properties");
            return;
        }

        PropertySectionOrganizer.OrganizedProperties organizedProperties =
                PropertySectionOrganizer.organize(properties);

        if (!organizedProperties.generalProperties().isEmpty()) {
            renderPropertyGroup(organizedProperties.generalProperties(), "General");
        }

        for (PropertySectionOrganizer.PropertySection section : organizedProperties.sections()) {
            if (!section.displayName().isEmpty()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.72f, 0.76f, 0.82f, 1.0f);
                int headerFlags = section.collapsedByDefault() ? 0 : ImGuiTreeNodeFlags.DefaultOpen;
                boolean open = ImGui.collapsingHeader(section.displayName(), headerFlags);
                ImGui.popStyleColor();
                if (open) {
                    renderPropertyGroup(section.properties(), section.categoryKey());
                }
            }
        }
    }


    private void renderPropertyGroup(List<PropertyDescriptor> props, String categoryInternalName) {
        PropertyGroupRenderer.renderGroup(this, selectedNode, props, categoryInternalName, editSession);
    }

    /**
     * 清理当前选中节点的临时值
     */
    private void clearCurrentNodeTempValues() {
        clearNodeScopedData(selectedNode);
    }

    private void clearNodeScopedData(INode node) {
        editSession.clearForNode(node);
    }

    /**
     * 清理所有临时值
     */
    private void clearAllTempValues() {
        editSession.clearAll();
    }

    /**
     * 清理当前选中节点的所有数据
     * 包括临时值、编辑锁等
     */
    private void clearSelectedNodeData(INode nodeToClear) {
        clearNodeScopedData(nodeToClear);
    }

    public void setSelectedNode(INode node) {
        UUID nextNodeId = node == null ? null : node.getId();
        INode previousNode;
        synchronized (selectionLock) {
            UUID currentNodeId = selectedNodeIdSnapshot.get();
            if (Objects.equals(currentNodeId, nextNodeId)) {
                // Keep the latest reference, but avoid redundant clear/reload churn.
                this.selectedNode = node;
                return;
            }

            previousNode = this.selectedNode;
            this.selectedNode = node;
            selectedNodeIdSnapshot.set(nextNodeId);
        }

        clearPropertyPanelPreviews(previousNode);
        clearSelectedNodeData(previousNode);
        if (node != null) {
            NodeCraft.LOGGER.debug("属性面板更新选中节点: {}", node.getId());
        } else {
            NodeCraft.LOGGER.debug("属性面板已清除选中节点");
        }
        aiAssistantPanel.onSelectedNodeChanged(node);
    }

    private NodeGraph getNodeGraph() {
        return nodeGraphAccess.getCurrentGraph();
    }

    private void renderInputPorts() {
        PortTableRenderer.renderInputPorts(selectedNode, getNodeGraph());
    }

    private void renderOutputPorts() {
        PortTableRenderer.renderOutputPorts(selectedNode, getNodeGraph());
    }

    // 增强List渲染，根据列表项类型使用专门的渲染逻辑
    public void renderList(List<?> list, String label) {
        portDataRenderer.renderList(list, label);
    }

    // 复制文本到剪贴板
    private void copyToClipboard(String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            NodeCraft.LOGGER.info("Copied to clipboard: {}", text);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to copy to clipboard", e);
        }
    }

    private void clearPropertyPanelPreviews(INode node) {
        if (node == null) {
            return;
        }
        String ownerId = PortHighlightSupport.previewOwnerId(node.getId());
        SelectionVisualFeedback.getInstance().clearFeedback(ownerId);
        PreviewManager.hideNodePreviews(ownerId);
    }

    private void highlightPoint(Vec3 point) {
        if (selectedNode == null || point == null) {
            return;
        }
        String ownerId = PortHighlightSupport.previewOwnerId(selectedNode.getId());
        Coordinate coordinate = PortHighlightSupport.vec3ToCoordinate(point);
        SelectionVisualFeedback.getInstance().showBlockSelection(
                ownerId,
                coordinate,
                SelectionVisualFeedback.SelectionState.SELECTED
        );
    }

    private void highlightPoints(List<?> points) {
        if (selectedNode == null || points == null || points.isEmpty()) {
            return;
        }
        List<Coordinate> coordinates = PortHighlightSupport.vec3ListToCoordinates(points);
        if (coordinates.isEmpty()) {
            return;
        }
        String ownerId = PortHighlightSupport.previewOwnerId(selectedNode.getId());
        SelectionVisualFeedback.getInstance().showMultiBlockSelection(
                ownerId,
                coordinates,
                SelectionVisualFeedback.SelectionState.SELECTED
        );
    }

    private void highlightRegion(Object region) {
        if (selectedNode == null || region == null) {
            return;
        }
        String ownerId = PortHighlightSupport.previewOwnerId(selectedNode.getId());
        PortHighlightSupport.extractRegionBounds(region).ifPresentOrElse(
                bounds -> PreviewManager.showRegionBox(ownerId, bounds.min(), bounds.maxExclusive()),
                () -> NodeCraft.LOGGER.debug("Could not highlight region for node {}: unsupported type {}",
                        selectedNode.getId(), region.getClass().getName())
        );
    }
    // 添加这些方法来管理属性编辑状态

    /**
     * 标记属性为正在编辑状态
     * @param node 节点
     * @param propName 属性名
     */
    public void markPropertyBeingEdited(INode node, String propName) {
        editSession.markPropertyBeingEdited(node, propName);
    }

    /**
     * 标记属性为编辑完成状态
     * @param node 节点
     * @param propName 属性名
     */
    public void markPropertyEditingFinished(INode node, String propName) {
        editSession.markPropertyEditingFinished(node, propName);
        String key = getTempValueKey(node, propName);
        NodeCraft.LOGGER.trace("属性 {} 标记为编辑完成。", key);
    }

    /**
     * 检查属性是否正在被编辑 (或编辑锁未过期)
     * @param node 节点
     * @param propName 属性名
     * @return 是否正在被编辑
     */
    public boolean isPropertyBeingEdited(INode node, String propName) {
        return editSession.isPropertyBeingEdited(node, propName);
    }

    public void refreshPropertyBeingEdited(INode node, String propName) {
        editSession.refreshPropertyBeingEdited(node, propName);
    }

    /**
     * 检查和清理过期的编辑锁
     * 定期调用，移除所有超时的编辑锁
     */
    private void checkAndCleanExpiredEditLocks() {
        editSession.checkAndCleanExpiredEditLocks();
    }

    // 修改为使用节点ID和属性名作为键
    public String getTempValueKey(INode node, String propName) {
        return editSession.getTempValueKey(node, propName);
    }

    public <T> T getOrCreateTempValue(String key, Supplier<T> supplier) {
        return editSession.getOrCreateTempValue(key, supplier);
    }

    public <T> T getOrReplaceTempValue(String key, Class<T> type, Supplier<T> supplier) {
        return editSession.getOrReplaceTempValue(key, type, supplier);
    }

    public void clearPropertyError(INode node, String propName) {
        editSession.clearPropertyError(node, propName);
    }

    private List<PropertyDescriptor> getPropertiesForNode(Class<?> nodeClass) {
        return propertyInspector.getPropertiesForNode(nodeClass);
    }

    // 渲染器注册表：类型 -> 渲染器
    static {
        PropertyEditorRegistry.registerPrimitives();
        registerRenderer(Vec3.class, VEC3_RENDERER);
        registerRenderer(PlaneData.class, PLANE_RENDERER);
        registerRenderer(PolylineData.class, POLYLINE_RENDERER);
        registerRenderer(LSystemRule.class, L_SYSTEM_RULE_RENDERER);
        registerRenderer(PlantStructure.class, PLANT_STRUCTURE_RENDERER);
        registerRenderer(RegionData.class, REGION_RENDERER);
        registerRenderer(BoxGeometryData.class, BOX_GEOMETRY_RENDERER);
        registerRenderer(BoxFaceData.class, BOX_FACE_RENDERER);
        registerRenderer(PolygonProfileData.class, POLYGON_PROFILE_RENDERER);
        registerRenderer(SurfaceStripData.class, SURFACE_STRIP_RENDERER);
        registerRenderer(PrismGeometryData.class, PRISM_GEOMETRY_RENDERER);
        registerRenderer(SquarePyramidGeometryData.class, SQUARE_PYRAMID_RENDERER);
        registerRenderer(TetrahedronGeometryData.class, TETRAHEDRON_RENDERER);
        registerRenderer(ConeGeometryData.class, CONE_GEOMETRY_RENDERER);
        registerRenderer(FrustumConeGeometryData.class, FRUSTUM_CONE_GEOMETRY_RENDERER);
        registerRenderer(HemisphereGeometryData.class, HEMISPHERE_GEOMETRY_RENDERER);
        registerRenderer(CylinderGeometryData.class, CYLINDER_GEOMETRY_RENDERER);
        registerRenderer(EllipsoidGeometryData.class, ELLIPSOID_GEOMETRY_RENDERER);
        registerRenderer(OctahedronGeometryData.class, OCTAHEDRON_RENDERER);
        registerRenderer(IcosahedronGeometryData.class, ICOSAHEDRON_GEOMETRY_RENDERER);
        registerRenderer(DodecahedronGeometryData.class, DODECAHEDRON_GEOMETRY_RENDERER);
        registerRenderer(TorusGeometryData.class, TORUS_GEOMETRY_RENDERER);
        registerRenderer(Vector3d.class, VECTOR3D_RENDERER);
        registerRenderer(BlockPos.class, BLOCK_POS_RENDERER);
        registerRenderer(ColorData.class, COLOR_RENDERER);
        registerRenderer(Color.class, NODE_COLOR_RENDERER);
        registerRenderer(PointData.class, POINT_RENDERER);
        registerRenderer(BoundingBoxData.class, BOUNDING_BOX_RENDERER);
        registerRenderer(SphereData.class, SPHERE_RENDERER);
        registerRenderer(LineData.class, LINE_RENDERER);
        registerRenderer(GeometryData.class, GEOMETRY_RENDERER);
        registerRenderer(Curve.class, CURVE_RENDERER);
        registerRenderer(BlockPosList.class, BLOCK_POS_LIST_RENDERER);
        registerRenderer(PlantStructure.PlantBlock.class, PLANT_BLOCK_RENDERER);
        registerRenderer(List.class, LIST_RENDERER);
    }

    /**
     * 注册一个类型的属性渲染器
     * @param type 要注册渲染器的类型
     * @param renderer 对应的渲染器实现
     */
    public static void registerRenderer(Class<?> type, PropertyRenderer renderer) {
        if (type == null || renderer == null) {
            throw new IllegalArgumentException("类型和渲染器都不能为null");
        }

        PropertyRendererRegistry.registerRenderer(type, renderer);
        NodeCraft.LOGGER.debug("已注册属性渲染器: {}", type.getName());
    }

    /**
     * 为类型获取合适的渲染器
     * @param type 需要获取渲染器的类型
     * @return 对应的渲染器，如果没有注册则返回null
     */
    public PropertyRenderer getRendererForType(Class<?> type) {
        return PropertyRendererRegistry.getRendererForType(type, PropertyEditorRegistry.enumRenderer());
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean handleEvent(String eventType, Object eventData) {
        switch (eventType) {
            case "nodeSelected":
                if (eventData instanceof UUID nodeId) {
                    NodeGraph currentGraph = getNodeGraph(); // 安全地获取图
                    if (currentGraph != null) {
                        INode newlySelectedNode = currentGraph.getNode(nodeId);
                        setSelectedNode(newlySelectedNode); // 调用 setter
                    } else {
                        setSelectedNode(null); // 清除选择或图无效
                    }
                    return true;
                } else if (eventData == null) { // 明确处理 eventData 为 null 的情况
                    setSelectedNode(null); // 调用 setter
                    return true;
                }
                break;
            case "nodeSelectionCleared":
            case "graphChanged": // 图改变或清除选择时都清除当前选中
                setSelectedNode(null); // 调用 setter
                return true;
        }
        return false;
    }
}
