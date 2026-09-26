package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.TwistedSdfData;
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
    id = "transform.deformations.twist_geometry",
    displayName = "Twist Geometry",
    description = "Applies an axial twist domain deformation to SDF or geometry, outputting a twisted SDF-backed Geometry",
    category = "transform.deformations",
    order = 9
)
public class TwistGeometryNode extends BaseNode {

    @NodeProperty(displayName = "Angle Degrees", category = "Twist", order = 1)
    private double angleDegrees = 180.0d;

    @NodeProperty(displayName = "Twist Length", category = "Twist", order = 2)
    private double twistLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Twist", order = 3)
    private TwistedSdfData.ClampMode clampMode = TwistedSdfData.ClampMode.CLAMP;

    @NodeProperty(displayName = "Bounds Padding", category = "Bounds", order = 4)
    private double boundsPadding = 2.0d;

    @NodeProperty(displayName = "Bounds Samples", category = "Bounds", order = 5,
        description = "Grid samples per axis for estimating the twisted output bounds")
    private int boundsSamples = 5;

    @NodeProperty(displayName = "Fill Source Geometry", category = "Approximation", order = 6,
        description = "When twisting non-SDF geometry, voxelize it as a solid before building the approximate source SDF")
    private boolean fillSourceGeometry = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_ANGLE_DEGREES_ID = "input_angle_degrees";
    private static final String INPUT_TWIST_LENGTH_ID = "input_twist_length";
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

