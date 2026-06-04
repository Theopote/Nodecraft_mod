package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.sweep_from_points",
    displayName = "Sweep Point List Along Path",
    description = "Sweeps an ordered point profile along a path and emits section paths plus connecting rail segments",
    category = "geometry.solids",
    order = 6
)
public class SweepPointListAlongPathNode extends BaseNode {

    @NodeProperty(displayName = "Orient To Path", category = "Sweep", order = 1)
    private boolean orientToPath = true;

    @NodeProperty(displayName = "Close Profile", category = "Sweep", order = 2)
    private boolean closeProfile = true;

    private static final String INPUT_PROFILE_POINTS_ID = "input_profile_points";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";

    private static final String OUTPUT_SPINE_POINTS_ID = "output_spine_points";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
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

        addOutputPort(new BasePort(OUTPUT_SPINE_POINTS_ID, "Spine Points", "Resolved spine point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "List of swept section polylines", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened list of all swept section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Line segments connecting corresponding section points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface made of swept sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of swept sections along the spine", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a profile and spine were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Sweeps an ordered point profile along a path and emits section paths plus connecting rail segments";
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

        Vector3d profileOrigin = new Vector3d(profilePoints.get(0));
        List<List<Vector3d>> sections = new ArrayList<>(spinePoints.size());
        List<Object> sectionPaths = new ArrayList<>(spinePoints.size());
        List<Vector3d> allPoints = new ArrayList<>(profilePoints.size() * spinePoints.size());

        for (int i = 0; i < spinePoints.size(); i++) {
            Vector3d spinePoint = spinePoints.get(i);
            Vector3d tangent = SolidNodeUtils.computeTangent(spinePoints, i);
            SolidNodeUtils.Frame frame = orientToPath
                ? SolidNodeUtils.buildFrame(spinePoint, tangent)
                : SolidNodeUtils.Frame.identity(spinePoint);

            List<Vector3d> section = new ArrayList<>(profilePoints.size());
            for (Vector3d profilePoint : profilePoints) {
                Vector3d local = new Vector3d(profilePoint).sub(profileOrigin);
                Vector3d worldPoint = frame.transform(local);
                section.add(worldPoint);
                allPoints.add(worldPoint);
            }
            sections.add(section);
            sectionPaths.add(SolidNodeUtils.createPolyline(section, closeProfile));
        }

        List<LineData> railSegments = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            int segmentCount = Math.min(current.size(), next.size());
            for (int pointIndex = 0; pointIndex < segmentCount; pointIndex++) {
                Vector3d start = current.get(pointIndex);
                Vector3d end = next.get(pointIndex);
                railSegments.add(new LineData(
                    new Vec3d(start.x, start.y, start.z),
                    new Vec3d(end.x, end.y, end.z)
                ));
            }
        }

        List<Boolean> sectionClosedFlags = new ArrayList<>(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            sectionClosedFlags.add(closeProfile);
        }
        SurfaceStripData surfaceStrip = new SurfaceStripData(sections, sectionClosedFlags);

        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.copyOf(spinePoints));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.copyOf(allPoints));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sections.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("orientToPath", orientToPath);
        state.put("closeProfile", closeProfile);
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

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SPINE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
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
}
