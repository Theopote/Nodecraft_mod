package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_list_bounds",
    displayName = "Point List Bounds",
    description = "Calculates an axis-aligned bounding box from a list of geometric points",
    category = "reference.points",
    order = 13
)
public class PointListBoundsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_MIN_POINT_ID = "output_min_point";
    private static final String OUTPUT_MAX_POINT_ID = "output_max_point";
    private static final String OUTPUT_CENTER_POINT_ID = "output_center_point";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PointListBoundsNode() {
        super(UUID.randomUUID(), "reference.points.point_list_bounds");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Geometric points to bound",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box",
            "Axis-aligned geometric bounding box", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_MIN_POINT_ID, "Min Point",
            "Minimum geometric corner of the bounds", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_MAX_POINT_ID, "Max Point",
            "Maximum geometric corner of the bounds", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_POINT_ID, "Center Point",
            "Geometric center of the bounds", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X",
            "Geometric size on the X axis", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y",
            "Geometric size on the Y axis", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z",
            "Geometric size on the Z axis", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of points in the input list", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input point list is valid and non-empty", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Point List Bounds";
    }

    @Override
    public String getDescription() {
        return "Calculates an axis-aligned bounding box from a list of geometric points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (points == null) {
            writeInvalid();
            return;
        }

        Vector3d min = new Vector3d(points.getFirst());
        Vector3d max = new Vector3d(points.getFirst());
        for (int i = 1; i < points.size(); i++) {
            Vector3d point = points.get(i);
            min.min(point);
            max.max(point);
        }

        Vector3d center = new Vector3d(min).add(max).mul(0.5);
        double sizeX = max.x - min.x;
        double sizeY = max.y - min.y;
        double sizeZ = max.z - min.z;

        outputValues.put(OUTPUT_BOUNDING_BOX_ID, new BoundingBoxData(min, max));
        outputValues.put(OUTPUT_MIN_POINT_ID, new PointData(min));
        outputValues.put(OUTPUT_MAX_POINT_ID, new PointData(max));
        outputValues.put(OUTPUT_CENTER_POINT_ID, new PointData(center));
        outputValues.put(OUTPUT_SIZE_X_ID, sizeX);
        outputValues.put(OUTPUT_SIZE_Y_ID, sizeY);
        outputValues.put(OUTPUT_SIZE_Z_ID, sizeZ);
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_MIN_POINT_ID, null);
        outputValues.put(OUTPUT_MAX_POINT_ID, null);
        outputValues.put(OUTPUT_CENTER_POINT_ID, null);
        outputValues.put(OUTPUT_SIZE_X_ID, Double.NaN);
        outputValues.put(OUTPUT_SIZE_Y_ID, Double.NaN);
        outputValues.put(OUTPUT_SIZE_Z_ID, Double.NaN);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
