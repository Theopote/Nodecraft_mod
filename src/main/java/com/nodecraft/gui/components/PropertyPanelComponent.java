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
import com.nodecraft.gui.components.node.NodeActionPanel;
import com.nodecraft.gui.components.node.NodeConstants;
import com.nodecraft.gui.components.node.NodeGraphAccess;
import com.nodecraft.gui.components.node.NodeStatusPresenter;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyEditSession;
import com.nodecraft.gui.components.property.core.PropertyEditorRegistry;
import com.nodecraft.gui.components.property.core.PropertyInspector;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.gui.components.property.core.PropertyRendererRegistry;
import com.nodecraft.gui.components.property.core.PropertySectionOrganizer;
import com.nodecraft.gui.components.property.renderers.*;
import com.nodecraft.gui.components.property.support.GeometryViewerPropertySupport;
import com.nodecraft.gui.components.port.PortDataRenderer;
import com.nodecraft.gui.components.port.PortTableRenderer;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTableColumnFlags; // 添加 ImGuiTableColumnFlags 导入
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
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

    public void applyPropertyValue(INode node, PropertyDescriptor prop, Object value) throws Throwable {
        prop.setter.invoke(node, value);
        if (node instanceof BaseCustomUINode customUINode) {
            customUINode.markDirty();
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

    // 改进的异常处理方法
    public void handlePropertyError(PropertyDescriptor prop, Throwable e) { // 统一捕获 Throwable
        // 根据错误类型选择日志级别
        boolean isSevere = false;
        String errorType;
        String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e; // 获取根原因

        if (cause instanceof IllegalAccessException) {
            errorType = "访问权限";
            isSevere = true;
        } else if (cause instanceof IllegalArgumentException || cause instanceof ClassCastException) {
            errorType = "类型不匹配/参数";
            isSevere = true;
        } else if (cause instanceof NullPointerException) {
            errorType = "空引用";
            isSevere = true;
        } else {
            errorType = "未知内部";
        }

        // 累加错误次数
        int errorCount = editSession.recordPropertyError(prop.name);

        // 如果错误次数超过阈值，标记为禁用
        if (errorCount >= NodeConstants.ERROR_THRESHOLD) { // 使用常量
            NodeCraft.LOGGER.error("属性 '{}' 因连续{}次{}错误已被禁用: {}", prop.name, errorCount, errorType, errorMessage);
            // 标记为禁用后，不再增加错误计数，且不再尝试渲染（在 PropertyRenderer 处处理）
        } else {
            // 第一次错误记录为WARN，后续为DEBUG以避免日志溢出
            if (errorCount == 1) {
                NodeCraft.LOGGER.warn("属性 '{}' {}错误: {}", prop.name, errorType, errorMessage, e);
            } else {
                NodeCraft.LOGGER.debug("属性 '{}' 再次发生{}错误 (第{}次): {}",
                        prop.name, errorType, errorCount, errorMessage);
            }
        }

        // 显示友好的错误消息
        ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "(错误)");

        if (ImGui.isItemHovered()) {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("属性 '").append(prop.displayName).append("' 处理失败:\n");
            tooltip.append(cause.getClass().getSimpleName()).append(": ").append(errorMessage).append("\n");

            if (isSevere) {
                tooltip.append("\n这是严重错误，请联系开发人员。");
            } else {
                tooltip.append("\n这是一个运行时错误，可能由节点逻辑问题引起。");
            }
            tooltip.append("\n\n继续操作可能不会影响其他属性。");
            if (errorCount < NodeConstants.ERROR_THRESHOLD) { // 使用常量
                tooltip.append("\n如果问题持续，此属性将在 ").append(NodeConstants.ERROR_THRESHOLD).append(" 次错误后被自动禁用。");
            } else {
                tooltip.append("\n此属性已被禁用，直到节点重新选择。");
            }

            ImGui.setTooltip(tooltip.toString());
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
        if (ImGui.beginChild("rightPanelPropertiesContent", 0, 0, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            try {
                if (selectedNode != null) {
                    if (ImGui.collapsingHeader("Basic Info")) {
                        renderNodeInfo();
                    }

                    if (ImGui.collapsingHeader("Node Properties", ImGuiTreeNodeFlags.DefaultOpen)) {
                        renderNodeProperties();
                    }

                    if (ImGui.collapsingHeader("Input Ports", ImGuiTreeNodeFlags.DefaultOpen)) {
                        renderInputPorts();
                    }

                    if (ImGui.collapsingHeader("Output Ports", ImGuiTreeNodeFlags.DefaultOpen)) {
                        renderOutputPorts();
                    }

                    if (ImGui.collapsingHeader("Actions", ImGuiTreeNodeFlags.DefaultOpen)) {
                        renderActionButtons();
                    }
                } else {
                    ImGui.text("No node selected");
                }
            } finally {
                ImGui.endChild();
            }
        }
    }

    private void renderAiAssistantTabContent() {
        if (ImGui.beginChild("rightPanelAiContent", 0, 0, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            try {
                aiAssistantPanel.render();
            } finally {
                ImGui.endChild();
            }
        }
    }

    private void renderNodeInfo() {
        String typeId = selectedNode.getTypeId();
        String categoryName = NodeStatusPresenter.getCategoryNameForNode(typeId);

        ImGui.text("Name: " + selectedNode.getDisplayName());
        ImGui.text("Category: " + categoryName);

        String description = selectedNode.getDescription();
        if (description != null && !description.isEmpty()) {
            ImGui.separator();
            ImGui.textWrapped("Description: " + description);
        }

        ImGui.separator();
        ImGui.text("Status: ");
        ImGui.sameLine();

        String nodeStatus = NodeStatusPresenter.getNodeStatus(selectedNode);
        ImVec4 statusColor = NodeStatusPresenter.getStatusColor(nodeStatus);

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
    }
    private void renderNodeProperties() {
        if (selectedNode == null) return;

        NodeActionPanel.renderAssistNodeControls(selectedNode, this::getNodeGraph);

        List<PropertyDescriptor> properties = getPropertiesForNode(selectedNode.getClass()).stream()
                .filter(prop -> !HIDDEN_NODE_PROPERTIES.contains(prop.name))
                .filter(prop -> GeometryViewerPropertySupport.shouldDisplayProperty(selectedNode, prop))
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
                boolean open = ImGui.collapsingHeader(section.displayName(), ImGuiTreeNodeFlags.DefaultOpen);
                ImGui.popStyleColor();
                if (open) {
                    renderPropertyGroup(section.properties(), section.categoryKey());
                }
            }
        }
    }


    private void renderPropertyGroup(List<PropertyDescriptor> props, String categoryInternalName) {
        if (ImGui.beginTable("propertiesTable_" + categoryInternalName, 2,
                ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersOuter)) {
            try {
                ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthFixed, ImGui.getContentRegionAvailX() * 0.32f);
                ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableHeadersRow();

                for (PropertyDescriptor prop : props) {
                    boolean isDisabled = editSession.isPropertyDisabled(prop.name, NodeConstants.ERROR_THRESHOLD);

                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    ImGui.text(prop.displayName);
                    if (ImGui.isItemHovered()) {
                        StringBuilder tooltip = new StringBuilder();
                        if (prop.description != null && !prop.description.isEmpty()) {
                            tooltip.append(prop.description);
                        }
                        if (prop.setter == null) {
                            if (!tooltip.isEmpty()) {
                                tooltip.append("\n");
                            }
                            tooltip.append("Read-only property");
                        }
                        if (isDisabled) {
                            if (!tooltip.isEmpty()) {
                                tooltip.append("\n");
                            }
                            tooltip.append("Temporarily disabled after repeated errors. Reselect the node to reset it.");
                        }
                        if (!tooltip.isEmpty()) {
                            ImGui.setTooltip(tooltip.toString());
                        }
                    }

                    ImGui.tableSetColumnIndex(1);
                    String uniqueId = selectedNode.getId().toString() + "_" + prop.name;
                    ImGui.pushID(uniqueId);
                    ImGui.pushItemWidth(-1.0f);
                    try {
                        PropertyRenderer renderer = prop.renderer != null ? prop.renderer : getRendererForType(prop.type);
                        renderer.render(this, selectedNode, prop, isDisabled);
                    } catch (Throwable e) {
                        handlePropertyError(prop, e);
                    } finally {
                        ImGui.popItemWidth();
                        ImGui.popID();
                    }
                }
            } finally {
                ImGui.endTable();
            }
        }
    }
    private void renderActionButtons() {
        NodeActionPanel.renderActionButtons(
                selectedNode,
                this::getNodeGraph,
                this::clearCurrentNodeTempValues,
                this::setSelectedNode
        );
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

    // 高亮单个坐标点 (占位符)
    private void highlightPoint(Vec3 point) {
        if (selectedNode == null) return;
        NodeCraft.LOGGER.info("Preview point: {}", point);
    }

    // 高亮坐标点集合 (占位符)
    private void highlightPoints(List<?> points) {
        if (selectedNode == null || points.isEmpty() || !(points.getFirst() instanceof Vec3)) return;
        NodeCraft.LOGGER.info("Preview {} points", points.size());
    }

    // 高亮区域 (占位符)
    private void highlightRegion(Object region) {
        if (selectedNode == null) return;
        NodeCraft.LOGGER.info("Preview region: {}", region);
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

    public void clearPropertyError(String propName) {
        editSession.clearPropertyError(propName);
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
