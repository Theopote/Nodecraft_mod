package com.nodecraft.nodesystem.nodes.inputs.basic;

// 导入必要的 NodeCraft 基类或接口
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

// 导入ImGui相关类用于自定义UI渲染
import imgui.ImGui;
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
 * 整数输入节点 - 现代化实现
 * 
 * 继承BaseCustomUINode，自动获得缩放感知和布局管理功能。
 * 
 * 特性：
 * - 数值输入框
 * - 可配置的数值范围
 * - 缩放感知的UI布局
 * - 现代化UI设计
 */
@NodeInfo(
    id = "inputs.basic.integer_input",
    displayName = "整数输入",
    description = "允许手动输入整数值的节点",
    category = "inputs.basic"
)
public class IntegerInputNode extends BaseCustomUINode {
    
    // --- 节点属性 ---
    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
                  description = "当前的整数值")
    private int value = 0;
    
    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
                  description = "允许的最小整数值")
    private int minValue = Integer.MIN_VALUE;
    
    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
                  description = "允许的最大整数值")
    private int maxValue = Integer.MAX_VALUE;

    @NodeProperty(displayName = "显示范围", category = "UI设置", order = 10,
                  description = "是否显示数值范围信息")
    private boolean showRange = false;

    @NodeProperty(displayName = "显示标签", category = "UI设置", order = 11,
                  description = "是否显示值标签")
    private boolean showLabel = true;
    
    // --- 输出端口 ---
    private static final String OUTPUT_VALUE_ID = "output_value";
    
    /**
     * 构造一个新的整数输入节点
     */
    public IntegerInputNode() {
        super(UUID.randomUUID(), "inputs.basic.integer_input");
        
        // 创建并添加输出端口
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "The integer value", NodeDataType.INTEGER, this);
        addOutputPort(valueOutput);
        
        // 初始化输出值
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "允许手动输入整数值的节点。";
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

        // 标签（如果启用）
        if (showLabel) {
            height += ImGui.getTextLineHeight();
            height += getSmallPadding();
        }

        // 输入框
        height += ImGui.getFrameHeight();
        height += getMediumPadding();

        // 范围信息（如果启用）
        if (showRange) {
            height += ImGui.getTextLineHeight();
            height += getSmallPadding();
        }

        height += getMediumPadding(); // 底部边距
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float minWidth = 120.0f; // 输入框的最小宽度

        // 考虑标签的宽度
        if (showLabel) {
            String labelText = "值: " + value;
            float labelWidth = ImGui.calcTextSize(labelText).x;
            minWidth = Math.max(minWidth, labelWidth);
        }

        // 考虑范围信息的宽度
        if (showRange) {
            String rangeText = String.format("范围: %d - %d", minValue, maxValue);
            float rangeWidth = ImGui.calcTextSize(rangeText).x;
            minWidth = Math.max(minWidth, rangeWidth);
        }

        return minWidth + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        // 使用新的 LayoutHelper API
        return layout(zoom, l -> {
            boolean valueChanged = false;

            try {
                // 计算可用宽度
                float availableWidth = l.getAvailableContentWidth(width);

                // 添加顶部间距
                l.addVerticalSpacing(getMediumPadding());

                // 标签（如果启用）
                if (showLabel) {
                    String labelText = "值: " + value;
                    float textWidth = ImGui.calcTextSize(labelText).x;
                    setCenterX(availableWidth, textWidth);

                    ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
                    ImGui.text(labelText);
                    ImGui.popStyleColor();

                    l.addVerticalSpacing(getSmallPadding());
                }

                // 输入框
                float inputWidth = Math.min(120.0f, availableWidth - l.toPixels(20.0f));
                setCenterX(availableWidth, l.toPixels(inputWidth));

                l.pushFramePadding(4.0f, 2.0f);
                l.setItemWidth(inputWidth);

                ImInt inputValue = new ImInt(value);
                if (ImGui.inputInt("##value_input", inputValue, 1, 10)) {
                    setValue(inputValue.get());
                    valueChanged = true;
                }

                l.popItemWidth();
                l.popStyleVar();

                l.addVerticalSpacing(getMediumPadding());

                // 范围信息（如果启用）
                if (showRange) {
                    String rangeText;
                    if (minValue == Integer.MIN_VALUE && maxValue == Integer.MAX_VALUE) {
                        rangeText = "范围: 无限制";
                    } else if (minValue == Integer.MIN_VALUE) {
                        rangeText = String.format("最大值: %d", maxValue);
                    } else if (maxValue == Integer.MAX_VALUE) {
                        rangeText = String.format("最小值: %d", minValue);
                    } else {
                        rangeText = String.format("范围: %d - %d", minValue, maxValue);
                    }
                    
                    float rangeTextWidth = ImGui.calcTextSize(rangeText).x;
                    setCenterX(availableWidth, rangeTextWidth);

                    ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
                    ImGui.text(rangeText);
                    ImGui.popStyleColor();

                    l.addVerticalSpacing(getSmallPadding());
                }

                // 底部间距
                l.addVerticalSpacing(getMediumPadding());

            } catch (Exception e) {
                System.err.println("IntegerInputNode UI渲染失败: " + e.getMessage());
            }

            return valueChanged;
        });
    }
    
    /**
     * 当用户在UI中输入值时调用此方法
     * @param value 新的整数值
     */
    public void setValue(int value) {
        // 应用范围约束
        int clampedValue = Math.max(minValue, Math.min(maxValue, value));
        
        // 如果值变化了，更新节点状态
        if (this.value != clampedValue) {
            this.value = clampedValue;
            updateOutput();
            
            // 通知系统此节点的输出已更新
            markDirty();
        }
    }
    
    /**
     * 更新输出端口的值
     */
    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, this.value);
    }
    
    // --- Getters/Setters for Properties ---
    
    public int getValue() {
        return value;
    }
    
    public int getMinValue() {
        return minValue;
    }
    
    public void setMinValue(int minValue) {
        this.minValue = minValue;
        // 确保当前值仍在范围内
        setValue(this.value);
    }
    
    public int getMaxValue() {
        return maxValue;
    }
    
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        // 确保当前值仍在范围内
        setValue(this.value);
    }

    public boolean isShowRange() {
        return showRange;
    }

    public void setShowRange(boolean showRange) {
        this.showRange = showRange;
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public void setShowLabel(boolean showLabel) {
        this.showLabel = showLabel;
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        // 返回节点所有可序列化的状态
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("value", getValue());
        state.put("min", getMinValue());
        state.put("max", getMaxValue());
        state.put("showRange", isShowRange());
        state.put("showLabel", isShowLabel());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            // 首先设置范围
            if (stateMap.containsKey("min")) {
                Object min = stateMap.get("min");
                if (min instanceof Number) {
                    setMinValue(((Number) min).intValue());
                }
            }
            
            if (stateMap.containsKey("max")) {
                Object max = stateMap.get("max");
                if (max instanceof Number) {
                    setMaxValue(((Number) max).intValue());
                }
            }

            // UI设置
            if (stateMap.containsKey("showRange")) {
                Object showRange = stateMap.get("showRange");
                if (showRange instanceof Boolean) {
                    setShowRange((Boolean) showRange);
                }
            }

            if (stateMap.containsKey("showLabel")) {
                Object showLabel = stateMap.get("showLabel");
                if (showLabel instanceof Boolean) {
                    setShowLabel((Boolean) showLabel);
                }
            }
            
            // 最后设置当前值，确保应用所有约束
            if (stateMap.containsKey("value")) {
                Object value = stateMap.get("value");
                if (value instanceof Number) {
                    setValue(((Number) value).intValue());
                } else if (value instanceof String) {
                    try {
                        setValue(Integer.parseInt((String) value));
                    } catch (NumberFormatException e) {
                        System.err.println("Failed to parse state value for IntegerInputNode: " + value);
                    }
                }
            }
        } else if (state instanceof Number) {
            // 向后兼容：如果状态只是一个数字，直接使用它作为当前值
            setValue(((Number) state).intValue());
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