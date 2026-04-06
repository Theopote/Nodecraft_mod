package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImInt;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.basic.integer_slider",
    displayName = "整数滑动条",
    description = "输出一个可通过滑动条调节的整数值",
    category = "inputs.basic"
)
public class IntegerSliderNode extends BaseCustomUINode {

    private static final String OUTPUT_ID = "value";

    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
        description = "滑动条当前输出的整数值")
    private int value = 50;

    @NodeProperty(displayName = "最小值", category = "范围", order = 2,
        description = "滑动条允许的最小值")
    private int min = 0;

    @NodeProperty(displayName = "最大值", category = "范围", order = 3,
        description = "滑动条允许的最大值")
    private int max = 100;

    @NodeProperty(displayName = "步长", category = "范围", order = 4,
        description = "点击加减按钮时每次变化的数值")
    private int step = 1;

    @NodeProperty(displayName = "显示数值输入", category = "UI设置", order = 10,
        description = "在滑动条上方显示手动输入框")
    private boolean showValueInput = true;

    @NodeProperty(displayName = "显示范围信息", category = "UI设置", order = 11,
        description = "在底部显示最小值、最大值和步长")
    private boolean showRangeInfo = true;

    @NodeProperty(displayName = "紧凑模式", category = "UI设置", order = 12,
        description = "隐藏冗余信息，减少节点高度")
    private boolean compact = false;

    public IntegerSliderNode() {
        super(UUID.randomUUID(), "inputs.basic.integer_slider");
        addOutputPort(new BasePort(OUTPUT_ID, "Value", "选中的整数值", NodeDataType.INTEGER, this));
        normalizeRange();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "输出一个可通过滑动条调节的整数值。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();

        if (!compact && showValueInput) {
            height += ImGui.getFrameHeight();
            height += getMediumPadding();
        }

        height += ImGui.getFrameHeight();
        height += getMediumPadding();

        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return compact ? 180.0f : 220.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            boolean interacted = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());

            if (!compact && showValueInput) {
                float buttonHeight = ImGui.getFrameHeight();
                float buttonWidth = buttonHeight;
                float spacingX = ImGui.getStyle().getItemSpacingX();
                float inputWidth = Math.min(120.0f, availableWidth / Math.max(zoom, 0.001f));
                float inputPixelWidth = l.toPixels(inputWidth);
                float rowWidth = buttonWidth * 2.0f + spacingX * 2.0f + inputPixelWidth;

                ImGui.setCursorPosX(baseCursorX + edgeMargin + Math.max(0.0f, (availableWidth - rowWidth) * 0.5f));
                ImGui.pushID("input_minus_btn");
                if (ImGui.button("-", buttonWidth, buttonHeight)) {
                    setValue(value - step);
                    changed = true;
                }
                interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
                ImGui.popID();

                ImGui.sameLine();
                l.pushFramePadding(4.0f, 2.0f);
                l.setItemWidth(inputWidth);
                ImInt inputValue = new ImInt(value);
                if (ImGui.inputInt("##value_input", inputValue, 0, 0)) {
                    setValue(inputValue.get());
                    changed = true;
                }
                interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
                l.popItemWidth();
                l.popStyleVar();

                ImGui.sameLine();
                ImGui.pushID("input_plus_btn");
                if (ImGui.button("+", buttonWidth, buttonHeight)) {
                    setValue(value + step);
                    changed = true;
                }
                interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
                ImGui.popID();

                l.addVerticalSpacing(getMediumPadding());
            }

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.setItemWidth(availableWidth / Math.max(zoom, 0.001f));
            int[] sliderValue = {value};
            if (ImGui.sliderInt("##slider", sliderValue, min, max)) {
                setValue(sliderValue[0]);
                changed = true;
            }
            interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
            l.popItemWidth();

            l.addVerticalSpacing(getMediumPadding());

            l.addVerticalSpacing(getMediumPadding());
            return interacted || changed;
        });
    }

    public int getValue() {
        return value;
    }

    public void setValue(int newValue) {
        int normalized = clampValue(newValue);
        if (value != normalized) {
            value = normalized;
            updateOutput();
            markDirty();
        }
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        if (this.min != min) {
            this.min = min;
            normalizeRange();
            invalidateCache();
            markDirty();
        }
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        if (this.max != max) {
            this.max = max;
            normalizeRange();
            invalidateCache();
            markDirty();
        }
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        int safeStep = Math.max(1, step);
        if (this.step != safeStep) {
            this.step = safeStep;
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

    public boolean isShowRangeInfo() {
        return showRangeInfo;
    }

    public void setShowRangeInfo(boolean showRangeInfo) {
        if (this.showRangeInfo != showRangeInfo) {
            this.showRangeInfo = showRangeInfo;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isCompact() {
        return compact;
    }

    public void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("value", value);
        state.put("min", min);
        state.put("max", max);
        state.put("step", step);
        state.put("showValueInput", showValueInput);
        state.put("showRangeInfo", showRangeInfo);
        state.put("compact", compact);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("min") instanceof Number minValue) {
            this.min = minValue.intValue();
        }
        if (map.get("max") instanceof Number maxValue) {
            this.max = maxValue.intValue();
        }
        if (map.get("step") instanceof Number stepValue) {
            this.step = Math.max(1, stepValue.intValue());
        }
        if (map.get("showValueInput") instanceof Boolean showInput) {
            this.showValueInput = showInput;
        }
        if (map.get("showRangeInfo") instanceof Boolean showInfo) {
            this.showRangeInfo = showInfo;
        }
        if (map.get("compact") instanceof Boolean compactMode) {
            this.compact = compactMode;
        }

        normalizeRange();

        Object savedValue = map.get("value");
        if (savedValue instanceof Number number) {
            value = clampValue(number.intValue());
        } else {
            value = clampValue(value);
        }

        updateOutput();
        invalidateCache();
        markDirty();
    }

    private void normalizeRange() {
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        step = Math.max(1, step);
        value = clampValue(value);
        updateOutput();
    }

    private int clampValue(int rawValue) {
        return Math.max(min, Math.min(max, rawValue));
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_ID, value);
        syncOutputPorts();
    }
}
