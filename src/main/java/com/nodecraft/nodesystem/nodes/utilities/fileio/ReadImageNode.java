package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ImportPathUtil;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "utilities.fileio.read_image",
    displayName = "Read Image",
    description = "Reads a local image file and outputs dimensions, colors, and grayscale samples",
    category = "utilities.fileio",
    order = 0
)
public class ReadImageNode extends BaseNode {

    public enum ReadMode {
        FULL,
        DOWNSAMPLED,
        METADATA_ONLY
    }

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_MAX_PIXELS_ID = "input_max_pixels";
    private static final String INPUT_DOWNSAMPLE_STEP_ID = "input_downsample_step";
    private static final String INPUT_READ_MODE_ID = "input_read_mode";
    private static final String INPUT_ALLOW_EXTERNAL_PATHS_ID = "input_allow_external_paths";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_WIDTH_ID = "output_width";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_ASPECT_RATIO_ID = "output_aspect_ratio";
    private static final String OUTPUT_PIXEL_COLORS_ID = "output_pixel_colors";
    private static final String OUTPUT_GRAYSCALE_VALUES_ID = "output_grayscale_values";
    private static final String OUTPUT_AVERAGE_COLOR_ID = "output_average_color";
    private static final String OUTPUT_PIXEL_COUNT_ID = "output_pixel_count";
    private static final String OUTPUT_WAS_DOWNSAMPLED_ID = "output_was_downsampled";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Read Mode", category = "Image", order = 1)
    private ReadMode readMode = ReadMode.DOWNSAMPLED;

    @NodeProperty(displayName = "Max Pixels", category = "Safety", order = 2)
    private int maxPixels = 1_048_576;

    @NodeProperty(displayName = "Downsample Step", category = "Safety", order = 3)
    private int downsampleStep = 1;

    @NodeProperty(displayName = "Allow External Paths", category = "Safety", order = 4)
    private boolean allowExternalPaths = false;

