package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.loft",
    displayName = "Loft Profiles",
    description = "Lofts two polygon profiles with matching edge counts into a side surface strip",
    category = "geometry.solids"
)
public class LoftProfilesNode extends BaseNode {

    private static final String INPUT_SOURCE_PROFILE_ID = "input_source_profile";
    private static final String INPUT_TARGET_PROFILE_ID = "input_target_profile";

    private static final String OUTPUT_SOURCE_PROFILE_ID = "output_source_profile";
    private static final String OUTPUT_TARGET_PROFILE_ID = "output_target_profile";
    private static final String OUTPUT_SOURCE_POINTS_ID = "output_source_points";
    private static final String OUTPUT_TARGET_POINTS_ID = "output_target_points";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_SIDE_SURFACE_ID = "output_side_surface";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LoftProfilesNode() {
        super(UUID.randomUUID(), "geometry.solids.loft");

        addInputPort(new BasePort(INPUT_SOURCE_PROFILE_ID, "Source Profile", "First polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_TARGET_PROFILE_ID, "Target Profile", "Second polygon profile", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_SOURCE_PROFILE_ID, "Source Profile", "Resolved source polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_PROFILE_ID, "Target Profile", "Resolved target polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_POINTS_ID, "Source Points", "Closed source polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_POINTS_ID, "Target Points", "Closed target polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Segments connecting corresponding source and target vertices", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SURFACE_ID, "Side Surface", "Side strip surface between the two profiles", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of loft rails", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both profiles are compatible for lofting", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Lofts two polygon profiles with matching edge counts into a side surface strip";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sourceObj = inputValues.get(INPUT_SOURCE_PROFILE_ID);
        Object targetObj = inputValues.get(INPUT_TARGET_PROFILE_ID);

        if (!(sourceObj instanceof PolygonProfileData sourceProfile) || !(targetObj instanceof PolygonProfileData targetProfile)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> sourceUniquePoints = sourceProfile.getUniquePoints();
        List<Vector3d> targetUniquePoints = targetProfile.getUniquePoints();
        if (sourceUniquePoints.size() < 3 || sourceUniquePoints.size() != targetUniquePoints.size()) {
            writeEmptyOutputs();
            return;
        }

        List<LineData> railSegments = new ArrayList<>(sourceUniquePoints.size());
        for (int i = 0; i < sourceUniquePoints.size(); i++) {
            Vector3d sourcePoint = sourceUniquePoints.get(i);
            Vector3d targetPoint = targetUniquePoints.get(i);
            railSegments.add(new LineData(
                new Vec3d(sourcePoint.x, sourcePoint.y, sourcePoint.z),
                new Vec3d(targetPoint.x, targetPoint.y, targetPoint.z)
            ));
        }

        SurfaceStripData sideSurface = new SurfaceStripData(
            List.of(sourceUniquePoints, targetUniquePoints),
            List.of(true, true)
        );

        outputValues.put(OUTPUT_SOURCE_PROFILE_ID, sourceProfile);
        outputValues.put(OUTPUT_TARGET_PROFILE_ID, targetProfile);
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, sourceProfile.getClosedPoints());
        outputValues.put(OUTPUT_TARGET_POINTS_ID, targetProfile.getClosedPoints());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, sideSurface);
        outputValues.put(OUTPUT_COUNT_ID, railSegments.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SOURCE_PROFILE_ID, null);
        outputValues.put(OUTPUT_TARGET_PROFILE_ID, null);
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TARGET_POINTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_SIDE_SURFACE_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
