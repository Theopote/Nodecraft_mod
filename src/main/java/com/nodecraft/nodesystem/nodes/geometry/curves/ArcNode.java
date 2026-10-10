package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveSampleFence;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
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
    description = "Builds a sampled circular arc from a center point, plane, radius, and start/end angles. Sweep is directed and may exceed ±360°.",
    category = "geometry.curves",
    order = 4
)
public class ArcNode extends AbstractCurveNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Radius", category = "Arc", order = 1)
    private double defaultRadius = 4.0d;

    @NodeProperty(displayName = "Default Samples", category = "Arc", order = 2)
    private int defaultSamples = 24;

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
    private static final String INPUT_SAMPLES_ID = "input_samples";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_SWEEP_DEGREES_ID = "output_sweep_degrees";

    public ArcNode() {
        super(UUID.randomUUID(), "geometry.curves.arc");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Arc center point", NodeDataType.POINT, this, false, false));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Plane defining the arc orientation", NodeDataType.PLANE, this, false, false));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Fallback normal vector when no plane is connected", NodeDataType.VECTOR, this, false, false));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Arc radius", NodeDataType.DOUBLE, this, false, false));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Start angle in degrees", NodeDataType.DOUBLE, this, false, false));
        addInputPort(new BasePort(INPUT_END_ANGLE_ID, "End Angle",
            "End angle in degrees. Sweep is directed (end − start) and may exceed ±360°", NodeDataType.DOUBLE, this, false, false));
        addInputPort(new BasePort(INPUT_SAMPLES_ID, "Samples", "Number of sample points along the arc", NodeDataType.INTEGER, this, false, false));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary arc path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled arc points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Analytical arc length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SWEEP_DEGREES_ID, "Sweep Degrees",
            "Directed angular sweep from start to end (may exceed ±360°)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the arc inputs resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center;
        if (CurveInputUtils.isConnected(this, INPUT_CENTER_ID)) {
            center = CurveInputUtils.requireConnectedPointData(this, INPUT_CENTER_ID);
            if (center == null) {
                invalidate("Center is connected but invalid (must be finite PointData)");
                return;
            }
        } else {
            center = new Vector3d(centerX, centerY, centerZ);
            if (!VectorUtils.isFinite(center)) {
                invalidate("Default center is non-finite");
                return;
            }
        }

        Vector3d normal;
        if (CurveInputUtils.isConnected(this, INPUT_PLANE_ID)) {
            PlaneData plane = OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
            if (plane == null) {
                invalidate("Plane is connected but invalid");
                return;
            }
            normal = plane.getNormal();
        } else if (CurveInputUtils.isConnected(this, INPUT_NORMAL_ID)) {
            normal = CurveInputUtils.requireConnectedVectorData(this, INPUT_NORMAL_ID);
            if (normal == null) {
                invalidate("Normal is connected but invalid (must be finite VectorData)");
                return;
            }
        } else {
            normal = PlaneProjectionUtils.planeFromPreset(defaultPlane).getNormal();
        }

        Double radius = resolvePositiveDouble(INPUT_RADIUS_ID, defaultRadius);
        Double startDegrees = resolveFiniteDouble(INPUT_START_ANGLE_ID, 0.0d);
        Double endDegrees = resolveFiniteDouble(INPUT_END_ANGLE_ID, 90.0d);
        Integer samples = resolveBoundedInteger(INPUT_SAMPLES_ID, defaultSamples, 2, GenerationLimits.MAX_CURVE_SAMPLES);

        if (normal == null || !VectorUtils.isFinite(normal)) {
            invalidate("Plane or Normal could not be resolved");
            return;
        }
        if (radius == null) {
            invalidate("Radius must be a finite value greater than 0");
            return;
        }
        if (startDegrees == null || endDegrees == null) {
            invalidate("Start Angle and End Angle must be finite");
            return;
        }
        if (samples == null) {
            invalidate("Samples must be an integer from 2 to " + GenerationLimits.MAX_CURVE_SAMPLES);
            return;
        }

        var basis = PlaneProjectionUtils.createBasisFromNormal(normal);
        if (basis == null) {
            invalidate("Plane basis could not be constructed");
            return;
        }

        double sweepDegrees = endDegrees - startDegrees;
        double sweepRadians = Math.toRadians(sweepDegrees);
        if (!Double.isFinite(sweepDegrees) || !Double.isFinite(sweepRadians) || Math.abs(sweepRadians) <= EPSILON) {
            invalidate("Arc sweep must be a finite non-zero value");
            return;
        }
        double analyticalLength = Math.abs(sweepRadians) * radius;
        if (!Double.isFinite(analyticalLength)) {
            invalidate("Arc length is non-finite");
            return;
        }

        List<Vec3d> sampledPoints = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            double t = (double) i / (double) (samples - 1);
            double angleRadians = Math.toRadians(startDegrees) + sweepRadians * t;
            if (!Double.isFinite(angleRadians)) {
                invalidate("Arc sample angle is non-finite");
                return;
            }
            Vector3d radialX = VectorUtils.safeScale(basis.xAxis(), Math.cos(angleRadians) * radius);
            Vector3d radialY = VectorUtils.safeScale(basis.yAxis(), Math.sin(angleRadians) * radius);
            Vector3d point = VectorUtils.safeAdd(center, VectorUtils.safeAdd(radialX, radialY));
            if (point == null || !VectorUtils.isFinite(point)) {
                invalidate("Arc sample point is non-finite");
                return;
            }
            sampledPoints.add(new Vec3d(point.x, point.y, point.z));
        }

        CurveSampleFence.Result fenced = CurveSampleFence.validate(sampledPoints);
        if (fenced == null) {
            invalidate("Arc samples are non-finite or over budget");
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, fenced.path());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(fenced.vectors()));
        outputValues.put(OUTPUT_LENGTH_ID, analyticalLength);
        outputValues.put(OUTPUT_SWEEP_DEGREES_ID, sweepDegrees);
        markSuccess();
    }

    public double getDefaultRadius() {
        return defaultRadius;
    }

    public void setDefaultRadius(double defaultRadius) {
        if (defaultRadius > 0.0d && Double.isFinite(defaultRadius)
                && Double.compare(this.defaultRadius, defaultRadius) != 0) {
            this.defaultRadius = defaultRadius;
            markDirty();
        }
    }

    public int getDefaultSamples() {
        return defaultSamples;
    }

    public void setDefaultSamples(int defaultSamples) {
        if (defaultSamples >= 2
                && defaultSamples <= GenerationLimits.MAX_CURVE_SAMPLES
                && this.defaultSamples != defaultSamples) {
            this.defaultSamples = defaultSamples;
            markDirty();
        }
    }

    /** @deprecated Use {@link #getDefaultSamples()}. */
    @Deprecated
    public int getDefaultResolution() {
        return defaultSamples;
    }

    /** @deprecated Use {@link #setDefaultSamples(int)}. */
    @Deprecated
    public void setDefaultResolution(int defaultResolution) {
        setDefaultSamples(defaultResolution);
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
        if (Double.isFinite(centerX) && Double.compare(this.centerX, centerX) != 0) {
            this.centerX = centerX;
            markDirty();
        }
    }

    public double getCenterY() {
        return centerY;
    }

    public void setCenterY(double centerY) {
        if (Double.isFinite(centerY) && Double.compare(this.centerY, centerY) != 0) {
            this.centerY = centerY;
            markDirty();
        }
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(double centerZ) {
        if (Double.isFinite(centerZ) && Double.compare(this.centerZ, centerZ) != 0) {
            this.centerZ = centerZ;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultRadius", defaultRadius);
            put("defaultSamples", defaultSamples);
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
        if (map.get("defaultSamples") instanceof Number value) {
            setDefaultSamples(value.intValue());
        } else if (map.get("defaultResolution") instanceof Number value) {
            setDefaultSamples(value.intValue());
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
                // keep current defaultPlane
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

    private void invalidate(String message) {
        putNullOutputs(OUTPUT_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_LENGTH_ID, OUTPUT_SWEEP_DEGREES_ID);
        markInvalid(message);
    }
}
