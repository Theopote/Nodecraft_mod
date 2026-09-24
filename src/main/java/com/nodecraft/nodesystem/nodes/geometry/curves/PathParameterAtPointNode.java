package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.path_parameter_at_point",
    displayName = "Path Parameter At Point",
    description = "Returns the normalized path parameter for the closest point to a query point.",
    category = "geometry.curves",
    order = 16
)
public class PathParameterAtPointNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_PARAMETER_ID = "output_parameter";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PathParameterAtPointNode() {
        super(UUID.randomUUID(), "geometry.curves.path_parameter_at_point");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to evaluate (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Query point projected onto the path", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_PARAMETER_ID, "Parameter",
            "Normalized parameter in [0..1] at the closest point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from query point to the path", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when parameter was computed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d query = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_POINT_ID));
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        PathUtils.ClosestPointResult result = PathUtils.closestPointOnPath(verts, query);
        if (result == null) {
            outputValues.put(OUTPUT_PARAMETER_ID, Double.NaN);
            outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        outputValues.put(OUTPUT_PARAMETER_ID, result.parameter());
        outputValues.put(OUTPUT_DISTANCE_ID, result.distance());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
