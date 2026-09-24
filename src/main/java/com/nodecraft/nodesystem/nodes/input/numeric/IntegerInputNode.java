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
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.integer",
    displayName = "Integer Input",
    description = "允许手动输入整数值的节点",
    category = "input.numeric",
    order = 0
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

    public IntegerInputNode() {
        super(UUID.randomUUID(), "input.numeric.integer");
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
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 140.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());
            float inputWidth = availableWidth / Math.max(zoom, 0.001f);
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.pushFramePadding(4.0f, 2.0f);
            l.setItemWidth(inputWidth);

            ImInt inputValue = new ImInt(value);
            if (ImGui.inputInt("##value_input", inputValue, Math.max(step, 1), Math.max(step * 10, 10))) {
                setValue(inputValue.get());
                changed = true;
            }

            l.popItemWidth();
            l.popStyleVar();
            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("value", value);
        state.put("min", minValue);
        state.put("max", maxValue);
        state.put("step", step);
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
