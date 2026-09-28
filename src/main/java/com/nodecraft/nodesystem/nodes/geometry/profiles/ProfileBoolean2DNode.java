package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PolygonProfileValidator;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.boolean_2d",
    displayName = "Profile Boolean 2D",
    description = "Performs 2D boolean operations on two coplanar polygon profiles; outputs PLANAR_REGION (supports holes)",
    category = "geometry.profiles",
    order = 17
)
public class ProfileBoolean2DNode extends AbstractProfileNode {
    @NodeProperty(displayName = "Operation", category = "Boolean", order = 1)
    private String operation = "UNION";

    private static final String INPUT_A_ID = "input_profile_a";
    private static final String INPUT_B_ID = "input_profile_b";

    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_REGIONS_ID = "output_regions";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ProfileBoolean2DNode() {
        super(UUID.randomUUID(), "geometry.profiles.boolean_2d");
        addInputPort(new BasePort(INPUT_A_ID, "Profile A", "First polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_B_ID, "Profile B", "Second polygon profile", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Primary planar region (largest area; may include holes)", NodeDataType.PLANAR_REGION, this));
        addOutputPort(new BasePort(OUTPUT_REGIONS_ID, "Regions",
            "All planar regions from the boolean result", NodeDataType.PLANAR_REGION_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile",
            "Convenience outer of primary region (not full region when holes exist)", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles",
            "Outer profiles of each result region", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane of the primary result", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Center of the primary outer profile", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output regions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when boolean operation succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Performs 2D boolean operations on two coplanar polygon profiles; outputs PLANAR_REGION (supports holes)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PolygonProfileData a = resolveStrictProfile(INPUT_A_ID);
        PolygonProfileData b = resolveStrictProfile(INPUT_B_ID);
        if (a == null || b == null) {
            writeFailure("Valid polygon profiles are required on both inputs");
            return;
        }

        int totalVertices = a.getEdgeCount() + b.getEdgeCount();
        if (!GenerationLimits.isWithinProfileBooleanVertices(totalVertices)) {
            writeFailure("Combined profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_BOOLEAN_VERTICES + ")");
            return;
        }

        if (!PolygonProfileValidator.profilesCoplanar(a, b)) {
            writeFailure("Profiles must lie on the same plane");
            return;
        }

        PlaneData plane = a.plane();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        GeometryFactory gf = new GeometryFactory();

        Polygon pa = ProfilePlanarOps.toJtsPolygon(a, axes, gf);
        Polygon pb = ProfilePlanarOps.toJtsPolygon(b, axes, gf);
        if (pa == null || pb == null) {
            writeFailure("Failed to convert profiles for boolean operation");
            return;
        }

        Geometry out = switch (parseOperation(operation)) {
            case INTERSECTION -> pa.intersection(pb);
            case DIFFERENCE -> pa.difference(pb);
            case UNION -> pa.union(pb);
        };

        List<PlanarRegionData> regions = new ArrayList<>();
        String conversionError = ProfilePlanarOps.appendPlanarRegions(out, axes, plane, regions);
        if (conversionError != null) {
            writeFailure(conversionError);
            return;
        }

        if (regions.isEmpty()) {
            writeSuccessEmpty(plane);
            return;
        }

        String budgetError = ProfilePlanarOps.validateRegionOutputBudget(regions);
        if (budgetError != null) {
            writeFailure(budgetError);
            return;
        }

        PlanarRegionData primary = ProfilePlanarOps.selectPrimaryRegion(regions, axes);
        List<PolygonProfileData> outers = new ArrayList<>(regions.size());
        for (PlanarRegionData region : regions) {
            outers.add(region.outer());
        }

        outputValues.put(OUTPUT_REGION_ID, primary);
        outputValues.put(OUTPUT_REGIONS_ID, new ArrayList<>(regions));
        outputValues.put(OUTPUT_PROFILE_ID, primary.outer());
        outputValues.put(OUTPUT_PROFILES_ID, outers);
        outputValues.put(OUTPUT_PLANE_ID, primary.plane());
        outputValues.put(OUTPUT_CENTER_ID, new PointData(primary.outer().getCenter()));
        outputValues.put(OUTPUT_COUNT_ID, regions.size());
        markSuccess();
    }

    private Operation parseOperation(String raw) {
        if (raw == null) {
            return Operation.UNION;
        }
        return switch (raw.trim().toUpperCase()) {
            case "INTERSECTION" -> Operation.INTERSECTION;
            case "DIFFERENCE" -> Operation.DIFFERENCE;
            default -> Operation.UNION;
        };
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_REGION_ID, OUTPUT_PROFILE_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        putEmptyListOutputs(OUTPUT_REGIONS_ID, OUTPUT_PROFILES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }

    private void writeSuccessEmpty(PlaneData plane) {
        putNullOutputs(OUTPUT_REGION_ID, OUTPUT_PROFILE_ID, OUTPUT_CENTER_ID);
        putEmptyListOutputs(OUTPUT_REGIONS_ID, OUTPUT_PROFILES_ID);
        outputValues.put(OUTPUT_PLANE_ID, plane);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markSuccess();
    }

    private enum Operation {
        UNION,
        INTERSECTION,
        DIFFERENCE
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
