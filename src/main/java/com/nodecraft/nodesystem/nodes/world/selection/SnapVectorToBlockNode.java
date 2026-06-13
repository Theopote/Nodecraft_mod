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
        NEAREST,
        CEIL
    }

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    private SnapMode snapMode = SnapMode.NEAREST;

    public SnapVectorToBlockNode() {
        super(UUID.randomUUID(), "world.selection.snap_vector_to_block");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector",
            "Vector position to convert to block coordinates", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate",
            "Converted block coordinate", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X",
            "Snapped X coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y",
            "Snapped Y coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z",
            "Snapped Z coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input vector is valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the original vector to the snapped block coordinate", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Converts a Vector3d position into a block coordinate using floor, nearest, or ceil snapping.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object vectorObj = inputValues.get(INPUT_VECTOR_ID);
        if (!(vectorObj instanceof Vector3d vec)) {
            outputValues.put(OUTPUT_COORDINATE_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_X_ID, 0);
            outputValues.put(OUTPUT_Y_ID, 0);
            outputValues.put(OUTPUT_Z_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0D);
            return;
        }

        int x = snap(vec.x);
        int y = snap(vec.y);
        int z = snap(vec.z);
        BlockPos coordinate = new BlockPos(x, y, z);
        double dx = vec.x - coordinate.getX();
        double dy = vec.y - coordinate.getY();
        double dz = vec.z - coordinate.getZ();

        outputValues.put(OUTPUT_COORDINATE_ID, coordinate);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_DISTANCE_ID, Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public SnapMode getSnapMode() {
        return snapMode;
    }

    public void setSnapMode(SnapMode snapMode) {
        this.snapMode = snapMode == null ? SnapMode.NEAREST : snapMode;
        markDirty();
    }

    public void setSnapModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setSnapMode(SnapMode.NEAREST);
            return;
        }
        try {
            String normalized = mode.trim().toUpperCase();
            setSnapMode("ROUND".equals(normalized) ? SnapMode.NEAREST : SnapMode.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            setSnapMode(SnapMode.NEAREST);
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

    private int snap(double value) {
        return switch (snapMode) {
            case FLOOR -> (int) Math.floor(value);
            case CEIL -> (int) Math.ceil(value);
            case NEAREST -> (int) Math.round(value);
        };
    }
}
