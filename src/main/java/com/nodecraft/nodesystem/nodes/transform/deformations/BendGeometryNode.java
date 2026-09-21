package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BentSdfData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VoxelizedGeometrySdfData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.bend_geometry",
    displayName = "Bend Geometry",
    description = "Applies an axial bend domain deformation to SDF or geometry before voxelization",
    category = "transform.deformations",
    order = 9
)
public class BendGeometryNode extends BaseNode {
    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Bend Degrees", category = "Bend", order = 1)
    private double bendDegrees = 90.0d;

    @NodeProperty(displayName = "Bend Length", category = "Bend", order = 2)
    private double bendLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Bend", order = 3)
    private BentSdfData.ClampMode clampMode = BentSdfData.ClampMode.CLAMP;

    @NodeProperty(displayName = "Bounds Padding", category = "Bounds", order = 4)
    private double boundsPadding = 2.0d;

    @NodeProperty(displayName = "Bounds Samples", category = "Bounds", order = 5)
    private int boundsSamples = 5;

    @NodeProperty(displayName = "Fill Source Geometry", category = "Approximation", order = 6)
    private boolean fillSourceGeometry = true;

    @NodeProperty(displayName = "Max Source Voxels", category = "Approximation", order = 7)
    private int maxSourceVoxels = 32768;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_BEND_NORMAL_ID = "input_bend_normal";
    private static final String INPUT_BEND_DEGREES_ID = "input_bend_degrees";
    private static final String INPUT_BEND_LENGTH_ID = "input_bend_length";
    private static final String INPUT_BOUNDS_MIN_ID = "input_bounds_min";
    private static final String INPUT_BOUNDS_MAX_ID = "input_bounds_max";
    private static final String INPUT_ISO_ID = "input_iso";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_BOUNDS_MIN_ID = "output_bounds_min";
    private static final String OUTPUT_BOUNDS_MAX_ID = "output_bounds_max";
    private static final String OUTPUT_APPROXIMATE_ID = "output_approximate";
    private static final String OUTPUT_SOURCE_VOXELS_ID = "output_source_voxels";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BendGeometryNode() {
        super(UUID.randomUUID(), "transform.deformations.bend_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to bend; SDF Geometry stays continuous", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Optional source SDF when no Geometry is connected", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Point on the bend axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Direction along which bend is distributed", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_BEND_NORMAL_ID, "Bend Normal", "Direction the bend curves toward", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_BEND_DEGREES_ID, "Bend Degrees", "Total bend angle over the bend length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEND_LENGTH_ID, "Bend Length", "Length over which the angle is distributed", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MIN_ID, "Bounds Min", "Optional source sampling minimum when using a raw SDF", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MAX_ID, "Bounds Max", "Optional source sampling maximum when using a raw SDF", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ISO_ID, "Iso Value", "SDF iso-surface threshold for Geometry output", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Bent SDF-backed Geometry for voxel baking", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Bent signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MIN_ID, "Bounds Min", "Estimated output bounds minimum", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MAX_ID, "Bounds Max", "Estimated output bounds maximum", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_APPROXIMATE_ID, "Approximate", "True when non-SDF geometry was voxelized before bending", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_VOXELS_ID, "Source Voxels", "Voxel count used for approximate non-SDF geometry input", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when bend geometry was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SourceData source = resolveSource();
        Vector3d axisOrigin = resolvePoint(inputValues.get(INPUT_AXIS_ORIGIN_ID), new Vector3d());
        Vector3d axisDirection = resolveDirection(inputValues.get(INPUT_AXIS_DIRECTION_ID), new Vector3d(1.0d, 0.0d, 0.0d));
        Vector3d bendNormal = resolveDirection(inputValues.get(INPUT_BEND_NORMAL_ID), new Vector3d(0.0d, 1.0d, 0.0d));
        if (source == null || axisDirection == null || bendNormal == null) {
            writeInvalid();
            return;
        }

        double resolvedDegrees = resolveDouble(inputValues.get(INPUT_BEND_DEGREES_ID), bendDegrees);
        double resolvedLength = Math.max(EPS, Math.abs(resolveDouble(inputValues.get(INPUT_BEND_LENGTH_ID), bendLength)));
        double iso = resolveDouble(inputValues.get(INPUT_ISO_ID), source.isoValue);

        BentSdfData bent = new BentSdfData(source.sdf, axisOrigin, axisDirection, bendNormal, resolvedDegrees, resolvedLength, clampMode);
        SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(bent);
        if (estimated == null || !estimated.isValid()) {
            writeInvalid();
            return;
        }

        SdfBoundsEstimator.AxisAlignedBounds outputBounds = estimated.expanded(Math.max(0.0d, boundsPadding));
        GeometryData geometry = new SdfGeometryData(bent, outputBounds.min(), outputBounds.max(), iso);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_SDF_ID, bent);
        outputValues.put(OUTPUT_BOUNDS_MIN_ID, new PointData(outputBounds.min()));
        outputValues.put(OUTPUT_BOUNDS_MAX_ID, new PointData(outputBounds.max()));
        outputValues.put(OUTPUT_APPROXIMATE_ID, source.approximate);
        outputValues.put(OUTPUT_SOURCE_VOXELS_ID, source.sourceVoxelCount);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private @Nullable SourceData resolveSource() {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (geometryObj instanceof SdfGeometryData sdfGeometry) {
            return new SourceData(sdfGeometry.getSdf(), sdfGeometry.getMin(), sdfGeometry.getMax(), sdfGeometry.getIsoValue(), false, 0);
        }
        if (geometryObj instanceof GeometryData geometry) {
            RegionData region = GeometryVoxelizer.createBoundingRegion(geometry);
            BlockPosList blocks = GeometryVoxelizer.voxelize(geometry, fillSourceGeometry);
            Bounds bounds = boundsFromRegion(region);
            if (bounds == null || blocks.isEmpty() || blocks.size() > Math.max(1, maxSourceVoxels)) {
                return null;
            }
            return new SourceData(new VoxelizedGeometrySdfData(blocks), bounds.min, bounds.max, 0.0d, true, blocks.size());
        }

        if (!(inputValues.get(INPUT_SDF_ID) instanceof SignedDistanceFieldData sdf)) {
            return null;
        }
        Bounds bounds = resolveInputBounds();
        if (bounds == null) {
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                return null;
            }
            bounds = new Bounds(estimated.min(), estimated.max());
        }
        return new SourceData(sdf, bounds.min, bounds.max, 0.0d, false, 0);
    }

