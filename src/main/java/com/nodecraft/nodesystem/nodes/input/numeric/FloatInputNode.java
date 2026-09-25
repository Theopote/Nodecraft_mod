package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.NumericInputUtils;
import imgui.ImGui;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.float",
    displayName = "Number Input",
    description = "精确浮点值输入。Min/Max 可选。需要快速有界探索时使用 Number Slider。",
    category = "input.numeric",
    order = 1
)
public class FloatInputNode extends BaseCustomUINode {

    private static final String OUTPUT_VALUE_ID = "output_value";

    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
        description = "当前浮点数值")
    private double value = 0.0;

    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
        description = "允许输入的最小值")
    private double minValue = Double.NEGATIVE_INFINITY;

    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
        description = "允许输入的最大值")
    private double maxValue = Double.POSITIVE_INFINITY;

    @NodeProperty(displayName = "精度", category = "精度", order = 4,
        description = "界面显示与输入的保留小数位数")
    private int precision = 2;

    @NodeProperty(displayName = "拖拽速度", category = "UI设置", order = 12,
        description = "拖拽输入时的数值变化速度，设为 0 时自动根据精度计算")
    private float dragSpeed = 0.0f;

    private transient String formatString = "%.2f";

    public FloatInputNode() {
        super(UUID.randomUUID(), "input.numeric.float");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "当前浮点数值", NodeDataType.DOUBLE, this);
        addOutputPort(valueOutput);
        refreshPrecisionState();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "精确浮点值输入。Min/Max 可选。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
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
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());
            float inputWidthPx = availableWidth;
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.pushFramePadding(4.0f, 3.0f);
            l.setItemWidth(inputWidthPx / Math.max(zoom, 0.001f));

            ImDouble inputValue = new ImDouble(value);
            boolean changedInput = ImGui.inputDouble("##float_input", inputValue, 0.0, 0.0, formatString);
            if (changedInput) {
                setValue(inputValue.get());
                changed = true;
            }

            l.popItemWidth();
            l.popStyleVar();
            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private int getSafePrecision() {
        return Math.max(0, Math.min(6, precision));
    }

    private void refreshPrecisionState() {
        formatString = NumericInputUtils.formatString(precision, 6);
    }

    public void setValue(double value) {
        double accepted = NumericInputUtils.acceptFiniteOrKeep(value, this.value);
        double clampedValue = NumericInputUtils.clampOptionalBounds(accepted, minValue, maxValue);
        if (Double.compare(this.value, clampedValue) != 0) {
            this.value = clampedValue;
            updateOutput();
            markDirty();
        }
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, value);
        syncOutputPorts();
    }

    public double getValue() {
        return value;
    }

    public double getMinValue() {
        return minValue;
    }

    public void setMinValue(double minValue) {
        double sanitized = NumericInputUtils.sanitizeOptionalBound(minValue, this.minValue);
        if (Double.compare(this.minValue, sanitized) != 0) {
            this.minValue = sanitized;
            if (Double.compare(this.minValue, this.maxValue) > 0) {
                double tmp = this.minValue;
                this.minValue = this.maxValue;
                this.maxValue = tmp;
            }
            setValue(this.value);
            invalidateCache();
            markDirty();
        }
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        double sanitized = NumericInputUtils.sanitizeOptionalBound(maxValue, this.maxValue);
        if (Double.compare(this.maxValue, sanitized) != 0) {
            this.maxValue = sanitized;
            if (Double.compare(this.minValue, this.maxValue) > 0) {
                double tmp = this.minValue;
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
                this.minValue = NumericInputUtils.sanitizeOptionalBound(min.doubleValue(), this.minValue);
            }
            if (stateMap.get("max") instanceof Number max) {
                this.maxValue = NumericInputUtils.sanitizeOptionalBound(max.doubleValue(), this.maxValue);
            }
            if (Double.compare(this.minValue, this.maxValue) > 0) {
                double tmp = this.minValue;
                this.minValue = this.maxValue;
                this.maxValue = tmp;
            }
            if (stateMap.get("dragSpeed") instanceof Number speed) {
                this.dragSpeed = Math.max(0.0f, speed.floatValue());
            }

            Object valueObj = stateMap.get("value");
            if (valueObj instanceof Number number) {
                this.value = number.doubleValue();
            } else if (valueObj instanceof String str) {
                try {
                    this.value = Double.parseDouble(str);
                } catch (NumberFormatException ignored) {
                }
            }

            setValue(this.value);
            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setValue(number.doubleValue());
        }
    }
}
