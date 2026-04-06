package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.basic.float_input",
    displayName = "浮点数输入",
    description = "允许用户手动输入浮点数值",
    category = "inputs.basic"
)
public class FloatInputNode extends BaseCustomUINode {

    private static final String OUTPUT_VALUE_ID = "output_value";

    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
        description = "当前浮点数值")
    private float value = 0.0f;

    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
        description = "允许输入的最小值")
    private float minValue = Float.NEGATIVE_INFINITY;

    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
        description = "允许输入的最大值")
    private float maxValue = Float.POSITIVE_INFINITY;

    @NodeProperty(displayName = "精度", category = "精度", order = 4,
        description = "界面显示与输入的保留小数位数")
    private int precision = 2;

    @NodeProperty(displayName = "显示范围", category = "UI设置", order = 10,
        description = "是否显示范围信息")
    private boolean showRange = false;

    @NodeProperty(displayName = "显示标签", category = "UI设置", order = 11,
        description = "是否显示当前值标签")
    private boolean showLabel = true;

    @NodeProperty(displayName = "拖拽速度", category = "UI设置", order = 12,
        description = "拖拽输入时的数值变化速度，设为 0 时自动根据精度计算")
    private float dragSpeed = 0.0f;

    private transient String formatString = "%.2f";

    public FloatInputNode() {
        super(UUID.randomUUID(), "inputs.basic.float_input");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "当前浮点数值", NodeDataType.FLOAT, this);
        addOutputPort(valueOutput);
        refreshPrecisionState();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "允许用户手动输入浮点数值。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 150.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getMediumPadding());
            float availableWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());
            float inputWidthPx = Math.min(l.toPixels(160.0f), availableWidth);
            ImGui.setCursorPosX(baseCursorX + edgeMargin + Math.max(0.0f, (availableWidth - inputWidthPx) * 0.5f));
            l.pushFramePadding(4.0f, 3.0f);
            l.setItemWidth(inputWidthPx / Math.max(zoom, 0.001f));

            float[] inputValue = {value};
            boolean hasBounds = !Float.isInfinite(minValue) && !Float.isInfinite(maxValue);
            float actualDragSpeed = dragSpeed > 0 ? dragSpeed : (float) Math.pow(10, -getSafePrecision());
            boolean dragged;
            if (hasBounds) {
                dragged = ImGui.dragFloat("##float_drag", inputValue, actualDragSpeed, minValue, maxValue, formatString);
            } else {
                dragged = ImGui.dragFloat("##float_drag", inputValue, actualDragSpeed, 0f, 0f, formatString);
            }
            if (dragged) {
                setValue(inputValue[0]);
                changed = true;
            }

            l.popItemWidth();
            l.popStyleVar();
            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private int getSafePrecision() {
        return Math.max(0, Math.min(6, precision));
    }

    private String getRangeText() {
        if (Float.isInfinite(minValue) && Float.isInfinite(maxValue)) {
            return "范围: 无限制";
        }
        if (Float.isInfinite(minValue)) {
            return "最大值: " + String.format(formatString, maxValue);
        }
        if (Float.isInfinite(maxValue)) {
            return "最小值: " + String.format(formatString, minValue);
        }
        return "范围: " + String.format(formatString, minValue) + " ~ " + String.format(formatString, maxValue);
    }

    private void refreshPrecisionState() {
        formatString = "%." + getSafePrecision() + "f";
    }

    public void setValue(float value) {
        float clampedValue = Math.max(minValue, Math.min(maxValue, value));
        float multiplier = (float) Math.pow(10, getSafePrecision());
        clampedValue = Math.round(clampedValue * multiplier) / multiplier;
        if (Float.compare(this.value, clampedValue) != 0) {
            this.value = clampedValue;
            updateOutput();
            markDirty();
        }
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, value);
        syncOutputPorts();
    }

    public float getValue() {
        return value;
    }

    public float getMinValue() {
        return minValue;
    }

    public void setMinValue(float minValue) {
        if (Float.compare(this.minValue, minValue) != 0) {
            this.minValue = minValue;
            if (this.minValue > this.maxValue) {
                float tmp = this.minValue;
                this.minValue = this.maxValue;
                this.maxValue = tmp;
            }
            setValue(this.value);
            invalidateCache();
            markDirty();
        }
    }

    public float getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(float maxValue) {
        if (Float.compare(this.maxValue, maxValue) != 0) {
            this.maxValue = maxValue;
            if (this.minValue > this.maxValue) {
                float tmp = this.minValue;
                this.minValue = this.maxValue;
                this.maxValue = tmp;
            }
            setValue(this.value);
            invalidateCache();
            markDirty();
        }
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        int normalized = Math.max(0, Math.min(6, precision));
        if (this.precision != normalized) {
            this.precision = normalized;
            refreshPrecisionState();
            setValue(this.value);
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowRange() {
        return showRange;
    }

    public void setShowRange(boolean showRange) {
        if (this.showRange != showRange) {
            this.showRange = showRange;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public void setShowLabel(boolean showLabel) {
        if (this.showLabel != showLabel) {
            this.showLabel = showLabel;
            invalidateCache();
            markDirty();
        }
    }

    public float getDragSpeed() {
        return dragSpeed;
    }

    public void setDragSpeed(float dragSpeed) {
        float normalized = Math.max(0.0f, dragSpeed);
        if (Float.compare(this.dragSpeed, normalized) != 0) {
            this.dragSpeed = normalized;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("value", value);
        state.put("min", minValue);
        state.put("max", maxValue);
        state.put("precision", precision);
        state.put("showRange", showRange);
        state.put("showLabel", showLabel);
        state.put("dragSpeed", dragSpeed);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            if (stateMap.get("precision") instanceof Number precisionValue) {
                this.precision = Math.max(0, Math.min(6, precisionValue.intValue()));
            }
            refreshPrecisionState();

            if (stateMap.get("min") instanceof Number min) {
                this.minValue = min.floatValue();
            }
            if (stateMap.get("max") instanceof Number max) {
                this.maxValue = max.floatValue();
            }
            if (this.minValue > this.maxValue) {
                float tmp = this.minValue;
                this.minValue = this.maxValue;
                this.maxValue = tmp;
            }
            if (stateMap.get("showRange") instanceof Boolean showRange) {
                this.showRange = showRange;
            }
            if (stateMap.get("showLabel") instanceof Boolean showLabel) {
                this.showLabel = showLabel;
            }
            if (stateMap.get("dragSpeed") instanceof Number speed) {
                this.dragSpeed = Math.max(0.0f, speed.floatValue());
            }

            Object valueObj = stateMap.get("value");
            if (valueObj instanceof Number number) {
                this.value = number.floatValue();
            } else if (valueObj instanceof String str) {
                try {
                    this.value = Float.parseFloat(str);
                } catch (NumberFormatException ignored) {
                }
            }

            setValue(this.value);
            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setValue(number.floatValue());
        }
    }
}
