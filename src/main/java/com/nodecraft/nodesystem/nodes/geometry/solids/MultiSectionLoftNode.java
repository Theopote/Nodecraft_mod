package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
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
    effect = NodeEffect.PURE,
    id = "geometry.solids.loft_multi_section",
    displayName = "Multi-Section Loft Surface",
    description = "Lofts multiple polygon sections into one SURFACE_STRIP (surface topology, not a solid) with close, flip, seam, and resample options.",
    category = "geometry.solids",
    order = 11
)
public class MultiSectionLoftNode extends BaseNode {

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

    @NodeProperty(displayName = "Auto Resample", category = "Compatibility", order = 10,
        description = "Resample sections to a shared point count when their vertex counts differ")
    private boolean autoResample = true;

    @NodeProperty(displayName = "Target Section Points", category = "Compatibility", order = 11,
        description = "Target point count for auto resampling. Use 0 to use the largest section count.")
    private int targetSectionPoints = 0;

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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MultiSectionLoftNode() {
        super(UUID.randomUUID(), "geometry.solids.loft_multi_section");
        addInputPort(new BasePort(INPUT_PROFILES_ID, "Profiles", "Ordered polygon profile list", NodeDataType.POLYGON_PROFILE_LIST, this));

        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "Resolved section profiles", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Resolved section profiles keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary path for each section", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Section paths keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Section points keyed by section index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAILS_ID, "Rails", "Polyline rails connecting corresponding vertices across sections", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAILS_TREE_ID, "Rails Tree", "Loft rails keyed by vertex index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface", "Lofted side strip across all sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of loft sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when multi-section loft succeeds", NodeDataType.BOOLEAN, this));
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
        Object profilesObj = inputValues.get(INPUT_PROFILES_ID);
        if (!(profilesObj instanceof List<?> list)) {
            writeInvalid();
            return;
        }

        List<PolygonProfileData> profiles = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof PolygonProfileData p) {
                profiles.add(p);
            }
        }
        if (profiles.size() < 2) {
            writeInvalid();
            return;
        }
        if (reverseSectionOrder) {
            Collections.reverse(profiles);
        }

        List<List<Vector3d>> stripSections = new ArrayList<>(profiles.size());
        for (PolygonProfileData profile : profiles) {
            List<Vector3d> unique = prepareSection(profile.getUniquePoints());
            if (unique.size() < 3) {
                writeInvalid();
                return;
            }
            stripSections.add(List.copyOf(unique));
        }

        if (autoResample) {
            int target = resolveTargetPointCount(profiles);
            stripSections = resampleSections(stripSections, target, closeSections);
        } else if (!allSameSize(stripSections)) {
            writeInvalid();
            return;
        }

        if (!allSameSize(stripSections) || stripSections.getFirst().isEmpty()) {
            writeInvalid();
            return;
        }

        int expected = stripSections.getFirst().size();
        List<Boolean> closedFlags = new ArrayList<>(profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            closedFlags.add(closeSections);
        }
        List<Object> sectionPaths = rebuildSectionPaths(stripSections);

        List<PolylineData> rails = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            List<net.minecraft.util.math.Vec3d> railPts = new ArrayList<>(profiles.size());
            for (List<Vector3d> section : stripSections) {
                Vector3d p = section.get(i);
                railPts.add(SolidNodeUtils.toVec3d(p));
            }
            rails.add(new PolylineData(railPts));
        }

        SurfaceStripData surface = new SurfaceStripData(stripSections, closedFlags);
        outputValues.put(OUTPUT_PROFILES_ID, List.copyOf(profiles));
        outputValues.put(OUTPUT_PROFILES_TREE_ID, SolidDataTreeUtils.indexedValueTree(profiles));
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(sectionPaths));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(stripSections));
        outputValues.put(OUTPUT_RAILS_ID, List.copyOf(rails));
        outputValues.put(OUTPUT_RAILS_TREE_ID, SolidDataTreeUtils.indexedValueTree(rails));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, surface);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, profiles.size());
        outputValues.put(OUTPUT_VALID_ID, true);
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

    private int resolveTargetPointCount(List<PolygonProfileData> profiles) {
        if (targetSectionPoints >= 3) {
            return targetSectionPoints;
        }
        int max = 0;
        for (PolygonProfileData profile : profiles) {
            max = Math.max(max, profile.getUniquePoints().size());
        }
        return Math.max(3, max);
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

    private List<Object> rebuildSectionPaths(List<List<Vector3d>> sections) {
        List<Object> paths = new ArrayList<>(sections.size());
        for (List<Vector3d> section : sections) {
            paths.add(SolidNodeUtils.createPolyline(section, closeSections));
        }
        return paths;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAILS_ID, List.of());
        outputValues.put(OUTPUT_RAILS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public boolean isCloseSections() {
        return closeSections;
    }

    public void setCloseSections(boolean closeSections) {
        if (this.closeSections != closeSections) {
            this.closeSections = closeSections;
            markDirty();
        }
    }

    public boolean isFlipSections() {
        return flipSections;
    }

    public void setFlipSections(boolean flipSections) {
        if (this.flipSections != flipSections) {
            this.flipSections = flipSections;
            markDirty();
        }
    }

    public boolean isReverseSectionOrder() {
        return reverseSectionOrder;
    }

    public void setReverseSectionOrder(boolean reverseSectionOrder) {
        if (this.reverseSectionOrder != reverseSectionOrder) {
            this.reverseSectionOrder = reverseSectionOrder;
            markDirty();
        }
    }

    public int getSeamOffset() {
        return seamOffset;
    }

    public void setSeamOffset(int seamOffset) {
        if (this.seamOffset != seamOffset) {
            this.seamOffset = seamOffset;
            markDirty();
        }
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
        int resolved = Math.max(0, targetSectionPoints);
        if (this.targetSectionPoints != resolved) {
            this.targetSectionPoints = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("closeSections", closeSections);
        state.put("flipSections", flipSections);
        state.put("reverseSectionOrder", reverseSectionOrder);
        state.put("seamOffset", seamOffset);
        state.put("autoResample", autoResample);
        state.put("targetSectionPoints", targetSectionPoints);
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
        if (map.get("autoResample") instanceof Boolean value) {
            autoResample = value;
        }
        if (map.get("targetSectionPoints") instanceof Number value) {
            targetSectionPoints = Math.max(0, value.intValue());
        }
        markDirty();
    }
}
