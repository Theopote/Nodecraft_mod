package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.sweep_from_points",
    displayName = "Sweep Point List Along Path",
    description = "Sweeps an ordered point profile along a path with optional scale, rotation, close, and flip controls",
    category = "geometry.solids",
    order = 6
)
public class SweepPointListAlongPathNode extends BaseNode {

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

    private static final String INPUT_PROFILE_POINTS_ID = "input_profile_points";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";
    private static final String INPUT_SCALE_VALUES_ID = "input_scale_values";
    private static final String INPUT_ROTATION_VALUES_ID = "input_rotation_values";

    private static final String OUTPUT_SPINE_POINTS_ID = "output_spine_points";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_SECTION_PATHS_TREE_ID = "output_section_paths_tree";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_SECTION_POINTS_TREE_ID = "output_section_points_tree";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_RAIL_SEGMENTS_TREE_ID = "output_rail_segments_tree";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SweepPointListAlongPathNode() {
        super(UUID.randomUUID(), "geometry.solids.sweep_from_points");

        addInputPort(new BasePort(INPUT_PROFILE_POINTS_ID, "Profile Points", "Ordered profile point list to sweep", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Optional line spine", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Optional polyline spine", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Optional curve spine", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points", "Optional ordered path point list fallback", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_SCALE_VALUES_ID, "Scale Values", "Optional scale list sampled along the path", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ROTATION_VALUES_ID, "Rotation Values", "Optional rotation degrees list sampled along the path", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_SPINE_POINTS_ID, "Spine Points", "Resolved spine point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "List of swept section polylines", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Section paths keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all swept section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Section points keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Line segments connecting corresponding section points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_TREE_ID, "Rail Segments Tree", "Rail segments grouped by source section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of swept sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of swept sections along the spine", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and spine were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Sweeps an ordered point profile along a path with optional scale, rotation, close, and flip controls";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_POINTS_ID);
        List<Vector3d> profilePoints = SolidNodeUtils.resolvePointList(profileObj);
        List<Vector3d> spinePoints = resolveSpinePoints();

        if (profilePoints.size() < 2 || spinePoints.size() < 2) {
            writeEmptyOutputs();
            return;
        }

        if (flipProfile) {
            profilePoints = new ArrayList<>(profilePoints);
            Collections.reverse(profilePoints);
        }

        Vector3d profileOrigin = SolidNodeUtils.computeCenter(profilePoints);
        List<Double> scaleValues = resolveNumberList(inputValues.get(INPUT_SCALE_VALUES_ID));
        List<Double> rotationValues = resolveNumberList(inputValues.get(INPUT_ROTATION_VALUES_ID));
        List<List<Vector3d>> sections = new ArrayList<>(spinePoints.size());
        List<Object> sectionPaths = new ArrayList<>(spinePoints.size());
        List<Vector3d> allPoints = new ArrayList<>(profilePoints.size() * spinePoints.size());

        for (int i = 0; i < spinePoints.size(); i++) {
            Vector3d spinePoint = spinePoints.get(i);
            Vector3d tangent = SolidNodeUtils.computeTangent(spinePoints, i);
            SolidNodeUtils.Frame frame = orientToPath
                ? SolidNodeUtils.buildFrame(spinePoint, tangent)
                : SolidNodeUtils.Frame.identity(spinePoint);
            double t = spinePoints.size() <= 1 ? 0.0d : (double) i / (double) (spinePoints.size() - 1);
            double scale = resolveScalarAt(scaleValues, t, startScale, endScale);
            double rotationRadians = Math.toRadians(resolveScalarAt(rotationValues, t, startRotationDegrees, endRotationDegrees));

            List<Vector3d> section = new ArrayList<>(profilePoints.size());
            for (Vector3d profilePoint : profilePoints) {
                Vector3d local = new Vector3d(profilePoint).sub(profileOrigin);
                local = transformLocalProfilePoint(local, scale, rotationRadians);
                Vector3d worldPoint = frame.transform(local);
                section.add(worldPoint);
                allPoints.add(worldPoint);
            }
            sections.add(section);
            sectionPaths.add(SolidNodeUtils.createPolyline(section, closeProfile));
        }

        List<LineData> railSegments = new ArrayList<>();
        List<List<LineData>> railSegmentRows = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            int segmentCount = Math.min(current.size(), next.size());
            List<LineData> row = new ArrayList<>(segmentCount);
            for (int pointIndex = 0; pointIndex < segmentCount; pointIndex++) {
                Vector3d start = current.get(pointIndex);
                Vector3d end = next.get(pointIndex);
                LineData rail = new LineData(
                    new Vec3d(start.x, start.y, start.z),
                    new Vec3d(end.x, end.y, end.z)
                );
                railSegments.add(rail);
                row.add(rail);
            }
            railSegmentRows.add(row);
        }

        List<Boolean> sectionClosedFlags = new ArrayList<>(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            sectionClosedFlags.add(closeProfile);
        }
        SurfaceStripData surfaceStrip = new SurfaceStripData(sections, sectionClosedFlags);

        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.copyOf(spinePoints));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.copyOf(allPoints));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(sections));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(railSegmentRows));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sections.size());
        outputValues.put(OUTPUT_VALID_ID, true);
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
        if (map.get("orientToPath") instanceof Boolean value) {
            setOrientToPath(value);
        }
        if (map.get("closeProfile") instanceof Boolean value) {
            setCloseProfile(value);
        }
        if (map.get("flipProfile") instanceof Boolean value) {
            setFlipProfile(value);
        }
        if (map.get("startScale") instanceof Number value) {
            setStartScale(value.doubleValue());
        }
        if (map.get("endScale") instanceof Number value) {
            setEndScale(value.doubleValue());
        }
        if (map.get("startRotationDegrees") instanceof Number value) {
            setStartRotationDegrees(value.doubleValue());
        }
        if (map.get("endRotationDegrees") instanceof Number value) {
            setEndRotationDegrees(value.doubleValue());
        }
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
        this.closeProfile = closeProfile;
        markDirty();
    }

    public boolean isFlipProfile() {
        return flipProfile;
    }

    public void setFlipProfile(boolean flipProfile) {
        this.flipProfile = flipProfile;
        markDirty();
    }

    public double getStartScale() {
        return startScale;
    }

    public void setStartScale(double startScale) {
        this.startScale = startScale;
        markDirty();
    }

    public double getEndScale() {
        return endScale;
    }

    public void setEndScale(double endScale) {
        this.endScale = endScale;
        markDirty();
    }

    public double getStartRotationDegrees() {
        return startRotationDegrees;
    }

    public void setStartRotationDegrees(double startRotationDegrees) {
        this.startRotationDegrees = startRotationDegrees;
        markDirty();
    }

    public double getEndRotationDegrees() {
        return endRotationDegrees;
    }

    public void setEndRotationDegrees(double endRotationDegrees) {
        this.endRotationDegrees = endRotationDegrees;
        markDirty();
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, DataTreeData.empty());
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
