package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.surface_volume_distribution.image_scatter",
    displayName = "Image Scatter",
    description = "Scatters points using an exact-size density map on a plane or world XZ (file images via Read Image upstream)",
    category = "pattern.surface_volume_distribution",
    order = 5
)
public class ImageBasedScatterNode extends AbstractSurfaceVolumeDistributionNode {

    @NodeProperty(displayName = "Target Count", category = "Scatter", order = 1)
    private int targetCount = 256;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Threshold", category = "Scatter", order = 3)
    private double threshold = 0.0d;

    @NodeProperty(displayName = "Invert", category = "Scatter", order = 4)
    private boolean invert = false;

    @NodeProperty(displayName = "Span U", category = "Scatter", order = 5)
    private double spanU = 1.0d;

    @NodeProperty(displayName = "Span V", category = "Scatter", order = 6)
    private double spanV = 1.0d;

    private static final String INPUT_DENSITY_VALUES_ID = "input_density_values";
    private static final String INPUT_IMAGE_WIDTH_ID = "input_image_width";
    private static final String INPUT_IMAGE_HEIGHT_ID = "input_image_height";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_SPAN_U_ID = "input_span_u";
    private static final String INPUT_SPAN_V_ID = "input_span_v";
    private static final String INPUT_TARGET_COUNT_ID = "input_target_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_THRESHOLD_ID = "input_threshold";
    private static final String INPUT_INVERT_ID = "input_invert";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_U_VALUES_ID = "output_u_values";
    private static final String OUTPUT_V_VALUES_ID = "output_v_values";
    private static final String OUTPUT_DENSITY_VALUES_ID = "output_density_values";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ImageBasedScatterNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.image_scatter");
        addInputPort(new BasePort(INPUT_DENSITY_VALUES_ID, "Density Values", "Flattened row-major density values in [0,1]; size must equal Width×Height", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_IMAGE_WIDTH_ID, "Image Width", "Image width in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_IMAGE_HEIGHT_ID, "Image Height", "Image height in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional target plane (unconnected → world XZ)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional scatter origin point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SPAN_U_ID, "Span U", "World span along U axis (> 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPAN_V_ID, "Span V", "World span along V axis (> 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Exact number of scattered points", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold", "Density threshold in [0,1]", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INVERT_ID, "Invert", "Invert density values", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_U_VALUES_ID, "U Values", "Normalized U coordinate per point", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_V_VALUES_ID, "V Values", "Normalized V coordinate per point", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DENSITY_VALUES_ID, "Density Values", "Sampled density per point", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of scattered points", NodeDataType.INTEGER, this));
        addValidErrorAndCompleteOutputs();
    }

    @Override
    public String getDescription() {
        return "Scatters points using an exact-size density map on a plane or world XZ";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ImageData image = resolveDensityData();
        if (image == null) {
            return; // resolveDensityData already wrote failure
        }

        Integer resolvedTarget = resolveLayoutCount(INPUT_TARGET_COUNT_ID, targetCount);
        if (resolvedTarget == null) {
            writeFail(countFailureReason(INPUT_TARGET_COUNT_ID, targetCount));
            return;
        }

        Integer resolvedSeed = resolveSeed(INPUT_SEED_ID, seed);
        if (resolvedSeed == null) {
            writeFail("Seed connected but invalid");
            return;
        }

        Double resolvedThreshold = OptionalPortDrive.resolveOptionalDouble(this, INPUT_THRESHOLD_ID, threshold);
        if (resolvedThreshold == null
                || !Double.isFinite(resolvedThreshold)
                || resolvedThreshold < 0.0d
                || resolvedThreshold > 1.0d) {
            writeFail("Threshold must be finite and in [0,1]");
            return;
        }

        Boolean resolvedInvert = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_INVERT_ID, invert);
        if (resolvedInvert == null) {
            writeFail("Invert connected but invalid");
            return;
        }

        Double resolvedSpanU = resolvePositiveFinite(INPUT_SPAN_U_ID, spanU);
        Double resolvedSpanV = resolvePositiveFinite(INPUT_SPAN_V_ID, spanV);
        if (resolvedSpanU == null || resolvedSpanV == null) {
            writeFail("Span U/V must be finite and > 0");
            return;
        }

        PlaneData plane = null;
        if (OptionalPortDrive.isConnected(this, INPUT_PLANE_ID)) {
            PlaneData resolved = OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
            if (resolved == null) {
                writeFail("Plane connected but invalid");
                return;
            }
            plane = resolved.normalized();
            if (plane == null) {
                writeFail("Plane is degenerate");
                return;
            }
        }

        Vector3d origin;
        if (OptionalPortDrive.isConnected(this, INPUT_ORIGIN_ID)) {
            origin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_ORIGIN_ID, null);
            if (origin == null) {
                writeFail("Origin connected but invalid");
                return;
            }
        } else if (plane != null) {
            origin = new Vector3d(plane.getPoint());
        } else {
            origin = new Vector3d();
        }

        double[] cumulative = buildCumulativeDensity(image.values, resolvedThreshold, resolvedInvert);
        if (cumulative.length == 0
                || !Double.isFinite(cumulative[cumulative.length - 1])
                || cumulative[cumulative.length - 1] <= 1.0e-9d) {
            writeFail("Density map has zero usable weight");
            return;
        }

        PlaneProjectionUtils.PlaneAxes axes = plane != null ? PlaneProjectionUtils.PlaneAxes.from(plane) : null;

        Random rng = new Random(resolvedSeed);
        List<Vector3d> points = new ArrayList<>(resolvedTarget);
        List<Double> uValues = new ArrayList<>(resolvedTarget);
        List<Double> vValues = new ArrayList<>(resolvedTarget);
        List<Double> densityValues = new ArrayList<>(resolvedTarget);

        for (int i = 0; i < resolvedTarget; i++) {
            int pixel = sampleIndex(cumulative, rng);
            int px = pixel % image.width;
            int py = pixel / image.width;

            double jitterX = rng.nextDouble();
            double jitterY = rng.nextDouble();
            double u01 = (px + jitterX) / image.width;
            double v01 = (py + jitterY) / image.height;

            double localU = (u01 - 0.5d) * resolvedSpanU;
            double localV = (v01 - 0.5d) * resolvedSpanV;
            Vector3d world = toWorldPoint(origin, axes, localU, localV);
            if (!PointUtils.isFinite(world)) {
                writeFail("Non-finite output point");
                return;
            }
            points.add(world);
            uValues.add(u01);
            vValues.add(v01);
            densityValues.add(density(image.values.get(pixel), resolvedThreshold, resolvedInvert));
        }

        if (points.size() != resolvedTarget) {
            writeFail("Emitted count does not match Target Count");
            return;
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_U_VALUES_ID, List.copyOf(uValues));
        outputValues.put(OUTPUT_V_VALUES_ID, List.copyOf(vValues));
        outputValues.put(OUTPUT_DENSITY_VALUES_ID, List.copyOf(densityValues));
        putIntOutputs(points.size(), OUTPUT_COUNT_ID);
        markSuccess(true);
    }

