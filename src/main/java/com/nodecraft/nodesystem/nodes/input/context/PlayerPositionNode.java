package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import com.nodecraft.nodesystem.util.Vector3;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "input.context.player_position",
    displayName = "Player Position",
    description = "Outputs a snapped player world position. Click Update Position to refresh the snapshot.",
    category = "input.context",
    order = 0
)
public class PlayerPositionNode extends BaseCustomUINode {

    @NodeProperty(
            displayName = "Use Eye Position",
            category = "Position",
            order = 1,
            description = "Use the eye position instead of the feet position when updating the snapshot."
    )
    private boolean useEyePosition = false;

    private static final String OUTPUT_POSITION_ID = "output_position";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    private boolean hasCachedPosition = false;
    private double cachedX = 0.0;
    private double cachedY = 0.0;
    private double cachedZ = 0.0;

    @SuppressWarnings("deprecation")
    public PlayerPositionNode() {
        super(UUID.randomUUID(), "input.context.player_position");

        // POSITION is the legacy continuous-location alias: it drives POINT inputs
        // (World Plane Origin) implicitly, and still aliases to VECTOR for existing
        // Move Geometry translation presets. New location sources should prefer POINT.
        addOutputPort(new BasePort(OUTPUT_POSITION_ID, "Position",
                "Snapped player world location", NodeDataType.POSITION, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X coordinate", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y coordinate", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z coordinate", NodeDataType.DOUBLE, this));

        updateOutputs(cachedPosition());
    }

    @Override
    public String getDescription() {
        return "Outputs a snapped player world position. Click Update Position to refresh the snapshot.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!hasCachedPosition) {
            capturePosition(context);
        }
        updateOutputs(cachedPosition());
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float labelWidth = ImGui.calcTextSize("Not set").x;
        float coordsWidth = ImGui.calcTextSize("00000.00, 00000.00, 00000.00").x;
        float buttonWidth = ImGui.calcTextSize("Update Position").x + 24.0f;
        return Math.max(176.0f, Math.max(labelWidth, Math.max(coordsWidth, buttonWidth))) + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float buttonWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            l.addVerticalSpacing(getMediumPadding());

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            if (hasCachedPosition) {
                ImGui.text(String.format("%.2f, %.2f, %.2f", cachedX, cachedY, cachedZ));
            } else {
                ImGui.textDisabled("Not set");
            }

            l.addVerticalSpacing(getSmallPadding());

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            if (ImGui.button("Update Position##updatePosition", buttonWidth, ImGui.getFrameHeight())) {
                if (capturePosition(null)) {
                    changed = true;
                    markDirty();
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(hasCachedPosition
                        ? "Refresh the snapshot from the current player position."
                        : "Capture the current player position into this node.");
            }

            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    /**
     * Samples the current player position into the node snapshot.
     *
     * @param context optional execution context; when null, samples from the client player
     * @return true if a position was captured
     */
    private boolean capturePosition(@Nullable ExecutionContext context) {
        if (context != null) {
            Vector3d fromContext = readPlayerPosition(context);
            if (fromContext != null) {
                setCachedPosition(fromContext.x, fromContext.y, fromContext.z);
                return true;
            }
        }
        return captureFromClientPlayer();
    }

    private boolean captureFromClientPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        PlayerEntity player = client.player;
        double x = player.getX();
        double y = useEyePosition ? player.getEyeY() : player.getY();
        double z = player.getZ();
        setCachedPosition(x, y, z);
        return true;
    }

    private @Nullable Vector3d readPlayerPosition(ExecutionContext context) {
        // DefaultPlayerAccessor returns (0,0,0) when player is missing — do not treat that as a snapshot.
        if (context.getPlayer() == null) {
            return null;
        }
        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) {
            return null;
        }
        Vector3 position = useEyePosition
                ? playerAccessor.getPlayerEyePosition()
                : playerAccessor.getPlayerPosition();
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }

    private void setCachedPosition(double x, double y, double z) {
        this.cachedX = x;
        this.cachedY = y;
        this.cachedZ = z;
        this.hasCachedPosition = true;
        updateOutputs(cachedPosition());
    }

    private Vector3d cachedPosition() {
        return new Vector3d(cachedX, cachedY, cachedZ);
    }

    private void updateOutputs(Vector3d position) {
        outputValues.put(OUTPUT_POSITION_ID, position);
        outputValues.put(OUTPUT_X_ID, position.x);
        outputValues.put(OUTPUT_Y_ID, position.y);
        outputValues.put(OUTPUT_Z_ID, position.z);
    }

    public boolean isUseEyePosition() {
        return useEyePosition;
    }

    public void setUseEyePosition(boolean useEyePosition) {
        if (this.useEyePosition == useEyePosition) {
            return;
        }
        this.useEyePosition = useEyePosition;
        // Re-sample when possible so feet/eye toggle stays in sync with current player.
        capturePosition(null);
        markDirty();
    }

    public boolean hasCachedPosition() {
        return hasCachedPosition;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("useEyePosition", isUseEyePosition());
        state.put("hasCachedPosition", hasCachedPosition);
        state.put("cachedX", cachedX);
        state.put("cachedY", cachedY);
        state.put("cachedZ", cachedZ);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("useEyePosition") instanceof Boolean bool) {
            this.useEyePosition = bool;
        }
        Double x = asDouble(map.get("cachedX"));
        Double y = asDouble(map.get("cachedY"));
        Double z = asDouble(map.get("cachedZ"));
        boolean restored = map.get("hasCachedPosition") instanceof Boolean has && has
                && x != null && y != null && z != null;
        if (restored) {
            setCachedPosition(x, y, z);
        } else if (x != null && y != null && z != null) {
            // Older saves without the flag still restore numeric snapshots.
            setCachedPosition(x, y, z);
        } else {
            hasCachedPosition = false;
            cachedX = 0.0;
            cachedY = 0.0;
            cachedZ = 0.0;
            updateOutputs(cachedPosition());
        }
    }

    private static @Nullable Double asDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }
}
