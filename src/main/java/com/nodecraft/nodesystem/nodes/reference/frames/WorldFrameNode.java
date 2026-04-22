package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "reference.frames.world_frame",
    displayName = "World Frame",
    description = "Outputs the world coordinate frame origin and axis vectors",
    category = "reference.frames",
    order = 2
)
public class WorldFrameNode extends BaseNode {

    private static final String OUTPUT_ORIGIN_POS_ID = "output_origin_pos";
    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_X_AXIS_ID = "output_x_axis";
    private static final String OUTPUT_Y_AXIS_ID = "output_y_axis";
    private static final String OUTPUT_Z_AXIS_ID = "output_z_axis";
    private static final String OUTPUT_XY_PLANE_ID = "output_xy_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public WorldFrameNode() {
        super(UUID.randomUUID(), "reference.frames.world_frame");
        addOutputPort(new BasePort(OUTPUT_ORIGIN_POS_ID, "Origin Pos", "World origin block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin", "World origin vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "World X axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "World Y axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "World Z axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_XY_PLANE_ID, "XY Plane", "World XY plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Always true", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the world coordinate frame origin and axis vectors";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = new Vector3d(0.0d, 0.0d, 0.0d);
        outputValues.put(OUTPUT_ORIGIN_POS_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_ORIGIN_ID, origin);
        outputValues.put(OUTPUT_X_AXIS_ID, new Vector3d(1.0d, 0.0d, 0.0d));
        outputValues.put(OUTPUT_Y_AXIS_ID, new Vector3d(0.0d, 1.0d, 0.0d));
        outputValues.put(OUTPUT_Z_AXIS_ID, new Vector3d(0.0d, 0.0d, 1.0d));
        outputValues.put(OUTPUT_XY_PLANE_ID, PlaneData.XY_PLANE);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

