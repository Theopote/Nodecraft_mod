package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.GeometryTransform;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "pattern.linear.curve_array_geometry",
    displayName = "Curve Array Geometry",
    description = "Creates repeated geometry copies along a curve, polyline, or line path with optional tangent orientation",
    category = "pattern.linear",
    order = 6
)
public class CurveArrayGeometryNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Orient To Path", category = "Array", order = 1)
    private boolean orientToPath = true;

    @NodeProperty(displayName = "Include Ends", category = "Array", order = 2)
    private boolean includeEnds = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PIVOT_ID = "input_pivot";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRIES_ID = "output_geometries";
    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_ORIGIN_TREE_ID = "output_origin_tree";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveArrayGeometryNode() {
        super(UUID.randomUUID(), "pattern.linear.curve_array_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to copy along the path", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "Local pivot point in the source geometry that maps to each path frame", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Curve path to sample", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Fallback polyline path", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Fallback line path", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of instances along the path. Overrides Spacing when >= 2.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Distance between instances when Count is not set", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for path frames", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all path copies", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRIES_ID, "Geometries", "List of copied geometry values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins", "Path frame origins used for each copy", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "One branch per emitted geometry copy", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_TREE_ID, "Origin Tree", "Path origins keyed by copy branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted geometry copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the array was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates repeated geometry copies along a curve, polyline, or line path with optional tangent orientation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(List.of(), List.of(), false);
            return;
        }

        List<Vector3d> path = resolvePath();
        if (path == null || path.size() < 2) {
            writeResult(List.of(), List.of(), false);
            return;
        }

        boolean closed = PathUtils.isClosed(path);
        List<Vector3d> unique = closed ? path.subList(0, path.size() - 1) : path;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null || cumulative[cumulative.length - 1] <= EPS) {
            writeResult(List.of(), List.of(), false);
            return;
        }

        double total = cumulative[cumulative.length - 1];
        List<Double> distances = resolveDistances(total);
        if (distances.isEmpty()) {
            writeResult(List.of(), List.of(), false);
            return;
        }

        Vector3d pivot = resolvePoint(inputValues.get(INPUT_PIVOT_ID));
        if (!isFinite(pivot)) {
            pivot = new Vector3d();
        }
        Vector3d up = resolveDirection(inputValues.get(INPUT_UP_VECTOR_ID), new Vector3d(0.0d, 1.0d, 0.0d));

        List<GeometryData> copies = new ArrayList<>(distances.size());
        List<Vector3d> origins = new ArrayList<>(distances.size());
        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);

        for (double distance : distances) {
            Vector3d origin = PathUtils.sampleAtDistance(unique, closed, cumulative, distance);
            Matrix3d rotation = orientToPath
                ? frameRotation(unique, closed, cumulative, total, distance, delta, up)
                : new Matrix3d().identity();
            if (rotation == null) {
                continue;
            }
            Vector3d rotatedPivot = new Vector3d(pivot);
            rotation.transform(rotatedPivot);
            Vector3d translation = new Vector3d(origin).sub(rotatedPivot);
            GeometryData copy = GeometryTransform.transform(geometry, translation, rotation, 1.0d);
            if (copy != null) {
                copies.add(copy);
                origins.add(origin);
            }
        }

        writeResult(copies, origins, !copies.isEmpty());
    }

    private @Nullable List<Vector3d> resolvePath() {
        List<Vector3d> points = PathUtils.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
        return points == null ? null : List.copyOf(points);
    }

    private List<Double> resolveDistances(double total) {
        int count = inputValues.get(INPUT_COUNT_ID) instanceof Number n ? n.intValue() : -1;
        double spacing = inputValues.get(INPUT_SPACING_ID) instanceof Number n ? n.doubleValue() : 0.0d;
        List<Double> distances = new ArrayList<>();
        if (count >= 2) {
            int denominator = includeEnds ? count - 1 : count + 1;
            int start = includeEnds ? 0 : 1;
            int end = includeEnds ? count - 1 : count;
            for (int i = start; i <= end; i++) {
                distances.add(total * i / (double) denominator);
            }
            return distances;
        }
        if (spacing > EPS) {
            for (double d = includeEnds ? 0.0d : spacing; d <= total + EPS; d += spacing) {
                distances.add(Math.min(d, total));
            }
            if (includeEnds && (distances.isEmpty() || distances.getLast() < total - EPS)) {
                distances.add(total);
            }
        }
        return distances;
    }

    private @Nullable Matrix3d frameRotation(List<Vector3d> unique,
                                             boolean closed,
                                             double[] cumulative,
                                             double total,
                                             double distance,
                                             double delta,
                                             Vector3d up) {
        double backDistance = closed ? wrapDistance(distance - delta, total) : Math.max(0.0d, distance - delta);
        double forwardDistance = closed ? wrapDistance(distance + delta, total) : Math.min(total, distance + delta);
        Vector3d prev = PathUtils.sampleAtDistance(unique, closed, cumulative, backDistance);
        Vector3d next = PathUtils.sampleAtDistance(unique, closed, cumulative, forwardDistance);
        Vector3d tangent = new Vector3d(next).sub(prev);
        if (tangent.lengthSquared() <= EPS) {
            return null;
        }
        tangent.normalize();

        Vector3d binormal = new Vector3d(tangent).cross(up);
        if (binormal.lengthSquared() <= EPS) {
            Vector3d fallbackUp = Math.abs(tangent.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
            binormal = new Vector3d(tangent).cross(fallbackUp);
        }
        if (binormal.lengthSquared() <= EPS) {
            return null;
        }
        binormal.normalize();
        Vector3d normal = new Vector3d(binormal).cross(tangent).normalize();

        return new Matrix3d(
            tangent.x, normal.x, binormal.x,
            tangent.y, normal.y, binormal.y,
            tangent.z, normal.z, binormal.z
        );
    }

    private static double wrapDistance(double value, double length) {
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }

    private static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        if (value instanceof PointData pointData) return new Vector3d(pointData.getPosition());
        if (value instanceof Vector3d vector) return new Vector3d(vector);
        if (value instanceof Vec3d vector) return new Vector3d(vector.x, vector.y, vector.z);
        if (value instanceof BlockPos pos) return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        return null;
    }

    private static Vector3d resolveDirection(@Nullable Object value, Vector3d fallback) {
        Vector3d direction = resolvePoint(value);
        if (!isFinite(direction) || direction.lengthSquared() <= EPS) {
            return fallback;
        }
        return direction.normalize();
    }

    private static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(List<GeometryData> copies, List<Vector3d> origins, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRIES_ID, List.copyOf(copies));
        outputValues.put(OUTPUT_GEOMETRY_ID, copies.isEmpty() ? null : new CompositeGeometryData(copies));
        outputValues.put(OUTPUT_ORIGINS_ID, List.copyOf(origins));
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
