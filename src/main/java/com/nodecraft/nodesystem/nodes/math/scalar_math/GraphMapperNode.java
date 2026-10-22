package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.gui.components.customui.CurvePlotWidget;
import com.nodecraft.gui.components.customui.CurvePlotWidget.BezierHandle;
import com.nodecraft.gui.components.customui.CustomUIPropertyHistory;
import com.nodecraft.gui.components.customui.CustomUIMetricsSafe;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import imgui.ImGui;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.NumericDomainResolver;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.graph_mapper",
    displayName = "Graph Mapper",
    description = "Advanced: maps a value through a selectable normalized graph function (Grasshopper-style Graph Mapper)",
    category = "math.scalar_math",
    order = 22
)
public class GraphMapperNode extends BaseCustomUINode {

    /** Local solver tolerance only — not graph-facing zero semantics. */
    private static final double SOLVER_EPS = 1.0e-12d;

    public enum CurveType {
        LINEAR,
        SMOOTHSTEP,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        SINE,
        POWER,
        EXPONENTIAL,
        GAUSSIAN,
        BEZIER
    }

    @NodeProperty(displayName = "Curve Type", category = "Graph", order = 1)
    private CurveType curveType = CurveType.SMOOTHSTEP;

    @NodeProperty(displayName = "Clamp Input", category = "Graph", order = 2)
    private boolean clampInput = true;

    @NodeProperty(displayName = "Default Exponent", category = "Graph", order = 3,
        description = "Used by Power, Ease, and Exponential mappings. Negative values use abs(); magnitudes below 0.001 clamp to 0.001.")
    private double defaultExponent = 2.0d;

    @NodeProperty(displayName = "Gaussian Center", category = "Graph", order = 4,
        description = "Gaussian center in normalized 0..1 space")
    private double gaussianCenter = 0.5d;

    @NodeProperty(displayName = "Gaussian Width", category = "Graph", order = 5,
        description = "Gaussian width in normalized 0..1 space")
    private double gaussianWidth = 0.2d;

    @NodeProperty(displayName = "Bezier X1", category = "Bezier", order = 6)
    private double bezierX1 = 0.25d;

    @NodeProperty(displayName = "Bezier Y1", category = "Bezier", order = 7)
    private double bezierY1 = 0.0d;

    @NodeProperty(displayName = "Bezier X2", category = "Bezier", order = 8)
    private double bezierX2 = 0.75d;

    @NodeProperty(displayName = "Bezier Y2", category = "Bezier", order = 9)
    private double bezierY2 = 1.0d;

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_SOURCE_ID = "input_source";
    private static final String INPUT_TARGET_ID = "input_target";
    private double defaultSourceStart = 0.0d;
    private double defaultSourceEnd = 1.0d;
    private double defaultTargetStart = 0.0d;
    private double defaultTargetEnd = 1.0d;

    private transient final CurvePlotWidget.InteractionState curveInteraction = new CurvePlotWidget.InteractionState();
    private transient Object bezierDragBeforeState;

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_T_ID = "output_t";
    private static final String OUTPUT_MAPPED_ID = "output_mapped";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GraphMapperNode() {
        super(UUID.randomUUID(), "math.scalar_math.graph_mapper");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value to map", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SOURCE_ID, "Source", "Source domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_TARGET_ID, "Target", "Target domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Mapped value remapped to target domain", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_T_ID, "T", "Normalized input parameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MAPPED_ID, "Mapped 0..1", "Graph function output before output-range remapping", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when mapping succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Graph Mapper";
    }

