package com.nodecraft.gui.components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.gui.components.AiAssistantComponent.AiChatMessage;
import com.nodecraft.gui.components.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.gui.components.AiAssistantComponent.AiPlanConnection;
import com.nodecraft.gui.components.AiAssistantComponent.AiPlanNode;
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

public class PropertyPanelComponent implements EditorComponent {

    private static final String COMPONENT_ID = "property_panel";
    private static final long AI_SESSION_SAVE_DEBOUNCE_MS = 800L;
    private static final int AI_HISTORY_MAX_CHARS_PER_MESSAGE = 1800;
    private static final int AI_HISTORY_MAX_TOTAL_CHARS = 9000;
    private static final int AI_LATEST_USER_MESSAGE_MAX_CHARS = 7000;
        private static final String[] AI_PROVIDER_STRATEGY_OPTIONS = {
            AiSettingsStore.PROVIDER_AUTO,
            AiSettingsStore.PROVIDER_OPENAI_COMPAT,
            AiSettingsStore.PROVIDER_ANTHROPIC
        };
            private static final String[] OPENAI_MODELS = {
                "gpt-4.1-mini",
                "gpt-4.1",
                "gpt-4o-mini",
                "gpt-4o"
            };
            private static final String[] ANTHROPIC_MODELS = {
                "claude-3-5-haiku-latest",
                "claude-3-7-sonnet-latest",
                "claude-sonnet-4-0"
            };
            private static final String[] DEEPSEEK_MODELS = {
                "deepseek-chat",
                "deepseek-reasoner"
            };
            private static final String[] QWEN_MODELS = {
                "qwen-max",
                "qwen-plus",
                "qwen-turbo",
                "qwen3-32b"
            };
            private static final String[] GROQ_MODELS = {
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "qwen/qwen3-32b"
            };
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
    private final AiAssistantComponent aiAssistantComponent = new AiAssistantComponent();

    private final ImString aiPromptInput = new ImString("", 2048);
    private final ImBoolean aiUseSelectionContext = new ImBoolean(true);
    private final ImBoolean aiIncludeGraphContext = new ImBoolean(true);
    private final List<AiChatMessage> aiChatMessages = aiAssistantComponent.getChatMessages();
    private final ImString aiApiBaseUrl = new ImString("https://api.openai.com/v1", 512);
    private final ImString aiApiKey = new ImString("", 512);
    private final ImString aiModel = new ImString("gpt-4.1-mini", 128);
    private final ImInt aiProviderStrategyIndex = new ImInt(0);
    private final ImString aiSystemPrompt = new ImString("You are a NodeCraft graph planning assistant.", 2048);
    private final ImInt aiMaxOutputTokens = new ImInt(2048);
    private final ImInt aiRequestTimeoutSeconds = new ImInt(60);
    private final ImInt aiConversationHistoryTurns = new ImInt(6);
    private final ImBoolean aiShowApiKey = new ImBoolean(false);
    private final ImBoolean aiEnableRemotePlanner = new ImBoolean(false);
    private final ImBoolean aiAutoLayoutBeforeApply = new ImBoolean(true);
    private final ImBoolean aiPreviewOnlyMode = new ImBoolean(false);
    private final ImBoolean aiPatchApplyMode = new ImBoolean(true);
    private final ImBoolean aiPatchRemoveScopedConnections = new ImBoolean(false);
    private final Path aiSettingsPath;
    private String aiLastSubmittedPrompt = "";
    private String aiLastDetectedProviderLabel = "";
    private CompletableFuture<AiRemotePlannerService.RemotePlanResult> aiConnectionTestFuture = null;
    private int lastRenderedChatCount = 0;
    private int lastAiUndoStepCount = 0;
    private String aiPlanStatusMessage = "";
    private String aiSettingsStatusMessage = "";

    private enum RightPanelTab {
        PROPERTIES,
        AI_ASSISTANT
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
        this.aiAssistantComponent.initializeSessionStore(aiSettingsPath);
        loadAiSettingsFromDisk();
        loadAiSessionStateFromDisk();
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
        saveAiSessionStateToDiskNow();
        aiAssistantComponent.cleanup();
        // 移除未保存更改相关的清理
        selectedNode = null;
        aiChatMessages.clear();
        aiPromptInput.clear();
        aiApiKey.clear();
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
            flushAiSessionStateIfDue();

            ImGui.text("Inspector");
            ImGui.separator();

