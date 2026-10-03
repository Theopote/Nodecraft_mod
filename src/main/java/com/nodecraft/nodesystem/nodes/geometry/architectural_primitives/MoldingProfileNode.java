package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates decorative molding cross-section profiles on a plane or box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.molding_profile",
    displayName = "Molding Profile",
    description = "Generates decorative molding cross-section profiles",
    category = "geometry.architectural_primitives",
    order = 14
)
public class MoldingProfileNode extends BaseNode {

    private static final Set<String> PROFILE_TYPES = Set.of("flat", "step", "cove", "ogee", "bevel");

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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public MoldingProfileNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.molding_profile");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Optional box face used to derive the molding plane", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional explicit construction plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional profile center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PROFILE_TYPE_ID, "Profile Type", "flat, step, cove, ogee, or bevel", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Profile width along local X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Profile height along local Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Profile projection depth", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Curve segments used for rounded transitions", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Generated polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed profile points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid molding profile could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates decorative molding cross-section profiles (profile only; sweep/extrude separately)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ResolvedPlane resolved = resolvePlaneAndBasis();
        if (resolved == null) {
            writeInvalid("Face or Plane is required");
            return;
        }
        PlaneData plane = resolved.plane();
        Vector3d center = resolveCenter(plane);
        if (center == null) {
            writeInvalid("Center must be a finite Point when connected");
            return;
        }

        Basis basis = resolved.basis();
        if (basis == null) {
            writeInvalid("Plane normal must be a finite non-zero vector");
            return;
        }

        String profileType = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_PROFILE_TYPE_ID, "flat", PROFILE_TYPES);
        if (profileType == null) {
            writeInvalid("Profile Type must be one of: flat, step, cove, ogee, bevel");
            return;
        }
        Double width = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_WIDTH_ID, 0.5d);
        if (width == null) {
            writeInvalid("Width must be a positive finite number");
            return;
        }
        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 0.5d);
        if (height == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }
        Double depth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DEPTH_ID, 0.1d);
        if (depth == null) {
            writeInvalid("Depth must be a positive finite number");
            return;
        }
        Integer segments = ArchitecturalInputUtils.resolveOptionalBoundedExactInteger(
            this, INPUT_SEGMENTS_ID, 8, 4, GenerationLimits.MAX_ARCHITECTURAL_PROFILE_SEGMENTS);
        if (segments == null) {
            writeInvalid("Segments must be an exact integer in [4, "
                + GenerationLimits.MAX_ARCHITECTURAL_PROFILE_SEGMENTS + "]");
            return;
        }

        List<Vector3d> points = buildProfilePoints(center, basis, profileType, width, height, depth, segments);
        if (points.size() < 4) {
            writeInvalid("Could not generate molding profile for the given parameters");
            return;
        }

        PolygonProfileData profile = new PolygonProfileData(points, plane);
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_BOUNDARY_ID, PathUtils.createPolylineOrNull(PathUtils.toVec3dList(points, false)));
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<Vector3d> buildProfilePoints(
        Vector3d center,
        Basis basis,
        String profileType,
        double width,
        double height,
        double depth,
        int segments
    ) {
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

    private void addQuarterArc(
        List<Vector3d> points,
        Vector3d center,
        Basis basis,
        double cx,
        double cy,
        double radius,
        double startAngle,
        double endAngle,
        int segments
    ) {
        List<Vector3d> guide = new ArrayList<>(5);
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0d;
            double angle = startAngle + (endAngle - startAngle) * t;
            guide.add(new Vector3d(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius, 0.0d));
        }
        appendResampledLocalCurve(points, center, basis, guide, segments);
    }

    private void addSCurve(
        List<Vector3d> points,
        Vector3d center,
        Basis basis,
        double startX,
        double startY,
        double width,
        double height,
        int segments
    ) {
        List<Vector3d> guide = new ArrayList<>(5);
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0d;
            double eased = t * t * (3.0d - 2.0d * t);
            guide.add(new Vector3d(startX + width * t, startY + height * eased, 0.0d));
        }
        appendResampledLocalCurve(points, center, basis, guide, segments);
    }

    private void appendResampledLocalCurve(
        List<Vector3d> points,
        Vector3d center,
        Basis basis,
        List<Vector3d> guide,
        int segments
    ) {
        if (guide.size() < 2) {
            return;
        }
        double[] cumulative = PathUtils.buildCumulative(guide, false);
        if (cumulative == null) {
            return;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= 1.0e-9d) {
            return;
        }
        for (int i = 1; i <= segments; i++) {
            Vector3d sample = PathUtils.sampleAtDistance(guide, false, cumulative, total * i / (double) segments);
            points.add(localPoint(center, basis, sample.x, sample.y));
        }
    }

    private Vector3d localPoint(Vector3d center, Basis basis, double x, double y) {
        return new Vector3d(center).fma(x, basis.xAxis()).fma(y, basis.yAxis());
    }

    private @Nullable ResolvedPlane resolvePlaneAndBasis() {
        Object faceObj = getInput(INPUT_FACE_ID);
        if (faceObj instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                PlaneData plane = new PlaneData(frame.center(), frame.zAxis());
                Basis basis = new Basis(
                    new Vector3d(frame.xAxis()),
                    new Vector3d(frame.yAxis()),
                    new Vector3d(frame.zAxis()));
                return new ResolvedPlane(plane, basis);
            }
            if (ArchitecturalInputUtils.isConnected(this, INPUT_FACE_ID)) {
                return null;
            }
        } else if (ArchitecturalInputUtils.isConnected(this, INPUT_FACE_ID)) {
            return null;
        }

        PlaneData plane = null;
        if (ArchitecturalInputUtils.isConnected(this, INPUT_PLANE_ID)) {
            plane = OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
        } else {
            Object planeObj = getInput(INPUT_PLANE_ID);
            if (planeObj instanceof PlaneData value) {
                plane = value;
            }
        }
        if (plane == null) {
            return null;
        }
        return new ResolvedPlane(plane, createBasis(plane));
    }

    private @Nullable Vector3d resolveCenter(PlaneData plane) {
        if (ArchitecturalInputUtils.isConnected(this, INPUT_CENTER_ID)) {
            return ArchitecturalInputUtils.resolveRequiredPointData(this, INPUT_CENTER_ID);
        }
        if (getInput(INPUT_FACE_ID) instanceof BoxFaceData face) {
            return face.getCenter();
        }
        return plane.getPoint();
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
            xAxis = Math.abs(normal.x) < 0.99d
                ? new Vector3d(1.0d, 0.0d, 0.0d).cross(normal)
                : new Vector3d(0.0d, 1.0d, 0.0d).cross(normal);
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

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) {
    }

    private record ResolvedPlane(PlaneData plane, @Nullable Basis basis) {
    }
}
