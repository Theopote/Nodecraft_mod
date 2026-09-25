package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.NumericInputUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.xy_slider",
    displayName = "XY Slider",
    description = "Provides a two-dimensional slider pad that outputs X and Y values from one draggable handle",
    category = "input.numeric",
    order = 6
)
public class XYSliderNode extends BaseCustomUINode {

    private static final float PAD_SIZE = 132.0f;

    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_UV_ID = "output_uv";

    @NodeProperty(displayName = "X", category = "Value", order = 1)
    private double x = 0.5d;

    @NodeProperty(displayName = "Y", category = "Value", order = 2)
    private double y = 0.5d;

    @NodeProperty(displayName = "Min X", category = "Range", order = 3)
    private double minX = 0.0d;

    @NodeProperty(displayName = "Max X", category = "Range", order = 4)
    private double maxX = 1.0d;

    @NodeProperty(displayName = "Min Y", category = "Range", order = 5)
    private double minY = 0.0d;

    @NodeProperty(displayName = "Max Y", category = "Range", order = 6)
    private double maxY = 1.0d;

    @NodeProperty(displayName = "Step", category = "Range", order = 7,
        description = "Snap interval. Use 0 to disable snapping.")
    private double step = 0.0d;

    @NodeProperty(displayName = "Precision", category = "UI", order = 10)
    private int precision = 2;

    @NodeProperty(displayName = "Y Axis Up", category = "UI", order = 11,
        description = "When enabled, higher Y values are drawn toward the top of the pad.")
    private boolean yAxisUp = true;

    @NodeProperty(displayName = "Show Value Inputs", category = "UI", order = 12)
    private boolean showValueInputs = true;

    public XYSliderNode() {
        super(UUID.randomUUID(), "input.numeric.xy_slider");
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "Current X value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Current Y value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_UV_ID, "UV", "Normalized UV pair [x, y]", NodeDataType.DOUBLE_LIST, this));
        normalizeRanges();
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Provides a two-dimensional slider pad that outputs X and Y values from one draggable handle";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += PAD_SIZE;
        height += getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        if (showValueInputs) {
            height += ImGui.getFrameHeight();
            height += getMediumPadding();
        }
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 196.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            boolean interacted = false;
            float availableWidth = l.getAvailableContentWidth(width);

            l.addVerticalSpacing(getMediumPadding());

            float padSize = Math.min(l.toPixels(PAD_SIZE), availableWidth);
            padSize = Math.max(padSize, l.toPixels(72.0f));
            setCenterX(availableWidth, padSize);

            ImVec2 padStart = ImGui.getCursorScreenPos();
            ImGui.invisibleButton("##xy_slider_pad", padSize, padSize);
            boolean hovered = ImGui.isItemHovered();
            boolean active = ImGui.isItemActive();
            interacted = hovered || active;

            if ((hovered || active) && ImGui.isMouseDown(0)) {
                ImVec2 mouse = ImGui.getMousePos();
                double nx = clamp01((mouse.x - padStart.x) / padSize);
                double screenNy = clamp01((mouse.y - padStart.y) / padSize);
                double ny = yAxisUp ? 1.0d - screenNy : screenNy;
                setValueFromNormalized(nx, ny);
                changed = true;
            }

            drawPad(ImGui.getWindowDrawList(), padStart.x, padStart.y, padSize, hovered || active, zoom);
            l.addVerticalSpacing(getMediumPadding());

            String valueText = String.format("X %s  Y %s", formatValue(x), formatValue(y));
            float textWidth = ImGui.calcTextSize(valueText).x;
            setCenterX(availableWidth, textWidth);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.78f, 0.80f, 0.84f, 1.0f);
            ImGui.text(valueText);
            ImGui.popStyleColor();
            l.addVerticalSpacing(getSmallPadding());

            if (showValueInputs) {
                float inputWidth = Math.max((availableWidth - l.toPixels(8.0f)) / 2.0f, l.toPixels(72.0f));
                l.setItemWidth(inputWidth / Math.max(zoom, 0.001f));
                ImDouble xInput = new ImDouble(x);
                if (ImGui.inputDouble("X##xy_x", xInput, 0.0d, 0.0d, "%." + getSafePrecision() + "f")) {
                    setX(xInput.get());
                    changed = true;
                }
                interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
                ImGui.sameLine();
                ImDouble yInput = new ImDouble(y);
                if (ImGui.inputDouble("Y##xy_y", yInput, 0.0d, 0.0d, "%." + getSafePrecision() + "f")) {
                    setY(yInput.get());
                    changed = true;
                }
                interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
                l.popItemWidth();
                l.addVerticalSpacing(getMediumPadding());
            }

