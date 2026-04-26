package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads a raster image from disk and exposes basic image data for downstream nodes.
 */
@NodeInfo(
    id = "utilities.fileio.read_image",
    displayName = "Read Image",
    description = "Reads a local image file and outputs dimensions, colors, and grayscale samples",
    category = "utilities.fileio",
    order = 0
)
public class ReadImageNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_WIDTH_ID = "output_width";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_ASPECT_RATIO_ID = "output_aspect_ratio";
    private static final String OUTPUT_PIXEL_COLORS_ID = "output_pixel_colors";
    private static final String OUTPUT_GRAYSCALE_VALUES_ID = "output_grayscale_values";
    private static final String OUTPUT_AVERAGE_COLOR_ID = "output_average_color";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ReadImageNode() {
        super(UUID.randomUUID(), "utilities.fileio.read_image");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to a local raster image file", NodeDataType.FILE_PATH, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved image path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_WIDTH_ID, "Width", "Image width in pixels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Image height in pixels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ASPECT_RATIO_ID, "Aspect Ratio", "Width divided by height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PIXEL_COLORS_ID, "Pixel Colors", "Flattened row-major list of pixel colors", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRAYSCALE_VALUES_ID, "Grayscale Values", "Flattened row-major grayscale samples in [0, 1]", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_COLOR_ID, "Average Color", "Average image color", NodeDataType.COLOR, this));
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
            publishEmptyOutputs();
            return;
        }

        try {
            Path resolvedPath = Path.of(rawPath).toAbsolutePath().normalize();
            File imageFile = resolvedPath.toFile();
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                publishEmptyOutputs();
                outputValues.put(OUTPUT_PATH_ID, resolvedPath.toString());
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            double aspectRatio = height > 0 ? (double) width / (double) height : 0.0d;

            List<ColorData> colors = new ArrayList<>(width * height);
            List<Double> grayscaleValues = new ArrayList<>(width * height);

            double sumR = 0.0d;
            double sumG = 0.0d;
            double sumB = 0.0d;
            double sumA = 0.0d;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    ColorData color = ColorData.fromIntARGB(argb);
                    colors.add(color);

                    double grayscale = color.r() * 0.299d + color.g() * 0.587d + color.b() * 0.114d;
                    grayscaleValues.add(grayscale);

                    sumR += color.r();
                    sumG += color.g();
                    sumB += color.b();
                    sumA += color.a();
                }
            }

            int pixelCount = Math.max(1, width * height);
            ColorData averageColor = new ColorData(
                (float) (sumR / pixelCount),
                (float) (sumG / pixelCount),
                (float) (sumB / pixelCount),
                (float) (sumA / pixelCount)
            );

            outputValues.put(OUTPUT_PATH_ID, resolvedPath.toString());
            outputValues.put(OUTPUT_WIDTH_ID, width);
            outputValues.put(OUTPUT_HEIGHT_ID, height);
            outputValues.put(OUTPUT_ASPECT_RATIO_ID, aspectRatio);
            outputValues.put(OUTPUT_PIXEL_COLORS_ID, List.copyOf(colors));
            outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.copyOf(grayscaleValues));
            outputValues.put(OUTPUT_AVERAGE_COLOR_ID, averageColor);
            outputValues.put(OUTPUT_VALID_ID, true);
        } catch (Exception e) {
            publishEmptyOutputs();
            outputValues.put(OUTPUT_PATH_ID, rawPath);
        }
    }

    private @Nullable String getInputPath() {
        Object value = inputValues.get(INPUT_PATH_ID);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private void publishEmptyOutputs() {
        outputValues.put(OUTPUT_PATH_ID, "");
        outputValues.put(OUTPUT_WIDTH_ID, 0);
        outputValues.put(OUTPUT_HEIGHT_ID, 0);
        outputValues.put(OUTPUT_ASPECT_RATIO_ID, 0.0d);
        outputValues.put(OUTPUT_PIXEL_COLORS_ID, List.of());
        outputValues.put(OUTPUT_GRAYSCALE_VALUES_ID, List.of());
        outputValues.put(OUTPUT_AVERAGE_COLOR_ID, ColorData.BLACK);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
