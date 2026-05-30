package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.CurvePathSamplingUtil;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Replaces interior corners of an open polyline with circular fillets lying in a plane.
 */
@NodeInfo(
    id = "geometry.curves.fillet_polyline_corners",
    displayName = "Fillet Polyline Corners",
    description = "Fillets interior corners of an open polyline with circular arcs in the work plane",
    category = "geometry.curves",
    order = 11
)
public class PolylineCornerFilletNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Arc Segments", category = "Fillet", order = 1,
        description = "Number of straight segments used to approximate each circular fillet")
    private int arcSegments = 8;

    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolylineCornerFilletNode() {
        super(UUID.randomUUID(), "geometry.curves.fillet_polyline_corners");

        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Open polyline whose interior corners will be filleted (closed polylines are not supported)",
            NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Work plane containing the polyline",
            NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius",
            "Fillet radius (must be positive)",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "New polyline with circular fillets replacing sharp corners",
            NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a filleted polyline was produced",
            NodeDataType.BOOLEAN, this));
    }

    public int getArcSegments() {
        return arcSegments;
    }

    public void setArcSegments(int arcSegments) {
        int resolved = Math.max(1, arcSegments);
        if (this.arcSegments != resolved) {
            this.arcSegments = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("arcSegments", arcSegments);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("arcSegments") instanceof Number value) {
            setArcSegments(value.intValue());
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object polyObj = inputValues.get(INPUT_POLYLINE_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        if (!(polyObj instanceof PolylineData poly) || !(planeObj instanceof PlaneData plane) || !(radiusObj instanceof Number radNum)) {
            writeInvalid();
            return;
        }
        if (poly.isClosed()) {
            writeInvalid();
            return;
        }
        double radius = radNum.doubleValue();
        if (!(radius > EPS)) {
            writeInvalid();
            return;
        }
        int segs = Math.min(64, Math.max(1, arcSegments));

        List<Vec3d> raw = poly.getPoints();
        if (raw.size() < 3) {
            writeInvalid();
            return;
        }

        PolylineOffsetInPlaneNode.PlaneAxes axes = PolylineOffsetInPlaneNode.PlaneAxes.from(plane);
        List<Vector2d> pts = new ArrayList<>(raw.size());
        for (Vec3d v : raw) {
            Vector3d p3 = plane.projectPoint(new Vector3d(v.x, v.y, v.z));
            pts.add(axes.to2d(p3));
        }

        List<Vector2d> filleted = filletOpen(pts, radius, segs);
        if (filleted == null || filleted.size() < 2) {
            writeInvalid();
            return;
        }

        List<Vec3d> out = new ArrayList<>(filleted.size());
        for (Vector2d p : filleted) {
            Vector3d w = axes.from2d(p);
            out.add(new Vec3d(w.x, w.y, w.z));
        }
        PolylineData polyline = CurvePathSamplingUtil.createPolylineOrNull(out);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Vector2d> filletOpen(List<Vector2d> pts, double radius, int arcSegments) {
        int n = pts.size();
        if (n < 3) {
            return null;
        }

        List<Vector2d> out = new ArrayList<>();
        out.add(new Vector2d(pts.get(0)));

        for (int i = 1; i < n - 1; i++) {
            Vector2d a = pts.get(i - 1);
            Vector2d b = pts.get(i);
            Vector2d c = pts.get(i + 1);

            Vector2d dirAb = new Vector2d(b).sub(a);
            if (dirAb.lengthSquared() < EPS * EPS) {
                return null;
            }
            dirAb.normalize();
            Vector2d dirBc = new Vector2d(c).sub(b);
            if (dirBc.lengthSquared() < EPS * EPS) {
                return null;
            }
            dirBc.normalize();

            double cos = clamp(dirAb.dot(dirBc), -1.0d, 1.0d);
            double theta = Math.acos(cos);
            if (theta < 1.0e-4d || theta > Math.PI - 1.0e-3d) {
                appendIfFar(out, b);
                continue;
            }

            double tanHalf = Math.tan(theta * 0.5d);
            if (tanHalf < EPS) {
                appendIfFar(out, b);
                continue;
            }

            double lIdeal = radius / tanHalf;
            double edgeIn = b.distance(a);
            double edgeOut = c.distance(b);
            double l = Math.min(lIdeal, Math.min(edgeIn, edgeOut) * 0.49d);
            double rEff = l * tanHalf;
            if (l < EPS || rEff < EPS) {
                appendIfFar(out, b);
                continue;
            }

            Vector2d p1 = new Vector2d(b).sub(new Vector2d(dirAb).mul(l));
            Vector2d p2 = new Vector2d(b).add(new Vector2d(dirBc).mul(l));

            Vector2d bis = new Vector2d(dirBc).sub(dirAb);
            if (bis.lengthSquared() < EPS * EPS) {
                appendIfFar(out, b);
                continue;
            }
            bis.normalize();
            double sinHalf = Math.sin(theta * 0.5d);
            if (Math.abs(sinHalf) < EPS) {
                appendIfFar(out, b);
                continue;
            }
            double dCenter = rEff / sinHalf;
            Vector2d center = new Vector2d(b).add(new Vector2d(bis).mul(dCenter));

            double rActual = center.distance(p1);
            if (rActual < EPS) {
                appendIfFar(out, b);
                continue;
            }

            boolean ccw = cross2(dirAb, dirBc) > 0.0d;

            appendIfFar(out, p1);
            List<Vector2d> arc = sampleArc(center, rActual, p1, p2, ccw, arcSegments);
            for (int k = 1; k < arc.size(); k++) {
                appendIfFar(out, arc.get(k));
            }
        }

        appendIfFar(out, pts.get(n - 1));
        return out;
    }

    private static void appendIfFar(List<Vector2d> out, Vector2d p) {
        Vector2d last = out.get(out.size() - 1);
        if (last.distanceSquared(p) > EPS * EPS) {
            out.add(new Vector2d(p));
        }
    }

    private static List<Vector2d> sampleArc(Vector2d center, double radius,
                                           Vector2d p1, Vector2d p2, boolean ccw, int segments) {
        double a1 = Math.atan2(p1.y - center.y, p1.x - center.x);
        double a2 = Math.atan2(p2.y - center.y, p2.x - center.x);
        double delta = normalizeAngleDelta(a1, a2, ccw);
        List<Vector2d> arc = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double ang = a1 + delta * t;
            double x = center.x + Math.cos(ang) * radius;
            double y = center.y + Math.sin(ang) * radius;
            arc.add(new Vector2d(x, y));
        }
        return arc;
    }

    private static double normalizeAngleDelta(double a1, double a2, boolean ccw) {
        double d = a2 - a1;
        while (d <= -Math.PI) {
            d += Math.PI * 2.0d;
        }
        while (d > Math.PI) {
            d -= Math.PI * 2.0d;
        }
        if (ccw && d < 0.0d) {
            d += Math.PI * 2.0d;
        }
        if (!ccw && d > 0.0d) {
            d -= Math.PI * 2.0d;
        }
        return d;
    }

    private static double cross2(Vector2d a, Vector2d b) {
        return a.x * b.y - a.y * b.x;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