    private @Nullable ImageData resolveDensityData() {
        Integer width = OptionalPortDrive.resolveOptionalInteger(this, INPUT_IMAGE_WIDTH_ID, Integer.MIN_VALUE);
        Integer height = OptionalPortDrive.resolveOptionalInteger(this, INPUT_IMAGE_HEIGHT_ID, Integer.MIN_VALUE);
        boolean widthConnected = OptionalPortDrive.isConnected(this, INPUT_IMAGE_WIDTH_ID);
        boolean heightConnected = OptionalPortDrive.isConnected(this, INPUT_IMAGE_HEIGHT_ID);
        boolean densityConnected = OptionalPortDrive.isConnected(this, INPUT_DENSITY_VALUES_ID);

        if (!densityConnected || !widthConnected || !heightConnected) {
            writeFail("Density Values, Width, and Height must all be connected");
            return null;
        }
        if (width == null || height == null) {
            writeFail("Width/Height connected but invalid");
            return null;
        }
        if (width < 1 || height < 1) {
            writeFail("Width and Height must be >= 1");
            return null;
        }

        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, (long) height);
        } catch (ArithmeticException overflow) {
            writeFail("Image pixel product overflows");
            return null;
        }
        if (pixels > GenerationLimits.MAX_IMAGE_PIXELS) {
            writeFail("Image pixel product exceeds MAX_IMAGE_PIXELS");
            return null;
        }

        Object densityObj = inputValues.get(INPUT_DENSITY_VALUES_ID);
        if (!(densityObj instanceof List<?> list)) {
            writeFail("Density Values connected but invalid");
            return null;
        }
        if (list.size() != pixels) {
            writeFail("Density Values size must equal Width × Height");
            return null;
        }

        List<Double> values = new ArrayList<>((int) pixels);
        for (Object item : list) {
            if (!(item instanceof Number n)) {
                writeFail("Density Values must be finite numbers in [0,1]");
                return null;
            }
            double v = n.doubleValue();
            if (!Double.isFinite(v) || v < 0.0d || v > 1.0d) {
                writeFail("Density Values must be finite numbers in [0,1]");
                return null;
            }
            values.add(v);
        }
        return new ImageData(width, height, List.copyOf(values));
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_U_VALUES_ID, OUTPUT_V_VALUES_ID, OUTPUT_DENSITY_VALUES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    private double[] buildCumulativeDensity(List<Double> values, double thresholdValue, boolean invertDensity) {
        double[] cumulative = new double[values.size()];
        double acc = 0.0d;
        for (int i = 0; i < values.size(); i++) {
            double d = density(values.get(i), thresholdValue, invertDensity);
            if (!Double.isFinite(d)) {
                return new double[0];
            }
            acc += d;
            cumulative[i] = acc;
        }
        return cumulative;
    }

    private double density(double gray, double thresholdValue, boolean invertDensity) {
        if (!Double.isFinite(gray)) {
            return Double.NaN;
        }
        double d = invertDensity ? (1.0d - gray) : gray;
        if (d <= thresholdValue) {
            return 0.0d;
        }
        return (d - thresholdValue) / Math.max(1.0e-9d, 1.0d - thresholdValue);
    }

    private int sampleIndex(double[] cumulative, Random rng) {
        double total = cumulative[cumulative.length - 1];
        double target = rng.nextDouble() * total;
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (target <= cumulative[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    private Vector3d toWorldPoint(Vector3d origin, @Nullable PlaneProjectionUtils.PlaneAxes axes, double localU, double localV) {
        if (axes != null) {
            Vector2d uv = axes.to2d(origin);
            return axes.from2d(new Vector2d(uv.x + localU, uv.y + localV));
        }
        return new Vector3d(origin.x + localU, origin.y, origin.z + localV);
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
        markDirty();
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
        markDirty();
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
        markDirty();
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
        markDirty();
    }

    public double getSpanU() {
        return spanU;
    }

    public void setSpanU(double spanU) {
        this.spanU = spanU;
        markDirty();
    }

    public double getSpanV() {
        return spanV;
    }

    public void setSpanV(double spanV) {
        this.spanV = spanV;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("targetCount", targetCount);
        state.put("seed", seed);
        state.put("threshold", threshold);
        state.put("invert", invert);
        state.put("spanU", spanU);
        state.put("spanV", spanV);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("targetCount") instanceof Number countValue) {
            setTargetCount(countValue.intValue());
        }
        if (map.get("seed") instanceof Number seedValue) {
            setSeed(seedValue.intValue());
        }
        if (map.get("threshold") instanceof Number thresholdValue) {
            setThreshold(thresholdValue.doubleValue());
        }
        if (map.get("invert") instanceof Boolean invertValue) {
            setInvert(invertValue);
        }
        if (map.get("spanU") instanceof Number spanUValue) {
            setSpanU(spanUValue.doubleValue());
        }
        if (map.get("spanV") instanceof Number spanVValue) {
            setSpanV(spanVValue.doubleValue());
        }
    }

    private record ImageData(int width, int height, List<Double> values) {}
}
