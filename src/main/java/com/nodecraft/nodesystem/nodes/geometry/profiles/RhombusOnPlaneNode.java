package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.profiles.rhombus_profile",
    displayName = "Rhombus On Plane",
    description = "Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane",
    category = "geometry.profiles",
    order = 17
)
public class RhombusOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_DIAGONAL_X_ID = "input_diagonal_x";
    private static final String INPUT_DIAGONAL_Y_ID = "input_diagonal_y";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RhombusOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.rhombus_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Rhombus center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_DIAGONAL_X_ID, "Diagonal X", "Diagonal length along local X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DIAGONAL_Y_ID, "Diagonal Y", "Diagonal length along local Y axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane rhombus X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed rhombus points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Rhombus polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rhombus boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when rhombus profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object dxObj = inputValues.get(INPUT_DIAGONAL_X_ID);
        Object dyObj = inputValues.get(INPUT_DIAGONAL_Y_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(dxObj instanceof Number dxN) || !(dyObj instanceof Number dyN)) {
            writeInvalid();
            return;
        }
        double dx = dxN.doubleValue();
        double dy = dyN.doubleValue();
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || dx <= 0.0d || dy <= 0.0d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        double hx = dx * 0.5d;
        double hy = dy * 0.5d;
        List<Vector3d> points = new ArrayList<>(5);
        points.add(toWorld(center, basis, 0.0d, -hy));
        points.add(toWorld(center, basis, hx, 0.0d));
        points.add(toWorld(center, basis, 0.0d, hy));
        points.add(toWorld(center, basis, -hx, 0.0d));
        points.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, new PlaneData(center, basis.normal())));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d toWorld(Vector3d center, ProfilePlaneUtils.Basis basis, double localX, double localY) {
        return new Vector3d(center)
            .add(new Vector3d(basis.xAxis()).mul(localX))
            .add(new Vector3d(basis.yAxis()).mul(localY));
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
