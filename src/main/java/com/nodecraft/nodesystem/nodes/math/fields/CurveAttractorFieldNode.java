package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.PolylineClosestPoint3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "math.fields.curve_attractor_field",
    displayName = "Curve Attractor Field",
    description = "Builds a vector field that pulls points toward the closest point on a sampled curve.",
    category = "math.fields",
    order = 9
)
public class CurveAttractorFieldNode extends BaseNode {

    @NodeProperty(displayName = "Falloff", category = "Attractor", order = 1)
    private AttractorFieldUtils.FalloffMode falloff = AttractorFieldUtils.FalloffMode.INVERSE;

    @NodeProperty(displayName = "Strength", category = "Attractor", order = 2)
    private double strength = 1.0d;

    @NodeProperty(displayName = "Radius", category = "Attractor", order = 3)
    private double radius = 8.0d;

    @NodeProperty(displayName = "Exponent", category = "Attractor", order = 4)
    private double exponent = 2.0d;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_EXPONENT_ID = "input_exponent";
    private static final String OUTPUT_FIELD_ID = "output_field";

    public CurveAttractorFieldNode() {
        super(UUID.randomUUID(), "math.fields.curve_attractor_field");

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Attractor curve", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Field strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Falloff radius override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "Falloff exponent override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Curve attractor vector field output", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object curveObj = inputValues.get(INPUT_CURVE_ID);
        if (!(curveObj instanceof Curve curve)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        List<Vector3d> polyline = AttractorFieldUtils.sampleCurvePolyline(curve);
        if (polyline.size() < 2) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        double effectiveStrength = getInputDouble(INPUT_STRENGTH_ID, strength);
        double effectiveRadius = Math.max(AttractorFieldUtils.EPS, getInputDouble(INPUT_RADIUS_ID, radius));
        double effectiveExponent = Math.max(0.001d, getInputDouble(INPUT_EXPONENT_ID, exponent));
        AttractorFieldUtils.FalloffMode mode = falloff == null ? AttractorFieldUtils.FalloffMode.INVERSE : falloff;

        VectorFieldData field = (point, dest) -> {
            Vector3d closest = new Vector3d();
            PolylineClosestPoint3d.closestPoint(polyline, point, closest);
            dest.set(closest).sub(point);
            double lenSq = dest.lengthSquared();
            if (lenSq <= AttractorFieldUtils.EPS) {
                dest.zero();
                return;
            }
            double distance = Math.sqrt(lenSq);
            double weight = AttractorFieldUtils.falloff(distance, effectiveRadius, effectiveExponent, mode);
            dest.mul((effectiveStrength * weight) / distance);
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
