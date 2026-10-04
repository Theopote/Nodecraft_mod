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
    id = "geometry.profiles.region_boolean_2d",
    displayName = "Region Boolean 2D",
    description = "Performs 2D boolean operations on two coplanar planar regions (supports holes)",
    category = "geometry.profiles",
    order = 24
)
public class RegionBoolean2DNode extends AbstractProfileNode {
    @NodeProperty(displayName = "Operation", category = "Boolean", order = 1)
    private String operation = "UNION";

    private static final String INPUT_A_ID = "input_region_a";
    private static final String INPUT_B_ID = "input_region_b";

    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_REGIONS_ID = "output_regions";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public RegionBoolean2DNode() {
        super(UUID.randomUUID(), "geometry.profiles.region_boolean_2d");
        addInputPort(new BasePort(INPUT_A_ID, "Region A",
            "First planar region", NodeDataType.PLANAR_REGION, this));
        addInputPort(new BasePort(INPUT_B_ID, "Region B",
            "Second planar region", NodeDataType.PLANAR_REGION, this));

        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Primary planar region (largest area; may include holes)", NodeDataType.PLANAR_REGION, this));
        addOutputPort(new BasePort(OUTPUT_REGIONS_ID, "Regions",
            "All planar regions from the boolean result", NodeDataType.PLANAR_REGION_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile",
            "Convenience outer of primary region (not full region when holes exist)",
            NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles",
            "Outer profiles of each result region", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane",
            "Plane of the primary result", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center",
            "Center of the primary outer profile", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of output regions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when boolean operation succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Performs 2D boolean operations on two coplanar planar regions (supports holes)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlanarRegionData a = resolveStrictRegion(INPUT_A_ID);
        PlanarRegionData b = resolveStrictRegion(INPUT_B_ID);
        if (a == null || b == null) {
            writeFailure("Valid planar regions are required on both inputs");
            return;
        }

        ProfilePlanarOps.BooleanOp op = ProfilePlanarOps.parseBooleanOp(operation);
        if (op == null) {
            writeFailure("Unknown boolean operation");
            return;
        }

        ProfilePlanarOps.RegionOpOutcome outcome = ProfilePlanarOps.booleanRegions(a, b, op);
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
        return java.util.Map.of("operation", operation);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map && map.get("operation") instanceof String value) {
            operation = value;
        }
    }
}
