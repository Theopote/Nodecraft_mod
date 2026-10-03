package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Places repeated architectural elements along a curve or polyline path.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.array_along_curve",
    displayName = "Array Along Curve",
    description = "Places repeated columns, posts, or panels along a curve or polyline path",
    category = "geometry.architectural_primitives",
    order = 11
)
public class ArrayAlongCurveNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;
    private static final Set<String> ELEMENT_TYPES = Set.of("box", "cylinder");

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_ELEMENT_TYPE_ID = "input_element_type";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_DEPTH_ID = "input_depth";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ArrayAlongCurveNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.array_along_curve");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to array along (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points",
            "Fallback ordered point list when Path is unconnected", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Total instance count along the path", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Target spacing between samples when Count is unconnected", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ELEMENT_TYPE_ID, "Element Type", "box or cylinder", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Element width across the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Element height along world up", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Element depth along the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector used to stabilize frames", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the array elements", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of elements created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid array could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Places repeated columns, posts, or panels along a curve or polyline path";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pathPoints = resolvePathPoints();
        if (pathPoints == null) {
            return;
        }
        if (pathPoints.size() < 2) {
            writeInvalid("Path must contain at least 2 finite points");
            return;
        }

        boolean closed = PathUtils.isClosed(pathPoints);
        List<Vector3d> unique = closed ? pathPoints.subList(0, pathPoints.size() - 1) : pathPoints;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            writeInvalid("Unable to parameterize the path");
            return;
        }

        double total = cumulative[cumulative.length - 1];
        if (total <= EPSILON) {
            writeInvalid("Path length must be positive");
            return;
        }

        List<Double> sampleDistances = resolveSampleDistances(total, closed);
        if (sampleDistances == null) {
            return;
        }
        if (sampleDistances.isEmpty()) {
            writeInvalid("Connect Count (exact positive INTEGER) or Spacing (finite positive DOUBLE)");
            return;
        }

        String elementType = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_ELEMENT_TYPE_ID, "box", ELEMENT_TYPES);
        if (elementType == null) {
            writeInvalid("Element Type must be one of: box, cylinder");
            return;
        }
        Double width = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_WIDTH_ID, 0.3d);
        if (width == null) {
            writeInvalid("Width must be a finite positive DOUBLE");
            return;
        }
        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 1.0d);
        if (height == null) {
            writeInvalid("Height must be a finite positive DOUBLE");
            return;
        }
        Double depth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DEPTH_ID, 0.3d);
        if (depth == null) {
            writeInvalid("Depth must be a finite positive DOUBLE");
            return;
        }
        Vector3d up = OptionalPortDrive.resolveOptionalVector(this, INPUT_UP_VECTOR_ID, new Vector3d(0.0d, 1.0d, 0.0d));
        if (up == null || up.lengthSquared() <= EPSILON) {
            writeInvalid(ArchitecturalInputUtils.isConnected(this, INPUT_UP_VECTOR_ID)
                ? "Up Vector connected but invalid"
                : "Up Vector must be a non-zero finite VECTOR");
            return;
        }
        up = new Vector3d(up).normalize();

        List<GeometryData> elements = new ArrayList<>(sampleDistances.size());
        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);

        for (double distance : sampleDistances) {
            Vector3d origin = PathUtils.sampleAtDistance(unique, closed, cumulative, distance);
            double backDistance = closed ? wrapDistance(distance - delta, total) : Math.max(0.0d, distance - delta);
            double forwardDistance = closed ? wrapDistance(distance + delta, total) : Math.min(total, distance + delta);

            Vector3d prev = PathUtils.sampleAtDistance(unique, closed, cumulative, backDistance);
            Vector3d next = PathUtils.sampleAtDistance(unique, closed, cumulative, forwardDistance);
            Vector3d tangent = new Vector3d(next).sub(prev);
            if (tangent.lengthSquared() <= EPSILON) {
                continue;
            }
            tangent.normalize();

            Vector3d side = new Vector3d(tangent).cross(up);
            if (side.lengthSquared() <= EPSILON) {
                Vector3d fallbackUp = Math.abs(tangent.y) < 0.9d
                    ? new Vector3d(0.0d, 1.0d, 0.0d)
                    : new Vector3d(1.0d, 0.0d, 0.0d);
                side = new Vector3d(tangent).cross(fallbackUp);
            }
            if (side.lengthSquared() <= EPSILON) {
                continue;
            }
            side.normalize();

            Vector3d normal = new Vector3d(side).cross(tangent);
            if (normal.lengthSquared() <= EPSILON) {
                continue;
            }
            normal.normalize();

            Vector3d center = new Vector3d(origin).fma(height / 2.0d, normal);
            if ("cylinder".equals(elementType)) {
                Vector3d base = new Vector3d(center).fma(-height / 2.0d, normal);
                Vector3d top = new Vector3d(center).fma(height / 2.0d, normal);
                elements.add(new CylinderGeometryData(base, top, Math.min(width, depth) / 2.0d));
            } else {
                Matrix3d orientation = new Matrix3d(
                    side.x, normal.x, tangent.x,
                    side.y, normal.y, tangent.y,
                    side.z, normal.z, tangent.z
                );
                elements.add(new BoxGeometryData(
                    center, new Vector3d(width / 2.0d, height / 2.0d, depth / 2.0d), orientation, true));
            }
        }

        if (elements.isEmpty()) {
            writeInvalid("Unable to place array elements along the path");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(elements));
        outputValues.put(OUTPUT_COUNT_ID, elements.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable List<Vector3d> resolvePathPoints() {
        if (ArchitecturalInputUtils.isConnected(this, INPUT_PATH_ID)) {
            List<Vector3d> points = ArchitecturalInputUtils.resolveRequiredPathPoints(this, INPUT_PATH_ID);
            if (points == null) {
                writeInvalid("Path must be a finite PATH with at least 2 points");
                return null;
            }
            return points;
        }
        if (ArchitecturalInputUtils.isConnected(this, INPUT_PATH_POINTS_ID)) {
            List<Vector3d> points = PathUtils.resolvePath(inputValues.get(INPUT_PATH_POINTS_ID));
            if (points == null || points.size() < 2) {
                writeInvalid("Path Points must be a finite POINT_LIST with at least 2 points");
                return null;
            }
            for (Vector3d point : points) {
                if (!PointUtils.isFinite(point)) {
                    writeInvalid("Path Points must contain only finite points");
                    return null;
                }
            }
            return points;
        }
        writeInvalid("Connect Path or Path Points");
        return null;
    }

    private @Nullable List<Double> resolveSampleDistances(double total, boolean closed) {
        boolean spacingConnected = ArchitecturalInputUtils.isConnected(this, INPUT_SPACING_ID);
        boolean countConnected = ArchitecturalInputUtils.isConnected(this, INPUT_COUNT_ID);

        if (countConnected || !spacingConnected) {
            Integer count = ArchitecturalInputUtils.resolveOptionalBoundedExactInteger(
                this, INPUT_COUNT_ID, 2, 1, GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);
            if (count == null) {
                writeInvalid("Count must be an exact INTEGER between 1 and MAX_ARCHITECTURAL_INSTANCES ("
                    + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
                return null;
            }
            return ArchitecturalPathSupport.sampleDistancesEvenly(total, count, closed);
        }

        Double spacing = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_SPACING_ID, 1.0d);
        if (spacing == null || !(spacing > EPSILON)) {
            writeInvalid("Spacing must be a finite positive DOUBLE");
            return null;
        }

        List<Double> sampleDistances = ArchitecturalPathSupport.sampleDistancesBySpacing(
            total, spacing, closed, GenerationLimits.MAX_ARCHITECTURAL_INSTANCES);
        if (sampleDistances == null) {
            writeInvalid("Spacing-derived instance count exceeds MAX_ARCHITECTURAL_INSTANCES ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return null;
        }
        return sampleDistances;
    }

    private double wrapDistance(double value, double length) {
        if (length <= EPSILON) {
            return 0.0d;
        }
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
