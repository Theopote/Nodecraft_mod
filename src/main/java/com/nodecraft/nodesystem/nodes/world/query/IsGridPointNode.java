package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Checks whether a geometric point lies on the block cell-center lattice.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.query.is_grid_point",
    displayName = "Is Grid Point",
    description = "Checks whether a point lies on a block cell-center grid position without snapping",
    category = "world.query",
    order = 0
)
public class IsGridPointNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_IS_GRID_POINT_ID = "output_is_grid_point";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_NEAREST_COORDINATE_ID = "output_nearest_coordinate";
    private static final String OUTPUT_OFFSET_VECTOR_ID = "output_offset_vector";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    private double tolerance = 1.0E-6D;

    public IsGridPointNode() {
        super(UUID.randomUUID(), "world.query.is_grid_point");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to test against the block cell-center grid",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_IS_GRID_POINT_ID, "Is Grid Point",
            "True when the point lies on a block cell-center grid position", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input could be resolved to a geometric point", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NEAREST_COORDINATE_ID, "Nearest Coordinate",
            "Nearest block cell index for this point", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_OFFSET_VECTOR_ID, "Offset Vector",
            "Vector from the nearest cell center to the point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the point to the nearest cell center", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDisplayName() {
        return "Is Grid Point";
    }

    @Override
    public String getDescription() {
        return "Checks whether a point lies on a block cell-center grid position without snapping";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = PointUtils.toPointPosition(inputValues.get(INPUT_POINT_ID));
        if (!PointUtils.isFinite(point)) {
            outputValues.put(OUTPUT_IS_GRID_POINT_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_NEAREST_COORDINATE_ID, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_OFFSET_VECTOR_ID, new VectorData(0, 0, 0));
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0D);
            return;
        }

        BlockPos nearest = BlockSpace.nearestCellBlockPos(point);
        Vector3d offset = BlockSpace.offsetFromNearestCellCenter(point);
        double distance = offset.length();
        boolean isGridPoint = BlockSpace.isCellCenter(point, tolerance);

        outputValues.put(OUTPUT_IS_GRID_POINT_ID, isGridPoint);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_NEAREST_COORDINATE_ID, nearest);
        outputValues.put(OUTPUT_OFFSET_VECTOR_ID, VectorUtils.toVectorPort(offset));
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
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
