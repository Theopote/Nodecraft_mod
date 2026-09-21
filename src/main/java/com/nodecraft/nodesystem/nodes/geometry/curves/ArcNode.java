package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
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

    @NodeProperty(displayName = "Default Plane", category = "Arc", order = 3,
        description = "Fallback plane when Plane and Normal ports are unconnected")
    private PlaneProjectionUtils.DefaultPlane defaultPlane = PlaneProjectionUtils.DefaultPlane.XZ;

    @NodeProperty(displayName = "Center X", category = "Center", order = 4,
        description = "Default center X when Center port is unconnected")
    private double centerX = 0.0d;

    @NodeProperty(displayName = "Center Y", category = "Center", order = 5,
        description = "Default center Y when Center port is unconnected")
    private double centerY = 0.0d;

    @NodeProperty(displayName = "Center Z", category = "Center", order = 6,
        description = "Default center Z when Center port is unconnected")
    private double centerZ = 0.0d;

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
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled arc points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Analytical arc length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SWEEP_DEGREES_ID, "Sweep Degrees", "Angular sweep from start to end", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the arc inputs resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = PlaneProjectionUtils.resolvePointOrDefault(
            inputValues.get(INPUT_CENTER_ID), centerX, centerY, centerZ);
        Vector3d normal = PlaneProjectionUtils.resolveNormal(
            inputValues.get(INPUT_PLANE_ID),
            inputValues.get(INPUT_NORMAL_ID),
            defaultPlane
        );
        double radius = readDoubleInput(INPUT_RADIUS_ID, defaultRadius);
        double startDegrees = readDoubleInput(INPUT_START_ANGLE_ID, 0.0d);
        double endDegrees = readDoubleInput(INPUT_END_ANGLE_ID, 90.0d);
        int resolution = GenerationLimits.clampSegments(2, readIntInput(INPUT_RESOLUTION_ID, defaultResolution));

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
        List<Vector3d> sampledVectors = new ArrayList<>(resolution);

        for (int i = 0; i < resolution; i++) {
            double t = (double) i / (double) (resolution - 1);
            double angleRadians = Math.toRadians(startDegrees) + sweepRadians * t;
            Vector3d point = new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(angleRadians) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(angleRadians) * radius));

            sampledPoints.add(new Vec3d(point.x, point.y, point.z));
            sampledVectors.add(point);
        }

        Curve curve = buildLinearCurve(sampledPoints);

        PolylineData polyline = new PolylineData(sampledPoints);
        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(sampledVectors));
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
        int resolved = GenerationLimits.clampSegments(2, defaultResolution);
        if (this.defaultResolution != resolved) {
            this.defaultResolution = resolved;
            markDirty();
        }
    }

    public PlaneProjectionUtils.DefaultPlane getDefaultPlane() {
        return defaultPlane;
    }

    public void setDefaultPlane(PlaneProjectionUtils.DefaultPlane defaultPlane) {
        if (defaultPlane != null && this.defaultPlane != defaultPlane) {
            this.defaultPlane = defaultPlane;
            markDirty();
        }
    }

    public double getCenterX() {
        return centerX;
    }

    public void setCenterX(double centerX) {
        if (Double.compare(this.centerX, centerX) != 0) {
            this.centerX = centerX;
            markDirty();
        }
    }

    public double getCenterY() {
        return centerY;
    }

    public void setCenterY(double centerY) {
        if (Double.compare(this.centerY, centerY) != 0) {
            this.centerY = centerY;
            markDirty();
        }
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(double centerZ) {
        if (Double.compare(this.centerZ, centerZ) != 0) {
            this.centerZ = centerZ;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultRadius", defaultRadius);
            put("defaultResolution", defaultResolution);
            put("defaultPlane", defaultPlane.name());
            put("centerX", centerX);
            put("centerY", centerY);
            put("centerZ", centerZ);
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
        if (map.get("defaultPlane") instanceof String value) {
            try {
                setDefaultPlane(PlaneProjectionUtils.DefaultPlane.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // ignore invalid legacy values
            }
        } else if (map.get("defaultPlaneType") instanceof String legacy) {
            try {
                setDefaultPlane(PlaneProjectionUtils.DefaultPlane.valueOf(legacy));
            } catch (IllegalArgumentException ignored) {
                setDefaultPlane(PlaneProjectionUtils.DefaultPlane.XZ);
            }
        }
        if (map.get("centerX") instanceof Number value) {
            setCenterX(value.doubleValue());
        }
        if (map.get("centerY") instanceof Number value) {
            setCenterY(value.doubleValue());
        }
        if (map.get("centerZ") instanceof Number value) {
            setCenterZ(value.doubleValue());
        }
        if (map.get("defaultCenterCoords") instanceof String legacyCoords) {
            Vector3d parsed = parseLegacyCenterCoords(legacyCoords);
            if (parsed != null) {
                setCenterX(parsed.x);
                setCenterY(parsed.y);
                setCenterZ(parsed.z);
            }
        }
    }

    private static @Nullable Vector3d parseLegacyCenterCoords(String coords) {
        String[] parts = coords.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new Vector3d(
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void writeInvalid() {
        putNullOutputs(OUTPUT_CURVE_ID, OUTPUT_POLYLINE_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putDoubleOutputs(0.0d, OUTPUT_LENGTH_ID, OUTPUT_SWEEP_DEGREES_ID);
        putBooleanOutputs(false, OUTPUT_VALID_ID);
    }
}
