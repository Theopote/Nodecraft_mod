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
 * 物品类型选择器节点，提供搜索型下拉列表选择Minecraft物品。
 */
@NodeInfo(
    id = "inputs.selectors.item_type_selector",
    displayName = "物品类型选择器",
    description = "允许选择Minecraft物品类型",
    category = "inputs.selectors"
)
public class ItemTypeSelectorNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "选中物品", category = "选择", order = 1,
                  description = "当前选中的物品ID")
    private String selectedItem = "minecraft:diamond";

    @NodeProperty(displayName = "允许模组物品", category = "过滤", order = 10,
                  description = "是否允许选择模组物品")
    private boolean allowModded = true;
    
    // --- 输出端口 ---
    private static final String OUTPUT_ITEM_ID = "output_item_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_ITEM_PATH = "output_item_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_CATEGORY = "output_category";
    
    // --- UI状态 ---
    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredItems = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";
    private static final int MAX_RESULTS = 20;
    
    public ItemTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.item_type_selector");
        
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item ID", "The selected item's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_PATH, "Item Path", "The path part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the item is from a mod", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CATEGORY, "Category", "The item's category", NodeDataType.STRING, this));
        
        updateOutputs();
    }
    
    @Override
    public String getDescription() { return "允许搜索和选择Minecraft物品类型"; }
    
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
            height += Math.min(filteredItems.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
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
                
                // === 当前选中物品显示 ===
                String displayName = selectedItem.contains(":") ? selectedItem.split(":", 2)[1] : selectedItem;
                ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.7f, 0.9f, 1.0f);
                ImGui.text("◆ " + displayName);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索框 ===
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(availableWidth / zoom);
                
                if (ImGui.inputTextWithHint("##item_search", "搜索物品...", searchBuffer)) {
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
                if (showDropdown && !filteredItems.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    int displayCount = Math.min(filteredItems.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String itemId = filteredItems.get(i);
                        String itemPath = itemId.contains(":") ? itemId.split(":", 2)[1] : itemId;
                        boolean isSelected = itemId.equals(selectedItem);
                        if (isSelected) ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.8f, 1.0f, 1.0f);
                        
                        if (ImGui.selectable("  " + itemPath + "##" + i, isSelected)) {
                            setSelectedItem(itemId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }
                        if (isSelected) ImGui.popStyleColor();
                        if (ImGui.isItemHovered()) ImGui.setTooltip(itemId);
                    }
                    if (filteredItems.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... 还有 " + (filteredItems.size() - MAX_RESULTS) + " 个结果");
                        ImGui.popStyleColor();
                    }
                    ImGui.popStyleColor();
                }
                
                // === 完整ID显示 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text(selectedItem);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("ItemTypeSelectorNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void updateFilteredList(String searchText) {
        filteredItems.clear();
        if (searchText.isEmpty()) return;
        try {
            for (Identifier id : Registries.ITEM.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) continue;
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredItems.add(fullId);
                    if (filteredItems.size() >= MAX_RESULTS * 2) break;
                }
            }
        } catch (Exception e) { /* Registry可能还未初始化 */ }
        invalidateCache();
    }
    
    private String calculateItemCategory() {
        String path = selectedItem.contains(":") ? selectedItem.split(":", 2)[1] : selectedItem;
        if (path.contains("sword") || path.contains("bow") || path.contains("arrow") || path.equals("trident"))
            return "weapon";
        if (path.contains("pickaxe") || path.contains("shovel") || path.contains("hoe") || path.equals("shears"))
            return "tool";
        if (path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots"))
            return "armor";
        if (path.contains("apple") || path.contains("bread") || path.contains("beef") || path.contains("porkchop") || path.contains("stew"))
            return "food";
        if (path.contains("ore") || path.equals("diamond") || path.equals("gold_ingot") || path.equals("iron_ingot"))
            return "resource";
        return "misc";
    }
    
    public void setSelectedItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) itemId = "minecraft:diamond";
        if (!this.selectedItem.equals(itemId)) {
            this.selectedItem = itemId;
            updateOutputs();
            markDirty();
        }
    }
    
    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "diamond";
        if (selectedItem.contains(":")) {
            String[] parts = selectedItem.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_ITEM_ID, selectedItem);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_ITEM_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_CATEGORY, calculateItemCategory());
    }
    
    public String getSelectedItem() { return selectedItem; }
    public boolean isAllowModded() { return allowModded; }
    
    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedItem.startsWith("minecraft:")) {
            setSelectedItem("minecraft:diamond");
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("selectedItem", getSelectedItem());
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
            if (m.containsKey("selectedItem")) {
                Object v = m.get("selectedItem");
                if (v instanceof String) setSelectedItem((String) v);
            }
        }
    }
}