package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "reference.points.point_from_block",
    displayName = "Block To Point",
    description = "Explicitly converts a block coordinate into a geometric point, with optional block-center offset",
    category = "reference.points",
    order = 1
)
public class BlockToPointNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private boolean useBlockCenter = false;

    public BlockToPointNode() {
        super(UUID.randomUUID(), "reference.points.point_from_block");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate",
            "Block coordinate to convert into a geometric point or block-center point",
            NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Converted geometric point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Converted point as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X",
            "Converted X value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y",
            "Converted Y value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z",
            "Converted Z value", NodeDataType.DOUBLE, this));
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
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_X_ID, 0.0D);
            outputValues.put(OUTPUT_Y_ID, 0.0D);
            outputValues.put(OUTPUT_Z_ID, 0.0D);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double offset = useBlockCenter ? 0.5D : 0.0D;
        double x = blockPos.getX() + offset;
        double y = blockPos.getY() + offset;
        double z = blockPos.getZ() + offset;

        PointData point = new PointData(x, y, z);
        Vector3d vector = new Vector3d(x, y, z);

        outputValues.put(OUTPUT_POINT_ID, point);
        outputValues.put(OUTPUT_VECTOR_ID, vector);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
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
