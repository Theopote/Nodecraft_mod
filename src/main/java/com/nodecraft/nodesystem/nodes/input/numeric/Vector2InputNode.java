package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.numeric.vector2_input",
    displayName = "2D Vector Input",
    description = "Inputs a 2D vector (X/Y or U/V) and outputs vector + components.",
    category = "input.numeric",
    order = 8
)
public class Vector2InputNode extends BaseNode {

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_UV_ID = "output_uv";

    @NodeProperty(displayName = "X", category = "Value", order = 1)
    private double x = 0.0d;

    @NodeProperty(displayName = "Y", category = "Value", order = 2)
    private double y = 0.0d;

    public Vector2InputNode() {
        super(UUID.randomUUID(), "input.numeric.vector2_input");
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "2D vector as Vector3d(x,y,0)", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X / U component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y / V component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_UV_ID, "UV", "UV pair list [x, y]", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Inputs a 2D vector (X/Y or U/V) and outputs vector + components.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        outputValues.put(OUTPUT_VECTOR_ID, new Vector3d(x, y, 0.0d));
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_UV_ID, List.of(x, y));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("x", x);
        state.put("y", y);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("x") instanceof Number n) {
            x = n.doubleValue();
        }
        if (map.get("y") instanceof Number n) {
            y = n.doubleValue();
        }
    }
}

