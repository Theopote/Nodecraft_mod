package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
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
    id = "input.numeric.angle",
    displayName = "Angle Slider",
    description = "输出一个可通过滑动条调节的角度值，支持度和弧度输出。",
    category = "input.numeric",
    order = 4
)
public class AngleSliderNode extends BaseCustomUINode {

    public enum AngleUnit {
        DEGREES("度", "°"),
        RADIANS("弧度", "rad");

        private final String displayName;
        private final String symbol;

        AngleUnit(String displayName, String symbol) {
            this.displayName = displayName;
            this.symbol = symbol;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    private static final String OUTPUT_ANGLE_ID = "output_angle";

    @NodeProperty(displayName = "当前角度", category = "角度", order = 1,
        description = "当前角度，内部始终按度存储")
    private double currentAngle = 0.0;

    @NodeProperty(displayName = "最小角度", category = "角度", order = 2,
        description = "滑动条允许的最小角度")
    private double minAngle = 0.0;

    @NodeProperty(displayName = "最大角度", category = "角度", order = 3,
        description = "滑动条允许的最大角度")
    private double maxAngle = 360.0;

    @NodeProperty(displayName = "输出单位", category = "输出", order = 4,
        description = "选择输出为度或弧度")
    private AngleUnit angleUnit = AngleUnit.DEGREES;

    @NodeProperty(displayName = "显示范围输入", category = "UI设置", order = 10,
        description = "显示最小角度和最大角度输入")
    private boolean showRangeInputs = true;

    public AngleSliderNode() {
        super(UUID.randomUUID(), "input.numeric.angle");
        IPort angleOutput = new BasePort(OUTPUT_ANGLE_ID, "Angle", "当前角度值", NodeDataType.DOUBLE, this);
        addOutputPort(angleOutput);
        normalizeRange();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "输出一个可通过滑动条调节的角度值，支持度和弧度输出。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        if (showRangeInputs) {
            height += ImGui.getFrameHeight();
            height += getMediumPadding();
        }
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return showRangeInputs ? 260.0f : 220.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float availableWidth = l.getAvailableContentWidth(width);

            l.addVerticalSpacing(getMediumPadding());

            if (showRangeInputs) {
                float itemWidth = Math.max((availableWidth - l.toPixels(8.0f)) / 2.0f, l.toPixels(80.0f));
                l.setItemWidth(itemWidth / Math.max(zoom, 0.001f));
                float[] minInput = {(float) minAngle};
                if (ImGui.dragFloat("最小角度", minInput, 1.0f, -3600.0f, 3600.0f, "%.1f°")) {
                    setMinAngle(minInput[0]);
                    changed = true;
                }
                ImGui.sameLine();
                float[] maxInput = {(float) maxAngle};
                if (ImGui.dragFloat("最大角度", maxInput, 1.0f, -3600.0f, 3600.0f, "%.1f°")) {
                    setMaxAngle(maxInput[0]);
                    changed = true;
                }
                l.popItemWidth();
                l.addVerticalSpacing(getMediumPadding());
            }

            l.setItemWidth(Math.max(availableWidth / Math.max(zoom, 0.001f), 1.0f));
            float[] angleValue = {(float) currentAngle};
            if (ImGui.sliderFloat("##angle_slider", angleValue, (float) minAngle, (float) maxAngle, "%.1f°")) {
                setCurrentAngle(angleValue[0]);
                changed = true;
            }
            l.popItemWidth();
            l.addVerticalSpacing(getMediumPadding());

            l.setItemWidth(Math.min(l.toPixels(110.0f), availableWidth) / Math.max(zoom, 0.001f));
            ImInt unitIndex = new ImInt(angleUnit == AngleUnit.DEGREES ? 0 : 1);
            if (ImGui.combo("输出单位", unitIndex, new String[]{"度", "弧度"})) {
                setAngleUnit(unitIndex.get() == 0 ? AngleUnit.DEGREES : AngleUnit.RADIANS);
                changed = true;
            }
            l.popItemWidth();
            l.addVerticalSpacing(getMediumPadding());

            return changed;
        });
    }

    private void normalizeRange() {
        if (Double.compare(minAngle, maxAngle) > 0) {
            double temp = minAngle;
            minAngle = maxAngle;
            maxAngle = temp;
        }
        currentAngle = Math.max(minAngle, Math.min(maxAngle, currentAngle));
    }

    private void updateOutput() {
        double outputValue = angleUnit == AngleUnit.RADIANS ? Math.toRadians(currentAngle) : currentAngle;
        outputValues.put(OUTPUT_ANGLE_ID, outputValue);
        syncOutputPorts();
    }

    public double getCurrentAngle() {
        return currentAngle;
    }

    public void setCurrentAngle(double currentAngle) {
        double normalized = Math.max(minAngle, Math.min(maxAngle, currentAngle));
        if (Double.compare(this.currentAngle, normalized) != 0) {
            this.currentAngle = normalized;
            updateOutput();
            markDirty();
        }
    }

    public double getMinAngle() {
        return minAngle;
    }

    public void setMinAngle(double minAngle) {
        if (Double.compare(this.minAngle, minAngle) != 0) {
            this.minAngle = minAngle;
            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public double getMaxAngle() {
        return maxAngle;
    }

    public void setMaxAngle(double maxAngle) {
        if (Double.compare(this.maxAngle, maxAngle) != 0) {
            this.maxAngle = maxAngle;
            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public AngleUnit getAngleUnit() {
        return angleUnit;
    }

    public void setAngleUnit(AngleUnit angleUnit) {
        if (angleUnit != null && this.angleUnit != angleUnit) {
            this.angleUnit = angleUnit;
            updateOutput();
            markDirty();
        }
    }

    public boolean isShowRangeInputs() {
        return showRangeInputs;
    }

    public void setShowRangeInputs(boolean showRangeInputs) {
        if (this.showRangeInputs != showRangeInputs) {
            this.showRangeInputs = showRangeInputs;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("angle", currentAngle);
        state.put("unit", angleUnit.name());
        state.put("showRangeInputs", showRangeInputs);
        state.put("minAngle", minAngle);
        state.put("maxAngle", maxAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("unit") instanceof String unitName) {
                try {
                    this.angleUnit = AngleUnit.valueOf(unitName);
                } catch (IllegalArgumentException ignored) {
                    this.angleUnit = AngleUnit.DEGREES;
                }
            }
            if (map.get("showRangeInputs") instanceof Boolean value) {
                this.showRangeInputs = value;
            }
            if (map.get("minAngle") instanceof Number value) {
                this.minAngle = value.doubleValue();
            }
            if (map.get("maxAngle") instanceof Number value) {
                this.maxAngle = value.doubleValue();
            }

            Object angleValue = map.get("angle");
            if (angleValue instanceof Number number) {
                this.currentAngle = number.doubleValue();
            } else if (angleValue instanceof String text) {
                try {
                    this.currentAngle = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                }
            }

            normalizeRange();
            updateOutput();
            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setCurrentAngle(number.doubleValue());
        }
    }
}
