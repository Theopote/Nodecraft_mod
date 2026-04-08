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
    id = "inputs.basic.float_slider",
    displayName = "浮点数滑动条",
    description = "输出一个可通过滑动条调节的浮点数。",
    category = "inputs.basic"
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

    @NodeProperty(displayName = "显示范围标签", category = "UI设置", order = 10,
        description = "在滑动条下方显示最小值和最大值")
    private volatile boolean showMinMaxLabels = true;

    @NodeProperty(displayName = "显示数值输入", category = "UI设置", order = 11,
        description = "在滑动条上方显示当前值输入框")
    private volatile boolean showValueInput = true;

    @NodeProperty(displayName = "显示设置面板", category = "UI设置", order = 12,
        description = "显示范围与精度设置区域")
    private volatile boolean showSettingsPanel = false;

    private transient volatile String formatString = "%.2f";

    public FloatSliderNode() {
        super(UUID.randomUUID(), "inputs.basic.float_slider");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "当前浮点数值", NodeDataType.DOUBLE, this);
        addOutputPort(valueOutput);
        refreshFormatting();
        normalizeRange();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "输出一个可通过滑动条调节的浮点数。";
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
        float minWidth = 176.0f + getContentMargin();
        if (showMinMaxLabels) {
            String rangeText = String.format("范围: " + formatString + " ~ " + formatString, minValue, maxValue);
            minWidth = Math.max(minWidth, 176.0f + getContentMargin());
        }
        if (showSettingsPanel) {
            minWidth = Math.max(minWidth, 176.0f + getContentMargin());
        }
        return minWidth;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            if (showValueInput) {
                String labelText = "当前值: " + String.format(formatString, currentValue);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                l.setItemWidth(Math.max(availableWidth / Math.max(zoom, 0.001f), 1.0f));
                float[] inputValue = {(float) currentValue};
                if (ImGui.dragFloat("##float_value", inputValue, getDragSpeed(), (float) minValue, (float) maxValue, formatString)) {
                    setCurrentValue(inputValue[0]);
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

            if (false) {
                String rangeText = String.format("范围: " + formatString + " ~ " + formatString, minValue, maxValue);
                float rangeWidth = ImGui.calcTextSize(rangeText).x;
                setCenterX(availableWidth, rangeWidth);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.60f, 0.60f, 0.60f, 1.0f);
                ImGui.text(rangeText);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());
            }

            if (false) {
                ImGui.separator();
                l.addVerticalSpacing(getSmallPadding());

                float itemWidth = Math.max((availableWidth - l.toPixels(8.0f)) / 2.0f, l.toPixels(72.0f));
                l.setItemWidth(itemWidth / Math.max(zoom, 0.001f));
                float[] minInput = {(float) minValue};
                if (ImGui.dragFloat("最小值", minInput, getDragSpeed(), 0.0f, 0.0f, formatString)) {
                    setMinValue(minInput[0]);
                    changed = true;
                }
                ImGui.sameLine();
                float[] maxInput = {(float) maxValue};
                if (ImGui.dragFloat("最大值", maxInput, getDragSpeed(), 0.0f, 0.0f, formatString)) {
                    setMaxValue(maxInput[0]);
                    changed = true;
                }
                l.popItemWidth();

                l.addVerticalSpacing(getSmallPadding());

                int[] decimals = {decimalPlaces};
                l.setItemWidth(Math.min(l.toPixels(120.0f), availableWidth) / Math.max(zoom, 0.001f));
                if (ImGui.sliderInt("小数位数", decimals, 0, 6)) {
                    setDecimalPlaces(decimals[0]);
                    changed = true;
                }
                l.popItemWidth();
            } else {
            }

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

    public boolean isShowMinMaxLabels() {
        return showMinMaxLabels;
    }

    public void setShowMinMaxLabels(boolean showMinMaxLabels) {
        if (this.showMinMaxLabels != showMinMaxLabels) {
            this.showMinMaxLabels = showMinMaxLabels;
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

    public boolean isShowSettingsPanel() {
        return showSettingsPanel;
    }

    public void setShowSettingsPanel(boolean showSettingsPanel) {
        if (this.showSettingsPanel != showSettingsPanel) {
            this.showSettingsPanel = showSettingsPanel;
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
        state.put("showMinMaxLabels", showMinMaxLabels);
        state.put("showValueInput", showValueInput);
        state.put("showSettingsPanel", showSettingsPanel);
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
            if (map.get("showMinMaxLabels") instanceof Boolean value) {
                this.showMinMaxLabels = value;
            }
            if (map.get("showValueInput") instanceof Boolean value) {
                this.showValueInput = value;
            }
            if (map.get("showSettingsPanel") instanceof Boolean value) {
                this.showSettingsPanel = value;
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
