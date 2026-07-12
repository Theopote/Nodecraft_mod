package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "pattern.radial.phyllotaxis",
    displayName = "Phyllotaxis",
    description = "Repeats coordinates using sunflower-like golden-angle distribution.",
    category = "pattern.radial",
    order = 2
)
public class PhyllotaxisNode extends BaseNode {

    @NodeProperty(displayName = "Align To Tangent", category = "Orientation", order = 1)
    private boolean alignToTangent = false;

    @NodeProperty(displayName = "Radial Exponent", category = "Distribution", order = 2)
    private double radialExponent = 0.5d;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_RADIUS_SCALE_ID = "input_radius_scale";
    private static final String INPUT_ANGLE_STEP_ID = "input_angle_step";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";
    private static final String INPUT_HEIGHT_STEP_ID = "input_height_step";

    private static final String OUTPUT_COORDINATES_ID = "output_array_coordinates";
    private static final String OUTPUT_ANCHORS_ID = "output_anchors";

    public PhyllotaxisNode() {
        super(UUID.randomUUID(), "pattern.radial.phyllotaxis");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Coordinates to repeat", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Distribution center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of instances", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RADIUS_SCALE_ID, "Radius Scale", "Base radial scale factor", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ANGLE_STEP_ID, "Angle Step", "Angle step in degrees (137.507764 for golden angle)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_STEP_ID, "Height Step", "Per-instance Y offset", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Array Coordinates", "Phyllotaxis pattern coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ANCHORS_ID, "Anchors", "Anchor positions used by each instance", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Repeats coordinates using sunflower-like golden-angle distribution.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsObj = inputValues.get(INPUT_COORDINATES_ID);
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        if (!(coordsObj instanceof BlockPosList source) || !(centerObj instanceof BlockPos center)) {
            outputValues.put(OUTPUT_COORDINATES_ID, new BlockPosList());
            outputValues.put(OUTPUT_ANCHORS_ID, new BlockPosList());
            return;
        }

        int count = GenerationLimits.clampRepeatCount(Math.max(1, getInt(INPUT_COUNT_ID, 256)), source.size());
        double radiusScale = Math.max(0.0d, getDouble(INPUT_RADIUS_SCALE_ID, 0.75d));
        double angleStepRadians = Math.toRadians(getDouble(INPUT_ANGLE_STEP_ID, 137.507764d));
        double startAngleRadians = Math.toRadians(getDouble(INPUT_START_ANGLE_ID, 0.0d));
        double yStep = getDouble(INPUT_HEIGHT_STEP_ID, 0.0d);

        BlockPosList result = new BlockPosList();
        BlockPosList anchors = new BlockPosList();
        for (int i = 0; i < count; i++) {
            double angle = startAngleRadians + angleStepRadians * i;
            double radius = radiusScale * Math.pow(i, Math.max(0.0d, radialExponent));
            double x = center.getX() + Math.cos(angle) * radius;
            double y = center.getY() + yStep * i;
            double z = center.getZ() + Math.sin(angle) * radius;

            BlockPos anchor = new BlockPos((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
            anchors.add(anchor);

            double tangentAngle = angle + Math.PI * 0.5d;
            double cosA = Math.cos(tangentAngle);
            double sinA = Math.sin(tangentAngle);
            for (BlockPos sourcePos : source) {
                double localX = sourcePos.getX();
                double localY = sourcePos.getY();
                double localZ = sourcePos.getZ();
                if (alignToTangent) {
                    double rotatedX = localX * cosA - localZ * sinA;
                    double rotatedZ = localX * sinA + localZ * cosA;
                    localX = rotatedX;
                    localZ = rotatedZ;
                }
                result.add(new BlockPos(
                    (int) Math.round(anchor.getX() + localX),
                    (int) Math.round(localY + anchor.getY()),
                    (int) Math.round(anchor.getZ() + localZ)
                ));
            }
        }

        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_ANCHORS_ID, anchors);
    }

    private double getDouble(String portId, double fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private int getInt(String portId, int fallback) {
        Object v = inputValues.get(portId);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}

