package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.PolylineOffsetInPlaneNode;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.profiles.offset_profile_plane",
    displayName = "Profile Offset In Plane",
    description = "Offsets a polygon profile in its plane by signed distance using 2D buffer logic",
    category = "geometry.profiles",
    order = 23
)
public class ProfileOffsetInPlaneNode extends BaseNode {
    @NodeProperty(displayName = "Quadrant Segments", category = "Offset", order = 1)
    private int quadrantSegments = 8;

    @NodeProperty(displayName = "Join Style", category = "Offset", order = 2)
    private String joinStyle = "ROUND";

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 3)
    private double miterLimit = 4.0d;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProfileOffsetInPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.offset_profile_plane");
        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Input polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed offset distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary offset profile (largest area)", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "All offset polygon profiles", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of offset profiles produced", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a polygon profile in its plane by signed distance using 2D buffer logic";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        Object offsetObj = inputValues.get(INPUT_OFFSET_ID);
        if (!(profileObj instanceof PolygonProfileData profile) || !(offsetObj instanceof Number number)) {
            writeInvalid();
            return;
        }

        double offset = number.doubleValue();
        if (!Double.isFinite(offset) || Math.abs(offset) < 1.0e-9d) {
            writeInvalid();
            return;
        }

        PlaneData plane = profile.getPlane();
        PolylineOffsetInPlaneNode.PlaneAxes axes = PolylineOffsetInPlaneNode.PlaneAxes.from(plane);
        GeometryFactory gf = new GeometryFactory();
        Polygon polygon = toJtsPolygon(profile, axes, gf);
        if (polygon == null) {
            writeInvalid();
            return;
        }

        BufferParameters params = new BufferParameters();
        params.setQuadrantSegments(Math.max(1, quadrantSegments));
        params.setJoinStyle(parseJoinStyle(joinStyle));
        params.setMitreLimit(Math.max(1.0d, miterLimit));

        Geometry out = BufferOp.bufferOp(polygon, offset, params);
        List<PolygonProfileData> profiles = new ArrayList<>();
        appendPolygons(out, axes, plane, profiles);
        if (profiles.isEmpty()) {
            writeInvalid();
            return;
        }

        PolygonProfileData primary = profiles.get(0);
        for (PolygonProfileData p : profiles) {
            if (Math.abs(area2d(p, axes)) > Math.abs(area2d(primary, axes))) {
                primary = p;
            }
        }

        outputValues.put(OUTPUT_PROFILE_ID, primary);
        outputValues.put(OUTPUT_PROFILES_ID, new ArrayList<>(profiles));
        outputValues.put(OUTPUT_COUNT_ID, profiles.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Polygon toJtsPolygon(PolygonProfileData profile, PolylineOffsetInPlaneNode.PlaneAxes axes, GeometryFactory gf) {
        List<Vector3d> closed = profile.getClosedPoints();
        if (closed.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            Vector2d uv = axes.to2d(closed.get(i));
            coords[i] = new Coordinate(uv.x, uv.y);
        }
        return gf.createPolygon(coords);
    }

    private void appendPolygons(Geometry geometry,
                                PolylineOffsetInPlaneNode.PlaneAxes axes,
                                PlaneData plane,
                                List<PolygonProfileData> out) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof Polygon polygon) {
            PolygonProfileData profile = toProfile(polygon, axes, plane);
            if (profile != null) {
                out.add(profile);
            }
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                appendPolygons(collection.getGeometryN(i), axes, plane, out);
            }
        }
    }

    private @Nullable PolygonProfileData toProfile(Polygon polygon,
                                                   PolylineOffsetInPlaneNode.PlaneAxes axes,
                                                   PlaneData plane) {
        Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
        if (coords.length < 4) {
            return null;
        }
        List<Vector3d> closed = new ArrayList<>(coords.length);
        for (Coordinate c : coords) {
            closed.add(axes.from2d(new Vector2d(c.x, c.y)));
        }
        try {
            return new PolygonProfileData(closed, plane);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private double area2d(PolygonProfileData profile, PolylineOffsetInPlaneNode.PlaneAxes axes) {
        List<Vector3d> pts = profile.getClosedPoints();
        double area2 = 0.0d;
        for (int i = 0; i < pts.size() - 1; i++) {
            Vector2d a = axes.to2d(pts.get(i));
            Vector2d b = axes.to2d(pts.get(i + 1));
            area2 += (a.x * b.y - b.x * a.y);
        }
        return area2 * 0.5d;
    }

    private int parseJoinStyle(String raw) {
        if (raw == null) {
            return BufferParameters.JOIN_ROUND;
        }
        return switch (raw.trim().toUpperCase()) {
            case "MITER", "MITRE" -> BufferParameters.JOIN_MITRE;
            case "BEVEL" -> BufferParameters.JOIN_BEVEL;
            default -> BufferParameters.JOIN_ROUND;
        };
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
