package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.gui.components.customui.CustomUIMetricsSafe;
import com.nodecraft.gui.components.customui.ImagePreviewWidget;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.ImageData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import imgui.ImGui;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Samples color and channel values from an {@link ImageData} payload.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.fileio.image_sampler",
    displayName = "Image Sampler",
    description = "Samples IMAGE using UV or pixel coordinates. UV 0..1 maps to inclusive pixel endpoints (u=1 is the last column); wrap applies after that mapping.",
    category = "utilities.fileio",
    order = 1
)
public class ImageSamplerNode extends BaseCustomUINode {

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

    private static final String INPUT_IMAGE_ID = "input_image";
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

        addInputPort(new BasePort(INPUT_IMAGE_ID, "Image", "IMAGE payload from Read Image", NodeDataType.IMAGE, this));
        addInputPort(new BasePort(INPUT_U_ID, "U",
            "Normalized horizontal coordinate; 0..1 maps to inclusive endpoints (1 is last column)",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_V_ID, "V",
            "Normalized vertical coordinate; 0..1 maps to inclusive endpoints (1 is last row)",
            NodeDataType.DOUBLE, this));
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
        return "Samples IMAGE using UV or pixel coordinates. UV 0..1 maps to inclusive pixel endpoints (u=1 is the last column); wrap applies after that mapping.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object imageObj = inputValues.get(INPUT_IMAGE_ID);
        if (!(imageObj instanceof ImageData image)) {
            writeInvalid("Missing or invalid IMAGE");
            return;
        }

        CoordinateMode resolvedMode = coordinateMode == null ? CoordinateMode.UV : coordinateMode;
        double coordX;
        double coordY;
        if (resolvedMode == CoordinateMode.PIXEL) {
            Double px = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_X_ID));
            Double py = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_Y_ID));
            if (px == null || py == null) {
                writeInvalid("Pixel coordinates must be finite doubles");
                return;
            }
            coordX = px;
            coordY = py;
        } else {
            Double u = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_U_ID));
            Double v = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_V_ID));
            if (u == null || v == null) {
                writeInvalid("UV coordinates must be finite doubles");
                return;
            }
            coordX = u;
            coordY = v;
        }

        ImageSamplerMath.SamplePoint sampleSpace = ImageSamplerMath.toSampleSpace(
            coordX,
            coordY,
            resolvedMode == CoordinateMode.UV,
            image.sampleWidth(),
            image.sampleHeight(),
            originMode == null ? OriginMode.TOP_LEFT : originMode
        );

        ColorData color = ImageSamplerMath.sample(
            image,
            sampleSpace.x(),
            sampleSpace.y(),
            filterMode,
            wrapMode
        );

        if (color == null) {
            writeInvalid("Sample coordinate resolved outside image data");
            return;
        }

        outputValues.put(OUTPUT_COLOR_ID, color);
        outputValues.put(OUTPUT_RED_ID, (double) color.r());
        outputValues.put(OUTPUT_GREEN_ID, (double) color.g());
        outputValues.put(OUTPUT_BLUE_ID, (double) color.b());
        outputValues.put(OUTPUT_ALPHA_ID, (double) color.a());
        outputValues.put(OUTPUT_GRAYSCALE_ID, ImageSamplerMath.grayscale(color));
        outputValues.put(OUTPUT_SAMPLE_X_ID, ImageSamplerMath.resolveCoordinate(
            sampleSpace.x(), image.sampleWidth(), wrapMode));
        outputValues.put(OUTPUT_SAMPLE_Y_ID, ImageSamplerMath.resolveCoordinate(
            sampleSpace.y(), image.sampleHeight(), wrapMode));
        outputValues.put(OUTPUT_RESOLVED_MODE_ID, resolvedMode.name());
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public void setCoordinateMode(CoordinateMode coordinateMode) {
        if (coordinateMode != null && this.coordinateMode != coordinateMode) {
            this.coordinateMode = coordinateMode;
            markDirty();
        }
    }

    public void setWrapMode(WrapMode wrapMode) {
        if (wrapMode != null && this.wrapMode != wrapMode) {
            this.wrapMode = wrapMode;
            markDirty();
        }
    }

    public void setFilterMode(FilterMode filterMode) {
        if (filterMode != null && this.filterMode != filterMode) {
            this.filterMode = filterMode;
            markDirty();
        }
    }

    public void setOriginMode(OriginMode originMode) {
        if (originMode != null && this.originMode != originMode) {
            this.originMode = originMode;
            markDirty();
        }
    }

    public CoordinateMode getCoordinateMode() {
        return coordinateMode == null ? CoordinateMode.UV : coordinateMode;
    }

    @Override
    protected float calculateUIHeight() {
        boolean hasImage = inputValues.get(INPUT_IMAGE_ID) instanceof ImageData;
        return ImagePreviewWidget.preferredHeight(hasImage, true)
            + CustomUIMetricsSafe.layoutFrameHeight()
            + getMediumPadding();
    }

    @Override
    protected float calculateMinUIWidth() {
        return 168.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);

            CoordinateMode mode = getCoordinateMode();
            ImGui.textDisabled(mode.name() + " Mode");

            ImageData image = inputValues.get(INPUT_IMAGE_ID) instanceof ImageData data ? data : null;
            double sampleX = doubleOutput(OUTPUT_SAMPLE_X_ID);
            double sampleY = doubleOutput(OUTPUT_SAMPLE_Y_ID);
            float thumbH = l.toPixels(ImagePreviewWidget.LOGICAL_THUMB_HEIGHT);
            ImagePreviewWidget.renderThumbnail(
                getId(),
                image,
                availableWidth,
                thumbH,
                zoom,
                image != null && Boolean.TRUE.equals(outputValues.get(OUTPUT_VALID_ID)),
                sampleX,
                sampleY
            );

            if (mode == CoordinateMode.UV) {
                ImGui.text(String.format("U %.2f  V %.2f",
                    doubleInput(INPUT_U_ID), doubleInput(INPUT_V_ID)));
            } else {
                ImGui.text(String.format("X %.1f  Y %.1f",
                    doubleInput(INPUT_X_ID), doubleInput(INPUT_Y_ID)));
            }

            ColorData color = outputValues.get(OUTPUT_COLOR_ID) instanceof ColorData c ? c : null;
            float swatch = l.toPixels(14.0f);
            ImagePreviewWidget.renderSampleSwatch(color, swatch);
            ImGui.sameLine();
            if (Boolean.TRUE.equals(outputValues.get(OUTPUT_VALID_ID))) {
                ImGui.text("Sample");
            } else {
                Object err = outputValues.get(OUTPUT_ERROR_ID);
                ImGui.textDisabled(err instanceof String s && !s.isBlank() ? s : "Invalid");
            }
            return false;
        });
    }

    private double doubleOutput(String portId) {
        Object value = outputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private double doubleInput(String portId) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("coordinateMode", getCoordinateMode().name());
        state.put("wrapMode", (wrapMode == null ? WrapMode.CLAMP : wrapMode).name());
        state.put("filterMode", (filterMode == null ? FilterMode.BILINEAR : filterMode).name());
        state.put("originMode", (originMode == null ? OriginMode.TOP_LEFT : originMode).name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("coordinateMode") instanceof String text) {
            try {
                setCoordinateMode(CoordinateMode.valueOf(text));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("wrapMode") instanceof String text) {
            try {
                setWrapMode(WrapMode.valueOf(text));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("filterMode") instanceof String text) {
            try {
                setFilterMode(FilterMode.valueOf(text));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("originMode") instanceof String text) {
            try {
                setOriginMode(OriginMode.valueOf(text));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
