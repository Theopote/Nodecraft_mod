package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.TwistedSdfData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.twist_sdf",
    displayName = "Twist SDF",
    description = "Applies an axial twist domain deformation to a signed distance field",
    category = "transform.deformations",
    order = 9
)
public class TwistSdfNode extends AbstractSdfDeformationNode {

    @NodeProperty(displayName = "Angle Degrees", category = "Twist", order = 1)
    private double angleDegrees = 180.0d;

    @NodeProperty(displayName = "Twist Length", category = "Twist", order = 2)
    private double twistLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Twist", order = 3)
    private TwistedSdfData.ClampMode clampMode = TwistedSdfData.ClampMode.CLAMP;

    @NodeProperty(displayName = "Bounds Padding", category = "Bounds", order = 4)
    private double boundsPadding = 2.0d;

    @NodeProperty(displayName = "Bounds Samples", category = "Bounds", order = 5,
        description = "Grid samples per axis for estimating the twisted output bounds")
    private int boundsSamples = 5;

    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_ANGLE_DEGREES_ID = "input_angle_degrees";
    private static final String INPUT_TWIST_LENGTH_ID = "input_twist_length";

    public TwistSdfNode() {
        super("transform.deformations.twist_sdf");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF",
            "Source signed distance field to twist", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin",
            "Point on the twist axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction",
            "Twist axis direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_DEGREES_ID, "Angle Degrees",
            "Total twist angle over the twist length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TWIST_LENGTH_ID, "Twist Length",
            "Axial length over which the angle is distributed", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MIN_ID, "Bounds Min",
            "Optional source sampling minimum", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MAX_ID, "Bounds Max",
            "Optional source sampling maximum", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF",
            "Twisted signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MIN_ID, "Bounds Min",
            "Estimated output bounds minimum", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MAX_ID, "Bounds Max",
            "Estimated output bounds maximum", NodeDataType.POINT, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Applies an axial twist domain deformation to a signed distance field";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SdfSource source = resolveSdfSource(boundsPadding);
        Vector3d axisOrigin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_AXIS_ORIGIN_ID, new Vector3d());
        Vector3d axisDirection = OptionalPortDrive.resolveOptionalVector(this, INPUT_AXIS_DIRECTION_ID, null);
        Double resolvedAngle = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ANGLE_DEGREES_ID, angleDegrees);
        Double resolvedLength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_TWIST_LENGTH_ID, twistLength);

        if (source == null) {
            failSdfOutputs(resolveSdfFailureReason());
            return;
        }
        if (axisOrigin == null) {
            failSdfOutputs("Invalid axis origin");
            return;
        }
        if (!VectorUtils.isNonZero(axisDirection)) {
            failSdfOutputs("Axis direction must be non-zero");
            return;
        }
        Vector3d axis = VectorUtils.safeNormalize(axisDirection);
        if (axis == null) {
            failSdfOutputs("Axis direction must be a finite non-zero vector");
            return;
        }
        if (resolvedAngle == null) {
            failSdfOutputs("Invalid angle");
            return;
        }
        if (resolvedLength == null || resolvedLength <= 0.0d) {
            failSdfOutputs("Twist length must be positive");
            return;
        }

        TwistedSdfData twisted = new TwistedSdfData(
            source.sdf(),
            axisOrigin,
            axis,
            resolvedAngle,
            resolvedLength,
            clampMode
        );
        AxisAlignedBounds outputBounds = estimateTwistedBounds(
            source.min(),
            source.max(),
            twisted,
            GenerationLimits.clampBoundsSamples(boundsSamples),
            boundsPadding
        );
        if (outputBounds == null || !outputBounds.isValid()) {
            failSdfOutputs("Failed to estimate output bounds");
            return;
        }

        outputValues.put(OUTPUT_SDF_ID, twisted);
        outputValues.put(OUTPUT_BOUNDS_MIN_ID, new PointData(outputBounds.min()));
        outputValues.put(OUTPUT_BOUNDS_MAX_ID, new PointData(outputBounds.max()));
        markSuccess();
    }

    private String resolveSdfFailureReason() {
        boolean minConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MIN_ID);
        boolean maxConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MAX_ID);
        if (minConnected != maxConnected) {
            return "Invalid or incomplete bounds";
        }
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (OptionalPortDrive.isConnected(this, INPUT_SDF_ID) && sdfObj == null) {
            return "SDF input required";
        }
        if (!(sdfObj instanceof com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData)) {
            return "SDF input required";
        }
        if (minConnected) {
            return "Invalid or incomplete bounds";
        }
        return "SDF input required";
    }

    private static @Nullable AxisAlignedBounds estimateTwistedBounds(
        Vector3d sourceMin,
        Vector3d sourceMax,
        TwistedSdfData twisted,
        int samplesPerAxis,
        double padding
    ) {
        AxisAlignedBounds bounds = null;
        int samples = GenerationLimits.clampBoundsSamples(samplesPerAxis);
        for (int ix = 0; ix < samples; ix++) {
            double x = VectorUtils.safeScalarLerp(sourceMin.x, sourceMax.x, ix / (double) (samples - 1));
            for (int iy = 0; iy < samples; iy++) {
                double y = VectorUtils.safeScalarLerp(sourceMin.y, sourceMax.y, iy / (double) (samples - 1));
                for (int iz = 0; iz < samples; iz++) {
                    double z = VectorUtils.safeScalarLerp(sourceMin.z, sourceMax.z, iz / (double) (samples - 1));
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        return null;
                    }
                    Vector3d p = twisted.twistPoint(new Vector3d(x, y, z));
                    if (!VectorUtils.isFinite(p)) {
                        return null;
                    }
                    bounds = bounds == null ? AxisAlignedBounds.from(p, p) : bounds.include(p);
                }
            }
        }
        return bounds == null ? null : bounds.expanded(padding);
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(double angleDegrees) {
        if (Double.isFinite(angleDegrees)) {
            this.angleDegrees = angleDegrees;
            markDirty();
        }
    }

    public double getTwistLength() {
        return twistLength;
    }

    public void setTwistLength(double twistLength) {
        if (Double.isFinite(twistLength) && twistLength > 0.0d) {
            this.twistLength = twistLength;
            markDirty();
        }
    }

    public TwistedSdfData.ClampMode getClampMode() {
        return clampMode;
    }

    public void setClampMode(TwistedSdfData.ClampMode clampMode) {
        this.clampMode = clampMode == null ? TwistedSdfData.ClampMode.CLAMP : clampMode;
        markDirty();
    }

    public void setClampModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setClampMode(TwistedSdfData.ClampMode.CLAMP);
            return;
        }
        try {
            setClampMode(TwistedSdfData.ClampMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setClampMode(TwistedSdfData.ClampMode.CLAMP);
        }
    }

    public double getBoundsPadding() {
        return boundsPadding;
    }

    public void setBoundsPadding(double boundsPadding) {
        if (Double.isFinite(boundsPadding) && boundsPadding >= 0.0d) {
            this.boundsPadding = boundsPadding;
            markDirty();
        }
    }

    public int getBoundsSamples() {
        return boundsSamples;
    }

    public void setBoundsSamples(int boundsSamples) {
        this.boundsSamples = GenerationLimits.clampBoundsSamples(boundsSamples);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("angleDegrees", angleDegrees);
        state.put("twistLength", twistLength);
        state.put("clampMode", clampMode.name());
        state.put("boundsPadding", boundsPadding);
        state.put("boundsSamples", boundsSamples);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("angleDegrees") instanceof Number value) {
            setAngleDegrees(value.doubleValue());
        }
        if (map.get("twistLength") instanceof Number value) {
            setTwistLength(value.doubleValue());
        }
        if (map.get("clampMode") instanceof String value) {
            setClampModeString(value);
        }
        if (map.get("boundsPadding") instanceof Number value) {
            setBoundsPadding(value.doubleValue());
        }
        if (map.get("boundsSamples") instanceof Number value) {
            setBoundsSamples(value.intValue());
        }
    }
}
