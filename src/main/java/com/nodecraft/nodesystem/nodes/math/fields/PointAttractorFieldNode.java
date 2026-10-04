package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.point_attractor_field",
    displayName = "Point Attractor Field",
    description = "Builds a vector field that pulls points toward a center with configurable distance falloff.",
    category = "math.fields",
    order = 8
)
public class PointAttractorFieldNode extends BaseNode {

    @NodeProperty(displayName = "Falloff", category = "Attractor", order = 1)
    private AttractorFieldUtils.FalloffMode falloff = AttractorFieldUtils.FalloffMode.INVERSE;

    @NodeProperty(displayName = "Strength", category = "Attractor", order = 2)
    private double strength = 1.0d;

    @NodeProperty(displayName = "Radius", category = "Attractor", order = 3)
    private double radius = 8.0d;

    @NodeProperty(displayName = "Exponent", category = "Attractor", order = 4)
    private double exponent = 2.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_EXPONENT_ID = "input_exponent";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public PointAttractorFieldNode() {
        super(UUID.randomUUID(), "math.fields.point_attractor_field");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Attractor center position", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Field strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Falloff radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "Falloff exponent override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Attractor vector field output", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a vector field that pulls points toward a center with configurable distance falloff.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = FieldSampleUtils.resolveFinitePoint(inputValues.get(INPUT_CENTER_ID));
        if (center == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        Double effectiveStrength = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_STRENGTH_ID, strength);
        Double effectiveRadius = FieldSampleUtils.resolveOptionalAttractorRadius(this, INPUT_RADIUS_ID, radius);
        Double effectiveExponent = FieldSampleUtils.resolveOptionalAttractorExponent(this, INPUT_EXPONENT_ID, exponent);
        if (effectiveStrength == null || effectiveRadius == null || effectiveExponent == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        AttractorFieldUtils.FalloffMode mode = falloff == null ? AttractorFieldUtils.FalloffMode.INVERSE : falloff;
        final Vector3d centerFinal = new Vector3d(center);
        final double strengthFinal = effectiveStrength;
        final double radiusFinal = effectiveRadius;
        final double exponentFinal = effectiveExponent;

        VectorFieldData field = (point, dest) -> {
            dest.set(centerFinal).sub(point);
            double lenSq = dest.lengthSquared();
            if (lenSq <= AttractorFieldUtils.DISTANCE_SQUARED_EPS) {
                dest.zero();
                return;
            }
            double distance = Math.sqrt(lenSq);
            double weight = AttractorFieldUtils.falloff(distance, radiusFinal, exponentFinal, mode);
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
