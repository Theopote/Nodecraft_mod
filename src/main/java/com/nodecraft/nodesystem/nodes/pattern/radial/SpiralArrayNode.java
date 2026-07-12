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
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "pattern.radial.spiral_array",
    displayName = "Spiral Array",
    description = "Repeats coordinates along a spiral path around a center point",
    category = "pattern.radial",
    order = 1
)
public class SpiralArrayNode extends BaseNode {
    @NodeProperty(displayName = "Align To Tangent", category = "Orientation", order = 1)
    private boolean alignToTangent = false;


    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_TURNS_ID = "input_turns";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_START_RADIUS_ID = "input_start_radius";
    private static final String INPUT_RADIUS_STEP_ID = "input_radius_step";
    private static final String INPUT_HEIGHT_STEP_ID = "input_height_step";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";
    private static final String OUTPUT_COORDINATES_ID = "output_array_coordinates";

    public SpiralArrayNode() {
        super(UUID.randomUUID(), "pattern.radial.spiral_array");
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Coordinates to repeat in spiral", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Spiral center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TURNS_ID, "Turns", "Number of spiral turns", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of copies", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_START_RADIUS_ID, "Start Radius", "Initial spiral radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_STEP_ID, "Radius Step", "Radius growth per copy", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_STEP_ID, "Height Step", "Vertical step per copy", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle offset in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Array Coordinates", "Spiral array coordinates", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Repeats coordinates along a spiral path around a center point with optional tangent alignment";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsObj = inputValues.get(INPUT_COORDINATES_ID);
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        if (!(coordsObj instanceof BlockPosList source) || !(centerObj instanceof BlockPos center)) {
            outputValues.put(OUTPUT_COORDINATES_ID, new BlockPosList());
            return;
        }
        int count = GenerationLimits.clampRepeatCount(Math.max(1, getInt(INPUT_COUNT_ID, 24)), source.size());
        double turns = getDouble(INPUT_TURNS_ID, 2.0d);
        double startRadius = getDouble(INPUT_START_RADIUS_ID, 2.0d);
        double radiusStep = getDouble(INPUT_RADIUS_STEP_ID, 0.15d);
        double heightStep = getDouble(INPUT_HEIGHT_STEP_ID, 0.25d);
        double startAngleRadians = Math.toRadians(getDouble(INPUT_START_ANGLE_ID, 0.0d));

        BlockPosList result = new BlockPosList();
        Vector3d centerVec = new Vector3d(center.getX(), center.getY(), center.getZ());
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.0d : (double) i / (count - 1);
            double angle = turns * Math.PI * 2.0d * t + startAngleRadians;
            double radius = startRadius + radiusStep * i;
            Vector3d offset = new Vector3d(
                Math.cos(angle) * radius,
                heightStep * i,
                Math.sin(angle) * radius
            );
            double tangentAngle = angle + Math.PI / 2.0d;
            double cosA = Math.cos(tangentAngle);
            double sinA = Math.sin(tangentAngle);
            for (BlockPos pos : source) {
                double localX = pos.getX();
                double localY = pos.getY();
                double localZ = pos.getZ();
                if (alignToTangent) {
                    double rotatedX = localX * cosA - localZ * sinA;
                    double rotatedZ = localX * sinA + localZ * cosA;
                    localX = rotatedX;
                    localZ = rotatedZ;
                }
                result.add(new BlockPos(
                    (int) Math.round(localX + centerVec.x + offset.x),
                    (int) Math.round(localY + offset.y),
                    (int) Math.round(localZ + centerVec.z + offset.z)
                ));
            }
        }
        outputValues.put(OUTPUT_COORDINATES_ID, result);
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

