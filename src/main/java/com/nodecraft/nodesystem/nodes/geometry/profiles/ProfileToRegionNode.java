package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.profile_to_region",
    displayName = "Profile To Region",
    description = "Wraps a polygon profile as a planar region with no holes",
    category = "geometry.profiles",
    order = 23
)
public class ProfileToRegionNode extends AbstractProfileNode {
    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_PLANE_ID = "output_plane";

    public ProfileToRegionNode() {
        super(UUID.randomUUID(), "geometry.profiles.profile_to_region");
        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile",
            "Polygon profile to promote to a planar region", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Planar region with the profile as outer and no holes", NodeDataType.PLANAR_REGION, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane",
            "Plane of the region", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when conversion succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Wraps a polygon profile as a planar region with no holes";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PolygonProfileData profile = resolveStrictProfile(INPUT_PROFILE_ID);
        if (profile == null) {
            putNullOutputs(OUTPUT_REGION_ID, OUTPUT_PLANE_ID);
            markInvalid("Valid polygon profile is required");
            return;
        }

        PlanarRegionData region = PlanarRegionData.of(profile);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_PLANE_ID, region.plane());
        markSuccess();
    }
}