    public ReadImageNode() {
        super(UUID.randomUUID(), "utilities.fileio.read_image");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to a local raster image file", NodeDataType.FILE_PATH, this));
        addInputPort(new BasePort(INPUT_MAX_PIXELS_ID, "Max Pixels", "Maximum output samples to materialize", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DOWNSAMPLE_STEP_ID, "Downsample Step", "Pixel stride for downsampled reads", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_READ_MODE_ID, "Read Mode", "FULL, DOWNSAMPLED, or METADATA_ONLY", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_ALLOW_EXTERNAL_PATHS_ID, "Allow External Paths", "Permit paths outside common NodeCraft image locations", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved image path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_WIDTH_ID, "Width", "Image width in pixels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Image height in pixels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ASPECT_RATIO_ID, "Aspect Ratio", "Width divided by height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PIXEL_COLORS_ID, "Pixel Colors", "Flattened row-major list of pixel colors", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRAYSCALE_VALUES_ID, "Grayscale Values", "Flattened row-major grayscale samples in [0, 1]", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_COLOR_ID, "Average Color", "Average image color", NodeDataType.COLOR, this));
        addOutputPort(new BasePort(OUTPUT_PIXEL_COUNT_ID, "Pixel Count", "Number of output samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_WAS_DOWNSAMPLED_ID, "Was Downsampled", "True when output samples are downsampled", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why image read failed or was limited", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the image file was successfully read", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Reads a local image file and outputs dimensions, colors, and grayscale samples";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String rawPath = getInputPath();
        if (rawPath == null) {
            publishEmptyOutputs("", "Missing image path");
            return;
        }

        Path resolvedPath;
        try {
            resolvedPath = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            publishEmptyOutputs(rawPath, "Invalid path: " + e.getMessage());
            return;
        }

        boolean allowExternal = inputValues.get(INPUT_ALLOW_EXTERNAL_PATHS_ID) instanceof Boolean value ? value : allowExternalPaths;
        if (!allowExternal && !ImportPathUtil.isAllowedDefaultPath(resolvedPath, ImportPathUtil.ImportKind.IMAGE)) {
            publishEmptyOutputs(resolvedPath.toString(), "External image paths are disabled");
            return;
        }

        if (!Files.isRegularFile(resolvedPath)) {
            publishEmptyOutputs(resolvedPath.toString(), "Image file does not exist");
            return;
        }

        try {
            File imageFile = resolvedPath.toFile();
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                publishEmptyOutputs(resolvedPath.toString(), "Unsupported or unreadable image format");
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            long sourcePixels = (long) width * (long) height;
            int maxPixelValue = resolvePositiveInt(INPUT_MAX_PIXELS_ID, maxPixels);
            int step = resolvePositiveInt(INPUT_DOWNSAMPLE_STEP_ID, downsampleStep);
            ReadMode mode = resolveReadMode(inputValues.get(INPUT_READ_MODE_ID));
            if (mode == ReadMode.FULL && sourcePixels > maxPixelValue) {
                publishMetadataOnly(resolvedPath.toString(), width, height, "Image has " + sourcePixels + " pixels; exceeds max pixels " + maxPixelValue);
                return;
            }
            if (mode == ReadMode.METADATA_ONLY) {
                publishMetadataOnly(resolvedPath.toString(), width, height, "");
                return;
            }

            if (mode == ReadMode.DOWNSAMPLED) {
                step = Math.max(step, computeRequiredStep(width, height, maxPixelValue));
            }

            SampleResult samples = readSamples(image, step);
            outputValues.put(OUTPUT_PATH_ID, resolvedPath.toString());
            outputValues.put(OUTPUT_WIDTH_ID, width);
            outputValues.put(OUTPUT_HEIGHT_ID, height);
            outputValues.put(OUTPUT_ASPECT_RATIO_ID, height > 0 ? (double) width / (double) height : 0.0d);
            outputValues.put(OUTPUT_PIXEL_COLORS_ID, List.copyOf(samples.colors()));
            outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.copyOf(samples.grayscale()));
            outputValues.put(OUTPUT_AVERAGE_COLOR_ID, samples.averageColor());
            outputValues.put(OUTPUT_PIXEL_COUNT_ID, samples.colors().size());
            outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, step > 1);
            outputValues.put(OUTPUT_ERROR_ID, "");
            outputValues.put(OUTPUT_VALID_ID, true);
        } catch (Exception e) {
            publishEmptyOutputs(resolvedPath.toString(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private @Nullable String getInputPath() {
        Object value = inputValues.get(INPUT_PATH_ID);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private int resolvePositiveInt(String inputId, int fallback) {
        int resolved = fallback;
        Object value = inputValues.get(inputId);
        if (value instanceof Number number) {
            resolved = number.intValue();
        }
        return Math.max(1, resolved);
    }

    private ReadMode resolveReadMode(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            try {
                return ReadMode.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return readMode;
            }
        }
        return readMode == null ? ReadMode.DOWNSAMPLED : readMode;
    }

    private int computeRequiredStep(int width, int height, int maxPixelValue) {
        int step = 1;
        while (((long) Math.ceil((double) width / step) * (long) Math.ceil((double) height / step)) > maxPixelValue) {
            step++;
        }
        return step;
    }

    private SampleResult readSamples(BufferedImage image, int step) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<ColorData> colors = new ArrayList<>();
        List<Double> grayscaleValues = new ArrayList<>();

        double sumR = 0.0d;
        double sumG = 0.0d;
        double sumB = 0.0d;
        double sumA = 0.0d;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                ColorData color = ColorData.fromIntARGB(image.getRGB(x, y));
                colors.add(color);
                double grayscale = color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d;
                grayscaleValues.add(grayscale);
                sumR += color.r();
                sumG += color.g();
                sumB += color.b();
                sumA += color.a();
            }
        }

        int pixelCount = Math.max(1, colors.size());
        ColorData averageColor = new ColorData(
            (float) (sumR / pixelCount),
            (float) (sumG / pixelCount),
            (float) (sumB / pixelCount),
            (float) (sumA / pixelCount)
        );
        return new SampleResult(colors, grayscaleValues, averageColor);
    }

    private void publishMetadataOnly(String path, int width, int height, String error) {
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_WIDTH_ID, width);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_ASPECT_RATIO_ID, height > 0 ? (double) width / (double) height : 0.0d);
        outputValues.put(OUTPUT_PIXEL_COLORS_ID, List.of());
        outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.of());
        outputValues.put(OUTPUT_AVERAGE_COLOR_ID, ColorData.BLACK);
        outputValues.put(OUTPUT_PIXEL_COUNT_ID, 0);
        outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, error == null || error.isBlank());
    }

    private void publishEmptyOutputs(String path, String error) {
        outputValues.put(OUTPUT_PATH_ID, path == null ? "" : path);
        outputValues.put(OUTPUT_WIDTH_ID, 0);
        outputValues.put(OUTPUT_HEIGHT_ID, 0);
        outputValues.put(OUTPUT_ASPECT_RATIO_ID, 0.0d);
        outputValues.put(OUTPUT_PIXEL_COLORS_ID, List.of());
        outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.of());
        outputValues.put(OUTPUT_AVERAGE_COLOR_ID, ColorData.BLACK);
        outputValues.put(OUTPUT_PIXEL_COUNT_ID, 0);
        outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public ReadMode getReadMode() {
        return readMode;
    }

    public void setReadMode(ReadMode readMode) {
        this.readMode = readMode == null ? ReadMode.DOWNSAMPLED : readMode;
        markDirty();
    }

    public int getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(int maxPixels) {
        this.maxPixels = Math.max(1, maxPixels);
        markDirty();
    }

    public int getDownsampleStep() {
        return downsampleStep;
    }

    public void setDownsampleStep(int downsampleStep) {
        this.downsampleStep = Math.max(1, downsampleStep);
        markDirty();
    }

    public boolean isAllowExternalPaths() {
        return allowExternalPaths;
    }

    public void setAllowExternalPaths(boolean allowExternalPaths) {
        this.allowExternalPaths = allowExternalPaths;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "readMode", getReadMode().name(),
            "maxPixels", maxPixels,
            "downsampleStep", downsampleStep,
            "allowExternalPaths", allowExternalPaths
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("readMode") instanceof String value) {
            try {
                setReadMode(ReadMode.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                setReadMode(ReadMode.DOWNSAMPLED);
            }
        }
        if (map.get("maxPixels") instanceof Number value) {
            setMaxPixels(value.intValue());
        }
        if (map.get("downsampleStep") instanceof Number value) {
            setDownsampleStep(value.intValue());
        }
        if (map.get("allowExternalPaths") instanceof Boolean value) {
            setAllowExternalPaths(value);
        }
    }

    private record SampleResult(List<ColorData> colors, List<Double> grayscale, ColorData averageColor) {
    }
}
