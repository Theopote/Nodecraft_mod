package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.curve_from_points",
    displayName = "Points To Path",
    description = "Builds a line or polyline from an ordered point list",
    category = "geometry.curves",
    order = 0
)
public class PointsToPathNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_LINE_ID = "output_line";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private boolean closePath = false;

    public PointsToPathNode() {
        super(UUID.randomUUID(), "geometry.curves.curve_from_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Ordered point list. Supports Point, Vector, Position, or Block Coordinate values.",
            NodeDataType.LIST, this));

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
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof Collection<?> collection)) {
            outputValues.put(OUTPUT_LINE_ID, null);
            outputValues.put(OUTPUT_POLYLINE_ID, null);
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Vec3d> points = new ArrayList<>();
        for (Object entry : collection) {
            Vec3d point = resolvePoint(entry);
            if (point != null) {
                points.add(point);
            }
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
        boolean valid = points.size() >= 2;
        if (valid) {
            if (points.size() == 2) {
                line = new LineData(points.get(0), points.get(1));
            }
            polyline = new PolylineData(points);
        }

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

    private Vec3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            Vector3d p = pointData.getPosition();
            return new Vec3d(p.x, p.y, p.z);
        }
        if (value instanceof Coordinate coordinate) {
            return new Vec3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        if (value instanceof Vector3d vector) {
            return new Vec3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}
