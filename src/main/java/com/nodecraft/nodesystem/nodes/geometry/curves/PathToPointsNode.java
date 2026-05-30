package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.divide_curve_to_points",
    displayName = "Path To Points",
    description = "Extracts an ordered point list from a line, polyline, or curve",
    category = "geometry.curves",
    order = 1
)
public class PathToPointsNode extends BaseNode {

    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PathToPointsNode() {
        super(UUID.randomUUID(), "geometry.curves.divide_curve_to_points");

        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Line to convert into an ordered point list",
            NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Polyline to convert into an ordered point list",
            NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve",
            "Curve to sample into an ordered point list",
            NodeDataType.CURVE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Ordered point list extracted from the input path", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of points extracted from the input path", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when one of the path inputs was valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<PointData> points = new ArrayList<>();

        Object lineObj = inputValues.get(INPUT_LINE_ID);
        Object polylineObj = inputValues.get(INPUT_POLYLINE_ID);
        Object curveObj = inputValues.get(INPUT_CURVE_ID);

        if (lineObj instanceof LineData line) {
            points.add(fromVec3d(line.getStart()));
            points.add(fromVec3d(line.getEnd()));
        } else if (polylineObj instanceof PolylineData polyline) {
            for (Vec3d point : polyline.getPoints()) {
                points.add(fromVec3d(point));
            }
        } else if (curveObj instanceof Curve curve) {
            for (Vec3d point : curve.getSamplePoints()) {
                points.add(fromVec3d(point));
            }
        }

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

    private PointData fromVec3d(Vec3d point) {
        return new PointData(point.x, point.y, point.z);
    }
}