            return interacted || changed;
        });
    }

    private void drawPad(ImDrawList drawList, float x0, float y0, float size, boolean hot, float zoom) {
        float x1 = x0 + size;
        float y1 = y0 + size;
        int bgColor = ImGui.colorConvertFloat4ToU32(0.13f, 0.14f, 0.16f, 1.0f);
        int gridColor = ImGui.colorConvertFloat4ToU32(0.28f, 0.30f, 0.34f, 0.75f);
        int borderColor = hot
            ? ImGui.colorConvertFloat4ToU32(0.38f, 0.60f, 0.92f, 1.0f)
            : ImGui.colorConvertFloat4ToU32(0.34f, 0.36f, 0.40f, 1.0f);
        int axisColor = ImGui.colorConvertFloat4ToU32(0.44f, 0.47f, 0.52f, 0.85f);
        int handleColor = ImGui.colorConvertFloat4ToU32(0.42f, 0.72f, 1.0f, 1.0f);
        int handleBorderColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.95f);

        drawList.addRectFilled(x0, y0, x1, y1, bgColor, 4.0f * zoom);
        for (int i = 1; i < 4; i++) {
            float t = i / 4.0f;
            float gx = x0 + size * t;
            float gy = y0 + size * t;
            drawList.addLine(gx, y0, gx, y1, gridColor, Math.max(1.0f, zoom));
            drawList.addLine(x0, gy, x1, gy, gridColor, Math.max(1.0f, zoom));
        }

        double nx = normalized(x, minX, maxX);
        double ny = normalized(y, minY, maxY);
        float handleX = x0 + (float) nx * size;
        float handleY = y0 + (float) (yAxisUp ? 1.0d - ny : ny) * size;

        drawList.addLine(handleX, y0, handleX, y1, axisColor, Math.max(1.0f, zoom));
        drawList.addLine(x0, handleY, x1, handleY, axisColor, Math.max(1.0f, zoom));
        drawList.addCircleFilled(handleX, handleY, Math.max(5.0f * zoom, 4.0f), handleColor, 24);
        drawList.addCircle(handleX, handleY, Math.max(5.0f * zoom, 4.0f), handleBorderColor, 24, Math.max(1.25f * zoom, 1.0f));
        drawList.addRect(x0, y0, x1, y1, borderColor, 4.0f * zoom, 0, Math.max(1.25f * zoom, 1.0f));
    }

    private void setValueFromNormalized(double nx, double ny) {
        double newX = minX + nx * (maxX - minX);
        double newY = minY + ny * (maxY - minY);
        setValues(newX, newY);
    }

    private void setValues(double newX, double newY) {
        if (!Double.isFinite(newX) || !Double.isFinite(newY)) {
            return;
        }
        double normalizedX = clampAndSnap(newX, minX, maxX);
        double normalizedY = clampAndSnap(newY, minY, maxY);
        if (Double.compare(x, normalizedX) != 0 || Double.compare(y, normalizedY) != 0) {
            x = normalizedX;
            y = normalizedY;
            updateOutput();
            markDirty();
        }
    }

    private double clampAndSnap(double value, double min, double max) {
        return NumericInputUtils.snapToStep(value, min, max, step);
    }

    private double normalized(double value, double min, double max) {
        return NumericInputUtils.normalizedInRange(value, min, max);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String formatValue(double value) {
        return String.format("%." + getSafePrecision() + "f", value);
    }

    private int getSafePrecision() {
        return Math.max(0, Math.min(6, precision));
    }

    private void normalizeRanges() {
        minX = NumericInputUtils.sanitizeFiniteBound(minX, 0.0d);
        maxX = NumericInputUtils.sanitizeFiniteBound(maxX, 1.0d);
        if (!NumericInputUtils.isFiniteUsableSpan(minX, maxX)) {
            minX = 0.0d;
            maxX = 1.0d;
        } else if (Double.compare(minX, maxX) > 0) {
            double temp = minX;
            minX = maxX;
            maxX = temp;
        }

        minY = NumericInputUtils.sanitizeFiniteBound(minY, 0.0d);
        maxY = NumericInputUtils.sanitizeFiniteBound(maxY, 1.0d);
        if (!NumericInputUtils.isFiniteUsableSpan(minY, maxY)) {
            minY = 0.0d;
            maxY = 1.0d;
        } else if (Double.compare(minY, maxY) > 0) {
            double temp = minY;
            minY = maxY;
            maxY = temp;
        }

        step = Double.isFinite(step) ? Math.max(0.0d, step) : 0.0d;
        x = clampAndSnap(x, minX, maxX);
        y = clampAndSnap(y, minY, maxY);
    }

    private void updateOutput() {
        double nx = normalized(x, minX, maxX);
        double ny = normalized(y, minY, maxY);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_UV_ID, List.of(nx, ny));
        syncOutputPorts();
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        setValues(x, y);
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        setValues(x, y);
    }

    public double getMinX() {
        return minX;
    }

    public void setMinX(double minX) {
        double sanitized = NumericInputUtils.sanitizeFiniteBound(minX, this.minX);
        if (Double.compare(this.minX, sanitized) != 0) {
            this.minX = sanitized;
            normalizeRanges();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public double getMaxX() {
        return maxX;
    }

    public void setMaxX(double maxX) {
        double sanitized = NumericInputUtils.sanitizeFiniteBound(maxX, this.maxX);
        if (Double.compare(this.maxX, sanitized) != 0) {
            this.maxX = sanitized;
            normalizeRanges();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public double getMinY() {
        return minY;
    }

    public void setMinY(double minY) {
        double sanitized = NumericInputUtils.sanitizeFiniteBound(minY, this.minY);
        if (Double.compare(this.minY, sanitized) != 0) {
            this.minY = sanitized;
            normalizeRanges();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public double getMaxY() {
        return maxY;
    }

    public void setMaxY(double maxY) {
        double sanitized = NumericInputUtils.sanitizeFiniteBound(maxY, this.maxY);
        if (Double.compare(this.maxY, sanitized) != 0) {
            this.maxY = sanitized;
            normalizeRanges();
            updateOutput();
            invalidateCache();
            markDirty();
        }
    }

    public double getStep() {
        return step;
    }

    public void setStep(double step) {
        double safeStep = Double.isFinite(step) ? Math.max(0.0d, step) : 0.0d;
        if (Double.compare(this.step, safeStep) != 0) {
            this.step = safeStep;
            normalizeRanges();
            updateOutput();
            markDirty();
        }
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        int safePrecision = Math.max(0, Math.min(6, precision));
        if (this.precision != safePrecision) {
            this.precision = safePrecision;
            invalidateCache();
            markDirty();
        }
    }

    public boolean isYAxisUp() {
        return yAxisUp;
    }

    public void setYAxisUp(boolean yAxisUp) {
        if (this.yAxisUp != yAxisUp) {
            this.yAxisUp = yAxisUp;
            markDirty();
        }
    }

    public boolean isShowValueInputs() {
        return showValueInputs;
    }

    public void setShowValueInputs(boolean showValueInputs) {
        if (this.showValueInputs != showValueInputs) {
            this.showValueInputs = showValueInputs;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("x", x);
        state.put("y", y);
        state.put("minX", minX);
        state.put("maxX", maxX);
        state.put("minY", minY);
        state.put("maxY", maxY);
        state.put("step", step);
        state.put("precision", precision);
        state.put("yAxisUp", yAxisUp);
        state.put("showValueInputs", showValueInputs);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("minX") instanceof Number value) {
            minX = NumericInputUtils.finiteOrFallback(value.doubleValue(), minX);
        }
        if (map.get("maxX") instanceof Number value) {
            maxX = NumericInputUtils.finiteOrFallback(value.doubleValue(), maxX);
        }
        if (map.get("minY") instanceof Number value) {
            minY = NumericInputUtils.finiteOrFallback(value.doubleValue(), minY);
        }
        if (map.get("maxY") instanceof Number value) {
            maxY = NumericInputUtils.finiteOrFallback(value.doubleValue(), maxY);
        }
        if (map.get("step") instanceof Number value) {
            step = NumericInputUtils.finiteOrFallback(value.doubleValue(), step);
        }
        if (map.get("precision") instanceof Number value) {
            precision = Math.max(0, Math.min(6, value.intValue()));
        }
        if (map.get("yAxisUp") instanceof Boolean value) {
            yAxisUp = value;
        }
        if (map.get("showValueInputs") instanceof Boolean value) {
            showValueInputs = value;
        }

        normalizeRanges();

        if (map.get("x") instanceof Number value) {
            setX(value.doubleValue());
        }
        if (map.get("y") instanceof Number value) {
            setY(value.doubleValue());
        }

        updateOutput();
        invalidateCache();
        markDirty();
    }
}
