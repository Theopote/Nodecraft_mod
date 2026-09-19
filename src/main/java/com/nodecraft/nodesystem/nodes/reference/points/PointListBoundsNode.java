package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_list_bounds",
    displayName = "Point List Bounds",
    description = "Calculates an axis-aligned bounding box from a list of geometric points",
    category = "reference.points",
    order = 9
)
public class PointListBoundsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_REGION_ID = "output_region";
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
            "Collection of Point, Vector, Position, or Block Coordinate values to bound",
            NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box",
            "Axis-aligned geometric bounding box", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Outward snapped block region covering all points", NodeDataType.REGION, this));
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
            "Number of valid points used to build the bounds", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when at least one valid point was resolved", NodeDataType.BOOLEAN, this));
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
        Object value = inputValues.get(INPUT_POINTS_ID);
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            writeInvalid();
            return;
        }

        Vector3d min = null;
        Vector3d max = null;
        int count = 0;

        for (Object entry : collection) {
            Vector3d point = PointUtils.resolvePoint(entry);
            if (!PointUtils.isFinite(point)) {
                continue;
            }

            if (min == null) {
                min = new Vector3d(point);
                max = new Vector3d(point);
            } else {
                min.min(point);
                max.max(point);
            }
            count++;
        }

        if (min == null || max == null || count == 0) {
            writeInvalid();
            return;
        }

        Vector3d center = new Vector3d(min).add(max).mul(0.5);
        double sizeX = max.x - min.x;
        double sizeY = max.y - min.y;
        double sizeZ = max.z - min.z;

        BoundingBoxData boundingBox = new BoundingBoxData(min, max);
        RegionData region = new RegionData(
            new BlockPos((int) Math.floor(min.x), (int) Math.floor(min.y), (int) Math.floor(min.z)),
            new BlockPos((int) Math.ceil(max.x), (int) Math.ceil(max.y), (int) Math.ceil(max.z))
        );

        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_MIN_POINT_ID, new PointData(min));
        outputValues.put(OUTPUT_MAX_POINT_ID, new PointData(max));
        outputValues.put(OUTPUT_CENTER_POINT_ID, new PointData(center));
        outputValues.put(OUTPUT_SIZE_X_ID, sizeX);
        outputValues.put(OUTPUT_SIZE_Y_ID, sizeY);
        outputValues.put(OUTPUT_SIZE_Z_ID, sizeZ);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_REGION_ID, null);
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
