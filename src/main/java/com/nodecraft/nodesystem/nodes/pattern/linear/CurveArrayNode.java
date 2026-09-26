package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.nodes.transform.placement.PlaceGeometryOnFramesNode;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.curve_array",
    displayName = "Curve Array",
    description = "Creates repeated geometry copies along a curve using parallel-transport frames and placement",
    category = "pattern.linear",
    order = 3
)
public class CurveArrayNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Orient To Path", category = "Array", order = 1)
    private boolean orientToPath = true;

    @NodeProperty(displayName = "Include Ends", category = "Array", order = 2)
    private boolean includeEnds = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PIVOT_ID = "input_pivot";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRIES_ID = "output_geometries";
    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_ORIGIN_TREE_ID = "output_origin_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveArrayNode() {
        super(UUID.randomUUID(), "pattern.linear.curve_array");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy along the path", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "Local pivot point in the source geometry that maps to each path frame", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to sample (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total number of instances along the path. Overrides Spacing when connected.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Distance between instances when Count is not set", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for path frames", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all path copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRIES_ID, "Geometries", "List of copied geometry values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins", "Path frame origins used for each copy", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Path frames used for each copy", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_TREE_ID, "Origin Tree", "Path origins keyed by copy branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the array was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies along a curve using parallel-transport frames and placement";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(List.of(), List.of(), List.of(), false);
            return;
        }

        List<Vector3d> path = resolvePath();
        if (path == null || path.size() < 2) {
            writeResult(List.of(), List.of(), List.of(), false);
            return;
        }

        PathUtils.ClosedVertices closedVerts = PathUtils.closedUniqueVertices(path);
        List<Vector3d> unique = closedVerts.vertices();
        boolean closed = closedVerts.closed();
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null || cumulative[cumulative.length - 1] <= EPS) {
            writeResult(List.of(), List.of(), List.of(), false);
            return;
        }

        double total = cumulative[cumulative.length - 1];
        List<Double> distances = resolveDistances(total, closed);
        if (distances.isEmpty()) {
            writeResult(List.of(), List.of(), List.of(), false);
            return;
        }

        Vector3d pivot = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_PIVOT_ID));
        if (!isFinite(pivot)) {
            pivot = new Vector3d();
        }
        Vector3d up = resolveDirection(inputValues.get(INPUT_UP_VECTOR_ID), new Vector3d(0.0d, 1.0d, 0.0d));

        List<Vector3d> sampleOrigins = new ArrayList<>(distances.size());
        List<Vector3d> sampleTangents = new ArrayList<>(distances.size());
        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);
        for (double distance : distances) {
            Vector3d origin = PathUtils.sampleAtDistance(unique, closed, cumulative, distance);
            double backDistance = closed ? wrapDistance(distance - delta, total) : Math.max(0.0d, distance - delta);
            double forwardDistance = closed ? wrapDistance(distance + delta, total) : Math.min(total, distance + delta);
            Vector3d prev = PathUtils.sampleAtDistance(unique, closed, cumulative, backDistance);
            Vector3d next = PathUtils.sampleAtDistance(unique, closed, cumulative, forwardDistance);
            Vector3d tangent = new Vector3d(next).sub(prev);
            if (tangent.lengthSquared() <= EPS) {
                continue;
            }
            tangent.normalize();
            sampleOrigins.add(origin);
            sampleTangents.add(tangent);
        }

        if (sampleOrigins.isEmpty()) {
            writeResult(List.of(), List.of(), List.of(), false);
            return;
        }

        List<FrameData> candidateFrames = orientToPath
            ? PathFrameUtils.placementFramesFromSamples(sampleOrigins, sampleTangents, up)
            : identityFrames(sampleOrigins);

        List<GeometryData> copies = new ArrayList<>(candidateFrames.size());
        List<Vector3d> origins = new ArrayList<>(candidateFrames.size());
        List<FrameData> frames = new ArrayList<>(candidateFrames.size());
        for (FrameData frame : candidateFrames) {
            GeometryData copy = PlaceGeometryOnFramesNode.placeOnFrame(geometry, pivot, frame);
            if (copy != null) {
                copies.add(copy);
                origins.add(new Vector3d(frame.getOrigin()));
                frames.add(frame);
            }
        }

        writeResult(copies, origins, frames, !copies.isEmpty());
    }

    private static List<FrameData> identityFrames(List<Vector3d> origins) {
        List<FrameData> frames = new ArrayList<>(origins.size());
        for (Vector3d origin : origins) {
            frames.add(new FrameData(
                origin,
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
            ));
        }
        return frames;
    }

    private @Nullable List<Vector3d> resolvePath() {
        List<Vector3d> points = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        return points == null ? null : List.copyOf(points);
    }

    private List<Double> resolveDistances(double total, boolean closed) {
        Integer countValue = inputValues.get(INPUT_COUNT_ID) instanceof Integer i ? i : null;
        double spacing = inputValues.get(INPUT_SPACING_ID) instanceof Number n ? n.doubleValue() : 0.0d;
        List<Double> distances = new ArrayList<>();
        if (countValue != null) {
            if (countValue <= 0) {
                return distances;
            }
            int count = GenerationLimits.clampPositiveGeometryInstanceCount(countValue);
            if (closed) {
                for (int i = 0; i < count; i++) {
                    distances.add(total * i / (double) count);
                }
            } else if (count == 1) {
                distances.add(includeEnds ? 0.0d : total * 0.5d);
            } else {
                int denominator = includeEnds ? count - 1 : count + 1;
                int start = includeEnds ? 0 : 1;
                int end = includeEnds ? count - 1 : count;
                for (int i = start; i <= end; i++) {
                    distances.add(total * i / (double) denominator);
                }
            }
            return distances;
        }
        if (spacing > EPS) {
            int maxInstances = GenerationLimits.clampGeometrySpacingInstanceCount(total, spacing);
            int emitted = 0;
            for (double d = includeEnds ? 0.0d : spacing; d <= total + EPS && emitted < maxInstances; d += spacing) {
                if (closed && d >= total - EPS) {
                    break;
                }
                distances.add(Math.min(d, total));
                emitted++;
            }
            if (!closed && includeEnds && emitted < maxInstances
                && (distances.isEmpty() || distances.getLast() < total - EPS)) {
                distances.add(total);
            }
        }
        return distances;
    }

    private static double wrapDistance(double value, double length) {
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }

    private static Vector3d resolveDirection(@Nullable Object value, Vector3d fallback) {
        Vector3d direction = SpatialValueResolver.resolveVector(value);
        if (!isFinite(direction) || direction.lengthSquared() <= EPS) {
            return fallback;
        }
        return direction.normalize();
    }

    private static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(List<GeometryData> copies, List<Vector3d> origins, List<FrameData> frames, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRIES_ID, List.copyOf(copies));
        if (copies.isEmpty()) {
            outputValues.put(OUTPUT_GEOMETRY_ID, null);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_ORIGINS_ID, SpatialValueResolver.toPointDataList(origins));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildTree(copies));
        outputValues.put(OUTPUT_ORIGIN_TREE_ID, buildTree(origins));
        outputValues.put(OUTPUT_COUNT_ID, copies.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private DataTreeData buildTree(List<?> values) {
        List<DataTreeData.Branch> branches = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(values.get(i))));
        }
        return new DataTreeData(branches);
    }

    public boolean isOrientToPath() {
        return orientToPath;
    }

    public void setOrientToPath(boolean orientToPath) {
        if (this.orientToPath != orientToPath) {
            this.orientToPath = orientToPath;
            markDirty();
        }
    }

    public boolean isIncludeEnds() {
        return includeEnds;
    }

    public void setIncludeEnds(boolean includeEnds) {
        if (this.includeEnds != includeEnds) {
            this.includeEnds = includeEnds;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of("orientToPath", orientToPath, "includeEnds", includeEnds);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("orientToPath") instanceof Boolean value) {
            setOrientToPath(value);
        }
        if (map.get("includeEnds") instanceof Boolean value) {
            setIncludeEnds(value);
        }
    }
}
