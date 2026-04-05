package com.nodecraft.nodesystem.nodes.inputs.minecraft;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import com.nodecraft.nodesystem.util.Vector3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "inputs.minecraft.player_position",
    displayName = "Player Position",
    description = "Gets the player's current world position.",
    category = "inputs.minecraft"
)
public class PlayerPositionNode extends BaseCustomUINode {

    @NodeProperty(
            displayName = "Use Eye Position",
            category = "Position",
            order = 1,
            description = "Use the eye position instead of the feet position."
    )
    private boolean useEyePosition = false;

    private static final String OUTPUT_POSITION_ID = "output_position";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    public PlayerPositionNode() {
        super(UUID.randomUUID(), "inputs.minecraft.player_position");

        addOutputPort(new BasePort(OUTPUT_POSITION_ID, "Position", "The player's position vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X coordinate", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y coordinate", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z coordinate", NodeDataType.FLOAT, this));
    }

    @Override
    public String getDescription() {
        return "Gets the player's current world position.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            updateOutputs(new Vector3(0, 0, 0));
            return;
        }
        updateOutputs(getPlayerPosition(context));
    }

    @Override
    protected float calculateUIHeight() {
        return 0.0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }

    private Vector3 getPlayerPosition(ExecutionContext context) {
        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) {
            return new Vector3(0, 0, 0);
        }
        return useEyePosition ? playerAccessor.getPlayerEyePosition() : playerAccessor.getPlayerPosition();
    }

    private void updateOutputs(Vector3 position) {
        outputValues.put(OUTPUT_POSITION_ID, position);
        outputValues.put(OUTPUT_X_ID, position.getX());
        outputValues.put(OUTPUT_Y_ID, position.getY());
        outputValues.put(OUTPUT_Z_ID, position.getZ());
    }

    public boolean isUseEyePosition() {
        return useEyePosition;
    }

    public void setUseEyePosition(boolean useEyePosition) {
        if (this.useEyePosition != useEyePosition) {
            this.useEyePosition = useEyePosition;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useEyePosition", isUseEyePosition());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map && map.containsKey("useEyePosition")) {
            Object value = map.get("useEyePosition");
            if (value instanceof Boolean bool) {
                setUseEyePosition(bool);
            }
        }
    }
}
