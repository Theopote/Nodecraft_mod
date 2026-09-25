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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.angle",
    displayName = "Angle Slider",
    description = "输出一个可通过滑动条调节的角度值（度）。需要弧度时使用 Degrees To Radians。",
    category = "input.numeric",
    order = 4
)
public class AngleSliderNode extends BaseCustomUINode {

    private static final String OUTPUT_ANGLE_ID = "output_angle";

    @NodeProperty(displayName = "当前角度", category = "角度", order = 1,
        description = "当前角度（度）")
    private double currentAngle = 0.0;

    @NodeProperty(displayName = "最小角度", category = "角度", order = 2,
        description = "滑动条允许的最小角度（度）")
    private double minAngle = 0.0;

    @NodeProperty(displayName = "最大角度", category = "角度", order = 3,
        description = "滑动条允许的最大角度（度）")
    private double maxAngle = 360.0;

    @NodeProperty(displayName = "显示范围输入", category = "UI设置", order = 10,
        description = "显示最小角度和最大角度输入")
    private boolean showRangeInputs = true;

    public AngleSliderNode() {
        super(UUID.randomUUID(), "input.numeric.angle");
        IPort angleOutput = new BasePort(OUTPUT_ANGLE_ID, "Angle", "当前角度（度）", NodeDataType.DOUBLE, this);
        addOutputPort(angleOutput);
        normalizeRange();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "输出一个可通过滑动条调节的角度值（度）。需要弧度时使用 Degrees To Radians。";
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
            float[] sliderT = {(float) NumericInputUtils.normalizedInRange(currentAngle, minAngle, maxAngle)};
            if (ImGui.sliderFloat("##angle_slider", sliderT, 0.0f, 1.0f, "%.1f°")) {
                setCurrentAngle(NumericInputUtils.lerpFromNormalized(sliderT[0], minAngle, maxAngle));
                changed = true;
            }
            l.popItemWidth();
            l.addVerticalSpacing(getMediumPadding());

            return changed;
        });
    }

    private void normalizeRange() {
        minAngle = NumericInputUtils.sanitizeFiniteBound(minAngle, 0.0d);
        maxAngle = NumericInputUtils.sanitizeFiniteBound(maxAngle, 360.0d);
        if (!NumericInputUtils.isFiniteUsableSpan(minAngle, maxAngle)) {
            minAngle = 0.0d;
            maxAngle = 360.0d;
        } else if (Double.compare(minAngle, maxAngle) > 0) {
            double temp = minAngle;
            minAngle = maxAngle;
            maxAngle = temp;
        }
        setCurrentAngle(clampAngle(currentAngle));
    }

    private double clampAngle(double angle) {
        return NumericInputUtils.clampFiniteRange(
                NumericInputUtils.acceptFiniteOrKeep(angle, currentAngle),
                minAngle,
                maxAngle
        );
    }

    private void updateOutput() {
        // Graph language: angles are always degrees (see docs/nodecraft-v1-node-language.md).
        outputValues.put(OUTPUT_ANGLE_ID, currentAngle);
        syncOutputPorts();
    }

    public double getCurrentAngle() {
        return currentAngle;
    }

    public void setCurrentAngle(double currentAngle) {
        double normalized = clampAngle(currentAngle);
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
        double sanitized = NumericInputUtils.sanitizeFiniteBound(minAngle, this.minAngle);
        if (Double.compare(this.minAngle, sanitized) != 0) {
            this.minAngle = sanitized;
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
        double sanitized = NumericInputUtils.sanitizeFiniteBound(maxAngle, this.maxAngle);
        if (Double.compare(this.maxAngle, sanitized) != 0) {
            this.maxAngle = sanitized;
            normalizeRange();
            updateOutput();
            invalidateCache();
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
        state.put("showRangeInputs", showRangeInputs);
        state.put("minAngle", minAngle);
        state.put("maxAngle", maxAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            // Angle is always stored/emitted in degrees. Any legacy "unit" key is ignored
            // (pre-release: no behavior-preserving radians migration).
            if (map.get("showRangeInputs") instanceof Boolean value) {
                this.showRangeInputs = value;
            }
            if (map.get("minAngle") instanceof Number value) {
                this.minAngle = NumericInputUtils.sanitizeFiniteBound(value.doubleValue(), this.minAngle);
            }
            if (map.get("maxAngle") instanceof Number value) {
                this.maxAngle = NumericInputUtils.sanitizeFiniteBound(value.doubleValue(), this.maxAngle);
            }

            normalizeRange();

            Object angleValue = map.get("angle");
            if (angleValue instanceof Number number) {
                setCurrentAngle(number.doubleValue());
            } else if (angleValue instanceof String text) {
                try {
                    setCurrentAngle(Double.parseDouble(text));
                } catch (NumberFormatException ignored) {
                }
            }

            invalidateCache();
            markDirty();
        } else if (state instanceof Number number) {
            setCurrentAngle(number.doubleValue());
        }
    }
}
