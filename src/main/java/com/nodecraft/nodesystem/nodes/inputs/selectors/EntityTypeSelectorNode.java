package com.nodecraft.nodesystem.nodes.inputs.selectors;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImString;
import imgui.flag.ImGuiCol;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 实体类型选择器节点，提供搜索型下拉列表选择Minecraft实体。
 */
@NodeInfo(
    id = "inputs.selectors.entity_type_selector",
    displayName = "实体类型选择器",
    description = "允许选择Minecraft实体类型",
    category = "inputs.selectors"
)
public class EntityTypeSelectorNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "选中实体", category = "选择", order = 1,
                  description = "当前选中的实体ID")
    private String selectedEntity = "minecraft:pig";

    @NodeProperty(displayName = "允许模组实体", category = "过滤", order = 10,
                  description = "是否允许选择模组实体")
    private boolean allowModded = true;
    
    // --- 输出端口 ---
    private static final String OUTPUT_ENTITY_ID = "output_entity_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_ENTITY_PATH = "output_entity_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_CATEGORY = "output_category";
    
    // --- UI状态 ---
    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredEntities = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";
    private static final int MAX_RESULTS = 20;
    
    public EntityTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.entity_type_selector");
        
        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity ID", "The selected entity's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_PATH, "Entity Path", "The path part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the entity is from a mod", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CATEGORY, "Category", "The entity's category", NodeDataType.STRING, this));
        
        updateOutputs();
    }
    
    @Override
    public String getDescription() { return "允许搜索和选择Minecraft实体类型"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) { updateOutputs(); }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        if (showDropdown) {
            height += Math.min(filteredEntities.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += ImGui.getTextLineHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 200f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 当前选中实体显示 ===
                String displayName = selectedEntity.contains(":") ? selectedEntity.split(":", 2)[1] : selectedEntity;
                ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.6f, 0.3f, 1.0f);
                ImGui.text("▸ " + displayName);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索框 ===
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(availableWidth / zoom);
                
                if (ImGui.inputTextWithHint("##entity_search", "搜索实体...", searchBuffer)) {
                    String searchText = searchBuffer.get().trim().toLowerCase();
                    if (!searchText.equals(lastSearchText)) {
                        lastSearchText = searchText;
                        updateFilteredList(searchText);
                        showDropdown = !searchText.isEmpty();
                    }
                }
                if (ImGui.isItemActivated() && !searchBuffer.get().isEmpty()) {
                    showDropdown = true;
                }
                
                l.popItemWidth();
                l.popStyleVar();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索结果下拉列表 ===
                if (showDropdown && !filteredEntities.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    int displayCount = Math.min(filteredEntities.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String entityId = filteredEntities.get(i);
                        String entityPath = entityId.contains(":") ? entityId.split(":", 2)[1] : entityId;
                        boolean isSelected = entityId.equals(selectedEntity);
                        if (isSelected) ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.7f, 0.3f, 1.0f);
                        
                        if (ImGui.selectable("  " + entityPath + "##" + i, isSelected)) {
                            setSelectedEntity(entityId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }
                        if (isSelected) ImGui.popStyleColor();
                        if (ImGui.isItemHovered()) ImGui.setTooltip(entityId);
                    }
                    if (filteredEntities.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... 还有 " + (filteredEntities.size() - MAX_RESULTS) + " 个结果");
                        ImGui.popStyleColor();
                    }
                    ImGui.popStyleColor();
                }
                
                // === 完整ID显示 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text(selectedEntity);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("EntityTypeSelectorNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void updateFilteredList(String searchText) {
        filteredEntities.clear();
        if (searchText.isEmpty()) return;
        try {
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) continue;
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredEntities.add(fullId);
                    if (filteredEntities.size() >= MAX_RESULTS * 2) break;
                }
            }
        } catch (Exception e) { /* Registry可能还未初始化 */ }
        invalidateCache();
    }
    
    private String calculateEntityCategory() {
        String path = selectedEntity.contains(":") ? selectedEntity.split(":", 2)[1] : selectedEntity;
        return switch (path) {
            case "zombie", "skeleton", "creeper", "spider", "enderman" -> "hostile";
            case "pig", "cow", "sheep", "chicken", "rabbit" -> "passive";
            case "wolf", "bee", "dolphin", "panda", "llama" -> "neutral";
            case "ender_dragon", "wither" -> "boss";
            case "villager", "wandering_trader" -> "npc";
            default -> "unknown";
        };
    }
    
    public void setSelectedEntity(String entityId) {
        if (entityId == null || entityId.isEmpty()) entityId = "minecraft:pig";
        if (!this.selectedEntity.equals(entityId)) {
            this.selectedEntity = entityId;
            updateOutputs();
            markDirty();
        }
    }
    
    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "pig";
        if (selectedEntity.contains(":")) {
            String[] parts = selectedEntity.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_ENTITY_ID, selectedEntity);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_ENTITY_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_CATEGORY, calculateEntityCategory());
    }
    
    public String getSelectedEntity() { return selectedEntity; }
    public boolean isAllowModded() { return allowModded; }
    
    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedEntity.startsWith("minecraft:")) {
            setSelectedEntity("minecraft:pig");
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("selectedEntity", getSelectedEntity());
        state.put("allowModded", isAllowModded());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("allowModded")) {
                Object v = m.get("allowModded");
                if (v instanceof Boolean) setAllowModded((Boolean) v);
            }
            if (m.containsKey("selectedEntity")) {
                Object v = m.get("selectedEntity");
                if (v instanceof String) setSelectedEntity((String) v);
            }
        }
    }
}