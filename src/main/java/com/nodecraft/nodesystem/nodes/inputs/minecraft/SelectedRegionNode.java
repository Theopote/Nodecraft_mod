package com.nodecraft.nodesystem.nodes.inputs.minecraft;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import com.nodecraft.nodesystem.util.Vector3;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.minecraft.selected_region",
    displayName = "Selected Region",
    description = "Gets the player's selected region defined by two corner points.",
    category = "inputs.minecraft"
)
public class SelectedRegionNode extends BaseCustomUINode {

    @NodeProperty(
        displayName = "Auto Update",
        category = "Selection",
        order = 1,
        description = "Whether the node should refresh the selected region from the player automatically."
    )
    private boolean autoUpdate = true;

    private static final String OUTPUT_POS1_ID = "output_pos1";
    private static final String OUTPUT_POS2_ID = "output_pos2";
    private static final String OUTPUT_POS1_X_ID = "output_pos1_x";
    private static final String OUTPUT_POS1_Y_ID = "output_pos1_y";
    private static final String OUTPUT_POS1_Z_ID = "output_pos1_z";
    private static final String OUTPUT_POS2_X_ID = "output_pos2_x";
    private static final String OUTPUT_POS2_Y_ID = "output_pos2_y";
    private static final String OUTPUT_POS2_Z_ID = "output_pos2_z";
    private static final String OUTPUT_MIN_POS_ID = "output_min_pos";
    private static final String OUTPUT_MAX_POS_ID = "output_max_pos";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_HAS_SELECTION_ID = "output_has_selection";

    private volatile Vector3 pos1;
    private volatile Vector3 pos2;

