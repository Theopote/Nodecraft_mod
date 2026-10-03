package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.nodes.transform.placement.PlaceGeometryOnFramesNode;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryStructureUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.PathSpacingPlan;
import com.nodecraft.nodesystem.util.SpatialTolerance;
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
public class CurveArrayNode extends AbstractPatternLinearNode {

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
    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_ORIGIN_TREE_ID = "output_origin_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public CurveArrayNode() {
        super(UUID.randomUUID(), "pattern.linear.curve_array");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy along the path", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "Local pivot point in the source geometry that maps to each path frame", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to sample (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total number of instances along the path. Overrides Spacing when connected.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Distance between instances when Count is not connected", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for path frames", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all path copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins", "Path frame origins used for each copy", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Path frames used for each copy", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_TREE_ID, "Origin Tree", "Path origins keyed by copy branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies along a curve using parallel-transport frames and placement";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeFail("Missing or invalid Geometry");
            return;
        }

        List<Vector3d> path = resolvePath();
        if (path == null || path.size() < 2) {
            writeFail("Missing or invalid Path");
            return;
        }

        PathUtils.ClosedVertices closedVerts = PathUtils.closedUniqueVertices(path);
        List<Vector3d> unique = closedVerts.vertices();
        boolean closed = closedVerts.closed();
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null || cumulative[cumulative.length - 1] <= SpatialTolerance.EPS) {
            writeFail("Path length is zero");
            return;
        }

        double total = cumulative[cumulative.length - 1];
        DistancePlan plan = resolveDistances(total, closed);
        if (plan == null) {
            return;
        }
        List<Double> distances = plan.distances();
        if (distances.isEmpty()) {
            writeFail("No sample distances resolved");
            return;
        }

        long maxInstances = GenerationLimits.MAX_GEOMETRY_INSTANCES;
        long sourceLeaves = GeometryStructureUtils.countLeavesBounded(geometry, maxInstances);
        if (sourceLeaves > maxInstances
            || sourceLeaves * (long) distances.size() > maxInstances) {
            writeFail("Array workload exceeds limit (source leaves × instances > MAX_GEOMETRY_INSTANCES)");
            return;
        }

        Vector3d pivot = OptionalPortDrive.resolveOptionalPoint(this, INPUT_PIVOT_ID, new Vector3d());
        if (pivot == null) {
            writeFail("Pivot connected but invalid");
            return;
        }

        Vector3d up = resolveOptionalUpVector(this, INPUT_UP_VECTOR_ID);
        if (up == null) {
            writeFail(OptionalPortDrive.isConnected(this, INPUT_UP_VECTOR_ID)
                ? "Up Vector connected but invalid or zero"
                : "Up Vector is invalid");
            return;
        }

        List<Vector3d> sampleOrigins = new ArrayList<>(distances.size());
        List<Vector3d> sampleTangents = new ArrayList<>(distances.size());
        for (double distance : distances) {
            Vector3d origin = PathUtils.sampleAtDistance(unique, closed, cumulative, distance);
            Vector3d tangent = PathUtils.sampleTangentAtDistance(unique, closed, cumulative, distance);
            if (tangent == null) {
                writeFail("Degenerate tangent at requested sample");
                return;
            }
            sampleOrigins.add(origin);
            sampleTangents.add(tangent);
        }

        List<FrameData> candidateFrames;
        if (orientToPath) {
            boolean requireUp = OptionalPortDrive.isConnected(this, INPUT_UP_VECTOR_ID);
            candidateFrames = PathFrameUtils.placementFramesFromSamples(
                sampleOrigins, sampleTangents, up, requireUp, closed);
            if (candidateFrames == null) {
                writeFail("Up Vector is parallel to path tangent");
                return;
            }
        } else {
            candidateFrames = identityFrames(sampleOrigins);
        }

        if (candidateFrames.size() != distances.size()) {
            writeFail("Failed to construct frames for all requested samples");
            return;
        }

        List<GeometryData> copies = new ArrayList<>(candidateFrames.size());
        List<Vector3d> origins = new ArrayList<>(candidateFrames.size());
        List<FrameData> frames = new ArrayList<>(candidateFrames.size());
        for (FrameData frame : candidateFrames) {
            GeometryData copy = PlaceGeometryOnFramesNode.placeOnFrame(geometry, pivot, frame);
            if (copy == null) {
                writeFail("Geometry placement failed");
                return;
            }
            copies.add(copy);
            origins.add(new Vector3d(frame.getOrigin()));
            frames.add(frame);
        }

        if (plan.requestedCount() >= 0 && copies.size() != plan.requestedCount()) {
            writeFail("Emitted count does not match requested Count");
            return;
        }

        writeSuccess(copies, origins, frames);
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

    /**
     * Resolves sample distances. Returns null when the node has already been marked invalid.
     * {@link DistancePlan#requestedCount()} is &gt;= 0 in Count mode, -1 in Spacing mode.
     */
    private @Nullable DistancePlan resolveDistances(double total, boolean closed) {
        if (OptionalPortDrive.isConnected(this, INPUT_COUNT_ID)) {
            Integer countValue = OptionalPortDrive.resolveOptionalInteger(this, INPUT_COUNT_ID, 0);
            if (countValue == null) {
                writeFail("Count connected but invalid");
                return null;
            }
            if (countValue <= 0) {
                writeFail("Count must be >= 1");
                return null;
            }
            if (countValue > GenerationLimits.MAX_GEOMETRY_INSTANCES) {
                writeFail("Count exceeds MAX_GEOMETRY_INSTANCES");
                return null;
            }
            return new DistancePlan(buildCountDistances(total, closed, countValue), countValue);
        }

        Double spacing = OptionalPortDrive.resolveOptionalDouble(this, INPUT_SPACING_ID, 0.0d);
        if (spacing == null) {
            writeFail("Spacing connected but invalid");
            return null;
        }
        if (!(spacing > 0.0d) || !Double.isFinite(spacing)) {
            writeFail("Spacing must be > 0 when Count is not connected");
            return null;
        }

        List<Double> distances = PathSpacingPlan.distances(
            total, closed, includeEnds, spacing, GenerationLimits.MAX_GEOMETRY_INSTANCES);
        if (distances == null || distances.isEmpty()) {
            writeFail("Spacing would exceed instance budget");
            return null;
        }
        return new DistancePlan(distances, -1);
    }

    private List<Double> buildCountDistances(double total, boolean closed, int count) {
        List<Double> distances = new ArrayList<>(count);
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

    private void writeFail(String error) {
        markInvalid(error);
        putNullOutputs(OUTPUT_GEOMETRY_ID);
        putEmptyListOutputs(OUTPUT_ORIGINS_ID, OUTPUT_FRAMES_ID);
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, new DataTreeData(List.of()));
        outputValues.put(OUTPUT_ORIGIN_TREE_ID, new DataTreeData(List.of()));
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    private void writeSuccess(List<GeometryData> copies, List<Vector3d> origins, List<FrameData> frames) {
        markSuccess();
        if (copies.isEmpty()) {
            putNullOutputs(OUTPUT_GEOMETRY_ID);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_ORIGINS_ID, SpatialValueResolver.toPointDataList(origins));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, buildTree(copies));
        outputValues.put(OUTPUT_ORIGIN_TREE_ID, buildTree(origins));
        putIntOutputs(copies.size(), OUTPUT_COUNT_ID);
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

    private record DistancePlan(List<Double> distances, int requestedCount) {
    }
}
