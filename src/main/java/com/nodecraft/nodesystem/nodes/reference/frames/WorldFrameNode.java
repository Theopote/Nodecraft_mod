package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.world_frame",
    displayName = "World Frame",
    description = "Outputs the world coordinate frame as FRAME",
    category = "reference.frames",
    order = 2
)
public class WorldFrameNode extends BaseNode {

    private static final String OUTPUT_FRAME_ID = "output_frame";

    public WorldFrameNode() {
        super(UUID.randomUUID(), "reference.frames.world_frame");
        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "World coordinate frame", NodeDataType.FRAME, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the world coordinate frame as FRAME";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d x = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d y = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d z = new Vector3d(0.0d, 0.0d, 1.0d);
        outputValues.put(OUTPUT_FRAME_ID, new FrameData(origin, x, y, z));
    }
}
