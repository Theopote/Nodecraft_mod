package com.nodecraft.nodesystem.nodes.input.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Color;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.basic.color_picker",
    displayName = "Color Picker",
    description = "Allows selecting a color value with RGB and alpha support.",
    category = "input.values",
    order = 1
)
public class ColorPickerNode extends BaseCustomUINode {

    private static final float COLOR_PREVIEW_HEIGHT = 30.0f;
    private static final String OUTPUT_COLOR_ID = "output_color";
    private static final String OUTPUT_RED_ID = "output_red";
    private static final String OUTPUT_GREEN_ID = "output_green";
    private static final String OUTPUT_BLUE_ID = "output_blue";
    private static final String OUTPUT_ALPHA_ID = "output_alpha";

    @NodeProperty(displayName = "Color", category = "Settings", order = 1,
        description = "Current selected color value")
    private Color color = new Color(1.0f, 1.0f, 1.0f, 1.0f);

    @NodeProperty(displayName = "Include Alpha", category = "Settings", order = 2,
        description = "Whether alpha channel editing is enabled")
    private boolean includeAlpha = true;

    @NodeProperty(displayName = "Show Preview", category = "UI Settings", order = 10,
        description = "Show the top color preview bar")
    private boolean showPreview = true;

    @NodeProperty(displayName = "Show Hex Value", category = "UI Settings", order = 11,
        description = "Show the hexadecimal color value")
    private boolean showHexValue = true;

    @NodeProperty(displayName = "Show RGB Values", category = "UI Settings", order = 12,
        description = "Show numeric RGB channel values")
    private boolean showRGBValues = true;

    @NodeProperty(displayName = "Show Alpha Toggle", category = "UI Settings", order = 13,
        description = "Show alpha related controls in the editor")
    private boolean showAlphaToggle = true;

    private final float[] colorArray = {1.0f, 1.0f, 1.0f, 1.0f};
    private boolean needsUIUpdate = false;