    private @Nullable Bounds resolveInputBounds() {
        Vector3d min = resolvePoint(inputValues.get(INPUT_BOUNDS_MIN_ID), null);
        Vector3d max = resolvePoint(inputValues.get(INPUT_BOUNDS_MAX_ID), null);
        if (!isFinite(min) || !isFinite(max) || min.x > max.x || min.y > max.y || min.z > max.z) {
            return null;
        }
        return new Bounds(min, max);
    }

    private static @Nullable Bounds boundsFromRegion(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return null;
        }
        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return null;
        }
        return new Bounds(
            new Vector3d(min.getX(), min.getY(), min.getZ()),
            new Vector3d(max.getX() + 1.0d, max.getY() + 1.0d, max.getZ() + 1.0d)
        );
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_SDF_ID, null);
        outputValues.put(OUTPUT_BOUNDS_MIN_ID, null);
        outputValues.put(OUTPUT_BOUNDS_MAX_ID, null);
        outputValues.put(OUTPUT_APPROXIMATE_ID, false);
        outputValues.put(OUTPUT_SOURCE_VOXELS_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static @Nullable Vector3d resolvePoint(@Nullable Object value, @Nullable Vector3d fallback) {
        Vector3d point = SpatialValueResolver.resolveVector3d(value);
        if (isFinite(point)) {
            return point;
        }
        return fallback == null ? null : new Vector3d(fallback);
    }

    private static @Nullable Vector3d resolveDirection(@Nullable Object value, Vector3d fallback) {
        Vector3d direction = resolvePoint(value, fallback);
        if (!isFinite(direction) || direction.lengthSquared() <= EPS) {
            return null;
        }
        return direction.normalize();
    }

    private static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            double candidate = number.doubleValue();
            return Double.isFinite(candidate) ? candidate : fallback;
        }
        return fallback;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("bendDegrees", bendDegrees);
        state.put("bendLength", bendLength);
        state.put("clampMode", clampMode.name());
        state.put("boundsPadding", boundsPadding);
        state.put("boundsSamples", boundsSamples);
        state.put("fillSourceGeometry", fillSourceGeometry);
        state.put("maxSourceVoxels", maxSourceVoxels);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("bendDegrees") instanceof Number value) bendDegrees = value.doubleValue();
        if (map.get("bendLength") instanceof Number value) bendLength = Math.max(EPS, Math.abs(value.doubleValue()));
        if (map.get("clampMode") instanceof String value) setClampModeString(value);
        if (map.get("boundsPadding") instanceof Number value) boundsPadding = Math.max(0.0d, value.doubleValue());
        if (map.get("boundsSamples") instanceof Number value) boundsSamples = Math.max(2, value.intValue());
        if (map.get("fillSourceGeometry") instanceof Boolean value) fillSourceGeometry = value;
        if (map.get("maxSourceVoxels") instanceof Number value) maxSourceVoxels = Math.max(1, value.intValue());
    }

    private void setClampModeString(String value) {
        try {
            clampMode = BentSdfData.ClampMode.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ignored) {
            clampMode = BentSdfData.ClampMode.CLAMP;
        }
    }

    private record SourceData(
        SignedDistanceFieldData sdf,
        Vector3d min,
        Vector3d max,
        double isoValue,
        boolean approximate,
        int sourceVoxelCount
    ) {
    }

    private record Bounds(Vector3d min, Vector3d max) {
        Bounds {
            min = new Vector3d(min);
            max = new Vector3d(max);
        }
    }
}
