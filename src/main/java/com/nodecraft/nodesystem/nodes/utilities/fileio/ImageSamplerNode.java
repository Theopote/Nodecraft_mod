package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Samples color and channel values from flattened image pixel data.
 */
@NodeInfo(
    id = "utilities.fileio.image_sampler",
    displayName = "Image Sampler",
    description = "Samples color, channels, and grayscale values from image pixels using UV or pixel coordinates",
    category = "utilities.fileio",
    order = 1
)
public class ImageSamplerNode extends BaseNode {

    public enum CoordinateMode {
        UV,
        PIXEL
    }

    public enum WrapMode {
        CLAMP,
        REPEAT,
        MIRROR
    }

    public enum FilterMode {
        NEAREST,
        BILINEAR
    }

    public enum OriginMode {
        TOP_LEFT,
        BOTTOM_LEFT
    }

    @NodeProperty(displayName = "Coordinate Mode", category = "Sampling", order = 1)
    private CoordinateMode coordinateMode = CoordinateMode.UV;

    @NodeProperty(displayName = "Wrap Mode", category = "Sampling", order = 2)
    private WrapMode wrapMode = WrapMode.CLAMP;

    @NodeProperty(displayName = "Filter Mode", category = "Sampling", order = 3)
    private FilterMode filterMode = FilterMode.BILINEAR;

    @NodeProperty(displayName = "Origin", category = "Sampling", order = 4)
    private OriginMode originMode = OriginMode.TOP_LEFT;

    private static final String INPUT_PIXEL_COLORS_ID = "input_pixel_colors";
    private static final String INPUT_IMAGE_WIDTH_ID = "input_image_width";
    private static final String INPUT_IMAGE_HEIGHT_ID = "input_image_height";
    private static final String INPUT_U_ID = "input_u";
    private static final String INPUT_V_ID = "input_v";
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";

