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
 * 声音事件选择器节点，提供搜索型下拉列表选择Minecraft声音事件。
 */
@NodeInfo(
    id = "inputs.selectors.sound_event_selector",
    displayName = "声音事件选择器",
    description = "允许选择Minecraft声音事件",
    category = "inputs.selectors"
)
public class SoundEventSelectorNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "选中声音", category = "选择", order = 1,
                  description = "当前选中的声音事件ID")
    private String selectedSound = "minecraft:entity.player.levelup";

    @NodeProperty(displayName = "允许模组声音", category = "过滤", order = 10,
                  description = "是否允许选择模组声音事件")
    private boolean allowModded = true;
    
    // --- 输出端口 ---
    private static final String OUTPUT_SOUND_ID = "output_sound_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_SOUND_PATH = "output_sound_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_CATEGORY = "output_category";
    
    // --- UI状态 ---
    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredSounds = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";
    private static final int MAX_RESULTS = 20;
    
    public SoundEventSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.sound_event_selector");
        
        addOutputPort(new BasePort(OUTPUT_SOUND_ID, "Sound ID", "The selected sound's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SOUND_PATH, "Sound Path", "The path part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the sound is from a mod", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CATEGORY, "Category", "The sound's category", NodeDataType.STRING, this));
        
        updateOutputs();
    }
    
    @Override
    public String getDescription() { return "允许搜索和选择Minecraft声音事件"; }
    
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
            height += Math.min(filteredSounds.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += ImGui.getTextLineHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 220f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 当前选中声音显示 ===
                String path = selectedSound.contains(":") ? selectedSound.split(":", 2)[1] : selectedSound;
                String category = getSoundCategory();
                ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.5f, 0.9f, 1.0f);
                ImGui.text("♪ " + path);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索框 ===
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(availableWidth / zoom);
                
                if (ImGui.inputTextWithHint("##sound_search", "搜索声音...", searchBuffer)) {
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
                if (showDropdown && !filteredSounds.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    int displayCount = Math.min(filteredSounds.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String soundId = filteredSounds.get(i);
                        String soundPath = soundId.contains(":") ? soundId.split(":", 2)[1] : soundId;
                        boolean isSelected = soundId.equals(selectedSound);
                        if (isSelected) ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.6f, 1.0f, 1.0f);
                        
                        if (ImGui.selectable("  ♪ " + soundPath + "##" + i, isSelected)) {
                            setSelectedSound(soundId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }
                        if (isSelected) ImGui.popStyleColor();
                        if (ImGui.isItemHovered()) ImGui.setTooltip(soundId);
                    }
                    if (filteredSounds.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... 还有 " + (filteredSounds.size() - MAX_RESULTS) + " 个结果");
                        ImGui.popStyleColor();
                    }
                    ImGui.popStyleColor();
                }
                
                // === 类别标签 ===
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text("[" + category + "] " + selectedSound);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("SoundEventSelectorNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void updateFilteredList(String searchText) {
        filteredSounds.clear();
        if (searchText.isEmpty()) return;
        try {
            for (Identifier id : Registries.SOUND_EVENT.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) continue;
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredSounds.add(fullId);
                    if (filteredSounds.size() >= MAX_RESULTS * 2) break;
                }
            }
        } catch (Exception e) { /* Registry可能还未初始化 */ }
        invalidateCache();
    }
    
    private String getSoundCategory() {
        String path = selectedSound.contains(":") ? selectedSound.split(":", 2)[1] : selectedSound;
        if (path.isEmpty()) return "unknown";
        String[] pathParts = path.split("\\.", 2);
        if (pathParts.length > 0) {
            String first = pathParts[0];
            return switch (first) {
                case "block", "blocks" -> "block";
                case "entity", "entities", "mob", "mobs" -> "entity";
                case "item", "items" -> "item";
                case "music" -> "music";
                case "ambient" -> "ambient";
                case "weather" -> "weather";
                case "player" -> "player";
                case "ui" -> "ui";
                default -> first;
            };
        }
        return "unknown";
    }
    
    public void setSelectedSound(String soundId) {
        if (soundId == null || soundId.isEmpty()) soundId = "minecraft:entity.player.levelup";
        if (!this.selectedSound.equals(soundId)) {
            this.selectedSound = soundId;
            updateOutputs();
            markDirty();
        }
    }
    
    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "entity.player.levelup";
        if (selectedSound.contains(":")) {
            String[] parts = selectedSound.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_SOUND_ID, selectedSound);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_SOUND_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_CATEGORY, getSoundCategory());
    }
    
    public String getSelectedSound() { return selectedSound; }
    public boolean isAllowModded() { return allowModded; }
    
    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedSound.startsWith("minecraft:")) {
            setSelectedSound("minecraft:entity.player.levelup");
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("selectedSound", getSelectedSound());
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
            if (m.containsKey("selectedSound")) {
                Object v = m.get("selectedSound");
                if (v instanceof String) setSelectedSound((String) v);
            }
        }
    }
}