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

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.path_to_points",
    displayName = "Path To Points",
    description = "Extracts an ordered point list from a line, polyline, or curve",
    category = "geometry.curves",
    order = 1
)
public class PathToPointsNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PathToPointsNode() {
        super(UUID.randomUUID(), "geometry.curves.path_to_points");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to convert into an ordered point list (line, polyline, or curve)",
            NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Ordered point list extracted from the input path", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of points extracted from the input path", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the path input was valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> vertices = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        var points = vertices == null ? List.<com.nodecraft.nodesystem.datatypes.PointData>of()
            : SpatialValueResolver.toPointDataList(vertices);

        boolean valid = !points.isEmpty();
        outputValues.put(OUTPUT_POINTS_ID, points);
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    @Override
    public Object getNodeState() {
        return new HashMap<String, Object>();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }
}
