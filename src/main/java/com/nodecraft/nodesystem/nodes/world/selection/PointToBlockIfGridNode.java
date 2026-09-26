package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Emits a block cell only when the point already lies on the cell-center lattice
 * (same contract as {@code world.query.is_grid_point}).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.selection.point_to_block_if_grid",
    displayName = "Point To Block If Grid",
    description = "Outputs a block cell only when the point lies on the cell-center lattice within tolerance",
    category = "world.selection",
    order = 4
)
public class PointToBlockIfGridNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_IS_GRID_POINT_ID = "output_is_grid_point";
    private static final String OUTPUT_NEAREST_COORDINATE_ID = "output_nearest_coordinate";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_OFFSET_VECTOR_ID = "output_offset_vector";

    private double tolerance = 1.0E-6D;

    public PointToBlockIfGridNode() {
        super(UUID.randomUUID(), "world.selection.point_to_block_if_grid");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to test against the block cell-center lattice",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate",
            "Block cell index only when the point is on-grid", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input could be resolved to a geometric point", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when the point input is invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_GRID_POINT_ID, "Is Grid Point",
            "True when the point lies on a cell-center lattice position", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NEAREST_COORDINATE_ID, "Nearest Coordinate",
            "Nearest block cell index for this point", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the point to the nearest cell center", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_OFFSET_VECTOR_ID, "Offset Vector",
            "Vector from the nearest cell center to the point", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return "Outputs a block cell only when the point lies on the cell-center lattice within tolerance";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = WorldSelectionResolveUtils.requirePoint(inputValues.get(INPUT_POINT_ID));
        if (point == null) {
            outputValues.put(OUTPUT_COORDINATE_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Point input must be a finite POINT.");
            outputValues.put(OUTPUT_IS_GRID_POINT_ID, false);
            outputValues.put(OUTPUT_NEAREST_COORDINATE_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_OFFSET_VECTOR_ID, new VectorData(0, 0, 0));
            return;
        }

        BlockPos nearest = BlockSpace.nearestCellBlockPos(point);
        Vector3d offset = BlockSpace.offsetFromNearestCellCenter(point);
        double distance = offset.length();
        boolean isGridPoint = BlockSpace.isCellCenter(point, tolerance);

        outputValues.put(OUTPUT_COORDINATE_ID, isGridPoint ? nearest : null);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_IS_GRID_POINT_ID, isGridPoint);
        outputValues.put(OUTPUT_NEAREST_COORDINATE_ID, nearest);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_OFFSET_VECTOR_ID, VectorUtils.toVectorPort(offset));
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = Math.max(0.0D, tolerance);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("tolerance", tolerance);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object toleranceObj = stateMap.get("tolerance");
            if (toleranceObj instanceof Number number) {
                setTolerance(number.doubleValue());
            }
        }
    }
}
