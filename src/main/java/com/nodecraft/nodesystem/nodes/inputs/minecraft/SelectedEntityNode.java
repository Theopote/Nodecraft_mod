package com.nodecraft.nodesystem.nodes.inputs.minecraft;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import net.minecraft.util.math.Vec3d;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/**
 * 选定实体节点
 * 用于获取玩家选定的实体信息，并提供视觉反馈
 */
@NodeInfo(
    id = "inputs.minecraft.selected_entity",
    displayName = "选定实体",
    description = "获取玩家选定的实体信息",
    category = "inputs.minecraft"
)
public class SelectedEntityNode extends BaseCustomUINode implements NodeEditorInteractionManager.IEntityPickerCallback {
    
    // --- 节点设置 ---
    private float maxDistance = 100.0f;
    private boolean showHighlight = true;
    
    // --- 核心数据状态 ---
    private String pickedEntityId = null;
    private String pickedEntityType = "minecraft:pig";
    private Coordinate pickedEntityPosition = null;
    private Vec3d pickedEntityExactPosition = null;
    private boolean hasPickedEntity = false;

    // --- UI折叠状态（影响节点高度计算） ---
    private transient boolean entityInfoExpanded = true;
    private transient boolean settingsExpanded = false;
    
    // --- 输出端口 ---
    private static final String OUTPUT_ENTITY_ID = "output_entity_id";
    private static final String OUTPUT_ENTITY_TYPE = "output_entity_type";
    private static final String OUTPUT_ENTITY_POSITION = "output_entity_position";
    private static final String OUTPUT_ENTITY_X = "output_entity_x";
    private static final String OUTPUT_ENTITY_Y = "output_entity_y";
    private static final String OUTPUT_ENTITY_Z = "output_entity_z";
    private static final String OUTPUT_HAS_ENTITY = "output_has_entity";
    