            if (ImGui.beginTabBar("rightPanelTabs")) {
                RightPanelTab activeTab = RightPanelTab.PROPERTIES;
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
        pollConnectionTestResultIfReady();

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
            ImGui.textColored(0.95f, 0.72f, 0.22f, 1.0f,
                    "Warning: reused-node parameter updates may not be undoable.");
            ImGui.sameLine();
            ImGui.textDisabled("(?)");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Patch mode can update state on matched existing nodes directly.\n"
                        + "Graph edits are undoable, but some parameter/state updates may require manual revert.");
            }
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
                // Fixed: Only auto-scroll when a new message is added, preserved manual scroll position otherwise.
                if (aiChatMessages.size() > lastRenderedChatCount) {
                    ImGui.setScrollHereY(1.0f);
                    lastRenderedChatCount = aiChatMessages.size();
                }
            }
        }
        ImGui.endChild();

        if (isRemotePlannerBusy()) ImGui.beginDisabled();

        // Calculate dynamic height: base height for 1 line, expanding to 4 lines max.
        String currentInput = aiPromptInput.get();
        int lineCount = (currentInput == null || currentInput.isEmpty()) ? 1 : 
                        (int) (currentInput.chars().filter(ch -> ch == '\n').count() + 1);
        float inputHeight = Math.min(4, Math.max(1, lineCount)) * ImGui.getFrameHeight();

        ImGui.pushItemWidth(-85.0f);
        // Multiline with smart return: Enter to submit, Ctrl+Enter or Shift+Enter for new line
        // Note: ImGuiInputTextFlags.EnterReturnsTrue paired with Multiline usually requires custom handling
        // for better UX, but here we prioritize the 'expansion' feel.
        boolean submitted = ImGui.inputTextMultiline("##ai_prompt", aiPromptInput, ImGui.getContentRegionAvailX() - 85.0f, inputHeight, 
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CtrlEnterForNewLine);
        ImGui.popItemWidth();

        ImGui.sameLine();
        if (ImGui.button("Send", 80.0f, inputHeight) || submitted) {
            submitAiPrompt();
        }
        if (isRemotePlannerBusy()) ImGui.endDisabled();

        ImGui.textDisabled(aiEnableRemotePlanner.get()
                ? "Current mode: remote planner + DSL validation + apply/undo"
                : "Current mode: local mock planner + DSL validation + apply/undo");
    }

    private void renderAiSettingsPopup() {
        String detectedProviderLabel = resolveDetectedProviderLabel(aiApiBaseUrl.get());
        String[] suggestedModels = resolveSuggestedModels(aiApiBaseUrl.get());
        maybeAutofillModelByProviderChange(detectedProviderLabel, suggestedModels);

        AiAssistantSettingsPopupRenderer.renderSettingsPopup(
                new AiAssistantSettingsPopupRenderer.State(
                        aiEnableRemotePlanner,
                        aiApiBaseUrl,
                        aiApiKey,
                        aiModel,
                        detectedProviderLabel,
                        suggestedModels,
                    aiProviderStrategyIndex,
                        aiSystemPrompt,
                        aiMaxOutputTokens,
                        aiRequestTimeoutSeconds,
                        aiConversationHistoryTurns,
                        aiShowApiKey,
                        aiAutoLayoutBeforeApply,
                        aiSettingsPath
                ),
                new AiAssistantSettingsPopupRenderer.Actions() {
                    @Override
                    public void onValidateLocal() {
                        aiSettingsStatusMessage = validateAiSettings();
                    }

                    @Override
                    public void onTestRemoteConnection() {
                        testRemoteConnection();
                    }

                    @Override
                    public void onSaveSettings() {
                        saveAiSettingsToDisk();
                    }

                    @Override
                    public void onReloadSettings() {
                        loadAiSettingsFromDisk();
                        aiSettingsStatusMessage = "AI settings reloaded from disk.";
                    }
                }
        );
    }

    private void renderAiDebugConsolePopup() {
        String compactDiagnostics = buildAiDiagnosticsExportText(false);
        String fullDiagnostics = buildAiDiagnosticsExportText(true);

        AiAssistantDebugConsoleRenderer.renderDebugConsolePopup(
                new AiAssistantDebugConsoleRenderer.State(
                aiAssistantComponent.getLastRemoteErrorCategory(),
                aiAssistantComponent.getLastRemoteAttempts(),
                aiAssistantComponent.getLastRemoteRawResponse(),
                aiAssistantComponent.getLastRemoteModelText(),
                aiAssistantComponent.getLastRemoteRequestSnapshot(),
                        compactDiagnostics,
                        fullDiagnostics
                ),
                new AiAssistantDebugConsoleRenderer.Actions() {
                    @Override
                    public void renderFailureSummarySection() {
                        AiAssistantFailurePanelRenderer.renderFailureSummaryCard(
                                new AiAssistantFailurePanelRenderer.State(
                                    aiAssistantComponent.getLastRemoteErrorCategory(),
                                    aiAssistantComponent.getLastRemoteStatusCode(),
                                    aiAssistantComponent.getLastRemoteAttempts(),
                                    aiAssistantComponent.getLastRemoteErrorMessage(),
                                        aiLastSubmittedPrompt != null && !aiLastSubmittedPrompt.isBlank(),
                                        isRemotePlannerBusy(),
                                        aiEnableRemotePlanner.get()
                                ),
                                new AiAssistantFailurePanelRenderer.Actions() {
                                    @Override
                                    public void retryLastRequest() {
                                        retryLastAiRequest();
                                    }

                                    @Override
                                    public void increaseTimeoutSeconds(int deltaSeconds) {
                                        increaseAiTimeoutSeconds(deltaSeconds);
                                    }

                                    @Override
                                    public void togglePlannerMode() {
                                        aiEnableRemotePlanner.set(!aiEnableRemotePlanner.get());
                                        saveAiSettingsToDisk();
                                        aiSettingsStatusMessage = aiEnableRemotePlanner.get()
                                                ? "Remote planner re-enabled."
                                                : "Switched to local planner for the next request.";
                                    }

                                    @Override
                                    public void openAiSettingsPopup() {
                                        ImGui.openPopup("AI Settings");
                                    }

                                    @Override
                                    public void resaveSettings() {
                                        saveAiSettingsToDisk();
                                        aiSettingsStatusMessage = "AI settings saved.";
                                    }
                                }
                        );
                    }

                    @Override
                    public void copyRawResponse() {
                        copyToClipboard(aiAssistantComponent.getLastRemoteRawResponse());
                        aiPlanStatusMessage = "Raw response copied to clipboard.";
                    }

                    @Override
                    public void copyModelText() {
                        copyToClipboard(aiAssistantComponent.getLastRemoteModelText());
                        aiPlanStatusMessage = "Model text copied to clipboard.";
                    }

                    @Override
                    public void copyRequestSnapshot() {
                        copyToClipboard(aiAssistantComponent.getLastRemoteRequestSnapshot());
                        aiPlanStatusMessage = "Request snapshot copied to clipboard.";
                    }

                    @Override
                    public void copyCompactExport() {
                        copyToClipboard(compactDiagnostics);
                        aiPlanStatusMessage = "Compact diagnostics exported to clipboard.";
                    }

                    @Override
                    public void copyFullExport() {
                        copyToClipboard(fullDiagnostics);
                        aiPlanStatusMessage = "Full diagnostics exported to clipboard.";
                    }
                }
        );
    }

    private boolean hasAiDebugData() {
        return (aiAssistantComponent.getLastRemoteRawResponse() != null && !aiAssistantComponent.getLastRemoteRawResponse().isBlank())
            || (aiAssistantComponent.getLastRemoteModelText() != null && !aiAssistantComponent.getLastRemoteModelText().isBlank())
            || (aiAssistantComponent.getLastRemoteRequestSnapshot() != null && !aiAssistantComponent.getLastRemoteRequestSnapshot().isBlank())
            || (aiAssistantComponent.getLastRemoteErrorMessage() != null && !aiAssistantComponent.getLastRemoteErrorMessage().isBlank());
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
                "category: " + nullToEmpty(aiAssistantComponent.getLastRemoteErrorCategory()) + "\n" +
                "statusCode: " + aiAssistantComponent.getLastRemoteStatusCode() + "\n" +
                "attempts: " + aiAssistantComponent.getLastRemoteAttempts() + "\n" +
                "errorMessage: " + nullToEmpty(aiAssistantComponent.getLastRemoteErrorMessage()) + "\n" +
                "statusMessage: " + nullToEmpty(aiPlanStatusMessage) + "\n\n" +
                "[Request Snapshot]\n" +
                formatDiagnosticsSection(aiAssistantComponent.getLastRemoteRequestSnapshot(), includeFullPayloads) + "\n" +
                "[Model Text]\n" +
                formatDiagnosticsSection(aiAssistantComponent.getLastRemoteModelText(), includeFullPayloads) + "\n" +
                "[Raw Response]\n" +
                formatDiagnosticsSection(aiAssistantComponent.getLastRemoteRawResponse(), includeFullPayloads) + "\n";
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

    private void loadAiSessionStateFromDisk() {
        String status = aiAssistantComponent.loadSessionState(this::deserializePendingPlanFromDsl);
        if (status != null && !status.isBlank()) {
            aiSettingsStatusMessage = status;
        }
    }

    private void saveAiSessionStateToDisk() {
        aiAssistantComponent.queueSessionStateSave(AI_SESSION_SAVE_DEBOUNCE_MS);
    }

    private void saveAiSessionStateToDiskNow() {
        aiAssistantComponent.saveSessionStateNow(this::serializePendingPlanToDsl);
    }

    private void flushAiSessionStateIfDue() {
        aiAssistantComponent.flushSessionStateIfDue(AI_SESSION_SAVE_DEBOUNCE_MS, this::serializePendingPlanToDsl);
    }

    private String serializePendingPlanToDsl(AiGraphPlan plan) {
        if (plan == null) {
            return "";
        }
        return AiPlanDslWorkflowService.toDslJson(toServiceGraphPlanForHistory(plan));
    }

    private AiGraphPlan deserializePendingPlanFromDsl(String pendingPlanDslJson) {
        AiGraphDslSupport.ParseValidationResult parsed =
                AiGraphDslSupport.parseAndValidate(pendingPlanDslJson, NodeRegistry.getInstance());
        if (!parsed.isSuccess() || parsed.graph() == null) {
            aiPlanStatusMessage = "Stored pending plan skipped due to validation failure.";
            return null;
        }
        return fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(parsed.graph()));
    }

    private void addAiChatMessage(String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        aiChatMessages.add(new AiChatMessage(role == null ? "assistant" : role, content, System.currentTimeMillis()));
        saveAiSessionStateToDisk();
    }

    private void setPendingAiPlan(AiGraphPlan plan) {
        aiAssistantComponent.setPendingPlan(plan);
        saveAiSessionStateToDisk();
    }

    private AiGraphPlan getPendingAiPlan() {
        return aiAssistantComponent.getPendingPlan();
    }

    private AiSettingsStore.AiSettingsData collectAiSettingsData() {
        return new AiSettingsStore.AiSettingsData(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
            providerStrategyFromIndex(aiProviderStrategyIndex.get()),
                aiSystemPrompt.get(),
                aiMaxOutputTokens.get(),
                aiRequestTimeoutSeconds.get(),
                aiConversationHistoryTurns.get(),
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
        aiProviderStrategyIndex.set(indexFromProviderStrategy(data.providerStrategy()));
        aiSystemPrompt.set(data.systemPrompt());
        aiMaxOutputTokens.set(data.maxOutputTokens());
        aiRequestTimeoutSeconds.set(data.timeoutSeconds());
        aiConversationHistoryTurns.set(data.conversationHistoryTurns());
        aiShowApiKey.set(data.showApiKey());
        aiEnableRemotePlanner.set(data.enableRemotePlanner());
        aiAutoLayoutBeforeApply.set(data.autoLayoutBeforeApply());
        aiIncludeGraphContext.set(data.includeGraphContext());
        aiPreviewOnlyMode.set(data.previewOnlyMode());
        aiPatchApplyMode.set(data.patchApplyMode());
        aiPatchRemoveScopedConnections.set(data.patchRemoveScopedConnections());
    }

    private void renderAiPlanPreviewSection() {
        AiGraphPlan plan = getPendingAiPlan();
        boolean hasPlan = plan != null;

        AiGraphDiffService.GraphDiffSummary heuristicDiff = hasPlan ? buildGraphDiffSummary(plan) : null;
        AiGraphDiffService.MappedDiffSummary mappedDiff = hasPlan ? buildMappedDiffSummary(plan) : null;
        boolean canApply = hasPlan && plan.isValid() && !plan.nodes().isEmpty();

        AiAssistantPlanPreviewRenderer.renderPlanPreviewSection(
                new AiAssistantPlanPreviewRenderer.State(
                        hasPlan,
                        hasPlan ? plan.summary() : "",
                        hasPlan ? plan.nodes().size() : 0,
                        hasPlan ? plan.connections().size() : 0,
                        hasPlan ? plan.validationErrors() : List.of(),
                        hasPlan ? buildPlannedNodePreviewLines(plan) : List.of(),
                        hasPlan ? buildPlannedConnectionPreviewLines(plan) : List.of(),
                        heuristicDiff,
                        mappedDiff,
                        canApply,
                        lastAiUndoStepCount > 0,
                        aiPlanStatusMessage
                ),
                new AiAssistantPlanPreviewRenderer.Actions() {
                    @Override
                    public void applyPlan() {
                        if (aiPreviewOnlyMode.get()) {
                            runDryRunForPendingPlan();
                        } else {
                            applyPendingAiPlan();
                        }
                    }

                    @Override
                    public void dryRunReport() {
                        runDryRunForPendingPlan();
                    }

                    @Override
                    public void undoLastApply() {
                        undoLastAiApply();
                    }
                }
        );
    }

    private List<String> buildPlannedNodePreviewLines(AiGraphPlan plan) {
        if (plan == null || plan.nodes().isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            lines.add(node.ref() + " -> " + node.typeId()
                    + "  (" + String.format(Locale.ROOT, "%.0f", node.offsetX())
                    + ", " + String.format(Locale.ROOT, "%.0f", node.offsetY()) + ")");
        }
        return lines;
    }

    private List<String> buildPlannedConnectionPreviewLines(AiGraphPlan plan) {
        if (plan == null || plan.connections().isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            lines.add(connection.sourceRef() + "." + connection.sourcePortId()
                    + " -> " + connection.targetRef() + "." + connection.targetPortId());
        }
        return lines;
    }

    private void runDryRunForPendingPlan() {
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
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

        String reportText = AiPlanDryRunReportService.buildDryRunReport(
                pendingAiPlan.nodes().size(),
                pendingAiPlan.connections().size(),
                heuristic,
                mapped
        );
        aiPlanStatusMessage = reportText;
        addAiChatMessage("assistant", reportText);
    }

    private AiGraphDiffService.GraphDiffSummary buildGraphDiffSummary(AiGraphPlan plan) {
        if (plan == null) {
            return AiGraphDiffAdapterService.buildGraphDiffSummary(List.of(), List.of(), getNodeGraph());
        }

        List<AiGraphDiffAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphDiffAdapterService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiGraphDiffAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphDiffAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return AiGraphDiffAdapterService.buildGraphDiffSummary(nodes, connections, getNodeGraph());
    }

    private AiGraphDiffService.MappedDiffSummary buildMappedDiffSummary(AiGraphPlan plan) {
        if (plan == null) {
            return AiGraphDiffAdapterService.buildMappedDiffSummary(List.of(), List.of(), getNodeGraph());
        }

        List<AiGraphDiffAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphDiffAdapterService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiGraphDiffAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphDiffAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return AiGraphDiffAdapterService.buildMappedDiffSummary(nodes, connections, getNodeGraph());
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
        addAiChatMessage("user", trimmedPrompt);

        if (aiEnableRemotePlanner.get()) {
            startRemotePlannerRequest(trimmedPrompt);
            return;
        }

        String dslJson = AiPlanDslWorkflowService.toDslJson(
            AiPlanDslWorkflowService.buildMockGraphPlan(trimmedPrompt)
        );
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
            addAiChatMessage("assistant", validation);
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
        List<AiRemotePlannerService.ConversationMessage> conversationHistory =
                buildConversationHistory(userPrompt, userPromptPayload);
        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
            providerStrategyFromIndex(aiProviderStrategyIndex.get()),
                systemPrompt,
                aiMaxOutputTokens.get(),
                aiRequestTimeoutSeconds.get()
        );

        String requestSnapshot = buildRemoteRequestSnapshot(config, userPrompt, userPromptPayload, relevantSchemas.size());
        aiPlanStatusMessage = "Remote planner request submitted...";
        aiAssistantComponent.submitRemotePlannerRequest(userPrompt, config, conversationHistory, requestSnapshot);
    }

    private void testRemoteConnection() {
        String validation = validateAiSettings();
        if (validation.startsWith("Validation failed")) {
            aiSettingsStatusMessage = validation;
            return;
        }

        if (aiConnectionTestFuture != null && !aiConnectionTestFuture.isDone()) {
            aiSettingsStatusMessage = "Connection test is already running...";
            return;
        }

        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
                providerStrategyFromIndex(aiProviderStrategyIndex.get()),
                aiSystemPrompt.get(),
                aiMaxOutputTokens.get(),
                aiRequestTimeoutSeconds.get()
        );
        aiSettingsStatusMessage = "Testing remote API connection...";
        aiConnectionTestFuture = aiAssistantComponent.testRemoteConnectionAsync(config);
    }

    private void pollConnectionTestResultIfReady() {
        if (aiConnectionTestFuture == null || !aiConnectionTestFuture.isDone()) {
            return;
        }

        try {
            AiRemotePlannerService.RemotePlanResult result = aiConnectionTestFuture.join();
            if (result.success()) {
                aiSettingsStatusMessage = "Remote API connection successful (HTTP " + result.statusCode() + ").";
            } else {
                aiSettingsStatusMessage = "Remote API connection failed: " + formatRemoteErrorMessage(result);
            }
        } catch (Exception e) {
            aiSettingsStatusMessage = "Remote API connection failed: " + e.getMessage();
        } finally {
            aiConnectionTestFuture = null;
        }
    }

    private List<AiRemotePlannerService.ConversationMessage> buildConversationHistory(
            String newUserPrompt,
            String userPromptPayload
    ) {
        List<AiChatMessage> recent = getRecentPlanningMessages(resolveConversationHistoryLimit(), newUserPrompt);
        List<AiConversationHistoryService.ChatLine> historyLines = new ArrayList<>(recent.size());
        for (AiChatMessage message : recent) {
            historyLines.add(new AiConversationHistoryService.ChatLine(
                    message.role(),
                    message.content(),
                    message.timestampMs()
            ));
        }

        List<AiRemotePlannerService.ConversationMessage> history = AiConversationHistoryService.toConversationMessages(
                historyLines,
                AI_HISTORY_MAX_CHARS_PER_MESSAGE,
                AI_HISTORY_MAX_TOTAL_CHARS
        );

        String latestUserMessage = userPromptPayload;
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        if (pendingAiPlan != null) {
            String currentPlanJson = AiPlanDslWorkflowService.toDslJson(toServiceGraphPlanForHistory(pendingAiPlan));
            latestUserMessage = "Current plan in effect:\n```json\n"
                    + currentPlanJson
                    + "\n```\n\n"
                    + "User follow-up:\n"
                    + userPromptPayload;
        }

                latestUserMessage = AiConversationHistoryService.compactMessage(
                    latestUserMessage,
                    AI_LATEST_USER_MESSAGE_MAX_CHARS
                );

        history.add(new AiRemotePlannerService.ConversationMessage("user", latestUserMessage));
        return history;
    }

    private int resolveConversationHistoryLimit() {
        return Math.max(1, Math.min(20, aiConversationHistoryTurns.get()));
    }

    private List<AiChatMessage> getRecentPlanningMessages(int limit, String latestUserPrompt) {
        if (aiChatMessages.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<AiConversationHistoryService.ChatLine> allLines = new ArrayList<>(aiChatMessages.size());
        for (AiChatMessage message : aiChatMessages) {
            allLines.add(new AiConversationHistoryService.ChatLine(
                    message.role(),
                    message.content(),
                    message.timestampMs()
            ));
        }

        List<AiConversationHistoryService.ChatLine> selected = AiConversationHistoryService.selectRecentPlanningMessages(
                allLines,
                latestUserPrompt,
                limit
        );

        List<AiChatMessage> recent = new ArrayList<>(selected.size());
        for (AiConversationHistoryService.ChatLine line : selected) {
            recent.add(new AiChatMessage(line.role(), line.content(), line.timestampMs()));
        }
        return recent;
    }

    private void pollRemotePlannerResultIfReady() {
        AiAssistantComponent.RemotePollResult pollResult = aiAssistantComponent.pollRemotePlannerResultIfReady();
        if (pollResult == null) {
            return;
        }

        if (pollResult.hasException()) {
            String error = "Remote planner failed: " + pollResult.exceptionMessage();
            aiPlanStatusMessage = error;
            addAiChatMessage("assistant", error);
            return;
        }

        String prompt = pollResult.prompt();
        AiRemotePlannerService.RemotePlanResult result = pollResult.result();
        if (result == null) {
            String error = "Remote planner failed: unknown error";
            aiPlanStatusMessage = error;
            addAiChatMessage("assistant", error);
            return;
        }

        if (!result.success()) {
            String error = formatRemoteErrorMessage(result);
            aiPlanStatusMessage = error;
            addAiChatMessage("assistant", error);
            fallbackToLocalPlan(prompt, "remote request failed");
            return;
        }

        applyDslResponse(prompt, result.modelContent(), result.structuredPayload() ? "remote-tool" : "remote");
    }

    private void applyDslResponse(String prompt, String dslOrModelResponse, String source) {
        boolean isStructured = "remote-tool".equals(source);
        AiGraphDslSupport.ParseValidationResult parsed = isStructured
            ? AiGraphDslSupport.parseStructured(dslOrModelResponse, NodeRegistry.getInstance())
            : AiGraphDslSupport.parseAndValidate(dslOrModelResponse, NodeRegistry.getInstance());

        if (!parsed.isSuccess() || parsed.graph() == null) {
            setPendingAiPlan(null);
            String errorMessage = "Plan JSON validation failed: " + String.join("; ", parsed.errors());
            addAiChatMessage("assistant", errorMessage);
            aiPlanStatusMessage = errorMessage;
            if ("remote".equals(source)) {
                fallbackToLocalPlan(prompt, "remote JSON invalid");
            }
            return;
        }

        setPendingAiPlan(fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(parsed.graph())));
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        String warningSuffix = formatValidationWarningSuffix(parsed.warnings());
        addAiChatMessage(
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
                ) + warningSuffix
        );
        aiPlanStatusMessage = "Plan JSON validated (" + source + "). Review and click Apply Plan." + warningSuffix;
    }

    private void fallbackToLocalPlan(String prompt, String reason) {
        String localDslJson = AiPlanDslWorkflowService.toDslJson(
            AiPlanDslWorkflowService.buildMockGraphPlan(prompt)
        );
        AiGraphDslSupport.ParseValidationResult localParsed =
                AiGraphDslSupport.parseAndValidate(localDslJson, NodeRegistry.getInstance());

        if (!localParsed.isSuccess() || localParsed.graph() == null) {
            aiPlanStatusMessage = "Local fallback also failed: " + String.join("; ", localParsed.errors());
            addAiChatMessage("assistant", aiPlanStatusMessage);
            return;
        }

        setPendingAiPlan(fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(localParsed.graph())));
        String warningSuffix = formatValidationWarningSuffix(localParsed.warnings());
        aiPlanStatusMessage = "Remote planner fallback applied (" + reason + "). Review and click Apply Plan." + warningSuffix;
        addAiChatMessage("assistant", aiPlanStatusMessage);
    }

    private String formatValidationWarningSuffix(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }
        return " Warning: " + String.join("; ", warnings);
    }

    private boolean isRemotePlannerBusy() {
        return aiAssistantComponent.isRemotePlannerBusy();
    }

    private void cancelRemotePlannerRequest() {
        aiAssistantComponent.cancelRemotePlannerRequest();
        aiPlanStatusMessage = "Remote planner request canceled.";
        addAiChatMessage("assistant", aiPlanStatusMessage);
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
            "providerStrategy: " + nullToEmpty(config.providerStrategy()) + "\n" +
                "maxOutputTokens: " + config.maxOutputTokens() + "\n" +
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

    private String providerStrategyFromIndex(int index) {
        int safeIndex = Math.max(0, Math.min(AI_PROVIDER_STRATEGY_OPTIONS.length - 1, index));
        return AI_PROVIDER_STRATEGY_OPTIONS[safeIndex];
    }

    private int indexFromProviderStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return 0;
        }
        for (int i = 0; i < AI_PROVIDER_STRATEGY_OPTIONS.length; i++) {
            if (AI_PROVIDER_STRATEGY_OPTIONS[i].equalsIgnoreCase(strategy)) {
                return i;
            }
        }
        return 0;
    }

    private String resolveDetectedProviderLabel(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return "Unknown";
        }

        if (normalized.contains("deepseek")) {
            return "DeepSeek";
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs") || normalized.contains("qwen")) {
            return "Qwen (DashScope)";
        }
        if (normalized.contains("anthropic")) {
            return "Anthropic";
        }
        if (normalized.contains("groq")) {
            return "Groq";
        }
        if (normalized.contains("openai")) {
            return "OpenAI-Compatible";
        }
        return "OpenAI-Compatible";
    }

    private String[] resolveSuggestedModels(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return OPENAI_MODELS;
        }

        if (normalized.contains("deepseek")) {
            return DEEPSEEK_MODELS;
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs") || normalized.contains("qwen")) {
            return QWEN_MODELS;
        }
        if (normalized.contains("anthropic")) {
            return ANTHROPIC_MODELS;
        }
        if (normalized.contains("groq")) {
            return GROQ_MODELS;
        }
        return OPENAI_MODELS;
    }

    private String normalizeProviderInput(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.trim().toLowerCase(Locale.ROOT);
    }

    private void maybeAutofillModelByProviderChange(String detectedProviderLabel, String[] suggestedModels) {
        if (detectedProviderLabel == null) {
            detectedProviderLabel = "";
        }
        if (suggestedModels == null || suggestedModels.length == 0) {
            aiLastDetectedProviderLabel = detectedProviderLabel;
            return;
        }

        if (detectedProviderLabel.equals(aiLastDetectedProviderLabel)) {
            return;
        }

        String currentModel = aiModel.get();
        boolean shouldAutofill = currentModel == null
                || currentModel.isBlank()
                || !isModelInSuggestions(currentModel, suggestedModels);

        if (shouldAutofill) {
            aiModel.set(suggestedModels[0]);
            aiSettingsStatusMessage = "Provider changed to " + detectedProviderLabel
                    + ", model auto-filled: " + suggestedModels[0];
        }

        aiLastDetectedProviderLabel = detectedProviderLabel;
    }

    private boolean isModelInSuggestions(String model, String[] suggestedModels) {
        if (model == null || suggestedModels == null) {
            return false;
        }
        for (String suggestion : suggestedModels) {
            if (suggestion != null && suggestion.equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }

    private AiGraphPlanDslAdapterService.GraphPlan toServiceGraphPlanForHistory(AiGraphPlan plan) {
        if (plan == null) {
            return new AiGraphPlanDslAdapterService.GraphPlan("", List.of(), List.of(), List.of());
        }

        List<AiGraphPlanDslAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphPlanDslAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphPlanDslAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
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
                plan.validationErrors() == null ? List.of() : plan.validationErrors()
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
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
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

        List<AiPlanApplyCoordinatorService.PlanNode> applyNodes = new ArrayList<>(nodesToApply.size());
        for (AiPlanNode node : nodesToApply) {
            applyNodes.add(new AiPlanApplyCoordinatorService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiPlanApplyCoordinatorService.PlanConnection> applyConnections = new ArrayList<>(pendingAiPlan.connections().size());
        for (AiPlanConnection connection : pendingAiPlan.connections()) {
            applyConnections.add(new AiPlanApplyCoordinatorService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        AiPlanApplyCoordinatorService.ApplyResult result = AiPlanApplyCoordinatorService.applyExact(
                editor,
                applyNodes,
                applyConnections,
                anchor
        );

        if (result.success()) {
            lastAiUndoStepCount = result.undoSteps();
        }

        aiPlanStatusMessage = result.statusMessage()
                + (result.success() && aiAutoLayoutBeforeApply.get() ? " (auto layout enabled)" : "");
    }

    private void applyPendingAiPlanPatch(ImGuiNodeEditor editor, List<AiPlanNode> nodesToApply, float[] anchor) {
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        NodeGraph graph = getNodeGraph();
        if (graph == null) {
            aiPlanStatusMessage = "Patch apply failed: current graph is unavailable.";
            return;
        }

        List<AiGraphApplyAdapterService.PlanNode> patchNodes = new ArrayList<>(nodesToApply.size());
        for (AiPlanNode node : nodesToApply) {
            patchNodes.add(new AiGraphApplyAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphApplyAdapterService.PlanConnection> patchConnections = new ArrayList<>(pendingAiPlan.connections().size());
        for (AiPlanConnection connection : pendingAiPlan.connections()) {
            patchConnections.add(new AiGraphApplyAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        AiGraphApplyAdapterService.PatchPayload payload =
                AiGraphApplyAdapterService.toPatchPayload(patchNodes, patchConnections);

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                editor,
                graph,
                payload.nodes(),
                payload.connections(),
                anchor,
                aiPatchRemoveScopedConnections.get()
        );
        lastAiUndoStepCount = result.undoSteps();
        aiPlanStatusMessage = result.statusMessage()
                + (result.success() && aiAutoLayoutBeforeApply.get() ? " (auto layout enabled for new nodes)" : "");
    }

    private void undoLastAiApply() {
        if (lastAiUndoStepCount <= 0) {
            aiPlanStatusMessage = "No AI apply operation to undo.";
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        int expectedUndoSteps = lastAiUndoStepCount;
        int undone = AiPlanApplyCoordinatorService.undo(editor, expectedUndoSteps);

        aiPlanStatusMessage = "Undo completed: " + undone + " / " + expectedUndoSteps + " steps.";
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

        List<AiPlanAutoLayoutService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiPlanAutoLayoutService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiPlanAutoLayoutService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiPlanAutoLayoutService.PlanConnection(
                    connection.sourceRef(),
                    connection.targetRef()
            ));
        }

        List<AiPlanAutoLayoutService.ArrangedNode> arranged = AiPlanAutoLayoutService.autoLayout(nodes, connections);
        List<AiPlanNode> result = new ArrayList<>(arranged.size());
        for (AiPlanAutoLayoutService.ArrangedNode node : arranged) {
            result.add(new AiPlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
        }
        return result;
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
                aiAssistantComponent.handleEvent("nodeSelected", node.getId());
            } else {
                NodeCraft.LOGGER.debug("属性面板已清除选中节点");
                aiAssistantComponent.handleEvent("nodeSelected", null);
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
        aiAssistantComponent.handleEvent(eventType, eventData);
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
