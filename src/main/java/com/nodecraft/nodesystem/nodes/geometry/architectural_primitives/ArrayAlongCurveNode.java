package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Places repeated architectural elements along a curve or polyline path.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.array_along_curve",
    displayName = "Array Along Curve",
    description = "Places repeated columns, posts, or panels along a curve or polyline path",
    category = "geometry.architectural_primitives",
    order = 10
)
public class ArrayAlongCurveNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
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

    public ArrayAlongCurveNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.array_along_curve");

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Curve path to sample", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Polyline path fallback", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Line path fallback", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points", "Ordered path point list fallback", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Target sample count along the path", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Target spacing between samples", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ELEMENT_TYPE_ID, "Element Type", "box or cylinder", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Element width across the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Element height along world up", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Element depth along the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector used to stabilize frames", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the array elements", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of elements created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid array could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Places repeated columns, posts, or panels along a curve or polyline path";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pathPoints = resolvePathPoints();
        if (pathPoints.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = PathUtils.isClosed(pathPoints);
        List<Vector3d> unique = closed ? pathPoints.subList(0, pathPoints.size() - 1) : pathPoints;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            writeInvalid();
            return;
        }

        double total = cumulative[cumulative.length - 1];
        if (total <= EPSILON) {
            writeInvalid();
            return;
        }

        List<Double> sampleDistances = resolveSampleDistances(total);
        if (sampleDistances.isEmpty()) {
            writeInvalid();
            return;
        }

        String elementType = resolveElementType(inputValues.get(INPUT_ELEMENT_TYPE_ID));
        double width = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 0.3d);
        double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 1.0d);
        double depth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_DEPTH_ID), 0.3d);
        Vector3d up = resolveUpVector(inputValues.get(INPUT_UP_VECTOR_ID));

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
                elements.add(new BoxGeometryData(center, new Vector3d(width / 2.0d, height / 2.0d, depth / 2.0d), orientation, true));
            }
        }

        if (elements.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, new com.nodecraft.nodesystem.datatypes.CompositeGeometryData(elements));
        outputValues.put(OUTPUT_COUNT_ID, elements.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<Double> resolveSampleDistances(double total) {
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        Object spacingObj = inputValues.get(INPUT_SPACING_ID);

        int count = countObj instanceof Number number ? number.intValue() : -1;
        if (count >= 2) {
            count = GenerationLimits.clampPositiveCount(count);
        }
        double spacing = spacingObj instanceof Number number ? number.doubleValue() : 0.0d;

        List<Double> sampleDistances = new ArrayList<>();
        if (count >= 2) {
            for (int i = 0; i < count; i++) {
                sampleDistances.add(total * i / (double) (count - 1));
            }
            return sampleDistances;
        }

        if (spacing > EPSILON) {
            int maxInstances = GenerationLimits.clampSpacingInstanceCount(total, spacing);
            int emitted = 0;
            for (double d = 0.0d; d <= total + EPSILON && emitted < maxInstances; d += spacing) {
                sampleDistances.add(Math.min(d, total));
                emitted++;
            }
            if (emitted < maxInstances
                && (sampleDistances.isEmpty() || sampleDistances.get(sampleDistances.size() - 1) < total - EPSILON)) {
                sampleDistances.add(total);
            }
        }
        return sampleDistances;
    }

    private List<Vector3d> resolvePathPoints() {
        return PathUtils.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        ) != null
            ? PathUtils.resolveVertices(inputValues.get(INPUT_CURVE_ID), inputValues.get(INPUT_POLYLINE_ID), inputValues.get(INPUT_LINE_ID))
            : resolveFallbackPathPoints();
    }

    private List<Vector3d> resolveFallbackPathPoints() {
        Object pathPointsObj = inputValues.get(INPUT_PATH_POINTS_ID);
        if (!(pathPointsObj instanceof List<?> list)) {
            return List.of();
        }
        List<Vector3d> resolved = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof Vector3d vector) {
                resolved.add(new Vector3d(vector));
            } else if (entry instanceof Vec3d vec3d) {
                resolved.add(new Vector3d(vec3d.x, vec3d.y, vec3d.z));
            }
        }
        return resolved;
    }

    private String resolveElementType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(java.util.Locale.ROOT);
        }
        return "box";
    }

    private Vector3d resolveUpVector(Object value) {
        if (value instanceof Vector3d vector && vector.lengthSquared() > EPSILON) {
            return new Vector3d(vector).normalize();
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    private double wrapDistance(double value, double length) {
        if (length <= EPSILON) {
            return 0.0d;
        }
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}