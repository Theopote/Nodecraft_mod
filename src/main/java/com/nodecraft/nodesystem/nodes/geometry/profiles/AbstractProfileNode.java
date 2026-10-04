package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.ProfileInputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

abstract class AbstractProfileNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";
    protected static final PlaneData DEFAULT_PLANE = PlaneData.XZ_PLANE;

    protected AbstractProfileNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected final void addErrorOutputPort() {
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    protected final void markInvalid(String error) {
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    protected final void markSuccess() {
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    protected final void putNullOutputs(String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, null);
        }
    }

    protected final void putEmptyListOutputs(String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, List.of());
        }
    }

    protected final void putIntOutputs(int value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
        }
    }

    protected final void putDoubleOutputs(double value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
        }
    }

    protected final @Nullable PlaneData resolveConstructionPlane(String portId) {
        return ProfileInputUtils.resolveOptionalPlane(this, portId, DEFAULT_PLANE);
    }

    protected final @Nullable Vector3d resolveConstructionCenter(String portId, PlaneData plane) {
        Vector3d origin = plane.getPoint();
        Vector3d fallback = origin != null ? new Vector3d(origin) : new Vector3d();
        return ProfileInputUtils.resolveOptionalCenter(this, portId, fallback);
    }

    protected final @Nullable ProfilePlaneUtils.Basis resolveConstructionBasis(
            PlaneData plane,
            String xAxisPortId
    ) {
        Vector3d preferredAxis = ProfileInputUtils.resolveOptionalInPlaneAxis(this, xAxisPortId, plane);
        if (ProfileInputUtils.isConnected(this, xAxisPortId) && preferredAxis == null) {
            return null;
        }
        return ProfilePlaneUtils.createBasis(plane, preferredAxis);
    }

    protected final @Nullable ProfilePlaneUtils.Basis resolveConstructionBasis(
            PlaneData plane,
            @Nullable Vector3d preferredAxis
    ) {
        return ProfilePlaneUtils.createBasis(plane, preferredAxis);
    }

    protected final @Nullable Integer resolveBoundedInteger(String portId, int fallback, int min, int max) {
        return ProfileInputUtils.resolveOptionalBoundedExactInteger(this, portId, fallback, min, max);
    }

    protected final @Nullable Integer resolvePositiveInteger(String portId, int fallback) {
        return ProfileInputUtils.resolveOptionalExactPositiveInteger(this, portId, fallback);
    }

    protected final @Nullable Double resolveFiniteDouble(String portId, double fallback) {
        return ProfileInputUtils.resolveOptionalFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolvePositiveDouble(String portId, double fallback) {
        return ProfileInputUtils.resolveOptionalPositiveFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolveNonNegativeDouble(String portId, double fallback) {
        return ProfileInputUtils.resolveOptionalNonNegativeFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable PolygonProfileData resolveStrictProfile(String portId) {
        return ProfileInputUtils.resolveStrictProfile(this, portId);
    }

    protected final @Nullable PlanarRegionData resolveStrictRegion(String portId) {
        return ProfileInputUtils.resolveStrictRegion(this, portId);
    }

    protected final @Nullable PathData pathFromProfile(@Nullable PolygonProfileData profile) {
        return profile == null ? null : profile.getBoundaryPath();
    }

    protected final boolean isWithinProfileVertices(int vertexCount) {
        return GenerationLimits.isWithinProfileVertices(vertexCount);
    }

    protected static void restoreFiniteDouble(Map<?, ?> map, String key, DoubleConsumer setter) {
        if (map.get(key) instanceof Double value && Double.isFinite(value)) {
            setter.accept(value);
        }
    }

    protected static void restoreInteger(Map<?, ?> map, String key, IntConsumer setter) {
        if (map.get(key) instanceof Integer value) {
            setter.accept(value);
        }
    }

    /**
     * Writes shared region-op outputs (Region / Regions / Profile / Profiles / Plane / Center / Count).
     */
    protected final void writeRegionOpSuccess(
            ProfilePlanarOps.RegionOpOutcome outcome,
            String regionId,
            String regionsId,
            String profileId,
            String profilesId,
            String planeId,
            String centerId,
            String countId
    ) {
        List<PlanarRegionData> regions = outcome.regions();
        if (regions.isEmpty()) {
            putNullOutputs(regionId, profileId, centerId);
            putEmptyListOutputs(regionsId, profilesId);
            outputValues.put(planeId, outcome.plane());
            putIntOutputs(0, countId);
            markSuccess();
            return;
        }

        PlaneProjectionUtils.PlaneAxes axes =
            PlaneProjectionUtils.PlaneAxes.from(regions.getFirst().plane());
        PlanarRegionData primary = ProfilePlanarOps.selectPrimaryRegion(regions, axes);
        List<PolygonProfileData> outers = new ArrayList<>(regions.size());
        for (PlanarRegionData region : regions) {
            outers.add(region.outer());
        }

        outputValues.put(regionId, primary);
        outputValues.put(regionsId, new ArrayList<>(regions));
        outputValues.put(profileId, primary.outer());
        outputValues.put(profilesId, outers);
        outputValues.put(planeId, primary.plane());
        outputValues.put(centerId, new PointData(primary.outer().getCenter()));
        putIntOutputs(regions.size(), countId);
        markSuccess();
    }
}