    public ColorPickerNode() {
        super(UUID.randomUUID(), "input.basic.color_picker");

        IPort colorOutput = new BasePort(OUTPUT_COLOR_ID, "Color", "Current color value", NodeDataType.COLOR, this);
        addOutputPort(colorOutput);
        IPort redOutput = new BasePort(OUTPUT_RED_ID, "Red", "Red channel (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(redOutput);
        IPort greenOutput = new BasePort(OUTPUT_GREEN_ID, "Green", "Green channel (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(greenOutput);
        IPort blueOutput = new BasePort(OUTPUT_BLUE_ID, "Blue", "Blue channel (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(blueOutput);
        IPort alphaOutput = new BasePort(OUTPUT_ALPHA_ID, "Alpha", "Alpha channel (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(alphaOutput);

        updateColorArray();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Allows selecting a color value with RGB and alpha support.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        if (showPreview) {
            height += COLOR_PREVIEW_HEIGHT + getSmallPadding();
        }
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 196.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;

            if (needsUIUpdate) {
                updateColorArray();
                needsUIUpdate = false;
            }

            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            if (showPreview) {
                renderColorPreview(availableWidth, l, baseCursorX, edgeMargin);
                l.addVerticalSpacing(getSmallPadding());
            }

            changed |= renderColorEditor(availableWidth, l, baseCursorX, edgeMargin);

            return changed;
        });
    }

    private void renderColorPreview(float availableWidth, LayoutHelper l, float baseCursorX, float edgeMargin) {
        float buttonHeight = l.toPixels(COLOR_PREVIEW_HEIGHT);
        ImGui.setCursorPosX(baseCursorX + edgeMargin);
        ImGui.pushItemWidth(availableWidth);
        ImGui.colorButton("##color_preview_" + getId(), colorArray, ImGuiColorEditFlags.AlphaPreview, availableWidth, buttonHeight);
        ImGui.popItemWidth();
    }

    private boolean renderColorEditor(float availableWidth, LayoutHelper l, float baseCursorX, float edgeMargin) {
        int flags = ImGuiColorEditFlags.DisplayRGB
            | ImGuiColorEditFlags.InputRGB
            | ImGuiColorEditFlags.Float
            | ImGuiColorEditFlags.PickerHueWheel;

        if (includeAlpha) {
            flags |= ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreview;
        } else {
            flags |= ImGuiColorEditFlags.NoAlpha;
        }

        l.pushFramePadding(4.0f, 4.0f);
        l.pushFrameRounding(3.0f);
        ImGui.setCursorPosX(baseCursorX + edgeMargin);
        ImGui.pushItemWidth(availableWidth);
        boolean changed = includeAlpha
            ? ImGui.colorEdit4("##color_edit_" + getId(), colorArray, flags)
            : ImGui.colorEdit3("##color_edit_" + getId(), colorArray, flags);
        ImGui.popItemWidth();
        l.popStyleVar(2);

        if (changed) {
            onColorChangedFromUI();
        }
        return changed;
    }

    private void onColorChangedFromUI() {
        float alpha = includeAlpha ? colorArray[3] : color.getAlpha();
        Color newColor = new Color(colorArray[0], colorArray[1], colorArray[2], alpha);
        if (!this.color.equals(newColor)) {
            this.color = newColor;
            updateOutput();
            markDirty();
        }
    }

    private void updateColorArray() {
        colorArray[0] = color.getRed();
        colorArray[1] = color.getGreen();
        colorArray[2] = color.getBlue();
        colorArray[3] = color.getAlpha();
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        Color normalized = color != null ? color : new Color(1.0f, 1.0f, 1.0f, 1.0f);
        if (!includeAlpha) {
            normalized = new Color(normalized.getRed(), normalized.getGreen(), normalized.getBlue(), this.color.getAlpha());
        }
        if (!this.color.equals(normalized)) {
            this.color = normalized;
            needsUIUpdate = true;
            updateOutput();
            markDirty();
        }
    }

    public void setColor(float red, float green, float blue, float alpha) {
        if (!includeAlpha) {
            alpha = this.color.getAlpha();
        }
        setColor(new Color(red, green, blue, alpha));
    }

    public void setColorFromHex(String hexColor) {
        Color parsed = Color.fromHex(hexColor);
        if (!includeAlpha) {
            parsed = new Color(parsed.getRed(), parsed.getGreen(), parsed.getBlue(), this.color.getAlpha());
        }
        setColor(parsed);
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_COLOR_ID, color);
        outputValues.put(OUTPUT_RED_ID, color.getRed());
        outputValues.put(OUTPUT_GREEN_ID, color.getGreen());
        outputValues.put(OUTPUT_BLUE_ID, color.getBlue());
        outputValues.put(OUTPUT_ALPHA_ID, color.getAlpha());
        syncOutputPorts();
    }

    public boolean isIncludeAlpha() {
        return includeAlpha;
    }

    public void setIncludeAlpha(boolean includeAlpha) {
        if (this.includeAlpha != includeAlpha) {
            this.includeAlpha = includeAlpha;
            needsUIUpdate = true;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowPreview() {
        return showPreview;
    }

    public void setShowPreview(boolean showPreview) {
        if (this.showPreview != showPreview) {
            this.showPreview = showPreview;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowHexValue() {
        return showHexValue;
    }

    public void setShowHexValue(boolean showHexValue) {
        if (this.showHexValue != showHexValue) {
            this.showHexValue = showHexValue;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowRGBValues() {
        return showRGBValues;
    }

    public void setShowRGBValues(boolean showRGBValues) {
        if (this.showRGBValues != showRGBValues) {
            this.showRGBValues = showRGBValues;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isShowAlphaToggle() {
        return showAlphaToggle;
    }

    public void setShowAlphaToggle(boolean showAlphaToggle) {
        if (this.showAlphaToggle != showAlphaToggle) {
            this.showAlphaToggle = showAlphaToggle;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("color", color.toArgb());
        state.put("includeAlpha", includeAlpha);
        state.put("showPreview", showPreview);
        state.put("showHexValue", showHexValue);
        state.put("showRGBValues", showRGBValues);
        state.put("showAlphaToggle", showAlphaToggle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            if (stateMap.get("includeAlpha") instanceof Boolean includeAlpha) {
                setIncludeAlpha(includeAlpha);
            }
            if (stateMap.get("showPreview") instanceof Boolean showPreview) {
                setShowPreview(showPreview);
            }
            if (stateMap.get("showHexValue") instanceof Boolean showHexValue) {
                setShowHexValue(showHexValue);
            }
            if (stateMap.get("showRGBValues") instanceof Boolean showRGBValues) {
                setShowRGBValues(showRGBValues);
            }
            if (stateMap.get("showAlphaToggle") instanceof Boolean showAlphaToggle) {
                setShowAlphaToggle(showAlphaToggle);
            }
            if (stateMap.containsKey("color")) {
                Object colorValue = stateMap.get("color");
                if (colorValue instanceof Integer argb) {
                    setColor(new Color(argb));
                } else if (colorValue instanceof String hex) {
                    setColorFromHex(hex);
                }
            }
        }
    }
}
