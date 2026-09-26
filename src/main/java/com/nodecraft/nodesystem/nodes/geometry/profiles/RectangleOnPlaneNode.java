package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.rectangle_profile",
    displayName = "Rectangle On Plane",
    description = "Constructs a planar rectangle from width, height, and an optional center/plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 0
)
public class RectangleOnPlaneNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_WIDTH_ID = "output_width";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Width", category = "Size", order = 1)
    private double width = 5.0d;

    @NodeProperty(displayName = "Height", category = "Size", order = 2)
    private double height = 5.0d;

    public RectangleOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.rectangle_profile");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Rectangle width along local X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Rectangle height along local Y axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional in-plane axis to control rectangle rotation", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Rectangle corner points in closed order", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Rectangle polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rectangle boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved rectangle center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_WIDTH_ID, "Width", "Resolved width", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Resolved height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a rectangle could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a planar rectangle from width, height, and an optional center/plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double resolvedWidth = resolveDouble(inputValues.get(INPUT_WIDTH_ID), width);
        double resolvedHeight = resolveDouble(inputValues.get(INPUT_HEIGHT_ID), height);
        Vector3d preferredXAxis = inputValues.get(INPUT_X_AXIS_ID) instanceof Vector3d vector ? new Vector3d(vector) : null;

        if (!Double.isFinite(resolvedWidth) || !Double.isFinite(resolvedHeight)
                || resolvedWidth <= 0.0d || resolvedHeight <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferredXAxis);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        Vector3d halfX = new Vector3d(basis.xAxis()).mul(resolvedWidth * 0.5d);
        Vector3d halfY = new Vector3d(basis.yAxis()).mul(resolvedHeight * 0.5d);

        List<Vector3d> corners = new ArrayList<>(5);
        corners.add(new Vector3d(center).sub(halfX).sub(halfY));
        corners.add(new Vector3d(center).add(halfX).sub(halfY));
        corners.add(new Vector3d(center).add(halfX).add(halfY));
        corners.add(new Vector3d(center).sub(halfX).add(halfY));
        corners.add(new Vector3d(corners.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(corners));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(corners, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(corners));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_WIDTH_ID, resolvedWidth);
        outputValues.put(OUTPUT_HEIGHT_ID, resolvedHeight);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_WIDTH_ID, 0.0d);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("width", width);
        state.put("height", height);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("width") instanceof Number n) width = n.doubleValue();
        if (map.get("height") instanceof Number n) height = n.doubleValue();
    }
}
