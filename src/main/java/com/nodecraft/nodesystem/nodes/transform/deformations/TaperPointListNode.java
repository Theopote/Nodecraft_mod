package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.taper",
    displayName = "Taper Point List",
    description = "Scales radial distance along an axis to create tapered forms",
    category = "transform.deformations",
    order = 2
)
public class TaperPointListNode extends AbstractDeformationNode {

    public enum ClampMode {CLAMP, REPEAT, UNBOUNDED}

    @NodeProperty(displayName = "Start Scale", category = "Taper", order = 1)
    private double startScale = 1.0d;

    @NodeProperty(displayName = "End Scale", category = "Taper", order = 2)
    private double endScale = 0.35d;

    @NodeProperty(displayName = "Taper Length", category = "Taper", order = 3)
    private double taperLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Taper", order = 4)
    private ClampMode clampMode = ClampMode.CLAMP;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_START_SCALE_ID = "input_start_scale";
    private static final String INPUT_END_SCALE_ID = "input_end_scale";
    private static final String INPUT_TAPER_LENGTH_ID = "input_taper_length";

    public TaperPointListNode() {
        super("transform.deformations.taper");
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to taper", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Origin point of the taper axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Direction vector of the taper axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_START_SCALE_ID, "Start Scale", "Optional start scale override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_SCALE_ID, "End Scale", "Optional end scale override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TAPER_LENGTH_ID, "Taper Length", "Optional taper length override", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Tapered point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of points in the tapered output", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Scales radial distance along an axis to create tapered forms";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = PointUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID), GenerationLimits.MAX_LIST_ELEMENTS);
        Vector3d axisOrigin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_AXIS_ORIGIN_ID, new Vector3d());
        Vector3d axisDirection = SpatialValueResolver.resolveVector(inputValues.get(INPUT_AXIS_DIRECTION_ID));
        Double resolvedStartScale = OptionalPortDrive.resolveOptionalDouble(this, INPUT_START_SCALE_ID, startScale);
        Double resolvedEndScale = OptionalPortDrive.resolveOptionalDouble(this, INPUT_END_SCALE_ID, endScale);
        Double resolvedLength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_TAPER_LENGTH_ID, taperLength);

        if (pointsInput == null) {
            failPointList("Invalid or oversized point list");
            return;
        }
        if (axisOrigin == null) {
            failPointList("Invalid axis origin");
            return;
        }
        if (!VectorUtils.isNonZero(axisDirection)) {
            failPointList("Axis direction must be non-zero");
            return;
        }
        if (resolvedStartScale == null || resolvedEndScale == null) {
            failPointList("Invalid scale");
            return;
        }
        if (resolvedLength == null || resolvedLength <= 0.0d) {
            failPointList("Taper length must be positive");
            return;
        }
        if (resolvedStartScale < 0.0d || resolvedEndScale < 0.0d) {
            failPointList("Scale must be non-negative");
            return;
        }

        Vector3d axis = new Vector3d(axisDirection).normalize();

        List<Vector3d> taperedPoints = new ArrayList<>(pointsInput.size());
        for (Vector3d point : pointsInput) {
            Vector3d offset = new Vector3d(point).sub(axisOrigin);
            double axialDistance = offset.dot(axis);
            Vector3d axialComponent = new Vector3d(axis).mul(axialDistance);
            Vector3d radialComponent = new Vector3d(offset).sub(axialComponent);

            double normalizedDistance = axialDistance / resolvedLength;
            double taperFactor = applyClampMode(normalizedDistance);
            double scale = resolvedStartScale + (resolvedEndScale - resolvedStartScale) * taperFactor;

            taperedPoints.add(new Vector3d(axisOrigin).add(axialComponent).add(radialComponent.mul(scale)));
        }

        commitPointList(taperedPoints);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("startScale", startScale);
        state.put("endScale", endScale);
        state.put("taperLength", taperLength);
        state.put("clampMode", clampMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("startScale") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0.0d) {
            startScale = value.doubleValue();
        }
        if (map.get("endScale") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0.0d) {
            endScale = value.doubleValue();
        }
        if (map.get("taperLength") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0d) {
            taperLength = value.doubleValue();
        }
        if (map.get("clampMode") instanceof String value) {
            try {
                clampMode = ClampMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                clampMode = ClampMode.CLAMP;
            }
        }
    }

    private double applyClampMode(double normalizedDistance) {
        return switch (clampMode) {
            case CLAMP -> Math.max(0.0d, Math.min(1.0d, normalizedDistance));
            case REPEAT -> normalizedDistance - Math.floor(normalizedDistance);
            case UNBOUNDED -> normalizedDistance;
        };
    }
}
