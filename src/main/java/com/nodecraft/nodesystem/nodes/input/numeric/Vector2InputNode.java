package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

@NodeInfo(
    id = "input.numeric.vector2_input",
    displayName = "2D Vector Input",
    description = "Inputs a 2D vector (X/Y or U/V) and outputs vector + components.",
    category = "input.numeric",
    order = 8
)
public class Vector2InputNode extends BaseCustomUINode {

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_UV_ID = "output_uv";

    @NodeProperty(displayName = "X", category = "Value", order = 1)
    private double x = 0.0d;

    @NodeProperty(displayName = "Y", category = "Value", order = 2)
    private double y = 0.0d;

    @NodeProperty(displayName = "Precision", category = "UI", order = 10,
        description = "Decimal places shown in the node panel inputs")
    private int precision = 2;

    public Vector2InputNode() {
        super(UUID.randomUUID(), "input.numeric.vector2_input");
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "2D vector as Vector3d(x,y,0)", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X / U component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y / V component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_UV_ID, "UV", "UV pair list [x, y]", NodeDataType.LIST, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Inputs a 2D vector (X/Y or U/V) and outputs vector + components.";
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

            changed |= renderComponentInput("X", availableWidth, l, x, this::setX, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Y", availableWidth, l, y, this::setY, baseCursorX, edgeMargin);

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
        outputValues.put(OUTPUT_VECTOR_ID, new Vector3d(x, y, 0.0d));
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_UV_ID, List.of(x, y));
        syncOutputPorts();
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
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
        if (map.get("x") instanceof Number n) {
            x = n.doubleValue();
        }
        if (map.get("y") instanceof Number n) {
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
