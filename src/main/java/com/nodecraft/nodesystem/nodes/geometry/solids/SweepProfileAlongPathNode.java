package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.sweep",
    displayName = "Sweep Profile Along Path",
    description = "Sweeps a polygon profile along a path with optional scale, rotation, close, and flip controls",
    category = "geometry.solids",
    order = 5
)
public class SweepProfileAlongPathNode extends BaseNode {

    @NodeProperty(displayName = "Orient To Path", category = "Sweep", order = 1)
    private boolean orientToPath = true;

    @NodeProperty(displayName = "Close Profile", category = "Sweep", order = 2)
    private boolean closeProfile = true;

    @NodeProperty(displayName = "Flip Profile", category = "Sweep", order = 3)
    private boolean flipProfile = false;

    @NodeProperty(displayName = "Start Scale", category = "Scale", order = 10)
    private double startScale = 1.0d;

    @NodeProperty(displayName = "End Scale", category = "Scale", order = 11)
    private double endScale = 1.0d;

    @NodeProperty(displayName = "Start Rotation Degrees", category = "Rotation", order = 20)
    private double startRotationDegrees = 0.0d;

    @NodeProperty(displayName = "End Rotation Degrees", category = "Rotation", order = 21)
    private double endRotationDegrees = 0.0d;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";
    private static final String INPUT_SCALE_VALUES_ID = "input_scale_values";
    private static final String INPUT_ROTATION_VALUES_ID = "input_rotation_values";

