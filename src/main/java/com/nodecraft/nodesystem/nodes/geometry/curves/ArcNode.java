package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.core.NodeCraft;
import net.minecraft.util.math.BlockPos;
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
public class ArcNode extends BaseNode {

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
    public String getDescription() {
        return "Builds a sampled circular arc from a center point, plane, radius, and start/end angles";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveCenter(inputValues.get(INPUT_CENTER_ID));
        Vector3d normal = resolveNormal(inputValues.get(INPUT_PLANE_ID), inputValues.get(INPUT_NORMAL_ID));
        double radius = getInputDouble(INPUT_RADIUS_ID, defaultRadius);
        double startDegrees = getInputDouble(INPUT_START_ANGLE_ID, 0.0d);
        double endDegrees = getInputDouble(INPUT_END_ANGLE_ID, 90.0d);
        int resolution = Math.max(2, getInputInt(INPUT_RESOLUTION_ID, defaultResolution));

        // Validate inputs: center and normal are required
        if (center == null) {
            NodeCraft.LOGGER.warn("ArcNode: center is null; cannot build arc");
            writeEmptyOutputs();
            return;
        }
        if (normal == null || normal.lengthSquared() <= EPSILON) {
            NodeCraft.LOGGER.warn("ArcNode: normal is null or zero; cannot build arc");
            writeEmptyOutputs();
            return;
        }
        if (radius <= EPSILON) {
            NodeCraft.LOGGER.warn("ArcNode: radius must be positive");
            writeEmptyOutputs();
            return;
        }

        Vector3d normalizedNormal = new Vector3d(normal).normalize();
        Basis basis = buildBasis(normalizedNormal);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        double sweepDegrees = endDegrees - startDegrees;
        double sweepRadians = Math.toRadians(sweepDegrees);
        if (Math.abs(sweepRadians) <= EPSILON) {
            writeEmptyOutputs();
            return;
        }

        List<Vec3d> sampledPoints = new ArrayList<>(resolution);
        List<PointData> pointData = new ArrayList<>(resolution);
        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);

        for (int i = 0; i < resolution; i++) {
            double t = resolution == 1 ? 0.0d : (double) i / (double) (resolution - 1);
            double angleRadians = Math.toRadians(startDegrees) + sweepRadians * t;
            Vector3d point = new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(angleRadians) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(angleRadians) * radius));

            Vec3d vec = new Vec3d(point.x, point.y, point.z);
            sampledPoints.add(vec);
            pointData.add(new PointData(point));
            curve.addControlPoint(vec);
        }

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

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_SWEEP_DEGREES_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private @Nullable Vector3d resolveCenter(@Nullable Object value) {
        // If input is provided, try to resolve it
        Vector3d resolved = SpatialValueResolver.resolveVector3d(value);
        if (resolved != null) {
            return resolved;
        }

        // Otherwise, use default center coordinates (typically "0,0,0")
        try {
            String[] parts = defaultCenterCoords.trim().split(",");
            if (parts.length == 3) {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                return new Vector3d(x, y, z);
            }
        } catch (NumberFormatException e) {
            NodeCraft.LOGGER.warn("ArcNode: invalid defaultCenterCoords format '{}'; using 0,0,0", defaultCenterCoords);
        }

        // Fallback to origin
        return new Vector3d(0.0d, 0.0d, 0.0d);
    }

    private @Nullable Vector3d resolveNormal(@Nullable Object planeValue, @Nullable Object normalValue) {
        // Priority 1: If PlaneData is provided, extract normal
        if (planeValue instanceof PlaneData plane) {
            return plane.getNormal();
        }

        // Priority 2: If Normal vector is provided directly, use it
        Vector3d resolved = SpatialValueResolver.resolveVector3d(normalValue);
        if (resolved != null) {
            return resolved;
        }

        // Priority 3: Use default plane based on defaultPlaneType property
        PlaneData defaultPlane = getDefaultPlane(defaultPlaneType);
        return defaultPlane.getNormal();
    }

    private PlaneData getDefaultPlane(String planeType) {
        return switch (planeType) {
            case "XY" -> PlaneData.XY_PLANE;
            case "YZ" -> PlaneData.YZ_PLANE;
            default -> PlaneData.XZ_PLANE;  // Default to XZ plane
        };
    }

    private @Nullable Basis buildBasis(Vector3d normal) {
        Vector3d reference = Math.abs(normal.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d xAxis = reference.cross(normal, new Vector3d());
        if (xAxis.lengthSquared() <= EPSILON) {
            reference = new Vector3d(0.0d, 0.0d, 1.0d);
            xAxis = reference.cross(normal, new Vector3d());
        }
        if (xAxis.lengthSquared() <= EPSILON) {
            return null;
        }
        xAxis.normalize();
        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= EPSILON) {
            return null;
        }
        return new Basis(xAxis, yAxis.normalize());
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private record Basis(Vector3d xAxis, Vector3d yAxis) {
    }
}
