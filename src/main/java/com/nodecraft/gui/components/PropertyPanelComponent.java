package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft; // For logging
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
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
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalMergeNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.TagRelayNode;
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
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // 用于多线程安全的缓存
import java.util.stream.Collectors;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class PropertyPanelComponent implements EditorComponent {

    private static final String COMPONENT_ID = "property_panel";
    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";
    private static final String SET_PREFIX = "set";
    private static final Set<Class<?>> SYSTEM_DECLARING_CLASSES = Set.of(
            INode.class,
            BaseNode.class
    );
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
    private final PropertyEditorState editorState = new PropertyEditorState();

    // 缓存: Class -> List<PropertyDescriptor>
    private final Map<Class<?>, List<PropertyDescriptor>> propertyCache = new ConcurrentHashMap<>(); // 使用ConcurrentHashMap
    // 临时值存储, Key: nodeId_propertyName
    private final Map<String, Object> tempValues = new ConcurrentHashMap<>(); // 使用ConcurrentHashMap

    // 正在编辑的属性集合：Key: nodeId_propertyName -> 最后活跃时间戳
    // 用于防止节点计算覆盖用户正在输入的值，并支持精确的超时清理
    private final Map<String, Long> propertiesBeingEdited = new ConcurrentHashMap<>(); // 使用ConcurrentHashMap
    // 编辑锁定超时（毫秒），超过此时间没有新输入则自动释放锁
    private static final long EDIT_LOCK_TIMEOUT = 2000; // 2秒

    // 错误计数器：属性名 -> 错误次数 (针对当前 selectedNode 的属性)
    private final Map<String, Integer> errorCounts = new HashMap<>(); // 每次 selectedNode 切换时重置

    // 用于获取NodeGraph的接口，默认使用ImGuiNodeEditor
    private NodeGraphProvider graphProvider = () -> {
        try {
            return ImGuiNodeEditor.getInstance().getCurrentGraph();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("获取节点图失败", e);
            return null;
        }
    };

    // 可以提供一个接受NodeGraphProvider的构造函数，实现依赖注入
    public PropertyPanelComponent() {
        // 使用默认的graphProvider
    }

    private void applyPropertyValue(INode node, PropertyDescriptor prop, Object value) throws Throwable {
        prop.setter.invoke(node, value);
        if (node instanceof BaseCustomUINode customUINode) {
            customUINode.markDirty();
        }
    }

    // 内部类：属性描述符
    static class PropertyDescriptor {
        final String name; // 属性的规范名称, e.g., "nodeName"
        final String displayName; // UI显示的名称, e.g., "Node Name"
        final Class<?> type;
        final MethodAccessor getter;
        final MethodAccessor setter; // Setter可以为null，表示只读属性
        final PropertyRenderer renderer;
        final String description; // 属性描述，用于工具提示
        final String category; // 属性分类
        final int order; // 排序顺序
        // boolean disabled; // 禁用状态由 errorCounts 和当前节点选择决定，不存储在描述符中

        PropertyDescriptor(String name, String displayName, Class<?> type, MethodAccessor getter, MethodAccessor setter,
                           PropertyRenderer renderer, String description, String category, int order) {
            this.name = name;
            this.displayName = displayName;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
            this.renderer = renderer;
            this.description = description;
            this.category = category;
            this.order = order;
            // this.disabled = false; // 移除此字段
        }
    }

    // 函数式接口：属性渲染器
    @FunctionalInterface
    private interface PropertyRenderer {
        /**
         * 渲染属性的UI控件.
         * @param panel 当前属性面板实例 (用于访问 tempValues 等)
         * @param node 当前选中的节点
         * @param prop 当前属性的描述符
         * @param isDisabled 当前属性是否因为错误而被禁用
         */
        void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled);
    }

    // 接口：方法访问器
    interface MethodAccessor {
        Object invoke(Object obj, Object... args) throws Throwable; // 统一为 Throwable，因为 MethodHandle.invoke 可以抛出任意 Throwable
        Class<?> getReturnType();
        Class<?>[] getParameterTypes();
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
            boolean isReadOnly = prop.setter == null;
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

            if (isReadOnly) ImGui.beginDisabled();
            if (ImGui.dragFloat("##" + prop.name, valArr, 0.01f)) {
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
            String[] names = Arrays.stream(values)
                    .map(Enum::name) // ImGui.combo 使用的是字符串名称
                    .toArray(String[]::new);

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
                StringBuilder tooltip = new StringBuilder("可用值:\n");
                for (String name : names) {
                    tooltip.append("- ").append(name).append("\n");
                }
                ImGui.setTooltip(tooltip.toString());
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
            if (delegate != null && delegate != fallbackGeometryRenderer) {
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
        propertyCache.clear(); // 清空所有缓存
        errorCounts.clear(); // 清空错误计数
        propertiesBeingEdited.clear(); // 清空编辑锁
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

            ImGui.text("Properties");
            ImGui.separator();

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

        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to render property panel", e);
            ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "Render error: " + e.getMessage());
        } finally {
            ImGui.popStyleVar();
        }
    }
    private void renderNodeInfo() {
        String typeId = selectedNode.getTypeId();
        String categoryName = getCategoryNameForNode(typeId);

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

        String nodeStatus = getNodeStatus();
        ImVec4 statusColor = getStatusColor(nodeStatus);

        ImGui.textColored(statusColor.x, statusColor.y, statusColor.z, statusColor.w, nodeStatus);

        if (nodeStatus.equals("Error") || nodeStatus.equals("Warning")) {
            ImGui.sameLine();
            ImGui.textDisabled("(?)");
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 22.0f);
                ImGui.textUnformatted(getNodeStatusMessage());
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
        }
    }
    /**
     * 获取节点所属的分类名称
     * @param typeId 节点类型ID
     * @return 分类显示名称
     */
    private String getCategoryNameForNode(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return "Unknown";
        }

        // 从typeId中提取分类部分
        String categoryId = "";
        int firstDotIndex = typeId.indexOf('.');
        if (firstDotIndex != -1) {
            categoryId = typeId.substring(0, firstDotIndex);
        }

        try {
            // 获取NodeRegistry实例
            com.nodecraft.nodesystem.registry.NodeRegistry registry = com.nodecraft.nodesystem.registry.NodeRegistry.getInstance();
            if (registry != null) {
                // 尝试获取分类对象
                com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory category = registry.getCategory(categoryId);
                if (category != null) {
                    // 返回分类的显示名称
                    return category.getDisplayName();
                }
            }
        } catch (Exception e) {
            // 如果发生错误，记录日志并使用备用格式化
            NodeCraft.LOGGER.error("Failed to resolve node category: {}", e.getMessage());
        }

        // 如果无法通过注册表获取分类，则进行简单格式化
        if (!categoryId.isEmpty()) {
            return formatSingleWord(categoryId); // 使用 formatSingleWord 来格式化一级分类
        }

        // 回退：尝试使用类型ID的第一部分作为分类
        String[] parts = typeId.split("\\.");
        return parts.length > 0 ? formatSingleWord(parts[0]) : "Uncategorized";
    }

    /**
     * 格式化单个单词（例如，将 "my_category" 格式化为 "My Category"）
     */
    private String formatSingleWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        // 将下划线替换为空格，然后转换为驼峰式（每个单词首字母大写）
        String[] parts = word.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                formatted.append(Character.toUpperCase(part.charAt(0)));
                formatted.append(part.substring(1).toLowerCase());
                formatted.append(" "); // 每个单词后添加空格
            }
        }
        return formatted.toString().trim(); // 去掉尾部空格
    }

    // 获取节点状态 (示例实现，需要根据实际节点系统修改)
    private String getNodeStatus() {
        if (selectedNode == null) return "Unselected";

        try {
            Method getErrorMethod = selectedNode.getClass().getMethod("getErrorState");
            Object errorState = getErrorMethod.invoke(selectedNode);
            if (errorState instanceof String && !((String) errorState).isEmpty()) {
                return "Error";
            }
            if (errorState instanceof Boolean && (Boolean) errorState) {
                return "Error";
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }

        boolean hasOutputPorts = !selectedNode.getOutputPorts().isEmpty();
        boolean allOutputsConnected = true;
        if (hasOutputPorts) {
            for (IPort port : selectedNode.getOutputPorts()) {
                if (!port.isConnected()) {
                    allOutputsConnected = false;
                    break;
                }
            }
            if (!allOutputsConnected) {
                return "Warning";
            }
        }

        boolean hasInputPorts = !selectedNode.getInputPorts().isEmpty();
        boolean allInputsReady = true;
        if (hasInputPorts) {
            for (IPort port : selectedNode.getInputPorts()) {
                if (!port.isConnected() && port.getValue() == null) {
                    allInputsReady = false;
                    break;
                }
            }
            if (!allInputsReady) {
                return "Warning";
            }
        }

        if (selectedNode instanceof BaseNode) {
            Object nodeState = selectedNode.getNodeState();
            if (nodeState instanceof Map<?, ?> stateMap && stateMap.containsKey("status")) {
                Object status = stateMap.get("status");
                if (status instanceof String statusString) {
                    if (statusString.equals("calculating")) return "Calculating";
                    if (statusString.equals("disabled")) return "Disabled";
                }
            }
        }

        return "Ready";
    }

    private String getNodeStatusMessage() {
        String status = getNodeStatus();

        return switch (status) {
            case "Error" -> "The node failed to evaluate and could not produce a valid result.";
            case "Warning" -> "The node has missing links or incomplete input/output state.";
            case "Calculating" -> "The node is currently evaluating.";
            case "Disabled" -> "The node is disabled and will not participate in execution.";
            case "Ready" -> "The node is ready.";
            case "Unselected" -> "No node is currently selected.";
            default -> "Unknown node status.";
        };
    }

    private ImVec4 getStatusColor(String status) {
        return switch (status) {
            case "Error" -> new ImVec4(1.0f, 0.3f, 0.3f, 1.0f);
            case "Warning" -> new ImVec4(1.0f, 0.9f, 0.3f, 1.0f);
            case "Calculating" -> new ImVec4(0.3f, 0.7f, 1.0f, 1.0f);
            case "Disabled" -> new ImVec4(0.5f, 0.5f, 0.5f, 1.0f);
            case "Ready" -> new ImVec4(0.3f, 0.9f, 0.3f, 1.0f);
            default -> new ImVec4(1.0f, 1.0f, 1.0f, 1.0f);
        };
    }
    private void renderNodeProperties() {
        if (selectedNode == null) return;

        renderAssistNodeControls();

        List<PropertyDescriptor> properties = propertyInspector.getPropertiesForNode(selectedNode.getClass()).stream()
                .filter(prop -> !HIDDEN_NODE_PROPERTIES.contains(prop.name))
                .toList();
        if (properties.isEmpty()) {
            ImGui.textDisabled("No editable properties");
            return;
        }

        Map<String, List<PropertyDescriptor>> groupedProperties = properties.stream()
                .collect(Collectors.groupingBy(prop -> normalizePropertyCategory(prop.category)));

        if (groupedProperties.containsKey("")) {
            List<PropertyDescriptor> uncategorizedProps = groupedProperties.remove("");
            renderPropertyGroup(uncategorizedProps, "General");
        }

        List<String> categories = new ArrayList<>(groupedProperties.keySet());
        categories.sort(Comparator.naturalOrder());

        for (String category : categories) {
            String formattedCategory = formatPropertyCategory(category);
            if (!formattedCategory.isEmpty()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.72f, 0.76f, 0.82f, 1.0f);
                boolean open = ImGui.collapsingHeader(formattedCategory, ImGuiTreeNodeFlags.DefaultOpen);
                ImGui.popStyleColor();
                if (open) {
                    renderPropertyGroup(groupedProperties.get(category), category);
                }
            }
        }
    }

    private void renderAssistNodeControls() {
        if (selectedNode instanceof SignalForkNode forkNode) {
            renderSignalForkControls(forkNode);
            ImGui.separator();
        }

        if (selectedNode instanceof SignalMergeNode mergeNode) {
            renderSignalMergeControls(mergeNode);
            ImGui.separator();
        }

        if (selectedNode instanceof TagRelayNode) {
            renderTagRelayRuleHint();
            ImGui.separator();
        }
    }

    private void renderSignalForkControls(SignalForkNode forkNode) {
        ImGui.text("Branch Controls");
        ImGui.textDisabled("Output branches: " + forkNode.getOutputBranchCount() + " (1-8)");

        boolean canRemove = forkNode.canDecreaseOutputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Output")) {
            String removedPortId = forkNode.removeLastOutputBranch();
            if (removedPortId != null) {
                removeConnectionsForPort(forkNode.getId(), removedPortId, false);
            }
        }
        if (!canRemove) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean canAdd = forkNode.canIncreaseOutputBranch();
        if (!canAdd) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("+ Output")) {
            forkNode.addOutputBranch();
        }
        if (!canAdd) {
            ImGui.endDisabled();
        }
    }

    private void renderSignalMergeControls(SignalMergeNode mergeNode) {
        ImGui.text("Branch Controls");
        ImGui.textDisabled("Input branches: " + mergeNode.getInputBranchCount() + " (2-8)");

        boolean canRemove = mergeNode.canDecreaseInputBranch();
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("- Input")) {
            String removedPortId = mergeNode.removeLastInputBranch();
            if (removedPortId != null) {
                removeConnectionsForPort(mergeNode.getId(), removedPortId, true);
            }
        }
        if (!canRemove) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean canAdd = mergeNode.canIncreaseInputBranch();
        if (!canAdd) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("+ Input")) {
            mergeNode.addInputBranch();
        }
        if (!canAdd) {
            ImGui.endDisabled();
        }
    }

    private void renderTagRelayRuleHint() {
        ImGui.text("Tag Relay Rules");
        ImGui.textWrapped("Color supports #RRGGBB/#AARRGGBB, named tokens (danger/warn/io/math/flow/debug), or auto by tag keywords.");
        ImGui.textDisabled("Canvas shows short tag label with mapped color.");
    }

    private void removeConnectionsForPort(UUID nodeId, String portId, boolean inputPort) {
        NodeGraph graph = getNodeGraph();
        if (graph == null || portId == null) {
            return;
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
            boolean matched;
            if (inputPort) {
                matched = connection.targetNode.getId().equals(nodeId)
                    && connection.targetPort.getId().equals(portId);
            } else {
                matched = connection.sourceNode.getId().equals(nodeId)
                    && connection.sourcePort.getId().equals(portId);
            }

            if (matched) {
                graph.removeConnection(connection);
            }
        }
    }

    private String normalizePropertyCategory(String category) {
        if (category == null) {
            return "";
        }
        String trimmed = category.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return switch (trimmed) {
            case "精度", "数值", "鏁板€?" -> "数值";
            case "UI设置", "UI璁剧疆" -> "UI设置";
            case "范围", "鑼冨洿" -> "范围";
            default -> trimmed;
        };
    }

    private String formatPropertyCategory(String category) {
        String normalized = normalizePropertyCategory(category);
        return switch (normalized) {
            case "UI设置" -> "UI Settings";
            case "数值" -> "Values";
            case "范围" -> "Range";
            default -> formatSingleWord(normalized);
        };
    }

    private void renderPropertyGroup(List<PropertyDescriptor> props, String categoryInternalName) {
        if (ImGui.beginTable("propertiesTable_" + categoryInternalName, 2,
                ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersOuter)) {
            ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthFixed, ImGui.getContentRegionAvailX() * 0.32f);
            ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableHeadersRow();

            props.sort(Comparator.comparingInt((PropertyDescriptor p) -> p.order).thenComparing(p -> p.displayName));

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
                PropertyRenderer renderer = prop.renderer != null ? prop.renderer : getRendererForType(prop.type);
                renderer.render(this, selectedNode, prop, isDisabled);
                ImGui.popItemWidth();
                ImGui.popID();
            }
            ImGui.endTable();
        }
    }
    private void renderActionButtons() {
        ImGui.separator();

        if (ImGui.button("Reset Properties")) {
            clearCurrentNodeTempValues();
            if (selectedNode instanceof BaseNode) {
                try {
                    Method resetMethod = selectedNode.getClass().getMethod("resetProperties");
                    resetMethod.invoke(selectedNode);
                    NodeCraft.LOGGER.info("Reset node properties for {}", selectedNode.getDisplayName());
                } catch (NoSuchMethodException e) {
                    NodeCraft.LOGGER.debug("Node {} does not expose resetProperties()", selectedNode.getDisplayName());
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("Failed to reset node properties for {}: {}", selectedNode.getDisplayName(), e.getMessage());
                }
            }
        }

        ImGui.sameLine();

        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 0.6f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1.0f, 0.4f, 0.4f, 1.0f);
        if (ImGui.button("Delete Node")) {
            NodeGraph graph = getNodeGraph();
            if (graph != null) {
                boolean success = graph.removeNode(selectedNode.getId());
                if (success) {
                    NodeCraft.LOGGER.info("Removed node from graph: {}", selectedNode.getDisplayName());
                    setSelectedNode(null);
                } else {
                    NodeCraft.LOGGER.warn("Failed to remove node from graph: {}", selectedNode.getDisplayName());
                }
            }
        }
        ImGui.popStyleColor(3);
    }

    /**
     * 清理当前选中节点的临时值
     */
    private void clearCurrentNodeTempValues() {
        if (selectedNode == null) return;

        // 只清理当前选中节点的临时值
        String nodeIdPrefix = selectedNode.getId().toString() + "_";
        tempValues.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));

        // 清理编辑状态
        propertiesBeingEdited.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));

        errorCounts.clear(); // 清理当前节点的错误计数
    }

    /**
     * 清理所有临时值
     */
    private void clearAllTempValues() {
        tempValues.clear();
        propertiesBeingEdited.clear();
        errorCounts.clear(); // 清空所有错误计数
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
        if (graphProvider != null) {
            try {
                return graphProvider.getCurrentGraph();
            } catch (Exception e) {
                NodeCraft.LOGGER.error("NodeGraphProvider 获取图时发生错误", e);
                return null;
            }
        }
        NodeCraft.LOGGER.warn("NodeGraphProvider 未设置, 无法获取节点图。PropertyPanelComponent 可能无法正常工作。");
        return null;
    }

    // 函数式接口：用于从外部提供 NodeGraph 实例
    @FunctionalInterface
    public interface NodeGraphProvider {
        NodeGraph getCurrentGraph();
    }

    // 创建getter访问器 - 使用MethodHandle改进性能
    private static MethodAccessor createFieldGetter(Field field) {
        try {
            return new FieldHandleGetter(field);
        } catch (Throwable e) { // 捕获 Throwable
            NodeCraft.LOGGER.warn("创建MethodHandle getter for field {} 失败, 回退到传统反射: {}", field.getName(), e.getMessage());
            return new FieldGetter(field);
        }
    }

    // 创建setter访问器 - 使用MethodHandle改进性能
    private static MethodAccessor createFieldSetter(Field field) {
        if (false) return null;
        try {
            return new FieldHandleSetter(field);
        } catch (Throwable e) { // 捕获 Throwable
            NodeCraft.LOGGER.warn("创建MethodHandle setter for field {} 失败, 回退到传统反射: {}", field.getName(), e.getMessage());
            return new FieldSetter(field);
        }
    }

    // 使用MethodHandle实现的Method访问器
    private static class MethodHandleAccessorImpl implements MethodAccessor { // 重命名为 Impl 以避免与 MethodWrapper 混淆
        private final MethodHandle handle;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

        public MethodHandleAccessorImpl(Method method) {
            try {
                // 获取方法的MethodHandle
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                // setAccessible(true) 是为了访问非公共方法，MethodHandle 默认不检查
                method.setAccessible(true); // 确保 MethodHandle 也能访问私有/保护方法
                handle = lookup.unreflect(method);

                // 保存返回类型和参数类型
                returnType = method.getReturnType();
                parameterTypes = method.getParameterTypes();
            } catch (IllegalAccessException e) {
                // 如果 MethodHandle 无法创建 (例如安全管理器限制)，抛出 RuntimeException
                throw new RuntimeException("Failed to create MethodHandle for " + method, e);
            }
        }

    @Override
        public Object invoke(Object obj, Object... args) throws Throwable { // 统一为 Throwable
            if (args == null || args.length == 0) {
                return handle.invoke(obj);
            } else if (args.length == 1) {
                // 对于单个参数的情况，直接传递参数值，避免数组包装问题
                return handle.invoke(obj, args[0]);
            } else {
                // 对于多个参数的情况，使用 invokeWithArguments
                Object[] invokeArgs = new Object[args.length + 1];
                invokeArgs[0] = obj;
                System.arraycopy(args, 0, invokeArgs, 1, args.length);
                return handle.invokeWithArguments(invokeArgs);
            }
        }

    @Override
        public Class<?> getReturnType() {
            return returnType;
        }

    @Override
        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }
    }

    // 使用MethodHandle实现的Field Getter访问器
    private static class FieldHandleGetter implements MethodAccessor {
        private final MethodHandle getter;
        private final Class<?> fieldType;

        public FieldHandleGetter(Field field) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                field.setAccessible(true); // 确保 MethodHandle 也能访问私有字段
                getter = lookup.unreflectGetter(field);
                fieldType = field.getType();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to create MethodHandle getter for field " + field, e);
            }
        }

    @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            return getter.invoke(obj);
        }

    @Override
        public Class<?> getReturnType() {
            return fieldType;
        }

    @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[0];
        }
    }

    // 使用MethodHandle实现的Field Setter访问器
    private static class FieldHandleSetter implements MethodAccessor {
        private final MethodHandle setter;
        private final Class<?> fieldType;

        public FieldHandleSetter(Field field) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                field.setAccessible(true); // 确保 MethodHandle 也能访问私有字段
                setter = lookup.unreflectSetter(field);
                fieldType = field.getType();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to create MethodHandle setter for field " + field, e);
            }
        }

    @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("Setter requires a value argument");
            }
            setter.invoke(obj, args[0]);
            return null; // Setter方法没有返回值
        }

    @Override
        public Class<?> getReturnType() {
            return void.class;
        }

    @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[] { fieldType };
        }
    }

    // 方法访问器包装器 (使用MethodHandle，作为首选)
    private static class MethodWrapper implements MethodAccessor {
        private final MethodHandleAccessorImpl methodHandleAccessor;

        public MethodWrapper(Method method) {
            methodHandleAccessor = new MethodHandleAccessorImpl(method);
        }

    @Override
        public Object invoke(Object obj, Object... args) throws Throwable {
            return methodHandleAccessor.invoke(obj, args);
        }

    @Override
        public Class<?> getReturnType() {
            return methodHandleAccessor.getReturnType();
        }

    @Override
        public Class<?>[] getParameterTypes() {
            return methodHandleAccessor.getParameterTypes();
        }
    }

    // 字段Getter访问器 (传统反射实现，作为后备)
    private static class FieldGetter implements MethodAccessor {
        private final Field field;

        public FieldGetter(Field field) {
            this.field = field;
            field.setAccessible(true);
        }

    @Override
        public Object invoke(Object obj, Object... args) throws IllegalAccessException {
            return field.get(obj);
        }

    @Override
        public Class<?> getReturnType() {
            return field.getType();
        }

    @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[0];
        }
    }

    // 字段Setter访问器 (传统反射实现，作为后备)
    private static class FieldSetter implements MethodAccessor {
        private final Field field;

        public FieldSetter(Field field) {
            this.field = field;
            field.setAccessible(true);
        }

    @Override
        public Object invoke(Object obj, Object... args) throws IllegalAccessException {
            if (args != null && args.length > 0) {
                field.set(obj, args[0]);
            }
            return null;
        }

    @Override
        public Class<?> getReturnType() {
            return void.class;
        }

    @Override
        public Class<?>[] getParameterTypes() {
            return new Class<?>[] { field.getType() };
        }
    }

    // 新增：渲染输入端口信息
    private void renderInputPorts() {
        List<IPort> inputPorts = selectedNode.getInputPorts();
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

                if (port.isConnected()) {
                    NodeGraph graph = getNodeGraph();
                    if (graph != null) {
                        UUID sourceNodeId = graph.getConnectedOutputNodeId(selectedNode.getId(), port.getId());
                        String sourcePortId = graph.getConnectedOutputPortId(selectedNode.getId(), port.getId());

                        if (sourceNodeId != null) {
                            INode sourceNode = graph.getNode(sourceNodeId);
                            if (sourceNode != null) {
                                ImGui.textDisabled("Connected from: " + sourceNode.getDisplayName());

                                for (IPort sourcePort : sourceNode.getOutputPorts()) {
                                    if (sourcePort.getId().equals(sourcePortId)) {
                                        Object value = resolveNodeOutput(graph, sourceNode, sourcePortId, new HashSet<>());
                                        ImGui.textWrapped(formatValuePreview(value));
                                        if (ImGui.isItemHovered()) {
                                            ImGui.setTooltip(formatValueDetails(value, "Input Data"));
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Object value = port.getValue();
                    ImGui.textWrapped(formatValuePreview(value));
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(formatValueDetails(value, "Default Value"));
                    }
                }
            }
            ImGui.endTable();
        }
    }
    private void renderOutputPorts() {
        List<IPort> outputPorts = selectedNode.getOutputPorts();
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

                NodeGraph graph = getNodeGraph();
                Object value = graph != null
                        ? resolveNodeOutput(graph, selectedNode, port.getId(), new HashSet<>())
                        : selectedNode.getOutput(port.getId());
                if (value != null) {
                    ImGui.textWrapped(formatValuePreview(value));
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(formatValueDetails(value, "Output Data"));
                    }
                } else {
                    value = port.getValue();
                    ImGui.textWrapped(formatValuePreview(value));
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(formatValueDetails(value, "Current Value"));
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

    private String formatValuePreview(Object value) {
        if (value == null) {
            return "(empty)";
        }

        String preview = switch (value) {
            case Collection<?> collection -> "Collection (" + collection.size() + ")";
            case Map<?, ?> map -> "Map (" + map.size() + ")";
            case Vec3 vec -> String.format("Vec3(%.2f, %.2f, %.2f)", vec.getX(), vec.getY(), vec.getZ());
            default -> value.toString();
        };

        return preview.length() > 96 ? preview.substring(0, 93) + "..." : preview;
    }

    private String formatValueDetails(Object value, String label) {
        if (value == null) {
            return label + ": (empty)";
        }

        String typeName = value.getClass().getSimpleName();
        String rendered = value.toString();
        if (rendered.length() > 600) {
            rendered = rendered.substring(0, 597) + "...";
        }
        return label + "\nType: " + typeName + "\nValue: " + rendered;
    }
    private void renderPortData(Object value, String label) {
        if (value == null) {
            ImGui.textDisabled("(empty)");
            return;
        }

        // 使用一个通用的树节点来包裹，以便于折叠
        // 确保每个树节点都有唯一ID，以防多处调用导致ID冲突
        String treeNodeId = "portData_" + label + "_" + value.hashCode(); // 简单hash，可能冲突，但对于大多数情况够用

        if (ImGui.treeNodeEx(treeNodeId, ImGuiTreeNodeFlags.SpanAvailWidth)) { // 展开时占满宽度
            try {
                // 根据值类型渲染不同的显示方式
                String className = value.getClass().getSimpleName();

                // 优先处理通用类型
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    ImGui.text(label + ": " + value);
                } else if (value instanceof List<?> list) {
                    renderList(list, label);
                } else if (value instanceof Map<?, ?> map) {
                    renderMap(map, label);
                } else if (value instanceof Vec3 vec) {
                    renderVec3(vec, label);
                }
                // 再处理特定领域对象，如果存在
                else if (className.equals("BlockInfo")) { // 假设 BlockInfo 类存在
                    renderBlockInfo(value, label);
                } else if (className.equals("MinecraftBlock")) { // 假设 MinecraftBlock 类存在
                    renderMinecraftBlock(value, label);
                } else if (className.equals("ItemStack")) { // 假设 ItemStack 类存在
                    renderItemStack(value, label);
                } else if (className.equals("Region")) { // 假设 Region 类存在
                    renderRegion(value, label);
                } else if (className.equals("CompoundTag") || className.equals("CompoundNBT")) { // 假设 NBT 类存在
                    renderNBT(value, label);
                } else {
                    // 其他类型直接显示 toString()
                    ImGui.text(label + ": " + value);
                }
            } catch (Exception e) {
                ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to render port data: " + e.getMessage());
            } finally {
                ImGui.treePop();
            }
        }
    }

    // 渲染BlockInfo数据
    private void renderBlockInfo(Object blockInfo, String label) {
        try {
            Method getIdMethod = blockInfo.getClass().getMethod("getId");
            Method getNameMethod = blockInfo.getClass().getMethod("getName");
            Method getPositionMethod = blockInfo.getClass().getMethod("getPosition");

            Object id = getIdMethod.invoke(blockInfo);
            Object name = getNameMethod.invoke(blockInfo);
            Object position = getPositionMethod.invoke(blockInfo);

            ImGui.text("Block ID: " + id);
            ImGui.text("Name: " + name);
            ImGui.text("Position: " + position);

            if (ImGui.button("Highlight In World")) {
                if (position instanceof Vec3 pos) {
                    highlightPoint(pos);
                }
            }
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "BlockInfo is missing required methods (getId/getName/getPosition).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read block info: " + e.getMessage());
        }
    }

    // 渲染MinecraftBlock数据
    private void renderMinecraftBlock(Object block, String label) {
        try {
            Method getIdMethod = block.getClass().getMethod("getId");
            Method getNameMethod = block.getClass().getMethod("getName");

            Object id = getIdMethod.invoke(block);
            Object name = getNameMethod.invoke(block);

            ImGui.text("Block ID: " + id);
            ImGui.text("Name: " + name);
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "MinecraftBlock is missing required methods (getId/getName).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read block info: " + e.getMessage());
        }
    }

    // 渲染ItemStack数据
    private void renderItemStack(Object itemStack, String label) {
        try {
            Method getItemMethod = itemStack.getClass().getMethod("getItem");
            Method getCountMethod = itemStack.getClass().getMethod("getCount");
            Method getNameMethod = null;
            try {
                getNameMethod = itemStack.getClass().getMethod("getName");
            } catch (NoSuchMethodException ignored) {
            }

            Object item = getItemMethod.invoke(itemStack);
            Object count = getCountMethod.invoke(itemStack);
            Object name = null;
            if (getNameMethod != null) {
                name = getNameMethod.invoke(itemStack);
            }

            ImGui.text("Item: " + (name != null ? name : item));
            ImGui.text("Count: " + count);

            if (ImGui.button("Copy Give Command")) {
                String itemId = (item != null) ? item.toString() : "minecraft:air";
                String command = "/give @p " + itemId + " " + count;
                copyToClipboard(command);
            }
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "ItemStack is missing required methods (getItem/getCount).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read item info: " + e.getMessage());
        }
    }

    // 渲染Region数据
    private void renderRegion(Object region, String label) {
        try {
            Method getMinMethod = region.getClass().getMethod("getMin");
            Method getMaxMethod = region.getClass().getMethod("getMax");
            Method getBlockCountMethod = region.getClass().getMethod("getBlockCount");

            Object min = getMinMethod.invoke(region);
            Object max = getMaxMethod.invoke(region);
            Object blockCount = getBlockCountMethod.invoke(region);

            if (min instanceof Vec3 minVec && max instanceof Vec3 maxVec) {
                ImGui.text(String.format("Min Corner: (%.1f, %.1f, %.1f)", minVec.getX(), minVec.getY(), minVec.getZ()));
                ImGui.text(String.format("Max Corner: (%.1f, %.1f, %.1f)", maxVec.getX(), maxVec.getY(), maxVec.getZ()));
            } else {
                ImGui.text("Min Corner: " + min);
                ImGui.text("Max Corner: " + max);
            }

            ImGui.text("Block Count: " + blockCount);

            if (ImGui.button("Highlight Region")) {
                highlightRegion(region);
            }
        } catch (NoSuchMethodException e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Region is missing required methods (getMin/getMax/getBlockCount).");
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read region info: " + e.getMessage());
        }
    }

    // 渲染NBT数据
    private void renderNBT(Object nbt, String label) {
        try {
            ImGui.textWrapped(nbt.toString());

            if (ImGui.button("Copy NBT")) {
                copyToClipboard(nbt.toString());
            }
        } catch (Exception e) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Failed to read NBT data: " + e.getMessage());
        }
    }

    // 增强List渲染，根据列表项类型使用专门的渲染逻辑
    private void renderList(List<?> list, String label) {
        int size = list.size();
        if (ImGui.treeNode(label + ": List (" + size + " items)")) {
            int displayLimit = Math.min(size, 10);

            if (!list.isEmpty()) {
                for (int i = 0; i < displayLimit; i++) {
                    Object item = list.get(i);
                    ImGui.pushID(i);
                    if (item == null) {
                        ImGui.text(String.format("[%d] null", i));
                    } else {
                        renderPortData(item, String.format("[%d]", i));
                    }
                    ImGui.popID();
                }
            }

            if (size > displayLimit) {
                if (ImGui.button("View All " + size + " Items...")) {
                    ImGui.openPopup("Full List: " + label);
                }
            }

            if (!list.isEmpty() && list.getFirst() instanceof Vec3) {
                if (ImGui.button("Preview Point Set")) {
                    highlightPoints(list);
                }
            }

            if (ImGui.beginPopup("Full List: " + label)) {
                ImGui.text("Full List (" + size + " items)");
                ImGui.separator();

                float heightLimit = ImGui.getWindowHeight() * 0.6f;
                float childHeight = Math.min(heightLimit, size * (ImGui.getFontSize() + ImGui.getStyle().getItemSpacingY()) + ImGui.getStyle().getWindowPaddingY() * 2);

                if (ImGui.beginChild("ListContent_" + label, ImGui.getContentRegionAvailX(), childHeight, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
                    for (int i = 0; i < size; i++) {
                        Object item = list.get(i);
                        ImGui.text(String.format("[%d] %s", i, item != null ? item.toString() : "null"));
                    }
                    ImGui.endChild();
                }

                ImGui.separator();
                if (ImGui.button("Close")) {
                    ImGui.closeCurrentPopup();
                }

                ImGui.endPopup();
            }
        }
    }

    // 增强Map渲染
    private void renderMap(Map<?, ?> map, String label) {
        int size = map.size();
        if (ImGui.treeNode(label + ": Map (" + size + " entries)")) {
            if (ImGui.beginTable("mapTable_" + label, 2, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingFixedFit)) {
                ImGui.tableSetupColumn("Key");
                ImGui.tableSetupColumn("Value");
                ImGui.tableHeadersRow();

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object value = entry.getValue();

                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    ImGui.text(key != null ? key.toString() : "null");

                    ImGui.tableSetColumnIndex(1);
                    if (value == null) {
                        ImGui.textDisabled("null");
                    } else {
                        renderPortData(value, "");
                    }
                }
                ImGui.endTable();
            }
        }
    }

    // 渲染Vec3数据
    private void renderVec3(Vec3 vec, String label) {
        ImGui.text(String.format("X: %.2f, Y: %.2f, Z: %.2f", vec.getX(), vec.getY(), vec.getZ()));

        if (ImGui.button("Copy To Clipboard")) {
            String vecStr = String.format("%.2f %.2f %.2f", vec.getX(), vec.getY(), vec.getZ());
            copyToClipboard(vecStr);
        }

        ImGui.sameLine();

        if (ImGui.button("Preview Point")) {
            highlightPoint(vec);
        }
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

    // 清除节点预览
    private void clearNodePreview() {
        if (selectedNode == null) return;

        NodeCraft.LOGGER.info("Clear node preview: {}", selectedNode.getId());
    }

    private Map<String, Object> collectConnectedInputs(NodeGraph graph, INode node, Set<UUID> visiting) {
        Map<String, Object> inputs = new HashMap<>();
        if (graph == null || node == null) {
            return inputs;
        }

        Map<String, IPort> inputPortsById = new HashMap<>();
        for (IPort port : node.getInputPorts()) {
            inputPortsById.put(port.getId(), port);
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (connection.targetNode.getId().equals(node.getId())) {
                Object value = resolveNodeOutput(graph, connection.sourceNode, connection.sourcePort.getId(), visiting);
                mergeCollectedInput(inputs, inputPortsById.get(connection.targetPort.getId()), value);
            }
        }
        return inputs;
    }

    private void mergeCollectedInput(Map<String, Object> inputs, IPort targetPort, Object value) {
        if (targetPort == null) {
            return;
        }

        String portId = targetPort.getId();
        if (targetPort.allowsMultipleIncomingConnections()) {
            List<Object> values = null;
            Object existing = inputs.get(portId);
            if (existing instanceof List<?> existingList) {
                values = new ArrayList<>(existingList);
            }
            if (values == null) {
                values = new ArrayList<>();
                if (existing != null) {
                    values.add(existing);
                }
            }
            values.add(value);
            inputs.put(portId, values);
            return;
        }

        inputs.put(portId, value);
    }

    private Object resolveNodeOutput(NodeGraph graph, INode node, String outputPortId, Set<UUID> visiting) {
        if (node == null || outputPortId == null) {
            return null;
        }

        if (visiting.contains(node.getId())) {
            Object cached = node.getOutput(outputPortId);
            return cached != null ? cached : getPortValue(node, outputPortId, false);
        }

        boolean hasIncomingConnections = false;
        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (connection.targetNode.getId().equals(node.getId())) {
                hasIncomingConnections = true;
                break;
            }
        }

        if (hasIncomingConnections) {
            visiting.add(node.getId());
            try {
                Map<String, Object> inputs = collectConnectedInputs(graph, node, visiting);
                for (IPort port : node.getInputPorts()) {
                    inputs.putIfAbsent(port.getId(), port.getValue());
                }
                node.compute(inputs);
            } finally {
                visiting.remove(node.getId());
            }
        }

        Object value = node.getOutput(outputPortId);
        return value != null ? value : getPortValue(node, outputPortId, false);
    }

    private Object getPortValue(INode node, String portId, boolean inputPort) {
        List<IPort> ports = inputPort ? node.getInputPorts() : node.getOutputPorts();
        for (IPort port : ports) {
            if (port.getId().equals(portId)) {
                return port.getValue();
            }
        }
        return null;
    }

    // 记录节点性能数据的字段
    private double lastExecutionTime = 0;
    private double averageExecutionTime = 0;
    private int executionCount = 0;

    // 添加这些方法来管理属性编辑状态

    /**
     * 标记属性为正在编辑状态
     * @param node 节点
     * @param propName 属性名
     */
    private void markPropertyBeingEdited(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.put(key, System.currentTimeMillis());
        NodeCraft.LOGGER.trace("属性 {} 标记为正在编辑。", key);
    }

    /**
     * 标记属性为编辑完成状态
     * @param node 节点
     * @param propName 属性名
     */
    private void markPropertyEditingFinished(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.remove(key);
        NodeCraft.LOGGER.trace("属性 {} 标记为编辑完成。", key);
    }

    /**
     * 检查属性是否正在被编辑 (或编辑锁未过期)
     * @param node 节点
     * @param propName 属性名
     * @return 是否正在被编辑
     */
    private boolean isPropertyBeingEdited(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        Long timestamp = propertiesBeingEdited.get(key);
        if (timestamp == null) {
            return false;
        }
        // 检查是否过期
        if (System.currentTimeMillis() - timestamp > EDIT_LOCK_TIMEOUT) {
            propertiesBeingEdited.remove(key); // 移除过期锁
            NodeCraft.LOGGER.debug("属性 {} 的编辑锁已过期并移除。", key);
            return false;
        }
        return true;
    }

    /**
     * 检查和清理过期的编辑锁
     * 定期调用，移除所有超时的编辑锁
     */
    private void checkAndCleanExpiredEditLocks() {
        long currentTime = System.currentTimeMillis();
        // 使用迭代器安全地移除 Map 中的元素
        propertiesBeingEdited.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > EDIT_LOCK_TIMEOUT;
            if (expired) {
                NodeCraft.LOGGER.debug("清理过期编辑锁: {}", entry.getKey());
            }
            return expired;
        });
    }

    // 修改为使用节点ID和属性名作为键
    private String getTempValueKey(INode node, String propName) {
        return node.getId().toString() + "_" + propName;
    }

    private List<PropertyDescriptor> getPropertiesForNode(Class<?> nodeClass) {
        // 从缓存获取，如果不存在则计算并存入
        return propertyCache.computeIfAbsent(nodeClass, clazz -> {
            List<PropertyDescriptor> descriptors = new ArrayList<>();
            Map<String, Method> getters = new HashMap<>();
            Map<String, Method> setters = new HashMap<>();

            // 1. 处理标记有@NodeProperty注解的字段
            processAnnotatedFields(clazz, descriptors);

            // 2. 处理标记有@NodeProperty注解的方法
            processAnnotatedMethods(clazz, descriptors);

            // 3. 回退到JavaBean命名约定的方法查找
            // 遍历当前类和所有父类的公共方法
            Class<?> currentClass = clazz;
            while (currentClass != null && !currentClass.equals(Object.class)) {
                for (Method method : currentClass.getMethods()) {
                    // 过滤掉 Object 类的方法
                    if (method.getDeclaringClass().equals(Object.class)) {
                        continue;
                    }
                    if (SYSTEM_DECLARING_CLASSES.contains(method.getDeclaringClass())) {
                        continue;
                    }

                    String methodName = method.getName();
                    if (method.getParameterCount() == 0) { // Potential getter
                        if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
                            getters.put(extractPropertyName(methodName, GET_PREFIX), method);
                        } else if (methodName.startsWith(IS_PREFIX) && methodName.length() > IS_PREFIX.length() &&
                                (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class))) {
                            getters.put(extractPropertyName(methodName, IS_PREFIX), method);
                        }
                    } else if (method.getParameterCount() == 1 && methodName.startsWith(SET_PREFIX) &&
                            method.getReturnType().equals(void.class)) { // Potential setter
                        setters.put(extractPropertyName(methodName, SET_PREFIX), method);
                    }
                }
                if (currentClass.isRecord()) {
                    for (RecordComponent component : currentClass.getRecordComponents()) {
                        Method accessor = component.getAccessor();
                        if (accessor != null && !SYSTEM_DECLARING_CLASSES.contains(accessor.getDeclaringClass())) {
                            getters.putIfAbsent(component.getName(), accessor);
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }

            // 3.2 匹配 getter 和 setter，创建 PropertyDescriptor
            for (Map.Entry<String, Method> getterEntry : getters.entrySet()) {
                String propertyName = getterEntry.getKey();
                Method getterMethod = getterEntry.getValue();

                // 跳过已经由注解处理的属性，避免重复
                if (descriptors.stream().anyMatch(d -> d.name.equals(propertyName))) {
                    continue;
                }

                Method setterMethod = setters.get(propertyName); // 尝试找到对应的setter

                // 验证setter参数类型是否与getter返回类型匹配 (setter可以为null，表示只读)
                // 且 setter 必须是公共的且非静态
                if (setterMethod != null &&
                        (Modifier.isStatic(setterMethod.getModifiers()) ||
                                !Modifier.isPublic(setterMethod.getModifiers()) ||
                                !setterMethod.getParameterTypes()[0].isAssignableFrom(getterMethod.getReturnType()))) {
                    NodeCraft.LOGGER.trace("属性 {} 的setter方法类型或修饰符不匹配，或不是公共非静态方法，将视为只读。", propertyName);
                    setterMethod = null; // 将其视为只读
                }

                Class<?> type = getterMethod.getReturnType();
                PropertyRenderer renderer = getRendererForType(type);

                // 创建Method包装器
                MethodAccessor getter = new MethodWrapper(getterMethod);
                MethodAccessor setter = setterMethod != null ? new MethodWrapper(setterMethod) : null;

                descriptors.add(new PropertyDescriptor(
                        propertyName,
                        formatPropertyName(propertyName), // 格式化属性名
                        type,
                        getter,
                        setter,
                        renderer,
                        null, // 没有来自注解的描述
                        "", // 默认分类为空字符串
                        100 // 默认排序
                ));
            }

            // 替换按照分类和order排序的代码段
            descriptors.sort((p1, p2) -> {
                // 先按分类排序 (空字符串分类排在最前面)
                int catComp = p1.category.compareTo(p2.category);
                if (catComp != 0) return catComp;

                // 再按order排序
                int orderComp = Integer.compare(p1.order, p2.order);
                if (orderComp != 0) return orderComp;

                // 最后按显示名称排序
                return p1.displayName.compareTo(p2.displayName);
            });

            return descriptors;
        });
    }

    // 处理带有@NodeProperty注解的字段
    private void processAnnotatedFields(Class<?> clazz, List<PropertyDescriptor> descriptors) {
        // 遍历当前类和所有父类的字段
        Class<?> currentClass = clazz;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Field field : currentClass.getDeclaredFields()) { // 只获取声明的字段
                NodeProperty annotation = field.getAnnotation(NodeProperty.class);
                if (annotation != null) {
                    String propertyName = field.getName();

                    // 严格验证字段修饰符
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        NodeCraft.LOGGER.error("无效的@NodeProperty注解用法: 静态字段 '{}' 在类 '{}' 中。注解将被忽略。",
                                propertyName, currentClass.getName());
                        continue;
                    }

                    // final 字段必须标记为 readOnly
                    if (Modifier.isFinal(modifiers) && !annotation.readOnly()) {
                        NodeCraft.LOGGER.warn("@NodeProperty注解在final字段 '{}' 上未标记为readOnly，将强制视为只读。",
                                propertyName);
                    }

                    String displayName = annotation.displayName().isEmpty()
                            ? formatPropertyName(propertyName)
                            : annotation.displayName();

                    PropertyRenderer renderer = getRendererForType(field.getType());

                    // 创建field getter和setter的Method包装
                    MethodAccessor getter = createFieldGetter(field);
                    MethodAccessor setter = (annotation.readOnly() || Modifier.isFinal(modifiers))
                            ? null
                            : createFieldSetter(field); // false 表示不是 readOnly

                    descriptors.add(new PropertyDescriptor(
                            propertyName,
                            displayName,
                            field.getType(),
                            getter,
                            setter,
                            renderer,
                            annotation.description(),
                            annotation.category(),
                            annotation.order()
                    ));
                }
            }
            currentClass = currentClass.getSuperclass(); // 继续检查父类
        }
    }

    // 处理带有@NodeProperty注解的方法
    private void processAnnotatedMethods(Class<?> clazz, List<PropertyDescriptor> descriptors) {
        // 遍历当前类和所有父类的公共方法
        Class<?> currentClass = clazz;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Method method : currentClass.getDeclaredMethods()) { // 只获取声明的方法
                NodeProperty annotation = method.getAnnotation(NodeProperty.class);
                if (annotation != null) {
                    String methodName = method.getName();

                    // 严格验证方法类型和修饰符
                    int modifiers = method.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        NodeCraft.LOGGER.error("无效的@NodeProperty注解用法: 静态方法 '{}' 在类 '{}' 中。注解将被忽略。",
                                methodName, currentClass.getName());
                        continue;
                    }

                    // 方法必须是getter（无参数且有返回值）
                    if (method.getParameterCount() != 0) {
                        NodeCraft.LOGGER.error("无效的@NodeProperty注解用法: 方法 '{}' 有参数。只有无参数的getter方法才能使用此注解。",
                                methodName);
                        continue;
                    }

                    if (method.getReturnType().equals(void.class)) {
                        NodeCraft.LOGGER.error("无效的@NodeProperty注解用法: 方法 '{}' 返回void。只有有返回值的getter方法才能使用此注解。",
                                methodName);
                        continue;
                    }

                    String propertyName;
                    // 如果方法名不是标准的getter命名，发出警告并使用方法名作为属性名
                    if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
                        propertyName = extractPropertyName(methodName, GET_PREFIX);
                    } else if (methodName.startsWith(IS_PREFIX) && methodName.length() > IS_PREFIX.length() &&
                            (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class))) {
                        propertyName = extractPropertyName(methodName, IS_PREFIX);
                    } else {
                        propertyName = methodName; // 非标准getter命名
                        NodeCraft.LOGGER.warn("方法 '{}' 使用了@NodeProperty注解但不遵循标准的getter命名规范。使用方法名作为属性名。", methodName);
                    }

                    String displayName = annotation.displayName().isEmpty()
                            ? formatPropertyName(propertyName)
                            : annotation.displayName();

                    Class<?> type = method.getReturnType();
                    PropertyRenderer renderer = getRendererForType(type);

                    // 查找对应的setter
                    Method setterMethod = null;
                    if (!annotation.readOnly()) {
                        String setterName = SET_PREFIX + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
                        try {
                            setterMethod = currentClass.getMethod(setterName, type); // 尝试在当前类中找

                            // 验证setter的修饰符和参数类型
                            if (Modifier.isStatic(setterMethod.getModifiers()) ||
                                    !Modifier.isPublic(setterMethod.getModifiers()) ||
                                    setterMethod.getParameterTypes().length != 1 ||
                                    !setterMethod.getParameterTypes()[0].equals(type)) { // 严格匹配参数类型
                                NodeCraft.LOGGER.warn("属性 '{}' 的setter方法 '{}' 类型或修饰符不匹配，将视为只读。",
                                        propertyName, setterName);
                                setterMethod = null;
                            }
                        } catch (NoSuchMethodException e) {
                            // 没有找到匹配的setter，记录一个debug日志但继续（属性将是只读的）
                            NodeCraft.LOGGER.debug("属性 '{}' 未找到对应的setter方法 '{}'，将作为只读属性处理。",
                                    propertyName, setterName);
                        }
                    }

                    MethodAccessor getter = new MethodWrapper(method);
                    MethodAccessor setter = setterMethod != null ? new MethodWrapper(setterMethod) : null;

                    descriptors.add(new PropertyDescriptor(
                            propertyName,
                            displayName,
                            type,
                            getter,
                            setter,
                            renderer,
                            annotation.description(),
                            annotation.category(),
                            annotation.order()
                    ));
                }
            }
            currentClass = currentClass.getSuperclass(); // 继续检查父类
        }
    }

    // 渲染器注册表：类型 -> 渲染器
    private static final Map<Class<?>, PropertyRenderer> RENDERER_REGISTRY = new HashMap<>();

    // 回退渲染器，用于处理没有专门渲染器的类型
    private static final PropertyRenderer FALLBACK_RENDERER = (panel, node, prop, isDisabled) -> {
        try {
            Object value = prop.getter.invoke(node);
            if (value == null) {
                ImGui.textDisabled("(空)");
                return;
            }

            // 对只读属性进行禁用处理
            boolean isReadOnly = prop.setter == null || isDisabled;
            if (isReadOnly) ImGui.beginDisabled();

            ImGui.text(value.toString());

            // 对于集合类型，显示大小信息
            if (value instanceof Collection<?> collection) {
                ImGui.sameLine();
                ImGui.textDisabled("(" + collection.size() + " 项)");
            } else if (value instanceof Map<?,?> map) {
                ImGui.sameLine();
                ImGui.textDisabled("(" + map.size() + " 键值对)");
            }

            if (isReadOnly) ImGui.endDisabled();

        } catch (Throwable e) { // 捕获 Throwable
            panel.handlePropertyError(prop, e);
        }
    };

    // 静态初始化代码块，注册默认渲染器
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
        registerRenderer(CylinderGeometryData.class, CYLINDER_GEOMETRY_RENDERER);
        registerRenderer(EllipsoidGeometryData.class, ELLIPSOID_GEOMETRY_RENDERER);
        registerRenderer(OctahedronGeometryData.class, OCTAHEDRON_RENDERER);
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

        RENDERER_REGISTRY.put(type, renderer);
        NodeCraft.LOGGER.debug("已注册属性渲染器: " + type.getName());
    }

    /**
     * 为类型获取合适的渲染器
     * @param type 需要获取渲染器的类型
     * @return 对应的渲染器，如果没有注册则返回null
     */
    private PropertyRenderer getRendererForType(Class<?> type) {
        // 1. 直接检查是否有精确匹配的渲染器
        PropertyRenderer renderer = RENDERER_REGISTRY.get(type);
        if (renderer != null) {
            return renderer;
        }

        // 2. 如果是枚举类型，返回枚举渲染器
        if (type.isEnum()) {
            return ENUM_RENDERER;
        }

        // 3. 尝试为子类找到合适的渲染器（处理继承关系）
        // 遍历所有已注册的渲染器，看是否有其 Key 是给定 Type 的父类或接口
        for (Map.Entry<Class<?>, PropertyRenderer> entry : RENDERER_REGISTRY.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                // 找到了匹配的父类渲染器
                return entry.getValue();
            }
        }

        // 4. 对于没有注册渲染器的类型，返回回退渲染器
        return FALLBACK_RENDERER;
    }

    private String extractPropertyName(String methodName, String prefix) {
        String name = methodName.substring(prefix.length());
        if (name.isEmpty()) return "";

        // JavaBean规范:
        // 1. 如果第一个和第二个字符都是大写，保持第一个字符大写 (getURL -> URL)
        // 2. 如果第一个字符是大写，第二个字符是小写，将第一个字符转为小写 (getName -> name)
        // 3. 如果第一个字符是小写，保持原样 (getaValue -> aValue)

        if (name.length() == 1) {
            // 单个字符的情况
            return name.toLowerCase();
        }

        char firstChar = name.charAt(0);
        char secondChar = name.charAt(1);

        // 情况1: 连续大写 (getURL -> URL)
        if (Character.isUpperCase(firstChar) && Character.isUpperCase(secondChar)) {
            return name;
        }
        // 情况2: 首字母大写，第二个字母小写 (getName -> name)
        else if (Character.isUpperCase(firstChar) && Character.isLowerCase(secondChar)) {
            return Character.toLowerCase(firstChar) + name.substring(1);
        }
        // 情况3: 首字母小写 (getaValue -> aValue)
        else {
            return name;
        }
    }

    private String formatPropertyName(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) return "";

        StringBuilder result = new StringBuilder();

        // 特殊处理：如果前两个字母都是大写，保持整个词大写
        // 例如：URL -> URL，而不是 "U R L"
        if (propertyName.length() >= 2 &&
                Character.isUpperCase(propertyName.charAt(0)) &&
                Character.isUpperCase(propertyName.charAt(1))) {

            // 查找连续大写字母的结束位置
            int upperCaseEnd = 2;
            while (upperCaseEnd < propertyName.length() &&
                    Character.isUpperCase(propertyName.charAt(upperCaseEnd))) {
                upperCaseEnd++;
            }

            // 第一个单词是全大写缩写
            if (upperCaseEnd == propertyName.length()) {
                // 全部是大写，直接返回
                return propertyName;
            } else {
                // 添加缩写部分
                result.append(propertyName, 0, upperCaseEnd);

                // 添加空格，除非下一个字符是特殊字符
                char nextChar = propertyName.charAt(upperCaseEnd);
                if (Character.isLetterOrDigit(nextChar)) {
                    result.append(' ');
                }

                // 处理剩余部分
                propertyName = propertyName.substring(upperCaseEnd);
                // 如果剩余部分以小写开头，将其首字母大写
                if (Character.isLowerCase(propertyName.charAt(0))) {
                    propertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                }
            }
        } else {
            // 普通处理：首字母大写
            result.append(Character.toUpperCase(propertyName.charAt(0)));
            propertyName = propertyName.substring(1);
        }

        // 处理剩余部分
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);

            // 在大写字母前且前一个字符不是大写字母时添加空格
            // (处理驼峰如 myValue -> My Value, TCPValue -> TCP Value)
            if (Character.isUpperCase(c) &&
                    (i > 0 && !Character.isUpperCase(propertyName.charAt(i - 1))) &&
                    (i + 1 < propertyName.length() && Character.isLowerCase(propertyName.charAt(i + 1)))) {
                result.append(' ');
            }

            result.append(c);
        }

        return result.toString();
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

    // 定义 NodeConstants 类
    private static class NodeConstants {
        // 错误阈值，超过此值则禁用属性渲染
        private static final int ERROR_THRESHOLD = 3;
    }
}
