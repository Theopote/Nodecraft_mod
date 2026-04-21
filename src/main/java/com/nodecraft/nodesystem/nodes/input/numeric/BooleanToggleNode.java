package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.basic.boolean_toggle",
    displayName = "Boolean Toggle",
    description = "提供一个可以切换的布尔值开关控制",
    category = "input.basic",
    order = 6
)
public class BooleanToggleNode extends BaseCustomUINode {

    private static final String OUTPUT_VALUE_ID = "output_value";

    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
        description = "当前输出的布尔值")
    private boolean value = false;

    @NodeProperty(displayName = "True 标签", category = "标签", order = 2,
        description = "值为 true 时显示的标签文本")
    private String trueLabel = "ON";

    @NodeProperty(displayName = "False 标签", category = "标签", order = 3,
        description = "值为 false 时显示的标签文本")
    private String falseLabel = "OFF";

    @NodeProperty(displayName = "显示状态文本", category = "UI设置", order = 10,
        description = "是否在开关下方显示启用状态文本")
    private boolean showStateText = true;

    public BooleanToggleNode() {
        super(UUID.randomUUID(), "input.basic.boolean_toggle");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "当前布尔值", NodeDataType.BOOLEAN, this);
        addOutputPort(valueOutput);
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "提供一个可以切换的布尔值开关控制。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += 24f;
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float switchWidth = 50f;
        String longerLabel = trueLabel.length() > falseLabel.length() ? trueLabel : falseLabel;
        float labelWidth = ImGui.calcTextSize(longerLabel).x;
        return Math.max(112f, switchWidth + labelWidth + 12f) + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            boolean interacted;

            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(80.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            float switchWidth = 44f * zoom;
            float switchHeight = 22f * zoom;
            float knobRadius = 9f * zoom;
            float knobPadding = 2f * zoom;

            String currentLabel = value ? trueLabel : falseLabel;
            float labelWidth = ImGui.calcTextSize(currentLabel).x;
            float totalWidth = switchWidth + 8f * zoom + labelWidth;
            float localOffset = Math.max(0.0f, (availableWidth - totalWidth) * 0.5f);
            ImGui.setCursorPosX(baseCursorX + edgeMargin + localOffset);

            ImVec2 cursor = ImGui.getCursorScreenPos();
            float switchX = cursor.x;
            float switchY = cursor.y;
            float rounding = switchHeight * 0.5f;

            ImDrawList drawList = ImGui.getWindowDrawList();
            int bgColor = value
                ? ImGui.colorConvertFloat4ToU32(0.15f, 0.68f, 0.38f, 1.0f)
                : ImGui.colorConvertFloat4ToU32(0.45f, 0.45f, 0.50f, 1.0f);
            drawList.addRectFilled(switchX, switchY, switchX + switchWidth, switchY + switchHeight, bgColor, rounding);

            float knobX = value
                ? switchX + switchWidth - knobRadius - knobPadding
                : switchX + knobRadius + knobPadding;
            float knobY = switchY + switchHeight * 0.5f;
            drawList.addCircleFilled(knobX, knobY, knobRadius, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f));

            ImGui.invisibleButton("##toggle_switch", switchWidth, switchHeight);
            boolean hovered = ImGui.isItemHovered();
            boolean active = ImGui.isItemActive();
            interacted = hovered || active;

            if (hovered) {
                drawList.addRectFilled(
                    switchX, switchY, switchX + switchWidth, switchY + switchHeight,
                    ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), rounding
                );
            }

            if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
                toggle();
                changed = true;
            }

            ImGui.sameLine();
            ImGui.dummy(4f * zoom, 0);
            ImGui.sameLine();

            float textY = (switchHeight - ImGui.getTextLineHeight()) * 0.5f;
            ImGui.setCursorPosY(ImGui.getCursorPosY() + textY);
            if (value) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.15f, 0.85f, 0.45f, 1.0f);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.65f, 0.65f, 1.0f);
            }
            ImGui.text(currentLabel);
            ImGui.popStyleColor();

            if (false) {
                l.addVerticalSpacing(getSmallPadding());
                String stateText = value ? "● 已启用" : "● 已禁用";
                float stateWidth = ImGui.calcTextSize(stateText).x;
                setCenterX(availableWidth, stateWidth);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.55f, 0.55f, 1.0f);
                ImGui.text(stateText);
                ImGui.popStyleColor();
            }

            l.addVerticalSpacing(getSmallPadding());
            return interacted || changed;
        });
    }

    public void toggle() {
        setValue(!value);
    }

    public void setValue(boolean value) {
        if (this.value != value) {
            this.value = value;
            updateOutput();
            markDirty();
        }
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, this.value);
        syncOutputPorts();
    }

    public boolean getValue() {
        return value;
    }

    public String getTrueLabel() {
        return trueLabel;
    }

    public void setTrueLabel(String trueLabel) {
        String normalized = trueLabel != null ? trueLabel : "ON";
        if (!this.trueLabel.equals(normalized)) {
            this.trueLabel = normalized;
            invalidateCache();
            markDirty();
        }
    }

    public String getFalseLabel() {
        return falseLabel;
    }

    public void setFalseLabel(String falseLabel) {
        String normalized = falseLabel != null ? falseLabel : "OFF";
        if (!this.falseLabel.equals(normalized)) {
            this.falseLabel = normalized;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowStateText() {
        return showStateText;
    }

    public void setShowStateText(boolean showStateText) {
        if (this.showStateText != showStateText) {
            this.showStateText = showStateText;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("value", getValue());
        state.put("trueLabel", getTrueLabel());
        state.put("falseLabel", getFalseLabel());
        state.put("showStateText", isShowStateText());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            if (stateMap.get("trueLabel") instanceof String label) {
                setTrueLabel(label);
            }
            if (stateMap.get("falseLabel") instanceof String label) {
                setFalseLabel(label);
            }
            if (stateMap.get("showStateText") instanceof Boolean showState) {
                setShowStateText(showState);
            }
            if (stateMap.containsKey("value")) {
                Object valueObj = stateMap.get("value");
                if (valueObj instanceof Boolean bool) {
                    setValue(bool);
                } else if (valueObj instanceof String str) {
                    setValue(Boolean.parseBoolean(str));
                } else if (valueObj instanceof Number number) {
                    setValue(number.intValue() != 0);
                }
            }
        } else if (state instanceof Boolean bool) {
            setValue(bool);
        } else if (state instanceof String str) {
            setValue(Boolean.parseBoolean(str));
        } else if (state instanceof Number number) {
            setValue(number.intValue() != 0);
        }
    }
}
