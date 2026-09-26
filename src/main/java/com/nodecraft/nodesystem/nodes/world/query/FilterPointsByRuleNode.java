package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.query.filter_points_by_rule",
    displayName = "Filter Points By Rule",
    description = "Filters point sets by height and optional surface slope rules.",
    category = "world.query",
    order = 8
)
public class FilterPointsByRuleNode extends BaseNode {

    public enum RuleMode {
        ALL,
        ANY
    }

    @NodeProperty(displayName = "Mode", category = "Filter", order = 1)
    private RuleMode ruleMode = RuleMode.ALL;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_NORMALS_ID = "input_normals";
    private static final String INPUT_MIN_HEIGHT_ID = "input_min_height";
    private static final String INPUT_MAX_HEIGHT_ID = "input_max_height";
    private static final String INPUT_MIN_SLOPE_ID = "input_min_slope";
    private static final String INPUT_MAX_SLOPE_ID = "input_max_slope";
    private static final String INPUT_INVERT_ID = "input_invert";

    private static final String OUTPUT_FILTERED_POINTS_ID = "output_filtered_points";
    private static final String OUTPUT_REMOVED_POINTS_ID = "output_removed_points";
    private static final String OUTPUT_MASK_ID = "output_mask";
    private static final String OUTPUT_SLOPES_ID = "output_slopes";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FilterPointsByRuleNode() {
        super(UUID.randomUUID(), "world.query.filter_points_by_rule");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Points to filter", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Optional normals aligned with points for slope tests", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_MIN_HEIGHT_ID, "Min Height", "Minimum Y value to keep", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_HEIGHT_ID, "Max Height", "Maximum Y value to keep", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MIN_SLOPE_ID, "Min Slope", "Minimum slope angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_SLOPE_ID, "Max Slope", "Maximum slope angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INVERT_ID, "Invert", "Invert the final keep mask", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_FILTERED_POINTS_ID, "Filtered Points", "Points that passed the rule", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_POINTS_ID, "Removed Points", "Points rejected by the rule", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_MASK_ID, "Mask", "Boolean keep mask aligned with input points", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLOPES_ID, "Slopes", "Computed slope degrees aligned with input points when normals are available", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of kept points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of resolved input points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether filtering had valid point input", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when filtering fails", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (points == null) {
            writeFailure("Points input must be a non-empty POINT_LIST with finite PointData entries.");
            return;
        }

        OptionalRuleDouble minHeight = resolveOptionalRuleDouble(INPUT_MIN_HEIGHT_ID, "Min Height");
        if (!minHeight.ok()) {
            return;
        }
        OptionalRuleDouble maxHeight = resolveOptionalRuleDouble(INPUT_MAX_HEIGHT_ID, "Max Height");
        if (!maxHeight.ok()) {
            return;
        }
        OptionalRuleDouble minSlope = resolveOptionalRuleDouble(INPUT_MIN_SLOPE_ID, "Min Slope");
        if (!minSlope.ok()) {
            return;
        }
        OptionalRuleDouble maxSlope = resolveOptionalRuleDouble(INPUT_MAX_SLOPE_ID, "Max Slope");
        if (!maxSlope.ok()) {
            return;
        }

        Double minHeightValue = minHeight.value();
        Double maxHeightValue = maxHeight.value();
        Double minSlopeValue = minSlope.value();
        Double maxSlopeValue = maxSlope.value();

        if (minHeightValue != null && maxHeightValue != null && minHeightValue > maxHeightValue) {
            writeFailure("Min Height must be less than or equal to Max Height.");
            return;
        }
        if (minSlopeValue != null && maxSlopeValue != null && minSlopeValue > maxSlopeValue) {
            writeFailure("Min Slope must be less than or equal to Max Slope.");
            return;
        }

        boolean hasSlopeBound = minSlopeValue != null || maxSlopeValue != null;
        List<Vector3d> normals = null;
        boolean normalsPresent = OptionalPortDrive.isConnected(this, INPUT_NORMALS_ID)
                || inputValues.get(INPUT_NORMALS_ID) != null;
        if (normalsPresent) {
            normals = VectorUtils.resolveStrictVectorList(inputValues.get(INPUT_NORMALS_ID));
            if (normals == null || normals.size() != points.size()) {
                writeFailure(OptionalPortDrive.isConnected(this, INPUT_NORMALS_ID)
                        ? "Normals is connected but null or invalid."
                        : "Normals must be a VECTOR_LIST with the same count as Points.");
                return;
            }
            for (Vector3d normal : normals) {
                if (!VectorUtils.isNonZero(normal)) {
                    writeFailure("Normals must be finite non-zero vectors aligned with Points.");
                    return;
                }
            }
        } else if (hasSlopeBound) {
            writeFailure("Normals are required when Min Slope or Max Slope is set.");
            return;
        }

        Boolean invertValue = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_INVERT_ID, false);
        if (invertValue == null) {
            writeFailure("Invert is connected but null or invalid.");
            return;
        }

