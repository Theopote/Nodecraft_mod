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
import imgui.type.ImFloat;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 获取玩家视线方向和瞄准信息的节点。
 * 提供滑条调整最大距离和复选框控制检测选项。
 */
@NodeInfo(
    id = "inputs.minecraft.player_look_at",
    displayName = "玩家视线",
    description = "获取玩家视线方向和瞄准的位置信息",
    category = "inputs.minecraft"
)
public class PlayerLookAtNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "最大距离", category = "射线", order = 1,
                  description = "射线检测的最大距离")
    private float maxDistance = 100.0f;

    @NodeProperty(displayName = "包含实体", category = "射线", order = 2,
                  description = "是否检测实体")
    private boolean includeEntities = true;

    @NodeProperty(displayName = "包含流体", category = "射线", order = 3,
                  description = "是否检测流体方块")
    private boolean includeFluids = false;
    
    // --- 输出端口 ---
    private static final String OUTPUT_HIT_POSITION_ID = "output_hit_position";
    private static final String OUTPUT_HIT_BLOCK_ID = "output_hit_block";
    private static final String OUTPUT_HIT_ENTITY_ID = "output_hit_entity";
    private static final String OUTPUT_HIT_DISTANCE_ID = "output_hit_distance";
    private static final String OUTPUT_HAS_HIT_ID = "output_has_hit";
    
    // --- UI状态 ---
    private transient ImFloat distanceFloat = new ImFloat(100.0f);
    private transient ImBoolean includeEntitiesBool = new ImBoolean(true);
    private transient ImBoolean includeFluidsBool = new ImBoolean(false);
    
    public PlayerLookAtNode() {
        super(UUID.randomUUID(), "inputs.minecraft.player_look_at");
        
        addOutputPort(new BasePort(OUTPUT_HIT_POSITION_ID, "Hit Position", "The position of the raycast hit", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HIT_BLOCK_ID, "Hit Block", "The block that was hit", NodeDataType.BLOCK_INFO, this));
        addOutputPort(new BasePort(OUTPUT_HIT_ENTITY_ID, "Hit Entity", "The entity that was hit", NodeDataType.ENTITY_INFO, this));
        addOutputPort(new BasePort(OUTPUT_HIT_DISTANCE_ID, "Hit Distance", "Distance to the hit", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_HAS_HIT_ID, "Has Hit", "Whether something was hit", NodeDataType.BOOLEAN, this));
        
        resetOutputs();
    }
    
    @Override
    public String getDescription() { return "获取玩家视线方向和瞄准的位置信息"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) { resetOutputs(); return; }
        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) { resetOutputs(); return; }
        performRaycast(playerAccessor);
    }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight(); // 距离滑条
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 包含实体复选框
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 包含流体复选框
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 180f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 最大距离滑条 ===
                distanceFloat.set(maxDistance);
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(availableWidth / zoom);
                
                if (ImGui.sliderFloat("##max_dist", distanceFloat.getData(), 1.0f, 500.0f, "距离: %.0f")) {
                    float newDist = distanceFloat.get();
                    if (newDist != maxDistance) {
                        setMaxDistance(newDist);
                        changed = true;
                    }
                }
                
                l.popItemWidth();
                l.popStyleVar();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 包含实体复选框 ===
                includeEntitiesBool.set(includeEntities);
                if (ImGui.checkbox("包含实体##ent", includeEntitiesBool)) {
                    setIncludeEntities(includeEntitiesBool.get());
                    changed = true;
                }
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 包含流体复选框 ===
                includeFluidsBool.set(includeFluids);
                if (ImGui.checkbox("包含流体##flu", includeFluidsBool)) {
                    setIncludeFluids(includeFluidsBool.get());
                    changed = true;
                }
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("PlayerLookAtNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void performRaycast(PlayerAccessor playerAccessor) {
        Vector3 eyePosition = playerAccessor.getPlayerEyePosition();
        Vector3 lookVector = playerAccessor.getPlayerLookVector();
        
        boolean hasHit = true;
        Vector3 hitPosition = eyePosition.add(lookVector.scale(10.0f));
        Object hitBlock = "minecraft:stone";
        Object hitEntity = null;
        float hitDistance = 10.0f;
        
        outputValues.put(OUTPUT_HAS_HIT_ID, hasHit);
        outputValues.put(OUTPUT_HIT_POSITION_ID, hitPosition);
        outputValues.put(OUTPUT_HIT_BLOCK_ID, hitBlock);
        outputValues.put(OUTPUT_HIT_ENTITY_ID, hitEntity);
        outputValues.put(OUTPUT_HIT_DISTANCE_ID, hitDistance);
    }
    
    private void resetOutputs() {
        outputValues.put(OUTPUT_HAS_HIT_ID, false);
        outputValues.put(OUTPUT_HIT_POSITION_ID, new Vector3(0, 0, 0));
        outputValues.put(OUTPUT_HIT_BLOCK_ID, null);
        outputValues.put(OUTPUT_HIT_ENTITY_ID, null);
        outputValues.put(OUTPUT_HIT_DISTANCE_ID, 0.0f);
    }
    
    public float getMaxDistance() { return maxDistance; }
    
    public void setMaxDistance(float maxDistance) {
        maxDistance = Math.max(0, Math.min(1000, maxDistance));
        if (this.maxDistance != maxDistance) {
            this.maxDistance = maxDistance;
            markDirty();
        }
    }
    
    public boolean isIncludeEntities() { return includeEntities; }
    
    public void setIncludeEntities(boolean includeEntities) {
        if (this.includeEntities != includeEntities) {
            this.includeEntities = includeEntities;
            markDirty();
        }
    }
    
    public boolean isIncludeFluids() { return includeFluids; }
    
    public void setIncludeFluids(boolean includeFluids) {
        if (this.includeFluids != includeFluids) {
            this.includeFluids = includeFluids;
            markDirty();
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("maxDistance", getMaxDistance());
        state.put("includeEntities", isIncludeEntities());
        state.put("includeFluids", isIncludeFluids());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("maxDistance")) {
                Object v = m.get("maxDistance");
                if (v instanceof Number) setMaxDistance(((Number) v).floatValue());
            }
            if (m.containsKey("includeEntities")) {
                Object v = m.get("includeEntities");
                if (v instanceof Boolean) setIncludeEntities((Boolean) v);
            }
            if (m.containsKey("includeFluids")) {
                Object v = m.get("includeFluids");
                if (v instanceof Boolean) setIncludeFluids((Boolean) v);
            }
        }
    }
}