package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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
    effect = NodeEffect.PURE,
    id = "input.numeric.range",
    displayName = "Domain Input",
    description = "Defines a directed numeric domain (Start→End) and outputs domain, start, end, and directed span.",
    category = "input.numeric",
    order = 9
)
public class RangeInputNode extends BaseCustomUINode {

    private static final String OUTPUT_DOMAIN_ID = "output_domain";
    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_SPAN_ID = "output_span";

    @NodeProperty(displayName = "Start", category = "Value", order = 1)
    private double start = 0.0d;

    @NodeProperty(displayName = "End", category = "Value", order = 2)
    private double end = 1.0d;

    @NodeProperty(displayName = "Precision", category = "UI", order = 10,
        description = "Decimal places shown in the node panel inputs")
    private int precision = 2;

    public RangeInputNode() {
        super(UUID.randomUUID(), "input.numeric.range");
        addOutputPort(new BasePort(OUTPUT_DOMAIN_ID, "Domain", "Directed numeric domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));
        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Domain start value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Domain end value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SPAN_ID, "Span", "Directed span (End - Start)", NodeDataType.DOUBLE, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Defines a directed numeric domain (Start→End). Use with Remap, Clamp, and Random nodes.";
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

            changed |= renderBoundInput("Start", availableWidth, l, start, this::setStart, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderBoundInput("End", availableWidth, l, end, this::setEnd, baseCursorX, edgeMargin);

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
        NumericRangeData domain = new NumericRangeData(start, end);
        outputValues.put(OUTPUT_DOMAIN_ID, domain);
        outputValues.put(OUTPUT_START_ID, domain.start());
        outputValues.put(OUTPUT_END_ID, domain.end());
        outputValues.put(OUTPUT_SPAN_ID, domain.span());
        syncOutputPorts();
    }

    public double getStart() {
        return start;
    }

    public void setStart(double start) {
        if (Double.compare(this.start, start) != 0) {
            this.start = start;
            updateOutput();
            markDirty();
        }
    }

    /** @deprecated Use {@link #getStart()}. */
    @Deprecated
    public double getMin() {
        return start;
    }

    /** @deprecated Use {@link #setStart(double)}. */
    @Deprecated
    public void setMin(double min) {
        setStart(min);
    }

    public double getEnd() {
        return end;
    }

    public void setEnd(double end) {
        if (Double.compare(this.end, end) != 0) {
            this.end = end;
            updateOutput();
            markDirty();
        }
    }

    /** @deprecated Use {@link #getEnd()}. */
    @Deprecated
    public double getMax() {
        return end;
    }

    /** @deprecated Use {@link #setEnd(double)}. */
    @Deprecated
    public void setMax(double max) {
        setEnd(max);
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
        state.put("start", start);
        state.put("end", end);
        state.put("precision", precision);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("start") instanceof Number n) {
            start = n.doubleValue();
        } else if (map.get("min") instanceof Number n) {
            start = n.doubleValue();
        }
        if (map.get("end") instanceof Number n) {
            end = n.doubleValue();
        } else if (map.get("max") instanceof Number n) {
            end = n.doubleValue();
        }
        if (map.get("precision") instanceof Number precisionValue) {
            precision = Math.max(0, Math.min(6, precisionValue.intValue()));
        }
        updateOutput();
        invalidateCache();
        markDirty();
    }
}
