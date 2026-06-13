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

/**
 * Explicitly converts a geometric point into a block coordinate using a chosen snap mode.
 */
@NodeInfo(
    id = "world.selection.snap_point_to_block",
    displayName = "Snap Point To Block",
    description = "Explicitly snaps a geometric point onto the block grid using floor, nearest, or ceil",
    category = "world.selection",
    order = 2
)
public class SnapPointToBlockNode extends BaseNode {

    public enum SnapMode {
        FLOOR,
        NEAREST,
        CEIL
    }

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    private SnapMode snapMode = SnapMode.NEAREST;

    public SnapPointToBlockNode() {
        super(UUID.randomUUID(), "world.selection.snap_point_to_block");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Geometric point to snap onto the block grid. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate",
            "Snapped block coordinate", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X",
            "Snapped X coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y",
            "Snapped Y coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z",
            "Snapped Z coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input could be resolved to a geometric point", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the original point to the snapped block coordinate", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDisplayName() {
        return "Snap Point To Block";
    }

    @Override
    public String getDescription() {
        return "Explicitly snaps a geometric point onto the block grid using floor, nearest, or ceil";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = WorldSelectionResolveUtils.resolveVector3d(inputValues.get(INPUT_POINT_ID));
        if (point == null) {
            outputValues.put(OUTPUT_COORDINATE_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_X_ID, 0);
            outputValues.put(OUTPUT_Y_ID, 0);
            outputValues.put(OUTPUT_Z_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0D);
            return;
        }

        int x = snap(point.x);
        int y = snap(point.y);
        int z = snap(point.z);

        BlockPos snapped = new BlockPos(x, y, z);
        double dx = point.x - snapped.getX();
        double dy = point.y - snapped.getY();
        double dz = point.z - snapped.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        outputValues.put(OUTPUT_COORDINATE_ID, snapped);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
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
            setSnapMode(SnapMode.valueOf(mode.trim().toUpperCase()));
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
