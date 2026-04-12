package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.selection.snap_vector_to_block",
    displayName = "Snap Vector To Block",
    description = "Converts a Vector3d position into a block coordinate using floor, round, or ceil snapping.",
    category = "world.selection",
    order = 3
)
public class SnapVectorToBlockNode extends BaseNode {

    public enum SnapMode {
        FLOOR,
        ROUND,
        CEIL
    }

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";

    private SnapMode snapMode = SnapMode.FLOOR;

    public SnapVectorToBlockNode() {
        super(UUID.randomUUID(), "world.selection.snap_vector_to_block");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector",
            "Vector position to convert to block coordinates", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate",
            "Converted block coordinate", NodeDataType.BLOCK_POS, this));
    }

    @Override
    public String getDescription() {
        return "Converts a Vector3d position into a block coordinate using floor, round, or ceil snapping.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object vectorObj = inputValues.get(INPUT_VECTOR_ID);
        BlockPos coordinate = BlockPos.ORIGIN;

        if (vectorObj instanceof Vector3d vec) {
            int x;
            int y;
            int z;

            switch (snapMode) {
                case FLOOR -> {
                    x = (int) Math.floor(vec.x);
                    y = (int) Math.floor(vec.y);
                    z = (int) Math.floor(vec.z);
                }
                case ROUND -> {
                    x = (int) Math.round(vec.x);
                    y = (int) Math.round(vec.y);
                    z = (int) Math.round(vec.z);
                }
                case CEIL -> {
                    x = (int) Math.ceil(vec.x);
                    y = (int) Math.ceil(vec.y);
                    z = (int) Math.ceil(vec.z);
                }
                default -> throw new IllegalStateException("Unexpected snap mode: " + snapMode);
            }

            coordinate = new BlockPos(x, y, z);
        }

        outputValues.put(OUTPUT_COORDINATE_ID, coordinate);
    }

    public SnapMode getSnapMode() {
        return snapMode;
    }

    public void setSnapMode(SnapMode snapMode) {
        this.snapMode = snapMode == null ? SnapMode.FLOOR : snapMode;
        markDirty();
    }

    public void setSnapModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setSnapMode(SnapMode.FLOOR);
            return;
        }
        try {
            setSnapMode(SnapMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setSnapMode(SnapMode.FLOOR);
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("snapMode", snapMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object mode = stateMap.get("snapMode");
            if (mode instanceof String modeString) {
                setSnapModeString(modeString);
            }
        }
    }
}
