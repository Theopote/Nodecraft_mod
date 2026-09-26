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
    id = "pattern.radial.phyllotaxis",
    displayName = "Phyllotaxis",
    description = "Generates golden-angle phyllotaxis anchor points with tangents and placement frames",
    category = "pattern.radial",
    order = 2
)
public class PhyllotaxisNode extends BaseNode {

    private static final double DEFAULT_ANGLE_STEP_DEGREES = 137.507764d;
    private static final double DEFAULT_RADIAL_EXPONENT = 0.5d;

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_RADIUS_SCALE_ID = "input_radius_scale";
    private static final String INPUT_ANGLE_STEP_ID = "input_angle_step";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";
    private static final String INPUT_HEIGHT_STEP_ID = "input_height_step";
    private static final String INPUT_RADIAL_EXPONENT_ID = "input_radial_exponent";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_TANGENTS_ID = "output_tangents";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PhyllotaxisNode() {
        super(UUID.randomUUID(), "pattern.radial.phyllotaxis");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Distribution origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of phyllotaxis anchors", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RADIUS_SCALE_ID, "Radius Scale", "Base radial scale factor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ANGLE_STEP_ID, "Angle Step", "Angle step in degrees (137.507764 for golden angle)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_STEP_ID, "Height Step", "Per-anchor vertical offset", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIAL_EXPONENT_ID, "Radial Exponent", "Exponent in radius = scale * index^exponent", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Phyllotaxis anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Unit tangent at each anchor", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frame at each anchor", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchors", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a phyllotaxis layout was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates golden-angle phyllotaxis anchor points with tangents and placement frames";
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

        int requestedCount = getInputInteger(INPUT_COUNT_ID, 256);
        if (requestedCount <= 0) {
            writeEmpty();
            return;
        }

        double radiusScale = getInputDouble(INPUT_RADIUS_SCALE_ID, 0.75d);
        double angleStepRadians = Math.toRadians(getInputDouble(INPUT_ANGLE_STEP_ID, DEFAULT_ANGLE_STEP_DEGREES));
        double startAngleRadians = Math.toRadians(getInputDouble(INPUT_START_ANGLE_ID, 0.0d));
        double heightStep = getInputDouble(INPUT_HEIGHT_STEP_ID, 0.0d);
        double radialExponent = getInputDouble(INPUT_RADIAL_EXPONENT_ID, DEFAULT_RADIAL_EXPONENT);
        if (!Double.isFinite(radiusScale)
                || !Double.isFinite(angleStepRadians)
                || !Double.isFinite(startAngleRadians)
                || !Double.isFinite(heightStep)
                || !Double.isFinite(radialExponent)
                || radialExponent < 0.0d) {
            writeEmpty();
            return;
        }

        int count = GenerationLimits.clampLayoutInstanceCount(requestedCount);
        if (count == 0) {
            writeEmpty();
            return;
        }

        List<Vector3d> points = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double angle = startAngleRadians + angleStepRadians * i;
            double radius = radiusScale * Math.pow(i, radialExponent);
            if (!Double.isFinite(radius)) {
                writeEmpty();
                return;
            }

            double cosA = Math.cos(angle);
            double sinA = Math.sin(angle);
            Vector3d point = new Vector3d(origin).add(cosA * radius, heightStep * i, sinA * radius);
            if (!isFinite(point)) {
                writeEmpty();
                return;
            }
            points.add(point);
        }

        List<Vector3d> tangents = new ArrayList<>(count);
        List<FrameData> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vector3d tangent = tangentFromPoints(points, i);
            if (tangent == null) {
                writeEmpty();
                return;
            }
            tangents.add(tangent);
            frames.add(RadialFrameUtils.placementFrame(points.get(i), tangent));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_TANGENTS_ID, List.copyOf(tangents));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static @Nullable Vector3d tangentFromPoints(List<Vector3d> points, int index) {
        if (points.size() == 1) {
            return RadialFrameUtils.normalizeTangent(new Vector3d(1.0d, 0.0d, 0.0d));
        }
        Vector3d delta;
        if (index == 0) {
            delta = new Vector3d(points.get(1)).sub(points.get(0));
        } else if (index == points.size() - 1) {
            delta = new Vector3d(points.get(index)).sub(points.get(index - 1));
        } else {
            delta = new Vector3d(points.get(index + 1)).sub(points.get(index - 1));
        }
        return RadialFrameUtils.normalizeTangent(delta);
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
