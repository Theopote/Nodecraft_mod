package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialTolerance;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
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
    displayName = "Path Attract Point List",
    description = "Pulls points toward a path with quadratic falloff; optional displacement along full vector, tangent only, or perpendicular-to-tangent only",
    category = "transform.deformations",
    order = 6
)
public class CurveAttractPointListNode extends BaseNode {

    public enum DisplacementMode {
        /** Straight toward the closest point on the path */
        TOWARD_POINT,
        /** Only the component perpendicular to the local path tangent */
        PERPENDICULAR,
        /** Only the component parallel to the local path tangent */
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
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveAttractPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.curve_attract");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to deform", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Target path (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Attraction strength override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Falloff radius override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Deformed point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Pulls points toward a path with quadratic falloff; optional displacement along full vector, tangent only, or perpendicular-to-tangent only";
    }

    @Override
    public String getDisplayName() {
        return "Path Attract Point List";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        List<Vector3d> poly = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        Double str = OptionalPortDrive.resolveOptionalDouble(this, INPUT_STRENGTH_ID, strength);
        Double rad = OptionalPortDrive.resolveOptionalDouble(this, INPUT_RADIUS_ID, radius);

        if (pointsInput == null || poly == null || poly.size() < 2 || str == null || rad == null || rad <= 0.0d) {
            writeInvalid();
            return;
        }
        if (str < 0.0d || str > 1.0d) {
            writeInvalid();
            return;
        }
        DisplacementMode mode = displacementMode == null ? DisplacementMode.TOWARD_POINT : displacementMode;

        List<Vector3d> out = new ArrayList<>(pointsInput.size());
        Vector3d closest = new Vector3d();
        Vector3d tangent = new Vector3d();
        for (Vector3d p : pointsInput) {
            boolean tangentResolved = closestPointAndRealTangent(poly, p, closest, tangent);
            double d = p.distance(closest);
            if (d >= rad) {
                out.add(new Vector3d(p));
                continue;
            }
            double w = (1.0d - d / rad);
            w *= w;

            Vector3d toCurve = new Vector3d(closest).sub(p);
            if (!tangentResolved && mode != DisplacementMode.TOWARD_POINT) {
                writeInvalid();
                return;
            }

            Vector3d delta;
            if (mode == DisplacementMode.TOWARD_POINT) {
                delta = new Vector3d(toCurve);
            } else {
                double along = toCurve.dot(tangent);
                Vector3d parallel = new Vector3d(tangent).mul(along);
                Vector3d perp = new Vector3d(toCurve).sub(parallel);
                delta = mode == DisplacementMode.PERPENDICULAR ? perp : parallel;
            }
            delta.mul(str * w);
            out.add(new Vector3d(p).add(delta));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    /**
     * Closest point on polyline; writes a real unit tangent only when the winning segment is non-degenerate.
     * @return true when {@code destTangent} is a usable path direction (no world-+X fallback)
     */
    private static boolean closestPointAndRealTangent(
            List<Vector3d> polyline,
            Vector3d query,
            Vector3d destClosest,
            Vector3d destTangent
    ) {
        double bestD2 = Double.POSITIVE_INFINITY;
        Vector3d best = new Vector3d(polyline.getFirst());
        Vector3d bestTan = null;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vector3d a = polyline.get(i);
            Vector3d b = polyline.get(i + 1);
            Vector3d ab = new Vector3d(b).sub(a);
            double ab2 = ab.lengthSquared();
            double t = ab2 < SpatialTolerance.EPS_SQ
                    ? 0.0d
                    : Math.max(0.0d, Math.min(1.0d, new Vector3d(query).sub(a).dot(ab) / ab2));
            Vector3d cand = new Vector3d(a).fma(t, ab);
            double d2 = cand.distanceSquared(query);
            if (d2 < bestD2) {
                bestD2 = d2;
                best.set(cand);
                if (ab2 >= SpatialTolerance.EPS_SQ) {
                    bestTan = new Vector3d(ab).normalize();
                } else {
                    bestTan = null;
                }
            }
        }
        destClosest.set(best);
        if (bestTan != null) {
            destTangent.set(bestTan);
            return true;
        }
        destTangent.set(0, 0, 0);
        return false;
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
        if (map.get("strength") instanceof Number value) {
            double v = value.doubleValue();
            if (Double.isFinite(v) && v >= 0.0d && v <= 1.0d) {
                strength = v;
            }
        }
        if (map.get("radius") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0d) {
            radius = value.doubleValue();
        }
        if (map.get("displacementMode") instanceof String value) {
            try {
                displacementMode = DisplacementMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                displacementMode = DisplacementMode.TOWARD_POINT;
            }
        }
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
