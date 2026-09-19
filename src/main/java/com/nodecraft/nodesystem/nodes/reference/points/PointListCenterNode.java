package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_list_center",
    displayName = "Point List Center",
    description = "Calculates the average geometric center of a point list",
    category = "reference.points",
    order = 8
)
public class PointListCenterNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_CENTER_POINT_ID = "output_center_point";
    private static final String OUTPUT_CENTER_VECTOR_ID = "output_center_vector";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PointListCenterNode() {
        super(UUID.randomUUID(), "reference.points.point_list_center");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Collection of Point, Vector, Position, or Block Coordinate values to average",
            NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_POINT_ID, "Center Point",
            "Average geometric center of the valid input points", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_VECTOR_ID, "Center Vector",
            "Average center as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of valid points used to compute the center", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when at least one valid point was resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Point List Center";
    }

    @Override
    public String getDescription() {
        return "Calculates the average geometric center of a point list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_POINTS_ID);
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            writeInvalid();
            return;
        }

        Vector3d sum = new Vector3d();
        int count = 0;

        for (Object entry : collection) {
            Vector3d point = PointUtils.resolvePoint(entry);
            if (!PointUtils.isFinite(point)) {
                continue;
            }
            sum.add(point);
            count++;
        }

        if (count == 0) {
            writeInvalid();
            return;
        }

        Vector3d center = sum.div((double) count);

        outputValues.put(OUTPUT_CENTER_POINT_ID, new PointData(center));
        outputValues.put(OUTPUT_CENTER_VECTOR_ID, center);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CENTER_POINT_ID, null);
        outputValues.put(OUTPUT_CENTER_VECTOR_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
