package com.nodecraft.nodesystem.nodes.inputs.selectors;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImString;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 方块类型选择器节点，提供搜索型下拉列表选择Minecraft方块。
 */
@NodeInfo(
    id = "inputs.selectors.block_type_selector",
    displayName = "方块类型选择器",
    description = "允许选择Minecraft方块类型",
    category = "inputs.selectors"
)
public class BlockTypeSelectorNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "选中方块", category = "选择", order = 1,
                  description = "当前选中的方块ID")
    private String selectedBlock = "minecraft:stone";

    @NodeProperty(displayName = "允许模组方块", category = "过滤", order = 10,
                  description = "是否允许选择模组方块")
    private boolean allowModded = true;
    
    // --- 输出端口 ---
    private static final String OUTPUT_BLOCK_ID = "output_block_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_BLOCK_PATH = "output_block_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    
    // --- UI状态 ---
    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredBlocks = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient long lastSearchTime = 0;
    private transient String lastSearchText = "";
    private static final int MAX_RESULTS = 20;
    
    public BlockTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.block_type_selector");
        
        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block ID", "The selected block's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_PATH, "Block Path", "The path part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the block is from a mod", NodeDataType.BOOLEAN, this));
        
        updateOutputs();
    }
    
    @Override
    public String getDescription() { return "允许搜索和选择Minecraft方块类型"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) { updateOutputs(); }
    
    // === BaseCustomUINode 实现 ===

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight(); // "当前:" 标签
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 搜索框
        height += getSmallPadding();
        if (showDropdown) {
            height += Math.min(filteredBlocks.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += ImGui.getTextLineHeight(); // 选中方块显示
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
                float availableWidth = getAvailableWidth(width, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 当前选中方块显示 ===
                String displayName = selectedBlock.contains(":") ? selectedBlock.split(":", 2)[1] : selectedBlock;
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 0.4f, 1.0f);
                ImGui.text("▪ " + displayName);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索框 ===
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(availableWidth / zoom);
                
                if (ImGui.inputTextWithHint("##block_search", "搜索方块...", searchBuffer)) {
                    String searchText = searchBuffer.get().trim().toLowerCase();
                    if (!searchText.equals(lastSearchText)) {
                        lastSearchText = searchText;
                        updateFilteredList(searchText);
                        showDropdown = !searchText.isEmpty();
                    }
                }
                
                // 聚焦时自动打开下拉
                if (ImGui.isItemActivated() && !searchBuffer.get().isEmpty()) {
                    showDropdown = true;
                }
                
                l.popItemWidth();
                l.popStyleVar();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索结果下拉列表 ===
                if (showDropdown && !filteredBlocks.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    
                    int displayCount = Math.min(filteredBlocks.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String blockId = filteredBlocks.get(i);
                        String blockPath = blockId.contains(":") ? blockId.split(":", 2)[1] : blockId;
                        
                        boolean isSelected = blockId.equals(selectedBlock);
                        if (isSelected) {
                            ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.9f, 0.5f, 1.0f);
                        }
                        
                        if (ImGui.selectable("  " + blockPath + "##" + i, isSelected)) {
                            setSelectedBlock(blockId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }
                        
                        if (isSelected) {
                            ImGui.popStyleColor();
                        }
                        
                        // 悬浮时显示完整ID
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(blockId);
                        }
                    }
                    
                    if (filteredBlocks.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... 还有 " + (filteredBlocks.size() - MAX_RESULTS) + " 个结果");
                        ImGui.popStyleColor();
                    }
                    
                    ImGui.popStyleColor();
                }
                
                // === 完整ID显示 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text(selectedBlock);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
                
            } catch (Exception e) {
                System.err.println("BlockTypeSelectorNode UI渲染失败: " + e.getMessage());
            }
            
            return changed;
        });
    }
    
    // === 搜索逻辑 ===
    
    private void updateFilteredList(String searchText) {
        filteredBlocks.clear();
        if (searchText.isEmpty()) return;
        
        try {
            for (Identifier id : Registries.BLOCK.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) continue;
                
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredBlocks.add(fullId);
                    if (filteredBlocks.size() >= MAX_RESULTS * 2) break;
                }
            }
        } catch (Exception e) {
            // Registry可能还未初始化
        }
        
        invalidateCache();
    }
    
    // === 业务逻辑 ===
    
    public void setSelectedBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) blockId = "minecraft:stone";
        if (!this.selectedBlock.equals(blockId)) {
            this.selectedBlock = blockId;
            updateOutputs();
            markDirty();
        }
    }
    
    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "stone";
        if (selectedBlock.contains(":")) {
            String[] parts = selectedBlock.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_BLOCK_ID, selectedBlock);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_BLOCK_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
    }
    
    public String getSelectedBlock() { return selectedBlock; }
    public boolean isAllowModded() { return allowModded; }
    
    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedBlock.startsWith("minecraft:")) {
            setSelectedBlock("minecraft:stone");
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("selectedBlock", getSelectedBlock());
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
            if (m.containsKey("selectedBlock")) {
                Object v = m.get("selectedBlock");
                if (v instanceof String) setSelectedBlock((String) v);
            }
        }
    }

    protected final float getAvailableWidth(float totalWidth, float zoom) {
        return totalWidth - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
    }
}