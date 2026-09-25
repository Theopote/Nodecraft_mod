package com.nodecraft.nodesystem.nodes.input.values;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.values.gradient_ramp",
    displayName = "Gradient Ramp",
    description = "Creates and samples a customizable multi-stop gradient ramp with a visual editor",
    category = "input.values",
    order = 8
)
public class GradientRampNode extends BaseCustomUINode {

    public enum GradientMode {
        SCALAR,
        LINEAR,
        RADIAL,
        BOX,
        ANGULAR
    }

    public enum InterpolationMode {
        LINEAR,
        SMOOTH,
        CONSTANT
    }

    public enum WrapMode {
        CLAMP,
        REPEAT,
        MIRROR
    }

    private static final float RAMP_HEIGHT = 26.0f;
    private static final float MARKER_HEIGHT = 12.0f;
    private static final int MAX_STOPS = 12;

    private static final String INPUT_T_ID = "input_t";
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";

    private static final String OUTPUT_COLOR_ID = "output_color";
    private static final String OUTPUT_RED_ID = "output_red";
    private static final String OUTPUT_GREEN_ID = "output_green";
    private static final String OUTPUT_BLUE_ID = "output_blue";
    private static final String OUTPUT_ALPHA_ID = "output_alpha";
    private static final String OUTPUT_T_ID = "output_t";
    private static final String OUTPUT_HEX_ID = "output_hex";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Gradient Mode", category = "Sampling", order = 1)
    private GradientMode gradientMode = GradientMode.SCALAR;

    @NodeProperty(displayName = "Interpolation", category = "Sampling", order = 2)
    private InterpolationMode interpolationMode = InterpolationMode.LINEAR;

    @NodeProperty(displayName = "Wrap Mode", category = "Sampling", order = 3)
    private WrapMode wrapMode = WrapMode.CLAMP;

    @NodeProperty(displayName = "Angle Degrees", category = "Linear", order = 4)
    private double angleDegrees = 0.0d;

    @NodeProperty(displayName = "Center X", category = "Coordinate", order = 5)
    private double centerX = 0.5d;

    @NodeProperty(displayName = "Center Y", category = "Coordinate", order = 6)
    private double centerY = 0.5d;

    @NodeProperty(displayName = "Radius", category = "Coordinate", order = 7)
    private double radius = 0.5d;

    @NodeProperty(displayName = "Show Sample T", category = "UI", order = 10)
    private boolean showSampleInput = true;

    private final List<GradientStop> stops = new ArrayList<>();
    private int selectedStopIndex = 0;

