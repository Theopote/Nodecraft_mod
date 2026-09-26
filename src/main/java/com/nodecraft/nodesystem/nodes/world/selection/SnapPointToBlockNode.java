package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
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
 * Explicitly converts a geometric point into a block cell index using cell-center snap modes.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.selection.snap_point_to_block",
    displayName = "Snap Point To Block",
    description = "Snaps a point to a block cell using containing-cell or nearest-center modes (cell-center lattice)",
    category = "world.selection",
    order = 2
)
public class SnapPointToBlockNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_BLOCK_POS_ID = "output_coordinate";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    @NodeProperty(displayName = "Snap Mode", category = "Snap", order = 1)
    private WorldSelectionResolveUtils.SnapMode snapMode = WorldSelectionResolveUtils.SnapMode.NEAREST_CENTER;

    public SnapPointToBlockNode() {
        super(UUID.randomUUID(), "world.selection.snap_point_to_block");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Geometric point to snap onto the block cell lattice",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos",
            "Snapped block cell index", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input point is valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when snap fails", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the original point to the snapped cell center",
            NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Snaps a point to a block cell using containing-cell or nearest-center modes (cell-center lattice)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = WorldSelectionResolveUtils.requirePoint(inputValues.get(INPUT_POINT_ID));
        if (point == null) {
            outputValues.put(OUTPUT_BLOCK_POS_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Point input must be a finite POINT.");
            outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
            return;
        }

        BlockPos snapped = WorldSelectionResolveUtils.snapPointToBlock(point, snapMode);
        double distance = WorldSelectionResolveUtils.distanceToSnappedCenter(point, snapped);

        outputValues.put(OUTPUT_BLOCK_POS_ID, snapped);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
    }

    public WorldSelectionResolveUtils.SnapMode getSnapMode() {
        return snapMode == null ? WorldSelectionResolveUtils.SnapMode.NEAREST_CENTER : snapMode;
    }

    public void setSnapMode(WorldSelectionResolveUtils.SnapMode snapMode) {
        this.snapMode = snapMode == null ? WorldSelectionResolveUtils.SnapMode.NEAREST_CENTER : snapMode;
        markDirty();
    }

    /** Accepts legacy FLOOR/NEAREST/CEIL and V62 CONTAINING_CELL/NEAREST_CENTER. */
    public void setSnapModeString(String mode) {
        setSnapMode(WorldSelectionResolveUtils.SnapMode.fromLegacyOrName(mode));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("snapMode", getSnapMode().name());
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
