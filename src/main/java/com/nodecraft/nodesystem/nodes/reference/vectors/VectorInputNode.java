package com.nodecraft.nodesystem.nodes.reference.vectors;

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
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "reference.vectors.vector",
    displayName = "Vector Input",
    description = "Inputs a 3D vector and outputs the vector plus X, Y, and Z components.",
    category = "reference.vectors",
    order = 0
)
public class VectorInputNode extends BaseCustomUINode {

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    @NodeProperty(displayName = "X", category = "Components", order = 1, description = "X component")
    private double x = 0.0;

    @NodeProperty(displayName = "Y", category = "Components", order = 2, description = "Y component")
    private double y = 0.0;

    @NodeProperty(displayName = "Z", category = "Components", order = 3, description = "Z component")
    private double z = 0.0;

    @NodeProperty(displayName = "Show Label", category = "UI Settings", order = 10,
        description = "Whether to show the current vector summary")
    private boolean showLabel = true;

    @NodeProperty(displayName = "Precision", category = "UI Settings", order = 11,
        description = "Decimal places shown in the UI")
    private int precision = 2;

    public VectorInputNode() {
        super(UUID.randomUUID(), "reference.vectors.vector");
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "3D vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z component", NodeDataType.DOUBLE, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Inputs a 3D vector and outputs the vector plus X, Y, and Z components.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight() * 3;
        height += getSmallPadding() * 2;
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
            changed |= renderComponentInput("Z", availableWidth, l, z, this::setZ, baseCursorX, edgeMargin);

            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private boolean renderComponentInput(String label, float availableWidth, LayoutHelper l, double currentValue,
                                         java.util.function.DoubleConsumer setter, float baseCursorX, float edgeMargin) {
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
        Vector3d vector = new Vector3d(x, y, z);
        outputValues.put(OUTPUT_VECTOR_ID, vector);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
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

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        if (Double.compare(this.z, z) != 0) {
            this.z = z;
            updateOutput();
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
        state.put("z", z);
        state.put("showLabel", showLabel);
        state.put("precision", precision);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("x") instanceof Number number) {
                this.x = number.doubleValue();
            }
            if (map.get("y") instanceof Number number) {
                this.y = number.doubleValue();
            }
            if (map.get("z") instanceof Number number) {
                this.z = number.doubleValue();
            }
            if (map.get("showLabel") instanceof Boolean show) {
                this.showLabel = show;
            }
            if (map.get("precision") instanceof Number precisionValue) {
                this.precision = Math.max(0, Math.min(6, precisionValue.intValue()));
            }
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }
}