    private static final String OUTPUT_SPINE_POINTS_ID = "output_spine_points";
    private static final String OUTPUT_SECTION_PROFILES_ID = "output_section_profiles";
    private static final String OUTPUT_SECTION_PROFILES_TREE_ID = "output_section_profiles_tree";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_SECTION_PATHS_TREE_ID = "output_section_paths_tree";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_SECTION_POINTS_TREE_ID = "output_section_points_tree";
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
        addInputPort(new BasePort(INPUT_SCALE_VALUES_ID, "Scale Values", "Optional scale list sampled along the path", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ROTATION_VALUES_ID, "Rotation Values", "Optional rotation degrees list sampled along the path", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_SPINE_POINTS_ID, "Spine Points", "Resolved spine point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PROFILES_ID, "Section Profiles", "Polygon profiles generated along the path", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PROFILES_TREE_ID, "Section Profiles Tree", "Section profiles keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary polylines for each swept section", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Section paths keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all swept section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Section points keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of swept sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of swept sections along the spine", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and spine were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Sweeps a polygon profile along a path with optional scale, rotation, close, and flip controls";
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
        if (flipProfile) {
            baseUniquePoints = new ArrayList<>(baseUniquePoints);
            Collections.reverse(baseUniquePoints);
        }

        Vector3d profileOrigin = new Vector3d(profile.getCenter());
        List<Double> scaleValues = resolveNumberList(inputValues.get(INPUT_SCALE_VALUES_ID));
        List<Double> rotationValues = resolveNumberList(inputValues.get(INPUT_ROTATION_VALUES_ID));
        List<Object> sectionProfiles = new ArrayList<>(spinePoints.size());
        List<Object> sectionPaths = new ArrayList<>(spinePoints.size());
        List<Vector3d> allPoints = new ArrayList<>(baseUniquePoints.size() * spinePoints.size());
        List<List<Vector3d>> stripSections = new ArrayList<>(spinePoints.size());
        List<Boolean> sectionClosedFlags = new ArrayList<>(spinePoints.size());

        for (int i = 0; i < spinePoints.size(); i++) {
            Vector3d spinePoint = spinePoints.get(i);
            Vector3d tangent = SolidNodeUtils.computeTangent(spinePoints, i);
            SolidNodeUtils.Frame frame = orientToPath
                ? SolidNodeUtils.buildFrame(spinePoint, tangent)
                : SolidNodeUtils.Frame.identity(spinePoint);
            double t = spinePoints.size() <= 1 ? 0.0d : (double) i / (double) (spinePoints.size() - 1);
            double scale = resolveScalarAt(scaleValues, t, startScale, endScale);
            double rotationRadians = Math.toRadians(resolveScalarAt(rotationValues, t, startRotationDegrees, endRotationDegrees));

            List<Vector3d> uniqueSectionPoints = new ArrayList<>(baseUniquePoints.size());
            for (Vector3d profilePoint : baseUniquePoints) {
                Vector3d local = new Vector3d(profilePoint).sub(profileOrigin);
                local = transformLocalProfilePoint(local, scale, rotationRadians);
                Vector3d worldPoint = frame.transform(local);
                uniqueSectionPoints.add(worldPoint);
                allPoints.add(worldPoint);
            }

            if (closeProfile) {
                List<Vector3d> closedSectionPoints = new ArrayList<>(uniqueSectionPoints.size() + 1);
                closedSectionPoints.addAll(uniqueSectionPoints);
                closedSectionPoints.add(new Vector3d(uniqueSectionPoints.get(0)));

                PlaneData sectionPlane = new PlaneData(spinePoint, frame.zAxis());
                PolygonProfileData sectionProfile = new PolygonProfileData(closedSectionPoints, sectionPlane);
                sectionProfiles.add(sectionProfile);
                sectionPaths.add(sectionProfile.getBoundary());
            } else {
                sectionProfiles.add(SolidNodeUtils.createPolyline(uniqueSectionPoints, false));
                sectionPaths.add(SolidNodeUtils.createPolyline(uniqueSectionPoints, false));
            }
            stripSections.add(List.copyOf(uniqueSectionPoints));
            sectionClosedFlags.add(closeProfile);
        }

        SurfaceStripData surfaceStrip = new SurfaceStripData(stripSections, sectionClosedFlags);

        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.copyOf(spinePoints));
        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.copyOf(sectionProfiles));
        outputValues.put(OUTPUT_SECTION_PROFILES_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionProfiles));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.copyOf(allPoints));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(stripSections));
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

    public boolean isCloseProfile() {
        return closeProfile;
    }

    public void setCloseProfile(boolean closeProfile) {
        if (this.closeProfile != closeProfile) {
            this.closeProfile = closeProfile;
            markDirty();
        }
    }

    public boolean isFlipProfile() {
        return flipProfile;
    }

    public void setFlipProfile(boolean flipProfile) {
        if (this.flipProfile != flipProfile) {
            this.flipProfile = flipProfile;
            markDirty();
        }
    }

    public double getStartScale() {
        return startScale;
    }

    public void setStartScale(double startScale) {
        if (Double.compare(this.startScale, startScale) != 0) {
            this.startScale = startScale;
            markDirty();
        }
    }

    public double getEndScale() {
        return endScale;
    }

    public void setEndScale(double endScale) {
        if (Double.compare(this.endScale, endScale) != 0) {
            this.endScale = endScale;
            markDirty();
        }
    }

    public double getStartRotationDegrees() {
        return startRotationDegrees;
    }

    public void setStartRotationDegrees(double startRotationDegrees) {
        if (Double.compare(this.startRotationDegrees, startRotationDegrees) != 0) {
            this.startRotationDegrees = startRotationDegrees;
            markDirty();
        }
    }

    public double getEndRotationDegrees() {
        return endRotationDegrees;
    }

    public void setEndRotationDegrees(double endRotationDegrees) {
        if (Double.compare(this.endRotationDegrees, endRotationDegrees) != 0) {
            this.endRotationDegrees = endRotationDegrees;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("orientToPath", orientToPath);
        state.put("closeProfile", closeProfile);
        state.put("flipProfile", flipProfile);
        state.put("startScale", startScale);
        state.put("endScale", endScale);
        state.put("startRotationDegrees", startRotationDegrees);
        state.put("endRotationDegrees", endRotationDegrees);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("orientToPath") instanceof Boolean value) orientToPath = value;
        if (map.get("closeProfile") instanceof Boolean value) closeProfile = value;
        if (map.get("flipProfile") instanceof Boolean value) flipProfile = value;
        if (map.get("startScale") instanceof Number value) startScale = value.doubleValue();
        if (map.get("endScale") instanceof Number value) endScale = value.doubleValue();
        if (map.get("startRotationDegrees") instanceof Number value) startRotationDegrees = value.doubleValue();
        if (map.get("endRotationDegrees") instanceof Number value) endRotationDegrees = value.doubleValue();
        markDirty();
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveSpinePoints() {
        Object lineObj = inputValues.get(INPUT_LINE_ID);
        Object polylineObj = inputValues.get(INPUT_POLYLINE_ID);
        Object curveObj = inputValues.get(INPUT_CURVE_ID);
        Object pathPointsObj = inputValues.get(INPUT_PATH_POINTS_ID);

        return SolidNodeUtils.resolveSpinePoints(lineObj, polylineObj, curveObj, pathPointsObj);
    }

    private List<Double> resolveNumberList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Double> numbers = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                numbers.add(number.doubleValue());
            }
        }
        return List.copyOf(numbers);
    }

    private double resolveScalarAt(List<Double> values, double t, double start, double end) {
        if (values.isEmpty()) {
            return start + (end - start) * t;
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        double scaled = Math.max(0.0d, Math.min(1.0d, t)) * (values.size() - 1);
        int i = (int) Math.floor(scaled);
        int next = Math.min(values.size() - 1, i + 1);
        double localT = scaled - i;
        return values.get(i) + (values.get(next) - values.get(i)) * localT;
    }

    private Vector3d transformLocalProfilePoint(Vector3d local, double scale, double rotationRadians) {
        double scaledX = local.x * scale;
        double scaledY = local.y * scale;
        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        return new Vector3d(
            scaledX * cos - scaledY * sin,
            scaledX * sin + scaledY * cos,
            local.z * scale
        );
    }
}
