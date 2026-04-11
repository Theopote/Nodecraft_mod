package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.profiles.resample_profile",
    displayName = "Resample Polygon Profile",
    description = "Resamples a polygon profile to a target edge count using perimeter-distance sampling",
    category = "geometry.profiles"
)
public class ResamplePolygonProfileNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_EDGE_COUNT_ID = "input_edge_count";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ResamplePolygonProfileNode() {
        super(UUID.randomUUID(), "geometry.profiles.resample_profile");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to resample", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_EDGE_COUNT_ID, "Edge Count", "Target edge count after resampling", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Resampled polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed resampled polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Resampled polygon boundary", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edge Count", "Resolved target edge count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid polygon profile and target count were provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Resamples a polygon profile to a target edge count using perimeter-distance sampling";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        Object edgeCountObj = inputValues.get(INPUT_EDGE_COUNT_ID);

        if (!(profileObj instanceof PolygonProfileData profile) || !(edgeCountObj instanceof Number edgeCountNumber)) {
            writeEmptyOutputs();
            return;
        }

        int targetEdgeCount = edgeCountNumber.intValue();
        if (targetEdgeCount < 3) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> sourceClosedPoints = profile.getClosedPoints();
        if (sourceClosedPoints.size() < 4) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> uniqueResampledPoints = resampleClosedPolyline(sourceClosedPoints, targetEdgeCount);
        if (uniqueResampledPoints.size() != targetEdgeCount) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> closedResampledPoints = new ArrayList<>(uniqueResampledPoints.size() + 1);
        closedResampledPoints.addAll(uniqueResampledPoints);
        closedResampledPoints.add(new Vector3d(uniqueResampledPoints.get(0)));

        PolygonProfileData resampledProfile = new PolygonProfileData(closedResampledPoints, profile.getPlane());

        outputValues.put(OUTPUT_PROFILE_ID, resampledProfile);
        outputValues.put(OUTPUT_POINTS_ID, resampledProfile.getClosedPoints());
        outputValues.put(OUTPUT_BOUNDARY_ID, resampledProfile.getBoundary());
        outputValues.put(OUTPUT_EDGE_COUNT_ID, targetEdgeCount);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_EDGE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resampleClosedPolyline(List<Vector3d> closedPoints, int targetCount) {
        int segmentCount = closedPoints.size() - 1;
        if (segmentCount < 1) {
            return List.of();
        }

        double[] cumulative = new double[closedPoints.size()];
        cumulative[0] = 0.0d;
        double perimeter = 0.0d;
        for (int i = 0; i < segmentCount; i++) {
            double segmentLength = closedPoints.get(i).distance(closedPoints.get(i + 1));
            perimeter += segmentLength;
            cumulative[i + 1] = perimeter;
        }
        if (perimeter <= EPSILON) {
            return List.of();
        }

        List<Vector3d> result = new ArrayList<>(targetCount);
        for (int sampleIndex = 0; sampleIndex < targetCount; sampleIndex++) {
            double targetDistance = (perimeter * sampleIndex) / targetCount;
            result.add(sampleAtDistance(closedPoints, cumulative, targetDistance));
        }
        return List.copyOf(result);
    }

    private Vector3d sampleAtDistance(List<Vector3d> closedPoints, double[] cumulative, double targetDistance) {
        for (int i = 0; i < closedPoints.size() - 1; i++) {
            double startDistance = cumulative[i];
            double endDistance = cumulative[i + 1];
            if (targetDistance <= endDistance || i == closedPoints.size() - 2) {
                Vector3d start = closedPoints.get(i);
                Vector3d end = closedPoints.get(i + 1);
                double segmentLength = endDistance - startDistance;
                if (segmentLength <= EPSILON) {
                    return new Vector3d(start);
                }
                double t = (targetDistance - startDistance) / segmentLength;
                return new Vector3d(start).lerp(end, t);
            }
        }
        return new Vector3d(closedPoints.get(0));
    }
}
