package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.DeterministicSeedUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
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
    description = "Scatters points using image density maps on a plane or world XZ",
    category = "pattern.surface_volume_distribution",
    order = 7
)
public class ImageBasedScatterNode extends BaseNode {

    @NodeProperty(displayName = "Target Count", category = "Scatter", order = 1)
    private int targetCount = 256;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = DeterministicSeedUtils.DEFAULT_SEED;

    @NodeProperty(displayName = "Threshold", category = "Scatter", order = 3)
    private double threshold = 0.0d;

    @NodeProperty(displayName = "Invert", category = "Scatter", order = 4)
    private boolean invert = false;

    private static final String INPUT_DENSITY_VALUES_ID = "input_density_values";
    private static final String INPUT_IMAGE_WIDTH_ID = "input_image_width";
    private static final String INPUT_IMAGE_HEIGHT_ID = "input_image_height";
    private static final String INPUT_IMAGE_PATH_ID = "input_image_path";
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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ImageBasedScatterNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.image_scatter");
        addInputPort(new BasePort(INPUT_DENSITY_VALUES_ID, "Density Values", "Flattened row-major density values in [0,1]", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_IMAGE_WIDTH_ID, "Image Width", "Image width in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_IMAGE_HEIGHT_ID, "Image Height", "Image height in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_IMAGE_PATH_ID, "Image Path", "Optional image file path fallback", NodeDataType.FILE_PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional target plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional scatter origin point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SPAN_U_ID, "Span U", "World span along U axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPAN_V_ID, "Span V", "World span along V axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TARGET_COUNT_ID, "Target Count", "Target number of scattered points", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold", "Density threshold in [0,1]", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INVERT_ID, "Invert", "Invert density values", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_U_VALUES_ID, "U Values", "Normalized U coordinate per point", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_V_VALUES_ID, "V Values", "Normalized V coordinate per point", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DENSITY_VALUES_ID, "Density Values", "Sampled density per point", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of scattered points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when image data is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points using image density maps on a plane or world XZ";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ImageData image = resolveImageData();
        if (image == null || image.width < 1 || image.height < 1 || image.values.isEmpty()) {
            writeInvalid();
            return;
        }

        int requestedTarget = DeterministicSeedUtils.resolveStrictInteger(inputValues.get(INPUT_TARGET_COUNT_ID), targetCount);
        if (requestedTarget <= 0) {
            writeInvalid();
            return;
        }

        int resolvedTarget = GenerationLimits.clampLayoutInstanceCount(requestedTarget);
        if (resolvedTarget <= 0) {
            writeInvalid();
            return;
        }

        int resolvedSeed = DeterministicSeedUtils.resolveSeed(inputValues.get(INPUT_SEED_ID), seed);
        double resolvedThreshold = inputValues.get(INPUT_THRESHOLD_ID) instanceof Number n ? n.doubleValue() : threshold;
        if (!Double.isFinite(resolvedThreshold)) {
            writeInvalid();
            return;
        }
        resolvedThreshold = clamp01(resolvedThreshold);

        boolean resolvedInvert = inputValues.get(INPUT_INVERT_ID) instanceof Boolean b ? b : invert;
        double spanU = inputValues.get(INPUT_SPAN_U_ID) instanceof Number n ? n.doubleValue() : image.width;
        double spanV = inputValues.get(INPUT_SPAN_V_ID) instanceof Number n ? n.doubleValue() : image.height;
        if (!Double.isFinite(spanU) || !Double.isFinite(spanV) || spanU <= 0.0d || spanV <= 0.0d) {
            writeInvalid();
            return;
        }

        double[] cumulative = buildCumulativeDensity(image.values, resolvedThreshold, resolvedInvert);
        if (cumulative.length == 0 || !Double.isFinite(cumulative[cumulative.length - 1]) || cumulative[cumulative.length - 1] <= 1.0e-9d) {
            writeInvalid();
            return;
        }

        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData pd ? pd : null;
        Vector3d origin = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID), plane);
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

            double localU = (u01 - 0.5d) * spanU;
            double localV = (v01 - 0.5d) * spanV;
            Vector3d world = toWorldPoint(origin, axes, localU, localV);
            points.add(world);
            uValues.add(u01);
            vValues.add(v01);
            densityValues.add(density(image.values.get(pixel), resolvedThreshold, resolvedInvert));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_U_VALUES_ID, List.copyOf(uValues));
        outputValues.put(OUTPUT_V_VALUES_ID, List.copyOf(vValues));
        outputValues.put(OUTPUT_DENSITY_VALUES_ID, List.copyOf(densityValues));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_U_VALUES_ID, List.of());
        outputValues.put(OUTPUT_V_VALUES_ID, List.of());
        outputValues.put(OUTPUT_DENSITY_VALUES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private ImageData resolveImageData() {
        Object densityObj = inputValues.get(INPUT_DENSITY_VALUES_ID);
        Object widthObj = inputValues.get(INPUT_IMAGE_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_IMAGE_HEIGHT_ID);
        if (densityObj instanceof List<?> list && widthObj instanceof Integer width && heightObj instanceof Integer height) {
            if (width < 1 || height < 1) {
                return null;
            }
            List<Double> values = new ArrayList<>(width * height);
            for (Object item : list) {
                if (!(item instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                    return null;
                }
                values.add(clamp01(n.doubleValue()));
            }
            if (values.size() >= width * height) {
                return new ImageData(width, height, List.copyOf(values.subList(0, width * height)));
            }
        }
        return readImageFromPath(inputValues.get(INPUT_IMAGE_PATH_ID));
    }

    private ImageData readImageFromPath(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(text.trim()).toAbsolutePath().normalize();
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            List<Double> values = new ArrayList<>(width * height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    ColorData color = ColorData.fromIntARGB(argb);
                    values.add(clamp01(color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d));
                }
            }
            return new ImageData(width, height, List.copyOf(values));
        } catch (Exception ignored) {
            return null;
        }
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

    private Vector3d resolveOrigin(Object value, @Nullable PlaneData plane) {
        Vector3d resolved = SpatialValueResolver.resolveVector3d(value);
        if (resolved != null) {
            return resolved;
        }
        if (plane != null) {
            return new Vector3d(plane.getPoint());
        }
        return new Vector3d(0.0d, 0.0d, 0.0d);
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value) || value < 0.0d) {
            return 0.0d;
        }
        return Math.min(value, 1.0d);
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = Math.max(1, targetCount);
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("targetCount", targetCount);
        state.put("seed", seed);
        state.put("threshold", threshold);
        state.put("invert", invert);
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
    }

    private record ImageData(int width, int height, List<Double> values) {}
}
