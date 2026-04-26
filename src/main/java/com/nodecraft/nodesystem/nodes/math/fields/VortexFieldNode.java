package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
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
    id = "math.fields.vortex_field",
    displayName = "Vortex Field",
    description = "Builds a tangential swirl field around an axis, with radial falloff and clockwise/counter-clockwise control.",
    category = "math.fields",
    order = 11
)
public class VortexFieldNode extends BaseNode {

    @NodeProperty(displayName = "Falloff", category = "Vortex", order = 1)
    private AttractorFieldUtils.FalloffMode falloff = AttractorFieldUtils.FalloffMode.INVERSE;

    @NodeProperty(displayName = "Clockwise", category = "Vortex", order = 2)
    private boolean clockwise = false;

    @NodeProperty(displayName = "Strength", category = "Vortex", order = 3)
    private double strength = 1.0d;

    @NodeProperty(displayName = "Radius", category = "Vortex", order = 4)
    private double radius = 8.0d;

    @NodeProperty(displayName = "Exponent", category = "Vortex", order = 5)
    private double exponent = 2.0d;

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_EXPONENT_ID = "input_exponent";
    private static final String INPUT_CLOCKWISE_ID = "input_clockwise";
    private static final String OUTPUT_FIELD_ID = "output_field";

    public VortexFieldNode() {
        super(UUID.randomUUID(), "math.fields.vortex_field");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Axis origin point", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Axis direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Field strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Radial falloff radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "Falloff exponent override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CLOCKWISE_ID, "Clockwise", "Override swirl direction", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Vortex vector field output", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = FieldSampleUtils.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        Vector3d axis = FieldSampleUtils.resolvePoint(inputValues.get(INPUT_AXIS_ID));
        if (origin == null || axis == null || axis.lengthSquared() <= AttractorFieldUtils.EPS) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        Vector3d axisNorm = new Vector3d(axis).normalize();
        double effectiveStrength = getInputDouble(INPUT_STRENGTH_ID, strength);
        double effectiveRadius = Math.max(AttractorFieldUtils.EPS, getInputDouble(INPUT_RADIUS_ID, radius));
        double effectiveExponent = Math.max(0.001d, getInputDouble(INPUT_EXPONENT_ID, exponent));
        boolean effectiveClockwise = inputValues.get(INPUT_CLOCKWISE_ID) instanceof Boolean b ? b : clockwise;
        AttractorFieldUtils.FalloffMode mode = falloff == null ? AttractorFieldUtils.FalloffMode.INVERSE : falloff;

        VectorFieldData field = (point, dest) -> {
            Vector3d rel = new Vector3d(point).sub(origin);
            double axisDistance = rel.dot(axisNorm);
            Vector3d radial = rel.sub(new Vector3d(axisNorm).mul(axisDistance));
            double radialLenSq = radial.lengthSquared();
            if (radialLenSq <= AttractorFieldUtils.EPS) {
                dest.zero();
                return;
            }

            double radialLen = Math.sqrt(radialLenSq);
            Vector3d radialNorm = radial.mul(1.0d / radialLen);
            Vector3d tangent = new Vector3d(axisNorm).cross(radialNorm);
            if (effectiveClockwise) {
                tangent.negate();
            }
            double weight = AttractorFieldUtils.falloff(radialLen, effectiveRadius, effectiveExponent, mode);
            dest.set(tangent).mul(effectiveStrength * weight);
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
