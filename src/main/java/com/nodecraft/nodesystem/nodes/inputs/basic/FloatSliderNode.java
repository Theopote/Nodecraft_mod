package com.nodecraft.nodesystem.nodes.inputs.basic;

// 导入必要的 NodeCraft 基类或接口
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

// 导入ImGui相关类用于自定义UI渲染
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.flag.ImGuiCol;

// 导入属性注解
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.NodeInfo;

// 导入自定义UI基类
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;

import java.util.UUID;

/**
 * 浮点数滑动条节点 - 现代化实现
 * 
 * 继承BaseCustomUINode，自动获得缩放感知和布局管理功能。
 * 
 * 特性：
 * - 可配置的数值范围
 * - 精度控制（小数位数）
 * - 缩放感知的UI布局
 * - 现代化UI设计
 */
@NodeInfo(
    id = "inputs.basic.float_slider",
    displayName = "浮点数滑动条",
    description = "输出一个可通过滑动条调节的浮点数",
    category = "inputs.basic"
)
public class FloatSliderNode extends BaseCustomUINode {

    // --- 节点属性 ---
    @NodeProperty(displayName = "最小值", category = "范围", order = 1,
                  description = "滑动条的最小值")
    private double minValue = 0.0;

    @NodeProperty(displayName = "最大值", category = "范围", order = 2,
                  description = "滑动条的最大值")
    private double maxValue = 100.0;

    @NodeProperty(displayName = "当前值", category = "范围", order = 3,
                  description = "滑动条的当前值")
    private double currentValue = 50.0;

    @NodeProperty(displayName = "小数位数", category = "精度", order = 4,
                  description = "小数位数 (0-9)，同时影响步长")
    private int decimalPlaces = 2;

    @NodeProperty(displayName = "显示范围标签", category = "UI设置", order = 10,
                  description = "在滑动条旁显示最小/最大值标签")
    private boolean showMinMaxLabels = true;

    @NodeProperty(displayName = "显示数值输入", category = "UI设置", order = 12,
                  description = "显示数值输入框")
    private boolean showValueInput = true;

    @NodeProperty(displayName = "显示设置面板", category = "UI设置", order = 14,
                  description = "显示参数设置面板")
    private boolean showSettingsPanel = false;

    // === 内部字段 ===
    private double step;
    private String formatString;

    // --- 输出端口 ---
    private static final String OUTPUT_VALUE_ID = "output_value";

    /**
     * 构造一个新的浮点数滑动条节点
     */
    public FloatSliderNode() {
        super(UUID.randomUUID(), "inputs.basic.float_slider");

        // 创建并添加输出端口
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "The selected float value", NodeDataType.DOUBLE, this);
        addOutputPort(valueOutput);