    private static final String OUTPUT_COLOR_ID = "output_color";
    private static final String OUTPUT_RED_ID = "output_red";
    private static final String OUTPUT_GREEN_ID = "output_green";
    private static final String OUTPUT_BLUE_ID = "output_blue";
    private static final String OUTPUT_ALPHA_ID = "output_alpha";
    private static final String OUTPUT_GRAYSCALE_ID = "output_grayscale";
    private static final String OUTPUT_SAMPLE_X_ID = "output_sample_x";
    private static final String OUTPUT_SAMPLE_Y_ID = "output_sample_y";
    private static final String OUTPUT_RESOLVED_MODE_ID = "output_resolved_mode";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ImageSamplerNode() {
        super(UUID.randomUUID(), "utilities.fileio.image_sampler");

        addInputPort(new BasePort(INPUT_PIXEL_COLORS_ID, "Pixel Colors", "Flattened row-major list of image colors", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_IMAGE_WIDTH_ID, "Width", "Image width in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_IMAGE_HEIGHT_ID, "Height", "Image height in pixels", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_U_ID, "U", "Normalized horizontal coordinate in 0..1", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_V_ID, "V", "Normalized vertical coordinate in 0..1", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_ID, "X", "Pixel-space horizontal coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Pixel-space vertical coordinate", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_COLOR_ID, "Color", "Sampled color", NodeDataType.COLOR, this));
        addOutputPort(new BasePort(OUTPUT_RED_ID, "R", "Red channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_GREEN_ID, "G", "Green channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BLUE_ID, "B", "Blue channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ALPHA_ID, "A", "Alpha channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_GRAYSCALE_ID, "Grayscale", "Luma grayscale value in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLE_X_ID, "Sample X", "Resolved pixel-space X coordinate", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLE_Y_ID, "Sample Y", "Resolved pixel-space Y coordinate", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_MODE_ID, "Resolved Mode", "Coordinate mode used for this sample", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why sampling failed", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the image sample succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Image Sampler";
    }

    @Override
    public String getDescription() {
        return "Samples color, channels, and grayscale values from image pixels using UV or pixel coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ImageView image = resolveImage();
        if (image == null) {
            writeInvalid("Invalid image inputs");
            return;
        }

        double x;
        double y;
        CoordinateMode resolvedMode = coordinateMode == null ? CoordinateMode.UV : coordinateMode;
        if (resolvedMode == CoordinateMode.PIXEL) {
            x = getInputDouble(INPUT_X_ID, 0.0d);
            y = getInputDouble(INPUT_Y_ID, 0.0d);
        } else {
            double u = getInputDouble(INPUT_U_ID, 0.0d);
            double v = getInputDouble(INPUT_V_ID, 0.0d);
            x = u * Math.max(0, image.width - 1);
            y = v * Math.max(0, image.height - 1);
        }

        if (originMode == OriginMode.BOTTOM_LEFT) {
            y = Math.max(0, image.height - 1) - y;
        }

        ColorData color = filterMode == FilterMode.NEAREST
            ? sampleNearest(image, x, y)
            : sampleBilinear(image, x, y);

        if (color == null) {
            writeInvalid("Sample coordinate resolved outside image data");
            return;
        }

        outputValues.put(OUTPUT_COLOR_ID, color);
        outputValues.put(OUTPUT_RED_ID, (double) color.r());
        outputValues.put(OUTPUT_GREEN_ID, (double) color.g());
        outputValues.put(OUTPUT_BLUE_ID, (double) color.b());
        outputValues.put(OUTPUT_ALPHA_ID, (double) color.a());
        outputValues.put(OUTPUT_GRAYSCALE_ID, grayscale(color));
        outputValues.put(OUTPUT_SAMPLE_X_ID, resolveCoordinate(x, image.width));
        outputValues.put(OUTPUT_SAMPLE_Y_ID, resolveCoordinate(y, image.height));
        outputValues.put(OUTPUT_RESOLVED_MODE_ID, resolvedMode.name());
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private ImageView resolveImage() {
        Object colorsObj = inputValues.get(INPUT_PIXEL_COLORS_ID);
        Object widthObj = inputValues.get(INPUT_IMAGE_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_IMAGE_HEIGHT_ID);
        if (!(colorsObj instanceof List<?> colors) || !(widthObj instanceof Number wn) || !(heightObj instanceof Number hn)) {
            return null;
        }

        int width = Math.max(0, wn.intValue());
        int height = Math.max(0, hn.intValue());
        if (width < 1 || height < 1 || colors.size() < width * height) {
            return null;
        }

        return new ImageView(width, height, colors);
    }

    private ColorData sampleNearest(ImageView image, double x, double y) {
        int px = (int) Math.round(resolveCoordinate(x, image.width));
        int py = (int) Math.round(resolveCoordinate(y, image.height));
        return readColor(image, px, py);
    }

    private ColorData sampleBilinear(ImageView image, double x, double y) {
        double rx = resolveCoordinate(x, image.width);
        double ry = resolveCoordinate(y, image.height);
        int x0 = (int) Math.floor(rx);
        int y0 = (int) Math.floor(ry);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double tx = rx - x0;
        double ty = ry - y0;

        ColorData c00 = readColor(image, x0, y0);
        ColorData c10 = readColor(image, x1, y0);
        ColorData c01 = readColor(image, x0, y1);
        ColorData c11 = readColor(image, x1, y1);
        if (c00 == null || c10 == null || c01 == null || c11 == null) {
            return null;
        }

        float r = (float) bilerp(c00.r(), c10.r(), c01.r(), c11.r(), tx, ty);
        float g = (float) bilerp(c00.g(), c10.g(), c01.g(), c11.g(), tx, ty);
        float b = (float) bilerp(c00.b(), c10.b(), c01.b(), c11.b(), tx, ty);
        float a = (float) bilerp(c00.a(), c10.a(), c01.a(), c11.a(), tx, ty);
        return new ColorData(r, g, b, a);
    }

    private ColorData readColor(ImageView image, int x, int y) {
        int px = (int) resolveCoordinate(x, image.width);
        int py = (int) resolveCoordinate(y, image.height);
        int index = py * image.width + px;
        if (index < 0 || index >= image.colors.size()) {
            return null;
        }
        Object value = image.colors.get(index);
        if (value instanceof ColorData color) {
            return color;
        }
        if (value instanceof Number number) {
            double gray = clamp01(number.doubleValue());
            return new ColorData((float) gray, (float) gray, (float) gray, 1.0f);
        }
        return null;
    }

    private double resolveCoordinate(double value, int size) {
        if (size <= 1) {
            return 0.0d;
        }

        double max = size - 1.0d;
        WrapMode mode = wrapMode == null ? WrapMode.CLAMP : wrapMode;
        return switch (mode) {
            case CLAMP -> Math.max(0.0d, Math.min(max, value));
            case REPEAT -> positiveModulo(value, size);
            case MIRROR -> mirror(value, size);
        };
    }

    private double positiveModulo(double value, int size) {
        double result = value % size;
        return result < 0.0d ? result + size : result;
    }

    private double mirror(double value, int size) {
        if (size <= 1) {
            return 0.0d;
        }
        double period = (size - 1.0d) * 2.0d;
        double wrapped = value % period;
        if (wrapped < 0.0d) {
            wrapped += period;
        }
        double max = size - 1.0d;
        return wrapped <= max ? wrapped : period - wrapped;
    }

    private double bilerp(double c00, double c10, double c01, double c11, double tx, double ty) {
        double top = lerp(c00, c10, tx);
        double bottom = lerp(c01, c11, tx);
        return lerp(top, bottom, ty);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double grayscale(ColorData color) {
        return color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_COLOR_ID, ColorData.BLACK);
        outputValues.put(OUTPUT_RED_ID, 0.0d);
        outputValues.put(OUTPUT_GREEN_ID, 0.0d);
        outputValues.put(OUTPUT_BLUE_ID, 0.0d);
        outputValues.put(OUTPUT_ALPHA_ID, 1.0d);
        outputValues.put(OUTPUT_GRAYSCALE_ID, 0.0d);
        outputValues.put(OUTPUT_SAMPLE_X_ID, 0.0d);
        outputValues.put(OUTPUT_SAMPLE_Y_ID, 0.0d);
        outputValues.put(OUTPUT_RESOLVED_MODE_ID, coordinateMode == null ? CoordinateMode.UV.name() : coordinateMode.name());
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private record ImageView(int width, int height, List<?> colors) {}
}
