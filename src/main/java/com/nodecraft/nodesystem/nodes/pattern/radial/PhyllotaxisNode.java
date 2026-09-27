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
    id = "pattern.radial.phyllotaxis",
    displayName = "Phyllotaxis",
    description = "Generates golden-angle phyllotaxis anchor points with tangents and placement frames",
    category = "pattern.radial",
    order = 2
)
public class PhyllotaxisNode extends AbstractPatternRadialNode {

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

    @NodeProperty(displayName = "Count", category = "Phyllotaxis", order = 1)
    private int count = 256;

    @NodeProperty(displayName = "Radius Scale", category = "Phyllotaxis", order = 2)
    private double radiusScale = 0.75d;

    @NodeProperty(displayName = "Angle Step", category = "Phyllotaxis", order = 3)
    private double angleStep = DEFAULT_ANGLE_STEP_DEGREES;

    @NodeProperty(displayName = "Start Angle", category = "Phyllotaxis", order = 4)
    private double startAngle = 0.0d;

    @NodeProperty(displayName = "Height Step", category = "Phyllotaxis", order = 5)
    private double heightStep = 0.0d;

    @NodeProperty(displayName = "Radial Exponent", category = "Phyllotaxis", order = 6)
    private double radialExponent = DEFAULT_RADIAL_EXPONENT;

    public PhyllotaxisNode() {
        super(UUID.randomUUID(), "pattern.radial.phyllotaxis");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Distribution origin anchor point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of phyllotaxis anchors", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RADIUS_SCALE_ID, "Radius Scale", "Base radial scale factor (signed/finite)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ANGLE_STEP_ID, "Angle Step", "Angle step in degrees (137.507764 for golden angle)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_STEP_ID, "Height Step", "Per-anchor vertical offset (signed allowed)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIAL_EXPONENT_ID, "Radial Exponent", "Exponent in radius = scale * index^exponent (>= 0; 0 = constant radius)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Phyllotaxis anchor points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Unit tangent at each anchor", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frame at each anchor", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of emitted anchors", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates golden-angle phyllotaxis anchor points with tangents and placement frames";
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

        Double resolvedRadiusScale = OptionalPortDrive.resolveOptionalDouble(this, INPUT_RADIUS_SCALE_ID, radiusScale);
        Double resolvedAngleStep = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ANGLE_STEP_ID, angleStep);
        Double resolvedStartAngle = OptionalPortDrive.resolveOptionalDouble(this, INPUT_START_ANGLE_ID, startAngle);
        Double resolvedHeightStep = OptionalPortDrive.resolveOptionalDouble(this, INPUT_HEIGHT_STEP_ID, heightStep);
        Double resolvedExponent = OptionalPortDrive.resolveOptionalDouble(this, INPUT_RADIAL_EXPONENT_ID, radialExponent);
        if (resolvedRadiusScale == null) {
            writeFail("Radius Scale connected but invalid");
            return;
        }
        if (resolvedAngleStep == null) {
            writeFail("Angle Step connected but invalid");
            return;
        }
        if (resolvedStartAngle == null) {
            writeFail("Start Angle connected but invalid");
            return;
        }
        if (resolvedHeightStep == null) {
            writeFail("Height Step connected but invalid");
            return;
        }
        if (resolvedExponent == null) {
            writeFail("Radial Exponent connected but invalid");
            return;
        }
        if (resolvedExponent < 0.0d) {
            writeFail("Radial Exponent must be >= 0");
            return;
        }

        double angleStepRadians = Math.toRadians(resolvedAngleStep);
        double startAngleRadians = Math.toRadians(resolvedStartAngle);

        List<Vector3d> points = new ArrayList<>(resolvedCount);
        for (int i = 0; i < resolvedCount; i++) {
            double angle = startAngleRadians + angleStepRadians * i;
            double radius = resolvedRadiusScale * Math.pow(i, resolvedExponent);
            if (!Double.isFinite(radius)) {
                writeFail("Non-finite phyllotaxis radius");
                return;
            }
            double cosA = Math.cos(angle);
            double sinA = Math.sin(angle);
            points.add(new Vector3d(origin).add(cosA * radius, resolvedHeightStep * i, sinA * radius));
        }

        List<Vector3d> tangents = new ArrayList<>(resolvedCount);
        List<FrameData> frames = new ArrayList<>(resolvedCount);
        for (int i = 0; i < resolvedCount; i++) {
            Vector3d tangent = tangentFromPoints(points, i);
            if (tangent == null) {
                writeFail("Degenerate phyllotaxis tangent");
                return;
            }
            tangents.add(tangent);
            frames.add(RadialFrameUtils.placementFrame(points.get(i), tangent));
        }

        commitAlignedLayout(
            OUTPUT_POINTS_ID, OUTPUT_TANGENTS_ID, OUTPUT_FRAMES_ID, OUTPUT_COUNT_ID,
            points, tangents, frames
        );
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

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_TANGENTS_ID, OUTPUT_FRAMES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("count", count);
        state.put("radiusScale", radiusScale);
        state.put("angleStep", angleStep);
        state.put("startAngle", startAngle);
        state.put("heightStep", heightStep);
        state.put("radialExponent", radialExponent);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("count") instanceof Number value) {
            count = value.intValue();
        }
        if (map.get("radiusScale") instanceof Number value) {
            radiusScale = value.doubleValue();
        }
        if (map.get("angleStep") instanceof Number value) {
            angleStep = value.doubleValue();
        }
        if (map.get("startAngle") instanceof Number value) {
            startAngle = value.doubleValue();
        }
        if (map.get("heightStep") instanceof Number value) {
            heightStep = value.doubleValue();
        }
        if (map.get("radialExponent") instanceof Number value) {
            radialExponent = value.doubleValue();
        }
    }
}