        boolean anyMode = ruleMode == RuleMode.ANY;
        boolean invert = invertValue;
        boolean hasHeightRule = minHeightValue != null || maxHeightValue != null;
        boolean hasSlopeRule = hasSlopeBound;

        List<Vector3d> kept = new ArrayList<>();
        List<Vector3d> removed = new ArrayList<>();
        List<Boolean> mask = new ArrayList<>(points.size());
        List<Double> slopes = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            Vector3d point = points.get(i);

            boolean heightPass = !hasHeightRule || inRange(point.y, minHeightValue, maxHeightValue);
            Double slope = normals != null ? slopeDegrees(normals.get(i)) : null;
            boolean slopePass = !hasSlopeRule || inRange(slope, minSlopeValue, maxSlopeValue);
            slopes.add(slope);

            boolean keep;
            if (anyMode && (hasHeightRule || hasSlopeRule)) {
                keep = (hasHeightRule && heightPass) || (hasSlopeRule && slopePass);
            } else {
                keep = heightPass && slopePass;
            }
            if (invert) {
                keep = !keep;
            }

            mask.add(keep);
            if (keep) {
                kept.add(new Vector3d(point));
            } else {
                removed.add(new Vector3d(point));
            }
        }

        writeSuccess(kept, removed, mask, slopes);
    }

    /**
     * Optional rule DOUBLE: unconnected+null → disabled;
     * unconnected/local finite or connected finite → enabled;
     * connected null/NaN/Infinity/wrong type → fail closed.
     */
    private OptionalRuleDouble resolveOptionalRuleDouble(String portId, String displayName) {
        boolean connected = OptionalPortDrive.isConnected(this, portId);
        Object raw = inputValues.get(portId);
        if (raw == null) {
            if (connected) {
                writeFailure(displayName + " is connected but null or invalid.");
                return OptionalRuleDouble.invalid();
            }
            return OptionalRuleDouble.disabled();
        }
        if (!(raw instanceof Number number)) {
            if (connected) {
                writeFailure(displayName + " is connected but null or invalid.");
                return OptionalRuleDouble.invalid();
            }
            return OptionalRuleDouble.disabled();
        }
        double resolved = number.doubleValue();
        if (!Double.isFinite(resolved)) {
            writeFailure(displayName + (connected
                    ? " is connected but null or invalid."
                    : " must be a finite number when provided."));
            return OptionalRuleDouble.invalid();
        }
        return OptionalRuleDouble.enabled(resolved);
    }

    private record OptionalRuleDouble(boolean ok, @Nullable Double value) {
        static OptionalRuleDouble disabled() {
            return new OptionalRuleDouble(true, null);
        }

        static OptionalRuleDouble enabled(double value) {
            return new OptionalRuleDouble(true, value);
        }

        static OptionalRuleDouble invalid() {
            return new OptionalRuleDouble(false, null);
        }
    }

    private boolean inRange(double value, @Nullable Double min, @Nullable Double max) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private @Nullable Double slopeDegrees(Vector3d normal) {
        if (normal == null || normal.lengthSquared() <= VectorUtils.EPS_SQ) {
            return null;
        }
        Vector3d unit = new Vector3d(normal).normalize();
        double y = Math.max(-1.0d, Math.min(1.0d, Math.abs(unit.y)));
        return Math.toDegrees(Math.acos(y));
    }

    private void writeSuccess(List<Vector3d> kept, List<Vector3d> removed, List<Boolean> mask, List<Double> slopes) {
        outputValues.put(OUTPUT_FILTERED_POINTS_ID, SpatialValueResolver.toPointDataList(kept));
        outputValues.put(OUTPUT_REMOVED_POINTS_ID, SpatialValueResolver.toPointDataList(removed));
        outputValues.put(OUTPUT_MASK_ID, mask);
        outputValues.put(OUTPUT_SLOPES_ID, slopes);
        outputValues.put(OUTPUT_COUNT_ID, kept.size());
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, kept.size() + removed.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_FILTERED_POINTS_ID, List.of());
        outputValues.put(OUTPUT_REMOVED_POINTS_ID, List.of());
        outputValues.put(OUTPUT_MASK_ID, List.of());
        outputValues.put(OUTPUT_SLOPES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("ruleMode", ruleMode.name().toLowerCase(Locale.ROOT));
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object modeObj = map.get("ruleMode");
        if (modeObj instanceof String text) {
            try {
                ruleMode = RuleMode.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                ruleMode = RuleMode.ALL;
            }
        }
    }
}