    public GradientRampNode() {
        super(UUID.randomUUID(), "input.values.gradient_ramp");

        addInputPort(new BasePort(INPUT_T_ID, "T", "Scalar sample coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_ID, "X", "2D sample X coordinate for fill modes", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "2D sample Y coordinate for fill modes", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_COLOR_ID, "Color", "Sampled color", NodeDataType.COLOR, this));
        addOutputPort(new BasePort(OUTPUT_RED_ID, "R", "Red channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_GREEN_ID, "G", "Green channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BLUE_ID, "B", "Blue channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ALPHA_ID, "A", "Alpha channel in 0..1", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_T_ID, "T", "Resolved normalized sample coordinate", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HEX_ID, "Hex", "Sampled color as #RRGGBB", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sampling inputs and stops are finite and radius is positive when required", NodeDataType.BOOLEAN, this));

        stops.add(new GradientStop(0.0d, new ColorData(0.08f, 0.12f, 0.18f, 1.0f)));
        stops.add(new GradientStop(0.5d, new ColorData(0.10f, 0.65f, 0.88f, 1.0f)));
        stops.add(new GradientStop(1.0d, new ColorData(0.95f, 0.82f, 0.24f, 1.0f)));
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "Creates and samples a customizable multi-stop gradient ramp with a visual editor";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += RAMP_HEIGHT + MARKER_HEIGHT;
        height += getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        height += ImGui.getFrameHeight();
        if (showSampleInput) {
            height += getMediumPadding();
            height += ImGui.getFrameHeight();
        }
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 260.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            boolean interacted = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(140.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            changed |= renderRampEditor(availableWidth, l.toPixels(RAMP_HEIGHT), l.toPixels(MARKER_HEIGHT), zoom);
            interacted |= ImGui.isItemHovered() || ImGui.isItemActive();

            l.addVerticalSpacing(getMediumPadding());
            changed |= renderStopControls(availableWidth, l, zoom);
            interacted |= ImGui.isItemHovered() || ImGui.isItemActive();

            l.addVerticalSpacing(getMediumPadding());
            changed |= renderSelectedColorEditor(availableWidth, l, zoom);
            interacted |= ImGui.isItemHovered() || ImGui.isItemActive();

            if (showSampleInput) {
                l.addVerticalSpacing(getMediumPadding());
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                l.setItemWidth(Math.min(availableWidth, l.toPixels(120.0f)) / Math.max(zoom, 0.001f));
                ImDouble tInput = new ImDouble(getInputDouble(INPUT_T_ID, 0.5d));
                if (ImGui.inputDouble("T##gradient_sample_t", tInput, 0.0d, 0.0d, "%.3f")) {
                    inputValues.put(INPUT_T_ID, tInput.get());
                    updateOutput();
                    changed = true;
                    markDirty();
                }
                l.popItemWidth();
                interacted |= ImGui.isItemHovered() || ImGui.isItemActive();
            }

            l.addVerticalSpacing(getSmallPadding());
            return changed || interacted;
        });
    }

    private boolean renderRampEditor(float width, float rampHeight, float markerHeight, float zoom) {
        ensureValidStops();

        ImVec2 start = ImGui.getCursorScreenPos();
        float x0 = start.x;
        float y0 = start.y;
        float x1 = x0 + width;
        float y1 = y0 + rampHeight;
        float totalHeight = rampHeight + markerHeight;

        ImGui.invisibleButton("##gradient_ramp_editor", width, totalHeight);
        boolean changed = false;
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        float mouseX = ImGui.getMousePosX();

        if (hovered && ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            double t = clamp01((mouseX - x0) / Math.max(1.0f, width));
            int nearest = findNearestStop(t, 0.035d);
            if (nearest >= 0) {
                selectedStopIndex = nearest;
            } else if (stops.size() < MAX_STOPS) {
                ColorData color = sampleColor(t);
                stops.add(new GradientStop(t, color));
                sortStops();
                selectedStopIndex = findNearestStop(t, 0.001d);
                changed = true;
            }
        }

        if (active && selectedStopIndex >= 0 && selectedStopIndex < stops.size() && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            double t = clamp01((mouseX - x0) / Math.max(1.0f, width));
            GradientStop selected = stops.get(selectedStopIndex);
            if (Math.abs(selected.position - t) > 1.0e-5d) {
                selected.position = t;
                sortStopsKeeping(selected);
                changed = true;
            }
        }

        drawRamp(ImGui.getWindowDrawList(), x0, y0, x1, y1, markerHeight, zoom, hovered || active);
        if (changed) {
            updateOutput();
            markDirty();
        }
        return changed;
    }

    private boolean renderStopControls(float availableWidth, LayoutHelper l, float zoom) {
        boolean changed = false;
        GradientStop selected = getSelectedStop();
        if (selected == null) {
            return false;
        }

        float buttonHeight = ImGui.getFrameHeight();
        float buttonWidth = Math.min(l.toPixels(72.0f), availableWidth * 0.32f);
        if (ImGui.button("Add", buttonWidth, buttonHeight) && stops.size() < MAX_STOPS) {
            double t = selected.position < 0.95d ? selected.position + 0.1d : selected.position - 0.1d;
            t = clamp01(t);
            stops.add(new GradientStop(t, selected.color));
            sortStops();
            selectedStopIndex = findNearestStop(t, 0.001d);
            updateOutput();
            markDirty();
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.button("Remove", buttonWidth, buttonHeight) && stops.size() > 2) {
            stops.remove(selectedStopIndex);
            selectedStopIndex = Math.max(0, Math.min(selectedStopIndex, stops.size() - 1));
            updateOutput();
            markDirty();
            changed = true;
        }
        ImGui.sameLine();
        l.setItemWidth(Math.max(availableWidth - buttonWidth * 2.0f - ImGui.getStyle().getItemSpacingX() * 2.0f, l.toPixels(64.0f)) / Math.max(zoom, 0.001f));
        ImDouble positionInput = new ImDouble(selected.position);
        if (ImGui.inputDouble("Pos##gradient_stop_pos", positionInput, 0.0d, 0.0d, "%.3f")) {
            selected.position = clamp01(positionInput.get());
            sortStopsKeeping(selected);
            updateOutput();
            markDirty();
            changed = true;
        }
        l.popItemWidth();
        return changed;
    }

    private boolean renderSelectedColorEditor(float availableWidth, LayoutHelper l, float zoom) {
        GradientStop selected = getSelectedStop();
        if (selected == null) {
            return false;
        }
        float[] color = {selected.color.r(), selected.color.g(), selected.color.b(), selected.color.a()};
        int flags = ImGuiColorEditFlags.DisplayRGB
            | ImGuiColorEditFlags.InputRGB
            | ImGuiColorEditFlags.Float
            | ImGuiColorEditFlags.AlphaBar
            | ImGuiColorEditFlags.AlphaPreview;

        l.pushFramePadding(4.0f, 4.0f);
        l.setItemWidth(availableWidth / Math.max(zoom, 0.001f));
        boolean changed = ImGui.colorEdit4("##gradient_stop_color", color, flags);
        l.popItemWidth();
        l.popStyleVar();

        if (changed) {
            selected.color = new ColorData(color[0], color[1], color[2], color[3]);
            updateOutput();
            markDirty();
        }
        return changed;
    }

    private void drawRamp(ImDrawList drawList, float x0, float y0, float x1, float y1,
                          float markerHeight, float zoom, boolean hot) {
        float width = Math.max(1.0f, x1 - x0);
        int steps = Math.max(32, Math.min(128, (int) width));
        for (int i = 0; i < steps; i++) {
            double t0 = (double) i / steps;
            double t1 = (double) (i + 1) / steps;
            ColorData color = sampleColor((t0 + t1) * 0.5d);
            float sx0 = x0 + (float) t0 * width;
            float sx1 = x0 + (float) t1 * width + 0.75f;
            drawList.addRectFilled(sx0, y0, sx1, y1, toU32(color), 0.0f);
        }

        int border = hot
            ? ImGui.colorConvertFloat4ToU32(0.45f, 0.66f, 0.95f, 1.0f)
            : ImGui.colorConvertFloat4ToU32(0.32f, 0.34f, 0.38f, 1.0f);
        drawList.addRect(x0, y0, x1, y1, border, 3.0f * zoom, 0, Math.max(1.0f, 1.25f * zoom));

        float markerTop = y1 + 2.0f * zoom;
        for (int i = 0; i < stops.size(); i++) {
            GradientStop stop = stops.get(i);
            float mx = x0 + (float) stop.position * width;
            boolean selected = i == selectedStopIndex;
            int markerFill = toU32(stop.color);
            int markerBorder = selected
                ? ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f)
                : ImGui.colorConvertFloat4ToU32(0.08f, 0.09f, 0.10f, 1.0f);
            float radius = selected ? 5.5f * zoom : 4.5f * zoom;
            drawList.addLine(mx, y0, mx, y1 + markerHeight, markerBorder, selected ? 2.0f * zoom : 1.0f * zoom);
            drawList.addCircleFilled(mx, markerTop + radius, Math.max(3.5f, radius), markerFill, 20);
            drawList.addCircle(mx, markerTop + radius, Math.max(3.5f, radius), markerBorder, 20, Math.max(1.0f, zoom));
        }
    }

    private void updateOutput() {
        ensureValidStops();
        SampleResult sample = resolveSample();
        if (!sample.valid()) {
            invalidateOutputs();
            return;
        }
        ColorData color = sample.color();
        double t = sample.t();
        outputValues.put(OUTPUT_COLOR_ID, color);
        outputValues.put(OUTPUT_RED_ID, (double) color.r());
        outputValues.put(OUTPUT_GREEN_ID, (double) color.g());
        outputValues.put(OUTPUT_BLUE_ID, (double) color.b());
        outputValues.put(OUTPUT_ALPHA_ID, (double) color.a());
        outputValues.put(OUTPUT_T_ID, t);
        outputValues.put(OUTPUT_HEX_ID, toHex(color));
        outputValues.put(OUTPUT_VALID_ID, true);
        syncOutputPorts();
    }

    private void invalidateOutputs() {
        outputValues.put(OUTPUT_COLOR_ID, null);
        outputValues.put(OUTPUT_RED_ID, Double.NaN);
        outputValues.put(OUTPUT_GREEN_ID, Double.NaN);
        outputValues.put(OUTPUT_BLUE_ID, Double.NaN);
        outputValues.put(OUTPUT_ALPHA_ID, Double.NaN);
        outputValues.put(OUTPUT_T_ID, Double.NaN);
        outputValues.put(OUTPUT_HEX_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        syncOutputPorts();
    }

    private SampleResult resolveSample() {
        if (!stopsFinite()) {
            return SampleResult.invalid();
        }
        if (!Double.isFinite(angleDegrees) || !Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            return SampleResult.invalid();
        }
        GradientMode mode = gradientMode == null ? GradientMode.SCALAR : gradientMode;
        if (mode == GradientMode.RADIAL || mode == GradientMode.BOX) {
            if (!Double.isFinite(radius) || radius <= 0.0d) {
                return SampleResult.invalid();
            }
        }

        Double raw = resolveSampleTRaw();
        if (raw == null || !Double.isFinite(raw)) {
            return SampleResult.invalid();
        }
        double t = wrap(raw);
        if (!Double.isFinite(t)) {
            return SampleResult.invalid();
        }
        ColorData color = sampleColor(t);
        if (!ValueInputUtils.isFiniteColor(color)) {
            return SampleResult.invalid();
        }
        return new SampleResult(true, t, color);
    }

    private boolean stopsFinite() {
        if (stops.isEmpty()) {
            return false;
        }
        for (GradientStop stop : stops) {
            if (!Double.isFinite(stop.position) || !ValueInputUtils.isFiniteColor(stop.color)) {
                return false;
            }
        }
        return true;
    }

    private @Nullable Double resolveSampleTRaw() {
        if (gradientMode == GradientMode.SCALAR) {
            Double t = getInputDoubleOrNull(INPUT_T_ID, 0.5d);
            return t;
        }
        Double x = getInputDoubleOrNull(INPUT_X_ID, 0.5d);
        Double y = getInputDoubleOrNull(INPUT_Y_ID, 0.5d);
        if (x == null || y == null || !Double.isFinite(x) || !Double.isFinite(y)) {
            return null;
        }
        return sampleCoordinate(x, y);
    }

    private double sampleCoordinate(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;
        GradientMode mode = gradientMode == null ? GradientMode.SCALAR : gradientMode;
        return switch (mode) {
            case LINEAR -> {
                double angle = Math.toRadians(angleDegrees);
                yield 0.5d + dx * Math.cos(angle) + dy * Math.sin(angle);
            }
            case RADIAL -> Math.sqrt(dx * dx + dy * dy) / radius;
            case BOX -> Math.max(Math.abs(dx), Math.abs(dy)) / radius;
            case ANGULAR -> {
                double a = Math.atan2(dy, dx) + Math.PI;
                yield a / (Math.PI * 2.0d);
            }
            case SCALAR -> {
                Double t = getInputDoubleOrNull(INPUT_T_ID, 0.5d);
                yield t == null ? Double.NaN : t;
            }
        };
    }

    private double wrap(double value) {
        WrapMode mode = wrapMode == null ? WrapMode.CLAMP : wrapMode;
        return switch (mode) {
            case CLAMP -> clamp01(value);
            case REPEAT -> {
                double wrapped = value % 1.0d;
                yield wrapped < 0.0d ? wrapped + 1.0d : wrapped;
            }
            case MIRROR -> {
                double wrapped = value % 2.0d;
                if (wrapped < 0.0d) {
                    wrapped += 2.0d;
                }
                yield wrapped <= 1.0d ? wrapped : 2.0d - wrapped;
            }
        };
    }

    private ColorData sampleColor(double t) {
        ensureValidStops();
        double u = clamp01(t);
        if (stops.size() == 1 || u <= stops.get(0).position) {
            return stops.get(0).color;
        }
        GradientStop last = stops.get(stops.size() - 1);
        if (u >= last.position) {
            return last.color;
        }

        for (int i = 0; i < stops.size() - 1; i++) {
            GradientStop a = stops.get(i);
            GradientStop b = stops.get(i + 1);
            if (u >= a.position && u <= b.position) {
                if (interpolationMode == InterpolationMode.CONSTANT) {
                    return a.color;
                }
                double span = b.position - a.position;
                if (span == 0.0d) {
                    // Equal stops: pick upper stop for LINEAR/SMOOTH.
                    return b.color;
                }
                double k = (u - a.position) / span;
                if (interpolationMode == InterpolationMode.SMOOTH) {
                    k = k * k * (3.0d - 2.0d * k);
                }
                return lerp(a.color, b.color, k);
            }
        }
        return last.color;
    }

    private ColorData lerp(ColorData a, ColorData b, double t) {
        float k = (float) clamp01(t);
        return new ColorData(
            a.r() + (b.r() - a.r()) * k,
            a.g() + (b.g() - a.g()) * k,
            a.b() + (b.b() - a.b()) * k,
            a.a() + (b.a() - a.a()) * k
        );
    }

    private List<Map<String, Object>> toRampList() {
        List<Map<String, Object>> ramp = new ArrayList<>();
        for (GradientStop stop : stops) {
            Map<String, Object> item = new HashMap<>();
            item.put("position", stop.position);
            item.put("r", (double) stop.color.r());
            item.put("g", (double) stop.color.g());
            item.put("b", (double) stop.color.b());
            item.put("a", (double) stop.color.a());
            item.put("hex", toHex(stop.color));
            ramp.add(item);
        }
        return ramp;
    }

    private void ensureValidStops() {
        if (stops.isEmpty()) {
            stops.add(new GradientStop(0.0d, ColorData.BLACK));
            stops.add(new GradientStop(1.0d, ColorData.WHITE));
        }
        for (GradientStop stop : stops) {
            stop.position = clamp01(stop.position);
            if (stop.color == null) {
                stop.color = ColorData.WHITE;
            }
        }
        sortStops();
        selectedStopIndex = Math.max(0, Math.min(selectedStopIndex, stops.size() - 1));
    }

    private void sortStops() {
        stops.sort(Comparator.comparingDouble(s -> s.position));
    }

    private void sortStopsKeeping(GradientStop selected) {
        sortStops();
        selectedStopIndex = stops.indexOf(selected);
    }

    private int findNearestStop(double t, double threshold) {
        int nearest = -1;
        double best = threshold;
        for (int i = 0; i < stops.size(); i++) {
            double distance = Math.abs(stops.get(i).position - t);
            if (distance <= best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    private GradientStop getSelectedStop() {
        ensureValidStops();
        if (selectedStopIndex < 0 || selectedStopIndex >= stops.size()) {
            return null;
        }
        return stops.get(selectedStopIndex);
    }

    private double getInputDouble(String portId, double fallback) {
        Double value = getInputDoubleOrNull(portId, fallback);
        return value == null ? fallback : value;
    }

    private @Nullable Double getInputDoubleOrNull(String portId, double fallback) {
        Object value = inputValues.get(portId);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record SampleResult(boolean valid, double t, @Nullable ColorData color) {
        private static SampleResult invalid() {
            return new SampleResult(false, Double.NaN, null);
        }
    }

    private int toU32(ColorData color) {
        return ImGui.colorConvertFloat4ToU32(color.r(), color.g(), color.b(), color.a());
    }

    private String toHex(ColorData color) {
        int r = Math.round(color.r() * 255.0f);
        int g = Math.round(color.g() * 255.0f);
        int b = Math.round(color.b() * 255.0f);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("gradientMode", gradientMode.name());
        state.put("interpolationMode", interpolationMode.name());
        state.put("wrapMode", wrapMode.name());
        state.put("angleDegrees", angleDegrees);
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("radius", radius);
        state.put("showSampleInput", showSampleInput);
        state.put("selectedStopIndex", selectedStopIndex);
        state.put("stops", toRampList());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("gradientMode") instanceof String value) {
            gradientMode = parseEnum(value, GradientMode.SCALAR);
        }
        if (map.get("interpolationMode") instanceof String value) {
            interpolationMode = parseEnum(value, InterpolationMode.LINEAR);
        }
        if (map.get("wrapMode") instanceof String value) {
            wrapMode = parseEnum(value, WrapMode.CLAMP);
        }
        if (map.get("angleDegrees") instanceof Number value) {
            angleDegrees = value.doubleValue();
        }
        if (map.get("centerX") instanceof Number value) {
            centerX = value.doubleValue();
        }
        if (map.get("centerY") instanceof Number value) {
            centerY = value.doubleValue();
        }
        if (map.get("radius") instanceof Number value) {
            radius = value.doubleValue();
        }
        if (map.get("showSampleInput") instanceof Boolean value) {
            showSampleInput = value;
        }
        if (map.get("selectedStopIndex") instanceof Number value) {
            selectedStopIndex = value.intValue();
        }
        if (map.get("stops") instanceof List<?> list) {
            stops.clear();
            for (Object item : list) {
                GradientStop stop = parseStop(item);
                if (stop != null) {
                    stops.add(stop);
                }
            }
        }
        ensureValidStops();
        updateOutput();
        invalidateCache();
        markDirty();
    }

    private GradientStop parseStop(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        double position = map.get("position") instanceof Number n ? n.doubleValue() : 0.0d;
        float r = map.get("r") instanceof Number n ? n.floatValue() : 1.0f;
        float g = map.get("g") instanceof Number n ? n.floatValue() : 1.0f;
        float b = map.get("b") instanceof Number n ? n.floatValue() : 1.0f;
        float a = map.get("a") instanceof Number n ? n.floatValue() : 1.0f;
        return new GradientStop(position, new ColorData(r, g, b, a));
    }

    private <T extends Enum<T>> T parseEnum(String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static final class GradientStop {
        private double position;
        private ColorData color;

        private GradientStop(double position, ColorData color) {
            this.position = position;
            this.color = color;
        }
    }
}
