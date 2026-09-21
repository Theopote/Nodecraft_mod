package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.PolylineClosestPoint3d;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.curve_attract",
    displayName = "Curve Attract Point List",
    description = "Pulls points toward a sampled curve with quadratic falloff; optional displacement along full vector, tangent only, or perpendicular-to-tangent only",
    category = "transform.deformations",
    order = 4
)
public class CurveAttractPointListNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    public enum DisplacementMode {
        /** Straight toward the closest point on the curve */
        TOWARD_POINT,
        /** Only the component perpendicular to the local curve tangent */
        PERPENDICULAR,
        /** Only the component parallel to the local curve tangent */
        TANGENTIAL
    }

    @NodeProperty(displayName = "Strength", category = "Attract", order = 1,
        description = "Blend toward the filtered displacement (0 keeps original, 1 applies full scaled delta)")
    private double strength = 0.5d;

    @NodeProperty(displayName = "Radius", category = "Attract", order = 2,
        description = "Maximum distance where attraction is non-zero")
    private double radius = 5.0d;

    @NodeProperty(displayName = "Displacement", category = "Attract", order = 3)
    private DisplacementMode displacementMode = DisplacementMode.TOWARD_POINT;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveAttractPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.curve_attract");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to deform", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Target curve (sampled internally)", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Attraction strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Falloff radius override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Deformed point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Pulls points toward a sampled curve with quadratic falloff; optional displacement along full vector, tangent only, or perpendicular-to-tangent only";
    }

    @Override
    public String getDisplayName() {
        return "Curve Attract Point List";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        Object curveObj = inputValues.get(INPUT_CURVE_ID);
        if (pointsInput.isEmpty() || !(curveObj instanceof Curve curve)) {
            writeEmpty();
            return;
        }

        List<Vector3d> poly = sampleCurvePolyline(curve);
        if (poly.size() < 2) {
            writeEmpty();
            return;
        }

        double str = resolveDouble(inputValues.get(INPUT_STRENGTH_ID), strength);
        double rad = Math.max(EPS, resolveDouble(inputValues.get(INPUT_RADIUS_ID), radius));
        if (!Double.isFinite(str) || !Double.isFinite(rad)) {
            writeEmpty();
            return;
        }
        str = Math.max(0.0d, Math.min(1.0d, str));
        DisplacementMode mode = displacementMode == null ? DisplacementMode.TOWARD_POINT : displacementMode;

        List<Vector3d> out = new ArrayList<>();
        Vector3d closest = new Vector3d();
        Vector3d tangent = new Vector3d();
        for (Vector3d p : pointsInput) {
            PolylineClosestPoint3d.closestPointAndTangent(poly, p, closest, tangent);
            double d = p.distance(closest);
            if (d >= rad) {
                out.add(new Vector3d(p));
                continue;
            }
            double w = (1.0d - d / rad);
            w *= w;

            Vector3d toCurve = new Vector3d(closest).sub(p);
            Vector3d t = new Vector3d(tangent);
            if (t.lengthSquared() < EPS) {
                t.set(1.0d, 0.0d, 0.0d);
            } else {
                t.normalize();
            }
            double along = toCurve.dot(t);
            Vector3d parallel = new Vector3d(t).mul(along);
            Vector3d perp = new Vector3d(toCurve).sub(parallel);

            Vector3d delta = switch (mode) {
                case TOWARD_POINT -> new Vector3d(toCurve);
                case PERPENDICULAR -> new Vector3d(perp);
                case TANGENTIAL -> new Vector3d(parallel);
            };
            delta.mul(str * w);
            out.add(new Vector3d(p).add(delta));
        }

        if (out.isEmpty()) {
            writeEmpty();
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("strength", strength);
        state.put("radius", radius);
        state.put("displacementMode", displacementMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        strength = DeformationUtils.finiteOrCurrent(map.get("strength"), strength);
        radius = Math.max(EPS, DeformationUtils.finiteOrCurrent(map.get("radius"), radius));
        if (map.get("displacementMode") instanceof String value) {
            try {
                displacementMode = DisplacementMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                displacementMode = DisplacementMode.TOWARD_POINT;
            }
        }
    }

    private static List<Vector3d> sampleCurvePolyline(Curve curve) {
        List<Vector3d> poly = new ArrayList<>();
        for (Vec3d v : curve.getSamplePoints()) {
            poly.add(new Vector3d(v.x, v.y, v.z));
        }
        return poly;
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static double resolveDouble(Object value, double fallback) {
        return DeformationUtils.resolveFiniteDouble(value, fallback);
    }

}
