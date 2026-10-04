package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.SurfaceInputUtils;
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
    description = "Lofts two polygon profiles into a SURFACE_STRIP (surface topology, not a solid). Resamples when vertex counts differ per Match Sections mode.",
    category = "geometry.solids",
    order = 5
)
public class LoftProfilesNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Match Sections", category = "Compatibility", order = 10,
        description = "STRICT requires equal counts; RESAMPLE_MAX uses the larger count; RESAMPLE_COUNT uses Resample Count")
    private MatchSectionsMode matchSectionsMode = MatchSectionsMode.STRICT;

    @NodeProperty(displayName = "Resample Count", category = "Compatibility", order = 11,
        description = "Target vertex count when Match Sections is RESAMPLE_COUNT (minimum 3)")
    private int resampleCount = 0;

    @NodeProperty(displayName = "Flip Profiles", category = "Correspondence", order = 20,
        description = "Reverse vertex order on both profiles. Negative scale is not a flip.")
    private boolean flipProfiles = false;

    @NodeProperty(displayName = "Seam Offset", category = "Correspondence", order = 21,
        description = "Integer cyclic vertex shift applied after Flip")
    private int seamOffset = 0;

    @NodeProperty(displayName = "Match Seam", category = "Correspondence", order = 22,
        description = "INDEX keeps vertex indices; AUTO_SEAM is an explicit cyclic min-distance shift")
    private MatchSeamMode matchSeamMode = MatchSeamMode.INDEX;

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

    public LoftProfilesNode() {
        super(UUID.randomUUID(), "geometry.solids.loft");

        addInputPort(new BasePort(INPUT_SOURCE_PROFILE_ID, "Source Profile", "First polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_TARGET_PROFILE_ID, "Target Profile", "Second polygon profile", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_SOURCE_PROFILE_ID, "Source Profile", "Resolved source polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_PROFILE_ID, "Target Profile", "Resolved target polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_POINTS_ID, "Source Points", "Closed source polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_POINTS_ID, "Target Points", "Closed target polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Source and target section points keyed as {0} and {1}", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Segments connecting corresponding source and target vertices", NodeDataType.LINE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_TREE_ID, "Rail Segments Tree", "Loft rail segments keyed by rail index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface",
            "Primary output: lofted surface topology between the two profiles (not a solid)",
            NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of loft rails", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both profiles are compatible for lofting", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Lofts two polygon profiles into a SURFACE_STRIP (surface topology, not a solid). Resamples when vertex counts differ per Match Sections mode.";
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
            invalidate("Source and target profiles are required");
            return;
        }

        List<Vector3d> sourceUniquePoints = sourceProfile.getUniquePoints();
        List<Vector3d> targetUniquePoints = targetProfile.getUniquePoints();
        if (sourceUniquePoints.size() < 3 || targetUniquePoints.size() < 3) {
            invalidate("Each profile must have at least 3 vertices");
            return;
        }

        if (sourceUniquePoints.size() != targetUniquePoints.size()) {
            Integer targetCount = resolveTargetPointCount(sourceUniquePoints.size(), targetUniquePoints.size());
            if (targetCount == null) {
                return;
            }
            sourceUniquePoints = SolidNodeUtils.resampleSection(sourceUniquePoints, targetCount, true);
            targetUniquePoints = SolidNodeUtils.resampleSection(targetUniquePoints, targetCount, true);
            if (sourceUniquePoints.size() < 3 || sourceUniquePoints.size() != targetUniquePoints.size()) {
                invalidate("Profile resampling failed");
                return;
            }
            sourceProfile = rebuildProfile(sourceProfile, sourceUniquePoints);
            targetProfile = rebuildProfile(targetProfile, targetUniquePoints);
        }

        sourceUniquePoints = SectionCorrespondence.apply(
            sourceUniquePoints, flipProfiles, seamOffset, null, MatchSeamMode.INDEX);
        targetUniquePoints = SectionCorrespondence.apply(
            targetUniquePoints, flipProfiles, seamOffset, sourceUniquePoints, matchSeamMode);
        sourceProfile = rebuildProfile(sourceProfile, sourceUniquePoints);
        targetProfile = rebuildProfile(targetProfile, targetUniquePoints);

        List<LineData> railSegments = new ArrayList<>(sourceUniquePoints.size());
        for (int i = 0; i < sourceUniquePoints.size(); i++) {
            Vector3d sourcePoint = sourceUniquePoints.get(i);
            Vector3d targetPoint = targetUniquePoints.get(i);
            railSegments.add(new LineData(
                new Vec3d(sourcePoint.x, sourcePoint.y, sourcePoint.z),
                new Vec3d(targetPoint.x, targetPoint.y, targetPoint.z)
            ));
        }

        SurfaceStripData sideSurface;
        try {
            sideSurface = new SurfaceStripData(
                List.of(sourceUniquePoints, targetUniquePoints),
                List.of(true, true)
            );
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Loft surface strip is invalid" : ex.getMessage());
            return;
        }
        String stripError = validateSurfaceStrip(sideSurface);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        outputValues.put(OUTPUT_SOURCE_PROFILE_ID, sourceProfile);
        outputValues.put(OUTPUT_TARGET_PROFILE_ID, targetProfile);
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, SpatialValueResolver.toPointDataList(sourceProfile.closedPoints()));
        outputValues.put(OUTPUT_TARGET_POINTS_ID, SpatialValueResolver.toPointDataList(targetProfile.closedPoints()));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(List.of(sourceUniquePoints, targetUniquePoints)));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, SolidDataTreeUtils.indexedValueTree(railSegments));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, sideSurface);
        outputValues.put(OUTPUT_COUNT_ID, railSegments.size());
        markSuccess();
    }

    public MatchSectionsMode getMatchSectionsMode() {
        return matchSectionsMode;
    }

    public void setMatchSectionsMode(MatchSectionsMode matchSectionsMode) {
        MatchSectionsMode resolved = matchSectionsMode == null ? MatchSectionsMode.STRICT : matchSectionsMode;
        markDirtyIfChanged(this.matchSectionsMode, resolved);
        this.matchSectionsMode = resolved;
    }

    public void setMatchSectionsModeString(String key) {
        if (key == null || key.isBlank()) {
            setMatchSectionsMode(MatchSectionsMode.STRICT);
            return;
        }
        if (!MatchSectionsMode.KEYS.contains(key.toLowerCase())) {
            setMatchSectionsMode(MatchSectionsMode.STRICT);
            return;
        }
        setMatchSectionsMode(MatchSectionsMode.fromKey(key));
    }

    public int getResampleCount() {
        return resampleCount;
    }

    public void setResampleCount(int resampleCount) {
        markDirtyIfChanged(this.resampleCount, resampleCount);
        this.resampleCount = resampleCount;
    }

    public boolean isFlipProfiles() {
        return flipProfiles;
    }

    public void setFlipProfiles(boolean flipProfiles) {
        markDirtyIfChanged(this.flipProfiles, flipProfiles);
        this.flipProfiles = flipProfiles;
    }

    public int getSeamOffset() {
        return seamOffset;
    }

    public void setSeamOffset(int seamOffset) {
        markDirtyIfChanged(this.seamOffset, seamOffset);
        this.seamOffset = seamOffset;
    }

    public MatchSeamMode getMatchSeamMode() {
        return matchSeamMode;
    }

    public void setMatchSeamMode(MatchSeamMode matchSeamMode) {
        MatchSeamMode resolved = matchSeamMode == null ? MatchSeamMode.INDEX : matchSeamMode;
        markDirtyIfChanged(this.matchSeamMode, resolved);
        this.matchSeamMode = resolved;
    }

    public void setMatchSeamModeString(String key) {
        if (key == null || key.isBlank() || !MatchSeamMode.KEYS.contains(key.toLowerCase())) {
            setMatchSeamMode(MatchSeamMode.INDEX);
            return;
        }
        setMatchSeamMode(MatchSeamMode.fromKey(key));
    }

    /** @deprecated use {@link #getMatchSectionsMode()} */
    @Deprecated
    public boolean isAutoResample() {
        return matchSectionsMode != MatchSectionsMode.STRICT;
    }

    /** @deprecated use {@link #setMatchSectionsMode(MatchSectionsMode)} */
    @Deprecated
    public void setAutoResample(boolean autoResample) {
        setMatchSectionsMode(autoResample ? MatchSectionsMode.RESAMPLE_MAX : MatchSectionsMode.STRICT);
    }

    /** @deprecated use {@link #getResampleCount()} */
    @Deprecated
    public int getTargetSectionPoints() {
        return resampleCount;
    }

    /** @deprecated use {@link #setResampleCount(int)} */
    @Deprecated
    public void setTargetSectionPoints(int targetSectionPoints) {
        setResampleCount(targetSectionPoints);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("matchSectionsMode", matchSectionsMode.key());
        state.put("resampleCount", resampleCount);
        state.put("flipProfiles", flipProfiles);
        state.put("seamOffset", seamOffset);
        state.put("matchSeamMode", matchSeamMode.key());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("matchSectionsMode") instanceof String value) {
            setMatchSectionsModeString(value);
        } else if (map.get("autoResample") instanceof Boolean autoResample) {
            setAutoResample(autoResample);
        }
        if (map.get("resampleCount") instanceof Number value) {
            setResampleCount(value.intValue());
        } else if (map.get("targetSectionPoints") instanceof Number value) {
            setResampleCount(value.intValue());
        }
        if (map.get("flipProfiles") instanceof Boolean value) {
            setFlipProfiles(value);
        }
        if (map.get("seamOffset") instanceof Number value) {
            setSeamOffset(value.intValue());
        }
        if (map.get("matchSeamMode") instanceof String value) {
            setMatchSeamModeString(value);
        }
    }

    private @Nullable Integer resolveTargetPointCount(int sourceCount, int targetCount) {
        return switch (matchSectionsMode) {
            case STRICT -> {
                invalidate("Profiles must have matching vertex counts (Match Sections is STRICT)");
                yield null;
            }
            case RESAMPLE_MAX -> Math.max(3, Math.max(sourceCount, targetCount));
            case RESAMPLE_COUNT -> {
                int count = resampleCount >= 3 ? resampleCount : 0;
                if (count < 3) {
                    invalidate("Resample Count must be at least 3 when Match Sections is RESAMPLE_COUNT");
                    yield null;
                }
                if (!SurfaceInputUtils.isWithinSurfacePointsPerSection(count)) {
                    invalidate("Resample Count exceeds limit (" + com.nodecraft.nodesystem.util.GenerationLimits.MAX_SURFACE_POINTS_PER_SECTION + ")");
                    yield null;
                }
                yield count;
            }
        };
    }

    private static PolygonProfileData rebuildProfile(PolygonProfileData original, List<Vector3d> uniquePoints) {
        List<Vector3d> closed = new ArrayList<>(uniquePoints.size() + 1);
        for (Vector3d point : uniquePoints) {
            closed.add(new Vector3d(point));
        }
        if (!closed.isEmpty()) {
            closed.add(new Vector3d(closed.getFirst()));
        }
        return new PolygonProfileData(closed, original.plane());
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_SOURCE_PROFILE_ID, OUTPUT_TARGET_PROFILE_ID, OUTPUT_SIDE_SURFACE_ID);
        putEmptyListOutputs(OUTPUT_SOURCE_POINTS_ID, OUTPUT_TARGET_POINTS_ID, OUTPUT_RAIL_SEGMENTS_ID);
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, DataTreeData.empty());
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}
