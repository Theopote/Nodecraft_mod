package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 浮点数输入节点，提供数值输入框和拖拽调节功能。
 */
@NodeInfo(
    id = "inputs.basic.float_input",
    displayName = "浮点数输入",
    description = "允许用户手动输入浮点数值",
    category = "inputs.basic"
)
public class FloatInputNode extends BaseCustomUINode {
    
    // --- 节点属性 ---
    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
                  description = "当前的浮点数值")
    private float value = 0.0f;

    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
                  description = "允许的最小值")
    private float minValue = Float.NEGATIVE_INFINITY;

    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
                  description = "允许的最大值")
    private float maxValue = Float.POSITIVE_INFINITY;

    @NodeProperty(displayName = "精度", category = "精度", order = 4,
                  description = "小数位数 (0-6)")
    private int precision = 2;

    @NodeProperty(displayName = "显示范围", category = "UI设置", order = 10,
                  description = "是否显示数值范围信息")
    private boolean showRange = false;
    
    // --- 输出端口 ---
    private static final String OUTPUT_VALUE_ID = "output_value";

    // --- UI状态 ---
    private transient String formatString = "%.2f";
    private transient float dragSpeed = 0.1f;
    
    public FloatInputNode() {
        super(UUID.randomUUID(), "inputs.basic.float_input");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "The float value", NodeDataType.FLOAT, this);
        addOutputPort(valueOutput);
        updatePrecisionDependentValues();
        updateOutput();
    }
    
    @Override
    public String getDescription() {
        return "允许手动输入浮点数值，支持拖拽调节。";
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }
    
    // === BaseCustomUINode 抽象方法实现 ===

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding(); // 顶部
        height += ImGui.getTextLineHeight(); // 标签行 "值: xxx"
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 拖拽输入框
        height += getMediumPadding();
        if (showRange) {
            height += ImGui.getTextLineHeight(); // 范围信息行
            height += getSmallPadding();
        }
        height += getMediumPadding(); // 底部
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float minWidth = 140.0f;
        String valueText = String.format(formatString, value);
        String labelText = "值: " + valueText;
        float labelWidth = ImGui.calcTextSize(labelText).x;
        minWidth = Math.max(minWidth, labelWidth);
        if (showRange && !Float.isInfinite(minValue) && !Float.isInfinite(maxValue)) {
            String rangeText = String.format("范围: " + formatString + " ~ " + formatString, minValue, maxValue);
            float rangeWidth = ImGui.calcTextSize(rangeText).x;
            minWidth = Math.max(minWidth, rangeWidth);
        }
        return minWidth + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean valueChanged = false;
            
            try {
                float availableWidth = getAvailableWidth(width, zoom);
                
                l.addVerticalSpacing(getMediumPadding());
                
                // === 值标签 ===
                String valueText = String.format(formatString, value);
                String labelText = "值: " + valueText;
                float labelW = ImGui.calcTextSize(labelText).x;
                setCenterX(availableWidth, labelW);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.8f, 0.8f, 1.0f);
                ImGui.text(labelText);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 拖拽输入框 ===
                float inputWidth = Math.min(160.0f, availableWidth - l.toPixels(10.0f));
                setCenterX(availableWidth, l.toPixels(inputWidth));
                
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(inputWidth);
                
                float[] dragValue = {value};
                // 根据是否有范围限制选择不同的拖拽模式
                boolean hasBounds = !Float.isInfinite(minValue) && !Float.isInfinite(maxValue);
                boolean dragged;
                if (hasBounds) {
                    dragged = ImGui.dragFloat("##float_drag", dragValue, dragSpeed, minValue, maxValue, formatString);
                } else {
                    dragged = ImGui.dragFloat("##float_drag", dragValue, dragSpeed, 0, 0, formatString);
                }
                
                if (dragged) {
                    setValue(dragValue[0]);
                    valueChanged = true;
                }
                
                l.popItemWidth();
                l.popStyleVar(); // framePadding
                
                l.addVerticalSpacing(getMediumPadding());
                
                // === 范围信息 ===
                if (showRange) {
                    String rangeText;
                    if (Float.isInfinite(minValue) && Float.isInfinite(maxValue)) {
                        rangeText = "范围: 无限制";
                    } else if (Float.isInfinite(minValue)) {
                        rangeText = String.format("最大值: " + formatString, maxValue);
                    } else if (Float.isInfinite(maxValue)) {
                        rangeText = String.format("最小值: " + formatString, minValue);
                    } else {
                        rangeText = String.format("范围: " + formatString + " ~ " + formatString, minValue, maxValue);
                    }
                    
                    float rangeW = ImGui.calcTextSize(rangeText).x;
                    setCenterX(availableWidth, rangeW);
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.55f, 0.55f, 1.0f);
                    ImGui.text(rangeText);
                    ImGui.popStyleColor();
                    l.addVerticalSpacing(getSmallPadding());
                }
                
                l.addVerticalSpacing(getMediumPadding());
                
            } catch (Exception e) {
                System.err.println("FloatInputNode UI渲染失败: " + e.getMessage());
            }
            
            return valueChanged;
        });
    }
    
    // === 业务逻辑 ===
    
    public void setValue(float value) {
        float clampedValue = Math.max(minValue, Math.min(maxValue, value));
        
        if (precision >= 0) {
            float multiplier = (float) Math.pow(10, precision);
            clampedValue = Math.round(clampedValue * multiplier) / multiplier;
        }
        
        if (this.value != clampedValue) {
            this.value = clampedValue;
            updateOutput();
            markDirty();
        }
    }
    
    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, this.value);
    }

    private void updatePrecisionDependentValues() {
        int safePrecision = Math.max(0, Math.min(precision, 6));
        formatString = "%." + safePrecision + "f";
        dragSpeed = (float) Math.pow(10, -safePrecision);
    }
    
    // === Getters / Setters ===
    
    public float getValue() { return value; }
    public float getMinValue() { return minValue; }
    
    public void setMinValue(float minValue) {
        this.minValue = minValue;
        setValue(this.value);
    }
    
    public float getMaxValue() { return maxValue; }
    
    public void setMaxValue(float maxValue) {
        this.maxValue = maxValue;
        setValue(this.value);
    }
    
    public int getPrecision() { return precision; }
    
    public void setPrecision(int precision) {
        if (precision < 0) precision = 0;
        if (this.precision != precision) {
            this.precision = precision;
            updatePrecisionDependentValues();
            setValue(this.value);
            invalidateCache();
        }
    }

    public boolean isShowRange() { return showRange; }
    public void setShowRange(boolean showRange) { 
        this.showRange = showRange; 
        invalidateCache();
    }
    
    // === 序列化 ===
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("value", getValue());
        state.put("min", getMinValue());
        state.put("max", getMaxValue());
        state.put("precision", getPrecision());
        state.put("showRange", isShowRange());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("precision")) {
                Object precision = stateMap.get("precision");
                if (precision instanceof Number) setPrecision(((Number) precision).intValue());
            }
            if (stateMap.containsKey("min")) {
                Object min = stateMap.get("min");
                if (min instanceof Number) setMinValue(((Number) min).floatValue());
            }
            if (stateMap.containsKey("max")) {
                Object max = stateMap.get("max");
                if (max instanceof Number) setMaxValue(((Number) max).floatValue());
            }
            if (stateMap.containsKey("showRange")) {
                Object sr = stateMap.get("showRange");
                if (sr instanceof Boolean) setShowRange((Boolean) sr);
            }
            if (stateMap.containsKey("value")) {
                Object value = stateMap.get("value");
                if (value instanceof Number) setValue(((Number) value).floatValue());
                else if (value instanceof String) {
                    try { setValue(Float.parseFloat((String) value)); }
                    catch (NumberFormatException e) { System.err.println("Failed to parse state value for FloatInputNode: " + value); }
                }
            }
        } else if (state instanceof Number) {
            setValue(((Number) state).floatValue());
        }
        markDirty();
    }

    protected final float getAvailableWidth(float totalWidth, float zoom) {
        return totalWidth - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
    }
}