    public SelectedEntityNode() {
        super(UUID.randomUUID(), "inputs.minecraft.selected_entity");
        
        // 创建输出端口
        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity ID", 
                "The unique identifier of the entity", NodeDataType.STRING, this));
        
        addOutputPort(new BasePort(OUTPUT_ENTITY_TYPE, "Entity Type", 
                "The type of the entity", NodeDataType.STRING, this));
        
        addOutputPort(new BasePort(OUTPUT_ENTITY_POSITION, "Entity Position", 
                "The block coordinates of the entity", NodeDataType.COORDINATE, this));
        
        addOutputPort(new BasePort(OUTPUT_ENTITY_X, "Entity X", 
                "The X coordinate of the entity", NodeDataType.INTEGER, this));
        
        addOutputPort(new BasePort(OUTPUT_ENTITY_Y, "Entity Y", 
                "The Y coordinate of the entity", NodeDataType.INTEGER, this));
        
        addOutputPort(new BasePort(OUTPUT_ENTITY_Z, "Entity Z", 
                "The Z coordinate of the entity", NodeDataType.INTEGER, this));
        
        addOutputPort(new BasePort(OUTPUT_HAS_ENTITY, "Has Entity", 
                "Whether a valid entity is selected", NodeDataType.BOOLEAN, this));
        
        resetOutputs();
    }
    
    @Override
    public String getDescription() {
        return "Gets information about the entity selected by the player.";
    }
    
    @Override
    public String getDisplayName() {
        return "Selected Entity";
    }
    
    // === 核心节点逻辑 ===
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }
        
        updateOutputsWithPickedEntity();
    }
    
    private void updateOutputsWithPickedEntity() {
        outputValues.put(OUTPUT_HAS_ENTITY, hasPickedEntity);
        outputValues.put(OUTPUT_ENTITY_ID, pickedEntityId != null ? pickedEntityId : "");
        outputValues.put(OUTPUT_ENTITY_TYPE, pickedEntityType);
        
        if (pickedEntityPosition != null) {
            outputValues.put(OUTPUT_ENTITY_POSITION, pickedEntityPosition);
            outputValues.put(OUTPUT_ENTITY_X, pickedEntityPosition.getX());
            outputValues.put(OUTPUT_ENTITY_Y, pickedEntityPosition.getY());
            outputValues.put(OUTPUT_ENTITY_Z, pickedEntityPosition.getZ());
        } else {
            outputValues.put(OUTPUT_ENTITY_POSITION, new Coordinate(0, 0, 0));
            outputValues.put(OUTPUT_ENTITY_X, 0);
            outputValues.put(OUTPUT_ENTITY_Y, 0);
            outputValues.put(OUTPUT_ENTITY_Z, 0);
        }
    }
    
    private void resetOutputs() {
        outputValues.put(OUTPUT_HAS_ENTITY, false);
        outputValues.put(OUTPUT_ENTITY_ID, "");
        outputValues.put(OUTPUT_ENTITY_TYPE, "minecraft:pig");
        outputValues.put(OUTPUT_ENTITY_POSITION, new Coordinate(0, 0, 0));
        outputValues.put(OUTPUT_ENTITY_X, 0);
        outputValues.put(OUTPUT_ENTITY_Y, 0);
        outputValues.put(OUTPUT_ENTITY_Z, 0);
    }
    
    // === IEntityPickerCallback 实现 ===
    
    @Override
    public void onEntityPicked(String entityId, String entityType, Coordinate position) {
        this.pickedEntityId = entityId;
        this.pickedEntityType = entityType;
        this.pickedEntityPosition = position;
        this.pickedEntityExactPosition = new Vec3d(position.getX(), position.getY(), position.getZ());
        this.hasPickedEntity = true;
        
        // 标记节点为脏，触发重新计算
        markDirty();
        
        // 显示实体选择视觉反馈 - 橙色脉冲表示实体已选中
        if (showHighlight) {
            SelectionVisualFeedback.getInstance().showEntitySelection(
                getId().toString(), 
                pickedEntityExactPosition, 
                SelectionVisualFeedback.EntitySelectionState.SELECTED
            );
        }
        
        System.out.println("节点接收到拾取的实体: " + entityType + " (ID: " + entityId + ") at " + position);
    }
    
    @Override
    public void onInteractionCancelled() {
        System.out.println("实体拾取被取消");
    }
    
    // === 实体管理方法 ===
    
    public void clearPickedEntity() {
        hasPickedEntity = false;
        pickedEntityId = null;
        pickedEntityType = "minecraft:pig";
        pickedEntityPosition = null;
        pickedEntityExactPosition = null;
        
        // 清除选择视觉反馈
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        
        markDirty();
    }
    
    // === BaseCustomUINode 实现 ===
    
    @Override
    protected float calculateUIHeight() {
        float baseHeight = 80f; // 基础高度：按钮 + 间距
        
        // 如果正在拾取，为状态提示增加高度
        NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
        if (interactionManager.isCurrentInteractionNode(getId().toString())) {
            baseHeight += 50f;
        }
        
        // 实体信息区域（可折叠）
        if (hasPickedEntity) {
            baseHeight += 25f; // 折叠标题行
            if (entityInfoExpanded) {
                // 类型/ID/位置 + 清除按钮 + 间距
                baseHeight += 88f;
            }
        } else {
            baseHeight += 20f; // 未选择实体提示
        }

        // 设置区域（可折叠）
        baseHeight += 25f; // 设置标题行
        if (settingsExpanded) {
            // 高亮选项 + 距离文本和滑块 + 间距
            baseHeight += 72f;
        }
        
        baseHeight += getMediumPadding() * 2; // 上下边距
        return baseHeight;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 200f; // 最小宽度
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        boolean changed = false;
        boolean hasUIInteraction = false;

        try {
            float availableWidth = getAvailableWidth(width, zoom);
            
            // 添加顶部间距
            addVerticalSpacing(getMediumPadding(), zoom);

            // 拾取按钮
            NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
            boolean isCurrentlyPicking = interactionManager.isCurrentInteractionNode(getId().toString());
            
            String pickButtonText = isCurrentlyPicking ? "取消拾取" : "拾取实体";
            float buttonHeight = ImGui.getFrameHeight();
            
            if (ImGui.button(pickButtonText + "##pickEntity", availableWidth, buttonHeight)) {
                if (isCurrentlyPicking) {
                    // 取消当前拾取
                    interactionManager.cancelCurrentInteraction();
                } else {
                    // 确保编辑模式已激活
                    if (!interactionManager.isInEditorMode()) {
                        interactionManager.enterEditorMode();
                        System.out.println("激活编辑模式以支持实体拾取");
                    }
                    
                    // 请求实体拾取
                    interactionManager.requestEntityPicking(getId().toString(), this);
                    System.out.println("请求实体拾取 - 请在游戏中左键点击一个实体");
                }
                changed = true;
            }
            
            hasUIInteraction |= ImGui.isItemHovered() || ImGui.isItemActive();

            addVerticalSpacing(getSmallPadding(), zoom);

            // 拾取状态提示
            if (isCurrentlyPicking) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 0.0f, 1.0f); // 黄色
                ImGui.text("等待拾取中...");
                ImGui.text("在游戏中左键点击实体");
                ImGui.popStyleColor();
                addVerticalSpacing(getSmallPadding(), zoom);
            }

            // 当前选中的实体信息（可折叠）
            if (hasPickedEntity) {
                boolean infoExpandedNow = ImGui.collapsingHeader("已选择实体信息##entityInfo", ImGuiTreeNodeFlags.DefaultOpen);
                entityInfoExpanded = syncExpandableUiState(entityInfoExpanded, infoExpandedNow);

                if (infoExpandedNow) {
                    addVerticalSpacing(2, zoom);
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.9f, 1.0f, 1.0f);
                    ImGui.text("实体类型: " + pickedEntityType);
                    ImGui.popStyleColor();

                    if (pickedEntityId != null) {
                        addVerticalSpacing(2, zoom);
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.8f, 1.0f, 1.0f);
                        ImGui.text("实体ID: " + pickedEntityId);
                        ImGui.popStyleColor();
                    }

                    if (pickedEntityPosition != null) {
                        addVerticalSpacing(2, zoom);
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
                        ImGui.text(String.format("位置: %d, %d, %d",
                                pickedEntityPosition.getX(),
                                pickedEntityPosition.getY(),
                                pickedEntityPosition.getZ()));
                        ImGui.popStyleColor();
                    }

                    addVerticalSpacing(getSmallPadding(), zoom);

                    // 清除按钮
                    if (ImGui.button("清除选择##clearEntity", availableWidth, buttonHeight)) {
                        clearPickedEntity();
                        changed = true;
                    }

                    hasUIInteraction |= ImGui.isItemHovered() || ImGui.isItemActive();
                }
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
                ImGui.text("未选择实体");
                ImGui.popStyleColor();
            }

            addVerticalSpacing(getMediumPadding(), zoom);

            // 设置选项（可折叠）
            boolean settingsExpandedNow = ImGui.collapsingHeader("设置##settings");
            settingsExpanded = syncExpandableUiState(settingsExpanded, settingsExpandedNow);

            if (settingsExpandedNow) {
                addVerticalSpacing(getSmallPadding(), zoom);

                if (ImGui.checkbox("显示高亮##showHighlight", showHighlight)) {
                    setShowHighlight(!showHighlight);
                    changed = true;
                }
                hasUIInteraction |= ImGui.isItemHovered() || ImGui.isItemActive();

                // 最大距离设置
                addVerticalSpacing(getSmallPadding(), zoom);
                ImGui.text("最大距离:");
                float[] maxDistanceArray = {maxDistance};
                if (ImGui.sliderFloat("##maxDistance", maxDistanceArray, 10.0f, 200.0f, "%.1f")) {
                    setMaxDistance(maxDistanceArray[0]);
                    changed = true;
                }
                hasUIInteraction |= ImGui.isItemHovered() || ImGui.isItemActive();
            }

            addVerticalSpacing(getMediumPadding(), zoom);

        } catch (Exception e) {
            System.err.println("渲染SelectedEntityNode UI时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return changed || hasUIInteraction;
    }
    
    protected final float getAvailableWidth(float unscaledNodeWidth, float zoom) {
        // 使用父类方法统一将逻辑宽度转为像素后再减去像素边距，确保缩放一致
        // 注意：这里用 getMediumPadding 而非 getContentMargin，通过手动计算像素差
        float totalPx = toPixelsExact(unscaledNodeWidth, zoom);
        float marginPx = toPixels(getMediumPadding() * 2, zoom);
        return Math.max(0, totalPx - marginPx);
    }
    
    // === Getters/Setters ===
    
    public float getMaxDistance() {
        return maxDistance;
    }
    
    public void setMaxDistance(float maxDistance) {
        if (this.maxDistance != maxDistance) {
            this.maxDistance = Math.max(10.0f, Math.min(200.0f, maxDistance));
            markDirty();
        }
    }
    
    public boolean isShowHighlight() {
        return showHighlight;
    }
    
    public void setShowHighlight(boolean showHighlight) {
        if (this.showHighlight != showHighlight) {
            this.showHighlight = showHighlight;
            
            if (!showHighlight) {
                // 清除当前高亮
                SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
            } else if (hasPickedEntity && pickedEntityExactPosition != null) {
                // 重新显示高亮
                SelectionVisualFeedback.getInstance().showEntitySelection(
                    getId().toString(), 
                    pickedEntityExactPosition, 
                    SelectionVisualFeedback.EntitySelectionState.SELECTED
                );
            }

            // 将显示高亮作为可序列化节点状态的一部分纳入脏标记。
            markDirty();
        }
    }
    
    // === 状态序列化 ===
    
    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        
        state.put("maxDistance", maxDistance);
        state.put("showHighlight", showHighlight);
        
        // 保存拾取的实体信息
        if (hasPickedEntity && pickedEntityPosition != null) {
            Map<String, Object> pickedEntity = new HashMap<>();
            pickedEntity.put("entityId", pickedEntityId);
            pickedEntity.put("entityType", pickedEntityType);
            pickedEntity.put("x", pickedEntityPosition.getX());
            pickedEntity.put("y", pickedEntityPosition.getY());
            pickedEntity.put("z", pickedEntityPosition.getZ());
            state.put("pickedEntity", pickedEntity);
        }
        
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map)) {
            return;
        }
        
        Map<?, ?> stateMap = (Map<?, ?>) state;
        
        // 恢复设置
        if (stateMap.containsKey("maxDistance")) {
            Object maxDist = stateMap.get("maxDistance");
            if (maxDist instanceof Number) {
                setMaxDistance(((Number) maxDist).floatValue());
            }
        }
        
        if (stateMap.containsKey("showHighlight")) {
            Object showHigh = stateMap.get("showHighlight");
            if (showHigh instanceof Boolean) {
                setShowHighlight((Boolean) showHigh);
            }
        }
        
        // 恢复拾取的实体信息
        if (stateMap.containsKey("pickedEntity")) {
            Object pickedEntityObj = stateMap.get("pickedEntity");
            if (pickedEntityObj instanceof Map) {
                Map<?, ?> pickedEntityMap = (Map<?, ?>) pickedEntityObj;
                
                String entityId = null;
                String entityType = null;
                Integer x = null, y = null, z = null;
                
                Object entityIdObj = pickedEntityMap.get("entityId");
                if (entityIdObj instanceof String) {
                    entityId = (String) entityIdObj;
                }
                
                Object entityTypeObj = pickedEntityMap.get("entityType");
                if (entityTypeObj instanceof String) {
                    entityType = (String) entityTypeObj;
                }
                
                Object xObj = pickedEntityMap.get("x");
                if (xObj instanceof Integer) {
                    x = (Integer) xObj;
                }
                
                Object yObj = pickedEntityMap.get("y");
                if (yObj instanceof Integer) {
                    y = (Integer) yObj;
                }
                
                Object zObj = pickedEntityMap.get("z");
                if (zObj instanceof Integer) {
                    z = (Integer) zObj;
                }
                
                // 只有当所有必要数据都存在时才恢复
                if (entityType != null && x != null && y != null && z != null) {
                    this.pickedEntityId = entityId;
                    this.pickedEntityType = entityType;
                    this.pickedEntityPosition = new Coordinate(x, y, z);
                    this.pickedEntityExactPosition = new Vec3d(x, y, z);
                    this.hasPickedEntity = true;
                }
            }
        }
        
        markDirty();
    }
    
    // === 节点生命周期管理 ===
    
    public void onNodeRemoved() {
        // 清理选择视觉反馈
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        
        // 如果当前节点正在交互模式中，取消拾取
        NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
        if (interactionManager.isCurrentInteractionNode(getId().toString())) {
            interactionManager.cancelCurrentInteraction();
        }
    }
    
    public void onNodeSelected() {
        // 如果有拾取的实体且启用了高亮，显示高亮
        if (hasPickedEntity && showHighlight && pickedEntityExactPosition != null) {
            SelectionVisualFeedback.getInstance().showEntitySelection(
                getId().toString(), 
                pickedEntityExactPosition, 
                SelectionVisualFeedback.EntitySelectionState.SELECTED
            );
        }
    }
    
    public void onNodeDeselected() {
        // 保持高亮显示，因为这是节点功能的一部分
    }
} 