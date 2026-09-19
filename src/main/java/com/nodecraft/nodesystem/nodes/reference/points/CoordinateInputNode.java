package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImInt;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_from_coordinates",
    displayName = "Coordinate Input",
    description = "Inputs an integer coordinate and outputs Coordinate, Block Pos, X, Y, and Z.",
    category = "reference.points",
    order = 0
)
public class CoordinateInputNode extends BaseCustomUINode {

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_BLOCK_POS_ID = "output_block_pos";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    @NodeProperty(displayName = "X", category = "Components", order = 1, description = "X coordinate")
    private int x = 0;

    @NodeProperty(displayName = "Y", category = "Components", order = 2, description = "Y coordinate")
    private int y = 0;

    @NodeProperty(displayName = "Z", category = "Components", order = 3, description = "Z coordinate")
    private int z = 0;

    @NodeProperty(displayName = "Show Label", category = "UI Settings", order = 10,
        description = "Whether to show the current coordinate summary")
    private boolean showLabel = true;

    public CoordinateInputNode() {
        super(UUID.randomUUID(), "reference.points.point_from_coordinates");
        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "Integer coordinate", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos", "Block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z coordinate", NodeDataType.INTEGER, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Inputs an integer coordinate and outputs Coordinate, Block Pos, X, Y, and Z.";
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

            changed |= renderComponentInput("X", x, this::setX, availableWidth, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Y", y, this::setY, availableWidth, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Z", z, this::setZ, availableWidth, baseCursorX, edgeMargin);

            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private boolean renderComponentInput(String label, int currentValue, java.util.function.IntConsumer setter,
                                         float availableWidth, float baseCursorX, float edgeMargin) {
        float labelWidth = ImGui.calcTextSize(label).x;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float inputWidth = Math.max(availableWidth - labelWidth - spacing, 56.0f);

        ImGui.setCursorPosX(baseCursorX + edgeMargin);
        ImGui.text(label);
        ImGui.sameLine();
        ImGui.pushItemWidth(inputWidth);
        ImInt valueInput = new ImInt(currentValue);
        boolean changed = ImGui.inputInt("##" + label.toLowerCase(), valueInput, 1, 10);
        ImGui.popItemWidth();
        if (changed) {
            setter.accept(valueInput.get());
        }
        return changed;
    }

    private void updateOutput() {
        BlockPos blockPos = new BlockPos(x, y, z);
        outputValues.put(OUTPUT_COORDINATE_ID, blockPos);
        outputValues.put(OUTPUT_BLOCK_POS_ID, blockPos);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
        syncOutputPorts();
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        if (this.x != x) {
            this.x = x;
            updateOutput();
            markDirty();
        }
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        if (this.y != y) {
            this.y = y;
            updateOutput();
            markDirty();
        }
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        if (this.z != z) {
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("x", x);
        state.put("y", y);
        state.put("z", z);
        state.put("showLabel", showLabel);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("x") instanceof Number number) {
                this.x = number.intValue();
            }
            if (map.get("y") instanceof Number number) {
                this.y = number.intValue();
            }
            if (map.get("z") instanceof Number number) {
                this.z = number.intValue();
            }
            if (map.get("showLabel") instanceof Boolean show) {
                this.showLabel = show;
            }
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }
}
