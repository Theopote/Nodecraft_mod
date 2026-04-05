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
import imgui.type.ImInt;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.basic.integer_input",
    displayName = "整数输入",
    description = "允许手动输入整数值的节点",
    category = "inputs.basic"
)
public class IntegerInputNode extends BaseCustomUINode {

    private static final String OUTPUT_VALUE_ID = "output_value";

    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
        description = "当前整数值")
    private int value = 0;

    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
        description = "允许输入的最小值")
    private int minValue = Integer.MIN_VALUE;

    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
        description = "允许输入的最大值")
    private int maxValue = Integer.MAX_VALUE;

    @NodeProperty(displayName = "步长", category = "范围", order = 4,
        description = "输入框点击增减按钮时每次变化的值")
    private int step = 1;

    @NodeProperty(displayName = "显示范围", category = "UI设置", order = 10,
        description = "是否显示范围信息")
    private boolean showRange = false;

    @NodeProperty(displayName = "显示标签", category = "UI设置", order = 11,
        description = "是否显示当前值标签")
    private boolean showLabel = true;

    public IntegerInputNode() {
        super(UUID.randomUUID(), "inputs.basic.integer_input");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "当前整数值", NodeDataType.INTEGER, this);
        addOutputPort(valueOutput);
        normalizeRange();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "允许手动输入整数值的节点。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        if (showLabel) {
            height += ImGui.getTextLineHeight();
            height += getSmallPadding();
        }
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        if (showRange) {
            height += ImGui.getTextLineHeight();
            height += getSmallPadding();
        }
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float minWidth = 140.0f;
        if (showLabel) {
            String labelText = "当前值: " + value;
            minWidth = Math.max(minWidth, ImGui.calcTextSize(labelText).x);
        }
        if (showRange) {
            String rangeText = getRangeText();
            minWidth = Math.max(minWidth, ImGui.calcTextSize(rangeText).x);
        }
        return minWidth + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float availableWidth = l.getAvailableContentWidth(width);

            l.addVerticalSpacing(getMediumPadding());

            if (showLabel) {
                String labelText = "当前值: " + value;
                float labelWidth = ImGui.calcTextSize(labelText).x;
                setCenterX(availableWidth, labelWidth);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.75f, 0.75f, 0.75f, 1.0f);
                ImGui.text(labelText);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());
            }

            float inputWidth = Math.min(140.0f, availableWidth - l.toPixels(20.0f));
            setCenterX(availableWidth, l.toPixels(inputWidth));
            l.pushFramePadding(4.0f, 2.0f);
            l.setItemWidth(inputWidth);

            ImInt inputValue = new ImInt(value);
            if (ImGui.inputInt("##value_input", inputValue, Math.max(step, 1), Math.max(step * 10, 10))) {
                setValue(inputValue.get());
                changed = true;
            }

            l.popItemWidth();
            l.popStyleVar();
            l.addVerticalSpacing(getMediumPadding());

            if (showRange) {
                String rangeText = getRangeText();
                float textWidth = ImGui.calcTextSize(rangeText).x;
                setCenterX(availableWidth, textWidth);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.60f, 0.60f, 0.60f, 1.0f);
                ImGui.text(rangeText);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());
            }

            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private String getRangeText() {
        if (minValue == Integer.MIN_VALUE && maxValue == Integer.MAX_VALUE) {
            return "范围: 无限制";
        }
        if (minValue == Integer.MIN_VALUE) {
            return "最大值: " + maxValue;
        }
        if (maxValue == Integer.MAX_VALUE) {
            return "最小值: " + minValue;
        }
        return "范围: " + minValue + " - " + maxValue;
    }

    private void normalizeRange() {
        if (minValue > maxValue) {
            int tmp = minValue;
            minValue = maxValue;
            maxValue = tmp;
        }
        step = Math.max(1, step);
        value = Math.max(minValue, Math.min(maxValue, value));
    }

    public void setValue(int value) {
        int clampedValue = Math.max(minValue, Math.min(maxValue, value));
        if (this.value != clampedValue) {
            this.value = clampedValue;
            updateOutput();
            markDirty();
        }
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, value);
        syncOutputPorts();
    }

    public int getValue() {
        return value;
    }

    public int getMinValue() {
        return minValue;
    }

    public void setMinValue(int minValue) {
        if (this.minValue != minValue) {
            this.minValue = minValue;
            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        if (this.maxValue != maxValue) {
            this.maxValue = maxValue;
            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        int normalized = Math.max(1, step);
        if (this.step != normalized) {
            this.step = normalized;
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("value", value);
        state.put("min", minValue);
        state.put("max", maxValue);
        state.put("step", step);
        state.put("showRange", showRange);
        state.put("showLabel", showLabel);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            if (stateMap.get("min") instanceof Number min) {
                this.minValue = min.intValue();
            }
            if (stateMap.get("max") instanceof Number max) {
                this.maxValue = max.intValue();
            }
            if (stateMap.get("step") instanceof Number stepValue) {
                this.step = Math.max(1, stepValue.intValue());
            }
            if (stateMap.get("showRange") instanceof Boolean showRange) {
                this.showRange = showRange;
            }
            if (stateMap.get("showLabel") instanceof Boolean showLabel) {
                this.showLabel = showLabel;
            }

            normalizeRange();

            Object valueObj = stateMap.get("value");
            if (valueObj instanceof Number number) {
                this.value = Math.max(minValue, Math.min(maxValue, number.intValue()));
            } else if (valueObj instanceof String str) {
                try {
                    this.value = Math.max(minValue, Math.min(maxValue, Integer.parseInt(str)));
                } catch (NumberFormatException ignored) {
                }
            }

            updateOutput();
            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setValue(number.intValue());
        }
    }
}
