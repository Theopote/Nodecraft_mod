
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "pattern.surface_volume_distribution.image_scatter",
    displayName = "Image-Based Scatter",
    description = "Scatters points using image grayscale density maps on a plane or world XZ.",
    category = "pattern.surface_volume_distribution",
    order = 9
)
public class ImageBasedScatterNode extends BaseNode {

    @NodeProperty(displayName = "Count", category = "Scatter", order = 1)
    private int count = 256;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = 12345;

    @NodeProperty(displayName = "Threshold", category = "Scatter", order = 3)
    private double threshold = 0.0d;

    @NodeProperty(displayName = "Invert", category = "Scatter", order = 4)
    private boolean invert = false;

    private static final String INPUT_GRAYSCALE_VALUES_ID = "input_grayscale_values";
    private static final String INPUT_IMAGE_WIDTH_ID = "input_image_width";
    private static final String INPUT_IMAGE_HEIGHT_ID = "input_image_height";
    private static final String INPUT_IMAGE_PATH_ID = "input_image_path";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_SPAN_U_ID = "input_span_u";
    private static final String INPUT_SPAN_V_ID = "input_span_v";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_THRESHOLD_ID = "input_threshold";
    private static final String INPUT_INVERT_ID = "input_invert";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_UV_ID = "output_uv";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ImageBasedScatterNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.image_scatter");
        addInputPort(new BasePort(INPUT_GRAYSCALE_VALUES_ID, "Grayscale Values", "Flattened row-major grayscale values in [0,1]", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_IMAGE_WIDTH_ID, "Image Width", "Image width in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_IMAGE_HEIGHT_ID, "Image Height", "Image height in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_IMAGE_PATH_ID, "Image Path", "Optional image file path fallback", NodeDataType.FILE_PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional target plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional scatter origin point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_SPAN_U_ID, "Span U", "World span along U axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPAN_V_ID, "Span V", "World span along V axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Target number of scattered points", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold", "Density threshold in [0,1]", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INVERT_ID, "Invert", "Invert grayscale density", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Scattered points snapped to block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_UV_ID, "UV", "List of sampled UV maps {u,v,density}", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of scattered points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when image data is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points using image grayscale density maps on a plane or world XZ.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ImageData image = resolveImageData();
        if (image == null || image.width < 1 || image.height < 1 || image.values.isEmpty()) {
            writeInvalid();
            return;
        }

        int targetCount = Math.max(1, inputValues.get(INPUT_COUNT_ID) instanceof Number n ? n.intValue() : count);
        int resolvedSeed = inputValues.get(INPUT_SEED_ID) instanceof Number n ? n.intValue() : seed;
        double resolvedThreshold = clamp01(inputValues.get(INPUT_THRESHOLD_ID) instanceof Number n ? n.doubleValue() : threshold);
        boolean resolvedInvert = inputValues.get(INPUT_INVERT_ID) instanceof Boolean b ? b : invert;
        double spanU = Math.max(1.0d, inputValues.get(INPUT_SPAN_U_ID) instanceof Number n ? n.doubleValue() : image.width);
        double spanV = Math.max(1.0d, inputValues.get(INPUT_SPAN_V_ID) instanceof Number n ? n.doubleValue() : image.height);

        double[] cumulative = buildCumulativeDensity(image.values, resolvedThreshold, resolvedInvert);
        if (cumulative.length == 0 || cumulative[cumulative.length - 1] <= 1.0e-9d) {
            writeInvalid();
            return;
        }

        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData pd ? pd : null;
        Vector3d origin = resolveOrigin(inputValues.get(INPUT_ORIGIN_ID), plane);
        PlaneProjectionUtils.PlaneAxes axes = plane != null ? PlaneProjectionUtils.PlaneAxes.from(plane) : null;

        Random rng = new Random(resolvedSeed);
        List<Vector3d> points = new ArrayList<>(targetCount);
        BlockPosList blocks = new BlockPosList();
        List<java.util.Map<String, Object>> uv = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
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
            blocks.add(BlockPos.ofFloored(world.x, world.y, world.z));

            double d = density(image.values.get(pixel), resolvedThreshold, resolvedInvert);
            uv.add(java.util.Map.of("u", u01, "v", v01, "density", d));
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_UV_ID, List.copyOf(uv));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_UV_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private ImageData resolveImageData() {
        Object grayscaleObj = inputValues.get(INPUT_GRAYSCALE_VALUES_ID);
        Object widthObj = inputValues.get(INPUT_IMAGE_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_IMAGE_HEIGHT_ID);
        if (grayscaleObj instanceof List<?> list && widthObj instanceof Number wn && heightObj instanceof Number hn) {
            int width = Math.max(1, wn.intValue());
            int height = Math.max(1, hn.intValue());
            List<Double> values = new ArrayList<>(width * height);
            for (Object item : list) {
                if (item instanceof Number n) {
                    values.add(clamp01(n.doubleValue()));
                } else if (item instanceof ColorData c) {
                    values.add(clamp01(c.r() * 0.299d + c.g() * 0.587d + c.b() * 0.114d));
                }
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
            acc += density(values.get(i), thresholdValue, invertDensity);
            cumulative[i] = acc;
        }
        return cumulative;
    }

    private double density(double gray, double thresholdValue, boolean invertDensity) {
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
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof PointData p) {
            return new Vector3d(p.getPosition());
        }
        if (value instanceof BlockPos b) {
            return new Vector3d(b.getX(), b.getY(), b.getZ());
        }
        if (plane != null) {
            return new Vector3d(plane.getPoint());
        }
        return new Vector3d(0.0d, 0.0d, 0.0d);
    }

    private double clamp01(double value) {
        if (value < 0.0d) return 0.0d;
        if (value > 1.0d) return 1.0d;
        return value;
    }

    private record ImageData(int width, int height, List<Double> values) {}
}

