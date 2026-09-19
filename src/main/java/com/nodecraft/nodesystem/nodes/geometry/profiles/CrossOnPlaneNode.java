package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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
    effect = NodeEffect.PURE,
    id = "geometry.profiles.cross_profile",
    displayName = "Cross On Plane",
    description = "Constructs a plus-shaped cross profile from arm length, arm width, center, and plane",
    category = "geometry.profiles",
    order = 21
)
public class CrossOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_ARM_LENGTH_ID = "input_arm_length";
    private static final String INPUT_ARM_WIDTH_ID = "input_arm_width";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CrossOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.cross_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Cross center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ARM_LENGTH_ID, "Arm Length", "Length from center to each arm tip", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ARM_WIDTH_ID, "Arm Width", "Width of each arm", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane cross X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed cross points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Cross polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed cross boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when cross profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a plus-shaped cross profile from arm length, arm width, center, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object armLengthObj = inputValues.get(INPUT_ARM_LENGTH_ID);
        Object armWidthObj = inputValues.get(INPUT_ARM_WIDTH_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(armLengthObj instanceof Number lN) || !(armWidthObj instanceof Number wN)) {
            writeInvalid();
            return;
        }
        double armLength = lN.doubleValue();
        double armWidth = wN.doubleValue();
        if (!Double.isFinite(armLength) || !Double.isFinite(armWidth) || armLength <= 0.0d || armWidth <= 0.0d || armWidth >= armLength * 2.0d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        double h = armWidth * 0.5d;
        double l = armLength;
        double[][] local = {
            {-h, -l}, {h, -l}, {h, -h}, {l, -h}, {l, h}, {h, h},
            {h, l}, {-h, l}, {-h, h}, {-l, h}, {-l, -h}, {-h, -h}
        };

        List<Vector3d> points = new ArrayList<>(local.length + 1);
        for (double[] p : local) {
            points.add(toWorld(center, basis, p[0], p[1]));
        }
        points.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, new PlaneData(center, basis.normal())));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d toWorld(Vector3d center, ProfilePlaneUtils.Basis basis, double x, double y) {
        return new Vector3d(center)
            .add(new Vector3d(basis.xAxis()).mul(x))
            .add(new Vector3d(basis.yAxis()).mul(y));
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
