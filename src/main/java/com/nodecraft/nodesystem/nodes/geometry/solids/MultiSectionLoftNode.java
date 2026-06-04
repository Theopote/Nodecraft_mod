package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.loft_multi_section",
    displayName = "Multi-Section Loft",
    description = "Lofts multiple polygon sections with matching vertex counts into one surface strip.",
    category = "geometry.solids",
    order = 11
)
public class MultiSectionLoftNode extends BaseNode {
    private static final String INPUT_PROFILES_ID = "input_profiles";

    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_RAILS_ID = "output_rails";
    private static final String OUTPUT_SIDE_SURFACE_ID = "output_side_surface";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MultiSectionLoftNode() {
        super(UUID.randomUUID(), "geometry.solids.loft_multi_section");
        addInputPort(new BasePort(INPUT_PROFILES_ID, "Profiles", "Ordered polygon profile list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "Resolved section profiles", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Boundary path for each section", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAILS_ID, "Rails", "Polyline rails connecting corresponding vertices across sections", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface", "Lofted side strip across all sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of loft sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when multi-section loft succeeds", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Lofts multiple polygon sections with matching vertex counts into one surface strip.";
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

        int expected = -1;
        List<List<Vector3d>> stripSections = new ArrayList<>(profiles.size());
        List<Boolean> closedFlags = new ArrayList<>(profiles.size());
        List<Object> sectionPaths = new ArrayList<>(profiles.size());
        for (PolygonProfileData profile : profiles) {
            List<Vector3d> unique = profile.getUniquePoints();
            if (unique.size() < 3) {
                writeInvalid();
                return;
            }
            if (expected < 0) {
                expected = unique.size();
            } else if (expected != unique.size()) {
                writeInvalid();
                return;
            }
            stripSections.add(List.copyOf(unique));
            closedFlags.add(true);
            sectionPaths.add(profile.getBoundary());
        }

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
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_RAILS_ID, List.copyOf(rails));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, surface);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, profiles.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_RAILS_ID, List.of());
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