    public SelectedRegionNode() {
        super(UUID.randomUUID(), "inputs.minecraft.selected_region");

        addOutputPort(new BasePort(OUTPUT_POS1_ID, "Position 1", "The first corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_POS2_ID, "Position 2", "The second corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_POS1_X_ID, "Pos1 X", "X coordinate of position 1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS1_Y_ID, "Pos1 Y", "Y coordinate of position 1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS1_Z_ID, "Pos1 Z", "Z coordinate of position 1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS2_X_ID, "Pos2 X", "X coordinate of position 2", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS2_Y_ID, "Pos2 Y", "Y coordinate of position 2", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS2_Z_ID, "Pos2 Z", "Z coordinate of position 2", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MIN_POS_ID, "Min Position", "The minimum corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAX_POS_ID, "Max Position", "The maximum corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X", "Width of the selection on X", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y", "Height of the selection on Y", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z", "Depth of the selection on Z", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Total selection volume in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HAS_SELECTION_ID, "Has Selection", "Whether a valid selection exists", NodeDataType.BOOLEAN, this));

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return "Gets the player's selected region defined by two corner points.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            if (pos1 == null || pos2 == null) {
                resetOutputs();
            } else {
                updateOutputsFromPositions();
            }
            return;
        }

        if (autoUpdate) {
            PlayerAccessor playerAccessor = context.getPlayerAccessor();
            if (playerAccessor == null) {
                if (pos1 == null || pos2 == null) {
                    resetOutputs();
                }
                return;
            }
            fetchSelectionFromPlayer(playerAccessor);
        }

        updateOutputsFromPositions();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getSmallPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float buttonPadding = 20.0f;
        float labelWidth = ImGui.calcTextSize("Clear Selection").x;
        return Math.max(144.0f, labelWidth + buttonPadding);
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;
            boolean hasSelection = pos1 != null && pos2 != null;
            float edgeMargin = layout.toPixels(getSmallPadding());
            float buttonWidth = Math.max(0.0f, layout.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            layout.addVerticalSpacing(getSmallPadding());

            if (hasSelection) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF4444AA);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF5555CC);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF3333AA);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Clear Selection##clear", buttonWidth, 0)) {
                    clearSelection();
                    changed = true;
                }
                ImGui.popStyleColor(3);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF333333);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF333333);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF333333);
                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF666666);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.button("Clear Selection##clearDisabled", buttonWidth, 0);
                ImGui.popStyleColor(4);
            }

            layout.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private void fetchSelectionFromPlayer(PlayerAccessor playerAccessor) {
        Vector3 playerPos = playerAccessor.getPlayerPosition();
        pos1 = new Vector3(playerPos.getX() - 2, playerPos.getY() - 2, playerPos.getZ() - 2);
        pos2 = new Vector3(playerPos.getX() + 2, playerPos.getY() + 2, playerPos.getZ() + 2);
    }

    public void setPos1(int x, int y, int z) {
        pos1 = new Vector3(x, y, z);
        if (pos2 != null) {
            updateOutputsFromPositions();
        } else {
            outputValues.put(OUTPUT_POS1_ID, pos1);
            outputValues.put(OUTPUT_POS1_X_ID, (int) pos1.getX());
            outputValues.put(OUTPUT_POS1_Y_ID, (int) pos1.getY());
            outputValues.put(OUTPUT_POS1_Z_ID, (int) pos1.getZ());
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
        }
        invalidateCache();
        markDirty();
    }

    public void setPos2(int x, int y, int z) {
        pos2 = new Vector3(x, y, z);
        if (pos1 != null) {
            updateOutputsFromPositions();
        } else {
            outputValues.put(OUTPUT_POS2_ID, pos2);
            outputValues.put(OUTPUT_POS2_X_ID, (int) pos2.getX());
            outputValues.put(OUTPUT_POS2_Y_ID, (int) pos2.getY());
            outputValues.put(OUTPUT_POS2_Z_ID, (int) pos2.getZ());
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
        }
        invalidateCache();
        markDirty();
    }

    public void clearSelection() {
        pos1 = null;
        pos2 = null;
        resetOutputs();
        invalidateCache();
        markDirty();
    }

    private void updateOutputsFromPositions() {
        if (pos1 == null || pos2 == null) {
            resetOutputs();
            return;
        }

        outputValues.put(OUTPUT_POS1_ID, pos1);
        outputValues.put(OUTPUT_POS1_X_ID, (int) pos1.getX());
        outputValues.put(OUTPUT_POS1_Y_ID, (int) pos1.getY());
        outputValues.put(OUTPUT_POS1_Z_ID, (int) pos1.getZ());
        outputValues.put(OUTPUT_POS2_ID, pos2);
        outputValues.put(OUTPUT_POS2_X_ID, (int) pos2.getX());
        outputValues.put(OUTPUT_POS2_Y_ID, (int) pos2.getY());
        outputValues.put(OUTPUT_POS2_Z_ID, (int) pos2.getZ());

        float minX = Math.min(pos1.getX(), pos2.getX());
        float minY = Math.min(pos1.getY(), pos2.getY());
        float minZ = Math.min(pos1.getZ(), pos2.getZ());
        float maxX = Math.max(pos1.getX(), pos2.getX());
        float maxY = Math.max(pos1.getY(), pos2.getY());
        float maxZ = Math.max(pos1.getZ(), pos2.getZ());

        outputValues.put(OUTPUT_MIN_POS_ID, new Vector3(minX, minY, minZ));
        outputValues.put(OUTPUT_MAX_POS_ID, new Vector3(maxX, maxY, maxZ));

        int sizeX = (int) (maxX - minX) + 1;
        int sizeY = (int) (maxY - minY) + 1;
        int sizeZ = (int) (maxZ - minZ) + 1;

        outputValues.put(OUTPUT_SIZE_X_ID, sizeX);
        outputValues.put(OUTPUT_SIZE_Y_ID, sizeY);
        outputValues.put(OUTPUT_SIZE_Z_ID, sizeZ);
        outputValues.put(OUTPUT_VOLUME_ID, sizeX * sizeY * sizeZ);
        outputValues.put(OUTPUT_HAS_SELECTION_ID, true);
    }

    private void resetOutputs() {
        Vector3 zeroVec = new Vector3(0, 0, 0);
        outputValues.put(OUTPUT_POS1_ID, zeroVec);
        outputValues.put(OUTPUT_POS2_ID, zeroVec);
        outputValues.put(OUTPUT_POS1_X_ID, 0);
        outputValues.put(OUTPUT_POS1_Y_ID, 0);
        outputValues.put(OUTPUT_POS1_Z_ID, 0);
        outputValues.put(OUTPUT_POS2_X_ID, 0);
        outputValues.put(OUTPUT_POS2_Y_ID, 0);
        outputValues.put(OUTPUT_POS2_Z_ID, 0);
        outputValues.put(OUTPUT_MIN_POS_ID, zeroVec);
        outputValues.put(OUTPUT_MAX_POS_ID, zeroVec);
        outputValues.put(OUTPUT_SIZE_X_ID, 0);
        outputValues.put(OUTPUT_SIZE_Y_ID, 0);
        outputValues.put(OUTPUT_SIZE_Z_ID, 0);
        outputValues.put(OUTPUT_VOLUME_ID, 0);
        outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
    }

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        if (this.autoUpdate != autoUpdate) {
            this.autoUpdate = autoUpdate;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("autoUpdate", isAutoUpdate());
        if (pos1 != null) {
            Map<String, Float> pos1Map = new HashMap<>();
            pos1Map.put("x", pos1.getX());
            pos1Map.put("y", pos1.getY());
            pos1Map.put("z", pos1.getZ());
            state.put("pos1", pos1Map);
        }
        if (pos2 != null) {
            Map<String, Float> pos2Map = new HashMap<>();
            pos2Map.put("x", pos2.getX());
            pos2Map.put("y", pos2.getY());
            pos2Map.put("z", pos2.getZ());
            state.put("pos2", pos2Map);
        }
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.containsKey("autoUpdate")) {
                Object value = map.get("autoUpdate");
                if (value instanceof Boolean bool) {
                    setAutoUpdate(bool);
                }
            }
            if (map.containsKey("pos1")) {
                Object pos = map.get("pos1");
                if (pos instanceof Map<?, ?> posMap
                    && posMap.get("x") instanceof Number x
                    && posMap.get("y") instanceof Number y
                    && posMap.get("z") instanceof Number z) {
                    pos1 = new Vector3(x.floatValue(), y.floatValue(), z.floatValue());
                }
            } else {
                pos1 = null;
            }
            if (map.containsKey("pos2")) {
                Object pos = map.get("pos2");
                if (pos instanceof Map<?, ?> posMap
                    && posMap.get("x") instanceof Number x
                    && posMap.get("y") instanceof Number y
                    && posMap.get("z") instanceof Number z) {
                    pos2 = new Vector3(x.floatValue(), y.floatValue(), z.floatValue());
                }
            } else {
                pos2 = null;
            }

            if (pos1 != null && pos2 != null) {
                updateOutputsFromPositions();
            } else {
                resetOutputs();
            }
        }
    }
}