    @Override
    public String getDescription() {
        return "Advanced: maps a value through a selectable normalized graph function (Grasshopper-style Graph Mapper)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double valueObj = resolveOptionalValue();
        if (valueObj == null) {
            writeInvalid(ScalarMathPorts.ERROR_INVALID_INPUT);
            return;
        }
        double value = valueObj;
        NumericRangeData source = NumericDomainResolver.resolveOptionalDomain(
            this, INPUT_SOURCE_ID, defaultSourceStart, defaultSourceEnd);
        NumericRangeData target = NumericDomainResolver.resolveOptionalDomain(
            this, INPUT_TARGET_ID, defaultTargetStart, defaultTargetEnd);
        double exponent = defaultExponent;
        double center = gaussianCenter;
        double width = gaussianWidth;

        if (source == null || target == null
            || !allFinite(value, source.start(), source.end(), target.start(), target.end(), exponent, center, width)) {
            writeInvalid(ScalarMathPorts.ERROR_INVALID_DOMAIN);
            return;
        }
        if (source.delta() == 0.0d) {
            writeInvalid(ScalarMathPorts.ERROR_DEGENERATE_DOMAIN);
            return;
        }

        double t = source.normalizedParameter(value);
        if (clampInput) {
            t = clamp01(t);
        }

        double mapped = evaluate(t, exponent, center, width);
        if (!Double.isFinite(mapped)) {
            writeInvalid(ScalarMathPorts.ERROR_NON_FINITE_RESULT);
            return;
        }
        double result = target.lerp(mapped);
        if (!Double.isFinite(result) || !Double.isFinite(t)) {
            writeInvalid(ScalarMathPorts.ERROR_NON_FINITE_RESULT);
            return;
        }

        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_T_ID, t);
        outputValues.put(OUTPUT_MAPPED_ID, mapped);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    /**
     * Samples the normalized graph function at {@code t} using current node properties.
     * Intended for custom UI preview and unit tests.
     */
    public double sampleNormalizedCurve(double t) {
        return evaluate(t, defaultExponent, gaussianCenter, gaussianWidth);
    }

    public void resetCurveDefaults() {
        setCurveType(CurveType.SMOOTHSTEP);
        setClampInput(true);
        setDefaultExponent(2.0d);
        setGaussianCenter(0.5d);
        setGaussianWidth(0.2d);
        setBezierX1(0.25d);
        setBezierY1(0.0d);
        setBezierX2(0.75d);
        setBezierY2(1.0d);
        markDirty();
    }

    private void resetCurveDefaultsWithHistory() {
        Object before = getNodeState();
        resetCurveDefaults();
        CustomUIPropertyHistory.recordNodeStateChange(getId(), before, getNodeState(), "Reset curve");
    }

