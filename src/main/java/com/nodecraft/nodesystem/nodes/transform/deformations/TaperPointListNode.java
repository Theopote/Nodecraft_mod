package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.taper",
    displayName = "Taper Point List",
    description = "Scales radial distance along an axis to create tapered forms",
    category = "transform.deformations",
    order = 2
)
public class TaperPointListNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    public enum ClampMode {CLAMP, REPEAT, UNBOUNDED}

    @NodeProperty(displayName = "Start Scale", category = "Taper", order = 1)
    private double startScale = 1.0d;

    @NodeProperty(displayName = "End Scale", category = "Taper", order = 2)
    private double endScale = 0.35d;

    @NodeProperty(displayName = "Taper Length", category = "Taper", order = 3)
    private double taperLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Taper", order = 4)
    private ClampMode clampMode = ClampMode.CLAMP;

    @NodeProperty(displayName = "Min Scale", category = "Taper", order = 5)
    private double minScale = 0.05d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_START_SCALE_ID = "input_start_scale";
    private static final String INPUT_END_SCALE_ID = "input_end_scale";
    private static final String INPUT_TAPER_LENGTH_ID = "input_taper_length";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TaperPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.taper");
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to taper", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Origin point of the taper axis", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Direction vector of the taper axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_START_SCALE_ID, "Start Scale", "Optional start scale override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_SCALE_ID, "End Scale", "Optional end scale override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TAPER_LENGTH_ID, "Taper Length", "Optional taper length override", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Tapered point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of points in the tapered output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the inputs were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scales radial distance along an axis to create tapered forms";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        Vector3d axisOrigin = resolvePoint(inputValues.get(INPUT_AXIS_ORIGIN_ID));
        if (!(pointsObj instanceof List<?> pointsInput) || !DeformationUtils.isFinite(axisOrigin) || !(inputValues.get(INPUT_AXIS_DIRECTION_ID) instanceof Vector3d axisDirection)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d axis = new Vector3d(axisDirection);
        if (!DeformationUtils.isUsableDirection(axis)) {
            writeEmptyOutputs();
            return;
        }
        axis.normalize();

        double resolvedStartScale = resolveDouble(inputValues.get(INPUT_START_SCALE_ID), startScale);
        double resolvedEndScale = resolveDouble(inputValues.get(INPUT_END_SCALE_ID), endScale);
        double resolvedLength = Math.max(EPSILON, resolveDouble(inputValues.get(INPUT_TAPER_LENGTH_ID), taperLength));
        if (!Double.isFinite(resolvedStartScale) || !Double.isFinite(resolvedEndScale) || !Double.isFinite(resolvedLength)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> taperedPoints = new ArrayList<>(pointsInput.size());
        for (Object entry : pointsInput) {
            Vector3d point = resolvePoint(entry);
            if (point == null) {
                continue;
            }
            Vector3d offset = new Vector3d(point).sub(axisOrigin);
            double axialDistance = offset.dot(axis);
            Vector3d axialComponent = new Vector3d(axis).mul(axialDistance);
            Vector3d radialComponent = new Vector3d(offset).sub(axialComponent);

            double normalizedDistance = axialDistance / resolvedLength;
            double taperFactor = applyClampMode(normalizedDistance);
            double scale = resolvedStartScale + (resolvedEndScale - resolvedStartScale) * taperFactor;
            scale = Math.max(minScale, scale);

            taperedPoints.add(new Vector3d(axisOrigin).add(axialComponent).add(radialComponent.mul(scale)));
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(taperedPoints));
        outputValues.put(OUTPUT_COUNT_ID, taperedPoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("startScale", startScale);
        state.put("endScale", endScale);
        state.put("taperLength", taperLength);
        state.put("clampMode", clampMode.name());
        state.put("minScale", minScale);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        startScale = DeformationUtils.finiteOrCurrent(map.get("startScale"), startScale);
        endScale = DeformationUtils.finiteOrCurrent(map.get("endScale"), endScale);
        taperLength = Math.max(EPSILON, DeformationUtils.finiteOrCurrent(map.get("taperLength"), taperLength));
        minScale = Math.max(0.0d, DeformationUtils.finiteOrCurrent(map.get("minScale"), minScale));
        if (map.get("clampMode") instanceof String value) {
            try {
                clampMode = ClampMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                clampMode = ClampMode.CLAMP;
            }
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double resolveDouble(Object value, double fallback) {
        return DeformationUtils.resolveFiniteDouble(value, fallback);
    }

    private Vector3d resolvePoint(Object value) {
        Vector3d point = DeformationUtils.resolvePoint(value);
        return DeformationUtils.isFinite(point) ? point : null;
    }

    private double applyClampMode(double normalizedDistance) {
        return switch (clampMode) {
            case CLAMP -> Math.max(0.0d, Math.min(1.0d, normalizedDistance));
            case REPEAT -> normalizedDistance - Math.floor(normalizedDistance);
            case UNBOUNDED -> normalizedDistance;
        };
    }
}
