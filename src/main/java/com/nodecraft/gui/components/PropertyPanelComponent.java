package com.nodecraft.gui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nodecraft.gui.ai.*;
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
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.Vec3; // 确保 Vec3 可用
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.NodePosition;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTableColumnFlags; // 添加 ImGuiTableColumnFlags 导入
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

public class PropertyPanelComponent implements EditorComponent {

    private static final String COMPONENT_ID = "property_panel";
    private static final Gson AI_SETTINGS_GSON = new GsonBuilder().setPrettyPrinting().create();
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
    private INode selectedNode = null;
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

    private final ImString aiPromptInput = new ImString("", 2048);
    private final ImBoolean aiUseSelectionContext = new ImBoolean(true);
    private final ImBoolean aiIncludeGraphContext = new ImBoolean(true);
    private final List<AiChatMessage> aiChatMessages = new ArrayList<>();
    private final ImString aiApiBaseUrl = new ImString("https://api.openai.com/v1", 512);
    private final ImString aiApiKey = new ImString("", 512);
    private final ImString aiModel = new ImString("gpt-4.1-mini", 128);
    private final ImString aiSystemPrompt = new ImString("You are a NodeCraft graph planning assistant.", 2048);
    private final ImInt aiRequestTimeoutSeconds = new ImInt(60);
    private final ImBoolean aiShowApiKey = new ImBoolean(false);
    private final ImBoolean aiEnableRemotePlanner = new ImBoolean(false);
    private final ImBoolean aiAutoLayoutBeforeApply = new ImBoolean(true);
    private final ImBoolean aiPreviewOnlyMode = new ImBoolean(false);
    private final ImBoolean aiPatchApplyMode = new ImBoolean(true);
    private final ImBoolean aiPatchRemoveScopedConnections = new ImBoolean(false);
    private final Path aiSettingsPath;
    private final AiRemotePlannerService aiRemotePlannerService = new AiRemotePlannerService();
    private CompletableFuture<AiRemotePlannerService.RemotePlanResult> aiRemotePlanFuture = null;
    private String aiRemotePendingPrompt = "";
    private String aiLastSubmittedPrompt = "";
    private String aiLastRemoteRawResponse = "";
    private String aiLastRemoteModelText = "";
    private String aiLastRemoteRequestSnapshot = "";
    private String aiLastRemoteErrorCategory = "";
    private String aiLastRemoteErrorMessage = "";
    private int aiLastRemoteStatusCode = 0;
    private int aiLastRemoteAttempts = 0;
    private AiGraphPlan pendingAiPlan = null;
    private int lastAiUndoStepCount = 0;
    private String aiPlanStatusMessage = "";
    private String aiSettingsStatusMessage = "";

    private enum RightPanelTab {
        PROPERTIES,
        AI_ASSISTANT
    }

    private RightPanelTab activeTab = RightPanelTab.PROPERTIES;

    private record AiChatMessage(String role, String content, long timestampMs) {}

    private record AiPlanNode(String ref, String typeId, float offsetX, float offsetY, @org.jetbrains.annotations.Nullable Object nodeState) {}

    private record AiPlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {}

