package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.resample_profile",
    displayName = "Resample Polygon Profile",
    description = "Resamples a polygon profile to a target edge count using perimeter-distance sampling",
    category = "geometry.profiles",
    order = 15
)
public class ResamplePolygonProfileNode extends AbstractProfileNode {

    private static final double EPSILON = 1.0e-9d;

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_EDGE_COUNT_ID = "input_edge_count";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";

    public ResamplePolygonProfileNode() {
        super(UUID.randomUUID(), "geometry.profiles.resample_profile");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to resample", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_EDGE_COUNT_ID, "Edge Count", "Target edge count after resampling", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Resampled polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed resampled polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Resampled polygon boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved profile center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edge Count", "Resolved target edge count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when resampling succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Resamples a polygon profile to a target edge count using perimeter-distance sampling";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PolygonProfileData profile = resolveStrictProfile(INPUT_PROFILE_ID);
        if (profile == null) {
            writeFailure("Valid polygon profile is required");
            return;
        }

        Integer targetEdgeCount = resolveBoundedInteger(
            INPUT_EDGE_COUNT_ID, 0, 3, GenerationLimits.MAX_PROFILE_VERTICES);
        if (targetEdgeCount == null) {
            writeFailure("Edge count must be an exact integer from 3 to " + GenerationLimits.MAX_PROFILE_VERTICES);
            return;
        }

        List<Vector3d> sourceClosedPoints = profile.closedPoints();
        List<Vector3d> uniqueResampledPoints = resampleClosedPolyline(sourceClosedPoints, targetEdgeCount);
        if (uniqueResampledPoints == null || uniqueResampledPoints.size() != targetEdgeCount) {
            writeFailure("Failed to resample polygon profile");
            return;
        }

        List<Vector3d> closedResampledPoints = new ArrayList<>(uniqueResampledPoints.size() + 1);
        closedResampledPoints.addAll(uniqueResampledPoints);
        closedResampledPoints.add(new Vector3d(uniqueResampledPoints.getFirst()));

        StringBuilder error = new StringBuilder();
        PolygonProfileData resampledProfile = ProfileConstructionUtils.tryCreateProfile(
            closedResampledPoints, profile.plane(), error);
        if (resampledProfile == null) {
            writeFailure(error.isEmpty() ? "Failed to create resampled profile" : error.toString());
            return;
        }

        outputValues.put(OUTPUT_PROFILE_ID, resampledProfile);
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(closedResampledPoints));
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(resampledProfile));
        outputValues.put(OUTPUT_PLANE_ID, resampledProfile.plane());
        outputValues.put(OUTPUT_CENTER_ID, new PointData(resampledProfile.getCenter()));
        outputValues.put(OUTPUT_EDGE_COUNT_ID, targetEdgeCount);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_EDGE_COUNT_ID);
        markInvalid(error);
    }

    /**
     * Perimeter-distance resample. Returns {@code null} when any edge length or
     * running perimeter is non-finite (do not lerp on Inf).
     */
    public static @Nullable List<Vector3d> resampleClosedPolyline(List<Vector3d> closedPoints, int targetCount) {
        if (closedPoints == null || closedPoints.size() < 2 || targetCount < 1) {
            return null;
        }
        int segmentCount = closedPoints.size() - 1;
        double[] cumulative = new double[closedPoints.size()];
        cumulative[0] = 0.0d;
        double perimeter = 0.0d;
        for (int i = 0; i < segmentCount; i++) {
            double segmentLength = VectorUtils.safeDistance(closedPoints.get(i), closedPoints.get(i + 1));
            if (!Double.isFinite(segmentLength)) {
                return null;
            }
            perimeter += segmentLength;
            if (!Double.isFinite(perimeter)) {
                return null;
            }
            cumulative[i + 1] = perimeter;
        }
        if (perimeter <= EPSILON) {
            return List.of();
        }

        List<Vector3d> result = new ArrayList<>(targetCount);
        for (int sampleIndex = 0; sampleIndex < targetCount; sampleIndex++) {
            double targetDistance = (perimeter * sampleIndex) / targetCount;
            if (!Double.isFinite(targetDistance)) {
                return null;
            }
            Vector3d sample = sampleAtDistance(closedPoints, cumulative, targetDistance);
            if (sample == null) {
                return null;
            }
            result.add(sample);
        }
        return List.copyOf(result);
    }

    private static @Nullable Vector3d sampleAtDistance(
            List<Vector3d> closedPoints,
            double[] cumulative,
            double targetDistance
    ) {
        for (int i = 0; i < closedPoints.size() - 1; i++) {
            double startDistance = cumulative[i];
            double endDistance = cumulative[i + 1];
            if (targetDistance <= endDistance || i == closedPoints.size() - 2) {
                Vector3d start = closedPoints.get(i);
                Vector3d end = closedPoints.get(i + 1);
                double segmentLength = endDistance - startDistance;
                if (!Double.isFinite(segmentLength)) {
                    return null;
                }
                if (segmentLength <= EPSILON) {
                    return new Vector3d(start);
                }
                double t = (targetDistance - startDistance) / segmentLength;
                return VectorUtils.safeLerp(start, end, t);
            }
        }
        return new Vector3d(closedPoints.getFirst());
    }
}
