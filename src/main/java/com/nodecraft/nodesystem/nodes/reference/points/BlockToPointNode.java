package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.point_from_block",
    displayName = "Block To Point",
    description = "Explicitly converts a block coordinate into a geometric point, with optional block-center offset",
    category = "reference.points",
    order = 1
)
public class BlockToPointNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VALID_ID = "output_valid";

    /** Prefer block center when feeding geometry Center ports (see {@link BlockSpace}). */
    private boolean useBlockCenter = true;

    public BlockToPointNode() {
        super(UUID.randomUUID(), "reference.points.point_from_block");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate",
            "Block coordinate to convert into a geometric point or block-center point",
            NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Converted geometric point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the input coordinate was available", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Block To Point";
    }

    @Override
    public String getDescription() {
        return "Explicitly converts a block coordinate into a geometric point, with optional block-center offset";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        if (!(coordinateObj instanceof BlockPos blockPos)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d vector = useBlockCenter
            ? BlockSpace.cellCenter(blockPos)
            : BlockSpace.cellMinCorner(blockPos);
        double x = vector.x;
        double y = vector.y;
        double z = vector.z;

        PointData point = new PointData(x, y, z);

        outputValues.put(OUTPUT_POINT_ID, point);
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
