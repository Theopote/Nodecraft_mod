package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.points_to_path",
    displayName = "Points To Path",
    description = "Builds a line or polyline from an ordered point list",
    category = "geometry.curves",
    order = 0
)
public class PointsToPathNode extends AbstractCurveNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_LINE_ID = "output_line";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private boolean closePath = false;

    public PointsToPathNode() {
        super(UUID.randomUUID(), "geometry.curves.points_to_path");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Ordered point list",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Primary path output (line for 2 points, polyline for 3+)",
            NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_LINE_ID, "Line",
            "Line output when the path contains exactly 2 points", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "Polyline output when the path contains 2 or more points", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of valid points used to build the path", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when at least 2 valid points were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> resolved = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        List<Vec3d> points = new ArrayList<>(resolved.size());
        for (Vector3d point : resolved) {
            points.add(new Vec3d(point.x, point.y, point.z));
        }

        if (closePath && points.size() >= 2) {
            Vec3d first = points.get(0);
            Vec3d last = points.get(points.size() - 1);
            if (!first.equals(last)) {
                points.add(first);
            }
        }

        LineData line = null;
        PolylineData polyline = null;
        PathData path = null;
        boolean valid = points.size() >= 2;
        if (valid) {
            if (points.size() == 2) {
                line = new LineData(points.get(0), points.get(1));
                path = PathData.fromLine(line);
            } else {
                polyline = new PolylineData(points);
                path = PathData.fromPolyline(polyline);
            }
            if (polyline == null) {
                polyline = new PolylineData(points);
            }
        }

        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_LINE_ID, line);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    public boolean isClosePath() {
        return closePath;
    }

    public void setClosePath(boolean closePath) {
        this.closePath = closePath;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("closePath", closePath);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object close = stateMap.get("closePath");
            if (close instanceof Boolean enabled) {
                setClosePath(enabled);
            }
        }
    }
}
