package com.nodecraft.gui.components.panel;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.preset.GraphPresetApplier;
import com.nodecraft.gui.preset.GraphPresetLoader;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.gui.screens.NodecraftScreen;
import com.nodecraft.gui.utils.UserPreferences;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PresetLibraryPanel {

    private static final String PREF_ACTIVE_TAB_KEY = "left_panel.active_tab";
    private static final String SEARCH_HINT = "搜索预设...";

    private final ImString searchQuery = new ImString("", 128);
    private GraphPresetRules rules = new GraphPresetRules();
    private String statusMessage = "";
    private long statusMessageUntilMs = 0L;
    private boolean loaded;

    public void initialize() {
        if (loaded) {
            return;
        }
        rules = GraphPresetLoader.load();
        loaded = true;
    }

    public void reload() {
        rules = GraphPresetLoader.load();
        loaded = true;
    }

    public void render() {
        initialize();

        ImGui.text("浏览建筑原型、元素模板与常用节点组合。");
        ImGui.separator();

        ImGui.pushItemWidth(-1);
        ImGui.inputTextWithHint("##presetSearch", SEARCH_HINT, searchQuery);
        ImGui.popItemWidth();

        if (System.currentTimeMillis() < statusMessageUntilMs && !statusMessage.isBlank()) {
            ImGui.textColored(0.4f, 0.9f, 0.5f, 1.0f, statusMessage);
        }

        ImGui.separator();

        String filter = searchQuery.get().trim().toLowerCase(Locale.ROOT);
        if (rules.categories == null || rules.categories.isEmpty()) {
            ImGui.textDisabled("暂无预设数据");
            return;
        }

        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category == null || category.presets == null || category.presets.isEmpty()) {
                continue;
            }
            List<GraphPresetRules.GraphPresetDefinition> visible = filterPresets(category.presets, filter);
            if (visible.isEmpty()) {
                continue;
            }

            String categoryLabel = category.displayName != null ? category.displayName : category.id;
            if (ImGui.collapsingHeader(categoryLabel + "##preset_cat_" + category.id, ImGuiTreeNodeFlags.DefaultOpen)) {
                for (GraphPresetRules.GraphPresetDefinition preset : visible) {
                    renderPresetItem(preset);
                }
            }
        }
    }

    private List<GraphPresetRules.GraphPresetDefinition> filterPresets(
            List<GraphPresetRules.GraphPresetDefinition> presets,
            String filter) {
        if (filter.isEmpty()) {
            return presets;
        }
        List<GraphPresetRules.GraphPresetDefinition> visible = new ArrayList<>();
        for (GraphPresetRules.GraphPresetDefinition preset : presets) {
            if (preset == null) {
                continue;
            }
            String name = preset.displayName != null ? preset.displayName.toLowerCase(Locale.ROOT) : "";
            String description = preset.description != null ? preset.description.toLowerCase(Locale.ROOT) : "";
            String id = preset.id != null ? preset.id.toLowerCase(Locale.ROOT) : "";
            if (name.contains(filter) || description.contains(filter) || id.contains(filter)) {
                visible.add(preset);
            }
        }
        return visible;
    }

    private void renderPresetItem(GraphPresetRules.GraphPresetDefinition preset) {
        String label = preset.displayName != null ? preset.displayName : preset.id;
        boolean isComposite = "composite".equalsIgnoreCase(preset.kind);

        if (!isComposite) {
            ImGui.beginDisabled();
        }

        if (ImGui.selectable("  " + label + "##preset_" + preset.id)) {
            if (isComposite) {
                applyCompositePreset(preset);
            }
        }

        if (!isComposite) {
            ImGui.endDisabled();
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            if (preset.description != null) {
                ImGui.textWrapped(preset.description);
            }
            if (isComposite) {
                ImGui.text("点击添加到画布中心");
            } else {
                ImGui.textDisabled("筹备中");
            }
            ImGui.endTooltip();
        }
    }

    private void applyCompositePreset(GraphPresetRules.GraphPresetDefinition preset) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof NodecraftScreen screen)) {
            showStatus("编辑器未打开", false);
            return;
        }
        if (screen.getComponentManager() == null || screen.getComponentManager().getCanvasComponent() == null) {
            showStatus("画布不可用", false);
            return;
        }

        ImVec2 center = screen.getComponentManager().getCanvasComponent().getCanvasCenterWorldPosition();
        GraphPresetApplier.ApplyResult result = GraphPresetApplier.apply(preset, center.x, center.y);
        showStatus(result.message(), result.success());
        if (!result.success()) {
            NodeCraft.LOGGER.warn("Failed to apply preset {}: {}", preset.id, result.message());
        }
    }

    private void showStatus(String message, boolean success) {
        statusMessage = message;
        statusMessageUntilMs = System.currentTimeMillis() + 3000L;
        if (!success) {
            NodeCraft.LOGGER.debug("Preset status: {}", message);
        }
    }

    public static int loadPreferredTabIndex() {
        String stored = UserPreferences.getString(PREF_ACTIVE_TAB_KEY, "0");
        try {
            return Math.max(0, Math.min(1, Integer.parseInt(stored)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static void savePreferredTabIndex(int index) {
        UserPreferences.setString(PREF_ACTIVE_TAB_KEY, Integer.toString(Math.max(0, Math.min(1, index))));
    }
}
