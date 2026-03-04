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
import java.util.Set;
import java.util.UUID;

/**
 * 效果类型选择器节点，提供搜索型下拉列表选择Minecraft状态效果。
 */
@NodeInfo(
    id = "inputs.selectors.effect_type_selector",
    displayName = "效果类型选择器",
    description = "允许选择Minecraft状态效果类型",
    category = "inputs.selectors"
)
public class EffectTypeSelectorNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "选中效果", category = "选择", order = 1,
                  description = "当前选中的效果ID")
    private String selectedEffect = "minecraft:speed";

    @NodeProperty(displayName = "允许模组效果", category = "过滤", order = 10,
                  description = "是否允许选择模组效果")
    private boolean allowModded = true;
    
    // --- 有益效果列表 ---
    private static final Set<String> BENEFICIAL_EFFECTS = Set.of(
        "speed", "haste", "strength", "instant_health", "jump_boost",
        "regeneration", "resistance", "fire_resistance", "water_breathing",
        "invisibility", "night_vision", "health_boost", "absorption",
        "saturation", "luck", "slow_falling", "conduit_power", "dolphins_grace"
    );
    
    // --- 输出端口 ---
    private static final String OUTPUT_EFFECT_ID = "output_effect_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_EFFECT_PATH = "output_effect_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_IS_BENEFICIAL = "output_is_beneficial";
    private static final String OUTPUT_EFFECT_TYPE = "output_effect_type";
    
    // --- UI状态 ---
    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredEffects = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";
    private static final int MAX_RESULTS = 20;
    
    public EffectTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.effect_type_selector");
        
        addOutputPort(new BasePort(OUTPUT_EFFECT_ID, "Effect ID", "The selected effect's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_EFFECT_PATH, "Effect Path", "The path part", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the effect is from a mod", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_BENEFICIAL, "Is Beneficial", "Whether the effect is beneficial", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EFFECT_TYPE, "Effect Type", "The type (movement, combat, etc.)", NodeDataType.STRING, this));
        
        updateOutputs();
    }
    
    @Override
    public String getDescription() { return "允许搜索和选择Minecraft状态效果"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) { updateOutputs(); }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight(); // 当前效果显示
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 搜索框
        height += getSmallPadding();
        if (showDropdown) {
            height += Math.min(filteredEffects.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += ImGui.getTextLineHeight(); // 效果类型标签
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
                
                // === 当前选中效果显示 ===
                String displayName = selectedEffect.contains(":") ? selectedEffect.split(":", 2)[1] : selectedEffect;
                boolean beneficial = isEffectBeneficial();
                if (beneficial) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.9f, 0.6f, 1.0f);
                    ImGui.text("✦ " + displayName + " (有益)");
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.4f, 0.3f, 1.0f);
                    ImGui.text("✦ " + displayName + " (有害)");
                }
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 搜索框 ===
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(availableWidth / zoom);
                
                if (ImGui.inputTextWithHint("##effect_search", "搜索效果...", searchBuffer)) {
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
                if (showDropdown && !filteredEffects.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    int displayCount = Math.min(filteredEffects.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String effectId = filteredEffects.get(i);
                        String effectPath = effectId.contains(":") ? effectId.split(":", 2)[1] : effectId;
                        boolean isSelected = effectId.equals(selectedEffect);
                        
                        // 有益效果绿色，有害效果红色
                        boolean isBeneficial = BENEFICIAL_EFFECTS.contains(effectPath);
                        if (isSelected) {
                            ImGui.pushStyleColor(ImGuiCol.Text, isBeneficial ? 0.3f : 0.9f, isBeneficial ? 0.9f : 0.4f, isBeneficial ? 0.6f : 0.3f, 1.0f);
                        }
                        
                        String prefix = isBeneficial ? "  ↑ " : "  ↓ ";
                        if (ImGui.selectable(prefix + effectPath + "##" + i, isSelected)) {
                            setSelectedEffect(effectId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }
                        if (isSelected) ImGui.popStyleColor();
                        if (ImGui.isItemHovered()) ImGui.setTooltip(effectId);
                    }
                    if (filteredEffects.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... 还有 " + (filteredEffects.size() - MAX_RESULTS) + " 个结果");
                        ImGui.popStyleColor();
                    }
                    ImGui.popStyleColor();
                }
                
                // === 效果类型标签 ===
                String effectType = calculateEffectType();
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text(selectedEffect + " [" + effectType + "]");
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("EffectTypeSelectorNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void updateFilteredList(String searchText) {
        filteredEffects.clear();
        if (searchText.isEmpty()) return;
        try {
            for (Identifier id : Registries.STATUS_EFFECT.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) continue;
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredEffects.add(fullId);
                    if (filteredEffects.size() >= MAX_RESULTS * 2) break;
                }
            }
        } catch (Exception e) { /* Registry可能还未初始化 */ }
        invalidateCache();
    }
    
    private boolean isEffectBeneficial() {
        String path = selectedEffect.contains(":") ? selectedEffect.split(":", 2)[1] : selectedEffect;
        return BENEFICIAL_EFFECTS.contains(path);
    }
    
    private String calculateEffectType() {
        String path = selectedEffect.contains(":") ? selectedEffect.split(":", 2)[1] : selectedEffect;
        return switch (path) {
            case "speed", "slowness", "jump_boost", "slow_falling", "levitation", "dolphins_grace" -> "movement";
            case "strength", "weakness", "resistance", "instant_damage", "instant_health", "regeneration" -> "combat";
            case "fire_resistance", "water_breathing", "night_vision", "invisibility", "conduit_power" -> "survival";
            case "haste", "mining_fatigue" -> "mining";
            case "poison", "wither", "hunger", "nausea" -> "negative_status";
            default -> "other";
        };
    }
    
    public void setSelectedEffect(String effectId) {
        if (effectId == null || effectId.isEmpty()) effectId = "minecraft:speed";
        if (!this.selectedEffect.equals(effectId)) {
            this.selectedEffect = effectId;
            updateOutputs();
            markDirty();
        }
    }
    
    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "speed";
        if (selectedEffect.contains(":")) {
            String[] parts = selectedEffect.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_EFFECT_ID, selectedEffect);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_EFFECT_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_IS_BENEFICIAL, isEffectBeneficial());
        outputValues.put(OUTPUT_EFFECT_TYPE, calculateEffectType());
    }
    
    public String getSelectedEffect() { return selectedEffect; }
    public boolean isAllowModded() { return allowModded; }
    
    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedEffect.startsWith("minecraft:")) {
            setSelectedEffect("minecraft:speed");
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("selectedEffect", getSelectedEffect());
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
            if (m.containsKey("selectedEffect")) {
                Object v = m.get("selectedEffect");
                if (v instanceof String) setSelectedEffect((String) v);
            }
        }
    }
}