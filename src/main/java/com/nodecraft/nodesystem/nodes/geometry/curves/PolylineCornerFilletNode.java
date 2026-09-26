package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Replaces interior corners of an open path with circular fillets lying in a plane.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.fillet_polyline_corners",
    displayName = "Fillet Path Corners",
    description = "Fillets interior corners of an open path with circular arcs in the work plane",
    category = "geometry.curves",
    order = 11
)
public class PolylineCornerFilletNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Arc Segments", category = "Fillet", order = 1,
        description = "Number of straight segments used to approximate each circular fillet")
    private int arcSegments = 8;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolylineCornerFilletNode() {
        super(UUID.randomUUID(), "geometry.curves.fillet_polyline_corners");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Open path whose interior corners will be filleted (closed paths are not supported)",
            NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Work plane containing the path",
            NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius",
            "Fillet radius (must be positive)",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Fillet path with circular arcs replacing sharp corners",
            NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a filleted path was produced",
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
        List<Vector3d> raw = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        if (raw == null || raw.size() < 3 || PathUtils.isClosed(raw)
                || !(planeObj instanceof PlaneData plane) || !(radiusObj instanceof Number radNum)) {
            writeInvalid();
            return;
        }
        double radius = radNum.doubleValue();
        if (!(radius > EPS)) {
            writeInvalid();
            return;
        }
        int segs = Math.min(64, Math.max(1, arcSegments));

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        List<Vector2d> pts = new ArrayList<>(raw.size());
        for (Vector3d v : raw) {
            Vector3d p3 = plane.projectPoint(new Vector3d(v));
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
        PolylineData polyline = PathUtils.createPolylineOrNull(out);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATH_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Vector2d> filletOpen(List<Vector2d> pts, double radius, int arcSegments) {
        int n = pts.size();
        if (n < 3) {
            return null;
        }

        List<Vector2d> out = new ArrayList<>();
        out.add(new Vector2d(pts.getFirst()));

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

            double cos = Polyline2DUtils.clamp(dirAb.dot(dirBc), -1.0d, 1.0d);
            double theta = Math.acos(cos);
            if (theta < 1.0e-4d || theta > Math.PI - 1.0e-3d) {
                Polyline2DUtils.appendIfFar(out, b);
                continue;
            }

            double tanHalf = Math.tan(theta * 0.5d);
            if (tanHalf < EPS) {
                Polyline2DUtils.appendIfFar(out, b);
                continue;
            }

            double lIdeal = radius / tanHalf;
            double edgeIn = b.distance(a);
            double edgeOut = c.distance(b);
            double l = Math.min(lIdeal, Math.min(edgeIn, edgeOut) * 0.49d);
            double rEff = l * tanHalf;
            if (l < EPS || rEff < EPS) {
                Polyline2DUtils.appendIfFar(out, b);
                continue;
            }

            Vector2d p1 = new Vector2d(b).sub(new Vector2d(dirAb).mul(l));
            Vector2d p2 = new Vector2d(b).add(new Vector2d(dirBc).mul(l));

            Vector2d bis = new Vector2d(dirBc).sub(dirAb);
            if (bis.lengthSquared() < EPS * EPS) {
                Polyline2DUtils.appendIfFar(out, b);
                continue;
            }
            bis.normalize();
            double sinHalf = Math.sin(theta * 0.5d);
            if (Math.abs(sinHalf) < EPS) {
                Polyline2DUtils.appendIfFar(out, b);
                continue;
            }
            double dCenter = rEff / sinHalf;
            Vector2d center = new Vector2d(b).add(new Vector2d(bis).mul(dCenter));

            double rActual = center.distance(p1);
            if (rActual < EPS) {
                Polyline2DUtils.appendIfFar(out, b);
                continue;
            }

            boolean ccw = Polyline2DUtils.cross2(dirAb, dirBc) > 0.0d;

            Polyline2DUtils.appendIfFar(out, p1);
            List<Vector2d> arc = Polyline2DUtils.sampleArc(center, rActual, p1, p2, ccw, arcSegments);
            for (int k = 1; k < arc.size(); k++) {
                Polyline2DUtils.appendIfFar(out, arc.get(k));
            }
        }

        Polyline2DUtils.appendIfFar(out, pts.get(n - 1));
        return out;
    }
}
