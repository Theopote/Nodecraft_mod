package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

@NodeInfo(
    id = "input.numeric.range",
    displayName = "Range Input",
    description = "Defines a numeric interval and outputs min/max/span plus a range object.",
    category = "input.numeric",
    order = 9
)
public class RangeInputNode extends BaseCustomUINode {

    private static final String OUTPUT_RANGE_ID = "output_range";
    private static final String OUTPUT_MIN_ID = "output_min";
    private static final String OUTPUT_MAX_ID = "output_max";
    private static final String OUTPUT_SPAN_ID = "output_span";

    @NodeProperty(displayName = "Min", category = "Value", order = 1)
    private double min = 0.0d;

    @NodeProperty(displayName = "Max", category = "Value", order = 2)
    private double max = 1.0d;

    @NodeProperty(displayName = "Precision", category = "UI", order = 10,
        description = "Decimal places shown in the node panel inputs")
    private int precision = 2;

    public RangeInputNode() {
        super(UUID.randomUUID(), "input.numeric.range");
        addOutputPort(new BasePort(OUTPUT_RANGE_ID, "Range", "Numeric range object", NodeDataType.NUMERIC_RANGE, this));
        addOutputPort(new BasePort(OUTPUT_MIN_ID, "Min", "Lower bound", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MAX_ID, "Max", "Upper bound", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SPAN_ID, "Span", "Range span (max - min)", NodeDataType.DOUBLE, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Defines a numeric interval and outputs min/max/span plus a range object.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight() * 2;
        height += getSmallPadding();
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 180f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());

            changed |= renderBoundInput("Min", availableWidth, l, min, this::setMin, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderBoundInput("Max", availableWidth, l, max, this::setMax, baseCursorX, edgeMargin);

            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private boolean renderBoundInput(String label, float availableWidth, LayoutHelper l, double currentValue,
                                     DoubleConsumer setter, float baseCursorX, float edgeMargin) {
        float labelWidth = ImGui.calcTextSize(label).x;
        ImGui.setCursorPosX(baseCursorX + edgeMargin);
        ImGui.text(label);
        ImGui.sameLine();

        float inputWidth = Math.max(availableWidth - labelWidth - ImGui.getStyle().getItemSpacingX(), l.toPixels(80f));
        l.setItemWidth(inputWidth / Math.max(l.getZoom(), 0.001f));
        ImDouble valueInput = new ImDouble(currentValue);
        boolean changed = ImGui.inputDouble("##" + label.toLowerCase(), valueInput, 0.0, 0.0,
            "%." + getSafePrecision() + "f");
        l.popItemWidth();
        if (changed) {
            setter.accept(valueInput.get());
        }
        return changed;
    }

    private int getSafePrecision() {
        return Math.max(0, Math.min(6, precision));
    }

    private void updateOutput() {
        double resolvedMin = Math.min(min, max);
        double resolvedMax = Math.max(min, max);
        NumericRangeData range = new NumericRangeData(resolvedMin, resolvedMax);
        outputValues.put(OUTPUT_RANGE_ID, range);
        outputValues.put(OUTPUT_MIN_ID, range.min());
        outputValues.put(OUTPUT_MAX_ID, range.max());
        outputValues.put(OUTPUT_SPAN_ID, range.span());
        syncOutputPorts();
    }

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        if (Double.compare(this.min, min) != 0) {
            this.min = min;
            updateOutput();
            markDirty();
        }
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        if (Double.compare(this.max, max) != 0) {
            this.max = max;
            updateOutput();
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
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("min", min);
        state.put("max", max);
        state.put("precision", precision);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("min") instanceof Number n) {
            min = n.doubleValue();
        }
        if (map.get("max") instanceof Number n) {
            max = n.doubleValue();
        }
        if (map.get("precision") instanceof Number precisionValue) {
            precision = Math.max(0, Math.min(6, precisionValue.intValue()));
        }
        updateOutput();
        invalidateCache();
        markDirty();
    }
}
