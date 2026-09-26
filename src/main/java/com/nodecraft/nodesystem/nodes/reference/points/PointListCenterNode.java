package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_list_center",
    displayName = "Point List Center",
    description = "Calculates the average geometric center of a point list",
    category = "reference.points",
    order = 12
)
public class PointListCenterNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_CENTER_POINT_ID = "output_center_point";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PointListCenterNode() {
        super(UUID.randomUUID(), "reference.points.point_list_center");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Geometric points to average",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_POINT_ID, "Center Point",
            "Average geometric center of the input points", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of points in the input list", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input point list is valid and non-empty", NodeDataType.BOOLEAN, this));
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
        List<Vector3d> points = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (points == null) {
            writeInvalid();
            return;
        }

        Vector3d sum = new Vector3d();
        for (Vector3d point : points) {
            sum.add(point);
        }
        Vector3d center = sum.div((double) points.size());

        outputValues.put(OUTPUT_CENTER_POINT_ID, new PointData(center));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CENTER_POINT_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