    public TwistGeometryNode() {
        super(UUID.randomUUID(), "transform.deformations.twist_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Geometry to twist. SDF Geometry stays continuous; other geometry is converted to an approximate voxel SDF.",
            NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF",
            "Optional source SDF when no Geometry is connected", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin",
            "Point on the twist axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction",
            "Twist axis direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_DEGREES_ID, "Angle Degrees",
            "Total twist angle over the twist length", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TWIST_LENGTH_ID, "Twist Length",
            "Axial length over which the angle is distributed", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MIN_ID, "Bounds Min",
            "Optional source sampling minimum when using a raw SDF", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BOUNDS_MAX_ID, "Bounds Max",
            "Optional source sampling maximum when using a raw SDF", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ISO_ID, "Iso Value",
            "SDF iso-surface threshold for Geometry output", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Twisted SDF-backed Geometry for voxel baking", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF",
            "Twisted signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MIN_ID, "Bounds Min",
            "Estimated output bounds minimum", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MAX_ID, "Bounds Max",
            "Estimated output bounds maximum", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_APPROXIMATE_ID, "Approximate",
            "True when non-SDF geometry was converted to a voxel SDF before twisting", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_VOXELS_ID, "Source Voxels",
            "Voxel count used for approximate non-SDF geometry input", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when twist geometry was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies an axial twist domain deformation to SDF or geometry, outputting a twisted SDF-backed Geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        SourceData source = resolveSource();
        Vector3d axisOrigin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_AXIS_ORIGIN_ID, new Vector3d());
        Vector3d axisDirection = OptionalPortDrive.resolveOptionalVector(this, INPUT_AXIS_DIRECTION_ID, null);
        Double resolvedAngle = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ANGLE_DEGREES_ID, angleDegrees);
        Double resolvedLength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_TWIST_LENGTH_ID, twistLength);

        if (source == null
                || axisOrigin == null
                || !VectorUtils.isNonZero(axisDirection)
                || resolvedAngle == null
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
        TwistedSdfData twisted = new TwistedSdfData(
            source.sdf,
            axisOrigin,
            axis,
            resolvedAngle,
            resolvedLength,
            clampMode
        );
        Bounds outputBounds = estimateTwistedBounds(
            source.min,
            source.max,
            twisted,
            GenerationLimits.clampBoundsSamples(boundsSamples),
            Math.max(0.0d, boundsPadding)
        );
        if (outputBounds == null || !outputBounds.isValid()) {
            writeInvalid();
            return;
        }

        GeometryData geometry = new SdfGeometryData(twisted, outputBounds.min, outputBounds.max, iso);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_SDF_ID, twisted);
        outputValues.put(OUTPUT_BOUNDS_MIN_ID, new PointData(outputBounds.min));
        outputValues.put(OUTPUT_BOUNDS_MAX_ID, new PointData(outputBounds.max));
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
            if (region == null || !region.isComplete()) {
                return null;
            }
            BlockPosList blocks = GeometryVoxelizer.voxelize(geometry, fillSourceGeometry);
            if (blocks.isEmpty() || blocks.size() > GenerationLimits.MAX_DEFORM_SOURCE_VOXELS) {
                return null;
            }
            Bounds bounds = boundsFromRegion(region);
            if (bounds == null || !bounds.isValid()) {
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
        Bounds bounds = resolveInputBounds();
        if (bounds == null || !bounds.isValid()) {
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                return null;
            }
            bounds = new Bounds(estimated.min(), estimated.max()).expanded(Math.max(0.0d, boundsPadding));
        }
        return new SourceData(sdf, bounds.min, bounds.max, 0.0d, false, 0);
    }

    private @Nullable Bounds resolveInputBounds() {
        boolean minConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MIN_ID);
        boolean maxConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MAX_ID);
        if (!minConnected && !maxConnected) {
            return null;
        }
        Vector3d min = OptionalPortDrive.resolveOptionalPoint(this, INPUT_BOUNDS_MIN_ID, null);
        Vector3d max = OptionalPortDrive.resolveOptionalPoint(this, INPUT_BOUNDS_MAX_ID, null);
        if (!PointUtils.isFinite(min) || !PointUtils.isFinite(max)) {
            return null;
        }
        return new Bounds(min, max);
    }

    private static @Nullable Bounds boundsFromRegion(RegionData region) {
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

    private static @Nullable Bounds estimateTwistedBounds(Vector3d sourceMin,
                                                          Vector3d sourceMax,
                                                          TwistedSdfData twisted,
                                                          int samplesPerAxis,
                                                          double padding) {
        Bounds bounds = null;
        int samples = GenerationLimits.clampBoundsSamples(samplesPerAxis);
        for (int ix = 0; ix < samples; ix++) {
            double x = lerp(sourceMin.x, sourceMax.x, ix / (double) (samples - 1));
            for (int iy = 0; iy < samples; iy++) {
                double y = lerp(sourceMin.y, sourceMax.y, iy / (double) (samples - 1));
                for (int iz = 0; iz < samples; iz++) {
                    double z = lerp(sourceMin.z, sourceMax.z, iz / (double) (samples - 1));
                    Vector3d p = twisted.twistPoint(new Vector3d(x, y, z));
                    bounds = bounds == null ? new Bounds(p, p) : bounds.include(p);
                }
            }
        }
        return bounds == null ? null : bounds.expanded(padding);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(double angleDegrees) {
        if (Double.isFinite(angleDegrees)) {
            this.angleDegrees = angleDegrees;
            markDirty();
        }
    }

    public double getTwistLength() {
        return twistLength;
    }

    public void setTwistLength(double twistLength) {
        if (Double.isFinite(twistLength) && twistLength > 0.0d) {
            this.twistLength = twistLength;
            markDirty();
        }
    }

    public TwistedSdfData.ClampMode getClampMode() {
        return clampMode;
    }

    public void setClampMode(TwistedSdfData.ClampMode clampMode) {
        this.clampMode = clampMode == null ? TwistedSdfData.ClampMode.CLAMP : clampMode;
        markDirty();
    }

    public void setClampModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setClampMode(TwistedSdfData.ClampMode.CLAMP);
            return;
        }
        try {
            setClampMode(TwistedSdfData.ClampMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setClampMode(TwistedSdfData.ClampMode.CLAMP);
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
        state.put("angleDegrees", angleDegrees);
        state.put("twistLength", twistLength);
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
        if (map.get("angleDegrees") instanceof Number value) {
            setAngleDegrees(value.doubleValue());
        }
        if (map.get("twistLength") instanceof Number value) {
            setTwistLength(value.doubleValue());
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
            return VectorUtils.isFinite(min)
                && VectorUtils.isFinite(max)
                && min.x <= max.x
                && min.y <= max.y
                && min.z <= max.z;
        }

        Bounds include(Vector3d point) {
            return new Bounds(
                new Vector3d(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z)),
                new Vector3d(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z))
            );
        }

        Bounds expanded(double padding) {
            double p = Math.max(0.0d, padding);
            return new Bounds(
                new Vector3d(min).sub(p, p, p),
                new Vector3d(max).add(p, p, p)
            );
        }
    }
}
