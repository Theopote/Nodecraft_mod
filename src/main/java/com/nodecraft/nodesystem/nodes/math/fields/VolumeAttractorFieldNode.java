package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
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

    public VolumeAttractorFieldNode() {
        super(UUID.randomUUID(), "math.fields.volume_attractor_field");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry volume used by the attractor", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Optional SDF for accurate surface pull", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Field strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Falloff radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "Falloff exponent override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SDF_STEP_ID, "SDF Step", "Finite-difference step for SDF surface mode", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Volume attractor vector field output", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Vector3d center = FieldSampleUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));

        GeometryData geometry = geometryObj instanceof GeometryData data ? data : null;
        SignedDistanceFieldData sdf = AttractorFieldUtils.tryExtractSdf(sdfObj);
        if (sdf == null) {
            sdf = AttractorFieldUtils.tryExtractSdf(geometryObj);
        }

        Vector3d resolvedCenter = center != null ? new Vector3d(center) : new Vector3d();
        boolean hasCenter = center != null
            || AttractorFieldUtils.tryExtractCenter(geometryObj, resolvedCenter)
            || AttractorFieldUtils.tryExtractCenter(sdfObj, resolvedCenter);

        PullMode mode = pullMode == null ? PullMode.SURFACE_PULL : pullMode;
        if (!hasCenter && (mode == PullMode.CENTER_PULL || geometry == null && sdf == null)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        double effectiveStrength = getInputDouble(INPUT_STRENGTH_ID, strength);
        double effectiveRadius = Math.max(AttractorFieldUtils.EPS, getInputDouble(INPUT_RADIUS_ID, radius));
        double effectiveExponent = Math.max(0.001d, getInputDouble(INPUT_EXPONENT_ID, exponent));
        double effectiveSdfStep = Math.max(1.0e-4d, getInputDouble(INPUT_SDF_STEP_ID, sdfStep));
        AttractorFieldUtils.FalloffMode falloffMode = falloff == null ? AttractorFieldUtils.FalloffMode.INVERSE : falloff;

        final SignedDistanceFieldData fieldSdf = sdf;
        final GeometryData fieldGeometry = geometry;
        final Vector3d centerFinal = new Vector3d(resolvedCenter);

        VectorFieldData field = (point, dest) -> {
            Vector3d toTarget = new Vector3d();
            boolean resolved = false;
            if (mode == PullMode.SURFACE_PULL) {
                if (fieldSdf != null) {
                    resolved = AttractorFieldUtils.vectorToSdfSurface(fieldSdf, point, effectiveSdfStep, toTarget);
                }
                if (!resolved && fieldGeometry != null) {
                    resolved = AttractorFieldUtils.vectorToGeometrySurface(fieldGeometry, point, toTarget);
                }
            }
            if (!resolved) {
                toTarget.set(centerFinal).sub(point);
                resolved = true;
            }

            double lenSq = toTarget.lengthSquared();
            if (!resolved || lenSq <= AttractorFieldUtils.EPS) {
                dest.zero();
                return;
            }
            double distance = Math.sqrt(lenSq);
            double weight = AttractorFieldUtils.falloff(distance, effectiveRadius, effectiveExponent, falloffMode);
            dest.set(toTarget).mul((effectiveStrength * weight) / distance);
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
