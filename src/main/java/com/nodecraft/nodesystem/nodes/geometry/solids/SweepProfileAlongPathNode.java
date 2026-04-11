package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.sweep",
    displayName = "Sweep Profile Along Path",
    description = "Sweeps a polygon profile along a path and emits section profiles plus a side surface strip",
    category = "geometry.solids"
)
public class SweepProfileAlongPathNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Orient To Path", category = "Sweep", order = 1)
    private boolean orientToPath = true;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";

    private static final String OUTPUT_SPINE_POINTS_ID = "output_spine_points";
    private static final String OUTPUT_SECTION_PROFILES_ID = "output_section_profiles";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SweepProfileAlongPathNode() {
        super(UUID.randomUUID(), "geometry.solids.sweep");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to sweep", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Optional line spine", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Optional polyline spine", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Optional curve spine", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points", "Optional ordered path point list fallback", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_SPINE_POINTS_ID, "Spine Points", "Resolved spine point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PROFILES_ID, "Section Profiles", "Polygon profiles generated along the path", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary polylines for each swept section", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all swept section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of swept sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of swept sections along the spine", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and spine were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Sweeps a polygon profile along a path and emits section profiles plus a side surface strip";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        List<Vector3d> spinePoints = resolveSpinePoints();

        if (!(profileObj instanceof PolygonProfileData profile) || spinePoints.size() < 2) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> baseUniquePoints = profile.getUniquePoints();
        if (baseUniquePoints.size() < 3) {
            writeEmptyOutputs();
            return;
        }

        Vector3d profileOrigin = new Vector3d(profile.getCenter());
        List<Object> sectionProfiles = new ArrayList<>(spinePoints.size());
        List<Object> sectionPaths = new ArrayList<>(spinePoints.size());
        List<Vector3d> allPoints = new ArrayList<>(baseUniquePoints.size() * spinePoints.size());
        List<List<Vector3d>> stripSections = new ArrayList<>(spinePoints.size());
        List<Boolean> sectionClosedFlags = new ArrayList<>(spinePoints.size());

        for (int i = 0; i < spinePoints.size(); i++) {
            Vector3d spinePoint = spinePoints.get(i);
            Vector3d tangent = computeTangent(spinePoints, i);
            Frame frame = orientToPath ? buildFrame(spinePoint, tangent) : Frame.identity(spinePoint);

            List<Vector3d> uniqueSectionPoints = new ArrayList<>(baseUniquePoints.size());
            for (Vector3d profilePoint : baseUniquePoints) {
                Vector3d local = new Vector3d(profilePoint).sub(profileOrigin);
                Vector3d worldPoint = frame.transform(local);
                uniqueSectionPoints.add(worldPoint);
                allPoints.add(worldPoint);
            }

            List<Vector3d> closedSectionPoints = new ArrayList<>(uniqueSectionPoints.size() + 1);
            closedSectionPoints.addAll(uniqueSectionPoints);
            closedSectionPoints.add(new Vector3d(uniqueSectionPoints.get(0)));

            PlaneData sectionPlane = new PlaneData(spinePoint, frame.zAxis);
            PolygonProfileData sectionProfile = new PolygonProfileData(closedSectionPoints, sectionPlane);

            sectionProfiles.add(sectionProfile);
            sectionPaths.add(sectionProfile.getBoundary());
            stripSections.add(List.copyOf(uniqueSectionPoints));
            sectionClosedFlags.add(true);
        }

        SurfaceStripData surfaceStrip = new SurfaceStripData(stripSections, sectionClosedFlags);

        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.copyOf(spinePoints));
        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.copyOf(sectionProfiles));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.copyOf(allPoints));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, stripSections.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public boolean isOrientToPath() {
        return orientToPath;
    }

    public void setOrientToPath(boolean orientToPath) {
        this.orientToPath = orientToPath;
        markDirty();
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveSpinePoints() {
        Object lineObj = inputValues.get(INPUT_LINE_ID);
        Object polylineObj = inputValues.get(INPUT_POLYLINE_ID);
        Object curveObj = inputValues.get(INPUT_CURVE_ID);
        Object pathPointsObj = inputValues.get(INPUT_PATH_POINTS_ID);

        List<Vector3d> spinePoints = new ArrayList<>();
        if (lineObj instanceof LineData line) {
            spinePoints.add(fromVec3d(line.getStart()));
            spinePoints.add(fromVec3d(line.getEnd()));
        } else if (polylineObj instanceof PolylineData polyline) {
            for (Vec3d point : polyline.getPoints()) {
                spinePoints.add(fromVec3d(point));
            }
        } else if (curveObj instanceof Curve curve) {
            for (Vec3d point : curve.getSamplePoints()) {
                spinePoints.add(fromVec3d(point));
            }
        } else if (pathPointsObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Vector3d vector) {
                    spinePoints.add(new Vector3d(vector));
                }
            }
        }
        return spinePoints;
    }

    private Vector3d fromVec3d(Vec3d point) {
        return new Vector3d(point.x, point.y, point.z);
    }

    private Vector3d computeTangent(List<Vector3d> points, int index) {
        Vector3d tangent;
        if (index == 0) {
            tangent = new Vector3d(points.get(1)).sub(points.get(0));
        } else if (index == points.size() - 1) {
            tangent = new Vector3d(points.get(index)).sub(points.get(index - 1));
        } else {
            tangent = new Vector3d(points.get(index + 1)).sub(points.get(index - 1));
        }
        if (tangent.lengthSquared() <= EPSILON) {
            return new Vector3d(0.0d, 0.0d, 1.0d);
        }
        return tangent.normalize();
    }

    private Frame buildFrame(Vector3d origin, Vector3d tangent) {
        Vector3d zAxis = new Vector3d(tangent);
        if (zAxis.lengthSquared() <= EPSILON) {
            return Frame.identity(origin);
        }
        zAxis.normalize();

        Vector3d referenceUp = Math.abs(zAxis.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d xAxis = referenceUp.cross(zAxis, new Vector3d());
        if (xAxis.lengthSquared() <= EPSILON) {
            referenceUp.set(0.0d, 0.0d, 1.0d);
            xAxis = referenceUp.cross(zAxis, new Vector3d());
        }
        if (xAxis.lengthSquared() <= EPSILON) {
            return Frame.identity(origin);
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(zAxis).cross(xAxis);
        if (yAxis.lengthSquared() <= EPSILON) {
            return Frame.identity(origin);
        }
        yAxis.normalize();

        return new Frame(origin, xAxis, yAxis, zAxis);
    }

    private record Frame(Vector3d origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        static Frame identity(Vector3d origin) {
            return new Frame(
                new Vector3d(origin),
                new Vector3d(1.0d, 0.0d, 0.0d),
                new Vector3d(0.0d, 1.0d, 0.0d),
                new Vector3d(0.0d, 0.0d, 1.0d)
            );
        }

        Vector3d transform(Vector3d local) {
            Vector3d result = new Vector3d(origin);
            result.add(new Vector3d(xAxis).mul(local.x));
            result.add(new Vector3d(yAxis).mul(local.y));
            result.add(new Vector3d(zAxis).mul(local.z));
            return result;
        }
    }
}
