package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BentSdfData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.SdfExpressionLimits;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.bend_sdf",
    displayName = "Bend SDF",
    description = "Applies an axial bend domain deformation to a signed distance field",
    category = "transform.deformations",
    order = 10
)
public class BendSdfNode extends AbstractSdfDeformationNode {

    @NodeProperty(displayName = "Bend Degrees", category = "Bend", order = 1)
    private double bendDegrees = 90.0d;

    @NodeProperty(displayName = "Bend Length", category = "Bend", order = 2)
    private double bendLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Bend", order = 3)
    private BentSdfData.ClampMode clampMode = BentSdfData.ClampMode.CLAMP;

    @NodeProperty(displayName = "Bounds Padding", category = "Bounds", order = 4)
    private double boundsPadding = 2.0d;

    @NodeProperty(displayName = "Bounds Samples", category = "Bounds", order = 5)
    private int boundsSamples = 5;

    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_BEND_NORMAL_ID = "input_bend_normal";
    private static final String INPUT_BEND_DEGREES_ID = "input_bend_degrees";
    private static final String INPUT_BEND_LENGTH_ID = "input_bend_length";

    public BendSdfNode() {
        super("transform.deformations.bend_sdf");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF",
            "Source signed distance field to bend", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin",
            "Point on the bend axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction",
            "Direction along which bend is distributed", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_BEND_NORMAL_ID, "Bend Normal",
            "Direction the bend curves toward", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_BEND_DEGREES_ID, "Bend Degrees",
            "Total bend angle over the bend length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEND_LENGTH_ID, "Bend Length",
            "Length over which the angle is distributed", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MIN_ID, "Bounds Min",
            "Optional source sampling minimum", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MAX_ID, "Bounds Max",
            "Optional source sampling maximum", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF",
            "Bent signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MIN_ID, "Bounds Min",
            "Estimated output bounds minimum", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MAX_ID, "Bounds Max",
            "Estimated output bounds maximum", NodeDataType.POINT, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Applies an axial bend domain deformation to a signed distance field";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SdfSource source = resolveSdfSource(boundsPadding);
        Vector3d axisOrigin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_AXIS_ORIGIN_ID, new Vector3d());
        Vector3d axisDirection = OptionalPortDrive.resolveOptionalVector(
            this, INPUT_AXIS_DIRECTION_ID, new Vector3d(1.0d, 0.0d, 0.0d));
        Vector3d bendNormal = OptionalPortDrive.resolveOptionalVector(
            this, INPUT_BEND_NORMAL_ID, new Vector3d(0.0d, 1.0d, 0.0d));
        Double resolvedDegrees = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BEND_DEGREES_ID, bendDegrees);
        Double resolvedLength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BEND_LENGTH_ID, bendLength);

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
        if (!VectorUtils.isNonZero(bendNormal)) {
            failSdfOutputs("Bend normal must be non-zero");
            return;
        }
        if (resolvedDegrees == null) {
            failSdfOutputs("Invalid bend angle");
            return;
        }
        if (resolvedLength == null || resolvedLength <= 0.0d) {
            failSdfOutputs("Bend length must be positive");
            return;
        }

        DeformationUtils.BendFrame frame = DeformationUtils.resolveBendFrame(axisDirection, bendNormal);
        if (frame == null) {
            failSdfOutputs("Bend normal must not be parallel to axis direction");
            return;
        }

        if (!SdfExpressionLimits.canWrapUnary(source.sdf())) {
            failSdfOutputs(SdfExpressionLimits.BUDGET_EXCEEDED);
            return;
        }

        BentSdfData bent = new BentSdfData(
            source.sdf(),
            axisOrigin,
            frame.axis(),
            frame.normal(),
            resolvedDegrees,
            resolvedLength,
            clampMode
        );
        AxisAlignedBounds outputBounds = estimateBentBounds(
            source.min(),
            source.max(),
            bent,
            GenerationLimits.clampBoundsSamples(boundsSamples),
            boundsPadding
        );
        if (outputBounds == null || !outputBounds.isValid()) {
            failSdfOutputs("Failed to estimate output bounds");
            return;
        }

        outputValues.put(OUTPUT_SDF_ID, bent);
        outputValues.put(OUTPUT_BOUNDS_MIN_ID, new PointData(outputBounds.min()));
        outputValues.put(OUTPUT_BOUNDS_MAX_ID, new PointData(outputBounds.max()));
        markSuccess();
    }

    private static @Nullable AxisAlignedBounds estimateBentBounds(
        Vector3d sourceMin,
        Vector3d sourceMax,
        BentSdfData bent,
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
                    Vector3d p = bent.bendPoint(new Vector3d(x, y, z));
                    if (!VectorUtils.isFinite(p)) {
                        return null;
                    }
                    bounds = bounds == null ? AxisAlignedBounds.from(p, p) : bounds.include(p);
                }
            }
        }
        return bounds == null ? null : bounds.expanded(padding);
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

    public double getBendDegrees() {
        return bendDegrees;
    }

    public void setBendDegrees(double bendDegrees) {
        if (Double.isFinite(bendDegrees)) {
            this.bendDegrees = bendDegrees;
            markDirty();
        }
    }

    public double getBendLength() {
        return bendLength;
    }

    public void setBendLength(double bendLength) {
        if (Double.isFinite(bendLength) && bendLength > 0.0d) {
            this.bendLength = bendLength;
            markDirty();
        }
    }

    public BentSdfData.ClampMode getClampMode() {
        return clampMode;
    }

    public void setClampMode(BentSdfData.ClampMode clampMode) {
        this.clampMode = clampMode == null ? BentSdfData.ClampMode.CLAMP : clampMode;
        markDirty();
    }

    private void setClampModeString(String value) {
        if (value == null || value.isBlank()) {
            setClampMode(BentSdfData.ClampMode.CLAMP);
            return;
        }
        try {
            setClampMode(BentSdfData.ClampMode.valueOf(value.trim().toUpperCase()));
        } catch (RuntimeException ignored) {
            setClampMode(BentSdfData.ClampMode.CLAMP);
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
        state.put("bendDegrees", bendDegrees);
        state.put("bendLength", bendLength);
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
        if (map.get("bendDegrees") instanceof Number value) {
            setBendDegrees(value.doubleValue());
        }
        if (map.get("bendLength") instanceof Number value) {
            setBendLength(value.doubleValue());
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
