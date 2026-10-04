package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ProfileExtrusionUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.extrude",
    displayName = "Extrude",
    description = "Extrudes a polygon profile by a direction vector into prism geometry (canonical Extrude)",
    category = "geometry.solids",
    order = 0
)
public class ExtrudeProfileNode extends AbstractSolidNode {

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_DIRECTION_ID = "input_direction";

    private static final String OUTPUT_PRISM_ID = "output_prism";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BASE_PROFILE_ID = "output_base_profile";
    private static final String OUTPUT_TOP_PROFILE_ID = "output_top_profile";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_SIDE_SURFACE_ID = "output_side_surface";
    private static final String OUTPUT_HEIGHT_ID = "output_height";

    public ExtrudeProfileNode() {
        super(UUID.randomUUID(), "geometry.solids.extrude");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to extrude", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Extrusion direction vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PRISM_ID, "Prism", "Constructed prism geometry", NodeDataType.PRISM_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BASE_PROFILE_ID, "Base Profile", "Original polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_TOP_PROFILE_ID, "Top Profile", "Extruded polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Closed base polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Closed extruded polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface",
            "Side strip surface between the base and top profiles (surface shell, not a solid)",
            NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Extrusion vector length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid profile and direction were provided", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        Vector3d direction = resolveDirection(INPUT_DIRECTION_ID);

        if (!(profileObj instanceof PolygonProfileData baseProfile)) {
            invalidate("Profile is missing or invalid");
            return;
        }
        if (direction == null) {
            invalidate("Direction is missing, invalid, or zero-length");
            return;
        }

        StringBuilder error = new StringBuilder();
        ProfileExtrusionUtils.ExtrusionResult result =
            ProfileExtrusionUtils.extrudeProfile(baseProfile, direction, error);
        if (result == null) {
            invalidate(error.isEmpty() ? "Failed to extrude profile" : error.toString());
            return;
        }

        outputValues.put(OUTPUT_PRISM_ID, result.prism());
        outputValues.put(OUTPUT_GEOMETRY_ID, result.prism());
        outputValues.put(OUTPUT_BASE_PROFILE_ID, baseProfile);
        outputValues.put(OUTPUT_TOP_PROFILE_ID, result.topProfile());
        outputValues.put(OUTPUT_BASE_POINTS_ID, SpatialValueResolver.toPointDataList(result.baseClosedPoints()));
        outputValues.put(OUTPUT_TOP_POINTS_ID, SpatialValueResolver.toPointDataList(result.topClosedPoints()));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, result.sideSurface());
        outputValues.put(OUTPUT_HEIGHT_ID, result.height());
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PRISM_ID, OUTPUT_GEOMETRY_ID, OUTPUT_BASE_PROFILE_ID, OUTPUT_TOP_PROFILE_ID,
            OUTPUT_SIDE_SURFACE_ID);
        putEmptyListOutputs(OUTPUT_BASE_POINTS_ID, OUTPUT_TOP_POINTS_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_HEIGHT_ID);
        markInvalid(error);
    }
}
