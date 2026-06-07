package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.blend_curves",
    displayName = "Blend Curves",
    description = "Creates a smooth transition curve between two curve, polyline, or line endpoints",
    category = "geometry.curves",
    order = 22
)
public class BlendCurvesNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    public enum Continuity {
        G0,
        G1
    }

    @NodeProperty(displayName = "Continuity", category = "Blend", order = 1)
    private Continuity continuity = Continuity.G1;

    @NodeProperty(displayName = "Reverse A", category = "Blend", order = 2)
    private boolean reverseA = false;

    @NodeProperty(displayName = "Reverse B", category = "Blend", order = 3)
    private boolean reverseB = false;

    @NodeProperty(displayName = "Default Length A", category = "Blend", order = 4)
    private double defaultLengthA = 1.0d;

    @NodeProperty(displayName = "Default Length B", category = "Blend", order = 5)
    private double defaultLengthB = 1.0d;

    @NodeProperty(displayName = "Default Segments", category = "Blend", order = 6)
    private int defaultSegments = 12;

    private static final String INPUT_CURVE_A_ID = "input_curve_a";
    private static final String INPUT_POLYLINE_A_ID = "input_polyline_a";
    private static final String INPUT_LINE_A_ID = "input_line_a";
    private static final String INPUT_CURVE_B_ID = "input_curve_b";
    private static final String INPUT_POLYLINE_B_ID = "input_polyline_b";
    private static final String INPUT_LINE_B_ID = "input_line_b";
    private static final String INPUT_LENGTH_A_ID = "input_length_a";
    private static final String INPUT_LENGTH_B_ID = "input_length_b";
    private static final String INPUT_SEGMENTS_ID = "input_segments";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_JOINED_POLYLINE_ID = "output_joined_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_START_POINT_ID = "output_start_point";
    private static final String OUTPUT_END_POINT_ID = "output_end_point";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BlendCurvesNode() {
        super(UUID.randomUUID(), "geometry.curves.blend_curves");

        addInputPort(new BasePort(INPUT_CURVE_A_ID, "Curve A", "First curve to blend from", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_A_ID, "Polyline A", "Fallback first polyline", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_A_ID, "Line A", "Fallback first line", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_CURVE_B_ID, "Curve B", "Second curve to blend to", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_B_ID, "Polyline B", "Fallback second polyline", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_B_ID, "Line B", "Fallback second line", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_LENGTH_A_ID, "Length A", "Tangent handle length from curve A endpoint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LENGTH_B_ID, "Length B", "Tangent handle length toward curve B endpoint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Number of segments used to sample the blend", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Blend Curve", "Sampled blend curve", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Blend Polyline", "Sampled blend polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_JOINED_POLYLINE_ID, "Joined Polyline", "Curve A, blend, and curve B as one sampled polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Blend points as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_START_POINT_ID, "Start Point", "Blend start point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_END_POINT_ID, "End Point", "Blend end point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the blend was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates a smooth transition curve between two curve, polyline, or line endpoints";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsA = resolvePath(INPUT_CURVE_A_ID, INPUT_POLYLINE_A_ID, INPUT_LINE_A_ID, reverseA);
        List<Vector3d> pointsB = resolvePath(INPUT_CURVE_B_ID, INPUT_POLYLINE_B_ID, INPUT_LINE_B_ID, reverseB);
        if (pointsA == null || pointsB == null || pointsA.size() < 2 || pointsB.size() < 2) {
            writeInvalid();
            return;
        }

        Vector3d start = new Vector3d(pointsA.getLast());
        Vector3d end = new Vector3d(pointsB.getFirst());
        Vector3d tangentA = endTangent(pointsA);
        Vector3d tangentB = startTangent(pointsB);
        if (start.distanceSquared(end) <= EPS * EPS || tangentA == null || tangentB == null) {
            writeInvalid();
            return;
        }

        int segments = Math.max(1, getInputInt(INPUT_SEGMENTS_ID, defaultSegments));
        double lengthA = Math.max(0.0d, getInputDouble(INPUT_LENGTH_A_ID, defaultLengthA));
        double lengthB = Math.max(0.0d, getInputDouble(INPUT_LENGTH_B_ID, defaultLengthB));
        List<Vector3d> blendPoints = continuity == Continuity.G0
            ? sampleLinear(start, end, segments)
            : sampleHermite(start, end, tangentA.mul(lengthA), tangentB.mul(lengthB), segments);

        PolylineData blendPolyline = toPolyline(blendPoints);
        Curve blendCurve = buildLinearCurve(toVec3dList(blendPoints));
        PolylineData joinedPolyline = toPolyline(buildJoinedPoints(pointsA, blendPoints, pointsB));
        if (blendPolyline == null || joinedPolyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_CURVE_ID, blendCurve);
        outputValues.put(OUTPUT_POLYLINE_ID, blendPolyline);
        outputValues.put(OUTPUT_JOINED_POLYLINE_ID, joinedPolyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(blendPoints));
        outputValues.put(OUTPUT_START_POINT_ID, start);
        outputValues.put(OUTPUT_END_POINT_ID, end);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public Continuity getContinuity() {
        return continuity;
    }

    public void setContinuity(Continuity continuity) {
        Continuity resolved = continuity == null ? Continuity.G1 : continuity;
        if (this.continuity != resolved) {
            this.continuity = resolved;
            markDirty();
        }
    }

    public void setContinuityString(String continuity) {
        if (continuity == null || continuity.isBlank()) {
            setContinuity(Continuity.G1);
            return;
        }
        try {
            setContinuity(Continuity.valueOf(continuity.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setContinuity(Continuity.G1);
        }
    }

    public boolean isReverseA() {
        return reverseA;
    }

    public void setReverseA(boolean reverseA) {
        if (this.reverseA != reverseA) {
            this.reverseA = reverseA;
            markDirty();
        }
    }

    public boolean isReverseB() {
        return reverseB;
    }

    public void setReverseB(boolean reverseB) {
        if (this.reverseB != reverseB) {
            this.reverseB = reverseB;
            markDirty();
        }
    }

    public double getDefaultLengthA() {
        return defaultLengthA;
    }

    public void setDefaultLengthA(double defaultLengthA) {
        double resolved = Math.max(0.0d, defaultLengthA);
        if (Double.compare(this.defaultLengthA, resolved) != 0) {
            this.defaultLengthA = resolved;
            markDirty();
        }
    }

    public double getDefaultLengthB() {
        return defaultLengthB;
    }

    public void setDefaultLengthB(double defaultLengthB) {
        double resolved = Math.max(0.0d, defaultLengthB);
        if (Double.compare(this.defaultLengthB, resolved) != 0) {
            this.defaultLengthB = resolved;
            markDirty();
        }
    }

    public int getDefaultSegments() {
        return defaultSegments;
    }

    public void setDefaultSegments(int defaultSegments) {
        int resolved = Math.max(1, defaultSegments);
        if (this.defaultSegments != resolved) {
            this.defaultSegments = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "continuity", continuity.name(),
            "reverseA", reverseA,
            "reverseB", reverseB,
            "defaultLengthA", defaultLengthA,
            "defaultLengthB", defaultLengthB,
            "defaultSegments", defaultSegments
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("continuity") instanceof String value) {
            setContinuityString(value);
        }
        if (map.get("reverseA") instanceof Boolean value) {
            setReverseA(value);
        }
        if (map.get("reverseB") instanceof Boolean value) {
            setReverseB(value);
        }
        if (map.get("defaultLengthA") instanceof Number value) {
            setDefaultLengthA(value.doubleValue());
        }
        if (map.get("defaultLengthB") instanceof Number value) {
            setDefaultLengthB(value.doubleValue());
        }
        if (map.get("defaultSegments") instanceof Number value) {
            setDefaultSegments(value.intValue());
        }
    }

    private @Nullable List<Vector3d> resolvePath(String curveId, String polylineId, String lineId, boolean reverse) {
        List<Vector3d> points = PathUtils.resolveVertices(
            inputValues.get(curveId),
            inputValues.get(polylineId),
            inputValues.get(lineId)
        );
        if (points == null || points.size() < 2) {
            return null;
        }
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            copy.add(new Vector3d(point));
        }
        if (reverse) {
            Collections.reverse(copy);
        }
        return copy;
    }

    private @Nullable Vector3d endTangent(List<Vector3d> points) {
        for (int i = points.size() - 2; i >= 0; i--) {
            Vector3d tangent = new Vector3d(points.getLast()).sub(points.get(i));
            if (tangent.lengthSquared() > EPS * EPS) {
                return tangent.normalize();
            }
        }
        return null;
    }

    private @Nullable Vector3d startTangent(List<Vector3d> points) {
        for (int i = 1; i < points.size(); i++) {
            Vector3d tangent = new Vector3d(points.get(i)).sub(points.getFirst());
            if (tangent.lengthSquared() > EPS * EPS) {
                return tangent.normalize();
            }
        }
        return null;
    }

    private List<Vector3d> sampleLinear(Vector3d start, Vector3d end, int segments) {
        List<Vector3d> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            points.add(new Vector3d(start).lerp(end, t));
        }
        return points;
    }

    private List<Vector3d> sampleHermite(Vector3d start, Vector3d end, Vector3d tangentA, Vector3d tangentB, int segments) {
        List<Vector3d> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double t2 = t * t;
            double t3 = t2 * t;
            double h00 = 2.0d * t3 - 3.0d * t2 + 1.0d;
            double h10 = t3 - 2.0d * t2 + t;
            double h01 = -2.0d * t3 + 3.0d * t2;
            double h11 = t3 - t2;
            points.add(new Vector3d(start).mul(h00)
                .add(new Vector3d(tangentA).mul(h10))
                .add(new Vector3d(end).mul(h01))
                .add(new Vector3d(tangentB).mul(h11)));
        }
        return points;
    }

    private List<Vector3d> buildJoinedPoints(List<Vector3d> pointsA, List<Vector3d> blendPoints, List<Vector3d> pointsB) {
        List<Vector3d> joined = new ArrayList<>(pointsA.size() + blendPoints.size() + pointsB.size());
        appendAllFar(joined, pointsA);
        appendAllFar(joined, blendPoints);
        appendAllFar(joined, pointsB);
        return joined;
    }

    private void appendAllFar(List<Vector3d> target, List<Vector3d> points) {
        for (Vector3d point : points) {
            if (target.isEmpty() || target.getLast().distanceSquared(point) > EPS * EPS) {
                target.add(new Vector3d(point));
            }
        }
    }

    private @Nullable PolylineData toPolyline(List<Vector3d> points) {
        if (points.size() < 2) {
            return null;
        }
        return PathUtils.createPolylineOrNull(toVec3dList(points));
    }

    private List<Vec3d> toVec3dList(List<Vector3d> points) {
        List<Vec3d> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            out.add(new Vec3d(point.x, point.y, point.z));
        }
        return out;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_JOINED_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_START_POINT_ID, null);
        outputValues.put(OUTPUT_END_POINT_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
