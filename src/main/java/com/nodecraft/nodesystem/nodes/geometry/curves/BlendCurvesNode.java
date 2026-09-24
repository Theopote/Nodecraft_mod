package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.blend_curves",
    displayName = "Blend Paths",
    description = "Creates a smooth transition path between two path endpoints.",
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

    private static final String INPUT_PATH_A_ID = "input_path_a";
    private static final String INPUT_PATH_B_ID = "input_path_b";
    private static final String INPUT_LENGTH_A_ID = "input_length_a";
    private static final String INPUT_LENGTH_B_ID = "input_length_b";
    private static final String INPUT_SEGMENTS_ID = "input_segments";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_START_POINT_ID = "output_start_point";
    private static final String OUTPUT_END_POINT_ID = "output_end_point";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BlendCurvesNode() {
        super(UUID.randomUUID(), "geometry.curves.blend_curves");

        addInputPort(new BasePort(INPUT_PATH_A_ID, "Path A",
            "First path to blend from (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PATH_B_ID, "Path B",
            "Second path to blend to (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_LENGTH_A_ID, "Length A", "Tangent handle length from path A endpoint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LENGTH_B_ID, "Length B", "Tangent handle length toward path B endpoint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Number of segments used to sample the blend", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Sampled blend path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Blend points as point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_START_POINT_ID, "Start Point",
            "Blend start point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_POINT_ID, "End Point",
            "Blend end point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the blend was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates a smooth transition path between two path endpoints.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsA = resolvePathVertices(INPUT_PATH_A_ID, reverseA);
        List<Vector3d> pointsB = resolvePathVertices(INPUT_PATH_B_ID, reverseB);
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

        int segments = GenerationLimits.clampSegments(1, getInputInt(INPUT_SEGMENTS_ID, defaultSegments));
        double lengthA = Math.max(0.0d, getInputDouble(INPUT_LENGTH_A_ID, defaultLengthA));
        double lengthB = Math.max(0.0d, getInputDouble(INPUT_LENGTH_B_ID, defaultLengthB));
        List<Vector3d> blendPoints = continuity == Continuity.G0
            ? sampleLinear(start, end, segments)
            : sampleHermite(start, end, tangentA.mul(lengthA), tangentB.mul(lengthB), segments);

        PathData blendPath = PathUtils.toPathData(blendPoints);
        if (blendPath == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, blendPath);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(blendPoints));
        outputValues.put(OUTPUT_START_POINT_ID, new PointData(start));
        outputValues.put(OUTPUT_END_POINT_ID, new PointData(end));
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
        int resolved = GenerationLimits.clampSegments(1, defaultSegments);
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

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATH_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_START_POINT_ID, null);
        outputValues.put(OUTPUT_END_POINT_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
