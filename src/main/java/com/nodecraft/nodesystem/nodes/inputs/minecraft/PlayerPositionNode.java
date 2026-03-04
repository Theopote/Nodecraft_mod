package com.nodecraft.nodesystem.nodes.inputs.minecraft;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.util.Vector3;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 获取玩家当前位置的节点。
 * 提供切换开关选择脚底位置或眼睛位置。
 */
@NodeInfo(
    id = "inputs.minecraft.player_position",
    displayName = "玩家位置",
    description = "获取玩家当前的位置坐标",
    category = "inputs.minecraft"
)
public class PlayerPositionNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "眼睛位置", category = "位置", order = 1,
                  description = "使用眼睛位置而非脚底位置")
    private boolean useEyePosition = false;
    
    // --- 输出端口 ---
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
    public String getDescription() { return "获取玩家当前的位置坐标"; }
    
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
        float height = getMediumPadding();
        height += ImGui.getFrameHeight(); // 模式标签
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 复选框
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 160f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 位置模式标签 ===
                String modeLabel = useEyePosition ? "👁 眼睛位置" : "🦶 脚底位置";
                int modeColor = useEyePosition ? 0xFF44AAFF : 0xFF44CC88;
                ImGui.pushStyleColor(ImGuiCol.Text, modeColor);
                ImGui.text(modeLabel);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 眼睛位置复选框 ===
                ImBoolean useEyeBool = new ImBoolean(useEyePosition);
                if (ImGui.checkbox("使用眼睛位置##eye", useEyeBool)) {
                    setUseEyePosition(useEyeBool.get());
                    changed = true;
                }
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("PlayerPositionNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private Vector3 getPlayerPosition(ExecutionContext context) {
        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) return new Vector3(0, 0, 0);
        return useEyePosition ? playerAccessor.getPlayerEyePosition() : playerAccessor.getPlayerPosition();
    }
    
    private void updateOutputs(Vector3 position) {
        outputValues.put(OUTPUT_POSITION_ID, position);
        outputValues.put(OUTPUT_X_ID, position.getX());
        outputValues.put(OUTPUT_Y_ID, position.getY());
        outputValues.put(OUTPUT_Z_ID, position.getZ());
    }
    
    public boolean isUseEyePosition() { return useEyePosition; }
    
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
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("useEyePosition")) {
                Object v = m.get("useEyePosition");
                if (v instanceof Boolean) setUseEyePosition((Boolean) v);
            }
        }
    }
}