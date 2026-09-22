package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.block_to_vector",
    displayName = "Block To Vector",
    description = "Explicitly converts a block coordinate into a Vector3d position, with optional block-center offset.",
    category = "reference.points",
    order = 3
)
public class BlockToVectorNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private boolean useBlockCenter = false;

    public BlockToVectorNode() {
        super(UUID.randomUUID(), "reference.points.block_to_vector");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate",
            "Block coordinate to convert into a Vector3d position",
            NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Converted Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether the input coordinate was valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Explicitly converts a block coordinate into a Vector3d position, with optional block-center offset.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos pos = PointUtils.toBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            outputValues.put(OUTPUT_VECTOR_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double offset = useBlockCenter ? 0.5D : 0.0D;
        Vector3d vector = new Vector3d(
            pos.getX() + offset,
            pos.getY() + offset,
            pos.getZ() + offset
        );

        outputValues.put(OUTPUT_VECTOR_ID, vector);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public boolean isUseBlockCenter() {
        return useBlockCenter;
    }

    public void setUseBlockCenter(boolean useBlockCenter) {
        this.useBlockCenter = useBlockCenter;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("useBlockCenter", useBlockCenter);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object useCenter = stateMap.get("useBlockCenter");
            if (useCenter instanceof Boolean enabled) {
                setUseBlockCenter(enabled);
            }
        }
    }
}
