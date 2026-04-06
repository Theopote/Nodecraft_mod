package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.basic.coordinate_input",
    displayName = "坐标输入",
    description = "输入一个整数坐标，并同时输出 Coordinate / Block Pos / X / Y / Z",
    category = "inputs.basic"
)
public class CoordinateInputNode extends BaseCustomUINode {

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_BLOCK_POS_ID = "output_block_pos";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    @NodeProperty(displayName = "X", category = "分量", order = 1, description = "坐标的 X 分量")
    private int x = 0;

    @NodeProperty(displayName = "Y", category = "分量", order = 2, description = "坐标的 Y 分量")
    private int y = 0;

    @NodeProperty(displayName = "Z", category = "分量", order = 3, description = "坐标的 Z 分量")
    private int z = 0;

    @NodeProperty(displayName = "显示标签", category = "UI设置", order = 10, description = "是否显示当前坐标摘要")
    private boolean showLabel = true;

    public CoordinateInputNode() {
        super(UUID.randomUUID(), "inputs.basic.coordinate_input");
        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "整数坐标", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos", "方块坐标", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X 分量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y 分量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z 分量", NodeDataType.INTEGER, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "输入一个整数坐标，并同时输出 Coordinate / Block Pos / X / Y / Z。";
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
        height += getMediumPadding();
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
            float availableWidth = getAvailableContentWidth(width, zoom);

            l.addVerticalSpacing(getMediumPadding());

            changed |= renderComponentInput("X", x, this::setX);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Y", y, this::setY);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Z", z, this::setZ);

            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private boolean renderComponentInput(String label, int currentValue, java.util.function.IntConsumer setter) {
        ImGui.text(label);
        ImGui.sameLine();
        ImInt valueInput = new ImInt(currentValue);
        boolean changed = ImGui.inputInt("##" + label.toLowerCase(), valueInput, 1, 10);
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
