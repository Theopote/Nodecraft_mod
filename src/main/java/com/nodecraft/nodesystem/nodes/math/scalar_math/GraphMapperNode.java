package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.NumericDomainResolver;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.graph_mapper",
    displayName = "Graph Mapper",
    description = "Maps a value through a selectable normalized graph function, similar to Grasshopper's Graph Mapper",
    category = "math.scalar_math",
    order = 22
)
public class GraphMapperNode extends BaseNode {

    private static final double EPS = 1.0e-12d;

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
        description = "Used by Power, Ease, and Exponential mappings")
    private double defaultExponent = 2.0d;

    @NodeProperty(displayName = "Gaussian Center", category = "Graph", order = 4)
    private double gaussianCenter = 0.5d;

    @NodeProperty(displayName = "Gaussian Width", category = "Graph", order = 5)
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
    private static final String INPUT_EXPONENT_ID = "input_exponent";
    private static final String INPUT_GAUSSIAN_CENTER_ID = "input_gaussian_center";
    private static final String INPUT_GAUSSIAN_WIDTH_ID = "input_gaussian_width";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_T_ID = "output_t";
    private static final String OUTPUT_MAPPED_ID = "output_mapped";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GraphMapperNode() {
        super(UUID.randomUUID(), "math.scalar_math.graph_mapper");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value to map", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SOURCE_ID, "Source", "Source domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_TARGET_ID, "Target", "Target domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "Optional exponent or exponential strength", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_GAUSSIAN_CENTER_ID, "Gaussian Center", "Optional Gaussian center in normalized 0..1 space", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_GAUSSIAN_WIDTH_ID, "Gaussian Width", "Optional Gaussian width in normalized 0..1 space", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Mapped value remapped to target domain", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_T_ID, "T", "Normalized input parameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MAPPED_ID, "Mapped 0..1", "Graph function output before output-range remapping", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when mapping succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Graph Mapper";
    }

    @Override
    public String getDescription() {
        return "Maps a value through a selectable normalized graph function, similar to Grasshopper's Graph Mapper";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double value = getInputDouble(INPUT_VALUE_ID, 0.0d);
        NumericRangeData source = NumericDomainResolver.resolveDomain(
            inputValues.get(INPUT_SOURCE_ID), defaultSourceStart, defaultSourceEnd);
        NumericRangeData target = NumericDomainResolver.resolveDomain(
            inputValues.get(INPUT_TARGET_ID), defaultTargetStart, defaultTargetEnd);
        double exponent = getInputDouble(INPUT_EXPONENT_ID, defaultExponent);
        double center = getInputDouble(INPUT_GAUSSIAN_CENTER_ID, gaussianCenter);
        double width = getInputDouble(INPUT_GAUSSIAN_WIDTH_ID, gaussianWidth);

        if (!allFinite(value, source.start(), source.end(), target.start(), target.end(), exponent, center, width)
            || Math.abs(source.delta()) <= EPS) {
            writeInvalid();
            return;
        }

        double t = source.normalizedParameter(value);
        if (clampInput) {
            t = clamp01(t);
        }

        double mapped = evaluate(t, exponent, center, width);
        if (!Double.isFinite(mapped)) {
            writeInvalid();
            return;
        }
        double result = target.lerp(mapped);

        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_T_ID, t);
        outputValues.put(OUTPUT_MAPPED_ID, mapped);
        outputValues.put(OUTPUT_VALID_ID, true);
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
        if (Math.abs(strength) <= EPS) {
            return t;
        }
        double denom = Math.exp(strength) - 1.0d;
        if (Math.abs(denom) <= EPS) {
            return t;
        }
        return (Math.exp(strength * t) - 1.0d) / denom;
    }

    private static double gaussian(double t, double center, double width) {
        double sigma = Math.max(EPS, Math.abs(width));
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
            if (Math.abs(dx) > EPS) {
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

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
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

    private void writeInvalid() {
        outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
        outputValues.put(OUTPUT_T_ID, Double.NaN);
        outputValues.put(OUTPUT_MAPPED_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
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
        double width = Math.max(EPS, Math.abs(gaussianWidth));
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
