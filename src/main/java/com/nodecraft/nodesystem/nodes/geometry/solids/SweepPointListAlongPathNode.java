package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.SurfaceInputUtils;
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
    effect = NodeEffect.PURE,
    id = "geometry.solids.sweep_from_points",
    displayName = "Sweep Surface From Points",
    description = "Sweeps an ordered point profile along a path into a SURFACE_STRIP (surface topology, not a solid)",
    category = "geometry.solids",
    order = 9
)
public class SweepPointListAlongPathNode extends AbstractSolidNode {

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
    private static final String INPUT_PATH_ID = "input_path";
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

    public SweepPointListAlongPathNode() {
        super(UUID.randomUUID(), "geometry.solids.sweep_from_points");

        addInputPort(new BasePort(INPUT_PROFILE_POINTS_ID, "Profile Points", "Ordered profile point list to sweep", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Spine path (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_SCALE_VALUES_ID, "Scale Values", "Optional scale list sampled along the path", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_ROTATION_VALUES_ID, "Rotation Values", "Optional rotation degrees list sampled along the path", NodeDataType.DOUBLE_LIST, this));

        addOutputPort(new BasePort(OUTPUT_SPINE_POINTS_ID, "Spine Points", "Resolved spine point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Paths for each swept section", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Section paths keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all swept section points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Section points keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Line segments connecting corresponding section points", NodeDataType.LINE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_TREE_ID, "Rail Segments Tree", "Rail segments grouped by source section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip",
            "Primary output: swept surface topology (not a solid body)", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of swept sections along the spine", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and spine were resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Sweeps an ordered point profile along a path into a SURFACE_STRIP (surface topology, not a solid)";
    }

    @Override
    public String getDisplayName() {
        return "Sweep Surface From Points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> profilePoints = SolidNodeUtils.resolveStrictPointList(inputValues.get(INPUT_PROFILE_POINTS_ID));
        List<Vector3d> spinePoints = SolidNodeUtils.resolveSpinePoints(inputValues.get(INPUT_PATH_ID));

        if (profilePoints == null) {
            invalidate("Profile point list is missing or contains non-PointData / non-finite entries");
            return;
        }
        if (profilePoints.size() < 2) {
            invalidate("Profile point list requires at least two points");
            return;
        }
        if (spinePoints.size() < 2) {
            invalidate("Path is missing or requires at least two vertices");
            return;
        }
        if (!GenerationLimits.isWithinSurfaceSections(spinePoints.size())) {
            invalidate("Section count exceeds limit (" + GenerationLimits.MAX_SURFACE_SECTIONS + ")");
            return;
        }
        if (!SurfaceInputUtils.isWithinSurfaceWorkload(spinePoints.size(), profilePoints.size())) {
            invalidate("Sweep workload exceeds limit (" + GenerationLimits.MAX_SURFACE_TOTAL_POINTS + " total points)");
            return;
        }

        if (flipProfile) {
            profilePoints = new ArrayList<>(profilePoints);
            Collections.reverse(profilePoints);
        }

        List<Double> scaleValues = resolveScaleField(spinePoints.size());
        if (scaleValues == null) {
            return;
        }
        List<Double> rotationValues = resolveRotationField(spinePoints.size());
        if (rotationValues == null) {
            return;
        }

        Vector3d profileOrigin = SolidNodeUtils.computeCenter(profilePoints);
        List<Vector3d> localOffsets = PathFrameUtils.pointsToLocalOffsets(profilePoints, profileOrigin, null);
        List<List<Vector3d>> sections = new ArrayList<>(spinePoints.size());
        List<PathData> sectionPaths = new ArrayList<>(spinePoints.size());
        List<Vector3d> allPoints = new ArrayList<>(localOffsets.size() * spinePoints.size());

        List<PathFrameUtils.Frame> frames = orientToPath
            ? PathFrameUtils.framesAlongPolyline(spinePoints, null)
            : spinePoints.stream().map(PathFrameUtils.Frame::identity).toList();

        for (int i = 0; i < spinePoints.size(); i++) {
            PathFrameUtils.Frame frame = frames.get(i);
            double scale = scaleValues.get(i);
            double rotationRadians = Math.toRadians(rotationValues.get(i));

            List<Vector3d> section = new ArrayList<>(localOffsets.size());
            for (Vector3d localOffset : localOffsets) {
                Vector3d local = transformLocalProfilePoint(new Vector3d(localOffset), scale, rotationRadians);
                Vector3d worldPoint = frame.transform(local);
                section.add(worldPoint);
                allPoints.add(worldPoint);
            }
            sections.add(section);
            PathData sectionPath = SolidNodeUtils.toPath(SolidNodeUtils.createPolyline(section, closeProfile));
            if (sectionPath == null) {
                invalidate("Section path at index " + i + " is invalid");
                return;
            }
            sectionPaths.add(sectionPath);
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
        SurfaceStripData surfaceStrip;
        try {
            surfaceStrip = new SurfaceStripData(sections, sectionClosedFlags);
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Surface strip is invalid" : ex.getMessage());
            return;
        }
        String stripError = validateSurfaceStrip(surfaceStrip);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        outputValues.put(OUTPUT_SPINE_POINTS_ID, SpatialValueResolver.toPointDataList(spinePoints));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, SpatialValueResolver.toPointDataList(allPoints));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(sections));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(railSegmentRows));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sections.size());
        markSuccess();
    }

