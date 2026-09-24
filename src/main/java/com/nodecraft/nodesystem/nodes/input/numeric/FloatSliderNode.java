package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.float_slider",
    displayName = "Float Slider",
    description = "有界参数探索：精确 double 输入 + 滑动条快速调节。Min/Max 必须设置。",
    category = "input.numeric",
    order = 3
)
public class FloatSliderNode extends BaseCustomUINode {

    private static final String OUTPUT_VALUE_ID = "output_value";

    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
        description = "滑动条当前输出的浮点数值")
    private volatile double currentValue = 50.0;

    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
        description = "滑动条允许的最小值")
    private volatile double minValue = 0.0;

    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
        description = "滑动条允许的最大值")
    private volatile double maxValue = 100.0;

    @NodeProperty(displayName = "小数位数", category = "精度", order = 4,
        description = "界面显示和输入时保留的小数位数")
    private volatile int decimalPlaces = 2;

    @NodeProperty(displayName = "显示数值输入", category = "UI设置", order = 11,
        description = "在滑动条上方显示当前值输入框")
    private volatile boolean showValueInput = true;

    private transient volatile String formatString = "%.2f";

    public FloatSliderNode() {
        super(UUID.randomUUID(), "input.numeric.float_slider");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "当前浮点数值", NodeDataType.DOUBLE, this);
        addOutputPort(valueOutput);
        refreshFormatting();
        normalizeRange();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "有界参数探索：精确 double 输入 + 滑动条快速调节。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        if (showValueInput) {
            height += ImGui.getFrameHeight();
            height += getSmallPadding();
        }
        height += ImGui.getFrameHeight();
        height += 1.0f;
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 176.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            if (showValueInput) {
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                l.setItemWidth(Math.max(availableWidth / Math.max(zoom, 0.001f), 1.0f));
                ImDouble inputValue = new ImDouble(currentValue);
                if (ImGui.inputDouble("##float_value", inputValue, 0.0, 0.0, formatString)) {
                    setCurrentValue(inputValue.get());
                    changed = true;
                }
                l.popItemWidth();
                l.addVerticalSpacing(getSmallPadding());
            }

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.setItemWidth(Math.max(availableWidth / Math.max(zoom, 0.001f), 1.0f));
            float[] sliderValue = {(float) currentValue};
            if (ImGui.sliderFloat("##float_slider", sliderValue, (float) minValue, (float) maxValue, formatString)) {
                setCurrentValue(sliderValue[0]);
                changed = true;
            }
            l.popItemWidth();

            return changed;
        });
    }

    private void refreshFormatting() {
        formatString = "%." + Math.max(0, Math.min(6, decimalPlaces)) + "f";
    }

    private void normalizeRange() {
        if (Double.compare(minValue, maxValue) > 0) {
            double temp = minValue;
            minValue = maxValue;
            maxValue = temp;
        }
        currentValue = clampAndRound(currentValue);
    }

    private double clampAndRound(double value) {
        double clamped = Math.max(minValue, Math.min(maxValue, value));
        double multiplier = Math.pow(10.0, Math.max(0, Math.min(6, decimalPlaces)));
        return Math.round(clamped * multiplier) / multiplier;
    }

    private float getDragSpeed() {
        double range = Math.abs(maxValue - minValue);
        if (range <= 0.0) {
            return 0.1f;
        }
        return (float) Math.max(range / 200.0, Math.pow(10.0, -Math.max(0, Math.min(6, decimalPlaces))));
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, currentValue);
        syncOutputPorts();
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(double currentValue) {
        double normalized = clampAndRound(currentValue);
        if (Double.compare(this.currentValue, normalized) != 0) {
            this.currentValue = normalized;
            updateOutput();
            markDirty();
        }
    }

    public double getMinValue() {
        return minValue;
    }

    public void setMinValue(double minValue) {
        if (Double.compare(this.minValue, minValue) != 0) {
            this.minValue = minValue;
            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        if (Double.compare(this.maxValue, maxValue) != 0) {
            this.maxValue = maxValue;
            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(int decimalPlaces) {
        int normalized = Math.max(0, Math.min(6, decimalPlaces));
        if (this.decimalPlaces != normalized) {
            this.decimalPlaces = normalized;
            refreshFormatting();
            currentValue = clampAndRound(currentValue);
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowValueInput() {
        return showValueInput;
    }

    public void setShowValueInput(boolean showValueInput) {
        if (this.showValueInput != showValueInput) {
            this.showValueInput = showValueInput;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("currentValue", currentValue);
        state.put("minValue", minValue);
        state.put("maxValue", maxValue);
        state.put("decimalPlaces", decimalPlaces);
        state.put("showValueInput", showValueInput);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("decimalPlaces") instanceof Number decimals) {
                this.decimalPlaces = Math.max(0, Math.min(6, decimals.intValue()));
            }
            refreshFormatting();

            if (map.get("minValue") instanceof Number min) {
                this.minValue = min.doubleValue();
            }
            if (map.get("maxValue") instanceof Number max) {
                this.maxValue = max.doubleValue();
            }
            if (map.get("showValueInput") instanceof Boolean value) {
                this.showValueInput = value;
            }

            Object current = map.containsKey("currentValue") ? map.get("currentValue") : map.get("value");
            if (current instanceof Number number) {
                this.currentValue = number.doubleValue();
            } else if (current instanceof String text) {
                try {
                    this.currentValue = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                }
            }

            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setCurrentValue(number.doubleValue());
        }
    }
}
