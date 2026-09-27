package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.offset_profile_plane",
    displayName = "Profile Offset In Plane",
    description = "Offsets a polygon profile in its plane by signed distance using 2D buffer logic",
    category = "geometry.profiles",
    order = 16
)
public class ProfileOffsetInPlaneNode extends AbstractProfileNode {
    @NodeProperty(displayName = "Quadrant Segments", category = "Offset", order = 1)
    private int quadrantSegments = 8;

    @NodeProperty(displayName = "Join Style", category = "Offset", order = 2)
    private String joinStyle = "ROUND";

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 3)
    private double miterLimit = 4.0d;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ProfileOffsetInPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.offset_profile_plane");
        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Input polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed offset distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary offset profile (largest area)", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "All offset polygon profiles", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane of the primary offset profile", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Center of the primary offset profile", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of offset profiles produced", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Offsets a polygon profile in its plane by signed distance using 2D buffer logic";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PolygonProfileData profile = resolveStrictProfile(INPUT_PROFILE_ID);
        Double offset = resolveFiniteDouble(INPUT_OFFSET_ID, 0.0d);
        if (profile == null) {
            writeFailure("Valid polygon profile is required");
            return;
        }
        if (offset == null) {
            writeFailure("Offset must be a finite number");
            return;
        }

        if (Math.abs(offset) < 1.0e-12d) {
            writePassthrough(profile);
            return;
        }

        PlaneData plane = profile.plane();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        GeometryFactory gf = new GeometryFactory();
        Polygon polygon = ProfilePlanarOps.toJtsPolygon(profile, axes, gf);
        if (polygon == null) {
            writeFailure("Failed to convert profile for offset operation");
            return;
        }

        BufferParameters params = new BufferParameters();
        params.setQuadrantSegments(Math.max(1, quadrantSegments));
        params.setJoinStyle(parseJoinStyle(joinStyle));
        params.setMitreLimit(Math.max(1.0d, miterLimit));

        Geometry out = BufferOp.bufferOp(polygon, offset, params);
        List<PolygonProfileData> profiles = new ArrayList<>();
        String conversionError = ProfilePlanarOps.appendSimplePolygons(out, axes, plane, profiles);
        if (conversionError != null) {
            writeFailure(conversionError);
            return;
        }
        if (profiles.isEmpty()) {
            writeSuccessEmpty(plane);
            return;
        }

        String budgetError = ProfilePlanarOps.validateOutputBudget(profiles);
        if (budgetError != null) {
            writeFailure(budgetError);
            return;
        }

        PolygonProfileData primary = ProfilePlanarOps.selectPrimaryProfile(profiles, axes);
        outputValues.put(OUTPUT_PROFILE_ID, primary);
        outputValues.put(OUTPUT_PROFILES_ID, new ArrayList<>(profiles));
        outputValues.put(OUTPUT_PLANE_ID, primary.plane());
        outputValues.put(OUTPUT_CENTER_ID, new PointData(primary.getCenter()));
        outputValues.put(OUTPUT_COUNT_ID, profiles.size());
        markSuccess();
    }

    private void writePassthrough(PolygonProfileData profile) {
        List<PolygonProfileData> profiles = List.of(profile);
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_PROFILES_ID, new ArrayList<>(profiles));
        outputValues.put(OUTPUT_PLANE_ID, profile.plane());
        outputValues.put(OUTPUT_CENTER_ID, new PointData(profile.getCenter()));
        putIntOutputs(1, OUTPUT_COUNT_ID);
        markSuccess();
    }

    private void writeSuccessEmpty(PlaneData plane) {
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_CENTER_ID);
        putEmptyListOutputs(OUTPUT_PROFILES_ID);
        outputValues.put(OUTPUT_PLANE_ID, plane);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        putEmptyListOutputs(OUTPUT_PROFILES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }

    private int parseJoinStyle(String raw) {
        if (raw == null) {
            return BufferParameters.JOIN_ROUND;
        }
        return switch (raw.trim().toUpperCase()) {
            case "MITER", "MITRE" -> BufferParameters.JOIN_MITRE;
            case "BEVEL" -> BufferParameters.JOIN_BEVEL;
            default -> BufferParameters.JOIN_ROUND;
        };
    }
}
