package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.UUID;

/**
 * Finds the closest point in a point collection to a reference point.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.closest_point",
    displayName = "Closest Point",
    description = "Finds the closest point in a point collection to a reference point.",
    category = "reference.points",
    order = 7
)
public class ClosestPointNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";

    private static final String OUTPUT_CLOSEST_POINT_ID = "output_closest_point";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_POINT_DATA_ID = "output_point_data";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ClosestPointNode() {
        super(UUID.randomUUID(), "reference.points.closest_point");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Reference geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Points",
            "Candidate geometric points",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_CLOSEST_POINT_ID, "Closest Block",
            "Closest point snapped to a block coordinate", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the reference point to the closest point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index",
            "Index of the closest valid point in the input collection", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINT_DATA_ID, "Closest Point",
            "Closest point as PointData", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Closest Vector",
            "Closest point as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a closest point was found", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Closest Point";
    }

    @Override
    public String getDescription() {
        return "Finds the closest point in a point collection to a reference point.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d reference = PointUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (!PointUtils.isFinite(reference)) {
            writeInvalid();
            return;
        }

        Object candidatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Iterable<?> candidates = toIterable(candidatesObj);
        if (candidates == null) {
            writeInvalid();
            return;
        }

        double minDistanceSquared = Double.MAX_VALUE;
        Vector3d closest = null;
        int closestIndex = -1;
        int index = 0;

        for (Object candidateObj : candidates) {
            Vector3d candidate = PointUtils.resolvePoint(candidateObj);
            if (PointUtils.isFinite(candidate)) {
                double distanceSquared = PointUtils.distanceSquared(reference, candidate);
                if (distanceSquared < minDistanceSquared) {
                    minDistanceSquared = distanceSquared;
                    closest = candidate;
                    closestIndex = index;
                }
            }
            index++;
        }

        if (closest == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_CLOSEST_POINT_ID, BlockPos.ofFloored(closest.x, closest.y, closest.z));
        outputValues.put(OUTPUT_DISTANCE_ID, Math.sqrt(minDistanceSquared));
        outputValues.put(OUTPUT_INDEX_ID, closestIndex);
        outputValues.put(OUTPUT_POINT_DATA_ID, new PointData(closest));
        outputValues.put(OUTPUT_VECTOR_ID, new Vector3d(closest));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Iterable<?> toIterable(Object value) {
        if (value instanceof BlockPosList blockPosList) {
            return blockPosList;
        }
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return null;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CLOSEST_POINT_ID, null);
        outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
        outputValues.put(OUTPUT_INDEX_ID, -1);
        outputValues.put(OUTPUT_POINT_DATA_ID, null);
        outputValues.put(OUTPUT_VECTOR_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
