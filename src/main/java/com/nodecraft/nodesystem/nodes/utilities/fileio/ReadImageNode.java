package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.ImportAccessPolicy;
import com.nodecraft.nodesystem.util.ImportPathUtil;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.FILE_IO,
    id = "utilities.fileio.read_image",
    displayName = "Read Image",
    description = "Reads a local image file into an IMAGE payload with metadata-first safety caps",
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
    private static final String INPUT_DOWNSAMPLE_STEP_ID = "input_downsample_step";
    private static final String INPUT_READ_MODE_ID = "input_read_mode";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_IMAGE_ID = "output_image";
    private static final String OUTPUT_WIDTH_ID = "output_width";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_ASPECT_RATIO_ID = "output_aspect_ratio";
    private static final String OUTPUT_GRAYSCALE_VALUES_ID = "output_grayscale_values";
    private static final String OUTPUT_AVERAGE_COLOR_ID = "output_average_color";
    private static final String OUTPUT_PIXEL_COUNT_ID = "output_pixel_count";
    private static final String OUTPUT_SAMPLE_STEP_ID = "output_sample_step";
    private static final String OUTPUT_WAS_DOWNSAMPLED_ID = "output_was_downsampled";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Read Mode", category = "Image", order = 1)
    private ReadMode readMode = ReadMode.DOWNSAMPLED;

    @NodeProperty(displayName = "Downsample Step", category = "Image", order = 2)
    private int downsampleStep = 1;

    public ReadImageNode() {
        super(UUID.randomUUID(), "utilities.fileio.read_image");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to a local raster image file", NodeDataType.FILE_PATH, this));
        addInputPort(new BasePort(INPUT_DOWNSAMPLE_STEP_ID, "Downsample Step", "Requested pixel stride (≥1); may increase for budget", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_READ_MODE_ID, "Read Mode", "FULL, DOWNSAMPLED, or METADATA_ONLY", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved image path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_IMAGE_ID, "Image", "Sampled IMAGE payload (null on metadata-only)", NodeDataType.IMAGE, this));
        addOutputPort(new BasePort(OUTPUT_WIDTH_ID, "Width", "Sample width when IMAGE present; source width on metadata-only", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Sample height when IMAGE present; source height on metadata-only", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ASPECT_RATIO_ID, "Aspect Ratio", "Width divided by height for the reported dims", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_GRAYSCALE_VALUES_ID, "Grayscale Values", "Flattened row-major grayscale samples in [0, 1]", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_COLOR_ID, "Average Color", "Average sample color", NodeDataType.COLOR, this));
        addOutputPort(new BasePort(OUTPUT_PIXEL_COUNT_ID, "Pixel Count", "Number of sample colors", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLE_STEP_ID, "Sample Step", "Effective sample stride used", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_WAS_DOWNSAMPLED_ID, "Was Downsampled", "True when effective sample step > 1", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why image read failed or was limited", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the image file was successfully read", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Reads a local image file into an IMAGE payload with metadata-first safety caps";
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

        if (!ImportAccessPolicy.current().allows(resolvedPath, ImportPathUtil.ImportKind.IMAGE)) {
            publishEmptyOutputs(resolvedPath.toString(), "Image path is outside the import allowlist");
            return;
        }

        if (!Files.isRegularFile(resolvedPath)) {
            publishEmptyOutputs(resolvedPath.toString(), "Image file does not exist");
            return;
        }

        ReadMode mode = resolveReadMode();
        if (mode == null) {
            publishEmptyOutputs(resolvedPath.toString(), "Invalid Read Mode");
            return;
        }

        Integer requestedStep = OptionalPortDrive.resolveOptionalInteger(this, INPUT_DOWNSAMPLE_STEP_ID, downsampleStep);
        if (requestedStep == null || requestedStep < 1) {
            publishEmptyOutputs(resolvedPath.toString(), "Downsample Step must be an exact Integer ≥ 1");
            return;
        }

        try {
            long fileBytes = Files.size(resolvedPath);
            if (fileBytes > GenerationLimits.MAX_IMAGE_FILE_BYTES) {
                publishEmptyOutputs(
                    resolvedPath.toString(),
                    "Image file exceeds max bytes " + GenerationLimits.MAX_IMAGE_FILE_BYTES
                );
                return;
            }

            ImageMeta meta = readMetadata(resolvedPath);
            if (meta == null) {
                publishEmptyOutputs(resolvedPath.toString(), "Unsupported or unreadable image format");
                return;
            }

            long sourcePixels;
            try {
                sourcePixels = Math.multiplyExact((long) meta.width(), (long) meta.height());
            } catch (ArithmeticException ex) {
                publishEmptyOutputs(resolvedPath.toString(), "Image dimensions overflow pixel budget");
                return;
            }

            if (mode == ReadMode.METADATA_ONLY) {
                publishMetadataOnly(resolvedPath.toString(), meta.width(), meta.height(), "");
                return;
            }

            if (mode == ReadMode.FULL) {
                if (sourcePixels > GenerationLimits.MAX_IMAGE_PIXELS) {
                    publishEmptyOutputs(
                        resolvedPath.toString(),
                        "Image has " + sourcePixels + " pixels; exceeds max pixels "
                            + GenerationLimits.MAX_IMAGE_PIXELS
                    );
                    return;
                }
                BufferedImage image = ImageIO.read(resolvedPath.toFile());
                if (image == null) {
                    publishEmptyOutputs(resolvedPath.toString(), "Unsupported or unreadable image format");
                    return;
                }
                publishSamples(resolvedPath.toString(), meta.width(), meta.height(), 1, image);
                return;
            }

            // DOWNSAMPLED
            int step = Math.max(requestedStep, computeRequiredStep(meta.width(), meta.height(), GenerationLimits.MAX_IMAGE_PIXELS));
            BufferedImage sampled = readWithSubsampling(resolvedPath, step);
            if (sampled != null) {
                publishSamples(resolvedPath.toString(), meta.width(), meta.height(), step, sampled);
                return;
            }

            // Fallback: full decode only when source already within budget, then stride sample.
            if (sourcePixels > GenerationLimits.MAX_IMAGE_PIXELS) {
                publishEmptyOutputs(
                    resolvedPath.toString(),
                    "Cannot downsample safely; source pixels " + sourcePixels
                        + " exceed max " + GenerationLimits.MAX_IMAGE_PIXELS
                        + " and subsampling is unavailable"
                );
                return;
            }
            BufferedImage image = ImageIO.read(resolvedPath.toFile());
            if (image == null) {
                publishEmptyOutputs(resolvedPath.toString(), "Unsupported or unreadable image format");
                return;
            }
            SampleResult strideSamples = readSamplesWithStride(image, step);
            ImageData imageData = ImageData.create(
                meta.width(),
                meta.height(),
                strideSamples.sampleWidth(),
                strideSamples.sampleHeight(),
                step,
                strideSamples.colors()
            );
            publishImageResult(resolvedPath.toString(), imageData, strideSamples);
        } catch (Exception e) {
            publishEmptyOutputs(
                resolvedPath.toString(),
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            );
        }
    }

    private @Nullable String getInputPath() {
        Object value = inputValues.get(INPUT_PATH_ID);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private @Nullable ReadMode resolveReadMode() {
        if (OptionalPortDrive.isConnected(this, INPUT_READ_MODE_ID)) {
            Object value = inputValues.get(INPUT_READ_MODE_ID);
            if (!(value instanceof String text) || text.isBlank()) {
                return null;
            }
            try {
                return ReadMode.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return readMode == null ? ReadMode.DOWNSAMPLED : readMode;
    }

    private int computeRequiredStep(int width, int height, int maxPixelValue) {
        int step = 1;
        while (samplePixelCount(width, height, step) > maxPixelValue) {
            step++;
            if (step > Math.max(width, height)) {
                return step;
            }
        }
        return step;
    }

    private static long samplePixelCount(int width, int height, int step) {
        long sampleW = (width + step - 1L) / step;
        long sampleH = (height + step - 1L) / step;
        try {
            return Math.multiplyExact(sampleW, sampleH);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private @Nullable ImageMeta readMetadata(Path path) throws Exception {
        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                return new ImageMeta(width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private @Nullable BufferedImage readWithSubsampling(Path path, int step) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void publishSamples(String path, int sourceWidth, int sourceHeight, int step, BufferedImage image) {
        int sampleWidth = image.getWidth();
        int sampleHeight = image.getHeight();
        List<ColorData> colors = new ArrayList<>(sampleWidth * sampleHeight);
        List<Double> grayscale = new ArrayList<>(sampleWidth * sampleHeight);
        double sumR = 0.0d;
        double sumG = 0.0d;
        double sumB = 0.0d;
        double sumA = 0.0d;

        for (int y = 0; y < sampleHeight; y++) {
            for (int x = 0; x < sampleWidth; x++) {
                ColorData color = ColorData.fromIntARGB(image.getRGB(x, y));
                colors.add(color);
                grayscale.add(color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d);
                sumR += color.r();
                sumG += color.g();
                sumB += color.b();
                sumA += color.a();
            }
        }

        ImageData imageData = ImageData.create(sourceWidth, sourceHeight, sampleWidth, sampleHeight, step, colors);
        int pixelCount = Math.max(1, colors.size());
        ColorData average = new ColorData(
            (float) (sumR / pixelCount),
            (float) (sumG / pixelCount),
            (float) (sumB / pixelCount),
            (float) (sumA / pixelCount)
        );
        publishImageResult(path, imageData, new SampleResult(sampleWidth, sampleHeight, colors, grayscale, average));
    }

    private SampleResult readSamplesWithStride(BufferedImage image, int step) {
        int width = image.getWidth();
        int height = image.getHeight();
        int sampleWidth = (width + step - 1) / step;
        int sampleHeight = (height + step - 1) / step;
        List<ColorData> colors = new ArrayList<>(sampleWidth * sampleHeight);
        List<Double> grayscale = new ArrayList<>(sampleWidth * sampleHeight);
        double sumR = 0.0d;
        double sumG = 0.0d;
        double sumB = 0.0d;
        double sumA = 0.0d;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                ColorData color = ColorData.fromIntARGB(image.getRGB(x, y));
                colors.add(color);
                grayscale.add(color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d);
                sumR += color.r();
                sumG += color.g();
                sumB += color.b();
                sumA += color.a();
            }
        }

        int pixelCount = Math.max(1, colors.size());
        ColorData average = new ColorData(
            (float) (sumR / pixelCount),
            (float) (sumG / pixelCount),
            (float) (sumB / pixelCount),
            (float) (sumA / pixelCount)
        );
        return new SampleResult(sampleWidth, sampleHeight, colors, grayscale, average);
    }

    private void publishImageResult(String path, ImageData imageData, SampleResult samples) {
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_IMAGE_ID, imageData);
        outputValues.put(OUTPUT_WIDTH_ID, imageData.sampleWidth());
        outputValues.put(OUTPUT_HEIGHT_ID, imageData.sampleHeight());
        outputValues.put(
            OUTPUT_ASPECT_RATIO_ID,
            imageData.sampleHeight() > 0
                ? (double) imageData.sampleWidth() / (double) imageData.sampleHeight()
                : 0.0d
        );
        outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.copyOf(samples.grayscale()));
        outputValues.put(OUTPUT_AVERAGE_COLOR_ID, samples.averageColor());
        outputValues.put(OUTPUT_PIXEL_COUNT_ID, imageData.colors().size());
        outputValues.put(OUTPUT_SAMPLE_STEP_ID, imageData.sampleStep());
        outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, imageData.sampleStep() > 1);
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void publishMetadataOnly(String path, int width, int height, String error) {
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_IMAGE_ID, null);
        outputValues.put(OUTPUT_WIDTH_ID, width);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_ASPECT_RATIO_ID, height > 0 ? (double) width / (double) height : 0.0d);
        outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.of());
        outputValues.put(OUTPUT_AVERAGE_COLOR_ID, ColorData.BLACK);
        outputValues.put(OUTPUT_PIXEL_COUNT_ID, 0);
        outputValues.put(OUTPUT_SAMPLE_STEP_ID, 1);
        outputValues.put(OUTPUT_WAS_DOWNSAMPLED_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, error == null || error.isBlank());
    }

    private void publishEmptyOutputs(String path, String error) {
        outputValues.put(OUTPUT_PATH_ID, path == null ? "" : path);
        outputValues.put(OUTPUT_IMAGE_ID, null);
        outputValues.put(OUTPUT_WIDTH_ID, 0);
        outputValues.put(OUTPUT_HEIGHT_ID, 0);
        outputValues.put(OUTPUT_ASPECT_RATIO_ID, 0.0d);
        outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.of());
        outputValues.put(OUTPUT_AVERAGE_COLOR_ID, ColorData.BLACK);
        outputValues.put(OUTPUT_PIXEL_COUNT_ID, 0);
        outputValues.put(OUTPUT_SAMPLE_STEP_ID, 1);
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

    public int getDownsampleStep() {
        return downsampleStep;
    }

    public void setDownsampleStep(int downsampleStep) {
        this.downsampleStep = Math.max(1, downsampleStep);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("readMode", getReadMode().name());
        state.put("downsampleStep", downsampleStep);
        return state;
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
        if (map.get("downsampleStep") instanceof Number value) {
            setDownsampleStep(value.intValue());
        }
    }

    private record ImageMeta(int width, int height) {
    }

    private record SampleResult(
        int sampleWidth,
        int sampleHeight,
        List<ColorData> colors,
        List<Double> grayscale,
        ColorData averageColor
    ) {
    }
}
