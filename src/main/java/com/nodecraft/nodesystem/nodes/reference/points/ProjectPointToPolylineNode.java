package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Finds the closest point on a polyline (or line) to a query point, including segment parameterization.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.project_to_polyline",
    displayName = "Project Point To Polyline",
    description = "Projects a point onto the closest location on a polyline or line segment",
    category = "reference.points",
    order = 15
)
public class ProjectPointToPolylineNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_PATH_ID = "input_path";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SEGMENT_INDEX_ID = "output_segment_index";
    private static final String OUTPUT_SEGMENT_T_ID = "output_segment_t";
    private static final String OUTPUT_ARC_LENGTH_ID = "output_arc_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProjectPointToPolylineNode() {
        super(UUID.randomUUID(), "reference.points.project_to_polyline");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Query point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to project onto (line, polyline, or curve)",
            NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Closest Point",
            "Closest point on the path as PointData",
            NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Closest Vector",
            "Closest point as Vector3d",
            NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance from the query point to the closest point",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_INDEX_ID, "Segment Index",
            "Start vertex index of the closest segment",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_T_ID, "Segment T",
            "Interpolation parameter along the closest segment (0..1)",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ARC_LENGTH_ID, "Arc Length",
            "Path length from the polyline start to the closest point along the polyline",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when projection succeeded",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Project Point To Polyline";
    }

    @Override
    public String getDescription() {
        return "Projects a point onto the closest location on a polyline or line segment";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d query = PointUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        List<Vector3d> verts = resolveVertices();
        if (!PointUtils.isFinite(query) || verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        int segCount = closed ? unique.size() : unique.size() - 1;
        double bestDistSq = Double.MAX_VALUE;
        Vector3d bestPoint = null;
        int bestSeg = 0;
        double bestT = 0.0d;

        for (int i = 0; i < segCount; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            SegmentClosest sc = closestOnSegment(query, a, b);
            if (sc.distSq < bestDistSq) {
                bestDistSq = sc.distSq;
                bestPoint = sc.closest;
                bestSeg = i;
                bestT = sc.t;
            }
        }

        if (bestPoint == null) {
            writeInvalid();
            return;
        }

        double arcLen = 0.0d;
        for (int i = 0; i < bestSeg; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            arcLen += a.distance(b);
        }
        Vector3d a = unique.get(bestSeg);
        Vector3d b = unique.get((bestSeg + 1) % unique.size());
        arcLen += a.distance(b) * bestT;

        outputValues.put(OUTPUT_POINT_ID, new PointData(new Vector3d(bestPoint)));
        outputValues.put(OUTPUT_VECTOR_ID, new Vector3d(bestPoint));
        outputValues.put(OUTPUT_DISTANCE_ID, Math.sqrt(bestDistSq));
        outputValues.put(OUTPUT_SEGMENT_INDEX_ID, bestSeg);
        outputValues.put(OUTPUT_SEGMENT_T_ID, bestT);
        outputValues.put(OUTPUT_ARC_LENGTH_ID, arcLen);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_VECTOR_ID, null);
        outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
        outputValues.put(OUTPUT_SEGMENT_INDEX_ID, -1);
        outputValues.put(OUTPUT_SEGMENT_T_ID, Double.NaN);
        outputValues.put(OUTPUT_ARC_LENGTH_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveVertices() {
        return PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
    }

    private static boolean isClosed(List<Vector3d> verts) {
        if (verts.size() < 3) {
            return false;
        }
        return verts.get(0).distance(verts.get(verts.size() - 1)) < 1.0e-6d;
    }

    private static SegmentClosest closestOnSegment(Vector3d p, Vector3d a, Vector3d b) {
        Vector3d ab = new Vector3d(b).sub(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < EPS * EPS) {
            double d = p.distanceSquared(a);
            return new SegmentClosest(new Vector3d(a), d, 0.0d);
        }
        double t = new Vector3d(p).sub(a).dot(ab) / abLenSq;
        double tClamped = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d closest = new Vector3d(a).lerp(b, tClamped);
        double distSq = p.distanceSquared(closest);
        return new SegmentClosest(closest, distSq, tClamped);
    }

    private record SegmentClosest(Vector3d closest, double distSq, double t) {
    }
}