    private record AiGraphPlan(String summary, List<AiPlanNode> nodes, List<AiPlanConnection> connections, List<String> validationErrors) {
        boolean isValid() {
            return validationErrors == null || validationErrors.isEmpty();
        }
    }
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
        this.aiSettingsPath = resolveAiSettingsPath();
        loadAiSettingsFromDisk();
    }

    private void applyPropertyValue(INode node, PropertyDescriptor prop, Object value) throws Throwable {
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

    // 为Vec3添加渲染器
    private static final PropertyRenderer VEC3_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(已禁用)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("属性 '" + prop.displayName + "' 因频繁错误已被禁用");
            }
            return;
        }

        try {
            Vec3 vec = (Vec3) prop.getter.invoke(node);
            if (vec == null) {
                ImGui.textDisabled("(空)");
                return;
            }

            boolean isReadOnly = prop.setter == null;

            // 使用三个单独的浮点数组作为临时值，并与ImString同步
            String tempXKey = panel.getTempValueKey(node, prop.name + "_x");
            String tempYKey = panel.getTempValueKey(node, prop.name + "_y");
            String tempZKey = panel.getTempValueKey(node, prop.name + "_z");

            ImString xStr = (ImString) panel.tempValues.computeIfAbsent(tempXKey, k -> new ImString(String.format("%.3f", vec.getX()), 32));
            ImString yStr = (ImString) panel.tempValues.computeIfAbsent(tempYKey, k -> new ImString(String.format("%.3f", vec.getY()), 32));
            ImString zStr = (ImString) panel.tempValues.computeIfAbsent(tempZKey, k -> new ImString(String.format("%.3f", vec.getZ()), 32));

            // 同步值，但仅当未被编辑时
            if (!panel.isPropertyBeingEdited(node, prop.name + "_x")) xStr.set(String.format("%.3f", vec.getX()));
            if (!panel.isPropertyBeingEdited(node, prop.name + "_y")) yStr.set(String.format("%.3f", vec.getY()));
            if (!panel.isPropertyBeingEdited(node, prop.name + "_z")) zStr.set(String.format("%.3f", vec.getZ()));

            boolean changed = false;
            if (isReadOnly) ImGui.beginDisabled();

            float width = ImGui.getContentRegionAvailX() / 3 - ImGui.getStyle().getItemSpacingX(); // 确保留有间距

            ImGui.pushID("vec3_" + prop.name); // 为整个Vec3控件提供一个独立的ID空间

            // X
            ImGui.pushItemWidth(width);
            if (ImGui.inputText("X", xStr, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = true;
            }
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name + "_x");
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name + "_x");
            ImGui.popItemWidth();

            ImGui.sameLine();
            // Y
            ImGui.pushItemWidth(width);
            if (ImGui.inputText("Y", yStr, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = true;
            }
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name + "_y");
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name + "_y");
            ImGui.popItemWidth();

            ImGui.sameLine();
            // Z
            ImGui.pushItemWidth(width);
            if (ImGui.inputText("Z", zStr, ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue)) {
                changed = true;
            }
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name + "_z");
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name + "_z");
            ImGui.popItemWidth();

            ImGui.popID(); // 结束Vec3控件的ID空间

            // 如果值改变且存在setter，应用更改
            if (changed && !isReadOnly) {
                try {
                    Vec3 newVec = new Vec3(Double.parseDouble(xStr.get()),
                            Double.parseDouble(yStr.get()),
                            Double.parseDouble(zStr.get()));
                    // 只有当实际值发生变化时才调用setter
                    if (!newVec.equals(vec)) {
                        panel.applyPropertyValue(node, prop, newVec);
                        NodeCraft.LOGGER.debug("自动保存属性 '{}' 到节点 {}: {}", prop.name, node.getId(), newVec);
                    }
                } catch (NumberFormatException e) {
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "无效坐标");
                }
            }

            if (isReadOnly) ImGui.endDisabled();

            // 显示向量长度作为工具提示
            if (ImGui.isItemHovered()) {
                double length = vec.length();
                ImGui.setTooltip(String.format("长度: %.2f", length));
            }

            // 重置该属性的错误计数
            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) { // 统一捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer PLANE_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(宸茬鐢?");
            return;
        }

        try {
            PlaneData plane = (PlaneData) prop.getter.invoke(node);
            if (plane == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            Vector3d normal = plane.getNormal();
            double d = -normal.dot(plane.getPoint());
            String tempKey = panel.getTempValueKey(node, prop.name + "_plane");
            float[] values = (float[]) panel.tempValues.computeIfAbsent(tempKey,
                    k -> new float[]{(float) normal.x, (float) normal.y, (float) normal.z, (float) d});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = (float) normal.x;
                values[1] = (float) normal.y;
                values[2] = (float) normal.z;
                values[3] = (float) d;
            }

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed = false;
            float[] xValue = {values[0]};
            float[] yValue = {values[1]};
            float[] zValue = {values[2]};
            float[] dValue = {values[3]};
            changed |= ImGui.dragFloat("Normal X##" + prop.name, xValue, 0.01f);
            values[0] = xValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragFloat("Normal Y##" + prop.name, yValue, 0.01f);
            values[1] = yValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragFloat("Normal Z##" + prop.name, zValue, 0.01f);
            values[2] = zValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragFloat("Offset D##" + prop.name, dValue, 0.01f);
            values[3] = dValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);
            if (isReadOnly) ImGui.endDisabled();

            if (!isReadOnly && changed) {
                Vector3d newNormal = new Vector3d(values[0], values[1], values[2]);
                if (newNormal.lengthSquared() > 1.0e-8) {
                    newNormal.normalize();
                    double newD = values[3];
                    Vec3d origin = new Vec3d(-newD * newNormal.x, -newD * newNormal.y, -newD * newNormal.z);
                    Vec3d mcNormal = new Vec3d(newNormal.x, newNormal.y, newNormal.z);
                    panel.applyPropertyValue(node, prop, new PlaneData(origin, mcNormal));
                } else {
                    ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Normal cannot be zero");
                }
            }

            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer L_SYSTEM_RULE_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(宸茬鐢?");
            return;
        }

        try {
            LSystemRule rule = (LSystemRule) prop.getter.invoke(node);
            if (rule == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String symbolKey = panel.getTempValueKey(node, prop.name + "_symbol");
            String productionKey = panel.getTempValueKey(node, prop.name + "_production");
            String contextKey = panel.getTempValueKey(node, prop.name + "_context");
            String probabilityKey = panel.getTempValueKey(node, prop.name + "_probability");

            ImString symbol = (ImString) panel.tempValues.computeIfAbsent(symbolKey, k -> new ImString(rule.getSymbol(), 64));
            ImString production = (ImString) panel.tempValues.computeIfAbsent(productionKey, k -> new ImString(rule.getProduction(), 256));
            ImString context = (ImString) panel.tempValues.computeIfAbsent(contextKey, k -> new ImString(rule.getContext() != null ? rule.getContext() : "", 128));
            float[] probability = (float[]) panel.tempValues.computeIfAbsent(probabilityKey, k -> new float[]{rule.getProbability()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                symbol.set(rule.getSymbol());
                production.set(rule.getProduction());
                context.set(rule.getContext() != null ? rule.getContext() : "");
                probability[0] = rule.getProbability();
            }

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed = false;
            changed |= ImGui.inputText("Symbol##" + prop.name, symbol, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.inputText("Production##" + prop.name, production, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.inputText("Context##" + prop.name, context, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragFloat("Probability##" + prop.name, probability, 0.01f, 0.0f, 1.0f, "%.2f");
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);
            if (isReadOnly) ImGui.endDisabled();

            if (!isReadOnly && changed) {
                String contextValue = context.get().trim();
                panel.applyPropertyValue(node, prop, new LSystemRule(
                        symbol.get(),
                        production.get(),
                        Math.max(0.0f, Math.min(1.0f, probability[0])),
                        contextValue.isEmpty() ? null : contextValue
                ));
            }

            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

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

    private static final PropertyRenderer REGION_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            RegionData region = (RegionData) prop.getter.invoke(node);
            if (region == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Complete: " + (region.isComplete() ? "Yes" : "No"));
            ImGui.text("Corner 1: " + formatBlockPos(region.corner1()));
            ImGui.text("Corner 2: " + formatBlockPos(region.corner2()));
            if (region.isComplete()) {
                ImGui.text("Min: " + formatBlockPos(region.getMinCorner()));
                ImGui.text("Max: " + formatBlockPos(region.getMaxCorner()));
            }
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer PLANT_STRUCTURE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            PlantStructure structure = (PlantStructure) prop.getter.invoke(node);
            if (structure == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Total Blocks: " + structure.getTotalBlockCount());
            ImGui.text("Trunk: " + structure.getTrunkBlockCount());
            ImGui.text("Branches: " + structure.getBranchBlockCount());
            ImGui.text("Leaves: " + structure.getLeafBlockCount());
            ImGui.text("Flowers: " + structure.getFlowerBlockCount());
            ImGui.text("Roots: " + structure.getRootBlockCount());
            ImGui.text("Metadata: " + structure.getMetadata().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer BOX_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            BoxGeometryData box = (BoxGeometryData) prop.getter.invoke(node);
            if (box == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(box.getCenter()));
            ImGui.text("Half Extents: " + formatVector3d(box.getHalfExtents()));
            ImGui.text("Oriented: " + (box.isOriented() ? "Yes" : "No"));
            ImGui.text("Corners: " + box.getCornerCount());
            ImGui.text("Faces: " + box.getFaceCount());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

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

    private static final PropertyRenderer PRISM_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            PrismGeometryData prism = (PrismGeometryData) prop.getter.invoke(node);
            if (prism == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Base Vertices: " + prism.getBaseVertices().size());
            ImGui.text("Side Count: " + prism.getSideCount());
            ImGui.text(String.format("Height: %.2f", prism.getHeight()));
            ImGui.text("Extrusion: " + formatVector3d(prism.getExtrusionVector()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

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

    private static final PropertyRenderer CONE_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            ConeGeometryData cone = (ConeGeometryData) prop.getter.invoke(node);
            if (cone == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Base Center: " + formatVector3d(cone.getBaseCenter()));
            ImGui.text("Apex: " + formatVector3d(cone.getApex()));
            ImGui.text("Axis: " + formatVector3d(cone.getAxisVector()));
            ImGui.text(String.format("Height: %.2f", cone.getHeight()));
            ImGui.text(String.format("Base Radius: %.2f", cone.getBaseRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer FRUSTUM_CONE_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            FrustumConeGeometryData frustum = (FrustumConeGeometryData) prop.getter.invoke(node);
            if (frustum == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Base Center: " + formatVector3d(frustum.getBaseCenter()));
            ImGui.text("Top Center: " + formatVector3d(frustum.getTopCenter()));
            ImGui.text("Axis: " + formatVector3d(frustum.getAxisVector()));
            ImGui.text(String.format("Height: %.2f", frustum.getHeight()));
            ImGui.text(String.format("Base Radius: %.2f", frustum.getBaseRadius()));
            ImGui.text(String.format("Top Radius: %.2f", frustum.getTopRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer CYLINDER_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            CylinderGeometryData cylinder = (CylinderGeometryData) prop.getter.invoke(node);
            if (cylinder == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            Vector3d axis = cylinder.getEnd().sub(cylinder.getStart(), new Vector3d());
            ImGui.text("Start: " + formatVector3d(cylinder.getStart()));
            ImGui.text("End: " + formatVector3d(cylinder.getEnd()));
            ImGui.text("Axis: " + formatVector3d(axis));
            ImGui.text(String.format("Length: %.2f", axis.length()));
            ImGui.text(String.format("Radius: %.2f", cylinder.getRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer HEMISPHERE_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            HemisphereGeometryData hemisphere = (HemisphereGeometryData) prop.getter.invoke(node);
            if (hemisphere == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(hemisphere.getCenter()));
            ImGui.text("Axis: " + formatVector3d(hemisphere.getAxis()));
            ImGui.text(String.format("Radius: %.2f", hemisphere.getRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer ICOSAHEDRON_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            IcosahedronGeometryData icosa = (IcosahedronGeometryData) prop.getter.invoke(node);
            if (icosa == null) {
                ImGui.textDisabled("(绌?");
                return;
            }
            ImGui.text("Center: " + formatVector3d(icosa.getCenter()));
            ImGui.text(String.format("Edge Length: %.2f", icosa.getEdgeLength()));
            ImGui.text(String.format("Circumradius: %.2f", icosa.getCircumradius()));
            ImGui.text("Vertices: " + icosa.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer DODECAHEDRON_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            DodecahedronGeometryData dod = (DodecahedronGeometryData) prop.getter.invoke(node);
            if (dod == null) {
                ImGui.textDisabled("(绌?");
                return;
            }
            ImGui.text("Center: " + formatVector3d(dod.getCenter()));
            ImGui.text(String.format("Edge Length: %.2f", dod.getEdgeLength()));
            ImGui.text(String.format("Circumradius: %.2f", dod.getCircumradius()));
            ImGui.text("Vertices: " + dod.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer ELLIPSOID_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            EllipsoidGeometryData ellipsoid = (EllipsoidGeometryData) prop.getter.invoke(node);
            if (ellipsoid == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(ellipsoid.getCenter()));
            ImGui.text("Radii: " + formatVector3d(ellipsoid.getRadii()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer OCTAHEDRON_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            OctahedronGeometryData octahedron = (OctahedronGeometryData) prop.getter.invoke(node);
            if (octahedron == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(octahedron.getCenter()));
            ImGui.text(String.format("Vertex Radius: %.2f", octahedron.getVertexRadius()));
            ImGui.text("Vertices: " + octahedron.getVertices().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer TORUS_GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            TorusGeometryData torus = (TorusGeometryData) prop.getter.invoke(node);
            if (torus == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(torus.getCenter()));
            ImGui.text("Axis: " + formatVector3d(torus.getAxis()));
            ImGui.text(String.format("Major Radius: %.2f", torus.getMajorRadius()));
            ImGui.text(String.format("Minor Radius: %.2f", torus.getMinorRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer VECTOR3D_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(宸茬鐢?");
            return;
        }

        try {
            Vector3d vec = (Vector3d) prop.getter.invoke(node);
            if (vec == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_vector3d");
            float[] values = (float[]) panel.tempValues.computeIfAbsent(
                    tempKey, k -> new float[]{(float) vec.x, (float) vec.y, (float) vec.z});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = (float) vec.x;
                values[1] = (float) vec.y;
                values[2] = (float) vec.z;
            }

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed = false;
            float[] xValue = {values[0]};
            float[] yValue = {values[1]};
            float[] zValue = {values[2]};
            changed |= ImGui.dragFloat("X##" + prop.name, xValue, 0.01f);
            values[0] = xValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragFloat("Y##" + prop.name, yValue, 0.01f);
            values[1] = yValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragFloat("Z##" + prop.name, zValue, 0.01f);
            values[2] = zValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);
            if (isReadOnly) ImGui.endDisabled();

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new Vector3d(values[0], values[1], values[2]));
            }

            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer BLOCK_POS_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(宸茬鐢?");
            return;
        }

        try {
            BlockPos pos = (BlockPos) prop.getter.invoke(node);
            if (pos == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_blockpos");
            int[] values = (int[]) panel.tempValues.computeIfAbsent(
                    tempKey, k -> new int[]{pos.getX(), pos.getY(), pos.getZ()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = pos.getX();
                values[1] = pos.getY();
                values[2] = pos.getZ();
            }

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed = false;
            int[] xValue = {values[0]};
            int[] yValue = {values[1]};
            int[] zValue = {values[2]};
            changed |= ImGui.dragInt("X##" + prop.name, xValue, 1);
            values[0] = xValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragInt("Y##" + prop.name, yValue, 1);
            values[1] = yValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            changed |= ImGui.dragInt("Z##" + prop.name, zValue, 1);
            values[2] = zValue[0];
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);
            if (isReadOnly) ImGui.endDisabled();

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new BlockPos(values[0], values[1], values[2]));
            }

            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer COLOR_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(宸茬鐢?");
            return;
        }

        try {
            ColorData color = (ColorData) prop.getter.invoke(node);
            if (color == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_color");
            float[] values = (float[]) panel.tempValues.computeIfAbsent(
                    tempKey, k -> new float[]{color.r(), color.g(), color.b(), color.a()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = color.r();
                values[1] = color.g();
                values[2] = color.b();
                values[3] = color.a();
            }

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed = ImGui.colorEdit4("##" + prop.name, values);
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);
            if (isReadOnly) ImGui.endDisabled();

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new ColorData(values[0], values[1], values[2], values[3]));
            }

            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer NODE_COLOR_RENDERER = (panel, node, prop, isDisabled) -> {
        if (isDisabled) {
            ImGui.textDisabled("(宸茬鐢?");
            return;
        }

        try {
            Color color = (Color) prop.getter.invoke(node);
            if (color == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String tempKey = panel.getTempValueKey(node, prop.name + "_node_color");
            float[] values = (float[]) panel.tempValues.computeIfAbsent(
                    tempKey, k -> new float[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                values[0] = color.getRed();
                values[1] = color.getGreen();
                values[2] = color.getBlue();
                values[3] = color.getAlpha();
            }

            if (isReadOnly) ImGui.beginDisabled();
            boolean changed = ImGui.colorEdit4("##" + prop.name, values);
            if (ImGui.isItemActive()) panel.markPropertyBeingEdited(node, prop.name);
            if (ImGui.isItemDeactivated()) panel.markPropertyEditingFinished(node, prop.name);
            if (isReadOnly) ImGui.endDisabled();

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new Color(values[0], values[1], values[2], values[3]));
            }

            panel.errorCounts.remove(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer POINT_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            PointData point = (PointData) prop.getter.invoke(node);
            if (point == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Position: " + formatVector3d(point.getPosition()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer BOUNDING_BOX_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            BoundingBoxData box = (BoundingBoxData) prop.getter.invoke(node);
            if (box == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Min: " + formatVector3d(box.getMin()));
            ImGui.text("Max: " + formatVector3d(box.getMax()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer SPHERE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            SphereData sphere = (SphereData) prop.getter.invoke(node);
            if (sphere == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Center: " + formatVector3d(sphere.getCenter()));
            ImGui.text(String.format("Radius: %.2f", sphere.getRadius()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer LINE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            LineData line = (LineData) prop.getter.invoke(node);
            if (line == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Start: " + formatVec3d(line.getStart()));
            ImGui.text("End: " + formatVec3d(line.getEnd()));
            ImGui.text("Direction: " + formatVec3d(line.getDirection()));
            ImGui.text(String.format("Length: %.2f", line.getLength()));
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer GEOMETRY_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            Object value = prop.getter.invoke(node);
            if (value == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            PropertyRenderer fallbackGeometryRenderer = panel.getRendererForType(GeometryData.class);
            PropertyRenderer delegate = panel.getRendererForType(value.getClass());
            if (delegate != null
                    && delegate != fallbackGeometryRenderer
                    && !isGeometryRendererSelfReference(delegate)) {
                delegate.render(panel, node, prop, isDisabled);
                return;
            }

            ImGui.text("Type: " + value.getClass().getSimpleName());
            ImGui.textWrapped(value.toString());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer CURVE_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            Curve curve = (Curve) prop.getter.invoke(node);
            if (curve == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Type: " + curve.getCurveType());
            ImGui.text("Control Points: " + curve.size());
            ImGui.text("Resolution: " + curve.getResolution());
            ImGui.text("Sample Points: " + curve.getSamplePoints().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static boolean isGeometryRendererSelfReference(PropertyRenderer renderer) {
        return renderer == GEOMETRY_RENDERER;
    }

    private static final PropertyRenderer BLOCK_POS_LIST_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            BlockPosList positions = (BlockPosList) prop.getter.invoke(node);
            if (positions == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Count: " + positions.size());
            List<BlockPos> preview = positions.getPositions();
            if (!preview.isEmpty()) {
                ImGui.text("First: " + formatBlockPos(preview.getFirst()));
            }
            if (preview.size() > 1) {
                ImGui.text("Last: " + formatBlockPos(preview.getLast()));
            }
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

    private static final PropertyRenderer PLANT_BLOCK_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            PlantStructure.PlantBlock block = (PlantStructure.PlantBlock) prop.getter.invoke(node);
            if (block == null) {
                ImGui.textDisabled("(绌?");
                return;
            }

            ImGui.text("Position: " + formatBlockPos(block.getPosition()));
            ImGui.text("Block Type: " + block.getBlockType());
            ImGui.text(String.format("Thickness: %.2f", block.getThickness()));
            ImGui.text("Properties: " + block.getProperties().size());
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    };

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

    private void handlePropertyError(PropertyDescriptor prop, Throwable e) { // 统一捕获 Throwable
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
        saveAiSettingsToDisk();
        if (aiRemotePlanFuture != null && !aiRemotePlanFuture.isDone()) {
            aiRemotePlanFuture.cancel(true);
        }
        aiRemotePlanFuture = null;
        aiRemotePendingPrompt = "";
        aiLastRemoteRawResponse = "";
        aiLastRemoteModelText = "";
        aiLastRemoteRequestSnapshot = "";
        aiLastRemoteErrorCategory = "";
        aiLastRemoteErrorMessage = "";
        aiLastRemoteStatusCode = 0;
        aiLastRemoteAttempts = 0;
        // 移除未保存更改相关的清理
        selectedNode = null;
        aiChatMessages.clear();
        aiPromptInput.clear();
        aiApiKey.clear();
        pendingAiPlan = null;
        lastAiUndoStepCount = 0;
        aiPlanStatusMessage = "";
        aiSettingsStatusMessage = "";
    }

    @Override
    public void render(float x, float y, float width, float height, float windowPaddingX, float windowPaddingY) {
        if (!visible) return;

        float baseScrollbarSize = ImGui.getStyle().getScrollbarSize();
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ScrollbarSize, baseScrollbarSize);
        try {
            checkAndCleanExpiredEditLocks();

            ImGui.text("Inspector");
            ImGui.separator();

            if (ImGui.beginTabBar("rightPanelTabs")) {
                if (ImGui.beginTabItem("Properties")) {
                    activeTab = RightPanelTab.PROPERTIES;
                    renderPropertiesTabContent();
                    ImGui.endTabItem();
                }

                if (ImGui.beginTabItem("AI Assistant")) {
                    activeTab = RightPanelTab.AI_ASSISTANT;
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

    private void renderAiAssistantTabContent() {
        pollRemotePlannerResultIfReady();

        ImGui.textWrapped("Describe what you want to build, and AI will generate a node graph plan.");
        if (ImGui.smallButton("AI Settings")) {
            ImGui.openPopup("AI Settings");
        }
        renderAiSettingsPopup();
        ImGui.sameLine();
        ImGui.textDisabled(buildAiSettingsSummary());

        if (aiSettingsStatusMessage != null && !aiSettingsStatusMessage.isBlank()) {
            ImGui.textWrapped(aiSettingsStatusMessage);
        }

        if (hasAiDebugData() && ImGui.smallButton("Open Debug Console")) {
            ImGui.openPopup("AI Debug Console");
        }
        renderAiDebugConsolePopup();

        if (isRemotePlannerBusy()) {
            ImGui.textColored(0.95f, 0.78f, 0.30f, 1.0f, "AI is generating plan...");
            ImGui.sameLine();
            if (ImGui.smallButton("Cancel")) {
                cancelRemotePlannerRequest();
            }
        }

        ImGui.checkbox("Use current selection as context", aiUseSelectionContext);
        ImGui.checkbox("Include current canvas graph summary", aiIncludeGraphContext);
        ImGui.checkbox("Preview-only mode (do not mutate graph)", aiPreviewOnlyMode);
        ImGui.checkbox("Patch apply mode (reuse matching nodes)", aiPatchApplyMode);
        if (aiPatchApplyMode.get()) {
            ImGui.checkbox("Patch remove scoped stale connections", aiPatchRemoveScopedConnections);
        }

        if (aiUseSelectionContext.get()) {
            ImGui.separator();
            if (selectedNode != null) {
                ImGui.textColored(0.45f, 0.85f, 0.55f, 1.0f,
                        "Context: Selected node = " + selectedNode.getDisplayName());
                ImGui.textDisabled("Type ID: " + selectedNode.getTypeId());
            } else {
                ImGui.textDisabled("Context: No node selected");
            }
        }

        ImGui.separator();
        ImGui.text("Quick prompts:");
        if (ImGui.smallButton("Generate from selection")) {
            setAiPrompt("Generate a node graph based on current selection and keep existing style.");
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Optimize selected graph")) {
            setAiPrompt("Optimize selected node graph for readability and performance.");
        }
        if (ImGui.smallButton("Explain current node")) {
            setAiPrompt("Explain what the selected node does and how to connect it.");
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Mobius ring example")) {
            setAiPrompt("Build a parametrized Mobius ring above selected position with radius/width/thickness controls.");
        }

        renderAiPlanPreviewSection();

        float inputBlockHeight = ImGui.getFrameHeightWithSpacing() * 3.2f;
        float historyHeight = Math.max(120.0f, ImGui.getContentRegionAvailY() - inputBlockHeight);

        if (ImGui.beginChild("aiChatHistory", 0.0f, historyHeight, true)) {
            if (aiChatMessages.isEmpty()) {
                ImGui.textDisabled("No messages yet.");
                ImGui.textDisabled("Tip: Ask AI to create or modify a node graph.");
            } else {
                for (AiChatMessage message : aiChatMessages) {
                    boolean isUser = "user".equals(message.role());
                    ImGui.textColored(
                            isUser ? 0.45f : 0.65f,
                            isUser ? 0.75f : 0.85f,
                            isUser ? 1.0f : 0.55f,
                            1.0f,
                            isUser ? "You" : "AI");
                    ImGui.sameLine();
                    ImGui.textWrapped(message.content());
                    ImGui.spacing();
                }
                ImGui.setScrollHereY(1.0f);
            }
        }
        ImGui.endChild();

        if (isRemotePlannerBusy()) ImGui.beginDisabled();
        ImGui.pushItemWidth(-80.0f);
        boolean submitByEnter = ImGui.inputText("##ai_prompt", aiPromptInput, ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.popItemWidth();
        ImGui.sameLine();
        if (ImGui.button("Send") || submitByEnter) {
            submitAiPrompt();
        }
        if (isRemotePlannerBusy()) ImGui.endDisabled();

        ImGui.textDisabled(aiEnableRemotePlanner.get()
                ? "Current mode: remote planner + DSL validation + apply/undo"
                : "Current mode: local mock planner + DSL validation + apply/undo");
    }

    private void renderAiSettingsPopup() {
        int flags = ImGuiWindowFlags.AlwaysAutoResize;
        if (!ImGui.beginPopupModal("AI Settings", flags)) {
            return;
        }

        ImGui.text("Remote Planner Connection");
        ImGui.separator();

        ImGui.checkbox("Enable remote planner", aiEnableRemotePlanner);

        ImGui.text("API Base URL");
        ImGui.pushItemWidth(420.0f);
        ImGui.inputText("##ai_api_base_url", aiApiBaseUrl);
        ImGui.popItemWidth();

        ImGui.text("API Key");
        int keyFlags = aiShowApiKey.get() ? ImGuiInputTextFlags.None : ImGuiInputTextFlags.Password;
        ImGui.pushItemWidth(420.0f);
        ImGui.inputText("##ai_api_key", aiApiKey, keyFlags);
        ImGui.popItemWidth();
        ImGui.checkbox("Show API key", aiShowApiKey);

        ImGui.text("Model");
        ImGui.pushItemWidth(240.0f);
        ImGui.inputText("##ai_model", aiModel);
        ImGui.popItemWidth();

        ImGui.text("Request Timeout (seconds)");
        ImGui.pushItemWidth(120.0f);
        if (ImGui.inputInt("##ai_timeout_seconds", aiRequestTimeoutSeconds)) {
            aiRequestTimeoutSeconds.set(Math.max(5, Math.min(600, aiRequestTimeoutSeconds.get())));
        }
        ImGui.popItemWidth();

        ImGui.checkbox("Auto layout before apply", aiAutoLayoutBeforeApply);

        ImGui.text("System Prompt");
        ImGui.pushItemWidth(420.0f);
        ImGui.inputTextMultiline("##ai_system_prompt", aiSystemPrompt, 420.0f, 100.0f);
        ImGui.popItemWidth();

        ImGui.separator();
        if (ImGui.button("Validate (Local)")) {
            aiSettingsStatusMessage = validateAiSettings();
        }
        ImGui.sameLine();
        if (ImGui.button("Save Settings")) {
            saveAiSettingsToDisk();
        }
        ImGui.sameLine();
        if (ImGui.button("Reload Settings")) {
            loadAiSettingsFromDisk();
            aiSettingsStatusMessage = "AI settings reloaded from disk.";
        }
        ImGui.sameLine();
        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        ImGui.textDisabled("Config file: " + aiSettingsPath.toAbsolutePath());
        ImGui.textDisabled("Settings are persisted to disk and loaded on startup.");

        ImGui.endPopup();
    }

    private void renderAiDebugConsolePopup() {
        int flags = ImGuiWindowFlags.AlwaysAutoResize;
        if (!ImGui.beginPopupModal("AI Debug Console", flags)) {
            return;
        }

        renderAiFailureSummaryCard();
        ImGui.spacing();

        ImGui.textDisabled("Category: " + (aiLastRemoteErrorCategory == null || aiLastRemoteErrorCategory.isBlank()
                ? "none"
                : aiLastRemoteErrorCategory)
                + " | Attempts: " + aiLastRemoteAttempts);
        ImGui.separator();

        float width = Math.max(620.0f, ImGui.getContentRegionAvailX());
        if (ImGui.beginTabBar("aiDebugConsoleTabs")) {
            if (ImGui.beginTabItem("Raw Response")) {
                if (ImGui.beginChild("aiDebugRawBody", width, 280.0f, true)) {
                    if (aiLastRemoteRawResponse == null || aiLastRemoteRawResponse.isBlank()) {
                        ImGui.textDisabled("No raw response available.");
                    } else {
                        ImGui.textWrapped(aiLastRemoteRawResponse);
                    }
                }
                ImGui.endChild();
                if (ImGui.button("Copy Raw Response")) {
                    copyToClipboard(aiLastRemoteRawResponse == null ? "" : aiLastRemoteRawResponse);
                    aiPlanStatusMessage = "Raw response copied to clipboard.";
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Model Text")) {
                if (ImGui.beginChild("aiDebugModelTextBody", width, 280.0f, true)) {
                    if (aiLastRemoteModelText == null || aiLastRemoteModelText.isBlank()) {
                        ImGui.textDisabled("No extracted model text available.");
                    } else {
                        ImGui.textWrapped(aiLastRemoteModelText);
                    }
                }
                ImGui.endChild();
                if (ImGui.button("Copy Model Text")) {
                    copyToClipboard(aiLastRemoteModelText == null ? "" : aiLastRemoteModelText);
                    aiPlanStatusMessage = "Model text copied to clipboard.";
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Request Snapshot")) {
                ImGui.textDisabled("API key is masked for safety.");
                if (ImGui.beginChild("aiDebugRequestSnapshotBody", width, 260.0f, true)) {
                    if (aiLastRemoteRequestSnapshot == null || aiLastRemoteRequestSnapshot.isBlank()) {
                        ImGui.textDisabled("No request snapshot available.");
                    } else {
                        ImGui.textWrapped(aiLastRemoteRequestSnapshot);
                    }
                }
                ImGui.endChild();
                if (ImGui.button("Copy Request Snapshot")) {
                    copyToClipboard(aiLastRemoteRequestSnapshot == null ? "" : aiLastRemoteRequestSnapshot);
                    aiPlanStatusMessage = "Request snapshot copied to clipboard.";
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Export")) {
                String compactDiagnostics = buildAiDiagnosticsExportText(false);
                String fullDiagnostics = buildAiDiagnosticsExportText(true);
                if (ImGui.beginChild("aiDebugExportBody", width, 260.0f, true)) {
                    ImGui.textDisabled("Preview (compact):");
                    ImGui.textWrapped(compactDiagnostics);
                }
                ImGui.endChild();
                if (ImGui.button("Copy Compact Export")) {
                    copyToClipboard(compactDiagnostics);
                    aiPlanStatusMessage = "Compact diagnostics exported to clipboard.";
                }
                ImGui.sameLine();
                if (ImGui.button("Copy Full Export")) {
                    copyToClipboard(fullDiagnostics);
                    aiPlanStatusMessage = "Full diagnostics exported to clipboard.";
                }
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }

        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }

    private boolean hasAiDebugData() {
        return (aiLastRemoteRawResponse != null && !aiLastRemoteRawResponse.isBlank())
                || (aiLastRemoteModelText != null && !aiLastRemoteModelText.isBlank())
                || (aiLastRemoteRequestSnapshot != null && !aiLastRemoteRequestSnapshot.isBlank())
                || (aiLastRemoteErrorMessage != null && !aiLastRemoteErrorMessage.isBlank());
    }

    private void renderAiFailureSummaryCard() {
        if (aiLastRemoteErrorCategory == null || aiLastRemoteErrorCategory.isBlank() || "none".equals(aiLastRemoteErrorCategory)) {
            ImGui.textDisabled("No remote failure summary available.");
            return;
        }

        ImGui.text("Failure Summary");
        ImGui.separator();
        ImGui.textWrapped("Category: " + aiLastRemoteErrorCategory
                + " | Status: " + aiLastRemoteStatusCode
                + " | Attempts: " + aiLastRemoteAttempts);
        if (aiLastRemoteErrorMessage != null && !aiLastRemoteErrorMessage.isBlank()) {
            ImGui.textWrapped("Message: " + aiLastRemoteErrorMessage);
        }
        String suggestion = buildRemoteFailureSuggestion(aiLastRemoteErrorCategory);
        if (!suggestion.isBlank()) {
            ImGui.textWrapped("Suggested action: " + suggestion);
        }

        ImGui.spacing();
        renderAiFailureRecoveryActions();
    }

    private void renderAiFailureRecoveryActions() {
        boolean allowTimeoutAction = "timeout".equals(aiLastRemoteErrorCategory)
                || "server".equals(aiLastRemoteErrorCategory)
                || "rate-limit".equals(aiLastRemoteErrorCategory)
                || "network".equals(aiLastRemoteErrorCategory)
                || "request".equals(aiLastRemoteErrorCategory)
                || "response-format".equals(aiLastRemoteErrorCategory);
        boolean allowSettingsAction = "auth".equals(aiLastRemoteErrorCategory)
                || "request".equals(aiLastRemoteErrorCategory)
                || "timeout".equals(aiLastRemoteErrorCategory)
                || "response-format".equals(aiLastRemoteErrorCategory);
        boolean allowModeAction = "network".equals(aiLastRemoteErrorCategory)
                || "auth".equals(aiLastRemoteErrorCategory)
                || "request".equals(aiLastRemoteErrorCategory)
                || "server".equals(aiLastRemoteErrorCategory);

        if (aiLastSubmittedPrompt != null && !aiLastSubmittedPrompt.isBlank() && !isRemotePlannerBusy()) {
            if (ImGui.smallButton("Retry Last Request")) {
                retryLastAiRequest();
            }
            ImGui.sameLine();
        }

        if (allowTimeoutAction) {
            if (ImGui.smallButton("Increase Timeout +30s")) {
                increaseAiTimeoutSeconds(30);
            }
            ImGui.sameLine();
        }

        if (allowModeAction) {
            if (ImGui.smallButton(aiEnableRemotePlanner.get() ? "Use Local Planner" : "Use Remote Planner")) {
                aiEnableRemotePlanner.set(!aiEnableRemotePlanner.get());
                saveAiSettingsToDisk();
                aiSettingsStatusMessage = aiEnableRemotePlanner.get()
                        ? "Remote planner re-enabled."
                        : "Switched to local planner for the next request.";
            }
            ImGui.sameLine();
        }

        if (allowSettingsAction) {
            if (ImGui.smallButton("Open AI Settings")) {
                ImGui.openPopup("AI Settings");
            }
            ImGui.sameLine();
        }

        if (ImGui.smallButton("Resave Settings")) {
            saveAiSettingsToDisk();
            aiSettingsStatusMessage = "AI settings saved.";
        }
    }

    private String buildRemoteFailureSuggestion(String category) {
        return switch (category) {
            case "auth" -> "Verify API key, organization/project scope, and endpoint authorization policy.";
            case "rate-limit" -> "Reduce request frequency, switch to a lower-cost model, or increase provider quota.";
            case "timeout" -> "Increase timeout in AI settings and reduce prompt/schema size for this request.";
            case "network" -> "Check connectivity, proxy/firewall rules, and whether the base URL is reachable.";
            case "server" -> "Retry later or switch provider/model temporarily if 5xx errors persist.";
            case "request" -> "Check model name, endpoint path, and payload constraints for this provider.";
            case "response-format" -> "Inspect raw response and tighten system prompt to force strict JSON output.";
            case "canceled" -> "No action required; resend request when ready.";
            default -> "Open raw response and request snapshot for manual diagnosis.";
        };
    }

    private void increaseAiTimeoutSeconds(int deltaSeconds) {
        int current = aiRequestTimeoutSeconds.get();
        int updated = Math.max(5, Math.min(600, current + deltaSeconds));
        aiRequestTimeoutSeconds.set(updated);
        saveAiSettingsToDisk();
        aiSettingsStatusMessage = "AI timeout increased to " + updated + " seconds.";
    }

    private String buildAiDiagnosticsExportText(boolean includeFullPayloads) {

        return "[AI Debug Diagnostics]\n" +
                "category: " + nullToEmpty(aiLastRemoteErrorCategory) + "\n" +
                "statusCode: " + aiLastRemoteStatusCode + "\n" +
                "attempts: " + aiLastRemoteAttempts + "\n" +
                "errorMessage: " + nullToEmpty(aiLastRemoteErrorMessage) + "\n" +
                "statusMessage: " + nullToEmpty(aiPlanStatusMessage) + "\n\n" +
                "[Request Snapshot]\n" +
                formatDiagnosticsSection(aiLastRemoteRequestSnapshot, includeFullPayloads) + "\n" +
                "[Model Text]\n" +
                formatDiagnosticsSection(aiLastRemoteModelText, includeFullPayloads) + "\n" +
                "[Raw Response]\n" +
                formatDiagnosticsSection(aiLastRemoteRawResponse, includeFullPayloads) + "\n";
    }

    private String formatDiagnosticsSection(String value, boolean includeFullPayloads) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        if (includeFullPayloads) {
            return value;
        }
        return truncateForDiagnostics(value, 900);
    }

    private String truncateForDiagnostics(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n...[truncated, total chars=" + value.length() + "]";
    }

    private String validateAiSettings() {
        return AiSettingsStore.validate(collectAiSettingsData());
    }

    private String buildAiSettingsSummary() {
        return AiSettingsStore.buildSummary(collectAiSettingsData());
    }

    private Path resolveAiSettingsPath() {
        return AiSettingsStore.resolveSettingsPath();
    }

    private void loadAiSettingsFromDisk() {
        AiSettingsStore.LoadResult result = AiSettingsStore.load(aiSettingsPath);
        applyAiSettingsData(result.data());
        aiSettingsStatusMessage = result.statusMessage();
    }

    private void saveAiSettingsToDisk() {
        aiSettingsStatusMessage = AiSettingsStore.save(aiSettingsPath, collectAiSettingsData());
    }

    private AiSettingsStore.AiSettingsData collectAiSettingsData() {
        return new AiSettingsStore.AiSettingsData(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
                aiSystemPrompt.get(),
                aiRequestTimeoutSeconds.get(),
                aiShowApiKey.get(),
                aiEnableRemotePlanner.get(),
                aiAutoLayoutBeforeApply.get(),
                aiIncludeGraphContext.get(),
                aiPreviewOnlyMode.get(),
                aiPatchApplyMode.get(),
                aiPatchRemoveScopedConnections.get()
        );
    }

    private void applyAiSettingsData(AiSettingsStore.AiSettingsData data) {
        if (data == null) {
            return;
        }
        aiApiBaseUrl.set(data.apiBaseUrl());
        aiApiKey.set(data.apiKey());
        aiModel.set(data.model());
        aiSystemPrompt.set(data.systemPrompt());
        aiRequestTimeoutSeconds.set(data.timeoutSeconds());
        aiShowApiKey.set(data.showApiKey());
        aiEnableRemotePlanner.set(data.enableRemotePlanner());
        aiAutoLayoutBeforeApply.set(data.autoLayoutBeforeApply());
        aiIncludeGraphContext.set(data.includeGraphContext());
        aiPreviewOnlyMode.set(data.previewOnlyMode());
        aiPatchApplyMode.set(data.patchApplyMode());
        aiPatchRemoveScopedConnections.set(data.patchRemoveScopedConnections());
    }

    private void renderAiPlanPreviewSection() {
        ImGui.separator();
        ImGui.text("Plan Preview");

        if (pendingAiPlan == null) {
            ImGui.textDisabled("No plan yet. Send a prompt to generate a plan.");
            return;
        }

        ImGui.textWrapped(pendingAiPlan.summary());
        ImGui.text("Nodes: " + pendingAiPlan.nodes().size() + "  Connections: " + pendingAiPlan.connections().size());

        if (!pendingAiPlan.isValid()) {
            ImGui.textColored(1.0f, 0.45f, 0.35f, 1.0f, "Validation errors:");
            for (String error : pendingAiPlan.validationErrors()) {
                ImGui.bulletText(error);
            }
        }

        if (ImGui.treeNode("Planned Nodes")) {
            for (AiPlanNode node : pendingAiPlan.nodes()) {
                ImGui.bulletText(node.ref() + " -> " + node.typeId()
                        + "  (" + String.format(java.util.Locale.ROOT, "%.0f", node.offsetX())
                        + ", " + String.format(java.util.Locale.ROOT, "%.0f", node.offsetY()) + ")");
            }
            ImGui.treePop();
        }

        if (ImGui.treeNode("Planned Connections")) {
            if (pendingAiPlan.connections().isEmpty()) {
                ImGui.textDisabled("None");
            } else {
                for (AiPlanConnection connection : pendingAiPlan.connections()) {
                    ImGui.bulletText(connection.sourceRef() + "." + connection.sourcePortId()
                            + " -> " + connection.targetRef() + "." + connection.targetPortId());
                }
            }
            ImGui.treePop();
        }

        AiGraphDiffService.GraphDiffSummary diff = buildGraphDiffSummary(pendingAiPlan);
        if (ImGui.treeNode("Graph Diff (Heuristic)")) {
            ImGui.textDisabled("Compared by node type+params signature and typed connection signature.");
            ImGui.text("Potential additions: nodes=" + diff.nodeAdditions() + ", connections=" + diff.connectionAdditions());
            ImGui.text("Potential missing from plan: nodes=" + diff.nodeMissingFromPlan() + ", connections=" + diff.connectionMissingFromPlan());

            renderDiffSamples("Node additions", diff.nodeAdditionSamples());
            renderDiffSamples("Node missing from plan", diff.nodeMissingSamples());
            renderDiffSamples("Connection additions", diff.connectionAdditionSamples());
            renderDiffSamples("Connection missing from plan", diff.connectionMissingSamples());

            ImGui.treePop();
        }

    AiGraphDiffService.MappedDiffSummary mappedDiff = buildMappedDiffSummary(pendingAiPlan);
    if (ImGui.treeNode("Mapped Diff (Preview)")) {
        ImGui.textDisabled("Greedy matching by type+params, then type fallback. Estimates reusable vs new nodes.");
        ImGui.text("Reusable matches=" + mappedDiff.reusableNodeMatches()
            + ", new nodes=" + mappedDiff.newNodesToCreate());
        ImGui.text("Unchanged reused=" + mappedDiff.unchangedReusableNodes()
            + ", param updates=" + mappedDiff.paramUpdateCandidates());
        ImGui.text("Connection additions=" + mappedDiff.connectionAdditions()
            + ", connection removal candidates=" + mappedDiff.connectionRemovalCandidates()
            + ", incoming replacements=" + mappedDiff.incomingReplacementCandidates());

        renderDiffSamples("Node reuse matches", mappedDiff.nodeReuseSamples());
        renderDiffSamples("Node creation candidates", mappedDiff.nodeCreationSamples());
        renderDiffSamples("Param update candidates", mappedDiff.paramUpdateSamples());
        renderDiffSamples("Connection additions", mappedDiff.connectionAdditionSamples());
        renderDiffSamples("Connection removal candidates", mappedDiff.connectionRemovalSamples());
        renderDiffSamples("Incoming replacement candidates", mappedDiff.incomingReplacementSamples());

        ImGui.treePop();
    }

        boolean canApply = pendingAiPlan.isValid() && !pendingAiPlan.nodes().isEmpty();
        if (!canApply) ImGui.beginDisabled();
        if (ImGui.button("Apply Plan")) {
            if (aiPreviewOnlyMode.get()) {
                runDryRunForPendingPlan();
            } else {
                applyPendingAiPlan();
            }
        }
        if (!canApply) ImGui.endDisabled();

        ImGui.sameLine();
        if (!canApply) ImGui.beginDisabled();
        if (ImGui.button("Dry Run Report")) {
            runDryRunForPendingPlan();
        }
        if (!canApply) ImGui.endDisabled();

        ImGui.sameLine();
        boolean canUndo = lastAiUndoStepCount > 0;
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button("Undo Last AI Apply")) {
            undoLastAiApply();
        }
        if (!canUndo) ImGui.endDisabled();

        if (aiPlanStatusMessage != null && !aiPlanStatusMessage.isBlank()) {
            ImGui.textWrapped(aiPlanStatusMessage);
        }
    }

    private void runDryRunForPendingPlan() {
        if (pendingAiPlan == null) {
            aiPlanStatusMessage = "Dry run aborted: no plan available.";
            return;
        }
        if (!pendingAiPlan.isValid()) {
            aiPlanStatusMessage = "Dry run aborted: plan has validation errors.";
            return;
        }

        AiGraphDiffService.GraphDiffSummary heuristic = buildGraphDiffSummary(pendingAiPlan);
        AiGraphDiffService.MappedDiffSummary mapped = buildMappedDiffSummary(pendingAiPlan);

        StringBuilder report = new StringBuilder(512);
        report.append("Dry run only (no graph mutation). ")
                .append("Planned nodes=").append(pendingAiPlan.nodes().size())
                .append(", connections=").append(pendingAiPlan.connections().size()).append(". ")
                .append("Mapped: reusable=").append(mapped.reusableNodeMatches())
                .append(", new=").append(mapped.newNodesToCreate())
                .append(", paramUpdates=").append(mapped.paramUpdateCandidates())
                .append(", connAdd=").append(mapped.connectionAdditions())
                .append(", connRemoveCandidates=").append(mapped.connectionRemovalCandidates())
                .append(", incomingReplaceCandidates=").append(mapped.incomingReplacementCandidates())
                .append(". Heuristic missing: nodes=").append(heuristic.nodeMissingFromPlan())
                .append(", connections=").append(heuristic.connectionMissingFromPlan()).append(".");

            if (!mapped.incomingReplacementSamples().isEmpty()) {
                report.append(" Incoming replacements sample: ")
                    .append(String.join(" | ", mapped.incomingReplacementSamples().subList(0,
                        Math.min(3, mapped.incomingReplacementSamples().size()))))
                    .append(".");
            }

        String reportText = report.toString();
        aiPlanStatusMessage = reportText;
        aiChatMessages.add(new AiChatMessage("assistant", reportText, System.currentTimeMillis()));
    }

    private void renderDiffSamples(String title, List<String> samples) {
        if (!ImGui.treeNode(title)) {
            return;
        }
        if (samples == null || samples.isEmpty()) {
            ImGui.textDisabled("None");
        } else {
            for (String sample : samples) {
                ImGui.bulletText(sample);
            }
        }
        ImGui.treePop();
    }

    private AiGraphDiffService.GraphDiffSummary buildGraphDiffSummary(AiGraphPlan plan) {
        return AiGraphDiffService.buildGraphDiffSummary(toDiffGraphPlan(plan), getNodeGraph());
    }

    private AiGraphDiffService.MappedDiffSummary buildMappedDiffSummary(AiGraphPlan plan) {
        return AiGraphDiffService.buildMappedDiffSummary(toDiffGraphPlan(plan), getNodeGraph());
    }

    private AiGraphDiffService.GraphPlan toDiffGraphPlan(AiGraphPlan plan) {
        if (plan == null) {
            return new AiGraphDiffService.GraphPlan(List.of(), List.of());
        }

        List<AiGraphDiffService.PlanNode> nodes = new ArrayList<>();
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphDiffService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiGraphDiffService.PlanConnection> connections = new ArrayList<>();
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphDiffService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return new AiGraphDiffService.GraphPlan(nodes, connections);
    }

    private void setAiPrompt(String text) {
        if (text == null) {
            aiPromptInput.clear();
            return;
        }
        aiPromptInput.set(text);
    }

    private void submitAiPrompt() {
        String prompt = aiPromptInput.get();
        if (prompt == null || prompt.isBlank()) {
            return;
        }

        submitAiPromptWithText(prompt.trim());
        aiPromptInput.clear();
    }

    private void submitAiPromptWithText(String trimmedPrompt) {
        aiLastSubmittedPrompt = trimmedPrompt;
        aiChatMessages.add(new AiChatMessage("user", trimmedPrompt, System.currentTimeMillis()));

        if (aiEnableRemotePlanner.get()) {
            startRemotePlannerRequest(trimmedPrompt);
            return;
        }

        AiGraphPlan mockTemplatePlan = buildMockAiPlan(trimmedPrompt);
        String dslJson = buildDslJsonFromPlan(mockTemplatePlan);
        applyDslResponse(trimmedPrompt, dslJson, "local-template");
    }

    private void retryLastAiRequest() {
        if (aiLastSubmittedPrompt == null || aiLastSubmittedPrompt.isBlank()) {
            aiPlanStatusMessage = "No previous prompt is available to retry.";
            return;
        }

        if (isRemotePlannerBusy()) {
            aiPlanStatusMessage = "Remote planner is already running.";
            return;
        }

        submitAiPromptWithText(aiLastSubmittedPrompt);
        aiPlanStatusMessage = "Retrying last request...";
    }

    private void startRemotePlannerRequest(String userPrompt) {
        if (isRemotePlannerBusy()) {
            aiPlanStatusMessage = "Remote planner is already running.";
            return;
        }

        String validation = validateAiSettings();
        if (validation.startsWith("Validation failed")) {
            aiPlanStatusMessage = validation;
            aiChatMessages.add(new AiChatMessage("assistant", validation, System.currentTimeMillis()));
            return;
        }

        NodeRegistry registry = NodeRegistry.getInstance();
        List<AiNodeSchemaCatalog.NodeSchema> allSchemas = AiNodeSchemaCatalog.collectAll(registry);
        List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas = AiNodeSchemaCatalog.selectRelevant(allSchemas, userPrompt, 40);

        String systemPrompt = aiSystemPrompt.get();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = AiPromptBuilder.buildSystemPrompt(relevantSchemas);
        } else {
            systemPrompt = systemPrompt + "\n\n" + AiPromptBuilder.buildSystemPrompt(relevantSchemas);
        }

        String userPromptPayload = AiPromptBuilder.buildUserPrompt(
            userPrompt,
            AiPromptContextService.buildSelectionContextSummary(
                aiUseSelectionContext.get(),
                aiIncludeGraphContext.get(),
                selectedNode,
                getNodeGraph()
            )
        );
        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
                systemPrompt,
                aiRequestTimeoutSeconds.get()
        );

        aiRemotePendingPrompt = userPrompt;
        aiLastRemoteRawResponse = "";
        aiLastRemoteModelText = "";
        aiLastRemoteRequestSnapshot = buildRemoteRequestSnapshot(config, userPrompt, userPromptPayload, relevantSchemas.size());
        aiLastRemoteErrorCategory = "";
        aiLastRemoteErrorMessage = "";
        aiLastRemoteStatusCode = 0;
        aiLastRemoteAttempts = 0;
        aiPlanStatusMessage = "Remote planner request submitted...";
        aiRemotePlanFuture = aiRemotePlannerService.requestPlanAsync(config, userPromptPayload);
    }

    private void pollRemotePlannerResultIfReady() {
        if (aiRemotePlanFuture == null || !aiRemotePlanFuture.isDone()) {
            return;
        }

        AiRemotePlannerService.RemotePlanResult result;
        try {
            result = aiRemotePlanFuture.join();
        } catch (Exception e) {
            String error = "Remote planner failed: " + e.getMessage();
            aiPlanStatusMessage = error;
            aiChatMessages.add(new AiChatMessage("assistant", error, System.currentTimeMillis()));
            aiRemotePlanFuture = null;
            aiRemotePendingPrompt = "";
            return;
        }

        aiRemotePlanFuture = null;
        String prompt = aiRemotePendingPrompt;
        aiRemotePendingPrompt = "";
        aiLastRemoteAttempts = result.attempts();
        aiLastRemoteErrorCategory = result.errorCategory();
        aiLastRemoteErrorMessage = result.errorMessage() == null ? "" : result.errorMessage();
        aiLastRemoteStatusCode = result.statusCode();
        aiLastRemoteRawResponse = result.rawResponse() == null ? "" : result.rawResponse();
        aiLastRemoteModelText = result.modelContent() == null ? "" : result.modelContent();

        if (!result.success()) {
            String error = formatRemoteErrorMessage(result);
            aiPlanStatusMessage = error;
            aiChatMessages.add(new AiChatMessage("assistant", error, System.currentTimeMillis()));
            fallbackToLocalPlan(prompt, "remote request failed");
            return;
        }

        applyDslResponse(prompt, result.modelContent(), "remote");
    }

    private void applyDslResponse(String prompt, String dslOrModelResponse, String source) {
        AiGraphDslSupport.ParseValidationResult parsed =
                AiGraphDslSupport.parseAndValidate(dslOrModelResponse, NodeRegistry.getInstance());

        if (!parsed.isSuccess() || parsed.graph() == null) {
            pendingAiPlan = null;
            String errorMessage = "Plan JSON validation failed: " + String.join("; ", parsed.errors());
            aiChatMessages.add(new AiChatMessage("assistant", errorMessage, System.currentTimeMillis()));
            aiPlanStatusMessage = errorMessage;
            if ("remote".equals(source)) {
                fallbackToLocalPlan(prompt, "remote JSON invalid");
            }
            return;
        }

        pendingAiPlan = buildPlanFromDsl(parsed.graph());
        aiChatMessages.add(new AiChatMessage(
            "assistant",
            AiPromptContextService.buildAiPlanReply(
                prompt,
                source,
                aiUseSelectionContext.get(),
                selectedNode,
                pendingAiPlan.nodes().size(),
                pendingAiPlan.connections().size(),
                pendingAiPlan.isValid(),
                pendingAiPlan.validationErrors()
            ),
            System.currentTimeMillis()
        ));
        aiPlanStatusMessage = "Plan JSON validated (" + source + "). Review and click Apply Plan.";
    }

    private void fallbackToLocalPlan(String prompt, String reason) {
        AiGraphPlan localPlan = buildMockAiPlan(prompt);
        String localDslJson = buildDslJsonFromPlan(localPlan);
        AiGraphDslSupport.ParseValidationResult localParsed =
                AiGraphDslSupport.parseAndValidate(localDslJson, NodeRegistry.getInstance());

        if (!localParsed.isSuccess() || localParsed.graph() == null) {
            aiPlanStatusMessage = "Local fallback also failed: " + String.join("; ", localParsed.errors());
            aiChatMessages.add(new AiChatMessage("assistant", aiPlanStatusMessage, System.currentTimeMillis()));
            return;
        }

        pendingAiPlan = buildPlanFromDsl(localParsed.graph());
        aiPlanStatusMessage = "Remote planner fallback applied (" + reason + "). Review and click Apply Plan.";
        aiChatMessages.add(new AiChatMessage("assistant", aiPlanStatusMessage, System.currentTimeMillis()));
    }

    private boolean isRemotePlannerBusy() {
        return aiRemotePlanFuture != null && !aiRemotePlanFuture.isDone();
    }

    private void cancelRemotePlannerRequest() {
        if (aiRemotePlanFuture != null && !aiRemotePlanFuture.isDone()) {
            aiRemotePlanFuture.cancel(true);
        }
        aiRemotePlanFuture = null;
        aiRemotePendingPrompt = "";
        aiPlanStatusMessage = "Remote planner request canceled.";
        aiChatMessages.add(new AiChatMessage("assistant", aiPlanStatusMessage, System.currentTimeMillis()));
    }

    private String formatRemoteErrorMessage(AiRemotePlannerService.RemotePlanResult result) {
        String category = result.errorCategory();
        String headline = switch (category) {
            case "auth" -> "Remote planner auth failed. Please check API key and permissions.";
            case "rate-limit" -> "Remote planner rate-limited. Please retry shortly or reduce request frequency.";
            case "timeout" -> "Remote planner timed out. Increase timeout or retry.";
            case "network" -> "Remote planner network error. Check connectivity and endpoint.";
            case "server" -> "Remote planner service error. Server returned 5xx.";
            case "request" -> "Remote planner rejected the request. Check model/base URL/payload.";
            case "response-format" -> "Remote planner returned an unexpected response format.";
            case "canceled" -> "Remote planner request canceled.";
            default -> "Remote planner failed.";
        };

        String detail = result.errorMessage() == null ? "" : result.errorMessage();
        String attemptInfo = result.attempts() > 1 ? " (retried " + result.attempts() + " times)" : "";
        return headline + attemptInfo + (detail.isBlank() ? "" : " Detail: " + detail);
    }

    private String buildRemoteRequestSnapshot(
            AiRemotePlannerService.PlannerConfig config,
            String userPrompt,
            String userPromptPayload,
            int schemaCount
    ) {
        return "baseUrl: " + nullToEmpty(config.apiBaseUrl()) + "\n" +
                "apiKeyMasked: " + maskSecret(config.apiKey()) + "\n" +
                "model: " + nullToEmpty(config.model()) + "\n" +
                "timeoutSeconds: " + config.timeoutSeconds() + "\n" +
                "selectionContextEnabled: " + aiUseSelectionContext.get() + "\n" +
                "schemaCountInjected: " + schemaCount + "\n" +
                "systemPromptLength: " + (config.systemPrompt() == null ? 0 : config.systemPrompt().length()) + "\n" +
                "userPromptLength: " + (userPrompt == null ? 0 : userPrompt.length()) + "\n" +
                "payloadLength: " + (userPromptPayload == null ? 0 : userPromptPayload.length()) + "\n" +
                "\nuserPrompt:\n" + (userPrompt == null ? "" : userPrompt) + "\n";
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(empty)";
        }
        int len = secret.length();
        if (len <= 6) {
            return "***";
        }
        String prefix = secret.substring(0, 4);
        String suffix = secret.substring(Math.max(0, len - 2));
        return prefix + "***" + suffix;
    }

    private String buildDslJsonFromPlan(AiGraphPlan plan) {
        return AiGraphPlanDslAdapterService.toDslJson(toServiceGraphPlan(plan));
    }

    private AiGraphPlan buildPlanFromDsl(AiGraphDslSupport.DslGraph dslGraph) {
        return fromServiceGraphPlan(AiGraphPlanDslAdapterService.fromDsl(dslGraph));
    }

    private AiGraphPlan buildMockAiPlan(String prompt) {
        AiMockPlanService.MockPlan mockPlan = AiMockPlanService.buildMockPlan(prompt);
        return fromServiceGraphPlan(AiGraphPlanDslAdapterService.fromMockPlan(mockPlan));
    }

    private AiGraphPlanDslAdapterService.GraphPlan toServiceGraphPlan(AiGraphPlan plan) {
        List<AiGraphPlanDslAdapterService.PlanNode> nodes = new ArrayList<>();
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphPlanDslAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphPlanDslAdapterService.PlanConnection> connections = new ArrayList<>();
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphPlanDslAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return new AiGraphPlanDslAdapterService.GraphPlan(
                plan.summary(),
                nodes,
                connections,
                plan.validationErrors()
        );
    }

    private AiGraphPlan fromServiceGraphPlan(AiGraphPlanDslAdapterService.GraphPlan plan) {
        List<AiPlanNode> nodes = new ArrayList<>();
        for (AiGraphPlanDslAdapterService.PlanNode node : plan.nodes()) {
            nodes.add(new AiPlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
        }

        List<AiPlanConnection> connections = new ArrayList<>();
        for (AiGraphPlanDslAdapterService.PlanConnection connection : plan.connections()) {
            connections.add(new AiPlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        List<String> errors = plan.validationErrors() == null ? List.of() : plan.validationErrors();
        return new AiGraphPlan(plan.summary(), nodes, connections, errors);
    }

    private void applyPendingAiPlan() {
        if (pendingAiPlan == null) {
            aiPlanStatusMessage = "No plan available.";
            return;
        }
        if (!pendingAiPlan.isValid()) {
            aiPlanStatusMessage = "Cannot apply: plan has validation errors.";
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        float[] anchor = resolveAiPlanAnchorPosition(editor);
        List<AiPlanNode> nodesToApply = aiAutoLayoutBeforeApply.get()
                ? buildAutoLayoutNodes(pendingAiPlan)
                : pendingAiPlan.nodes();

        if (aiPatchApplyMode.get()) {
            applyPendingAiPlanPatch(editor, nodesToApply, anchor);
            return;
        }

        Map<String, UUID> createdNodeIds = new HashMap<>();
        int undoSteps = 0;
        int successfulConnections = 0;

        try {
            for (AiPlanNode node : nodesToApply) {
                float x = anchor[0] + node.offsetX();
                float y = anchor[1] + node.offsetY();
                INode created = node.nodeState() == null
                        ? editor.addNode(node.typeId(), x, y)
                        : editor.addNodeWithState(node.typeId(), null, x, y, node.nodeState());

                if (created == null) {
                    rollbackAiApply(editor, undoSteps);
                    aiPlanStatusMessage = "Failed to create node: " + node.ref() + " (" + node.typeId() + "). Auto-rolled back.";
                    return;
                }
                createdNodeIds.put(node.ref(), created.getId());
                undoSteps++;
            }

            for (AiPlanConnection connection : pendingAiPlan.connections()) {
                UUID sourceNodeId = createdNodeIds.get(connection.sourceRef());
                UUID targetNodeId = createdNodeIds.get(connection.targetRef());
                if (sourceNodeId == null || targetNodeId == null) {
                    rollbackAiApply(editor, undoSteps);
                    aiPlanStatusMessage = "Connection failed due to missing node ref: "
                        + connection.sourceRef() + " -> " + connection.targetRef() + ". Auto-rolled back.";
                    return;
                }

                boolean connected = editor.connectPorts(
                        sourceNodeId,
                        connection.sourcePortId(),
                        targetNodeId,
                        connection.targetPortId()
                );
                if (connected) {
                    successfulConnections++;
                    undoSteps++;
                } else {
                    NodeCraft.LOGGER.warn("AI plan connection failed and will rollback: {}.{} -> {}.{}",
                            connection.sourceRef(), connection.sourcePortId(),
                            connection.targetRef(), connection.targetPortId());
                    rollbackAiApply(editor, undoSteps);
                    aiPlanStatusMessage = "Failed to connect: "
                            + connection.sourceRef() + "." + connection.sourcePortId()
                            + " -> " + connection.targetRef() + "." + connection.targetPortId()
                            + ". Auto-rolled back.";
                    return;
                }
            }

            lastAiUndoStepCount = undoSteps;
            aiPlanStatusMessage = "Applied AI plan: created " + createdNodeIds.size()
                    + " nodes, connected " + successfulConnections
                    + ". Undo steps available: " + lastAiUndoStepCount + "."
                    + (aiAutoLayoutBeforeApply.get() ? " (auto layout enabled)" : "");
        } catch (Exception e) {
            rollbackAiApply(editor, undoSteps);
            NodeCraft.LOGGER.error("Failed to apply AI plan", e);
            aiPlanStatusMessage = "Failed to apply plan: " + e.getMessage() + ". Auto-rolled back.";
        }
    }

    private void applyPendingAiPlanPatch(ImGuiNodeEditor editor, List<AiPlanNode> nodesToApply, float[] anchor) {
        NodeGraph graph = getNodeGraph();
        if (graph == null) {
            aiPlanStatusMessage = "Patch apply failed: current graph is unavailable.";
            return;
        }
        List<AiGraphApplyService.ApplyNode> applyNodes = new ArrayList<>(nodesToApply.size());
        for (AiPlanNode node : nodesToApply) {
            applyNodes.add(new AiGraphApplyService.ApplyNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphApplyService.ApplyConnection> applyConnections = new ArrayList<>(pendingAiPlan.connections().size());
        for (AiPlanConnection connection : pendingAiPlan.connections()) {
            applyConnections.add(new AiGraphApplyService.ApplyConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                editor,
                graph,
                applyNodes,
                applyConnections,
                anchor,
                aiPatchRemoveScopedConnections.get()
        );
        lastAiUndoStepCount = result.undoSteps();
        aiPlanStatusMessage = result.statusMessage()
                + (result.success() && aiAutoLayoutBeforeApply.get() ? " (auto layout enabled for new nodes)" : "");
    }

    private void rollbackAiApply(ImGuiNodeEditor editor, int undoSteps) {
        int undone = 0;
        for (int i = 0; i < undoSteps; i++) {
            if (!editor.undo()) {
                break;
            }
            undone++;
        }
    }

    private void undoLastAiApply() {
        if (lastAiUndoStepCount <= 0) {
            aiPlanStatusMessage = "No AI apply operation to undo.";
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        int undone = 0;
        for (int i = 0; i < lastAiUndoStepCount; i++) {
            if (!editor.undo()) {
                break;
            }
            undone++;
        }

        aiPlanStatusMessage = "Undo completed: " + undone + " / " + lastAiUndoStepCount + " steps.";
        lastAiUndoStepCount = 0;
    }

    private float[] resolveAiPlanAnchorPosition(ImGuiNodeEditor editor) {
        if (selectedNode != null) {
            NodePosition selectedPosition = editor.getNodePosition(selectedNode.getId());
            if (selectedPosition != null) {
                return new float[]{selectedPosition.x + 280.0f, selectedPosition.y};
            }
        }
        return new float[]{0.0f, 0.0f};
    }

    private List<AiPlanNode> buildAutoLayoutNodes(AiGraphPlan plan) {
        if (plan == null || plan.nodes().isEmpty()) {
            return List.of();
        }

        Map<String, AiPlanNode> nodeByRef = new LinkedHashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Map<String, List<String>> edges = new HashMap<>();

        for (AiPlanNode node : plan.nodes()) {
            nodeByRef.put(node.ref(), node);
            indegree.put(node.ref(), 0);
            depth.put(node.ref(), 0);
            edges.put(node.ref(), new ArrayList<>());
        }

        for (AiPlanConnection connection : plan.connections()) {
            if (!nodeByRef.containsKey(connection.sourceRef()) || !nodeByRef.containsKey(connection.targetRef())) {
                continue;
            }
            edges.get(connection.sourceRef()).add(connection.targetRef());
            indegree.put(connection.targetRef(), indegree.get(connection.targetRef()) + 1);
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (AiPlanNode node : plan.nodes()) {
            if (indegree.get(node.ref()) == 0) {
                queue.add(node.ref());
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.getOrDefault(current, 0);
            for (String next : edges.getOrDefault(current, List.of())) {
                depth.put(next, Math.max(depth.getOrDefault(next, 0), currentDepth + 1));
                int nextIn = indegree.getOrDefault(next, 0) - 1;
                indegree.put(next, nextIn);
                if (nextIn == 0) {
                    queue.add(next);
                }
            }
        }

        Map<Integer, List<AiPlanNode>> layerMap = new TreeMap<>();
        for (AiPlanNode node : plan.nodes()) {
            int layer = Math.max(0, depth.getOrDefault(node.ref(), 0));
            layerMap.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(node);
        }

        return getAiPlanNodes(layerMap);
    }

    private static @NonNull List<AiPlanNode> getAiPlanNodes(Map<Integer, List<AiPlanNode>> layerMap) {
        float layerSpacingX = 320.0f;
        float layerSpacingY = 180.0f;
        List<AiPlanNode> arranged = new ArrayList<>();

        for (Map.Entry<Integer, List<AiPlanNode>> layerEntry : layerMap.entrySet()) {
            int layer = layerEntry.getKey();
            List<AiPlanNode> layerNodes = layerEntry.getValue();
            for (int i = 0; i < layerNodes.size(); i++) {
                AiPlanNode node = layerNodes.get(i);
                float x = layer * layerSpacingX;
                float y = (i - (layerNodes.size() - 1) / 2.0f) * layerSpacingY;
                arranged.add(new AiPlanNode(node.ref(), node.typeId(), x, y, node.nodeState()));
            }
        }
        return arranged;
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
        editorState.clearForNode(selectedNode);
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
    private void clearSelectedNodeData() {
        clearCurrentNodeTempValues();
        // propertiesBeingEdited 在 clearCurrentNodeTempValues 内部已经处理了
        // errorCounts 也在 clearCurrentNodeTempValues 内部处理了
    }

    public void setSelectedNode(INode node) {
        // 简化选择逻辑，直接设置选中节点
        if (this.selectedNode != node) { // 仅当选择的节点实际发生变化时才操作
            // 清理旧节点的数据
            clearSelectedNodeData();
            this.selectedNode = node;
            if (node != null) {
                NodeCraft.LOGGER.debug("属性面板更新选中节点: {}", node.getId());
            } else {
                NodeCraft.LOGGER.debug("属性面板已清除选中节点");
            }
        }
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
    private void markPropertyBeingEdited(INode node, String propName) {
        editorState.markPropertyBeingEdited(node, propName);
    }

    /**
     * 标记属性为编辑完成状态
     * @param node 节点
     * @param propName 属性名
     */
    private void markPropertyEditingFinished(INode node, String propName) {
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
    private boolean isPropertyBeingEdited(INode node, String propName) {
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
    private String getTempValueKey(INode node, String propName) {
        return editorState.getTempValueKey(node, propName);
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
    private PropertyRenderer getRendererForType(Class<?> type) {
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
