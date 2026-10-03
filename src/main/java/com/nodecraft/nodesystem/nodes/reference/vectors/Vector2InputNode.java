package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import imgui.ImGui;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.vector2_input",
    displayName = "2D Vector Input",
    description = "Inputs a 2D vector (X/Y) and outputs VECTOR(x, y, 0).",
    category = "reference.vectors",
    order = 1
)
public class Vector2InputNode extends BaseCustomUINode {

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "X", category = "Value", order = 1)
    private double x = 0.0d;

    @NodeProperty(displayName = "Y", category = "Value", order = 2)
    private double y = 0.0d;

    @NodeProperty(displayName = "Precision", category = "UI", order = 10,
        description = "Decimal places shown in the node panel inputs")
    private int precision = 2;

    public Vector2InputNode() {
        super(UUID.randomUUID(), "reference.vectors.vector2_input");
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "VECTOR(x, y, 0)", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when X and Y are finite", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Inputs a 2D vector (X/Y) and outputs VECTOR(x, y, 0).";
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

            changed |= renderComponentInput("X", availableWidth, l, getResolvedX(), this::setX, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Y", availableWidth, l, getResolvedY(), this::setY, baseCursorX, edgeMargin);

            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private boolean renderComponentInput(String label, float availableWidth, LayoutHelper l, double currentValue,
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
        if (!VectorUtils.isFinite(x) || !VectorUtils.isFinite(y)) {
            if (!VectorUtils.isFinite(x)) {
                writeInvalid("X must be finite DOUBLE");
            } else {
                writeInvalid("Y must be finite DOUBLE");
            }
        } else {
            outputValues.put(OUTPUT_VECTOR_ID, VectorUtils.toVectorPort(new org.joml.Vector3d(x, y, 0.0d)));
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
            syncOutputPorts();
        }
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_VECTOR_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
        syncOutputPorts();
    }

    private double getResolvedX() {
        return VectorUtils.isFinite(x) ? x : 0.0d;
    }

    private double getResolvedY() {
        return VectorUtils.isFinite(y) ? y : 0.0d;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        if (!VectorUtils.isFinite(x)) {
            writeInvalid("X must be finite DOUBLE");
            markDirty();
            return;
        }
        if (Double.compare(this.x, x) != 0) {
            this.x = x;
            updateOutput();
            markDirty();
        }
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        if (!VectorUtils.isFinite(y)) {
            writeInvalid("Y must be finite DOUBLE");
            markDirty();
            return;
        }
        if (Double.compare(this.y, y) != 0) {
            this.y = y;
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
        state.put("x", x);
        state.put("y", y);
        state.put("precision", precision);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("x") instanceof Number n && VectorUtils.isFinite(n.doubleValue())) {
            x = n.doubleValue();
        }
        if (map.get("y") instanceof Number n && VectorUtils.isFinite(n.doubleValue())) {
            y = n.doubleValue();
        }
        if (map.get("precision") instanceof Number precisionValue) {
            precision = Math.max(0, Math.min(6, precisionValue.intValue()));
        }
        updateOutput();
        invalidateCache();
        markDirty();
    }
}
