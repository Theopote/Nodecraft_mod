package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.arc",
    displayName = "Arc",
    description = "Builds a sampled circular arc from a center point, plane, radius, and start/end angles",
    category = "geometry.curves",
    order = 2
)
public class ArcNode extends AbstractCurveNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Radius", category = "Arc", order = 1)
    private double defaultRadius = 4.0d;

    @NodeProperty(displayName = "Default Resolution", category = "Arc", order = 2)
    private int defaultResolution = 24;

    @NodeProperty(displayName = "Default Plane", category = "Arc", order = 3)
    private String defaultPlaneType = "XZ";  // XZ, XY, or YZ

    @NodeProperty(displayName = "Default Center", category = "Arc", order = 4)
    private String defaultCenterCoords = "0,0,0";  // Format: x,y,z

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_NORMAL_ID = "input_normal";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";
    private static final String INPUT_END_ANGLE_ID = "input_end_angle";
    private static final String INPUT_RESOLUTION_ID = "input_resolution";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_SWEEP_DEGREES_ID = "output_sweep_degrees";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ArcNode() {
        super(UUID.randomUUID(), "geometry.curves.arc");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Arc center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Plane defining the arc orientation", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Fallback normal vector when no plane is connected", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Arc radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Start angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ANGLE_ID, "End Angle", "End angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution", "Number of samples along the arc", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled curve representation of the arc", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Polyline approximation of the arc", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled arc points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Analytical arc length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SWEEP_DEGREES_ID, "Sweep Degrees", "Angular sweep from start to end", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the arc inputs resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = PlaneProjectionUtils.resolvePointOrDefault(inputValues.get(INPUT_CENTER_ID), defaultCenterCoords);
        Vector3d normal = PlaneProjectionUtils.resolveNormal(
            inputValues.get(INPUT_PLANE_ID),
            inputValues.get(INPUT_NORMAL_ID),
            defaultPlaneType
        );
        double radius = readDoubleInput(INPUT_RADIUS_ID, defaultRadius);
        double startDegrees = readDoubleInput(INPUT_START_ANGLE_ID, 0.0d);
        double endDegrees = readDoubleInput(INPUT_END_ANGLE_ID, 90.0d);
        int resolution = Math.max(2, readIntInput(INPUT_RESOLUTION_ID, defaultResolution));

        // Validate inputs: center and normal are required
        if (normal == null || normal.lengthSquared() <= EPSILON) {
            writeInvalid();
            return;
        }
        if (radius <= EPSILON) {
            writeInvalid();
            return;
        }

        var basis = PlaneProjectionUtils.createBasisFromNormal(normal);
        if (basis == null) {
            writeInvalid();
            return;
        }

        double sweepDegrees = endDegrees - startDegrees;
        double sweepRadians = Math.toRadians(sweepDegrees);
        if (Math.abs(sweepRadians) <= EPSILON) {
            writeInvalid();
            return;
        }

        List<Vec3d> sampledPoints = new ArrayList<>(resolution);
        List<PointData> pointData = new ArrayList<>(resolution);

        for (int i = 0; i < resolution; i++) {
            double t = (double) i / (double) (resolution - 1);
            double angleRadians = Math.toRadians(startDegrees) + sweepRadians * t;
            Vector3d point = new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(angleRadians) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(angleRadians) * radius));

            Vec3d vec = new Vec3d(point.x, point.y, point.z);
            sampledPoints.add(vec);
            pointData.add(new PointData(point));
        }

        Curve curve = buildLinearCurve(sampledPoints);

        PolylineData polyline = new PolylineData(sampledPoints);
        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(pointData));
        outputValues.put(OUTPUT_LENGTH_ID, Math.abs(sweepRadians) * radius);
        outputValues.put(OUTPUT_SWEEP_DEGREES_ID, sweepDegrees);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public double getDefaultRadius() {
        return defaultRadius;
    }

    public void setDefaultRadius(double defaultRadius) {
        double resolved = Math.max(0.0d, defaultRadius);
        if (Double.compare(this.defaultRadius, resolved) != 0) {
            this.defaultRadius = resolved;
            markDirty();
        }
    }

    public int getDefaultResolution() {
        return defaultResolution;
    }

    public void setDefaultResolution(int defaultResolution) {
        int resolved = Math.max(2, defaultResolution);
        if (this.defaultResolution != resolved) {
            this.defaultResolution = resolved;
            markDirty();
        }
    }

    public String getDefaultPlaneType() {
        return defaultPlaneType;
    }

    public void setDefaultPlaneType(String planeType) {
        if (planeType != null && (planeType.equals("XY") || planeType.equals("XZ") || planeType.equals("YZ"))) {
            if (!this.defaultPlaneType.equals(planeType)) {
                this.defaultPlaneType = planeType;
                markDirty();
            }
        }
    }

    public String getDefaultCenterCoords() {
        return defaultCenterCoords;
    }

    public void setDefaultCenterCoords(String centerCoords) {
        if (centerCoords != null && !this.defaultCenterCoords.equals(centerCoords)) {
            this.defaultCenterCoords = centerCoords;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultRadius", defaultRadius);
            put("defaultResolution", defaultResolution);
            put("defaultPlaneType", defaultPlaneType);
            put("defaultCenterCoords", defaultCenterCoords);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultRadius") instanceof Number value) {
            setDefaultRadius(value.doubleValue());
        }
        if (map.get("defaultResolution") instanceof Number value) {
            setDefaultResolution(value.intValue());
        }
        if (map.get("defaultPlaneType") instanceof String value) {
            setDefaultPlaneType(value);
        }
        if (map.get("defaultCenterCoords") instanceof String value) {
            setDefaultCenterCoords(value);
        }
    }

    private void writeInvalid() {
        putNullOutputs(OUTPUT_CURVE_ID, OUTPUT_POLYLINE_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putDoubleOutputs(0.0d, OUTPUT_LENGTH_ID, OUTPUT_SWEEP_DEGREES_ID);
        putBooleanOutputs(false, OUTPUT_VALID_ID);
    }

}
