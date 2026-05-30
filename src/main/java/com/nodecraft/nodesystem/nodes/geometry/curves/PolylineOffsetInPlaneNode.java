package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Offsets a polyline (or line) within a reference plane using parallel segment offsets and miter joins.
 */
@NodeInfo(
    id = "geometry.curves.offset_polyline_plane",
    displayName = "Offset Polyline In Plane",
    description = "Offsets a polyline in a plane using parallel segments and miters (left is CCW in the plane UV basis)",
    category = "geometry.curves",
    order = 10
)
public class PolylineOffsetInPlaneNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 1,
        description = "Maximum miter extension factor relative to |offset| before falling back to a bevel corner")
    private double miterLimit = 4.0d;

    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolylineOffsetInPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.offset_polyline_plane");

        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Open or closed polyline to offset (projected into the plane)",
            NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Optional 2-point line when no polyline is connected",
            NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Work plane containing the polyline",
            NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset",
            "Signed offset distance in the plane (positive = left of forward segments in UV)",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "Offset polyline in 3D (still lying in the work plane)",
            NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when inputs resolved to a valid offset polyline",
            NodeDataType.BOOLEAN, this));
    }

    public double getMiterLimit() {
        return miterLimit;
    }

    public void setMiterLimit(double miterLimit) {
        double resolved = Math.max(0.0d, miterLimit);
        if (Double.compare(this.miterLimit, resolved) != 0) {
            this.miterLimit = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("miterLimit", miterLimit);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("miterLimit") instanceof Number value) {
            setMiterLimit(value.doubleValue());
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object offsetObj = inputValues.get(INPUT_OFFSET_ID);
        if (!(planeObj instanceof PlaneData plane) || !(offsetObj instanceof Number number)) {
            writeInvalid();
            return;
        }
        double offset = number.doubleValue();
        if (Math.abs(offset) < EPS) {
            writeInvalid();
            return;
        }

        List<Vector3d> worldVerts = resolvePathVertices();
        if (worldVerts == null || worldVerts.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = PathUtils.isClosed(worldVerts);
        List<Vector3d> unique = closed ? worldVerts.subList(0, worldVerts.size() - 1) : worldVerts;
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        List<Vector2d> pts2d = new ArrayList<>(unique.size());
        for (Vector3d p : unique) {
            Vector3d proj = plane.projectPoint(p);
            pts2d.add(axes.to2d(proj));
        }

        List<Vector2d> offset2d = offsetPolyline2d(pts2d, closed, offset, miterLimit);
        if (offset2d == null || offset2d.size() < 2) {
            writeInvalid();
            return;
        }

        List<Vec3d> outPts = new ArrayList<>(offset2d.size());
        for (Vector2d p : offset2d) {
            Vector3d w = axes.from2d(p);
            outPts.add(new Vec3d(w.x, w.y, w.z));
        }
        if (closed) {
            outPts.add(outPts.get(0));
        }

        PolylineData polyline = PathUtils.createPolylineOrNull(outPts);
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

    private List<Vector3d> resolvePathVertices() {
        return PathUtils.resolveVertices(
            null,
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }

    /**
     * Parallel offset for a 2D polyline. {@code pts} are unique vertices; {@code closed} means first/last should join.
     */
    private static List<Vector2d> offsetPolyline2d(List<Vector2d> pts, boolean closed, double offset, double miterLimit) {
        int n = pts.size();
        if (n < 2) {
            return null;
        }
        int segCount = closed ? n : n - 1;
        Vector2d[] left = new Vector2d[segCount];
        for (int i = 0; i < segCount; i++) {
            Vector2d a = pts.get(i);
            Vector2d b = pts.get((i + 1) % n);
            Vector2d d = new Vector2d(b).sub(a);
            double len = d.length();
            if (len < EPS) {
                return null;
            }
            d.mul(1.0d / len);
            Vector2d ln = new Vector2d(-d.y, d.x).mul(offset);
            left[i] = ln;
        }

        List<Vector2d> out = new ArrayList<>(n);
        if (!closed) {
            Vector2d start = new Vector2d(pts.get(0)).add(left[0]);
            out.add(start);
            for (int i = 1; i < n - 1; i++) {
                Vector2d corner = MiterJoinCalculator.intersectOrBevel(
                    pts.get(i - 1), pts.get(i), left[i - 1],
                    pts.get(i), pts.get(i + 1), left[i],
                    pts.get(i), miterLimit, offset);
                if (corner == null) {
                    return null;
                }
                out.add(corner);
            }
            Vector2d end = new Vector2d(pts.get(n - 1)).add(left[n - 2]);
            out.add(end);
            return out;
        }

        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int self = i;
            int next = (i + 1) % n;
            Vector2d corner = MiterJoinCalculator.intersectOrBevel(
                pts.get(prev), pts.get(self), left[prev],
                pts.get(self), pts.get(next), left[self],
                pts.get(self), miterLimit, offset);
            if (corner == null) {
                return null;
            }
            out.add(corner);
        }
        return out;
    }

    /**
     * Compatibility wrapper kept for nodes that still reference PlaneProjectionUtils.PlaneAxes.
     * New code should use PlaneProjectionUtils.PlaneAxes directly.
     */
    @Deprecated
    public static final class PlaneAxes {
        private final PlaneProjectionUtils.PlaneAxes delegate;

        private PlaneAxes(PlaneProjectionUtils.PlaneAxes delegate) {
            this.delegate = delegate;
        }

        public static PlaneAxes from(PlaneData plane) {
            return new PlaneAxes(PlaneProjectionUtils.PlaneAxes.from(plane));
        }

        public Vector2d to2d(Vector3d p) {
            return delegate.to2d(p);
        }

        public Vector3d from2d(Vector2d p) {
            return delegate.from2d(p);
        }
    }

}
