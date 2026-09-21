package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.loft",
    displayName = "Loft Surface",
    description = "Lofts two polygon profiles into a SURFACE_STRIP (surface topology, not a solid). Auto-resamples when vertex counts differ.",
    category = "geometry.solids",
    order = 3
)
public class LoftProfilesNode extends BaseNode {

    @NodeProperty(displayName = "Auto Resample", category = "Compatibility", order = 10,
        description = "Resample both profiles to a shared point count when vertex counts differ")
    private boolean autoResample = true;

    @NodeProperty(displayName = "Target Section Points", category = "Compatibility", order = 11,
        description = "Target point count for auto resampling. Use 0 to use the larger profile count.")
    private int targetSectionPoints = 0;

    private static final String INPUT_SOURCE_PROFILE_ID = "input_source_profile";
    private static final String INPUT_TARGET_PROFILE_ID = "input_target_profile";

    private static final String OUTPUT_SOURCE_PROFILE_ID = "output_source_profile";
    private static final String OUTPUT_TARGET_PROFILE_ID = "output_target_profile";
    private static final String OUTPUT_SOURCE_POINTS_ID = "output_source_points";
    private static final String OUTPUT_TARGET_POINTS_ID = "output_target_points";
    private static final String OUTPUT_SECTION_POINTS_TREE_ID = "output_section_points_tree";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_RAIL_SEGMENTS_TREE_ID = "output_rail_segments_tree";
    private static final String OUTPUT_SIDE_SURFACE_ID = "output_side_surface";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LoftProfilesNode() {
        super(UUID.randomUUID(), "geometry.solids.loft");

        addInputPort(new BasePort(INPUT_SOURCE_PROFILE_ID, "Source Profile", "First polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_TARGET_PROFILE_ID, "Target Profile", "Second polygon profile", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_SOURCE_PROFILE_ID, "Source Profile", "Resolved source polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_PROFILE_ID, "Target Profile", "Resolved target polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_POINTS_ID, "Source Points", "Closed source polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_POINTS_ID, "Target Points", "Closed target polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Source and target section points keyed as {0} and {1}", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Segments connecting corresponding source and target vertices", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_TREE_ID, "Rail Segments Tree", "Loft rail segments keyed by rail index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface",
            "Primary output: lofted surface topology between the two profiles (not a solid)",
            NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of loft rails", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both profiles are compatible for lofting", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Lofts two polygon profiles into a SURFACE_STRIP (surface topology, not a solid). Auto-resamples when vertex counts differ.";
    }

    @Override
    public String getDisplayName() {
        return "Loft Surface";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sourceObj = inputValues.get(INPUT_SOURCE_PROFILE_ID);
        Object targetObj = inputValues.get(INPUT_TARGET_PROFILE_ID);

        if (!(sourceObj instanceof PolygonProfileData sourceProfile) || !(targetObj instanceof PolygonProfileData targetProfile)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> sourceUniquePoints = sourceProfile.getUniquePoints();
        List<Vector3d> targetUniquePoints = targetProfile.getUniquePoints();
        if (sourceUniquePoints.size() < 3 || targetUniquePoints.size() < 3) {
            writeEmptyOutputs();
            return;
        }

        if (sourceUniquePoints.size() != targetUniquePoints.size()) {
            if (!autoResample) {
                writeEmptyOutputs();
                return;
            }
            int targetCount = resolveTargetPointCount(sourceUniquePoints.size(), targetUniquePoints.size());
            sourceUniquePoints = SolidNodeUtils.resampleSection(sourceUniquePoints, targetCount, true);
            targetUniquePoints = SolidNodeUtils.resampleSection(targetUniquePoints, targetCount, true);
            if (sourceUniquePoints.size() < 3 || sourceUniquePoints.size() != targetUniquePoints.size()) {
                writeEmptyOutputs();
                return;
            }
            sourceProfile = rebuildProfile(sourceProfile, sourceUniquePoints);
            targetProfile = rebuildProfile(targetProfile, targetUniquePoints);
        }

        List<LineData> railSegments = new ArrayList<>(sourceUniquePoints.size());
        for (int i = 0; i < sourceUniquePoints.size(); i++) {
            Vector3d sourcePoint = sourceUniquePoints.get(i);
            Vector3d targetPoint = targetUniquePoints.get(i);
            railSegments.add(new LineData(
                new Vec3d(sourcePoint.x, sourcePoint.y, sourcePoint.z),
                new Vec3d(targetPoint.x, targetPoint.y, targetPoint.z)
            ));
        }

        SurfaceStripData sideSurface = new SurfaceStripData(
            List.of(sourceUniquePoints, targetUniquePoints),
            List.of(true, true)
        );

        outputValues.put(OUTPUT_SOURCE_PROFILE_ID, sourceProfile);
        outputValues.put(OUTPUT_TARGET_PROFILE_ID, targetProfile);
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, SpatialValueResolver.toPointDataList(sourceProfile.getClosedPoints()));
        outputValues.put(OUTPUT_TARGET_POINTS_ID, SpatialValueResolver.toPointDataList(targetProfile.getClosedPoints()));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(List.of(sourceUniquePoints, targetUniquePoints)));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, SolidDataTreeUtils.indexedValueTree(railSegments));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, sideSurface);
        outputValues.put(OUTPUT_COUNT_ID, railSegments.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public boolean isAutoResample() {
        return autoResample;
    }

    public void setAutoResample(boolean autoResample) {
        if (this.autoResample != autoResample) {
            this.autoResample = autoResample;
            markDirty();
        }
    }

    public int getTargetSectionPoints() {
        return targetSectionPoints;
    }

    public void setTargetSectionPoints(int targetSectionPoints) {
        int clamped = Math.max(0, targetSectionPoints);
        if (this.targetSectionPoints != clamped) {
            this.targetSectionPoints = clamped;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("autoResample", autoResample);
        state.put("targetSectionPoints", targetSectionPoints);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("autoResample") instanceof Boolean value) {
            autoResample = value;
        }
        if (map.get("targetSectionPoints") instanceof Number value) {
            targetSectionPoints = Math.max(0, value.intValue());
        }
        markDirty();
    }

    private int resolveTargetPointCount(int sourceCount, int targetCount) {
        if (targetSectionPoints >= 3) {
            return targetSectionPoints;
        }
        return Math.max(3, Math.max(sourceCount, targetCount));
    }

    private static PolygonProfileData rebuildProfile(PolygonProfileData original, List<Vector3d> uniquePoints) {
        List<Vector3d> closed = new ArrayList<>(uniquePoints.size() + 1);
        for (Vector3d point : uniquePoints) {
            closed.add(new Vector3d(point));
        }
        if (!closed.isEmpty()) {
            closed.add(new Vector3d(closed.getFirst()));
        }
        return new PolygonProfileData(closed, original.getPlane());
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SOURCE_PROFILE_ID, null);
        outputValues.put(OUTPUT_TARGET_PROFILE_ID, null);
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TARGET_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