    private @Nullable List<Double> resolveScaleField(int sectionCount) {
        List<Double> connected = SurfaceInputUtils.resolveStrictFiniteDoubleList(inputValues.get(INPUT_SCALE_VALUES_ID));
        if (SurfaceInputUtils.isConnected(this, INPUT_SCALE_VALUES_ID) && connected == null) {
            invalidate("Scale values are connected but invalid (must be a finite double list)");
            return null;
        }
        if (connected != null && !connected.isEmpty()) {
            return SurfaceInputUtils.sampleFieldAlongU(connected, sectionCount, startScale);
        }
        return interpolateField(sectionCount, startScale, endScale);
    }

    private @Nullable List<Double> resolveRotationField(int sectionCount) {
        List<Double> connected = SurfaceInputUtils.resolveStrictFiniteDoubleList(inputValues.get(INPUT_ROTATION_VALUES_ID));
        if (SurfaceInputUtils.isConnected(this, INPUT_ROTATION_VALUES_ID) && connected == null) {
            invalidate("Rotation values are connected but invalid (must be a finite double list)");
            return null;
        }
        if (connected != null && !connected.isEmpty()) {
            return SurfaceInputUtils.sampleFieldAlongU(connected, sectionCount, startRotationDegrees);
        }
        return interpolateField(sectionCount, startRotationDegrees, endRotationDegrees);
    }

    private static List<Double> interpolateField(int sectionCount, double start, double end) {
        List<Double> values = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            double t = sectionCount <= 1 ? 0.0d : (double) i / (double) (sectionCount - 1);
            values.add(start + (end - start) * t);
        }
        return List.copyOf(values);
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
        markDirtyIfChanged(this.orientToPath, orientToPath);
        this.orientToPath = orientToPath;
    }

    public boolean isCloseProfile() {
        return closeProfile;
    }

    public void setCloseProfile(boolean closeProfile) {
        markDirtyIfChanged(this.closeProfile, closeProfile);
        this.closeProfile = closeProfile;
    }

    public boolean isFlipProfile() {
        return flipProfile;
    }

    public void setFlipProfile(boolean flipProfile) {
        markDirtyIfChanged(this.flipProfile, flipProfile);
        this.flipProfile = flipProfile;
    }

    public double getStartScale() {
        return startScale;
    }

    public void setStartScale(double startScale) {
        markDirtyIfChanged(this.startScale, startScale);
        this.startScale = startScale;
    }

    public double getEndScale() {
        return endScale;
    }

    public void setEndScale(double endScale) {
        markDirtyIfChanged(this.endScale, endScale);
        this.endScale = endScale;
    }

    public double getStartRotationDegrees() {
        return startRotationDegrees;
    }

    public void setStartRotationDegrees(double startRotationDegrees) {
        markDirtyIfChanged(this.startRotationDegrees, startRotationDegrees);
        this.startRotationDegrees = startRotationDegrees;
    }

    public double getEndRotationDegrees() {
        return endRotationDegrees;
    }

    public void setEndRotationDegrees(double endRotationDegrees) {
        markDirtyIfChanged(this.endRotationDegrees, endRotationDegrees);
        this.endRotationDegrees = endRotationDegrees;
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

    private void invalidate(String error) {
        putEmptyListOutputs(OUTPUT_SPINE_POINTS_ID, OUTPUT_SECTION_PATHS_ID, OUTPUT_ALL_POINTS_ID, OUTPUT_RAIL_SEGMENTS_ID);
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, DataTreeData.empty());
        putNullOutputs(OUTPUT_SURFACE_STRIP_ID);
        putIntOutputs(0, OUTPUT_SECTION_COUNT_ID);
        markInvalid(error);
    }
}