    @Override
    protected float calculateUIHeight() {
        float frameHeight = CustomUIMetricsSafe.logicalFrameHeight();
        float height = getMediumPadding();
        height += frameHeight;
        height += getSmallPadding();
        height += CurvePlotWidget.preferredHeight();
        height += getSmallPadding();
        height += frameHeight;
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 168.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.setItemWidth(Math.max(availableWidth / Math.max(zoom, 0.001f), 1.0f));
            CurveType current = curveType == null ? CurveType.SMOOTHSTEP : curveType;
            if (ImGui.beginCombo("##curve_type", current.name())) {
                for (CurveType type : CurveType.values()) {
                    if (ImGui.selectable(type.name(), type == current)) {
                        CustomUIPropertyHistory.recordPropertyChange(
                            getId(), "curveType", current.name(), type.name(), 0L);
                        setCurveType(type);
                        changed = true;
                    }
                }
                ImGui.endCombo();
            }
            l.popItemWidth();
            l.addVerticalSpacing(getSmallPadding());

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            float plotHeight = l.toPixels(CurvePlotWidget.LOGICAL_HEIGHT);
            changed = CurvePlotWidget.render(
                this, curveInteraction, availableWidth, plotHeight, zoom, bezierDragCallbacks()) || changed;
            l.addVerticalSpacing(getSmallPadding());

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            if (ImGui.button("Reset", Math.min(availableWidth * 0.4f, l.toPixels(72.0f)), ImGui.getFrameHeight())) {
                resetCurveDefaultsWithHistory();
                changed = true;
            }
            return changed;
        });
    }

    /**
     * Applies a Bezier handle drag through the same callbacks used by the custom UI plot.
     * Used by history integration tests and debugging.
     */
    public void applyBezierHandleDrag(BezierHandle handle, double x, double y) {
        CurvePlotWidget.BezierDragCallbacks callbacks = bezierDragCallbacks();
        callbacks.onBezierDragStarted(handle);
        callbacks.onBezierDragged(handle, x, y);
        callbacks.onBezierDragEnded(handle);
    }

    private CurvePlotWidget.BezierDragCallbacks bezierDragCallbacks() {
        return new CurvePlotWidget.BezierDragCallbacks() {
            @Override
            public void onBezierDragStarted(BezierHandle handle) {
                bezierDragBeforeState = getNodeState();
            }

            @Override
            public void onBezierDragged(BezierHandle handle, double x, double y) {
                if (handle == BezierHandle.P1) {
                    setBezierX1(x);
                    setBezierY1(y);
                } else if (handle == BezierHandle.P2) {
                    setBezierX2(x);
                    setBezierY2(y);
                }
                markDirty();
            }

            @Override
            public void onBezierDragEnded(BezierHandle handle) {
                if (bezierDragBeforeState != null) {
                    Object after = getNodeState();
                    CustomUIPropertyHistory.recordNodeStateChange(
                        getId(), bezierDragBeforeState, after, "Adjust Bezier curve");
                    bezierDragBeforeState = null;
                }
            }
        };
    }

    private double evaluate(double t, double exponent, double center, double width) {
        CurveType type = curveType == null ? CurveType.SMOOTHSTEP : curveType;
        return switch (type) {
            case LINEAR -> t;
            case SMOOTHSTEP -> smoothstep(clamp01(t));
            case EASE_IN -> Math.pow(clamp01(t), safeExponent(exponent));
            case EASE_OUT -> 1.0d - Math.pow(1.0d - clamp01(t), safeExponent(exponent));
            case EASE_IN_OUT -> easeInOut(clamp01(t), safeExponent(exponent));
            case SINE -> 0.5d - 0.5d * Math.cos(Math.PI * clamp01(t));
            case POWER -> Math.pow(Math.max(0.0d, clampInput ? clamp01(t) : t), safeExponent(exponent));
            case EXPONENTIAL -> exponential(clamp01(t), exponent);
            case GAUSSIAN -> gaussian(t, center, width);
            case BEZIER -> cubicBezierYForX(clamp01(t), bezierX1, bezierY1, bezierX2, bezierY2);
        };
    }

    private static double smoothstep(double t) {
        return t * t * (3.0d - 2.0d * t);
    }

    private static double easeInOut(double t, double exponent) {
        if (t < 0.5d) {
            return 0.5d * Math.pow(t * 2.0d, exponent);
        }
        return 1.0d - 0.5d * Math.pow((1.0d - t) * 2.0d, exponent);
    }

    private static double exponential(double t, double strength) {
        if (Math.abs(strength) <= SOLVER_EPS) {
            return t;
        }
        double denom = Math.exp(strength) - 1.0d;
        if (Math.abs(denom) <= SOLVER_EPS) {
            return t;
        }
        return (Math.exp(strength * t) - 1.0d) / denom;
    }

    private static double gaussian(double t, double center, double width) {
        double sigma = Math.max(SOLVER_EPS, Math.abs(width));
        double x = (t - center) / sigma;
        return Math.exp(-0.5d * x * x);
    }

    private static double cubicBezierYForX(double x, double x1, double y1, double x2, double y2) {
        double cx1 = clamp01(x1);
        double cx2 = clamp01(x2);
        double lo = 0.0d;
        double hi = 1.0d;
        double u = x;
        for (int i = 0; i < 20; i++) {
            double bx = cubicBezier(u, 0.0d, cx1, cx2, 1.0d);
            double dx = cubicBezierDerivative(u, 0.0d, cx1, cx2, 1.0d);
            if (Math.abs(bx - x) <= 1.0e-7d) {
                break;
            }
            if (bx < x) {
                lo = u;
            } else {
                hi = u;
            }
            if (Math.abs(dx) > SOLVER_EPS) {
                double next = u - (bx - x) / dx;
                u = next > lo && next < hi ? next : (lo + hi) * 0.5d;
            } else {
                u = (lo + hi) * 0.5d;
            }
        }
        return cubicBezier(u, 0.0d, y1, y2, 1.0d);
    }

    private static double cubicBezier(double t, double p0, double p1, double p2, double p3) {
        double u = 1.0d - t;
        return u * u * u * p0
            + 3.0d * u * u * t * p1
            + 3.0d * u * t * t * p2
            + t * t * t * p3;
    }

    private static double cubicBezierDerivative(double t, double p0, double p1, double p2, double p3) {
        double u = 1.0d - t;
        return 3.0d * u * u * (p1 - p0)
            + 6.0d * u * t * (p2 - p1)
            + 3.0d * t * t * (p3 - p2);
    }

    private static double safeExponent(double exponent) {
        return Math.max(0.001d, Math.abs(exponent));
    }

    private @Nullable Double resolveOptionalValue() {
        if (ScalarMathPorts.isPortDriven(this, INPUT_VALUE_ID)) {
            return StrictDoubleUtils.requireExactFiniteDouble(getInput(INPUT_VALUE_ID));
        }
        return 0.0d;
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
        outputValues.put(OUTPUT_T_ID, Double.NaN);
        outputValues.put(OUTPUT_MAPPED_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public CurveType getCurveType() {
        return curveType;
    }

    public void setCurveType(CurveType curveType) {
        CurveType resolved = curveType == null ? CurveType.SMOOTHSTEP : curveType;
        if (this.curveType != resolved) {
            this.curveType = resolved;
            markDirty();
        }
    }

    public void setCurveTypeString(String curveType) {
        if (curveType == null || curveType.isBlank()) {
            setCurveType(CurveType.SMOOTHSTEP);
            return;
        }
        try {
            setCurveType(CurveType.valueOf(curveType.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setCurveType(CurveType.SMOOTHSTEP);
        }
    }

    public boolean isClampInput() {
        return clampInput;
    }

    public void setClampInput(boolean clampInput) {
        if (this.clampInput != clampInput) {
            this.clampInput = clampInput;
            markDirty();
        }
    }

    public double getDefaultExponent() {
        return defaultExponent;
    }

    public void setDefaultExponent(double defaultExponent) {
        if (Double.isFinite(defaultExponent) && Double.compare(this.defaultExponent, defaultExponent) != 0) {
            this.defaultExponent = defaultExponent;
            markDirty();
        }
    }

    public double getGaussianCenter() {
        return gaussianCenter;
    }

    public void setGaussianCenter(double gaussianCenter) {
        if (Double.isFinite(gaussianCenter) && Double.compare(this.gaussianCenter, gaussianCenter) != 0) {
            this.gaussianCenter = gaussianCenter;
            markDirty();
        }
    }

    public double getGaussianWidth() {
        return gaussianWidth;
    }

    public void setGaussianWidth(double gaussianWidth) {
        double width = Math.max(SOLVER_EPS, Math.abs(gaussianWidth));
        if (Double.isFinite(width) && Double.compare(this.gaussianWidth, width) != 0) {
            this.gaussianWidth = width;
            markDirty();
        }
    }

    public double getBezierX1() {
        return bezierX1;
    }

    public void setBezierX1(double bezierX1) {
        setBezierValue("x1", bezierX1);
    }

    public double getBezierY1() {
        return bezierY1;
    }

    public void setBezierY1(double bezierY1) {
        setBezierValue("y1", bezierY1);
    }

    public double getBezierX2() {
        return bezierX2;
    }

    public void setBezierX2(double bezierX2) {
        setBezierValue("x2", bezierX2);
    }

    public double getBezierY2() {
        return bezierY2;
    }

    public void setBezierY2(double bezierY2) {
        setBezierValue("y2", bezierY2);
    }

    private void setBezierValue(String key, double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        switch (key) {
            case "x1" -> bezierX1 = clamp01(value);
            case "y1" -> bezierY1 = value;
            case "x2" -> bezierX2 = clamp01(value);
            case "y2" -> bezierY2 = value;
            default -> {
                return;
            }
        }
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("curveType", curveType.name());
        state.put("clampInput", clampInput);
        state.put("defaultExponent", defaultExponent);
        state.put("gaussianCenter", gaussianCenter);
        state.put("gaussianWidth", gaussianWidth);
        state.put("bezierX1", bezierX1);
        state.put("bezierY1", bezierY1);
        state.put("bezierX2", bezierX2);
        state.put("bezierY2", bezierY2);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("curveType") instanceof String value) {
            setCurveTypeString(value);
        }
        if (map.get("clampInput") instanceof Boolean value) {
            setClampInput(value);
        }
        if (map.get("defaultExponent") instanceof Number value) {
            setDefaultExponent(value.doubleValue());
        }
        if (map.get("gaussianCenter") instanceof Number value) {
            setGaussianCenter(value.doubleValue());
        }
        if (map.get("gaussianWidth") instanceof Number value) {
            setGaussianWidth(value.doubleValue());
        }
        if (map.get("bezierX1") instanceof Number value) {
            setBezierX1(value.doubleValue());
        }
        if (map.get("bezierY1") instanceof Number value) {
            setBezierY1(value.doubleValue());
        }
        if (map.get("bezierX2") instanceof Number value) {
            setBezierX2(value.doubleValue());
        }
        if (map.get("bezierY2") instanceof Number value) {
            setBezierY2(value.doubleValue());
        }
    }
}
