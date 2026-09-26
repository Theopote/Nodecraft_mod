package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.RadialFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.radial.spiral",
    displayName = "Spiral",
    description = "Generates spiral anchor points with tangents and placement frames",
    category = "pattern.radial",
    order = 1
)
public class SpiralNode extends BaseNode {

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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SpiralNode() {
        super(UUID.randomUUID(), "pattern.radial.spiral");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Spiral origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TURNS_ID, "Turns", "Number of spiral turns", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of spiral anchors", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_START_RADIUS_ID, "Start Radius", "Initial spiral radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_STEP_ID, "Radius Step", "Radius change per anchor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_STEP_ID, "Height Step", "Vertical step per anchor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle offset in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Spiral anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Unit tangent at each anchor", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frame at each anchor", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchors", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a spiral layout was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates spiral anchor points with tangents and placement frames";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        if (origin == null) {
            origin = new Vector3d(0.0d, 0.0d, 0.0d);
        }
        if (!isFinite(origin)) {
            writeEmpty();
            return;
        }

        int requestedCount = getInputInteger(INPUT_COUNT_ID, 24);
        if (requestedCount <= 0) {
            writeEmpty();
            return;
        }

        double turns = getInputDouble(INPUT_TURNS_ID, 2.0d);
        double startRadius = getInputDouble(INPUT_START_RADIUS_ID, 2.0d);
        double radiusStep = getInputDouble(INPUT_RADIUS_STEP_ID, 0.15d);
        double heightStep = getInputDouble(INPUT_HEIGHT_STEP_ID, 0.25d);
        double startAngleRadians = Math.toRadians(getInputDouble(INPUT_START_ANGLE_ID, 0.0d));
        if (!Double.isFinite(turns)
                || !Double.isFinite(startRadius)
                || !Double.isFinite(radiusStep)
                || !Double.isFinite(heightStep)
                || !Double.isFinite(startAngleRadians)) {
            writeEmpty();
            return;
        }

        int count = GenerationLimits.clampGeometryInstanceCount(requestedCount);
        if (count == 0) {
            writeEmpty();
            return;
        }

        double invSteps = count == 1 ? 1.0d : 1.0d / (count - 1);
        double angleStepPerIndex = turns * Math.PI * 2.0d * invSteps;

        List<Vector3d> points = new ArrayList<>(count);
        List<Vector3d> tangents = new ArrayList<>(count);
        List<FrameData> frames = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.0d : (double) i * invSteps;
            double angle = turns * Math.PI * 2.0d * t + startAngleRadians;
            double radius = startRadius + radiusStep * i;

            double cosA = Math.cos(angle);
            double sinA = Math.sin(angle);
            Vector3d point = new Vector3d(origin).add(cosA * radius, heightStep * i, sinA * radius);
            if (!isFinite(point)) {
                writeEmpty();
                return;
            }

            double dx = -sinA * radius * angleStepPerIndex + cosA * radiusStep;
            double dy = heightStep;
            double dz = cosA * radius * angleStepPerIndex + sinA * radiusStep;
            Vector3d tangent = RadialFrameUtils.normalizeTangent(new Vector3d(dx, dy, dz));
            if (tangent == null) {
                writeEmpty();
                return;
            }

            points.add(point);
            tangents.add(tangent);
            frames.add(RadialFrameUtils.placementFrame(point, tangent));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_TANGENTS_ID, List.copyOf(tangents));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int getInputInteger(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Integer i ? i : fallback;
    }

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TANGENTS_ID, List.of());
        outputValues.put(OUTPUT_FRAMES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
