package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.closest_point_on_path",
    displayName = "Closest Point On Path",
    description = "Finds the closest point on a path to a query point.",
    category = "geometry.curves",
    order = 15
)
public class ClosestPointOnPathNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_PARAMETER_ID = "output_parameter";
    private static final String OUTPUT_ARC_LENGTH_ID = "output_arc_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ClosestPointOnPathNode() {
        super(UUID.randomUUID(), "geometry.curves.closest_point_on_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to search (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Query point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Closest Point",
            "Closest point on the path", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from query point to closest point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PARAMETER_ID, "Parameter",
            "Normalized path parameter at closest point in [0..1]", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ARC_LENGTH_ID, "Arc Length",
            "Arc length from path start to closest point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when closest point was found", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d query = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_POINT_ID));
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        PathUtils.ClosestPointResult result = PathUtils.closestPointOnPath(verts, query);
        if (result == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POINT_ID, new PointData(result.point()));
        outputValues.put(OUTPUT_DISTANCE_ID, result.distance());
        outputValues.put(OUTPUT_PARAMETER_ID, result.parameter());
        outputValues.put(OUTPUT_ARC_LENGTH_ID, result.arcLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
        outputValues.put(OUTPUT_PARAMETER_ID, Double.NaN);
        outputValues.put(OUTPUT_ARC_LENGTH_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
