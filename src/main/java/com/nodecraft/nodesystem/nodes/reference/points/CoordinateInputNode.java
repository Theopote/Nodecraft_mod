package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import imgui.ImGui;
import imgui.type.ImInt;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Integer block-grid position input with optional X/Y/Z port overrides.
 * <p>
 * Player-facing spatial language: this is a <strong>Block Position</strong> source,
 * not a continuous geometric Point (see {@code docs/nodecraft-v1-node-language.md}).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.block_position",
    displayName = "Block Position Input",
    description = "Inputs an integer block position from panel values or optional X/Y/Z ports.",
    category = "reference.points",
    order = 0
)
public class CoordinateInputNode extends BaseCustomUINode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    private static final String OUTPUT_BLOCK_POS_ID = "output_block_pos";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "X", category = "Components", order = 1, description = "X block coordinate")
    private int x = 0;

    @NodeProperty(displayName = "Y", category = "Components", order = 2, description = "Y block coordinate")
    private int y = 0;

    @NodeProperty(displayName = "Z", category = "Components", order = 3, description = "Z block coordinate")
    private int z = 0;

    public CoordinateInputNode() {
        super(UUID.randomUUID(), "reference.points.block_position");
        addInputPort(new BasePort(INPUT_X_ID, "X", "Optional X override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Optional Y override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Optional Z override", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos", "Block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when all components resolved to valid integers", NodeDataType.BOOLEAN, this));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Inputs an integer block position from panel values or optional X/Y/Z ports.";
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

            changed |= renderComponentInput("X", INPUT_X_ID, getResolvedX(), this::setX,
                availableWidth, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Y", INPUT_Y_ID, getResolvedY(), this::setY,
                availableWidth, baseCursorX, edgeMargin);
            l.addVerticalSpacing(getSmallPadding());
            changed |= renderComponentInput("Z", INPUT_Z_ID, getResolvedZ(), this::setZ,
                availableWidth, baseCursorX, edgeMargin);

            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private boolean renderComponentInput(String label, String inputPortId, int currentValue, IntConsumer setter,
                                         float availableWidth, float baseCursorX, float edgeMargin) {
        float labelWidth = ImGui.calcTextSize(label).x;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float inputWidth = Math.max(availableWidth - labelWidth - spacing, 56.0f);

        ImGui.setCursorPosX(baseCursorX + edgeMargin);
        ImGui.text(label);
        ImGui.sameLine();
        ImGui.pushItemWidth(inputWidth);
        ImInt valueInput = new ImInt(currentValue);
        boolean hasOverride = isInputConnected(inputPortId);
        if (hasOverride) {
            ImGui.beginDisabled();
        }
        boolean changed = ImGui.inputInt("##" + label.toLowerCase(), valueInput, 1, 10);
        if (hasOverride) {
            ImGui.endDisabled();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(label + " is driven by an input connection.");
            }
        }
        ImGui.popItemWidth();
        if (changed && !hasOverride) {
            setter.accept(valueInput.get());
        }
        return changed;
    }

    private void updateOutput() {
        Integer resolvedX = resolveComponent(INPUT_X_ID, x);
        Integer resolvedY = resolveComponent(INPUT_Y_ID, y);
        Integer resolvedZ = resolveComponent(INPUT_Z_ID, z);

        if (resolvedX == null || resolvedY == null || resolvedZ == null) {
            outputValues.put(OUTPUT_BLOCK_POS_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
        } else {
            outputValues.put(OUTPUT_BLOCK_POS_ID, new BlockPos(resolvedX, resolvedY, resolvedZ));
            outputValues.put(OUTPUT_VALID_ID, true);
        }
        syncOutputPorts();
    }

    private int getResolvedX() {
        Integer resolved = resolveComponent(INPUT_X_ID, x);
        return resolved != null ? resolved : x;
    }

    private int getResolvedY() {
        Integer resolved = resolveComponent(INPUT_Y_ID, y);
        return resolved != null ? resolved : y;
    }

    private int getResolvedZ() {
        Integer resolved = resolveComponent(INPUT_Z_ID, z);
        return resolved != null ? resolved : z;
    }

    private @Nullable Integer resolveComponent(String inputPortId, int fallback) {
        if (isInputConnected(inputPortId)) {
            return StrictIntegerUtils.requireExactInteger(inputValues.get(inputPortId));
        }
        return fallback;
    }

    private boolean isInputConnected(String inputPortId) {
        return inputPorts.stream()
            .anyMatch(port -> inputPortId.equals(port.getId()) && port.isConnected());
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("x", x);
        state.put("y", y);
        state.put("z", z);
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
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }
}
