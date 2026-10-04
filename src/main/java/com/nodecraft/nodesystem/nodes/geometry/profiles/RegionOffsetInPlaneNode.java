package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.region_offset_plane",
    displayName = "Region Offset In Plane",
    description = "Offsets a planar region in its plane; preserves hole topology when possible",
    category = "geometry.profiles",
    order = 25
)
public class RegionOffsetInPlaneNode extends AbstractProfileNode {
    @NodeProperty(displayName = "Quadrant Segments", category = "Offset", order = 1)
    private int quadrantSegments = 8;

    @NodeProperty(displayName = "Join Style", category = "Offset", order = 2)
    private String joinStyle = "ROUND";

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 3)
    private double miterLimit = 4.0d;

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_REGIONS_ID = "output_regions";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public RegionOffsetInPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.region_offset_plane");
        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Input planar region", NodeDataType.PLANAR_REGION, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset",
            "Signed offset distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Primary offset planar region (may include holes)", NodeDataType.PLANAR_REGION, this));
        addOutputPort(new BasePort(OUTPUT_REGIONS_ID, "Regions",
            "All offset planar regions", NodeDataType.PLANAR_REGION_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile",
            "Convenience outer of primary region", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles",
            "Outer profiles of each offset region", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane",
            "Plane of the primary offset region", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center",
            "Center of the primary outer profile", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of offset regions produced", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when offset succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Offsets a planar region in its plane; preserves hole topology when possible";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlanarRegionData region = resolveStrictRegion(INPUT_REGION_ID);
        Double offset = resolveFiniteDouble(INPUT_OFFSET_ID, 0.0d);
        if (region == null) {
            writeFailure("Valid planar region is required");
            return;
        }
        if (offset == null) {
            writeFailure("Offset must be a finite number");
            return;
        }

        Integer join = ProfilePlanarOps.parseJoinStyle(joinStyle);
        String paramError = ProfilePlanarOps.validateOffsetParams(quadrantSegments, join, miterLimit);
        if (paramError != null) {
            writeFailure(paramError);
            return;
        }

        ProfilePlanarOps.RegionOpOutcome outcome = ProfilePlanarOps.offsetRegion(
            region,
            offset,
            quadrantSegments,
            join,
            miterLimit);
        if (outcome.failed()) {
            writeFailure(outcome.error());
            return;
        }

        writeRegionOpSuccess(
            outcome,
            OUTPUT_REGION_ID, OUTPUT_REGIONS_ID,
            OUTPUT_PROFILE_ID, OUTPUT_PROFILES_ID,
            OUTPUT_PLANE_ID, OUTPUT_CENTER_ID, OUTPUT_COUNT_ID);
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_REGION_ID, OUTPUT_PROFILE_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        putEmptyListOutputs(OUTPUT_REGIONS_ID, OUTPUT_PROFILES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "quadrantSegments", quadrantSegments,
            "joinStyle", joinStyle == null ? "ROUND" : joinStyle,
            "miterLimit", miterLimit
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        restoreInteger(map, "quadrantSegments", v -> quadrantSegments = v);
        if (map.get("joinStyle") instanceof String s) {
            joinStyle = s;
        }
        restoreFiniteDouble(map, "miterLimit", v -> miterLimit = v);
    }
}
