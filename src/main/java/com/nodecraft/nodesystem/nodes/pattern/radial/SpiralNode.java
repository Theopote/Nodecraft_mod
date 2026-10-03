package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.RadialFrameUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.radial.spiral",
    displayName = "Spiral",
    description = "Generates spiral anchor points with tangents and placement frames",
    category = "pattern.radial",
    order = 1
)
public class SpiralNode extends AbstractPatternRadialNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_TURNS_ID = "input_turns";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_START_RADIUS_ID = "input_start_radius";
    private static final String INPUT_RADIUS_STEP_ID = "input_radius_step";
    private static final String INPUT_HEIGHT_STEP_ID = "input_height_step";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_TANGENTS_ID = "output_tangents";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_COUNT_ID = "output_count";

    @NodeProperty(displayName = "Turns", category = "Spiral", order = 1)
    private double turns = 2.0d;

    @NodeProperty(displayName = "Count", category = "Spiral", order = 2)
    private int count = 24;

    @NodeProperty(displayName = "Start Radius", category = "Spiral", order = 3)
    private double startRadius = 2.0d;

    @NodeProperty(displayName = "Radius Step", category = "Spiral", order = 4)
    private double radiusStep = 0.15d;

    @NodeProperty(displayName = "Height Step", category = "Spiral", order = 5)
    private double heightStep = 0.25d;

    @NodeProperty(displayName = "Start Angle", category = "Spiral", order = 6)
    private double startAngle = 0.0d;

    public SpiralNode() {
        super(UUID.randomUUID(), "pattern.radial.spiral");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Spiral origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TURNS_ID, "Turns", "Number of spiral turns (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of spiral anchors", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_START_RADIUS_ID, "Start Radius", "Initial spiral radius (non-negative magnitude)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_STEP_ID, "Radius Step", "Radius change per anchor (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_STEP_ID, "Height Step", "Vertical step per anchor (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle offset in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Spiral anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Unit tangent at each anchor", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frame at each anchor", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchors", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates spiral anchor points with tangents and placement frames";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = resolveOptionalOrigin(this, INPUT_ORIGIN_ID);
        if (origin == null) {
            writeFail("Origin connected but invalid");
            return;
        }

        Integer resolvedCount = OptionalPortDrive.resolveOptionalInteger(this, INPUT_COUNT_ID, count);
        if (resolvedCount == null) {
            writeFail("Count connected but invalid");
            return;
        }
        if (resolvedCount <= 0) {
            writeFail("Count must be >= 1");
            return;
        }
        if (resolvedCount > GenerationLimits.MAX_LAYOUT_INSTANCES) {
            writeFail("Count exceeds MAX_LAYOUT_INSTANCES");
            return;
        }

        Double resolvedTurns = OptionalPortDrive.resolveOptionalDouble(this, INPUT_TURNS_ID, turns);
        Double resolvedStartRadius = OptionalPortDrive.resolveOptionalDouble(this, INPUT_START_RADIUS_ID, startRadius);
        Double resolvedRadiusStep = OptionalPortDrive.resolveOptionalDouble(this, INPUT_RADIUS_STEP_ID, radiusStep);
        Double resolvedHeightStep = OptionalPortDrive.resolveOptionalDouble(this, INPUT_HEIGHT_STEP_ID, heightStep);
        Double resolvedStartAngle = OptionalPortDrive.resolveOptionalDouble(this, INPUT_START_ANGLE_ID, startAngle);
        if (resolvedTurns == null) {
            writeFail("Turns connected but invalid");
            return;
        }
        if (resolvedStartRadius == null) {
            writeFail("Start Radius connected but invalid");
            return;
        }
        if (resolvedRadiusStep == null) {
            writeFail("Radius Step connected but invalid");
            return;
        }
        if (resolvedHeightStep == null) {
            writeFail("Height Step connected but invalid");
            return;
        }
        if (resolvedStartAngle == null) {
            writeFail("Start Angle connected but invalid");
            return;
        }
        if (!Double.isFinite(resolvedStartRadius) || resolvedStartRadius < 0.0d) {
            writeFail("Start Radius must be >= 0");
            return;
        }
        if (!Double.isFinite(resolvedRadiusStep)) {
            writeFail("Radius Step must be finite");
            return;
        }

        double startAngleRadians = Math.toRadians(resolvedStartAngle);
        double invSteps = resolvedCount == 1 ? 1.0d : 1.0d / (resolvedCount - 1);
        double angleStepPerIndex = resolvedTurns * Math.PI * 2.0d * invSteps;

        List<Vector3d> points = new ArrayList<>(resolvedCount);
        List<Vector3d> tangents = new ArrayList<>(resolvedCount);

        for (int i = 0; i < resolvedCount; i++) {
            double t = resolvedCount == 1 ? 0.0d : (double) i * invSteps;
            double angle = resolvedTurns * Math.PI * 2.0d * t + startAngleRadians;
            double radius = resolvedStartRadius + resolvedRadiusStep * i;
            if (!Double.isFinite(radius) || radius < 0.0d) {
                writeFail("Generated spiral radius must be >= 0");
                return;
            }

            double cosA = Math.cos(angle);
            double sinA = Math.sin(angle);
            Vector3d point = new Vector3d(origin).add(cosA * radius, resolvedHeightStep * i, sinA * radius);

            double dx = -sinA * radius * angleStepPerIndex + cosA * resolvedRadiusStep;
            double dy = resolvedHeightStep;
            double dz = cosA * radius * angleStepPerIndex + sinA * resolvedRadiusStep;
            Vector3d tangent = RadialFrameUtils.normalizeTangent(new Vector3d(dx, dy, dz));
            if (tangent == null) {
                writeFail("Degenerate spiral tangent");
                return;
            }

            points.add(point);
            tangents.add(tangent);
        }

        List<FrameData> frames = RadialFrameUtils.placementFrames(points, tangents);

        commitAlignedLayout(
            OUTPUT_POINTS_ID, OUTPUT_TANGENTS_ID, OUTPUT_FRAMES_ID, OUTPUT_COUNT_ID,
            points, tangents, frames
        );
    }

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_TANGENTS_ID, OUTPUT_FRAMES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("turns", turns);
        state.put("count", count);
        state.put("startRadius", startRadius);
        state.put("radiusStep", radiusStep);
        state.put("heightStep", heightStep);
        state.put("startAngle", startAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("turns") instanceof Number value) {
            turns = value.doubleValue();
        }
        if (map.get("count") instanceof Number value) {
            count = value.intValue();
        }
        if (map.get("startRadius") instanceof Number value) {
            startRadius = value.doubleValue();
        }
        if (map.get("radiusStep") instanceof Number value) {
            radiusStep = value.doubleValue();
        }
        if (map.get("heightStep") instanceof Number value) {
            heightStep = value.doubleValue();
        }
        if (map.get("startAngle") instanceof Number value) {
            startAngle = value.doubleValue();
        }
    }
}
