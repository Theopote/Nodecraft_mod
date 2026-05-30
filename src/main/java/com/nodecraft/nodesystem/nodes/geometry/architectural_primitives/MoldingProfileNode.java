package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates decorative molding cross-section profiles on a plane or box face.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.molding_profile",
    displayName = "Molding Profile",
    description = "Generates decorative molding cross-section profiles",
    category = "geometry.architectural_primitives",
    order = 13
)
public class MoldingProfileNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PROFILE_TYPE_ID = "input_profile_type";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_DEPTH_ID = "input_depth";
    private static final String INPUT_SEGMENTS_ID = "input_segments";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MoldingProfileNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.molding_profile");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Optional box face used to derive the molding plane", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional explicit construction plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional profile center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PROFILE_TYPE_ID, "Profile Type", "flat, step, cove, ogee, or bevel", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Profile width along local X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Profile height along local Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Profile projection depth", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Curve segments used for rounded transitions", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Generated polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed profile points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid molding profile could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates decorative molding cross-section profiles";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = resolvePlane();
        Vector3d center = resolveCenter();
        if (plane == null || center == null) {
            writeInvalid();
            return;
        }

        Basis basis = createBasis(plane);
        if (basis == null) {
            writeInvalid();
            return;
        }

        double width = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 0.5d);
        double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 0.5d);
        double depth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_DEPTH_ID), 0.1d);
        int segments = Math.max(4, ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_SEGMENTS_ID), 8));
        String profileType = resolveProfileType(inputValues.get(INPUT_PROFILE_TYPE_ID));

        List<Vector3d> points = buildProfilePoints(center, basis, profileType, width, height, depth, segments);
        if (points.size() < 4) {
            writeInvalid();
            return;
        }

        List<Vec3d> boundaryPoints = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            boundaryPoints.add(new Vec3d(point.x, point.y, point.z));
        }

        PolygonProfileData profile = new PolygonProfileData(points, plane);
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_BOUNDARY_ID, new PolylineData(boundaryPoints));
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<Vector3d> buildProfilePoints(Vector3d center, Basis basis, String profileType, double width, double height, double depth, int segments) {
        double halfWidth = width / 2.0d;
        double bottom = -height / 2.0d;
        double top = height / 2.0d;
        List<Vector3d> points = new ArrayList<>();

        switch (profileType) {
            case "step" -> {
                double step = Math.min(depth, width * 0.35d);
                points.add(localPoint(center, basis, -halfWidth, bottom));
                points.add(localPoint(center, basis, -halfWidth, bottom + step));
                points.add(localPoint(center, basis, -halfWidth + step, bottom + step));
                points.add(localPoint(center, basis, -halfWidth + step, top - step));
                points.add(localPoint(center, basis, halfWidth, top - step));
                points.add(localPoint(center, basis, halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, bottom));
            }
            case "cove" -> {
                points.add(localPoint(center, basis, -halfWidth, bottom));
                points.add(localPoint(center, basis, -halfWidth, bottom + depth));
                addQuarterArc(points, center, basis, -halfWidth + depth, bottom + depth, depth, Math.PI, Math.PI * 1.5d, segments);
                points.add(localPoint(center, basis, halfWidth - depth, top - depth));
                addQuarterArc(points, center, basis, halfWidth - depth, top - depth, depth, Math.PI * 1.5d, Math.PI * 2.0d, segments);
                points.add(localPoint(center, basis, -halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, bottom));
            }
            case "ogee" -> {
                points.add(localPoint(center, basis, -halfWidth, bottom));
                points.add(localPoint(center, basis, -halfWidth, bottom + depth * 0.35d));
                addSCurve(points, center, basis, -halfWidth, bottom + depth * 0.35d, width, height, segments);
                points.add(localPoint(center, basis, halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, bottom));
            }
            case "bevel" -> {
                double bevel = Math.min(depth, Math.min(width, height) * 0.25d);
                points.add(localPoint(center, basis, -halfWidth, bottom));
                points.add(localPoint(center, basis, -halfWidth + bevel, bottom));
                points.add(localPoint(center, basis, halfWidth, top - bevel));
                points.add(localPoint(center, basis, halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, bottom));
            }
            default -> {
                points.add(localPoint(center, basis, -halfWidth, bottom));
                points.add(localPoint(center, basis, halfWidth, bottom));
                points.add(localPoint(center, basis, halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, top));
                points.add(localPoint(center, basis, -halfWidth, bottom));
            }
        }

        if (!points.isEmpty()) {
            Vector3d first = points.getFirst();
            Vector3d last = points.getLast();
            if (!first.equals(last)) {
                points.add(new Vector3d(first));
            }
        }
        return points;
    }

    private void addQuarterArc(List<Vector3d> points, Vector3d center, Basis basis, double cx, double cy, double radius, double startAngle, double endAngle, int segments) {
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double angle = startAngle + (endAngle - startAngle) * t;
            double x = cx + Math.cos(angle) * radius;
            double y = cy + Math.sin(angle) * radius;
            points.add(localPoint(center, basis, x, y));
        }
    }

    private void addSCurve(List<Vector3d> points, Vector3d center, Basis basis, double startX, double startY, double width, double height, int segments) {
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double x = startX + width * t;
            double eased = t * t * (3.0d - 2.0d * t);
            double y = startY + height * eased;
            points.add(localPoint(center, basis, x, y));
        }
    }

    private Vector3d localPoint(Vector3d center, Basis basis, double x, double y) {
        return new Vector3d(center).fma(x, basis.xAxis()).fma(y, basis.yAxis());
    }

    private PlaneData resolvePlane() {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (faceObj instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                return new PlaneData(frame.center(), frame.zAxis());
            }
        }
        if (inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane) {
            return plane;
        }
        return null;
    }

    private Vector3d resolveCenter() {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        if (centerObj instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (centerObj instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        if (centerObj instanceof BoxFaceData face) {
            return face.getCenter();
        }
        if (inputValues.get(INPUT_FACE_ID) instanceof BoxFaceData face) {
            return face.getCenter();
        }
        if (inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane) {
            return plane.getPoint();
        }
        return null;
    }

    private Basis createBasis(PlaneData plane) {
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        normal.normalize();

        Vector3d reference = Math.abs(normal.z) < 0.99d ? new Vector3d(0.0d, 0.0d, 1.0d) : new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d xAxis = reference.sub(new Vector3d(normal).mul(reference.dot(normal)));
        if (xAxis.lengthSquared() <= 1.0e-12d) {
            xAxis = Math.abs(normal.x) < 0.99d ? new Vector3d(1.0d, 0.0d, 0.0d).cross(normal) : new Vector3d(0.0d, 1.0d, 0.0d).cross(normal);
        }
        if (xAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        yAxis.normalize();
        return new Basis(xAxis, yAxis, normal);
    }

    private String resolveProfileType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT);
        }
        return "flat";
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) { }
}