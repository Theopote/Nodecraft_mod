package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BentSdfData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VoxelizedGeometrySdfData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import com.nodecraft.nodesystem.util.VectorUtils;
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
    order = 10
)
public class BendGeometryNode extends BaseNode {

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
    public String getDescription() {
        return "Applies an axial bend domain deformation to SDF or geometry before voxelization";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SourceData source = resolveSource();
        Vector3d axisOrigin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_AXIS_ORIGIN_ID, new Vector3d());
        Vector3d axisDirection = OptionalPortDrive.resolveOptionalVector(
            this, INPUT_AXIS_DIRECTION_ID, new Vector3d(1.0d, 0.0d, 0.0d));
        Vector3d bendNormal = OptionalPortDrive.resolveOptionalVector(
            this, INPUT_BEND_NORMAL_ID, new Vector3d(0.0d, 1.0d, 0.0d));
        Double resolvedDegrees = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BEND_DEGREES_ID, bendDegrees);
        Double resolvedLength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BEND_LENGTH_ID, bendLength);

        if (source == null
                || axisOrigin == null
                || !VectorUtils.isNonZero(axisDirection)
                || !VectorUtils.isNonZero(bendNormal)
                || resolvedDegrees == null
                || resolvedLength == null
                || resolvedLength <= 0.0d) {
            writeInvalid();
            return;
        }

        Double iso = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ISO_ID, source.isoValue);
        if (iso == null) {
            writeInvalid();
            return;
        }

        Vector3d axis = new Vector3d(axisDirection).normalize();
        Vector3d normal = new Vector3d(bendNormal).normalize();
        BentSdfData bent = new BentSdfData(
            source.sdf, axisOrigin, axis, normal, resolvedDegrees, resolvedLength, clampMode);
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

    /**
     * Connection-aware source precedence: Geometry connected → Geometry only (never SDF);
     * Geometry unconnected → SDF port (unconnected+null / connected-invalid → fail).
     */
    private @Nullable SourceData resolveSource() {
        if (OptionalPortDrive.isConnected(this, INPUT_GEOMETRY_ID)) {
            return resolveGeometrySource(inputValues.get(INPUT_GEOMETRY_ID));
        }
        return resolveSdfSource();
    }

    private @Nullable SourceData resolveGeometrySource(@Nullable Object geometryObj) {
        if (geometryObj instanceof SdfGeometryData sdfGeometry) {
            return new SourceData(
                sdfGeometry.sdf(),
                sdfGeometry.min(),
                sdfGeometry.max(),
                sdfGeometry.isoValue(),
                false,
                0
            );
        }
        if (geometryObj instanceof GeometryData geometry) {
            RegionData region = GeometryVoxelizer.createBoundingRegion(geometry);
            BlockPosList blocks = GeometryVoxelizer.voxelize(geometry, fillSourceGeometry);
            Bounds bounds = boundsFromRegion(region);
            if (bounds == null
                    || blocks.isEmpty()
                    || blocks.size() > GenerationLimits.MAX_DEFORM_SOURCE_VOXELS) {
                return null;
            }
            return new SourceData(
                new VoxelizedGeometrySdfData(blocks),
                bounds.min,
                bounds.max,
                0.0d,
                true,
                blocks.size()
            );
        }
        return null;
    }

    private @Nullable SourceData resolveSdfSource() {
        boolean sdfConnected = OptionalPortDrive.isConnected(this, INPUT_SDF_ID);
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (sdfConnected) {
            if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
                return null;
            }
            return buildSdfSource(sdf);
        }
        // Unconnected: null → fail; a present valid SDF (e.g. compute() injection) is accepted.
        if (sdfObj == null) {
            return null;
        }
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            return null;
        }
        return buildSdfSource(sdf);
    }

    private @Nullable SourceData buildSdfSource(SignedDistanceFieldData sdf) {
        BoundsResolution boundsResolution = resolveInputBounds();
        if (boundsResolution.status == BoundsStatus.INVALID) {
            return null;
        }
        Bounds bounds = boundsResolution.bounds;
        if (boundsResolution.status == BoundsStatus.ABSENT) {
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                return null;
            }
            bounds = new Bounds(estimated.min(), estimated.max());
        }
        return new SourceData(sdf, bounds.min, bounds.max, 0.0d, false, 0);
    }

    /**
     * Bounds Min/Max are a paired optional input:
     * both unconnected → ABSENT (auto-estimate allowed);
     * both connected and valid → VALID;
     * half-connected or invalid → INVALID (fail closed, never estimate).
     */
    private BoundsResolution resolveInputBounds() {
        boolean minConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MIN_ID);
        boolean maxConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MAX_ID);
        if (!minConnected && !maxConnected) {
            return BoundsResolution.absent();
        }
        if (minConnected != maxConnected) {
            return BoundsResolution.invalid();
        }
        Vector3d min = OptionalPortDrive.resolveOptionalPoint(this, INPUT_BOUNDS_MIN_ID, null);
        Vector3d max = OptionalPortDrive.resolveOptionalPoint(this, INPUT_BOUNDS_MAX_ID, null);
        if (!PointUtils.isFinite(min) || !PointUtils.isFinite(max)
                || min.x > max.x || min.y > max.y || min.z > max.z) {
            return BoundsResolution.invalid();
        }
        Bounds bounds = new Bounds(min, max);
        if (!bounds.isValid()) {
            return BoundsResolution.invalid();
        }
        return BoundsResolution.valid(bounds);
    }

    private enum BoundsStatus {
        ABSENT,
        VALID,
        INVALID
    }

    private record BoundsResolution(BoundsStatus status, @Nullable Bounds bounds) {
        static BoundsResolution absent() {
            return new BoundsResolution(BoundsStatus.ABSENT, null);
        }

        static BoundsResolution invalid() {
            return new BoundsResolution(BoundsStatus.INVALID, null);
        }

        static BoundsResolution valid(Bounds bounds) {
            return new BoundsResolution(BoundsStatus.VALID, bounds);
        }
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

    public double getBendDegrees() {
        return bendDegrees;
    }

    public void setBendDegrees(double bendDegrees) {
        if (Double.isFinite(bendDegrees)) {
            this.bendDegrees = bendDegrees;
            markDirty();
        }
    }

    public double getBendLength() {
        return bendLength;
    }

    public void setBendLength(double bendLength) {
        if (Double.isFinite(bendLength) && bendLength > 0.0d) {
            this.bendLength = bendLength;
            markDirty();
        }
    }

    public BentSdfData.ClampMode getClampMode() {
        return clampMode;
    }

    public void setClampMode(BentSdfData.ClampMode clampMode) {
        this.clampMode = clampMode == null ? BentSdfData.ClampMode.CLAMP : clampMode;
        markDirty();
    }

    private void setClampModeString(String value) {
        if (value == null || value.isBlank()) {
            setClampMode(BentSdfData.ClampMode.CLAMP);
            return;
        }
        try {
            setClampMode(BentSdfData.ClampMode.valueOf(value.trim().toUpperCase()));
        } catch (RuntimeException ignored) {
            setClampMode(BentSdfData.ClampMode.CLAMP);
        }
    }

    public double getBoundsPadding() {
        return boundsPadding;
    }

    public void setBoundsPadding(double boundsPadding) {
        if (Double.isFinite(boundsPadding) && boundsPadding >= 0.0d) {
            this.boundsPadding = boundsPadding;
            markDirty();
        }
    }

    public int getBoundsSamples() {
        return boundsSamples;
    }

    public void setBoundsSamples(int boundsSamples) {
        this.boundsSamples = GenerationLimits.clampBoundsSamples(boundsSamples);
        markDirty();
    }

    public boolean isFillSourceGeometry() {
        return fillSourceGeometry;
    }

    public void setFillSourceGeometry(boolean fillSourceGeometry) {
        this.fillSourceGeometry = fillSourceGeometry;
        markDirty();
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
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("bendDegrees") instanceof Number value) {
            setBendDegrees(value.doubleValue());
        }
        if (map.get("bendLength") instanceof Number value) {
            setBendLength(value.doubleValue());
        }
        if (map.get("clampMode") instanceof String value) {
            setClampModeString(value);
        }
        if (map.get("boundsPadding") instanceof Number value) {
            setBoundsPadding(value.doubleValue());
        }
        if (map.get("boundsSamples") instanceof Number value) {
            setBoundsSamples(value.intValue());
        }
        if (map.get("fillSourceGeometry") instanceof Boolean value) {
            setFillSourceGeometry(value);
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

        boolean isValid() {
            return PointUtils.isFinite(min)
                && PointUtils.isFinite(max)
                && min.x <= max.x
                && min.y <= max.y
                && min.z <= max.z;
        }
    }
}
