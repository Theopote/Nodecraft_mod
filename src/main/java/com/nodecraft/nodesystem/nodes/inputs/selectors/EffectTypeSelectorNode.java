package com.nodecraft.nodesystem.nodes.inputs.selectors;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "inputs.selectors.effect_type_selector",
    displayName = "Effect Type Selector",
    description = "Searches and selects a Minecraft status effect.",
    category = "inputs.selectors"
)
public class EffectTypeSelectorNode extends BaseCustomUINode {

    @NodeProperty(
        displayName = "Selected Effect",
        category = "Selection",
        order = 1,
        description = "The currently selected effect ID."
    )
    private String selectedEffect = "minecraft:speed";

    @NodeProperty(
        displayName = "Allow Modded Effects",
        category = "Filter",
        order = 2,
        description = "Whether effect IDs outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final Set<String> BENEFICIAL_EFFECTS = Set.of(
        "speed", "haste", "strength", "instant_health", "jump_boost",
        "regeneration", "resistance", "fire_resistance", "water_breathing",
        "invisibility", "night_vision", "health_boost", "absorption",
        "saturation", "luck", "slow_falling", "conduit_power", "dolphins_grace"
    );

    private static final String OUTPUT_EFFECT_ID = "output_effect_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_EFFECT_PATH = "output_effect_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_IS_BENEFICIAL = "output_is_beneficial";
    private static final String OUTPUT_EFFECT_TYPE = "output_effect_type";

    private transient ImString searchBuffer = new ImString(256);
    private transient volatile List<String> filteredEffects = new ArrayList<>();
    private transient volatile boolean showDropdown = false;
    private transient volatile String lastSearchText = "";

    private static final int MAX_RESULTS = 20;

    public EffectTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.effect_type_selector");

        addOutputPort(new BasePort(OUTPUT_EFFECT_ID, "Effect ID", "The selected effect's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected effect ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_EFFECT_PATH, "Effect Path", "The path part of the selected effect ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected effect is outside the minecraft namespace", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_BENEFICIAL, "Is Beneficial", "Whether the selected effect is beneficial", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EFFECT_TYPE, "Effect Type", "A simple derived category for the selected effect", NodeDataType.STRING, this));

        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft status effect.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutputs();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        if (showDropdown) {
            height += Math.min(filteredEffects.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 184f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;

            try {
                float edgeMargin = toPixels(getSmallPadding(), zoom);
                float availableWidth = Math.max(0.0f, toPixelsExact(width, zoom) - edgeMargin * 2.0f);
                float baseCursorX = ImGui.getCursorPosX();
                layout.addVerticalSpacing(getMediumPadding());

                layout.pushFramePadding(4.0f, 3.0f);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                layout.setItemWidth(availableWidth / zoom);

                if (ImGui.inputTextWithHint("##effect_search", "Search effects...", searchBuffer)) {
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

                layout.popItemWidth();
                layout.popStyleVar();

                layout.addVerticalSpacing(0.5f);

                List<String> filteredSnapshot = filteredEffects;
                if (showDropdown && !filteredSnapshot.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    int displayCount = Math.min(filteredSnapshot.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String effectId = filteredSnapshot.get(i);
                        String effectPath = effectId.contains(":") ? effectId.split(":", 2)[1] : effectId;
                        boolean isSelected = effectId.equals(selectedEffect);
                        boolean isBeneficial = BENEFICIAL_EFFECTS.contains(effectPath);

                        if (isSelected) {
                            ImGui.pushStyleColor(
                                ImGuiCol.Text,
                                isBeneficial ? 0.3f : 0.9f,
                                isBeneficial ? 0.9f : 0.4f,
                                isBeneficial ? 0.6f : 0.3f,
                                1.0f
                            );
                        }

                        String prefix = isBeneficial ? "  +" : "  -";
                        if (ImGui.selectable(prefix + effectPath + "##" + i, isSelected)) {
                            setSelectedEffect(effectId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }

                        if (isSelected) {
                            ImGui.popStyleColor();
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(effectId);
                        }
                    }

                    if (filteredSnapshot.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... " + (filteredSnapshot.size() - MAX_RESULTS) + " more");
                        ImGui.popStyleColor();
                    }
                    ImGui.popStyleColor();
                }

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("EffectTypeSelectorNode UI render failed: " + e.getMessage());
            }
            return changed;
        });
    }

    private void updateFilteredList(String searchText) {
        List<String> nextFilteredEffects = new ArrayList<>();
        if (searchText.isEmpty()) {
            filteredEffects = nextFilteredEffects;
            invalidateCache();
            return;
        }
        try {
            for (Identifier id : Registries.STATUS_EFFECT.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) {
                    continue;
                }
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    nextFilteredEffects.add(fullId);
                    if (nextFilteredEffects.size() >= MAX_RESULTS * 2) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Registry may not be ready yet.
        }
        filteredEffects = nextFilteredEffects;
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
        if (effectId == null || effectId.isEmpty()) {
            effectId = "minecraft:speed";
        }
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

    public String getSelectedEffect() {
        return selectedEffect;
    }

    public boolean isAllowModded() {
        return allowModded;
    }

    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedEffect.startsWith("minecraft:")) {
            setSelectedEffect("minecraft:speed");
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedEffect", getSelectedEffect(),
            "allowModded", isAllowModded()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            if (map.get("selectedEffect") instanceof String value) {
                setSelectedEffect(value);
            }
        }
    }
}
