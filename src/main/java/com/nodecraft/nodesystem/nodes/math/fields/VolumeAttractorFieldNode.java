package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.volume_attractor_field",
    displayName = "Volume Attractor Field",
    description = "Builds a volume-based attractor field using center-pull or nearest-surface pull from geometry/SDF inputs.",
    category = "math.fields",
    order = 10
)
public class VolumeAttractorFieldNode extends BaseNode {

    public enum PullMode {
        CENTER_PULL,
        SURFACE_PULL
    }

    @NodeProperty(displayName = "Pull Mode", category = "Attractor", order = 1)
    private PullMode pullMode = PullMode.SURFACE_PULL;

    @NodeProperty(displayName = "Falloff", category = "Attractor", order = 2)
    private AttractorFieldUtils.FalloffMode falloff = AttractorFieldUtils.FalloffMode.INVERSE;

    @NodeProperty(displayName = "Strength", category = "Attractor", order = 3)
    private double strength = 1.0d;

    @NodeProperty(displayName = "Radius", category = "Attractor", order = 4)
    private double radius = 8.0d;

    @NodeProperty(displayName = "Exponent", category = "Attractor", order = 5)
    private double exponent = 2.0d;

    @NodeProperty(displayName = "SDF Step", category = "Attractor", order = 6)
    private double sdfStep = 0.25d;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_EXPONENT_ID = "input_exponent";
    private static final String INPUT_SDF_STEP_ID = "input_sdf_step";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VolumeAttractorFieldNode() {
        super(UUID.randomUUID(), "math.fields.volume_attractor_field");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry volume used by the attractor", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Optional SDF for accurate surface pull", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Field strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Falloff radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "Falloff exponent override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SDF_STEP_ID, "SDF Step", "Finite-difference step for SDF surface mode", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Volume attractor vector field output", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a volume-based attractor field using center-pull or nearest-surface pull from geometry/SDF inputs.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = null;
        if (FieldSampleUtils.isPortDriven(this, INPUT_GEOMETRY_ID)) {
            Object geometryObj = getInput(INPUT_GEOMETRY_ID);
            if (!(geometryObj instanceof GeometryData data)) {
                writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
                return;
            }
            geometry = data;
        }

        SignedDistanceFieldData sdf = null;
        if (FieldSampleUtils.isPortDriven(this, INPUT_SDF_ID)) {
            sdf = AttractorFieldUtils.tryExtractSdf(getInput(INPUT_SDF_ID));
            if (sdf == null) {
                writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
                return;
            }
        } else {
            sdf = AttractorFieldUtils.tryExtractSdf(geometry);
        }

        Vector3d resolvedCenter = new Vector3d();
        boolean hasCenter = false;
        if (FieldSampleUtils.isPortDriven(this, INPUT_CENTER_ID)) {
            Vector3d center = FieldSampleUtils.resolveFinitePoint(getInput(INPUT_CENTER_ID));
            if (center == null) {
                writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
                return;
            }
            resolvedCenter.set(center);
            hasCenter = true;
        } else if (AttractorFieldUtils.tryExtractCenter(geometry, resolvedCenter)
                || AttractorFieldUtils.tryExtractCenter(sdf, resolvedCenter)) {
            hasCenter = VectorUtils.isFinite(resolvedCenter);
            if (!hasCenter) {
                resolvedCenter.zero();
            }
        }

        PullMode mode = pullMode == null ? PullMode.SURFACE_PULL : pullMode;
        if (!hasCenter && (mode == PullMode.CENTER_PULL || geometry == null && sdf == null)) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        Double effectiveStrength = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_STRENGTH_ID, strength);
        Double effectiveRadius = FieldSampleUtils.resolveOptionalAttractorRadius(this, INPUT_RADIUS_ID, radius);
        Double effectiveExponent = FieldSampleUtils.resolveOptionalAttractorExponent(this, INPUT_EXPONENT_ID, exponent);
        Double effectiveSdfStep = FieldSampleUtils.resolveOptionalPositiveDouble(this, INPUT_SDF_STEP_ID, sdfStep);
        if (effectiveStrength == null || effectiveRadius == null || effectiveExponent == null
                || effectiveSdfStep == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        AttractorFieldUtils.FalloffMode falloffMode = falloff == null ? AttractorFieldUtils.FalloffMode.INVERSE : falloff;

        final SignedDistanceFieldData fieldSdf = sdf;
        final GeometryData fieldGeometry = geometry;
        final boolean hasCenterFinal = hasCenter;
        final Vector3d centerFinal = hasCenter ? new Vector3d(resolvedCenter) : null;
        final double strengthFinal = effectiveStrength;
        final double radiusFinal = effectiveRadius;
        final double exponentFinal = effectiveExponent;
        final double sdfStepFinal = effectiveSdfStep;

        VectorFieldData field = (point, dest) -> {
            boolean resolved = false;
            if (mode == PullMode.SURFACE_PULL) {
                if (fieldSdf != null) {
                    resolved = AttractorFieldUtils.vectorToSdfSurface(fieldSdf, point, sdfStepFinal, dest);
                }
                if (!resolved && fieldGeometry != null) {
                    resolved = AttractorFieldUtils.vectorToGeometrySurface(fieldGeometry, point, dest);
                }
            }
            if (!resolved) {
                if (!hasCenterFinal || centerFinal == null) {
                    dest.set(Double.NaN, Double.NaN, Double.NaN);
                    return;
                }
                dest.set(centerFinal).sub(point);
                resolved = true;
            }

            double lenSq = dest.lengthSquared();
            if (!resolved || lenSq <= AttractorFieldUtils.DISTANCE_SQUARED_EPS) {
                dest.zero();
                return;
            }
            double distance = Math.sqrt(lenSq);
            double weight = AttractorFieldUtils.falloff(distance, radiusFinal, exponentFinal, falloffMode);
            dest.mul((strengthFinal * weight) / distance);
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