        // 初始化精度相关值和输出
        updatePrecisionDependentValues();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "输出一个可通过滑动条调节的浮点数。";
    }

    /**
     * 节点的计算逻辑
     * @param context 执行上下文
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    // === BaseCustomUINode 抽象方法实现 ===
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding(); // 顶部边距

        // 数值输入框（如果启用）
        if (showValueInput) {
            height += ImGui.getTextLineHeight(); // 标签
            height += getSmallPadding();
            height += ImGui.getFrameHeight(); // 输入框
            height += getMediumPadding();
        }

        // 滑动条
        height += ImGui.getFrameHeight();
        height += getMediumPadding();

        // 范围标签（如果启用）
        if (showMinMaxLabels) {
            height += ImGui.getTextLineHeight();
            height += getSmallPadding();
        }

        // 设置面板切换按钮
        height += ImGui.getFrameHeight();
        height += getMediumPadding();

        // 设置面板（如果展开）
        if (showSettingsPanel) {
            height += 1.0f; // 分隔线
            height += getMediumPadding();
            
            // 三个设置项：Min Value, Max Value, Decimal Places
            for (int i = 0; i < 3; i++) {
                height += ImGui.getTextLineHeight(); // 标签
                height += getSmallPadding();
                height += ImGui.getFrameHeight(); // 输入框
                height += getSmallPadding();
            }
        }

        height += getLargePadding(); // 底部边距
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float minWidth = 0;

        // 滑动条的最小宽度
        float sliderMinWidth = 120.0f;

        // 如果显示范围标签，需要额外空间
        if (showMinMaxLabels) {
            String minText = String.format("最小: " + formatString, minValue);
            String maxText = String.format("最大: " + formatString, maxValue);
            float minTextWidth = ImGui.calcTextSize(minText).x;
            float maxTextWidth = ImGui.calcTextSize(maxText).x;
            float labelsWidth = Math.max(minTextWidth, maxTextWidth);
            minWidth = Math.max(minWidth, labelsWidth);
        }

        // 数值输入框的宽度需求
        if (showValueInput) {
            float inputWidth = 100.0f;
            minWidth = Math.max(minWidth, inputWidth);
        }

        // 设置面板的宽度需求
        if (showSettingsPanel) {
            float settingsWidth = 150.0f;
            minWidth = Math.max(minWidth, settingsWidth);
        }

        // 取最大值并加上节点内容边距
        minWidth = Math.max(minWidth, sliderMinWidth) + getContentMargin();

        return minWidth;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        // 使用新的 LayoutHelper API
        return layout(zoom, l -> {
            boolean valueChanged = false;
            boolean hasUIInteraction = false; // 跟踪是否有UI交互

            try {
                // 计算可用宽度
                float availableWidth = l.getAvailableContentWidth(width);

                // 添加顶部间距
                l.addVerticalSpacing(getMediumPadding());

                // 数值输入框（如果启用）
                if (showValueInput) {
                    boolean inputChanged = renderValueInput(availableWidth, l);
                    valueChanged |= inputChanged;
                    hasUIInteraction |= inputChanged || ImGui.isItemHovered() || ImGui.isItemActive();
                }

                // 滑动条
                boolean sliderChanged = renderSlider(availableWidth, l);
                valueChanged |= sliderChanged;
                hasUIInteraction |= sliderChanged; // renderSlider已经包含了交互检测

                // 范围标签（如果启用）
                if (showMinMaxLabels) {
                    renderRangeLabels(availableWidth, l);
                }

                // 设置面板切换按钮
                boolean toggleChanged = renderSettingsToggle(availableWidth, l);
                valueChanged |= toggleChanged;
                hasUIInteraction |= toggleChanged || ImGui.isItemHovered() || ImGui.isItemActive();

                // 设置面板（如果展开）
                if (showSettingsPanel) {
                    boolean settingsChanged = renderSettingsPanel(availableWidth, l);
                    valueChanged |= settingsChanged;
                    hasUIInteraction |= settingsChanged;
                }

                // 底部间距
                l.addVerticalSpacing(getLargePadding());

            } catch (Exception e) {
                System.err.println("FloatSliderNode UI渲染失败: " + e.getMessage());
            }

            // 如果有UI交互，阻止事件传播到底层画布和游戏世界
            if (hasUIInteraction) {
                return true;
            }

            return valueChanged;
        });
    }

    /**
     * 渲染数值输入框
     */
    private boolean renderValueInput(float availableWidth, LayoutHelper l) {
        // 标签
        String valueText = String.format("值: " + formatString, currentValue);
        float textWidth = ImGui.calcTextSize(valueText).x;
        setCenterX(availableWidth, textWidth);

        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
        ImGui.text(valueText);
        ImGui.popStyleColor();

        l.addVerticalSpacing(getSmallPadding());

        // 输入框
        float inputWidth = 100.0f;
        setCenterX(availableWidth, l.toPixels(inputWidth));

        l.pushFramePadding(2.0f, 1.0f);
        l.setItemWidth(inputWidth);

        ImFloat inputValue = new ImFloat((float) currentValue);
        boolean changed = ImGui.inputFloat("##value_input", inputValue, 0.0f, 0.0f, formatString);
        if (changed) {
            setCurrentValue(inputValue.get());
        }

        l.popItemWidth();
        l.popStyleVar();

        l.addVerticalSpacing(getMediumPadding());
        return changed;
    }

    /**
     * 渲染滑动条
     */
    private boolean renderSlider(float availableWidth, LayoutHelper l) {
        float sliderWidth = availableWidth - l.toPixels(getMediumPadding() * 2);
        
        // 居中对齐
        setCenterX(availableWidth, sliderWidth);

        // 设置样式
        l.pushFrameRounding(3.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.GrabRounding, l.toPixels(3.0f));
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.GrabMinSize, l.toPixels(14.0f));
        l.pushFramePadding(2.0f, 4.0f);

        // 设置滑动条颜色
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.12f, 0.12f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.16f, 0.16f, 0.16f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, 0.4f, 0.6f, 0.8f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, 0.5f, 0.7f, 0.9f, 1.0f);

        ImGui.pushItemWidth(sliderWidth);
        float[] floatValue = {(float) currentValue};
        boolean changed = ImGui.sliderFloat("##main_slider", floatValue, (float) minValue,
                (float) maxValue, formatString);
        if (changed) {
            setCurrentValue(floatValue[0]);
        }
        
        // 修复：检测滑动条的交互状态，确保在悬停或拖动时阻止节点拖动
        boolean isSliderHovered = ImGui.isItemHovered();
        boolean isSliderActive = ImGui.isItemActive();
        
        ImGui.popItemWidth();

        // 恢复样式
        ImGui.popStyleColor(4);
        ImGui.popStyleVar(4);

        l.addVerticalSpacing(getMediumPadding());
        
        // 修复：如果滑动条有交互状态，返回true以阻止节点拖动
        return changed || isSliderHovered || isSliderActive;
    }

    /**
     * 渲染范围标签
     */
    private void renderRangeLabels(float availableWidth, LayoutHelper l) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);

        String minText = String.format("最小: " + formatString, minValue);
        String maxText = String.format("最大: " + formatString, maxValue);

        // 左对齐最小值
        float padding = l.toPixels(getMediumPadding());
        ImGui.setCursorPosX(ImGui.getCursorPosX() + padding);
        ImGui.text(minText);

        // 右对齐最大值
        ImGui.sameLine();
        float maxTextWidth = ImGui.calcTextSize(maxText).x;
        float rightOffset = availableWidth - maxTextWidth - padding;
        ImGui.setCursorPosX(ImGui.getCursorPosX() + rightOffset - ImGui.getCursorPosX() + padding);
        ImGui.text(maxText);

        ImGui.popStyleColor();
        l.addVerticalSpacing(getSmallPadding());
    }

    /**
     * 渲染设置面板切换按钮
     */
    private boolean renderSettingsToggle(float availableWidth, LayoutHelper l) {
        float buttonWidth = 120.0f;
        setCenterX(availableWidth, l.toPixels(buttonWidth));

        l.pushFramePadding(2.0f, 1.0f);
        l.setItemWidth(buttonWidth);

        String buttonText = showSettingsPanel ? "隐藏设置" : "显示设置";
        boolean clicked = ImGui.button(buttonText);
        if (clicked) {
            showSettingsPanel = !showSettingsPanel;
        }

        l.popItemWidth();
        l.popStyleVar();

        l.addVerticalSpacing(getMediumPadding());
        return clicked;
    }

    /**
     * 渲染设置面板
     */
    private boolean renderSettingsPanel(float availableWidth, LayoutHelper l) {
        boolean valueChanged = false;

        // 分隔线
        ImGui.separator();
        l.addVerticalSpacing(getMediumPadding());

        float inputWidth = 100.0f;

        // 最小值设置
        ImGui.text("最小值:");
        l.addVerticalSpacing(getSmallPadding());
        setCenterX(availableWidth, l.toPixels(inputWidth));
        
        l.pushFramePadding(2.0f, 1.0f);
        l.setItemWidth(inputWidth);
        
        ImFloat minInput = new ImFloat((float) minValue);
        if (ImGui.inputFloat("##min_value", minInput, 0.0f, 0.0f, formatString)) {
            setMinValue(minInput.get());
            valueChanged = true;
        }
        l.popItemWidth();
        l.popStyleVar();
        l.addVerticalSpacing(getSmallPadding());

        // 最大值设置
        ImGui.text("最大值:");
        l.addVerticalSpacing(getSmallPadding());
        setCenterX(availableWidth, l.toPixels(inputWidth));
        
        l.pushFramePadding(2.0f, 1.0f);
        l.setItemWidth(inputWidth);
        
        ImFloat maxInput = new ImFloat((float) maxValue);
        if (ImGui.inputFloat("##max_value", maxInput, 0.0f, 0.0f, formatString)) {
            setMaxValue(maxInput.get());
            valueChanged = true;
        }
        l.popItemWidth();
        l.popStyleVar();
        l.addVerticalSpacing(getSmallPadding());

        // 小数位数设置
        ImGui.text("小数位数:");
        l.addVerticalSpacing(getSmallPadding());
        setCenterX(availableWidth, l.toPixels(inputWidth));
        
        l.pushFramePadding(2.0f, 1.0f);
        l.setItemWidth(inputWidth);
        
        ImInt decimalInput = new ImInt(decimalPlaces);
        if (ImGui.inputInt("##decimal_places", decimalInput, 1, 1)) {
            setDecimalPlaces(decimalInput.get());
            valueChanged = true;
        }
        l.popItemWidth();
        l.popStyleVar();

        return valueChanged;
    }

    /**
     * 设置当前值
     */
    public void setCurrentValue(double newValue) {
        // 应用范围约束
        double clampedValue = Math.max(minValue, Math.min(maxValue, newValue));

        // 应用精度约束
        if (decimalPlaces >= 0) {
            double multiplier = Math.pow(10, decimalPlaces);
            clampedValue = Math.round(clampedValue * multiplier) / multiplier;
        }

        if (Math.abs(this.currentValue - clampedValue) > 1e-9) {
            this.currentValue = clampedValue;
            updateOutput();
            markDirty();
        }
    }

    /**
     * 更新输出端口的值
     */
    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, currentValue);
    }

    /**
     * 更新精度相关的值
     */
    private void updatePrecisionDependentValues() {
        step = Math.pow(10, -decimalPlaces);
        formatString = "%." + decimalPlaces + "f";
    }

    // --- Getters/Setters for Properties ---

    public double getMinValue() {
        return minValue;
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
        // 确保当前值仍在范围内
        setCurrentValue(this.currentValue);
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
        // 确保当前值仍在范围内
        setCurrentValue(this.currentValue);
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(int decimalPlaces) {
        this.decimalPlaces = Math.max(0, Math.min(9, decimalPlaces));
        updatePrecisionDependentValues();
        // 重新应用精度到当前值
        setCurrentValue(this.currentValue);
    }

    public boolean isShowMinMaxLabels() {
        return showMinMaxLabels;
    }

    public void setShowMinMaxLabels(boolean showMinMaxLabels) {
        this.showMinMaxLabels = showMinMaxLabels;
    }

    public boolean isShowValueInput() {
        return showValueInput;
    }

    public void setShowValueInput(boolean showValueInput) {
        this.showValueInput = showValueInput;
    }

    public boolean isShowSettingsPanel() {
        return showSettingsPanel;
    }

    public void setShowSettingsPanel(boolean showSettingsPanel) {
        this.showSettingsPanel = showSettingsPanel;
    }

    public double getStep() {
        return step;
    }

    public String getFormatString() {
        return formatString;
    }

    // --- 节点状态序列化 ---

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("minValue", getMinValue());
        state.put("maxValue", getMaxValue());
        state.put("currentValue", getCurrentValue());
        state.put("decimalPlaces", getDecimalPlaces());
        state.put("showMinMaxLabels", isShowMinMaxLabels());
        state.put("showValueInput", isShowValueInput());
        state.put("showSettingsPanel", isShowSettingsPanel());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;

            // 首先设置精度和范围
            if (stateMap.containsKey("decimalPlaces")) {
                Object decimalPlaces = stateMap.get("decimalPlaces");
                if (decimalPlaces instanceof Number) {
                    setDecimalPlaces(((Number) decimalPlaces).intValue());
                }
            }

            if (stateMap.containsKey("minValue")) {
                Object minValue = stateMap.get("minValue");
                if (minValue instanceof Number) {
                    setMinValue(((Number) minValue).doubleValue());
                }
            }

            if (stateMap.containsKey("maxValue")) {
                Object maxValue = stateMap.get("maxValue");
                if (maxValue instanceof Number) {
                    setMaxValue(((Number) maxValue).doubleValue());
                }
            }

            // UI设置
            if (stateMap.containsKey("showMinMaxLabels")) {
                Object showLabels = stateMap.get("showMinMaxLabels");
                if (showLabels instanceof Boolean) {
                    setShowMinMaxLabels((Boolean) showLabels);
                }
            }

            if (stateMap.containsKey("showValueInput")) {
                Object showInput = stateMap.get("showValueInput");
                if (showInput instanceof Boolean) {
                    setShowValueInput((Boolean) showInput);
                }
            }

            if (stateMap.containsKey("showSettingsPanel")) {
                Object showSettings = stateMap.get("showSettingsPanel");
                if (showSettings instanceof Boolean) {
                    setShowSettingsPanel((Boolean) showSettings);
                }
            }

            // 最后设置当前值，确保应用所有约束
            if (stateMap.containsKey("currentValue")) {
                Object currentValue = stateMap.get("currentValue");
                if (currentValue instanceof Number) {
                    setCurrentValue(((Number) currentValue).doubleValue());
                } else if (currentValue instanceof String) {
                    try {
                        setCurrentValue(Double.parseDouble((String) currentValue));
                    } catch (NumberFormatException e) {
                        setCurrentValue((minValue + maxValue) / 2.0);
                    }
                }
            } else {
                setCurrentValue((minValue + maxValue) / 2.0);
            }
        }
        markDirty();
    }

    // === 添加缺失的助手方法 ===
    
    /**
     * 计算可用的内容宽度（减去边距）
     * @param totalWidth 总宽度
     * @param zoom 缩放因子
     * @return 可用宽度
     */
    protected final float getAvailableWidth(float totalWidth, float zoom) {
        return getAvailableContentWidth(totalWidth, zoom);
    }

    /**
     * 计算居中偏移量
     * @param availableWidth 可用宽度
     * @param elementWidth 元素宽度
     * @return 居中偏移量
     */
    protected final float getCenterOffset(float availableWidth, float elementWidth) {
        return ZoomHelper.getCenterOffset(availableWidth, elementWidth);
    }
} 