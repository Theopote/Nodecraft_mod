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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.profiles.boolean_2d",
    displayName = "Profile Boolean 2D",
    description = "Performs 2D boolean operations (union/intersection/difference) on two polygon profiles in a shared plane",
    category = "geometry.profiles",
    order = 24
)
public class ProfileBoolean2DNode extends BaseNode {
    @NodeProperty(displayName = "Operation", category = "Boolean", order = 1)
    private String operation = "UNION";

    private static final String INPUT_A_ID = "input_profile_a";
    private static final String INPUT_B_ID = "input_profile_b";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProfileBoolean2DNode() {
        super(UUID.randomUUID(), "geometry.profiles.boolean_2d");
        addInputPort(new BasePort(INPUT_A_ID, "Profile A", "First polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_B_ID, "Profile B", "Second polygon profile", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary output profile (largest area)", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "All output profiles", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output profiles", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when boolean operation produced output", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Performs 2D boolean operations (union/intersection/difference) on two polygon profiles in a shared plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);
        if (!(aObj instanceof PolygonProfileData a) || !(bObj instanceof PolygonProfileData b)) {
            writeInvalid();
            return;
        }

        PlaneData plane = a.getPlane();
        PolylineOffsetInPlaneNode.PlaneAxes axes = PolylineOffsetInPlaneNode.PlaneAxes.from(plane);
        GeometryFactory gf = new GeometryFactory();

        Polygon pa = toJtsPolygon(a, axes, gf);
        Polygon pb = toJtsPolygonProjecting(b, plane, axes, gf);
        if (pa == null || pb == null) {
            writeInvalid();
            return;
        }

        Geometry out = switch (parseOperation(operation)) {
            case INTERSECTION -> pa.intersection(pb);
            case DIFFERENCE -> pa.difference(pb);
            case UNION -> pa.union(pb);
        };

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

    private Operation parseOperation(String raw) {
        if (raw == null) {
            return Operation.UNION;
        }
        return switch (raw.trim().toUpperCase()) {
            case "INTERSECTION" -> Operation.INTERSECTION;
            case "DIFFERENCE" -> Operation.DIFFERENCE;
            default -> Operation.UNION;
        };
    }

    private Polygon toJtsPolygon(PolygonProfileData profile,
                                 PolylineOffsetInPlaneNode.PlaneAxes axes,
                                 GeometryFactory gf) {
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

    private Polygon toJtsPolygonProjecting(PolygonProfileData profile,
                                           PlaneData targetPlane,
                                           PolylineOffsetInPlaneNode.PlaneAxes axes,
                                           GeometryFactory gf) {
        List<Vector3d> closed = profile.getClosedPoints();
        if (closed.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            Vector3d projected = targetPlane.projectPoint(closed.get(i));
            Vector2d uv = axes.to2d(projected);
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
            PolygonProfileData p = toProfile(polygon, axes, plane);
            if (p != null) {
                out.add(p);
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

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private enum Operation {
        UNION,
        INTERSECTION,
        DIFFERENCE
    }
}
