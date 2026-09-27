package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.SurfaceInputUtils;
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
    id = "geometry.solids.loft_multi_section",
    displayName = "Multi-Section Loft Surface",
    description = "Lofts multiple polygon sections into one SURFACE_STRIP (surface topology, not a solid) with close, flip, seam, and resample options.",
    category = "geometry.solids",
    order = 7
)
public class MultiSectionLoftNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Close Sections", category = "Loft", order = 1,
        description = "Treat each section as closed when building the surface strip")
    private boolean closeSections = true;

    @NodeProperty(displayName = "Flip Sections", category = "Loft", order = 2,
        description = "Reverse point order inside every section")
    private boolean flipSections = false;

    @NodeProperty(displayName = "Reverse Section Order", category = "Loft", order = 3,
        description = "Reverse the order of the loft sections")
    private boolean reverseSectionOrder = false;

    @NodeProperty(displayName = "Seam Offset", category = "Loft", order = 4,
        description = "Rotates each section's point order by this many vertices")
    private int seamOffset = 0;

    @NodeProperty(displayName = "Match Sections", category = "Compatibility", order = 10,
        description = "STRICT requires equal counts; RESAMPLE_MAX uses the largest count; RESAMPLE_COUNT uses Resample Count")
    private MatchSectionsMode matchSectionsMode = MatchSectionsMode.STRICT;

    @NodeProperty(displayName = "Resample Count", category = "Compatibility", order = 11,
        description = "Target vertex count when Match Sections is RESAMPLE_COUNT (minimum 3)")
    private int resampleCount = 0;

    private static final String INPUT_PROFILES_ID = "input_profiles";

    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_PROFILES_TREE_ID = "output_profiles_tree";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_SECTION_PATHS_TREE_ID = "output_section_paths_tree";
    private static final String OUTPUT_SECTION_POINTS_TREE_ID = "output_section_points_tree";
    private static final String OUTPUT_RAILS_ID = "output_rails";
    private static final String OUTPUT_RAILS_TREE_ID = "output_rails_tree";
    private static final String OUTPUT_SIDE_SURFACE_ID = "output_side_surface";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";

    public MultiSectionLoftNode() {
        super(UUID.randomUUID(), "geometry.solids.loft_multi_section");
        addInputPort(new BasePort(INPUT_PROFILES_ID, "Profiles", "Ordered polygon profile list", NodeDataType.POLYGON_PROFILE_LIST, this));

        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "Resolved section profiles", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Resolved section profiles keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary path for each section", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Section paths keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Section points keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAILS_ID, "Rails", "Paths connecting corresponding vertices across sections", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAILS_TREE_ID, "Rails Tree", "Loft rails keyed by vertex index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface", "Lofted side strip across all sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of loft sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when multi-section loft succeeds", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Lofts multiple polygon sections into one SURFACE_STRIP (surface topology, not a solid) with close, flip, seam, and resample options.";
    }

    @Override
    public String getDisplayName() {
        return "Multi-Section Loft Surface";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<PolygonProfileData> profiles = SurfaceInputUtils.resolveStrictProfileList(inputValues.get(INPUT_PROFILES_ID));
        if (profiles == null || profiles.size() < 2) {
            invalidate("Profiles input requires at least two polygon profiles");
            return;
        }
        if (reverseSectionOrder) {
            profiles = new ArrayList<>(profiles);
            Collections.reverse(profiles);
        }

        List<List<Vector3d>> stripSections = new ArrayList<>(profiles.size());
        for (PolygonProfileData profile : profiles) {
            List<Vector3d> unique = prepareSection(profile.getUniquePoints());
            if (unique.size() < 3) {
                invalidate("Each section must have at least 3 vertices");
                return;
            }
            stripSections.add(List.copyOf(unique));
        }

        if (!allSameSize(stripSections)) {
            Integer targetCount = resolveTargetPointCount(stripSections);
            if (targetCount == null) {
                return;
            }
            stripSections = resampleSections(stripSections, targetCount, closeSections);
        }

        if (!allSameSize(stripSections) || stripSections.getFirst().isEmpty()) {
            invalidate("Loft sections could not be matched to a common point count");
            return;
        }

        int expected = stripSections.getFirst().size();
        List<Boolean> closedFlags = new ArrayList<>(profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            closedFlags.add(closeSections);
        }
        List<PathData> sectionPaths = new ArrayList<>(stripSections.size());
        for (int s = 0; s < stripSections.size(); s++) {
            PathData path = SolidNodeUtils.toPath(SolidNodeUtils.createPolyline(stripSections.get(s), closeSections));
            if (path == null) {
                invalidate("Section path at index " + s + " is invalid");
                return;
            }
            sectionPaths.add(path);
        }

        List<PathData> rails = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            List<Vector3d> railPoints = new ArrayList<>(profiles.size());
            for (List<Vector3d> section : stripSections) {
                railPoints.add(section.get(i));
            }
            PathData rail = PathUtils.toPathData(railPoints);
            if (rail == null) {
                invalidate("Rail path at vertex index " + i + " is invalid");
                return;
            }
            rails.add(rail);
        }

        SurfaceStripData surface;
        try {
            surface = new SurfaceStripData(stripSections, closedFlags);
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Surface strip is invalid" : ex.getMessage());
            return;
        }
        String stripError = validateSurfaceStrip(surface);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        outputValues.put(OUTPUT_PROFILES_ID, List.copyOf(profiles));
        outputValues.put(OUTPUT_PROFILES_TREE_ID, SolidDataTreeUtils.indexedValueTree(profiles));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionPaths));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(stripSections));
        outputValues.put(OUTPUT_RAILS_ID, List.copyOf(rails));
        outputValues.put(OUTPUT_RAILS_TREE_ID, SolidDataTreeUtils.indexedValueTree(rails));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, surface);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, profiles.size());
        markSuccess();
    }

    private @Nullable Integer resolveTargetPointCount(List<List<Vector3d>> sections) {
        return switch (matchSectionsMode) {
            case STRICT -> {
                invalidate("All sections must have the same point count (Match Sections is STRICT)");
                yield null;
            }
            case RESAMPLE_MAX -> {
                int max = 0;
                for (List<Vector3d> section : sections) {
                    max = Math.max(max, section.size());
                }
                yield Math.max(3, max);
            }
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

    private List<Vector3d> prepareSection(List<Vector3d> source) {
        List<Vector3d> points = new ArrayList<>(source.size());
        for (Vector3d point : source) {
            points.add(new Vector3d(point));
        }
        if (flipSections) {
            Collections.reverse(points);
        }
        return rotateSection(points, seamOffset);
    }

    private List<Vector3d> rotateSection(List<Vector3d> points, int offset) {
        if (points.isEmpty()) {
            return points;
        }
        int shift = Math.floorMod(offset, points.size());
        if (shift == 0) {
            return points;
        }
        List<Vector3d> rotated = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            rotated.add(points.get((i + shift) % points.size()));
        }
        return rotated;
    }

    private List<List<Vector3d>> resampleSections(List<List<Vector3d>> sections, int targetCount, boolean closed) {
        List<List<Vector3d>> result = new ArrayList<>(sections.size());
        for (List<Vector3d> section : sections) {
            result.add(SolidNodeUtils.resampleSection(section, targetCount, closed));
        }
        return result;
    }

    private boolean allSameSize(List<List<Vector3d>> sections) {
        if (sections.isEmpty()) {
            return false;
        }
        int size = sections.getFirst().size();
        for (List<Vector3d> section : sections) {
            if (section.size() != size) {
                return false;
            }
        }
        return true;
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

    public MatchSectionsMode getMatchSectionsMode() {
        return matchSectionsMode;
    }

    public void setMatchSectionsMode(MatchSectionsMode matchSectionsMode) {
        MatchSectionsMode resolved = matchSectionsMode == null ? MatchSectionsMode.STRICT : matchSectionsMode;
        markDirtyIfChanged(this.matchSectionsMode, resolved);
        this.matchSectionsMode = resolved;
    }

    public int getResampleCount() {
        return resampleCount;
    }

    public void setResampleCount(int resampleCount) {
        markDirtyIfChanged(this.resampleCount, resampleCount);
        this.resampleCount = resampleCount;
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

    public boolean isCloseSections() {
        return closeSections;
    }

    public void setCloseSections(boolean closeSections) {
        markDirtyIfChanged(this.closeSections, closeSections);
        this.closeSections = closeSections;
    }

    public boolean isFlipSections() {
        return flipSections;
    }

    public void setFlipSections(boolean flipSections) {
        markDirtyIfChanged(this.flipSections, flipSections);
        this.flipSections = flipSections;
    }

    public boolean isReverseSectionOrder() {
        return reverseSectionOrder;
    }

    public void setReverseSectionOrder(boolean reverseSectionOrder) {
        markDirtyIfChanged(this.reverseSectionOrder, reverseSectionOrder);
        this.reverseSectionOrder = reverseSectionOrder;
    }

    public int getSeamOffset() {
        return seamOffset;
    }

    public void setSeamOffset(int seamOffset) {
        markDirtyIfChanged(this.seamOffset, seamOffset);
        this.seamOffset = seamOffset;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("closeSections", closeSections);
        state.put("flipSections", flipSections);
        state.put("reverseSectionOrder", reverseSectionOrder);
        state.put("seamOffset", seamOffset);
        state.put("matchSectionsMode", matchSectionsMode.key());
        state.put("resampleCount", resampleCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("closeSections") instanceof Boolean value) {
            closeSections = value;
        }
        if (map.get("flipSections") instanceof Boolean value) {
            flipSections = value;
        }
        if (map.get("reverseSectionOrder") instanceof Boolean value) {
            reverseSectionOrder = value;
        }
        if (map.get("seamOffset") instanceof Number value) {
            seamOffset = value.intValue();
        }
        if (map.get("matchSectionsMode") instanceof String value) {
            if (MatchSectionsMode.KEYS.contains(value.toLowerCase())) {
                matchSectionsMode = MatchSectionsMode.fromKey(value);
            }
        } else if (map.get("autoResample") instanceof Boolean autoResample) {
            setAutoResample(autoResample);
        }
        if (map.get("resampleCount") instanceof Number value) {
            resampleCount = value.intValue();
        } else if (map.get("targetSectionPoints") instanceof Number value) {
            resampleCount = value.intValue();
        }
        markDirty();
    }

    private void invalidate(String error) {
        putEmptyListOutputs(OUTPUT_PROFILES_ID, OUTPUT_SECTION_PATHS_ID, OUTPUT_RAILS_ID);
        outputValues.put(OUTPUT_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAILS_TREE_ID, DataTreeData.empty());
        putNullOutputs(OUTPUT_SIDE_SURFACE_ID);
        putIntOutputs(0, OUTPUT_SECTION_COUNT_ID);
        markInvalid(error);
    }
}
