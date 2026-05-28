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
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import com.nodecraft.nodesystem.util.Vec3; // 确保 Vec3 可用
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTableColumnFlags; // 添加 ImGuiTableColumnFlags 导入
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
    private final PropertyEditorState editorState;
    private final PortDataRenderer portDataRenderer;

    // 临时值存储, Key: nodeId_propertyName
    private final Map<String, Object> tempValues = new ConcurrentHashMap<>();

    // 正在编辑的属性集合：Key: nodeId_propertyName -> 最后活跃时间戳
    // 用于防止节点计算覆盖用户正在输入的值，并支持精确的超时清理
    private final Map<String, Long> propertiesBeingEdited = new ConcurrentHashMap<>();

    // 错误计数器：属性名 -> 错误次数 (针对当前 selectedNode 的属性)
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>(); // 每次 selectedNode 切换时重置

    private final NodeGraphAccess nodeGraphAccess;
    private final AiAssistantComponent aiAssistantComponent = new AiAssistantComponent();
    private final AiAssistantPanel aiAssistantPanel;

    public PropertyPanelComponent() {
        this.editorState = new PropertyEditorState(tempValues, propertiesBeingEdited, errorCounts);
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

    void applyPropertyValue(INode node, PropertyDescriptor prop, Object value) throws Throwable {
        prop.setter.invoke(node, value);
        if (node instanceof BaseCustomUINode customUINode) {
            customUINode.markDirty();
        }
    }

    // --- 各种类型的渲染器实现 ---
    private static final PropertyRenderer BOOLEAN_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            boolean currentValue = (boolean) prop.getter.invoke(node);
            ImBoolean imVal = new ImBoolean(currentValue);
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) ImGui.beginDisabled();
            if (ImGui.checkbox("##" + prop.name, imVal)) {
                if (!isReadOnly) {
                    // 立即保存到节点
                    panel.applyPropertyValue(node, prop, imVal.get());
                    NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), imVal.get());
                }
            }
            if (isReadOnly) ImGui.endDisabled();

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer STRING_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            String currentValue = (String) prop.getter.invoke(node);
            if (currentValue == null) currentValue = "";
            boolean isReadOnly = prop.setter == null;

            if (panel.shouldUseColorPickerForStringProperty(prop, currentValue)) {
                panel.renderStringColorPropertyEditor(node, prop, currentValue, isReadOnly);
                panel.errorCounts.remove(prop.name);
                return;
            }

            String tempKey = panel.getTempValueKey(node, prop.name);
            ImString imStr;

            if (!panel.tempValues.containsKey(tempKey) || !(panel.tempValues.get(tempKey) instanceof ImString)) {
                imStr = new ImString(currentValue, 256); // 增加缓冲区大小
                panel.tempValues.put(tempKey, imStr);
            } else {
                imStr = (ImString) panel.tempValues.get(tempKey);

                // 关键改进：检查属性是否正在被编辑，避免覆盖用户输入
                // 仅当 ImGui 控件不活跃且属性未被锁定编辑时，才从节点同步值
                if (!ImGui.isItemActive() && !panel.isPropertyBeingEdited(node, prop.name)) {
                    if (!imStr.get().equals(currentValue)) {
                        imStr.set(currentValue);
                    }
                }
            }

            int flags = ImGuiInputTextFlags.None;
            if (isReadOnly) flags |= ImGuiInputTextFlags.ReadOnly;

            // 检测ImGui控件的交互状态
            boolean changed = ImGui.inputText("##" + prop.name, imStr, flags | ImGuiInputTextFlags.EnterReturnsTrue);

            // 如果控件刚变为活跃，标记属性为正在编辑状态
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            // 如果控件刚变为不活跃，标记属性为编辑完成状态
            if (ImGui.isItemDeactivated() && panel.isPropertyBeingEdited(node, prop.name)) {
                panel.markPropertyEditingFinished(node, prop.name);
            }

            // 如果按下了回车键或失去焦点，且值已更改 - 立即保存
            if (changed || (ImGui.isItemDeactivated() && panel.isPropertyBeingEdited(node, prop.name))) {
                if (!isReadOnly) {
                    if (!imStr.get().equals(currentValue)) { // 避免不必要的setter调用
                        panel.applyPropertyValue(node, prop, imStr.get());
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), imStr.get());
                    }
                }
            }

            if (panel.isGeometryViewerBlockType(node, prop)) {
                panel.renderGeometryViewerBlockTypeHint(imStr.get());
            }

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer INT_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            int currentValue = (int) prop.getter.invoke(node);
            int[] valArr = {currentValue}; // ImGui dragInt 需要数组
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) ImGui.beginDisabled();
            if (ImGui.dragInt("##" + prop.name, valArr, 1)) { // 默认速度为1
                if (!isReadOnly) {
                    if (valArr[0] != currentValue) { // 避免不必要的setter调用
                        panel.applyPropertyValue(node, prop, valArr[0]);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), valArr[0]);
                    }
                }
            }
            // 标记编辑状态
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);

            if (isReadOnly) ImGui.endDisabled();

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer FLOAT_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            float currentValue = (float) prop.getter.invoke(node);
            float[] valArr = {currentValue};
            boolean isReadOnly = prop.setter == null;
            boolean isGeometryTransparency = panel.isGeometryViewerTransparency(node, prop);

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed;
            if (isGeometryTransparency) {
                changed = ImGui.sliderFloat("##" + prop.name, valArr, 0.0f, 1.0f, "%.2f");
            } else {
                changed = ImGui.dragFloat("##" + prop.name, valArr, 0.01f);
            }
            if (changed) {
                if (!isReadOnly) {
                    if (valArr[0] != currentValue) { // 避免不必要的setter调用
                        panel.applyPropertyValue(node, prop, valArr[0]);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), valArr[0]);
                    }
                }
            }
            // 标记编辑状态
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);

            if (isGeometryTransparency) {
                ImGui.sameLine();
                ImGui.text(String.format(Locale.ROOT, "%d%%", Math.round(valArr[0] * 100.0f)));
            }

            if (isReadOnly) ImGui.endDisabled();

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    // 改进的双精度渲染器，使用文本输入保持精度
    private static final PropertyRenderer DOUBLE_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            double currentValue = (double) prop.getter.invoke(node);

            String tempKey = panel.getTempValueKey(node, prop.name);
            ImString textValue;

            if (!panel.tempValues.containsKey(tempKey) || !(panel.tempValues.get(tempKey) instanceof ImString)) {
                // 首次创建或不是ImString类型，初始化为当前值
                textValue = new ImString(String.format("%.12f", currentValue), 64); // 增加缓冲区大小
                panel.tempValues.put(tempKey, textValue);
            } else {
                textValue = (ImString)panel.tempValues.get(tempKey);

                // 只有在用户未编辑且值变化时才从节点同步值
                if (!ImGui.isItemActive() && !panel.isPropertyBeingEdited(node, prop.name)) {
                    try {
                        double currentTextValue = Double.parseDouble(textValue.get());
                        if (Math.abs(currentTextValue - currentValue) > 1e-12) { // 使用epsilon比较浮点数
                            textValue.set(String.format("%.12f", currentValue));
                        }
                    } catch (NumberFormatException e) {
                        // 如果当前文本不是有效数字，重置为当前值
                        textValue.set(String.format("%.12f", currentValue));
                    }
                }
            }

            // 文本输入框设置
            int flags = ImGuiInputTextFlags.CharsDecimal;
            boolean isReadOnly = prop.setter == null;
            if (isReadOnly) flags |= ImGuiInputTextFlags.ReadOnly;

            boolean valueChanged = ImGui.inputText("##" + prop.name, textValue, flags | ImGuiInputTextFlags.EnterReturnsTrue);

            // 标记编辑状态
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);

            // 如果按下了回车键或失去焦点，且正在编辑
            if (!isReadOnly && (valueChanged || (ImGui.isItemDeactivated() && panel.isPropertyBeingEdited(node, prop.name)))) {
                try {
                    double newValue = Double.parseDouble(textValue.get());
                    if (Math.abs(newValue - currentValue) > 1e-12) { // 避免不必要的setter调用
                        panel.applyPropertyValue(node, prop, newValue);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newValue);
                    }
                } catch (NumberFormatException e) {
                    // 输入的不是有效数字，显示错误但不修改值
                    ImGui.sameLine();
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "无效数字");
                }
            }

            // 在输入框后添加一个快速拖动控件
            if (!isReadOnly) { // 只有可写属性才提供拖动
                ImGui.sameLine();
                ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.25f);

                float[] dragValue = new float[]{0.0f}; // 拖动增量

                // 拖动控件激活时，标记属性为正在编辑状态
                if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
                if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);

                if (ImGui.dragFloat("##drag_" + prop.name, dragValue, 0.01f, 0.0f, 0.0f, "%.3f")) {
                    try {
                        // 解析当前文本值
                        double baseValue = Double.parseDouble(textValue.get());
                        // 计算新值 (基值 + 拖动增量)
                        double newValue = baseValue + dragValue[0];
                        // 更新显示文本和节点值
                        textValue.set(String.format("%.12f", newValue));
                        panel.applyPropertyValue(node, prop, newValue);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newValue);
                        // 重置拖动增量
                        dragValue[0] = 0.0f;
                    } catch (NumberFormatException e) {
                        // 无效数字时忽略拖动
                    }
                }
                ImGui.popItemWidth();
            }

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    // 添加枚举类型渲染器
    private static final PropertyRenderer ENUM_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            Enum<?> currentValue = (Enum<?>) prop.getter.invoke(node);
            if (currentValue == null) {
                ImGui.textDisabled("(空)");
                return;
            }

            // 获取枚举的所有值
            Enum<?>[] values = currentValue.getDeclaringClass().getEnumConstants();
                String[] names = panel.buildEnumDisplayNames(node, prop, values);

            int currentIndex = currentValue.ordinal();
            ImInt selectedIndex = new ImInt(currentIndex);
            boolean isReadOnly = prop.setter == null;

            if (isReadOnly) ImGui.beginDisabled();
            if (ImGui.combo("##" + prop.name, selectedIndex, names)) {
                if (!isReadOnly && selectedIndex.get() != currentIndex) { // 避免不必要的setter调用
                    panel.applyPropertyValue(node, prop, values[selectedIndex.get()]);
                    NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), values[selectedIndex.get()]);
                }
            }
            // 标记编辑状态
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);

            if (isReadOnly) ImGui.endDisabled();

            // 如果鼠标悬停，显示所有可用值
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(panel.buildEnumTooltip(node, prop, values, names, selectedIndex.get()));
            }

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer VEC3_RENDERER = Vec3PropertyRenderer.RENDERER;

    private static final PropertyRenderer PLANE_RENDERER = PlanePropertyRenderer.RENDERER;

    private static final PropertyRenderer L_SYSTEM_RULE_RENDERER = LSystemRulePropertyRenderer.RENDERER;

    private static final PropertyRenderer POLYLINE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            PolylineData polyline = (PolylineData) prop.getter.invoke(node);
            if (polyline == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Points: " + polyline.getPointCount());
            ImGui.text("Segments: " + polyline.getSegmentCount());
            ImGui.text(String.format("Length: %.2f", polyline.getLength()));
            ImGui.text("Closed: " + (polyline.isClosed() ? "Yes" : "No"));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer REGION_RENDERER = RegionPropertyRenderer.RENDERER;

    private static final PropertyRenderer PLANT_STRUCTURE_RENDERER = PlantStructurePropertyRenderer.RENDERER;

    private static final PropertyRenderer BOX_GEOMETRY_RENDERER = BoxGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer BOX_FACE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            BoxFaceData face = (BoxFaceData) prop.getter.invoke(node);
            if (face == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Face: " + face.getName() + " (#" + face.getIndex() + ")");
            ImGui.text("Center: " + formatVector3d(face.getCenter()));
            ImGui.text("Normal: " + formatVector3d(face.getNormal()));
            ImGui.text("Corners: " + face.getCorners().size());
            ImGui.text("Edges: " + face.getEdgeCount());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer POLYGON_PROFILE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            PolygonProfileData profile = (PolygonProfileData) prop.getter.invoke(node);
            if (profile == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(profile.getCenter()));
            ImGui.text("Edges: " + profile.getEdgeCount());
            ImGui.text("Unique Points: " + profile.getUniquePoints().size());
            ImGui.text("Plane Normal: " + formatVector3d(profile.getPlane().getNormal()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer SURFACE_STRIP_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            SurfaceStripData strip = (SurfaceStripData) prop.getter.invoke(node);
            if (strip == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Sections: " + strip.getSectionCount());
            ImGui.text("Points / Section: " + strip.getPointsPerSection());
            ImGui.text("Flattened Points: " + strip.getFlattenedPoints().size());
            ImGui.text("All Closed: " + (strip.areAllSectionsClosed() ? "Yes" : "No"));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer PRISM_GEOMETRY_RENDERER = PrismGeometryPropertyRenderer.RENDERER;

    private static final PropertyRenderer SQUARE_PYRAMID_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            SquarePyramidGeometryData pyramid = (SquarePyramidGeometryData) prop.getter.invoke(node);
            if (pyramid == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Base Center: " + formatVector3d(pyramid.getBaseCenter()));
            ImGui.text("Apex: " + formatVector3d(pyramid.getApex()));
            ImGui.text(String.format("Base Size: %.2f", pyramid.getBaseSize()));
            ImGui.text(String.format("Height: %.2f", pyramid.getHeight()));
            ImGui.text("Normal: " + formatVector3d(pyramid.getNormal()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer TETRAHEDRON_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            TetrahedronGeometryData tetrahedron = (TetrahedronGeometryData) prop.getter.invoke(node);
            if (tetrahedron == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(tetrahedron.getCenter()));
            ImGui.text(String.format("Edge Length: %.2f", tetrahedron.getEdgeLength()));
            ImGui.text(String.format("Circumradius: %.2f", tetrahedron.getCircumradius()));
            ImGui.text("Vertices: " + tetrahedron.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

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

    private static final PropertyRenderer LIST_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            Object value = prop.getter.invoke(node);
            if (!(value instanceof List<?> list)) {
                if (value == null) {
                    ImGui.textDisabled("(绌?");
                } else {
                    ImGui.textWrapped(value.toString());
                }
                return;
            }

            panel.renderList(list, prop.displayName);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    // 改进的异常处理方法
    private static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "(null)";
        }
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatVector3d(Vector3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format("(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }

    private static String formatVec3d(Vec3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format("(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }

    void handlePropertyError(PropertyDescriptor prop, Throwable e) { // 统一捕获 Throwable
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
        int errorCount = errorCounts.getOrDefault(prop.name, 0) + 1;
        errorCounts.put(prop.name, errorCount);

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
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ScrollbarSize, baseScrollbarSize);
        try {
            checkAndCleanExpiredEditLocks();
            aiAssistantPanel.flushSessionStateIfDue();

            ImGui.text("Inspector");
            ImGui.separator();

            if (ImGui.beginTabBar("rightPanelTabs")) {
                if (ImGui.beginTabItem("Properties")) {
                    renderPropertiesTabContent();
                    ImGui.endTabItem();
                }

                if (ImGui.beginTabItem("AI Assistant")) {
                    aiAssistantPanel.render();
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
                .filter(prop -> shouldDisplayProperty(selectedNode, prop))
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
                    boolean isDisabled = errorCounts.getOrDefault(prop.name, 0) >= NodeConstants.ERROR_THRESHOLD;

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
        editorState.clearForNode(node);
    }

    /**
     * 清理所有临时值
     */
    private void clearAllTempValues() {
        editorState.clearAll();
    }

    /**
     * 清理当前选中节点的所有数据
     * 包括临时值、编辑锁等
     */
    private void clearSelectedNodeData(INode nodeToClear) {
        clearNodeScopedData(nodeToClear);
        // propertiesBeingEdited 在 clearCurrentNodeTempValues 内部已经处理了
        // errorCounts 也在 clearCurrentNodeTempValues 内部处理了
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
    private void renderList(List<?> list, String label) {
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
    void markPropertyBeingEdited(INode node, String propName) {
        editorState.markPropertyBeingEdited(node, propName);
    }

    /**
     * 标记属性为编辑完成状态
     * @param node 节点
     * @param propName 属性名
     */
    void markPropertyEditingFinished(INode node, String propName) {
        editorState.markPropertyEditingFinished(node, propName);
        String key = getTempValueKey(node, propName);
        NodeCraft.LOGGER.trace("属性 {} 标记为编辑完成。", key);
    }

    /**
     * 检查属性是否正在被编辑 (或编辑锁未过期)
     * @param node 节点
     * @param propName 属性名
     * @return 是否正在被编辑
     */
    boolean isPropertyBeingEdited(INode node, String propName) {
        return editorState.isPropertyBeingEdited(node, propName);
    }

    /**
     * 检查和清理过期的编辑锁
     * 定期调用，移除所有超时的编辑锁
     */
    private void checkAndCleanExpiredEditLocks() {
        editorState.checkAndCleanExpiredEditLocks();
    }

    // 修改为使用节点ID和属性名作为键
    String getTempValueKey(INode node, String propName) {
        return editorState.getTempValueKey(node, propName);
    }

    @SuppressWarnings("unchecked")
    <T> T getOrCreateTempValue(String key, Supplier<T> supplier) {
        return (T) tempValues.computeIfAbsent(key, k -> supplier.get());
    }

    void clearPropertyError(String propName) {
        errorCounts.remove(propName);
    }

    private boolean shouldDisplayProperty(INode node, PropertyDescriptor prop) {
        if (node instanceof GeometryViewerNode geometryViewerNode) {
            boolean isGhostBackend = geometryViewerNode.getPreviewBackend() == com.nodecraft.nodesystem.preview.PreviewBackend.GHOST;
            GeometryViewerNode.GhostRenderMode mode = geometryViewerNode.getGhostRenderMode();

            if (!isGhostBackend && (
                "previewColor".equals(prop.name)
                    || "transparency".equals(prop.name)
                    || "showOutline".equals(prop.name)
                    || "ghostOutlineColor".equals(prop.name)
                    || "ghostRenderMode".equals(prop.name)
            )) {
                return false;
            }

            if ("ghostRenderMode".equals(prop.name)) {
                return isGhostBackend;
            }
            if ("previewColor".equals(prop.name)) {
                return isGhostBackend && mode != GeometryViewerNode.GhostRenderMode.BLOCK_COLOR;
            }
            if ("transparency".equals(prop.name)) {
                return isGhostBackend;
            }
            if ("showOutline".equals(prop.name)) {
                return isGhostBackend && mode == GeometryViewerNode.GhostRenderMode.SOLID_COLOR;
            }
            if ("ghostOutlineColor".equals(prop.name)) {
                return isGhostBackend
                    && mode == GeometryViewerNode.GhostRenderMode.SOLID_COLOR;
            }
        }
        return true;
    }

    private boolean isGeometryViewerTransparency(INode node, PropertyDescriptor prop) {
        return node instanceof GeometryViewerNode && "transparency".equals(prop.name);
    }

    private boolean isGeometryViewerBlockType(INode node, PropertyDescriptor prop) {
        return node instanceof GeometryViewerNode && "blockType".equals(prop.name);
    }

    private void renderGeometryViewerBlockTypeHint(String rawValue) {
        String value = rawValue != null ? rawValue.trim() : "";
        ImGui.sameLine();
        if (value.isEmpty()) {
            ImGui.textColored(0.95f, 0.8f, 0.35f, 1.0f, "Empty (fallback: minecraft:stone)");
            return;
        }

        boolean valid = isValidBlockTypeId(value);
        if (valid) {
            ImGui.textColored(0.35f, 0.85f, 0.45f, 1.0f, "Valid");
        } else {
            ImGui.textColored(0.95f, 0.4f, 0.4f, 1.0f, "Invalid block id");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Use namespace:path, e.g. minecraft:stone");
            }
        }
    }

    private boolean isValidBlockTypeId(String value) {
        try {
            Identifier id = Identifier.of(value);
            return Registries.BLOCK.containsId(id);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String[] buildEnumDisplayNames(INode node, PropertyDescriptor prop, Enum<?>[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = buildEnumDisplayName(node, prop, values[i]);
        }
        return labels;
    }

    private String buildEnumDisplayName(INode node, PropertyDescriptor prop, Enum<?> value) {
        if (node instanceof GeometryViewerNode && "ghostRenderMode".equals(prop.name)) {
            return switch (value.name()) {
                case "BLOCK_COLOR" -> "Block Color";
                case "SOLID_COLOR" -> "Solid Color";
                case "WIREFRAME" -> "Wireframe";
                default -> humanizeEnumName(value.name());
            };
        }
        return humanizeEnumName(value.name());
    }

    private String humanizeEnumName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

    private String buildEnumTooltip(INode node, PropertyDescriptor prop, Enum<?>[] values, String[] names, int selectedIndex) {
        if (node instanceof GeometryViewerNode && "ghostRenderMode".equals(prop.name)) {
            String current = (selectedIndex >= 0 && selectedIndex < names.length) ? names[selectedIndex] : "";
            return "Current: " + current + "\n"
                + "- Block Color: use block palette-derived color\n"
                + "- Solid Color: use Preview Color fill (+ optional Outline)\n"
                + "- Wireframe: render edges only";
        }

        StringBuilder tooltip = new StringBuilder("可用值:\n");
        for (String name : names) {
            tooltip.append("- ").append(name).append("\n");
        }
        return tooltip.toString();
    }

    private boolean shouldUseColorPickerForStringProperty(PropertyDescriptor prop, String value) {
        if (prop == null) {
            return false;
        }
        String name = prop.name != null ? prop.name.toLowerCase(Locale.ROOT) : "";
        String displayName = prop.displayName != null ? prop.displayName.toLowerCase(Locale.ROOT) : "";
        boolean colorNamed = name.contains("color") || displayName.contains("color");
        if (isHexColorString(value)) {
            return true;
        }
        if (!colorNamed) {
            return false;
        }
        return value == null || value.isBlank();
    }

    private void renderStringColorPropertyEditor(INode node, PropertyDescriptor prop, String currentValue, boolean isReadOnly) throws Throwable {
        String normalized = normalizeHexColor(currentValue);
        String tempKey = getTempValueKey(node, prop.name + "_hex_color");
        float[] rgb = (float[]) tempValues.computeIfAbsent(tempKey, key -> {
            Color parsed = Color.fromHex(normalized);
            return new float[]{parsed.getRed(), parsed.getGreen(), parsed.getBlue()};
        });

        if (!isPropertyBeingEdited(node, prop.name)) {
            Color parsed = Color.fromHex(normalized);
            rgb[0] = parsed.getRed();
            rgb[1] = parsed.getGreen();
            rgb[2] = parsed.getBlue();
        }

        if (isReadOnly) {
            ImGui.beginDisabled();
        }

        boolean colorChanged = ImGui.colorEdit3("##" + prop.name + "_picker", rgb);
        if (ImGui.isItemActive()) {
            markPropertyBeingEdited(node, prop.name);
        }
        if (ImGui.isItemDeactivated()) {
            markPropertyEditingFinished(node, prop.name);
        }

        if (isReadOnly) {
            ImGui.endDisabled();
        }

        if (!isReadOnly && colorChanged) {
            String newHex = toHexColor(rgb);
            if (!newHex.equalsIgnoreCase(normalized)) {
                applyPropertyValue(node, prop, newHex);
                NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newHex);
            }
        }
    }

    private static boolean isHexColorString(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }
        return trimmed.matches("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$");
    }

    private static String normalizeHexColor(String value) {
        if (isHexColorString(value)) {
            return value.trim();
        }
        return "#000000";
    }

    private static String toHexColor(float[] rgb) {
        int r = Math.max(0, Math.min(255, Math.round(rgb[0] * 255.0f)));
        int g = Math.max(0, Math.min(255, Math.round(rgb[1] * 255.0f)));
        int b = Math.max(0, Math.min(255, Math.round(rgb[2] * 255.0f)));
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<PropertyDescriptor> getPropertiesForNode(Class<?> nodeClass) {
        return propertyInspector.getPropertiesForNode(nodeClass);
    }

    // 渲染器注册表：类型 -> 渲染器
    static {
        // 注册基本类型渲染器
        registerRenderer(boolean.class, BOOLEAN_RENDERER);
        registerRenderer(Boolean.class, BOOLEAN_RENDERER);
        registerRenderer(String.class, STRING_RENDERER);
        registerRenderer(int.class, INT_RENDERER);
        registerRenderer(Integer.class, INT_RENDERER);
        registerRenderer(float.class, FLOAT_RENDERER);
        registerRenderer(Float.class, FLOAT_RENDERER);
        registerRenderer(double.class, DOUBLE_RENDERER);
        registerRenderer(Double.class, DOUBLE_RENDERER);
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
    PropertyRenderer getRendererForType(Class<?> type) {
        return PropertyRendererRegistry.getRendererForType(type, ENUM_RENDERER);
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
